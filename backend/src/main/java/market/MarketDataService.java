// backend/src/main/java/coinalarm/Coin_Alarm/market/MarketDataService.java
package coinalarm.Coin_Alarm.market;

import coinalarm.Coin_Alarm.coin.CoinResponseDto;
import coinalarm.Coin_Alarm.upbit.UpbitClient;
import coinalarm.Coin_Alarm.upbit.UpbitTickerResponse;
import coinalarm.Coin_Alarm.upbit.UpbitWSC;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.ZoneId; // ZoneId 임포트
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@EnableScheduling // @Scheduled 어노테이션을 사용하여 주기적인 작업을 활성화합니다.
public class MarketDataService {

  private final UpbitClient upbitClient; // REST API 호출용
  private final UpbitWSC upbitWSC; // WebSocket 호출용
  private final SimpMessagingTemplate messagingTemplate; // 클라이언트로 메시지 푸시용

  // 실시간 데이터를 저장할 임시 저장소 (마켓 코드 -> 최신 UpbitTickerResponse)
  private ConcurrentMap<String, UpbitTickerResponse> latestTickers = new ConcurrentHashMap<>();

  // 1분봉 거래대금 계산을 위한 임시 저장소 (마켓 코드 -> Accumulated Price For Current Minute)
  // Map<MarketCode, Map<TimestampInMinute, AccumulatedPriceForThatMinute>>
  private ConcurrentMap<String, ConcurrentMap<Long, Double>> minuteVolumeBuffers = new ConcurrentHashMap<>();

  // 알람 발생 로깅용 (중복 알람 방지 및 시간 기록)
  private ConcurrentMap<String, Instant> lastAlarmTime = new ConcurrentHashMap<>();
  private final long ALARM_COOLDOWN_SECONDS = 3; // 알람 쿨타임을 3초로 설정

  @Autowired
  public MarketDataService(UpbitClient upbitClient, UpbitWSC upbitWSC, SimpMessagingTemplate messagingTemplate) {
    this.upbitClient = upbitClient;
    this.upbitWSC = upbitWSC;
    this.messagingTemplate = messagingTemplate;
  }

  /**
   * 애플리케이션 시작 시 Upbit WebSocket 연결을 수립하고 실시간 데이터 처리 로직을 설정합니다.
   * @PostConstruct: 이 메서드가 스프링 빈 초기화 후 자동으로 실행되도록 합니다.
   */
  @PostConstruct
  public void init() {
    List<String> allKrwMarkets = upbitClient.getAllKrwMarketCodes(); // REST API로 모든 마켓 코드 가져오기
    upbitWSC.connect(allKrwMarkets); // WebSocket 연결 및 모든 마켓 구독

    // WebSocket으로부터 메시지 수신 시 호출될 콜백 설정
    upbitWSC.setOnTradeMessageReceived(ticker -> {
      // MarketDataService.java:67 에러 해결: ticker.getCode()가 market 키가 됩니다.
      // ticker.getCode()가 null일 경우 (Rest API 응답처럼), ticker.getMarket()을 사용하도록 방어적 코딩.
      String marketKey = ticker.getCode() != null ? ticker.getCode() : ticker.getMarket();
      if (marketKey == null) { // 둘 다 null이면 처리 불가능
        return;
      }
      latestTickers.put(marketKey, ticker); // 최신 티커 업데이트

      long currentMinuteTimestamp = Instant.now().getEpochSecond() / 60; // 현재 분의 시작 타임스탬프 (초 / 60)
      minuteVolumeBuffers
              .computeIfAbsent(marketKey, k -> new ConcurrentHashMap<>()) // <--- 여기도 marketKey 사용
              .merge(currentMinuteTimestamp, ticker.getTradePrice() * ticker.getTradeVolume(), Double::sum);


      // 1억 이상, 3초 쿨타임 알람 로직
      double tradeAmount = ticker.getTradePrice() != null && ticker.getTradeVolume() != null ?
              ticker.getTradePrice() * ticker.getTradeVolume() : 0.0; // null 체크 추가
      if (tradeAmount >= 100_000_000) { // 1억 이상 체결 시
        Instant now = Instant.now(); // 현재 시간
        Instant lastFired = lastAlarmTime.get(marketKey); // <--- 여기도 marketKey 사용

        // 마지막 알람 시간이 없거나, (마지막 알람 시간 + 쿨타임)이 현재 시간보다 이전이면 (쿨타임 지남)
        if (lastFired == null || lastFired.plusSeconds(ALARM_COOLDOWN_SECONDS).isBefore(now)) {
          String alarmMessage = String.format("[%s] %s: %.0f원 대량 체결 포착! (%.0f개 @ %.0f원)",
                  Instant.now().atZone(ZoneId.of("Asia/Seoul")).toLocalTime().withNano(0).toString(), // 한국 시간으로 시:분:초
                  marketKey, // <--- 여기도 marketKey 사용
                  tradeAmount,
                  ticker.getTradeVolume() != null ? ticker.getTradeVolume() : 0.0, // null 체크
                  ticker.getTradePrice() != null ? ticker.getTradePrice() : 0.0); // null 체크
          messagingTemplate.convertAndSend("/topic/alarm-log", alarmMessage); // 프론트엔드로 알람 푸시
          lastAlarmTime.put(marketKey, now); // 마지막 알람 시간 업데이트
        }
      }
    });
  }

  /**
   * REST API 엔드포인트용 서비스 메서드 (초기 데이터 제공)
   * 이 메서드는 초기 로딩 시에만 사용되도록 남겨둡니다. 실시간 데이터는 WebSocket으로 푸시됩니다.
   */
  public List<CoinResponseDto> getFilteredLiveMarketData(boolean all, boolean large, boolean mid, boolean small) {
    List<String> allMarketCodesFromRest = upbitClient.getAllKrwMarketCodes(); // getAllKrwMarketCodes()는 Rest API
    List<UpbitTickerResponse> tickersFromRest = upbitClient.getTicker(allMarketCodesFromRest); // getTicker()도 Rest API

    List<UpbitTickerResponse> filteredTickers = new ArrayList<>();

    if (all) {
      filteredTickers.addAll(tickersFromRest);
    } else {
      if (large) {
        // filter(t -> "KRW-BTC".equals(t.getCode()) || "KRW-ETH".equals(t.getCode())) // 이전 코드
        // MarketDataService.java:115 에러 해결: t.getCode() 또는 t.getMarket() 사용
        tickersFromRest.stream()
                .filter(t -> "KRW-BTC".equals(t.getCode() != null ? t.getCode() : t.getMarket()) ||
                        "KRW-ETH".equals(t.getCode() != null ? t.getCode() : t.getMarket()))
                .forEach(filteredTickers::add);
      }
      if (mid) {
        tickersFromRest.stream()
                .filter(t -> "KRW-XRP".equals(t.getCode() != null ? t.getCode() : t.getMarket()) ||
                        "KRW-ADA".equals(t.getCode() != null ? t.getCode() : t.getMarket()) ||
                        "KRW-SOL".equals(t.getCode() != null ? t.getCode() : t.getMarket()))
                .forEach(filteredTickers::add);
      }
      if (small) {
        tickersFromRest.stream()
                .filter(t -> "KRW-DOGE".equals(t.getCode() != null ? t.getCode() : t.getMarket()) ||
                        "KRW-DOT".equals(t.getCode() != null ? t.getCode() : t.getMarket()) ||
                        "KRW-AVAX".equals(t.getCode() != null ? t.getCode() : t.getMarket()))
                .forEach(filteredTickers::add);
      }
    }
    List<UpbitTickerResponse> distinctFilteredTickers = filteredTickers.stream().distinct().collect(Collectors.toList());

    return distinctFilteredTickers.stream().map(ticker -> {
      String marketCode = ticker.getCode() != null ? ticker.getCode() : ticker.getMarket();
      String[] marketParts = marketCode.split("-");
      String symbol = marketParts.length > 1 ? marketParts[1] : marketParts[0];

      double currentMinuteVolume = minuteVolumeBuffers.getOrDefault(marketCode, new ConcurrentHashMap<>())
              .getOrDefault(Instant.now().getEpochSecond() / 60, 0.0);

      // changeRate와 accTradePrice24h는 WebSocket trade 메시지에는 없습니다.
      // REST API 응답에서는 값이 있겠지만, WebSocket trade 메시지로 latestTickers가 채워지면 null이 될 수 있습니다.
      // 프론트엔드에서 null 체크를 잘 해야 합니다.
      Double changeRate = ticker.getChangeRate();
      Double accTradePrice24h = ticker.getAccTradePrice24h();


      return new CoinResponseDto(
              null,
              marketCode, // name
              symbol, // symbol
              ticker.getTradePrice(),
              String.format("%.2f%%", changeRate != null ? changeRate * 100 : 0.0), // null 체크
              accTradePrice24h, // 24H 거래대금 (trade message에서는 null)
              currentMinuteVolume,
              0.0, // 15분봉 (아직 구현 안 됨)
              0.0, // 1시간봉 (아직 구현 안 됨)
              List.of()
      );
    }).collect(Collectors.toList());
  }

  /**
   * 주기적으로 최신 코인 데이터를 프론트엔드로 푸시합니다.
   * fixedRate: 이전 실행이 완료된 후 N초 뒤에 다시 실행
   * fixedDelay: 이전 실행이 시작된 후 N초 뒤에 다시 실행 (실행 시간 포함)
   */
  @Scheduled(fixedRate = 1000) // 1초마다 실행
  public void pushLatestMarketDataToClients() {
    if (latestTickers.isEmpty()) {
      return;
    }

    List<CoinResponseDto> allCoins = latestTickers.values().stream()
            .map(ticker -> {
              String marketCode = ticker.getCode() != null ? ticker.getCode() : ticker.getMarket();
              String[] marketParts = marketCode.split("-");
              String symbol = marketParts.length > 1 ? marketParts[1] : marketParts[0];

              double currentMinuteVolume = minuteVolumeBuffers.getOrDefault(marketCode, new ConcurrentHashMap<>())
                      .getOrDefault(Instant.now().getEpochSecond() / 60, 0.0);

              Double changeRate = ticker.getChangeRate();
              Double accTradePrice24h = ticker.getAccTradePrice24h();


              return new CoinResponseDto(
                      null,
                      marketCode, // name
                      symbol, // symbol
                      ticker.getTradePrice(),
                      String.format("%.2f%%", changeRate != null ? changeRate * 100 : 0.0),
                      accTradePrice24h, // 24H 거래대금
                      currentMinuteVolume,
                      0.0,
                      0.0,
                      List.of()
              );
            })
            .collect(Collectors.toList());

    List<CoinResponseDto> top5Coins = allCoins.stream()
            .sorted(Comparator.comparing(CoinResponseDto::getVolume1m).reversed())
            .limit(5)
            .collect(Collectors.toList());

    messagingTemplate.convertAndSend("/topic/market-data", allCoins);
    messagingTemplate.convertAndSend("/topic/top-5-market-data", top5Coins);
  }

  /**
   * 매분 0초에 1분봉 거래대금 버퍼를 정리하고 이전 분의 데이터를 확정합니다.
   */
  @Scheduled(cron = "0 * * * * ?") // 매분 0초마다 실행
  public void clearAndProcessMinuteVolumeBuffer() {
    long oneMinuteAgoTimestamp = Instant.now().minusSeconds(60).getEpochSecond() / 60;

    minuteVolumeBuffers.forEach((market, buffer) -> {
      buffer.keySet().removeIf(timestamp -> {
        // 현재 분이 아닌 이전 분의 데이터만 정리 (정교한 1분 계산)
        return timestamp < oneMinuteAgoTimestamp;
      });
    });
  }
}