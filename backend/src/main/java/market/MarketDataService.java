// backend/src/main/java/coinalarm/Coin_Alarm/market/MarketDataService.java
package coinalarm.Coin_Alarm.market;

import coinalarm.Coin_Alarm.coin.CoinResponseDto;
import coinalarm.Coin_Alarm.upbit.UpbitClient; // REST API 클라이언트
import coinalarm.Coin_Alarm.upbit.UpbitTickerResponse;
import coinalarm.Coin_Alarm.upbit.UpbitWSC; // WebSocket 클라이언트
import jakarta.annotation.PostConstruct; // @PostConstruct 임포트
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling; // 스케줄링 활성화
import org.springframework.scheduling.annotation.Scheduled; // 스케줄링 어노테이션
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant; // Instant 임포트
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator; // Comparator 임포트
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap; // 동시성 맵
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import java.util.UUID; // UUID 임포트 (알람 ID 생성용)

@Service
@EnableScheduling // @Scheduled 어노테이션을 사용하여 주기적인 작업을 활성화합니다.
public class MarketDataService {

  private final UpbitClient upbitClient; // REST API 호출용
  private final UpbitWSC UpbitWSC; // WebSocket 호출용
  //private final MarketDataWSC webSocketController; // 클라이언트로 메시지 푸시용
  private final SimpMessagingTemplate messagingTemplate;

  // 실시간 데이터를 저장할 임시 저장소 (마켓 코드 -> 최신 UpbitTickerResponse)
  private ConcurrentMap<String, UpbitTickerResponse> latestTickers = new ConcurrentHashMap<>();

  // 1분봉 거래대금 계산을 위한 임시 저장소 (마켓 코드 -> Accumulated Volume within 1 minute)
  // Map<MarketCode, Map<TimestampInMinute, AccumulatedPriceForThatMinute>>
  private ConcurrentMap<String, ConcurrentMap<Long, Double>> minuteVolumeBuffers = new ConcurrentHashMap<>();

  // 알람 발생 로깅용 (중복 알람 방지 및 시간 기록)
  private ConcurrentMap<String, Instant> lastAlarmTime = new ConcurrentHashMap<>();
  private final long ALARM_COOLDOWN_SECONDS = 10; // 같은 코인 알람 쿨타임 (10초)


  @Autowired
//  public MarketDataService(UpbitClient upbitClient, UpbitWSC UpbitWSC, MarketDataWSC webSocketController) {
  public MarketDataService(UpbitClient upbitClient, UpbitWSC UpbitWSC, SimpMessagingTemplate  messagingTemplate) {
    this.upbitClient = upbitClient;
    this.UpbitWSC = UpbitWSC;
//    this.webSocketController = webSocketController;
    this.messagingTemplate = messagingTemplate; // <--- 주입받은 messagingTemplate 할당
  }

  /**
   * 애플리케이션 시작 시 Upbit WebSocket 연결을 수립합니다.
   * @PostConstruct: 이 메서드가 스프링 빈 초기화 후 자동으로 실행되도록 합니다.
   */
  @PostConstruct
  public void init() {
    List<String> allKrwMarkets = upbitClient.getAllKrwMarketCodes(); // REST API로 모든 마켓 코드 가져오기
    UpbitWSC.connect(allKrwMarkets); // WebSocket 연결 및 모든 마켓 구독

    // WebSocket으로부터 메시지 수신 시 처리할 콜백 설정
    UpbitWSC.setOnTradeMessageReceived(ticker -> {
      latestTickers.put(ticker.getMarket(), ticker); // 최신 티커 업데이트

      // 1분봉 거래대금 누적
      long currentMinuteTimestamp = Instant.now().getEpochSecond() / 60; // 현재 분의 타임스탬프
      minuteVolumeBuffers
              .computeIfAbsent(ticker.getMarket(), k -> new ConcurrentHashMap<>())
              .merge(currentMinuteTimestamp, ticker.getTradePrice() * ticker.getTradeVolume(), Double::sum); // 현재 분의 거래대금 누적

      // 일정 거래금액 이상 실시간 알람 (예: 1억 이상 단일 체결)
      // UpbitTickerResponse의 tradePrice와 tradeVolume은 단일 체결의 가격/수량임
      double tradeAmount = ticker.getTradePrice() * ticker.getTradeVolume();
      if (tradeAmount >= 100_000_000) { // 1억 이상 체결 시 알람
        Instant now = Instant.now();
        Instant lastAlarm = lastAlarmTime.get(ticker.getMarket());
        // 쿨타임 체크
        if (lastAlarm == null || lastAlarm.plusSeconds(ALARM_COOLDOWN_SECONDS).isBefore(now)) {
          String alarmMessage = String.format("[%s] %s: %.0f원 이상 단일 대량 체결! (%.0f개 @ %.0f원)",
                  Instant.now().toString(),
                  ticker.getMarket(),
                  tradeAmount,
                  ticker.getTradeVolume(),
                  ticker.getTradePrice());
          messagingTemplate.convertAndSend("/topic/alarm-log", alarmMessage); // <--- 직접 메시지 전송!
//          webSocketController.pushAlarmMessage(alarmMessage); // 프론트엔드로 알람 푸시
          lastAlarmTime.put(ticker.getMarket(), now);
        }
      }
    });
  }

  /**
   * 기존 REST API 엔드포인트용 서비스 메서드 (초기 데이터 제공)
   * 이 메서드는 이제 주로 초기 로딩용으로 사용되거나, WebSocket이 연결되기 전 대체용으로 사용됩니다.
   */
  public List<CoinResponseDto> getFilteredLiveMarketData(boolean all, boolean large, boolean mid, boolean small) {
    // 모든 마켓 코드를 가져와 최신 티커 정보를 기준으로 CoinResponseDto를 생성합니다.
    // 이 메서드는 UpbitClient를 통해 한 번만 호출되도록 변경할 수 있습니다 (예: 시작 시 1회).
    List<String> marketCodes = upbitClient.getAllKrwMarketCodes();
    List<UpbitTickerResponse> allTickers = upbitClient.getTicker(marketCodes); // REST API로 모든 티커 가져옴

    List<UpbitTickerResponse> filteredTickers = new ArrayList<>();

    if (all) {
      filteredTickers.addAll(allTickers);
    } else {
      // 필터 로직 (기존과 동일, 하드코딩된 예시 마켓 사용)
      if (large) {
        allTickers.stream()
                .filter(t -> t.getMarket().equals("KRW-BTC") || t.getMarket().equals("KRW-ETH"))
                .forEach(filteredTickers::add);
      }
      if (mid) {
        allTickers.stream()
                .filter(t -> t.getMarket().equals("KRW-XRP") || t.getMarket().equals("KRW-ADA") || t.getMarket().equals("KRW-SOL"))
                .forEach(filteredTickers::add);
      }
      if (small) {
        allTickers.stream()
                .filter(t -> t.getMarket().equals("KRW-DOGE") || t.getMarket().equals("KRW-DOT") || t.getMarket().equals("KRW-AVAX"))
                .forEach(filteredTickers::add);
      }
    }
    List<UpbitTickerResponse> distinctFilteredTickers = filteredTickers.stream().distinct().collect(Collectors.toList());

    return distinctFilteredTickers.stream().map(ticker -> {
      String[] marketParts = ticker.getMarket().split("-");
      String symbol = marketParts.length > 1 ? marketParts[1] : marketParts[0];

      // 최신 캔들 거래대금 가져오기 (latestTickers 맵에서 최신 정보 참조)
      // 여기서는 웹소켓으로 누적된 1분 거래대금을 사용 (Scheduled 메서드에서 맵을 비워주므로 정확한 1분 데이터가 아닐 수 있음)
      double volume1m = minuteVolumeBuffers.getOrDefault(ticker.getMarket(), new ConcurrentHashMap<>())
              .getOrDefault(Instant.now().getEpochSecond() / 60, 0.0);
      double volume15m = 0.0; // 15분, 1시간봉은 별도 로직 또는 REST API 호출 필요
      double volume1h = 0.0;

      return new CoinResponseDto(
              null, // id
              ticker.getMarket(), // name
              symbol, // symbol
              ticker.getTradePrice(), // currentPrice
              String.format("%.2f%%", ticker.getChangeRate() * 100), // priceChange
              ticker.getAccTradePrice24h(), // 24H 거래대금
              volume1m, // 1분봉 거래대금
              volume15m, // 15분봉 거래대금 (현재 구현 안됨)
              volume1h,  // 1시간봉 거래대금 (현재 구현 안됨)
              List.of() // alarm
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
              String[] marketParts = ticker.getMarket().split("-");
              String symbol = marketParts.length > 1 ? marketParts[1] : marketParts[0];

              // 최신 캔들 거래대금 (WebSocket에서 누적된 현재 분의 값)
              double volume1m = minuteVolumeBuffers.getOrDefault(ticker.getMarket(), new ConcurrentHashMap<>())
                      .getOrDefault(Instant.now().getEpochSecond() / 60, 0.0);
              // 15분, 1시간봉 거래대금은 현재 REST API로 가져오지 않으므로 0.0으로 설정
              double volume15m = 0.0;
              double volume1h = 0.0;

              return new CoinResponseDto(
                      null,
                      ticker.getMarket(),
                      symbol,
                      ticker.getTradePrice(),
                      String.format("%.2f%%", ticker.getChangeRate() * 100),
                      ticker.getAccTradePrice24h(), // 24H 거래대금
                      volume1m,
                      volume15m,
                      volume1h,
                      List.of()
              );
            })
            .collect(Collectors.toList());

    // Top 5 코인 선정 (가장 높은 1분 거래대금 기준)
    List<CoinResponseDto> top5Coins = allCoins.stream()
            .sorted(Comparator.comparing(CoinResponseDto::getVolume1m).reversed()) // volume1m 기준으로 내림차순 정렬
            .limit(5) // 상위 5개만 선택
            .collect(Collectors.toList());

    messagingTemplate.convertAndSend("/topic/market-data", allCoins); // <--- 직접 메시지 전송!
    messagingTemplate.convertAndSend("/topic/top-5-market-data", top5Coins); // <--- 직접 메시지 전
    //2025.8.4 삭제 주입을 서로 하고있으면 에러나는 문제 때문에
//    // 전체 코인 데이터 (필터링 없이 모든 코인 전달)
//    webSocketController.pushLiveMarketData(allCoins);
//    // Top 5 코인 데이터는 별도의 토픽으로 푸시 가능 (선택 사항)
//    //webSocketController.messagingTemplate.convertAndSend("/topic/top-5-market-data", top5Coins);
//    webSocketController.pushTop5MarketData(top5Coins);
    // 이전 분 버퍼 정리 (1분마다 한번씩) - 더 정교한 로직 필요
    // minuteVolumeBuffers.forEach((market, buffer) -> buffer.keySet().removeIf(ts -> ts < currentMinuteTimestamp));
    // 스케줄링 시점과 1분 계산 시점이 완벽히 일치하지 않아 문제 발생 가능
  }

  /**
   * 매분 0초에 1분봉 거래대금 버퍼를 초기화하고, 이전 분의 데이터를 확정합니다.
   * 정확한 1분봉 계산을 위해 추가된 스케줄링 메서드
   */
  @Scheduled(cron = "0 * * * * ?") // 매분 0초마다 실행
  public void clearAndProcessMinuteVolumeBuffer() {
    long currentMinuteTimestamp = Instant.now().getEpochSecond() / 60; // 현재 분의 타임스탬프

    minuteVolumeBuffers.forEach((market, buffer) -> {
      // 이전 분 데이터 처리 (옵션)
      buffer.keySet().removeIf(timestamp -> {
        // 현재 분이 아닌 이전 분의 데이터만 정리 (정교한 1분 계산)
        return timestamp != currentMinuteTimestamp;
      });
    });
    // 현재 분의 버퍼는 유지됩니다.
  }
}