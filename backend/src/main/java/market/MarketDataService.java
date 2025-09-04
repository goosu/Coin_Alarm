package coinalarm.Coin_Alarm.market;

import coinalarm.Coin_Alarm.coin.CoinResponseDto;
import coinalarm.Coin_Alarm.upbit.UpbitClient;
import coinalarm.Coin_Alarm.upbit.UpbitTickerResponse;
import coinalarm.Coin_Alarm.upbit.UpbitWSC;
import coinalarm.Coin_Alarm.upbit.UpbitCandleResponse;

import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * MarketDataService
 * - WebSocket으로 실시간 시세(Trade)를 수신하여 latestTickers 업데이트
 * - 라운드로빈 방식으로 캔들(1m,5m,15m,1h)을 REST API로 가져와 캐싱
 * - 즐겨찾기(favorites) 관리 API용 메서드 제공
 * - pushLatestMarketDataToClients()가 정렬/필터링 후 /topic/market-data 로 푸시
 */
@Service
@EnableScheduling
public class MarketDataService {

  private final UpbitClient upbitClient;
  private final UpbitWSC upbitWSC;
  private final SimpMessagingTemplate messagingTemplate;

  // 실시간 티커(시세)
  private final ConcurrentMap<String, UpbitTickerResponse> latestTickers = new ConcurrentHashMap<>();

  // 캔들 캐시: 1분, 5분, 15분, 1시간 누적 거래대금
  private final ConcurrentMap<String, Double> latest1MinuteVolume = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Double> latest5MinuteVolume = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Double> latest15MinuteVolume = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, Double> latest1HourVolume = new ConcurrentHashMap<>();

  // 슬라이딩 윈도우(매수/매도 비율) 용 실시간 체결 저장
  private final ConcurrentMap<String, List<Map<String, Object>>> tradeWindows = new ConcurrentHashMap<>();
  private final long WINDOW_SIZE_SECONDS = 10L; // 슬라이딩 윈도우 길이(초)

  // 알람 쿨타임 관리
  private final ConcurrentMap<String, Instant> lastAlarmTime = new ConcurrentHashMap<>();
  private final long ALARM_COOLDOWN_SECONDS = 3L;

  //2025821 add 로그관리위함
  private static final Logger log = LoggerFactory.getLogger(coinalarm.Coin_Alarm.market.MarketDataService.class);

  // 즐겨찾기 (메모리 기반)
  private final Set<String> favoriteMarkets = ConcurrentHashMap.newKeySet();

  // 캔들 라운드로빈 인덱스 및 마켓 리스트(초기화 시 getAllKrwMarketCodes 로 채움)
  private int candleFetchIndex = 0;
  private volatile List<String> allMarketCodesCache = new ArrayList<>();

  // 5분 기준 필터 임계값 (예시값, 필요시 조정)
  private final double MIN_5MIN_VOLUME_THRESHOLD = 50_000_000.0; // 예: 5천만 원

  @Autowired
  public MarketDataService(UpbitClient upbitClient, UpbitWSC upbitWSC, SimpMessagingTemplate messagingTemplate) {
    this.upbitClient = upbitClient;
    this.upbitWSC = upbitWSC;
    this.messagingTemplate = messagingTemplate;
  }

  @PostConstruct
  public void init() {
    // 캐시용 마켓 코드 초기화 (REST에서 한 번)
    try {
      List<String> allCodes = upbitClient.getAllKrwMarketCodes();
      if (allCodes != null && !allCodes.isEmpty()) {
        allMarketCodesCache = new ArrayList<>(allCodes);
      }
    } catch (Exception ignored) {
    }

    // WebSocket 연결 및 콜백 등록 (실시간 체결 수신)
    log.info("find error connect "); //2025821 에러문제

    upbitWSC.connect(allMarketCodesCache);
    upbitWSC.setOnTradeMessageReceived(ticker -> {
      // normalize market key
      String marketKey = ticker.getMarketCode(); // DTO의 normalized getter
      if (marketKey == null) return;

      latestTickers.put(marketKey, ticker);

      // 실시간 체결로 알람 판단 (예: 단일 체결 금액이 1억 이상)
      double price = ticker.getTradePriceNormalized() != null ? ticker.getTradePriceNormalized() : 0.0;
      double vol = ticker.getTradeVolumeNormalized() != null ? ticker.getTradeVolumeNormalized() : 0.0;
      double tradeAmount = price * vol;

      if (tradeAmount >= 100_000_000.0) {
        Instant now = Instant.now();
        Instant last = lastAlarmTime.get(marketKey);
        if (last == null || last.plusSeconds(ALARM_COOLDOWN_SECONDS).isBefore(now)) {
          String msg = String.format("[%s] %s: %.0f원 대량 체결! (%.4f @ %.0f원)",
                  Instant.now().atZone(ZoneId.of("Asia/Seoul")).toLocalTime().withNano(0),
                  marketKey, tradeAmount, vol, price);
          messagingTemplate.convertAndSend("/topic/alarm-log", msg);
          lastAlarmTime.put(marketKey, now);
        }
      }

      // 슬라이딩 윈도우에 trade event 저장 (매수/매도 구분 포함)
      Map<String, Object> event = new HashMap<>();
      event.put("timestamp", System.currentTimeMillis());
      event.put("amount", tradeAmount);
      event.put("type", ticker.getAskBid() != null ? ticker.getAskBid() : "UNKNOWN"); // "ASK" or "BID"
      tradeWindows.computeIfAbsent(marketKey, k -> Collections.synchronizedList(new LinkedList<>())).add(event);

      // 윈도우 오래된 항목 제거
      long cutoff = System.currentTimeMillis() - WINDOW_SIZE_SECONDS * 1000;
      List<Map<String, Object>> list = tradeWindows.get(marketKey);
      if (list != null) {
        list.removeIf(e -> ((Long) e.get("timestamp")) < cutoff);
      }
    });
  }

  // 즐겨찾기 관리
  public boolean addFavoriteMarket(String marketCode) { return favoriteMarkets.add(marketCode); }
  public boolean removeFavoriteMarket(String marketCode) { return favoriteMarkets.remove(marketCode); }
  public Set<String> getFavoriteMarkets() { return Collections.unmodifiableSet(favoriteMarkets); }

  // 라운드로빈 방식 캔들 수집: 1초에 약 5회 호출(시스템 상황에 맞게 fixedRate 조절)
  // 주의: Upbit rate limit을 반드시 고려하세요. 필요시 fixedRate를 늘리세요.
  @Scheduled(fixedRate = 200) // 200ms 마다 한 마켓 처리(설정에 따라 변경)
  public void fetchCandleRoundRobin() {
    List<String> codes = allMarketCodesCache;
    if (codes == null || codes.isEmpty()) return;

    String market = codes.get(candleFetchIndex % codes.size());
    candleFetchIndex = (candleFetchIndex + 1) % codes.size();

    try {
      // 1분, 5분, 15분, 60분 캔들(최신 1개) 가져와 캐시
      List<UpbitCandleResponse> c1 = upbitClient.getMinuteCandles(1, market, 1);
      if (!c1.isEmpty()) latest1MinuteVolume.put(market, c1.get(0).getCandleAccTradePrice());

      List<UpbitCandleResponse> c5 = upbitClient.getMinuteCandles(5, market, 1);
      if (!c5.isEmpty()) latest5MinuteVolume.put(market, c5.get(0).getCandleAccTradePrice());

      List<UpbitCandleResponse> c15 = upbitClient.getMinuteCandles(15, market, 1);
      if (!c15.isEmpty()) latest15MinuteVolume.put(market, c15.get(0).getCandleAccTradePrice());

      List<UpbitCandleResponse> c60 = upbitClient.getMinuteCandles(60, market, 1);
      if (!c60.isEmpty()) latest1HourVolume.put(market, c60.get(0).getCandleAccTradePrice());
    } catch (Exception e) {
      // Rate limit 또는 네트워크 이슈: 무시하거나 로깅
      //logger.warn("candle fetch failed for {}: {}", market, e.getMessage());
    }
  }

  // 초기 데이터(REST 기반) 로딩용
  public List<CoinResponseDto> getFilteredLiveMarketData(boolean all, boolean large, boolean mid, boolean small) {
    List<String> allCodes = upbitClient.getAllKrwMarketCodes();
    List<UpbitTickerResponse> tickers = upbitClient.getTicker(allCodes);

    List<UpbitTickerResponse> filtered = new ArrayList<>();
    if (all) filtered.addAll(tickers);
    else {
      if (large) filtered.addAll(tickers.stream()
              .filter(t -> {
                String code = t.getMarketCode();
                return "KRW-BTC".equals(code) || "KRW-ETH".equals(code);
              }).collect(Collectors.toList()));
      if (mid) filtered.addAll(tickers.stream()
              .filter(t -> {
                String code = t.getMarketCode();
                return "KRW-XRP".equals(code) || "KRW-ADA".equals(code) || "KRW-SOL".equals(code);
              }).collect(Collectors.toList()));
      if (small) filtered.addAll(tickers.stream()
              .filter(t -> {
                String code = t.getMarketCode();
                return "KRW-DOGE".equals(code) || "KRW-DOT".equals(code) || "KRW-AVAX".equals(code);
              }).collect(Collectors.toList()));
    }

    List<UpbitTickerResponse> distinct = filtered.stream().distinct().collect(Collectors.toList());
    return distinct.stream().map(t -> {
      String marketCode = t.getMarketCode();
      if (marketCode == null) marketCode = "UNKNOWN-UNKNOWN";
      String[] parts = marketCode.split("-");
      String symbol = parts.length > 1 ? parts[1] : parts[0];

      double v1 = latest1MinuteVolume.getOrDefault(marketCode, 0.0);
      double v5 = latest5MinuteVolume.getOrDefault(marketCode, 0.0);
      double v15 = latest15MinuteVolume.getOrDefault(marketCode, 0.0);
      double v1h = latest1HourVolume.getOrDefault(marketCode, 0.0);

      return new CoinResponseDto(null, marketCode, symbol,
              t.getTradePriceNormalized(), String.format("%.2f%%", t.getChangeRate() != null ? t.getChangeRate() * 100 : 0.0),
              t.getAccTradePrice24h(), v1, v5, v15, v1h, Collections.<String>emptyList());
    }).collect(Collectors.toList());
  }

  //캔들데이터 push
  // 주기적 푸시 (1초마다): 즐겨찾기 우선, 그 외는 필터링(예: 5분봉 기준)
  @Scheduled(fixedRate = 1000)
  public void pushLatestMarketDataToClients() {
    if (latestTickers.isEmpty()) return;

    double buyVolume = 0.0; //20250903 Add
    double sellVolume = 0.0; //20250903 Add

    // 매수/매도 비율 계산
    Map<String, Map<String, Double>> buySellRatios = new ConcurrentHashMap<>();
    long cutoff = System.currentTimeMillis() - WINDOW_SIZE_SECONDS * 1000;
    tradeWindows.forEach((market, list) -> {
      list.removeIf(e -> ((Long)e.get("timestamp")) < cutoff);
      double buy = 0, sell = 0;
      for (Map<String, Object> ev : list) {
        double amt = (Double) ev.get("amount");
        String type = (String) ev.get("type");
        if ("BID".equals(type)) buy += amt;
        else if ("ASK".equals(type)) sell += amt;
      }
      double tot = buy + sell;
      if (tot > 0) {
        Map<String, Double> r = new HashMap<>();
        r.put("buyRatio", buy / tot);
        r.put("sellRatio", sell / tot);
        buySellRatios.put(market, r);
      }
    });

    // build DTO lists  캔들데이터 가져옴
    List<CoinResponseDto> favorites = new ArrayList<>();
    List<CoinResponseDto> normals = new ArrayList<>();

    for (String key : new ArrayList<>(latestTickers.keySet())) {
      UpbitTickerResponse t = latestTickers.get(key);
      if (t == null) continue;
      String mc = t.getMarketCode();
      if (mc == null) continue;

      //이부분은 -단위로 나눈다음에 심볼부분만 가져오기위해서? 확인해보기
      String sym = mc.contains("-") ? mc.split("-")[1] : mc;

      double v1 = latest1MinuteVolume.getOrDefault(mc, 0.0);
      double v5 = latest5MinuteVolume.getOrDefault(mc, 0.0);
      double v15 = latest15MinuteVolume.getOrDefault(mc, 0.0);
      double v1h = latest1HourVolume.getOrDefault(mc, 0.0);

//20250829 bulider 사용으로 명시화하여 직관적이게 수정
//      CoinResponseDto dto = new CoinResponseDto(null, mc, sym, t.getTradePriceNormalized(),
//              String.format("%.2f%%", t.getChangeRate() != null ? t.getChangeRate() * 100 : 0.0),
//              t.getAccTradePrice24h(), v1, v5, v15, v1h, List.of());

      //20250829 수정 str
      // CoinResponseDto 빌드: 프론트엔드의 Coin 타입 필드명과 일치시켜 값을 할당합니다.
      CoinResponseDto dto = CoinResponseDto.builder()
              .symbol(mc) // Market Code (예: KRW-BTC)
              .price(t.getTradePriceNormalized()) // 현재가
              .change24h(t.getSignedChangeRateNormalized() * 100) // 전일대비 (%)
              .accTradePrice24h(t.getAccTradePrice24hNormalized()) // 24시간 누적 거래대금 (UpbitTickerResponse에서 가져옴)
              .volume1m(v1) // latest1MinuteVolume에서 가져온 1분봉 값
              .volume5m(v5) // latest5MinuteVolume에서 가져온 5분봉 값
              .volume15m(v15) // latest15MinuteVolume에서 가져온 15분봉 값
              .volume1h(v1h)   // latest1HourVolume에서 가져온 1시간봉 값
              .buyVolume(0.0)  // 매수/매도 거래대금 (tradeWindows에서 계산)
              .sellVolume(0.0)
              .build();

      // 디버깅용: DTO가 프론트엔드로 보내지기 전 최종 값 확인
      log.info("DEBUG_DTO_SENT: {}", dto.toString()); // CoinResponseDto에 @ToString (Lombok) 추가 필요!
      //20250829 수정 End

      if (favoriteMarkets.contains(mc)) favorites.add(dto);
      else normals.add(dto);
    }

    // favorites first (sorted by v1), then normals filtered by 5min threshold and sorted
    favorites.sort(Comparator.comparing(CoinResponseDto::getVolume1m).reversed());
    List<CoinResponseDto> filteredNormals = normals.stream()
            .filter(c -> c.getVolume5m() >= MIN_5MIN_VOLUME_THRESHOLD)
            .sorted(Comparator.comparing(CoinResponseDto::getVolume1m).reversed())
            .collect(Collectors.toList());

    List<CoinResponseDto> all = new ArrayList<>();
    all.addAll(favorites);
    all.addAll(filteredNormals);

    //messagingTemplate.convertAndSend("/topic/market-data", all);
    simpMessagingTemplate.convertAndSend("/marketData", marketData); //20250825 ADD
    messagingTemplate.convertAndSend("/topic/buy-sell-ratio", buySellRatios);
  }

  //20250826 데이터가공
  // !!!! 아래 getMarketData 메소드를 MarketDataService.java에 추가하거나 수정하세요 !!!!
  // 이 메소드가 MarketDataController에서 호출됩니다.
  // 기존 MarketDataService에 있는 getTicker 등을 활용하여 데이터 조합
//  public Map<String, Object> getMarketData() throws JsonProcessingException {
//    // 모든 KRW 마켓 코드 가져오기 (한번만 가져와 캐싱해두면 효율적)
//    // 여기서는 예시로 제한된 마켓 코드 사용
//    List<String> marketCodes = upbitClient.getAllKrwMarketCodes();
//    if (marketCodes.isEmpty()) {
//      marketCodes = List.of("KRW-BTC", "KRW-ETH", "KRW-XRP"); // 폴백
//    }
//
//    // Upbit REST API에서 티커 정보 가져오기
//    List<UpbitTickerResponse> tickers = upbitClient.getTicker(marketCodes);
//
//    // 프론트엔드 App.tsx의 Coin 타입에 맞게 데이터 가공
//    Map<String, Object> processedData = new HashMap<>();
//    for (UpbitTickerResponse ticker : tickers) {
//      Map<String, Object> coinData = new HashMap<>();
//      coinData.put("symbol", ticker.getMarket());
//      coinData.put("price", ticker.getTradePrice());
//      coinData.put("volume1m", ticker.getAccTradePrice24h()); // 임시로 24h 누적거래대금 사용 (1분봉 정보는 다른 API 필요)
//      coinData.put("change24h", ticker.getSignedChangeRate() * 100); // %로 변환
//
//      // 매수/매도 거래대금은 현재 ticker API에 없으므로 임시 값 또는 0
//      coinData.put("buyVolume", 0);
//      coinData.put("sellVolume", 0);
//
//      processedData.put(ticker.getMarket(), coinData);
//    }
//    return processedData;
//  }


}