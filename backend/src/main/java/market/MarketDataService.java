// backend/src/main/java/coinalarm/Coin_Alarm/market/MarketDataService.java (전체 덮어씌우기)
package coinalarm.Coin_Alarm.market;

import coinalarm.Coin_Alarm.coin.CoinResponseDto;
import coinalarm.Coin_Alarm.upbit.UpbitClient;
import coinalarm.Coin_Alarm.upbit.UpbitTickerResponse;
import coinalarm.Coin_Alarm.upbit.UpbitWSC;
import coinalarm.Coin_Alarm.upbit.UpbitCandleResponse;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Service
@EnableScheduling
public class MarketDataService {

  private final UpbitClient upbitClient;
  private final UpbitWSC upbitWSC;
  private final SimpMessagingTemplate messagingTemplate;

  // 실시간 티커 (시세) 데이터를 저장 (WebSocket으로부터 계속 업데이트)
  private ConcurrentMap<String, UpbitTickerResponse> latestTickers = new ConcurrentHashMap<>();

  // 각 분봉/시간봉 캔들의 누적 거래대금을 저장할 맵 (주기적 API 호출로 업데이트)
  private ConcurrentMap<String, Double> latest1MinuteVolume = new ConcurrentHashMap<>();
  private ConcurrentMap<String, Double> latest15MinuteVolume = new ConcurrentHashMap<>();
  private ConcurrentMap<String, Double> latest1HourVolume = new ConcurrentHashMap<>();

  // 알람 발생 로깅용 (중복 알람 방지)
  private ConcurrentMap<String, Instant> lastAlarmTime = new ConcurrentHashMap<>();
  private final long ALARM_COOLDOWN_SECONDS = 3;

  // 즐겨찾기 마켓 코드 목록 (동적 관리) - Redis/DB로 확장 가능
  // Set을 사용하여 중복을 방지하고 빠른 검색을 가능하게 합니다.
  private Set<String> favoriteMarkets = ConcurrentHashMap.newKeySet(); // Thread-safe Set

  // 슬라이딩 윈도우를 위한 실시간 매수/매도 체결 데이터 저장소
  // Map<MarketCode, List<TradeEvent>> -> TradeEvent { timestamp, amount, type(ASK/BID) }
  // 여기서는 간단히 { "timestamp": ..., "amount": ..., "type": "ASK/BID" } 맵을 List에 저장
  private ConcurrentMap<String, List<Map<String, Object>>> tradeWindows = new ConcurrentHashMap<>();
  private final long WINDOW_SIZE_SECONDS = 10; // 슬라이딩 윈도우 크기 (10초)

  // 동적 필터링 기준 (임시 설정)
  private final long MIN_TRADE_VOLUME_24H_THRESHOLD = 50_000_000_000L; // 500억 (롱터우님의 소형 기준)

  @Autowired
  public MarketDataService(UpbitClient upbitClient, UpbitWSC upbitWSC, SimpMessagingTemplate messagingTemplate) {
    this.upbitClient = upbitClient;
    this.upbitWSC = upbitWSC;
    this.messagingTemplate = messagingTemplate;
  }

  /**
   * 애플리케이션 시작 시 Upbit WebSocket 연결 및 초기 데이터/알람 로직 설정
   */
  @PostConstruct
  public void init() {
    List<String> allKrwMarkets = upbitClient.getAllKrwMarketCodes(); // REST API로 모든 마켓 코드 가져옴
    upbitWSC.connect(allKrwMarkets); // WebSocket 연결 및 모든 마켓 구독 (Trade 메시지)

    // 초기 즐겨찾기 설정 (테스트용)
    favoriteMarkets.add("KRW-BTC");
    favoriteMarkets.add("KRW-ETH");
    favoriteMarkets.add("KRW-XRP");
    favoriteMarkets.add("KRW-DOGE");
    favoriteMarkets.add("KRW-SOL");

    upbitWSC.setOnTradeMessageReceived(ticker -> {
      String marketKey = ticker.getCode();
      if (marketKey == null) { return; }
      latestTickers.put(marketKey, ticker); // 최신 티커 업데이트

      // 대량 체결 알람 로직
      double tradeAmount = ticker.getTradePrice() != null && ticker.getTradeVolume() != null ?
              ticker.getTradePrice() * ticker.getTradeVolume() : 0.0;
      if (tradeAmount >= 100_000_000) { // 1억 이상 체결 시
        Instant now = Instant.now();
        Instant lastFired = lastAlarmTime.get(marketKey);
        if (lastFired == null || lastFired.plusSeconds(ALARM_COOLDOWN_SECONDS).isBefore(now)) {
          String alarmMessage = String.format("[%s] %s: %.0f원 대량 체결 포착! (%.0f개 @ %.0f원)",
                  Instant.now().atZone(ZoneId.of("Asia/Seoul")).toLocalTime().withNano(0).toString(),
                  marketKey, tradeAmount, ticker.getTradeVolume(), ticker.getTradePrice());
          messagingTemplate.convertAndSend("/topic/alarm-log", alarmMessage);
          lastAlarmTime.put(marketKey, now);
        }
      }

      // 슬라이딩 윈도우 데이터 추가 및 정리 (매수/매도세 분석용)
      // TradeEvent { timestamp, amount, type(ASK/BID) }
      Map<String, Object> tradeEvent = new HashMap<>();
      tradeEvent.put("timestamp", System.currentTimeMillis()); // 현재 시간 (밀리초)
      tradeEvent.put("amount", tradeAmount);
      tradeEvent.put("type", ticker.getAskBid()); // "ASK" or "BID"

      tradeWindows.computeIfAbsent(marketKey, k -> Collections.synchronizedList(new LinkedList<>()))
              .add(tradeEvent);

      // 윈도우 사이즈 밖의 오래된 데이터 제거
      long cutoffTime = System.currentTimeMillis() - (WINDOW_SIZE_SECONDS * 1000);
      tradeWindows.get(marketKey).removeIf(event -> ((Long) event.get("timestamp")) < cutoffTime);
    });

    fetchAndCacheAllCandles(); // 앱 시작 시 모든 코인의 캔들 데이터 초기화
  }

  // =========================================================
  // 즐겨찾기 관리 API (프론트엔드에서 호출될 수 있도록 추가)
  // =========================================================
  public boolean addFavoriteMarket(String marketCode) {
    return favoriteMarkets.add(marketCode);
  }

  public boolean removeFavoriteMarket(String marketCode) {
    return favoriteMarkets.remove(marketCode);
  }

  public Set<String> getFavoriteMarkets() {
    return Collections.unmodifiableSet(favoriteMarkets); // 외부에서 수정 불가능하게 반환
  }

  // =========================================================
  // 캔들 데이터 주기적 업데이트 (모든 코인 대상으로)
  // Upbit API Rate Limit을 고려하여 API 호출 간격과 오류 처리 로직을 더 견고하게 구현해야 합니다.
  // 현재는 모든 코인에 대해 매분마다 캔들 정보를 요청합니다. (Rate Limit 초과 가능성 있음)
  // Rate Limit이 초과된다면 API 호출 주기를 더 길게(예: 5분마다) 변경하거나,
  // 특정 코인(즐겨찾기)만 자주 가져오고 나머지는 덜 자주 가져오는 전략 필요
  // =========================================================
  private int candleFetchIndex = 0;
  private List<String> allActiveMarketCodes = new ArrayList<>(); // 현재 웹소켓으로 데이터가 수신되는 모든 마켓 코드

  @Scheduled(fixedRate = 200) // 0.2초마다 1개의 마켓 캔들을 가져오면, 100개 마켓의 캔들을 20초마다 갱신 가능. (매 초 5회 호출)
  public void fetchAndCacheAllCandlesByRoundRobin() {
    if (latestTickers.isEmpty()) { // 웹소켓으로 시세 데이터가 들어오기 시작하면
      allActiveMarketCodes = new ArrayList<>(latestTickers.keySet()); // 현재 활성 마켓 코드를 업데이트
    }
    if (allActiveMarketCodes.isEmpty()) {
      return;
    }

    String marketCode = allActiveMarketCodes.get(candleFetchIndex);
    candleFetchIndex = (candleFetchIndex + 1) % allActiveMarketCodes.size(); // 다음 마켓 인덱스로

    try {
      // 1분봉 캔들 가져오기
      List<UpbitCandleResponse> candles1m = upbitClient.getMinuteCandles(1, marketCode, 1);
      if (!candles1m.isEmpty()) { latest1MinuteVolume.put(marketCode, candles1m.get(0).getCandleAccTradePrice()); }

      // 15분봉 캔들 가져오기
      List<UpbitCandleResponse> candles15m = upbitClient.getMinuteCandles(15, marketCode, 1);
      if (!candles15m.isEmpty()) { latest15MinuteVolume.put(marketCode, candles15m.get(0).getCandleAccTradePrice()); }

      // 1시간봉 캔들 가져오기
      List<UpbitCandleResponse> candles1h = upbitClient.getMinuteCandles(60, marketCode, 1);
      if (!candles1h.isEmpty()) { latest1HourVolume.put(marketCode, candles1h.get(0).getCandleAccTradePrice()); }

    } catch (Exception e) {
      // Rate Limit 초과, 네트워크 문제 등 - 경고 로그만 남김
      // log.warn("Error fetching candle for {}: {}", marketCode, e.getMessage());
    }
  }


  /**
   * REST API 엔드포인트용 서비스 메서드 (초기 데이터 제공)
   */
  public List<CoinResponseDto> getFilteredLiveMarketData(boolean all, boolean large, boolean mid, boolean small) {
    // 이 메서드는 Initial Load시 사용.
    // allActiveMarketCodes는 웹소켓이 시작된 후 latestTickers로 채워지므로, 앱 시작 초기에는 비어있을 수 있습니다.
    // 따라서 앱 시작 시에는 UpbitClient.getAllKrwMarketCodes()로 모든 마켓을 가져오는 기존 로직을 사용합니다.
    List<String> currentMarketCodes = upbitClient.getAllKrwMarketCodes();
    List<UpbitTickerResponse> tickers = upbitClient.getTicker(currentMarketCodes); // REST API로 현재 시세 가져옴

    List<UpbitTickerResponse> filteredTickers = new ArrayList<>();
    if (all) { filteredTickers.addAll(tickers); } else {
      if (large) { tickers.stream().filter(t -> "KRW-BTC".equals(t.getCode() != null ? t.getCode() : t.getMarket()) || "KRW-ETH".equals(t.getCode() != null ? t.getCode() : t.getMarket())).forEach(filteredTickers::add); }
      if (mid) { tickers.stream().filter(t -> "KRW-XRP".equals(t.getCode() != null ? t.getCode() : t.getMarket()) || "KRW-ADA".equals(t.getCode() != null ? t.getCode() : t.getMarket()) || "KRW-SOL".equals(t.getCode() != null ? t.getCode() : t.getMarket())).forEach(filteredTickers::add); }
      if (small) { tickers.stream().filter(t -> "KRW-DOGE".equals(t.getCode() != null ? t.getCode() : t.getMarket()) || "KRW-DOT".equals(t.getCode() != null ? t.getCode() : t.getMarket()) || "KRW-AVAX".equals(t.getCode() != null ? t.getCode() : t.getMarket())).forEach(filteredTickers::add); }
    }
    List<UpbitTickerResponse> distinctFilteredTickers = filteredTickers.stream().distinct().collect(Collectors.toList());

    return distinctFilteredTickers.stream().map(ticker -> {
      String marketCode = ticker.getCode() != null ? ticker.getCode() : ticker.getMarket();
      if (marketCode == null || marketCode.isEmpty()) { marketCode = "UNKNOWN-UNKNOWN"; }
      String[] marketParts = marketCode.split("-");
      String symbol = marketParts.length > 1 ? marketParts[1] : marketParts[0];

      // 캔들 값은 캐시된 맵에서 가져옵니다.
      double volume1m = latest1MinuteVolume.getOrDefault(marketCode, 0.0);
      double volume15m = latest15MinuteVolume.getOrDefault(marketCode, 0.0);
      double volume1h = latest1HourVolume.getOrDefault(marketCode, 0.0);

      Double changeRate = ticker.getChangeRate();
      Double accTradePrice24h = ticker.getAccTradePrice24h();

      return new CoinResponseDto(
              null, marketCode, symbol, ticker.getTradePrice(),
              String.format("%.2f%%", changeRate != null ? changeRate * 100 : 0.0),
              accTradePrice24h,
              volume1m, volume15m, volume1h, List.of()
      );
    }).collect(Collectors.toList());
  }


  /**
   * 주기적으로 최신 코인 데이터를 프론트엔드로 푸시합니다.
   * 이 메서드는 latestTickers (WebSocket을 통해 수신된 실시간 시세)와 캐시된 캔들 데이터를 기반으로 작동합니다.
   */
  @Scheduled(fixedRate = 1000) // 1초마다 실행
  public void pushLatestMarketDataToClients() {
    if (latestTickers.isEmpty()) { return; }

    // 매수/매도세 비율 계산
    Map<String, Map<String, Double>> buySellRatios = new ConcurrentHashMap<>();
    long currentWindowEnd = System.currentTimeMillis();
    long currentWindowStart = currentWindowEnd - (WINDOW_SIZE_SECONDS * 1000);

    tradeWindows.forEach((marketCode, trades) -> {
      double totalBuyAmount = 0;
      double totalSellAmount = 0;
      Iterator<Map<String, Object>> iterator = trades.iterator();
      while (iterator.hasNext()) {
        Map<String, Object> trade = iterator.next();
        long timestamp = (Long) trade.get("timestamp");
        if (timestamp < currentWindowStart) {
          iterator.remove(); // 오래된 데이터 제거
          continue;
        }
        double amount = (Double) trade.get("amount");
        String type = (String) trade.get("type"); // "ASK" (매도), "BID" (매수)
        if ("BID".equals(type)) {
          totalBuyAmount += amount;
        } else if ("ASK".equals(type)) {
          totalSellAmount += amount;
        }
      }

      double totalAmount = totalBuyAmount + totalSellAmount;
      if (totalAmount > 0) {
        Map<String, Double> ratio = new HashMap<>();
        ratio.put("buyRatio", totalBuyAmount / totalAmount);
        ratio.put("sellRatio", totalSellAmount / totalAmount);
        buySellRatios.put(marketCode, ratio);
      }
    });


    List<CoinResponseDto> allCoins = new ArrayList<>();
    List<CoinResponseDto> favoriteCoinDtos = new ArrayList<>();
    List<CoinResponseDto> normalCoinDtos = new ArrayList<>();

    for (String marketCode : new ArrayList<>(latestTickers.keySet())) {
      UpbitTickerResponse ticker = latestTickers.get(marketCode);
      if (ticker == null) continue;

      String currentMarketId = ticker.getCode() != null ? ticker.getCode() : ticker.getMarket();
      if (currentMarketId == null) { continue; }
      String[] marketParts = currentMarketId.split("-");
      String symbol = marketParts.length > 1 ? marketParts[1] : marketParts[0];

      double volume1m = latest1MinuteVolume.getOrDefault(currentMarketId, 0.0);
      double volume15m = latest15MinuteVolume.getOrDefault(currentMarketId, 0.0);
      double volume1h = latest1HourVolume.getOrDefault(currentMarketId, 0.0);

      Double changeRate = ticker.getChangeRate();
      Double accTradePrice24h = ticker.getAccTradePrice24h();

      CoinResponseDto dto = new CoinResponseDto(
              null, currentMarketId, symbol, ticker.getTradePrice(),
              String.format("%.2f%%", changeRate != null ? changeRate * 100 : 0.0),
              accTradePrice24h,
              volume1m, volume15m, volume1h, List.of()
      );

      // 즐겨찾기 분류
      if (favoriteMarkets.contains(currentMarketId)) {
        favoriteCoinDtos.add(dto);
      } else {
        normalCoinDtos.add(dto);
      }
    }

    // 즐겨찾기 코인 정렬 (1분봉 기준)
    favoriteCoinDtos.sort(Comparator.comparing(CoinResponseDto::getVolume1m).reversed());

    // 일반 코인 정렬
    normalCoinDtos.sort(Comparator.comparing(CoinResponseDto::getAccTradePrice24h, Comparator.nullsLast(Comparator.reverseOrder())) // 24H 거래대금 내림차순
            .thenComparing(CoinResponseDto::getPriceChange, Comparator.nullsLast(Comparator.reverseOrder()))); // 변화율 내림차순 (String이므로 주의)


    // 필터링 (일반 코인 중 최소 거래대금 미달 시 제외)
    List<CoinResponseDto> filteredNormalCoinDtos = normalCoinDtos.stream()
            .filter(coin -> {
              // 즐겨찾기가 아닌 코인만 필터링합니다. 즐겨찾기 코인은 필터링 조건과 무관하게 항상 표시됩니다.
              // AccTradePrice24h가 null일 수 있으므로 null 체크
              return coin.getAccTradePrice24h() != null && coin.getAccTradePrice24h() >= MIN_TRADE_VOLUME_24H_THRESHOLD;
            })
            .collect(Collectors.toList());


    // 최종 코인 리스트 (즐겨찾기 + 필터링된 일반 코인)
    allCoins.addAll(favoriteCoinDtos); // 즐겨찾기 코인 먼저 추가
    allCoins.addAll(filteredNormalCoinDtos); // 그 다음 일반 코인 추가


    // =========================================================
    // 프론트엔드로 데이터 푸시
    // =========================================================
    messagingTemplate.convertAndSend("/topic/market-data", allCoins); // 모든 코인 목록 (정렬, 필터링, 즐겨찾기 포함)
    messagingTemplate.convertAndSend("/topic/buy-sell-ratio", buySellRatios); // 매수/매도세 비율 데이터

    // 캔들 버퍼는 여기서 처리하지 않습니다.
  }
}