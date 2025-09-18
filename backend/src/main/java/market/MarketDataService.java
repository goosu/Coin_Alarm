package coinalarm.Coin_Alarm.market;

import coinalarm.Coin_Alarm.coin.Coin;
import coinalarm.Coin_Alarm.coin.CoinDao;
import coinalarm.Coin_Alarm.coin.CoinResponseDto;
import coinalarm.Coin_Alarm.upbit.UpbitClient;
import coinalarm.Coin_Alarm.upbit.UpbitCandleResponse;
import coinalarm.Coin_Alarm.upbit.UpbitTickerResponse;
import coinalarm.Coin_Alarm.upbit.UpbitWSC;

import jakarta.annotation.PostConstruct;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static coinalarm.Coin_Alarm.AccessingDataJpaApplication.log;

@Service
public class MarketDataService {

  private final SimpMessagingTemplate messagingTemplate;
  private final UpbitWSC upbitWSC;
  private final UpbitClient upbitClient;
  private final CoinDao coinDao;

  // --- 캐시 저장소들 ---
  private final ConcurrentHashMap<String, UpbitTickerResponse> latestTickers = new ConcurrentHashMap<>(); //rest apit
  private final ConcurrentHashMap<String, Double> latest1MinuteVolume = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Double> latest5MinuteVolume = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Double> latest15MinuteVolume = new ConcurrentHashMap<>();
  private final ConcurrentHashMap<String, Double> latest1HourVolume = new ConcurrentHashMap<>();

  // 매수/매도 비율 캐시 (key: 마켓 코드, value: Map<String, Double> (키: "buyRatio", "sellRatio"))
  private final ConcurrentHashMap<String, Map<String, Double>> buySellRatios = new ConcurrentHashMap<>();

  // --- 스케줄링 및 데이터 관리 변수 ---
  private int currentCandleMarketIndex = 0;
  private List<String> allMarketCodes;

  // 즐겨찾기 마켓 목록 (Set을 사용하여 중복 방지)
  private final Set<String> favoriteMarkets = ConcurrentHashMap.newKeySet();

  // --- 생성자: 의존성 주입 ---
  public MarketDataService(SimpMessagingTemplate messagingTemplate,
                           UpbitWSC upbitWSC,
                           UpbitClient upbitClient,
                           CoinDao coinDao) {
    this.messagingTemplate = messagingTemplate;
    this.upbitWSC = upbitWSC;
    this.upbitClient = upbitClient;
    this.coinDao = coinDao;
  }

  // --- 초기화 메소드: 애플리케이션 시작 시 한 번 실행됩니다 ---
  @PostConstruct
  public void init() {
    allMarketCodes = upbitClient.getAllKrwMarketCodes();
    upbitWSC.connectWebSocket(allMarketCodes, this::processTickerMessage);

    // addFavoriteMarket("KRW-BTC");
    // addFavoriteMarket("KRW-ETH");
  }

  // --- 웹소켓 메시지 처리 메소드: 실시간 티커 데이터를 받아 캐시에 업데이트합니다 ---
  //20250917 이부분은 따로 삭제처리나 수정 데이터가 안들어오는모델
  public void processTickerMessage(UpbitTickerResponse ticker) {
    latestTickers.put(ticker.getMarket(), ticker);

    // [널 처리]: NullPointerException 방지를 위해 accTradePrice24h 널 체크
    Double accTradePrice24h = (ticker.getAccTradePrice24h() != null) ? ticker.getAccTradePrice24h() : 0.0;

    Map<String, Double> ratioMap = new ConcurrentHashMap<>();
    ratioMap.put("buyRatio", accTradePrice24h * 0.5);
    ratioMap.put("sellRatio", accTradePrice24h * 0.5);
    buySellRatios.put(ticker.getMarket(), ratioMap);
  }

  // --- 캔들 데이터 주기적 가져오기: Upbit REST API 사용 ---
  // fixedRate = 1000: 이전 실행 시작 시간으로부터 1000ms(1초) 후에 다음 실행을 시작합니다.
  @Scheduled(fixedRate = 1000)
  public void fetchCandleRoundRobin() {
    if (allMarketCodes == null || allMarketCodes.isEmpty()) {
      return;
    }
    String marketCode = allMarketCodes.get(currentCandleMarketIndex);
    
    //20250911 getTradeVolume => candleAccTradePrice 수정  (getTradeVolume null로 넘어옴 체결데이터인듯)
    upbitClient.getMinuteCandles(marketCode, 1, 1)
            .blockOptional().ifPresent(candles -> {
              candles.forEach(candle -> {
//                if (candle.getTradeVolume() != null) {
                if (candle.getCandleAccTradePrice() != null) {
                  latest1MinuteVolume.put(marketCode, candle.getCandleAccTradePrice());
                }
              });
            });
    upbitClient.getMinuteCandles(marketCode, 5, 1)
            .blockOptional().ifPresent(candles -> {
              candles.forEach(candle -> {
                if (candle.getCandleAccTradePrice() != null) {
                  latest5MinuteVolume.put(marketCode, candle.getCandleAccTradePrice());
                }
              });
            });
    upbitClient.getMinuteCandles(marketCode, 15, 1)
            .blockOptional().ifPresent(candles -> {
              candles.forEach(candle -> {
                if (candle.getCandleAccTradePrice() != null) {
                  latest15MinuteVolume.put(marketCode, candle.getCandleAccTradePrice());
                }
              });
            });
    upbitClient.getMinuteCandles(marketCode, 60, 1)
            .blockOptional().ifPresent(candles -> {
              candles.forEach(candle -> {
                if (candle.getCandleAccTradePrice() != null) {
                  latest1HourVolume.put(marketCode, candle.getCandleAccTradePrice());
                }
              });
            });

    currentCandleMarketIndex = (currentCandleMarketIndex + 1) % allMarketCodes.size();
  }

  // --- 클라이언트(프론트엔드)에 최신 마켓 데이터를 주기적으로 푸시합니다 ---
  @Scheduled(fixedRate = 1000)
  public void pushLatestMarketDataToClients() {
    // 모든 티커 데이터를 CoinResponseDto로 변환 (DB 필터링 이전)
    List<CoinResponseDto> convertedList = latestTickers.values().stream()
            .map(ticker -> {
              // [널 처리]: 모든 필드에 대해 null 체크 및 기본값 설정 적용
              // 이전 NPE 발생 지점들 모두 커버
              return CoinResponseDto.builder()
                      .symbol(ticker.getMarket())
                      .price(ticker.getTradePrice() != null ? ticker.getTradePrice() : 0.0)
                      .volume1m(latest1MinuteVolume.getOrDefault(ticker.getMarket(), 0.0))
                      .volume5m(latest5MinuteVolume.getOrDefault(ticker.getMarket(), 0.0))
                      .volume15m(latest15MinuteVolume.getOrDefault(ticker.getMarket(), 0.0))
                      .volume1h(latest1HourVolume.getOrDefault(ticker.getMarket(), 0.0))
                      .accTradePrice24h(ticker.getAccTradePrice24h() != null ? ticker.getAccTradePrice24h() : 0.0) /*** [신규] 일봉 거래대금 ***/
                      .change24h(ticker.getSignedChangeRate() != null ? ticker.getSignedChangeRate()*100 : 0.0)
                      // buyVolume/sellVolume: Map<String, Map<String, Double>> 형태의 buySellRatios에서 추출
                      .buyVolume(buySellRatios.getOrDefault(ticker.getMarket(), Collections.emptyMap()).getOrDefault("buyRatio", 0.0))
                      .sellVolume(buySellRatios.getOrDefault(ticker.getMarket(), Collections.emptyMap()).getOrDefault("sellRatio", 0.0))
                      .timestamp(ticker.getTradeTimestamp() != null ? ticker.getTradeTimestamp() : System.currentTimeMillis())
                      .isFavorite(favoriteMarkets.contains(ticker.getMarket())) //20250918 추가
                      .build();
            })
            // 중복 제거 및 정렬
            .distinct()
            .sorted(Comparator.comparing(CoinResponseDto::getSymbol))
            .collect(Collectors.toList());

    // DB에 있는 코인 심볼들만 최종적으로 필터링하여 전송
    Set<String> dbMarketSymbols = coinDao.findAll().stream()
            .map(Coin::getSymbol)
            .collect(Collectors.toSet());

    // 필터링된 데이터를 Map 형태로 변환 (웹소켓 전송 포맷에 맞춤) //20250915 여기에서 필터링되어서 5개로 되네
    Map<String, CoinResponseDto> finalFilteredMap = convertedList.stream()
            .filter(dto -> dbMarketSymbols.contains(dto.getSymbol()))
            .collect(Collectors.toMap(CoinResponseDto::getSymbol, dto -> dto));

    messagingTemplate.convertAndSend("/topic/marketData", finalFilteredMap);
  }

  // --- 즐겨찾기 마켓 관리 메소드 ---
  public void addFavoriteMarket(String market) {
    //20250918 즐겨찾기 일봉을 보내기위한것
    boolean added = favoriteMarkets.add(market);
    if(added){
      updateSingleFavoritedailyVolume(market);
    }

  }

  //20250918 즐겨찾기 일봉을 보내기위한것 STR
  public void updateSingleFavoritedailyVolume(String market){
    upbitClient.updateDailyVolumesForFavorites(List.of(market))
            .subscribe(
                    null,
                    error -> log.error("즐겨찾기 일봉 업데이트 실패: {}", market, error)
            );
  }

  @Scheduled(fixedRate = 300,000 )
  public void updateFavoritesDailyVolumes(){
    if(favoriteMarkets.isEmpty()) return;

    List<String> favorites = new ArrayList<>(favoriteMarkets);
    log.info("즐겨찾기 마켓 일봉 Update: {}", favorites.size());
    upbitClient.updateDailyVolumesForFavorites(favorites)
            .subscribe(
                    null,
                    error -> log.error("즐겨찾기 마켓 일봉 업데이트 실패: {}", favorites, error),
                    () -> log.info("즐겨찾기 마켓 일봉 업데이트 성공: {}", favorites)
            );
  }
  //20250918 즐겨찾기 일봉을 보내기위한것 END

  public void removeFavoriteMarket(String market) {
    favoriteMarkets.remove(market);
  }

  public Set<String> getFavoriteMarkets() {
    return favoriteMarkets;
  }

  // --- 필터링된 실시간 시장 데이터 조회 (REST API 요청용) ---
  // MarketDataController에서 호출되며, large, mid, small 필터링 인자를 받습니다.
  public Map<String, CoinResponseDto> getFilteredLiveMarketData(boolean large, boolean mid, boolean small) {
    // 1. DB에서 시가총액 조건에 맞는 Coin 엔티티 목록을 가져옵니다.
    List<Coin> filteredCoinsFromDb = new ArrayList<>();
    if (large) {
      filteredCoinsFromDb.addAll(coinDao.findByMarketCapBetween(5_000_000_000_000L, Long.MAX_VALUE));
    }
    if (mid) {
      filteredCoinsFromDb.addAll(coinDao.findByMarketCapBetween(700_000_000_000L, 4_999_999_999_999L));
    }
    if (small) {
      filteredCoinsFromDb.addAll(coinDao.findByMarketCapBetween(50_000_000_000L, 699_999_999_699L));
    }

    // 2. 필터링된 코인 심볼들을 기반으로 최신 티커 데이터를 찾아 CoinResponseDto로 변환합니다.
    ConcurrentHashMap<String, CoinResponseDto> result = new ConcurrentHashMap<>();
    filteredCoinsFromDb.forEach(coin -> {
      UpbitTickerResponse latestTicker = latestTickers.get(coin.getSymbol());
      if (latestTicker != null) {
        // [널 처리]: NullPointerException 방지를 위해 널 체크 추가
        CoinResponseDto dto = CoinResponseDto.builder()
                .symbol(coin.getSymbol())
                .price(latestTicker.getTradePrice() != null ? latestTicker.getTradePrice() : 0.0)
                .volume1m(latest1MinuteVolume.getOrDefault(coin.getSymbol(), 0.0))
                .volume5m(latest5MinuteVolume.getOrDefault(coin.getSymbol(), 0.0))
                .volume15m(latest15MinuteVolume.getOrDefault(coin.getSymbol(), 0.0))
                .volume1h(latest1HourVolume.getOrDefault(coin.getSymbol(), 0.0))
                .accTradePrice24h(latestTicker.getAccTradePrice24h() != null ? latestTicker.getAccTradePrice24h() : 0.0)
                .change24h(latestTicker.getSignedChangeRate() != null ? latestTicker.getSignedChangeRate()*100 : 0.0)
                // buyVolume/sellVolume: Map<String, Map<String, Double>> 형태의 buySellRatios에서 추출
                .buyVolume(buySellRatios.getOrDefault(coin.getSymbol(), Collections.emptyMap()).getOrDefault("buyRatio", 0.0))
                .sellVolume(buySellRatios.getOrDefault(coin.getSymbol(), Collections.emptyMap()).getOrDefault("sellRatio", 0.0))
                .timestamp(latestTicker.getTradeTimestamp() != null ? latestTicker.getTradeTimestamp() : 0L)
                .isFavorite(favoriteMarkets.contains(coin.getSymbol())) //20250918 추가
                .build();
        result.put(coin.getSymbol(), dto);
      }
    });
    return result;
  }
}


//// backend/src/main/java/coinalarm/Coin_Alarm/market/MarketDataService.java
//package coinalarm.Coin_Alarm.market;
//
//import coinalarm.Coin_Alarm.coin.CoinResponseDto;
//import coinalarm.Coin_Alarm.upbit.UpbitClient;
//import coinalarm.Coin_Alarm.upbit.UpbitTickerResponse;
//import coinalarm.Coin_Alarm.upbit.UpbitCandleResponse;
//import coinalarm.Coin_Alarm.upbit.UpbitWSC;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import jakarta.annotation.PostConstruct;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.messaging.simp.SimpMessagingTemplate;
//import org.springframework.scheduling.annotation.EnableScheduling;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import java.time.Instant;
//import java.time.ZoneId;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ConcurrentMap;
//import java.util.stream.Collectors;
//import java.util.concurrent.CopyOnWriteArrayList; // List 타입으로 변경시
//
///**
// * MarketDataService
// * - WebSocket으로 실시간 시세(Trade)를 수신하여 latestTickers 업데이트
// * - 라운드로빈 방식으로 캔들(1m,5m,15m,1h)을 REST API로 가져와 캐싱
// * - 즐겨찾기(favorites) 관리 API용 메서드 제공
// * - pushLatestMarketDataToClients()가 정렬/필터링 후 /topic/market-data 로 푸시
// */
//@Service
//@EnableScheduling
//public class MarketDataService {
//
//  private final UpbitClient upbitClient;
//  private final UpbitWSC upbitWSC;
//  private final SimpMessagingTemplate messagingTemplate;
//
//  // 실시간 티커(시세)
//  private final ConcurrentMap<String, UpbitTickerResponse> latestTickers = new ConcurrentHashMap<>();
//
//  // 매수-매도 비율
//  private final ConcurrentMap<String, Map<String, Double>> buySellRatios = new ConcurrentHashMap<>();
//
//  // 캔들 캐시: 1분, 5분, 15분, 1시간 누적 거래대금
//  private final ConcurrentMap<String, Double> latest1MinuteVolume = new ConcurrentHashMap<>();
//  private final ConcurrentMap<String, Double> latest5MinuteVolume = new ConcurrentHashMap<>();
//  private final ConcurrentMap<String, Double> latest15MinuteVolume = new ConcurrentHashMap<>();
//  private final ConcurrentMap<String, Double> latest1HourVolume = new ConcurrentHashMap<>();
//
//  // 슬라이딩 윈도우(매수/매도 비율) 용 실시간 체결 저장
//  // LinkedList는 ConcurrentHashMap과 같이 쓰기 적절하지 않으므로 CopyOnWriteArrayList 또는 동기화된 리스트 사용 권장
//  private final ConcurrentMap<String, List<Map<String, Object>>> tradeWindows = new ConcurrentHashMap<>();
//  private final long WINDOW_SIZE_SECONDS = 10L; // 슬라이딩 윈도우 길이(초)
//
//  // 알람 쿨타임 관리
//  private final ConcurrentMap<String, Instant> lastAlarmTime = new ConcurrentHashMap<>();
//  private final long ALARM_COOLDOWN_SECONDS = 3L;
//
//  private static final Logger log = LoggerFactory.getLogger(MarketDataService.class); // 패키지명 변경: coinalarm.Coin_Alarm.market.MarketDataService -> MarketDataService
//
//  // 즐겨찾기 (메모리 기반)
//  private final Set<String> favoriteMarkets = ConcurrentHashMap.newKeySet();
//
//  // 캔들 라운드로빈 인덱스 및 마켓 리스트(초기화 시 getAllKrwMarketCodes 로 채움)
//  private int candleFetchIndex = 0;
//  private volatile List<String> allMarketCodesCache = new ArrayList<>();
//
//  // 5분 기준 필터 임계값 (예시값, 필요시 조정)
//  private final double MIN_5MIN_VOLUME_THRESHOLD = 50_000_000.0; // 예: 5천만 원
//
//  @Autowired
//  public MarketDataService(UpbitClient upbitClient, UpbitWSC upbitWSC, SimpMessagingTemplate messagingTemplate) {
//    this.upbitClient = upbitClient;
//    this.upbitWSC = upbitWSC;
//    this.messagingTemplate = messagingTemplate;
//  }
//
//  @PostConstruct
//  public void init() {
//    // 캐시용 마켓 코드 초기화 (REST에서 한 번)
//    try {
//      List<String> allCodes = upbitClient.getAllKrwMarketCodes();
//      if (allCodes != null && !allCodes.isEmpty()) {
//        allMarketCodesCache = new ArrayList<>(allCodes);
//      }
//    } catch (Exception ignored) {
//      log.error("Error fetching initial market codes: {}", ignored.getMessage());
//    }
//
//    // WebSocket 연결 및 콜백 등록 (실시간 체결 수신)
//    log.info("find error connect "); //2025821 에러문제 주석은 필요시 제거
//
//    upbitWSC.connect(allMarketCodesCache);
//    upbitWSC.setOnTradeMessageReceived(ticker -> {
//      // normalize market key
//      String marketKey = ticker.getMarketCode(); // DTO의 normalized getter
//      if (marketKey == null) {
//        log.warn("Received ticker with null market code: {}", ticker);
//        return;
//      }
//
//      latestTickers.put(marketKey, ticker);
//
//      // 실시간 체결로 알람 판단 (예: 단일 체결 금액이 1억 이상)
//      Double price = ticker.getTradePriceNormalized(); // NullPointerException 방지를 위해 Double 타입 확인
//      Double vol = ticker.getTradeVolumeNormalized();
//      double tradeAmount = (price != null && vol != null) ? price * vol : 0.0; // null 체크
//
//      if (tradeAmount >= 100_000_000.0) {
//        Instant now = Instant.now();
//        Instant last = lastAlarmTime.get(marketKey);
//        if (last == null || last.plusSeconds(ALARM_COOLDOWN_SECONDS).isBefore(now)) {
//          String msg = String.format("[%s] %s: %.0f원 대량 체결! (%.4f @ %.0f원)",
//                  Instant.now().atZone(ZoneId.of("Asia/Seoul")).toLocalTime().withNano(0),
//                  marketKey, tradeAmount, vol, price);
//          messagingTemplate.convertAndSend("/topic/alarm-log", msg);
//          lastAlarmTime.put(marketKey, now);
//        }
//      }
//
//      // 슬라이딩 윈도우에 trade event 저장 (매수/매도 구분 포함)
//      Map<String, Object> event = new HashMap<>();
//      event.put("timestamp", System.currentTimeMillis());
//      event.put("amount", tradeAmount);
//      // *** [수정] getAskBid()는 UpbitTickerResponse에 없습니다. 대신 임시로 다른 값 사용 또는 제거 ***
//      // 이 로직은 주로 체결(trade) WebSocket API를 통해 'ask_bid' 정보를 받을 때 사용합니다.
//      // Ticker API에는 이 정보가 없습니다. 따라서 항상 "UNKNOWN"으로 설정됩니다.
//      event.put("type", ticker.getAskBid() != null ? ticker.getAskBid() : "UNKNOWN"); // "ASK" or "BID" (현재 Ticker DTO에는 없음)
//
//      // *** [수정] tradeWindows 리스트 타입 명시 (LinkedList 대신 CopyOnWriteArrayList 권장) ***
//      // CopyOnWriteArrayList는 Read가 많고 Write가 적을 때 효율적이며 Thread-safe
//      tradeWindows.computeIfAbsent(marketKey, k -> new CopyOnWriteArrayList<>()).add(event);
//
//      // 윈도우 사이즈 초과하는 과거 데이터 제거
//      long cutoff = System.currentTimeMillis() - (WINDOW_SIZE_SECONDS * 1000);
//      List<Map<String, Object>> list = tradeWindows.get(marketKey); // list는 null이 아님 (computeIfAbsent 보장)
//      if (list != null) { // 명시적 null 체크 유지
//        list.removeIf(e -> (Long) e.get("timestamp") < cutoff);
//      }
//    });
//
//    log.info("UpbitWSC WebSocket 연결 완료 및 콜백 등록");
//  }
//
//  // 매 10초마다 캔들 데이터 라운드로빈 방식으로 가져오기 (Upbit API Rate Limit 고려)
//  // *** [수정] MarketDataService Gist에 있던 캔들 fetch 스케줄러 이름 (fetchCandleRoundRobin)과 fixedRate 200ms로 변경 ***
//  @Scheduled(fixedRate = 500) // 200ms 마다 한 마켓 처리 (Upbit API rate limit: 초당 10회 = 100ms 당 1회. 200ms는 안전)
//  public void fetchCandleRoundRobin() {
//    List<String> codes = allMarketCodesCache;
//    if (codes == null || codes.isEmpty()) return;
//
//    // 라운드로빈 방식으로 한 번에 하나의 마켓 코드만 요청
//    String targetMarket = codes.get(candleFetchIndex % codes.size());
//    candleFetchIndex = (candleFetchIndex + 1) % allMarketCodesCache.size(); // allMarketCodesCache.size()로 변경
//
//    try {
//      // 1분, 5분, 15분, 60분 캔들(최신 1개) 가져와 캐시
//      // UpbitClient의 getMinuteCandles 메소드가 Integer unit, String market, Integer count를 받음.
//      List<UpbitCandleResponse> c1 = upbitClient.getMinuteCandles(1, targetMarket, 1);
//      if (!c1.isEmpty() && c1.get(0).getCandleAccTradePrice() != null) {
//        latest1MinuteVolume.put(targetMarket, c1.get(0).getCandleAccTradePrice());
//        // 1분봉 누적 거래대금이 5천만원 이상일 경우 알람 발생 (추가 조건 예시)
//        if (c1.get(0).getCandleAccTradePrice() >= 50_000_000.0) {
//          log.info("1분봉 거래대금 5천만원 이상: " + targetMarket);
//        }
//      }
//
//      List<UpbitCandleResponse> c5 = upbitClient.getMinuteCandles(5, targetMarket, 1);
//      if (!c5.isEmpty() && c5.get(0).getCandleAccTradePrice() != null) {
//        latest5MinuteVolume.put(targetMarket, c5.get(0).getCandleAccTradePrice());
//      }
//
//      List<UpbitCandleResponse> c15 = upbitClient.getMinuteCandles(15, targetMarket, 1);
//      if (!c15.isEmpty() && c15.get(0).getCandleAccTradePrice() != null) {
//        latest15MinuteVolume.put(targetMarket, c15.get(0).getCandleAccTradePrice());
//      }
//
//      List<UpbitCandleResponse> c60 = upbitClient.getMinuteCandles(60, targetMarket, 1); // 1시간봉은 60분으로 조회
//      if (!c60.isEmpty() && c60.get(0).getCandleAccTradePrice() != null) {
//        latest1HourVolume.put(targetMarket, c60.get(0).getCandleAccTradePrice());
//      }
//
//    } catch (Exception e) {
//      log.error("Error fetching candles for {}: {}", targetMarket, e.getMessage());
//      // Upbit API Rate Limit 에러를 처리하기 위한 로직 추가 필요 (Thread.sleep 또는 재시도 큐)
//    }
//  }
//
//  // 매 1초마다 모든 시장 데이터를 필터링/가공하여 클라이언트에게 푸시
//  // 이 메소드는 프론트엔드의 App.tsx에서 요청하는 데이터의 최종 소스입니다.
//  @Scheduled(fixedRate = 1000) // 1초
//  public void pushLatestMarketDataToClients() {
//    if (latestTickers.isEmpty()) {
//      return;
//    }
//
//    buySellRatios.clear(); //매수매도 비율 초기화
//
//    // *** [추가] 매수/매도 비율 계산 변수 선언 및 슬라이딩 윈도우 컷오프 ***
//    Map<String, Map<String, Double>> buySellRatios = new ConcurrentHashMap<>();
//    long currentTimestamp = System.currentTimeMillis();
//    long cutoffTimestamp = currentTimestamp - (WINDOW_SIZE_SECONDS * 1000); // 윈도우 시간 범위
//
//    // tradeWindows를 순회하며 오래된 데이터 제거 및 buy/sell volume 계산
//    tradeWindows.forEach((market, trades) -> {
//      // *** [수정] tradeWindows list (CopyOnWriteArrayList)를 사용하는 removeIf
//      trades.removeIf(e -> ((Long)e.get("timestamp")) < cutoffTimestamp); // 오래된 항목 제거
//
//      double buy = 0.0;
//      double sell = 0.0;
//
//      for (Map<String, Object> event : trades) { // 남아있는 최신 데이터로 계산
//        Double amount = (Double) event.get("amount"); // null 체크 추가
//        String type = (String) event.get("type"); // null 체크 추가
//
//        if (amount == null) continue; // amount가 null이면 건너뛰기
//
//        if ("BID".equals(type)) { // 매수
//          buy += amount;
//        } else if ("ASK".equals(type)) { // 매도
//          sell += amount;
//        }
//      }
//
//      double total = buy + sell;
//      if (total > 0) {
//        Map<String, Double> r = new HashMap<>();
//        r.put("buyRatio", buy / total);
//        r.put("sellRatio", sell / total);
//        buySellRatios.put(market, r);
//      }
//    });
//
//    Map<String, CoinResponseDto> filteredMarketData = new HashMap<>();
//
//    // latestTickers에서 CoinResponseDto로 변환하여 filteredMarketData에 추가
//    latestTickers.forEach((marketCode, ticker) -> {
//      // 캔들 볼륨 데이터가 없으면 건너뜀 (아직 캔들 API 호출 안됨)
//      // `fetchCandleRoundRobin` 스케줄러가 느리거나 마켓 코드가 많으면 데이터가 누락될 수 있음
//      if (!latest1MinuteVolume.containsKey(marketCode) ||
//              !latest5MinuteVolume.containsKey(marketCode) ||
//              !latest15MinuteVolume.containsKey(marketCode) ||
//              !latest1HourVolume.containsKey(marketCode)) {
//        // log.debug("Skipping {} due to missing candle volume data.", marketCode); // 필요시 디버그 로그
//        return; // 데이터가 완전히 채워지지 않았으면 스킵
//      }
//
//      double current1mVol = latest1MinuteVolume.getOrDefault(marketCode, 0.0);
//      double current5mVol = latest5MinuteVolume.getOrDefault(marketCode, 0.0);
//
//      // 5분 거래대금이 특정 임계값을 넘는지 필터링 (기존 로직 유지)
//      if (current5mVol < MIN_5MIN_VOLUME_THRESHOLD) {
//        return;
//      }
//
//      // *** [수정] CoinResponseDto 빌더 - 필드명 정확히 매핑 ***
//      CoinResponseDto dto = CoinResponseDto.builder()
//              .symbol(marketCode) // 마켓 코드
//              .price(ticker.getTradePriceNormalized()) // 현재가
//              .volume1m(current1mVol) // 1분봉 거래대금
//              .volume5m(current5mVol) // 5분봉 거래대금
//              .volume15m(latest15MinuteVolume.getOrDefault(marketCode, 0.0)) // 15분봉 거래대금
//              .volume1h(latest1HourVolume.getOrDefault(marketCode, 0.0))   // 1시간봉 거래대금
//              .accTradePrice24h(ticker.getAccTradePrice24hNormalized()) // 24시간 누적 거래대금
//              .change24h(ticker.getSignedChangeRateNormalized() * 100) // 전일대비 (%)
//
//              // 매수/매도 비율은 위에서 계산된 buySellRatios 맵에서 가져와 설정
//              // Gist의 CoinResponseDto에는 buyVolume, sellVolume이 Double 타입으로 정의되어 있음
//              .buyVolume(buySellRatios.getOrDefault(marketCode, Collections.emptyMap()).getOrDefault("buyRatio", 0.0))
//              .sellVolume(buySellRatios.getOrDefault(marketCode, Collections.emptyMap()).getOrDefault("sellRatio", 0.0))
//
//              // Ticker 응답에 timestamp 있다면 사용, 없다면 0L 또는 현재시간 (MarketDataService에 이미 로직있음)
//              .timestamp(ticker.getTradeTimestamp() != null ? ticker.getTradeTimestamp() : System.currentTimeMillis())
//              .build();
//
//      // 필터링된 데이터 맵에 추가
//      filteredMarketData.put(marketCode, dto);
//    });
//
//    if (!filteredMarketData.isEmpty()) {
//      // *** [수정] 프론트엔드로 WebSocket 메시지 전송 - Map 형태로 전송 ***
//      // 프론트엔드의 App.tsx에서 이 Map<String, CoinResponseDto> 형태를 기대합니다.
//      messagingTemplate.convertAndSend("/topic/marketData", filteredMarketData);
//
//      // buySellRatios도 별도 토픽으로 보내거나 CoinResponseDto에 포함 가능
//      messagingTemplate.convertAndSend("/topic/buy-sell-ratio", buySellRatios);
//
//      log.info("✅ WebSocket 데이터 발행 성공 (스케줄러): {}개 코인 데이터", filteredMarketData.size());
//      // 디버깅용: 전송된 데이터 확인
//      // filteredMarketData.values().forEach(dto -> log.info("  -> DTO sent: {}", dto.toString()));
//    }
//  }
//
//  // 즐겨찾기 관련 (기존 로직 유지)
//  public boolean addFavoriteMarket(String marketCode) {
//    return favoriteMarkets.add(marketCode);
//  }
//
//  public boolean removeFavoriteMarket(String marketCode) {
//    return favoriteMarkets.remove(marketCode);
//  }
//
//  public Set<String> getFavoriteMarkets() {
//    return Collections.unmodifiableSet(favoriteMarkets);
//  }
//
//  // MarketDataController.java에서 사용
//  // 필터링된 라이브 시장 데이터 가져오기 (REST API용)
//  // *** [수정] getFilteredLiveMarketData 메소드 (CoinResponseDto 빌더 사용) ***
//  public List<CoinResponseDto> getFilteredLiveMarketData(boolean all, boolean large, boolean mid, boolean small) {
//    // latestTickers에서 CoinResponseDto로 변환
//    List<CoinResponseDto> convertedList = latestTickers.values().stream()
//            .filter(ticker -> latest1MinuteVolume.containsKey(ticker.getMarketCode())) // 캔들 데이터 있는 경우만
//            .map(ticker -> {
//              String marketCode = ticker.getMarketCode();
//              double current1mVol = latest1MinuteVolume.getOrDefault(marketCode, 0.0);
//              double current5mVol = latest5MinuteVolume.getOrDefault(marketCode, 0.0);
//
//              // CoinResponseDto 빌더
//              return CoinResponseDto.builder()
//                      .symbol(marketCode)
//                      .price(ticker.getTradePriceNormalized())
//                      .change24h(ticker.getSignedChangeRateNormalized() * 100)
//                      .accTradePrice24h(ticker.getAccTradePrice24hNormalized() != null ? ticker.getAccTradePrice24hNormalized() : 0.0)
//                      .volume1m(current1mVol)
//                      .volume5m(current5mVol)
//                      .volume15m(latest15MinuteVolume.getOrDefault(marketCode, 0.0))
//                      .volume1h(latest1HourVolume.getOrDefault(marketCode, 0.0))
//                      // 이 부분의 buyVolume/sellVolume은 MarketDataService의 멤버 변수인 buySellRatios를 직접 사용
//                      .buyVolume(buySellRatios.getOrDefault(marketCode, Collections.emptyMap()).getOrDefault("buyRatio", 0.0))
//                      .sellVolume(buySellRatios.getOrDefault(marketCode, Collections.emptyMap()).getOrDefault("sellRatio", 0.0))
//                      .timestamp(ticker.getTradeTimestamp() != null ? ticker.getTradeTimestamp() : System.currentTimeMillis())
//                      .build();
//            })
//            .collect(Collectors.toList());
//
//    List<CoinResponseDto> filtered = new ArrayList<>();
//
//    // *** [기존] 시가총액 필터링 로직 - 코인 심볼 하드코딩 방식 (권장되지 않음) ***
//    // 실제 시가총액 데이터가 없으면 필터링이 제대로 작동하지 않습니다.
//    // CoinResponseDto에 marketCap 필드를 채우는 로직이 백엔드에 있어야 정확한 필터링 가능
//    // 현재는 volume5m으로 필터링하는 로직을 가정합니다.
//    if (all) {
//      filtered.addAll(convertedList);
//    } else {
//      if (large) filtered.addAll(convertedList.stream()
//              .filter(dto -> dto.getAccTradePrice24h() >= 5_000_000_000_000.0) // 24시간 거래대금으로 5조 이상 필터링
//              .collect(Collectors.toList()));
//      if (mid) filtered.addAll(convertedList.stream()
//              .filter(dto -> dto.getAccTradePrice24h() >= 700_000_000_000.0 && dto.getAccTradePrice24h() < 5_000_000_000_000.0) // 7천억~5조
//              .collect(Collectors.toList()));
//      if (small) filtered.addAll(convertedList.stream()
//              .filter(dto -> dto.getAccTradePrice24h() >= 50_000_000_000.0 && dto.getAccTradePrice24h() < 700_000_000_000.0) // 5백억~7천억
//              .collect(Collectors.toList()));
//    }
//    // 필터링 결과에서 중복 제거 및 리턴 (CoinResponseDto의 equals/hashCode 구현 필요)
//    return filtered.stream().distinct().collect(Collectors.toList());
//  }
//
//  // *** [삭제] 불필요한 getMarketData() 메소드 (MarketDataController의 @Scheduled가 아님) ***
//  // public Map<String, Object> getMarketData() throws JsonProcessingException { ... } // 이 메소드는 삭제합니다!
//}

//package coinalarm.Coin_Alarm.market;
//
//import coinalarm.Coin_Alarm.coin.CoinResponseDto;
//import coinalarm.Coin_Alarm.upbit.UpbitClient;
//import coinalarm.Coin_Alarm.upbit.UpbitTickerResponse;
//import coinalarm.Coin_Alarm.upbit.UpbitWSC;
//import coinalarm.Coin_Alarm.upbit.UpbitCandleResponse;
//
//import com.fasterxml.jackson.core.JsonProcessingException;
//import jakarta.annotation.PostConstruct;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.messaging.simp.SimpMessagingTemplate;
//import org.springframework.scheduling.annotation.EnableScheduling;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import java.time.Instant;
//import java.time.ZoneId;
//import java.util.*;
//import java.util.concurrent.ConcurrentHashMap;
//import java.util.concurrent.ConcurrentMap;
//import java.util.stream.Collectors;
//
///**
// * MarketDataService
// * - WebSocket으로 실시간 시세(Trade)를 수신하여 latestTickers 업데이트
// * - 라운드로빈 방식으로 캔들(1m,5m,15m,1h)을 REST API로 가져와 캐싱
// * - 즐겨찾기(favorites) 관리 API용 메서드 제공
// * - pushLatestMarketDataToClients()가 정렬/필터링 후 /topic/market-data 로 푸시
// */
//@Service
//@EnableScheduling
//public class MarketDataService {
//
//  private final UpbitClient upbitClient;
//  private final UpbitWSC upbitWSC;
//  private final SimpMessagingTemplate messagingTemplate;
//
//  // 실시간 티커(시세)
//  private final ConcurrentMap<String, UpbitTickerResponse> latestTickers = new ConcurrentHashMap<>();
//
//  // 캔들 캐시: 1분, 5분, 15분, 1시간 누적 거래대금
//  private final ConcurrentMap<String, Double> latest1MinuteVolume = new ConcurrentHashMap<>();
//  private final ConcurrentMap<String, Double> latest5MinuteVolume = new ConcurrentHashMap<>();
//  private final ConcurrentMap<String, Double> latest15MinuteVolume = new ConcurrentHashMap<>();
//  private final ConcurrentMap<String, Double> latest1HourVolume = new ConcurrentHashMap<>();
//
//  // 슬라이딩 윈도우(매수/매도 비율) 용 실시간 체결 저장
//  private final ConcurrentMap<String, List<Map<String, Object>>> tradeWindows = new ConcurrentHashMap<>();
//  private final long WINDOW_SIZE_SECONDS = 10L; // 슬라이딩 윈도우 길이(초)
//
//  // 알람 쿨타임 관리
//  private final ConcurrentMap<String, Instant> lastAlarmTime = new ConcurrentHashMap<>();
//  private final long ALARM_COOLDOWN_SECONDS = 3L;
//
//  //2025821 add 로그관리위함
//  private static final Logger log = LoggerFactory.getLogger(coinalarm.Coin_Alarm.market.MarketDataService.class);
//
//  // 즐겨찾기 (메모리 기반)
//  private final Set<String> favoriteMarkets = ConcurrentHashMap.newKeySet();
//
//  // 캔들 라운드로빈 인덱스 및 마켓 리스트(초기화 시 getAllKrwMarketCodes 로 채움)
//  private int candleFetchIndex = 0;
//  private volatile List<String> allMarketCodesCache = new ArrayList<>();
//
//  // 5분 기준 필터 임계값 (예시값, 필요시 조정)
//  private final double MIN_5MIN_VOLUME_THRESHOLD = 50_000_000.0; // 예: 5천만 원
//
//  @Autowired
//  public MarketDataService(UpbitClient upbitClient, UpbitWSC upbitWSC, SimpMessagingTemplate messagingTemplate) {
//    this.upbitClient = upbitClient;
//    this.upbitWSC = upbitWSC;
//    this.messagingTemplate = messagingTemplate;
//  }
//
//  @PostConstruct
//  public void init() {
//    // 캐시용 마켓 코드 초기화 (REST에서 한 번)
//    try {
//      List<String> allCodes = upbitClient.getAllKrwMarketCodes();
//      if (allCodes != null && !allCodes.isEmpty()) {
//        allMarketCodesCache = new ArrayList<>(allCodes);
//      }
//    } catch (Exception ignored) {
//    }
//
//    // WebSocket 연결 및 콜백 등록 (실시간 체결 수신)
//    log.info("find error connect "); //2025821 에러문제
//
//    upbitWSC.connect(allMarketCodesCache);
//    upbitWSC.setOnTradeMessageReceived(ticker -> {
//      // normalize market key
//      String marketKey = ticker.getMarketCode(); // DTO의 normalized getter
//      if (marketKey == null) return;
//
//      latestTickers.put(marketKey, ticker);
//
//      // 실시간 체결로 알람 판단 (예: 단일 체결 금액이 1억 이상)
//      double price = ticker.getTradePriceNormalized() != null ? ticker.getTradePriceNormalized() : 0.0;
//      double vol = ticker.getTradeVolumeNormalized() != null ? ticker.getTradeVolumeNormalized() : 0.0;
//      double tradeAmount = price * vol;
//
//      if (tradeAmount >= 100_000_000.0) {
//        Instant now = Instant.now();
//        Instant last = lastAlarmTime.get(marketKey);
//        if (last == null || last.plusSeconds(ALARM_COOLDOWN_SECONDS).isBefore(now)) {
//          String msg = String.format("[%s] %s: %.0f원 대량 체결! (%.4f @ %.0f원)",
//                  Instant.now().atZone(ZoneId.of("Asia/Seoul")).toLocalTime().withNano(0),
//                  marketKey, tradeAmount, vol, price);
//          messagingTemplate.convertAndSend("/topic/alarm-log", msg);
//          lastAlarmTime.put(marketKey, now);
//        }
//      }
//
//      // 슬라이딩 윈도우에 trade event 저장 (매수/매도 구분 포함)
//      Map<String, Object> event = new HashMap<>();
//      event.put("timestamp", System.currentTimeMillis());
//      event.put("amount", tradeAmount);
//      event.put("type", ticker.getAskBid() != null ? ticker.getAskBid() : "UNKNOWN"); // "ASK" or "BID"
//      tradeWindows.computeIfAbsent(marketKey, k -> Collections.synchronizedList(new LinkedList<>())).add(event);
//
//      // 윈도우 오래된 항목 제거
//      long cutoff = System.currentTimeMillis() - WINDOW_SIZE_SECONDS * 1000;
//      List<Map<String, Object>> list = tradeWindows.get(marketKey);
//      if (list != null) {
//        list.removeIf(e -> ((Long) e.get("timestamp")) < cutoff);
//      }
//    });
//  }
//
//  // 즐겨찾기 관리
//  public boolean addFavoriteMarket(String marketCode) { return favoriteMarkets.add(marketCode); }
//  public boolean removeFavoriteMarket(String marketCode) { return favoriteMarkets.remove(marketCode); }
//  public Set<String> getFavoriteMarkets() { return Collections.unmodifiableSet(favoriteMarkets); }
//
//  // 라운드로빈 방식 캔들 수집: 1초에 약 5회 호출(시스템 상황에 맞게 fixedRate 조절)
//  // 주의: Upbit rate limit을 반드시 고려하세요. 필요시 fixedRate를 늘리세요.
//  @Scheduled(fixedRate = 200) // 200ms 마다 한 마켓 처리(설정에 따라 변경)
//  public void fetchCandleRoundRobin() {
//    List<String> codes = allMarketCodesCache;
//    if (codes == null || codes.isEmpty()) return;
//
//    String market = codes.get(candleFetchIndex % codes.size());
//    candleFetchIndex = (candleFetchIndex + 1) % codes.size();
//
//    try {
//      // 1분, 5분, 15분, 60분 캔들(최신 1개) 가져와 캐시
//      List<UpbitCandleResponse> c1 = upbitClient.getMinuteCandles(1, market, 1);
//      if (!c1.isEmpty()) latest1MinuteVolume.put(market, c1.get(0).getCandleAccTradePrice());
//
//      List<UpbitCandleResponse> c5 = upbitClient.getMinuteCandles(5, market, 1);
//      if (!c5.isEmpty()) latest5MinuteVolume.put(market, c5.get(0).getCandleAccTradePrice());
//
//      List<UpbitCandleResponse> c15 = upbitClient.getMinuteCandles(15, market, 1);
//      if (!c15.isEmpty()) latest15MinuteVolume.put(market, c15.get(0).getCandleAccTradePrice());
//
//      List<UpbitCandleResponse> c60 = upbitClient.getMinuteCandles(60, market, 1);
//      if (!c60.isEmpty()) latest1HourVolume.put(market, c60.get(0).getCandleAccTradePrice());
//    } catch (Exception e) {
//      // Rate limit 또는 네트워크 이슈: 무시하거나 로깅
//      //logger.warn("candle fetch failed for {}: {}", market, e.getMessage());
//    }
//  }
//
//  // 초기 데이터(REST 기반) 로딩용
//  public List<CoinResponseDto> getFilteredLiveMarketData(boolean all, boolean large, boolean mid, boolean small) {
//    List<String> allCodes = upbitClient.getAllKrwMarketCodes();
//    List<UpbitTickerResponse> tickers = upbitClient.getTicker(allCodes);
//
//    List<UpbitTickerResponse> filtered = new ArrayList<>();
//    if (all) filtered.addAll(tickers);
//    else {
//      if (large) filtered.addAll(tickers.stream()
//              .filter(t -> {
//                String code = t.getMarketCode();
//                return "KRW-BTC".equals(code) || "KRW-ETH".equals(code);
//              }).collect(Collectors.toList()));
//      if (mid) filtered.addAll(tickers.stream()
//              .filter(t -> {
//                String code = t.getMarketCode();
//                return "KRW-XRP".equals(code) || "KRW-ADA".equals(code) || "KRW-SOL".equals(code);
//              }).collect(Collectors.toList()));
//      if (small) filtered.addAll(tickers.stream()
//              .filter(t -> {
//                String code = t.getMarketCode();
//                return "KRW-DOGE".equals(code) || "KRW-DOT".equals(code) || "KRW-AVAX".equals(code);
//              }).collect(Collectors.toList()));
//    }
//
//    List<UpbitTickerResponse> distinct = filtered.stream().distinct().collect(Collectors.toList());
//    return distinct.stream().map(t -> {
//      String marketCode = t.getMarketCode();
//      if (marketCode == null) marketCode = "UNKNOWN-UNKNOWN";
//      String[] parts = marketCode.split("-");
//      String symbol = parts.length > 1 ? parts[1] : parts[0];
//
//      double v1 = latest1MinuteVolume.getOrDefault(marketCode, 0.0);
//      double v5 = latest5MinuteVolume.getOrDefault(marketCode, 0.0);
//      double v15 = latest15MinuteVolume.getOrDefault(marketCode, 0.0);
//      double v1h = latest1HourVolume.getOrDefault(marketCode, 0.0);
//
//      return new CoinResponseDto(null, marketCode, symbol,
//              t.getTradePriceNormalized(), String.format("%.2f%%", t.getChangeRate() != null ? t.getChangeRate() * 100 : 0.0),
//              t.getAccTradePrice24h(), v1, v5, v15, v1h, Collections.<String>emptyList());
//    }).collect(Collectors.toList());
//  }
//
//  //캔들데이터 push
//  // 주기적 푸시 (1초마다): 즐겨찾기 우선, 그 외는 필터링(예: 5분봉 기준)
//  @Scheduled(fixedRate = 1000)
//  public void pushLatestMarketDataToClients() {
//    if (latestTickers.isEmpty()) return;
//
//    double buyVolume = 0.0; //20250903 Add
//    double sellVolume = 0.0; //20250903 Add
//
//    // 매수/매도 비율 계산
//    Map<String, Map<String, Double>> buySellRatios = new ConcurrentHashMap<>();
//    long cutoff = System.currentTimeMillis() - WINDOW_SIZE_SECONDS * 1000;
//    tradeWindows.forEach((market, list) -> {
//      list.removeIf(e -> ((Long)e.get("timestamp")) < cutoff);
//      double buy = 0, sell = 0;
//      for (Map<String, Object> ev : list) {
//        double amt = (Double) ev.get("amount");
//        String type = (String) ev.get("type");
//        if ("BID".equals(type)) buy += amt;
//        else if ("ASK".equals(type)) sell += amt;
//      }
//      double tot = buy + sell;
//      if (tot > 0) {
//        Map<String, Double> r = new HashMap<>();
//        r.put("buyRatio", buy / tot);
//        r.put("sellRatio", sell / tot);
//        buySellRatios.put(market, r);
//      }
//    });
//
//    // build DTO lists  캔들데이터 가져옴
//    List<CoinResponseDto> favorites = new ArrayList<>();
//    List<CoinResponseDto> normals = new ArrayList<>();
//
//    for (String key : new ArrayList<>(latestTickers.keySet())) {
//      UpbitTickerResponse t = latestTickers.get(key);
//      if (t == null) continue;
//      String mc = t.getMarketCode();
//      if (mc == null) continue;
//
//      //이부분은 -단위로 나눈다음에 심볼부분만 가져오기위해서? 확인해보기
//      String sym = mc.contains("-") ? mc.split("-")[1] : mc;
//
//      double v1 = latest1MinuteVolume.getOrDefault(mc, 0.0);
//      double v5 = latest5MinuteVolume.getOrDefault(mc, 0.0);
//      double v15 = latest15MinuteVolume.getOrDefault(mc, 0.0);
//      double v1h = latest1HourVolume.getOrDefault(mc, 0.0);
//
////20250829 bulider 사용으로 명시화하여 직관적이게 수정
////      CoinResponseDto dto = new CoinResponseDto(null, mc, sym, t.getTradePriceNormalized(),
////              String.format("%.2f%%", t.getChangeRate() != null ? t.getChangeRate() * 100 : 0.0),
////              t.getAccTradePrice24h(), v1, v5, v15, v1h, List.of());
//
//      //20250829 수정 str
//      // CoinResponseDto 빌드: 프론트엔드의 Coin 타입 필드명과 일치시켜 값을 할당합니다.
//      CoinResponseDto dto = CoinResponseDto.builder()
//              .symbol(mc) // Market Code (예: KRW-BTC)
//              .price(t.getTradePriceNormalized()) // 현재가
//              .change24h(t.getSignedChangeRateNormalized() * 100) // 전일대비 (%)
//              .accTradePrice24h(t.getAccTradePrice24hNormalized()) // 24시간 누적 거래대금 (UpbitTickerResponse에서 가져옴)
//              .volume1m(v1) // latest1MinuteVolume에서 가져온 1분봉 값
//              .volume5m(v5) // latest5MinuteVolume에서 가져온 5분봉 값
//              .volume15m(v15) // latest15MinuteVolume에서 가져온 15분봉 값
//              .volume1h(v1h)   // latest1HourVolume에서 가져온 1시간봉 값
//              .buyVolume(0.0)  // 매수/매도 거래대금 (tradeWindows에서 계산)
//              .sellVolume(0.0)
//              .build();
//
//      // 디버깅용: DTO가 프론트엔드로 보내지기 전 최종 값 확인
//      log.info("DEBUG_DTO_SENT: {}", dto.toString()); // CoinResponseDto에 @ToString (Lombok) 추가 필요!
//      //20250829 수정 End
//
//      if (favoriteMarkets.contains(mc)) favorites.add(dto);
//      else normals.add(dto);
//    }
//
//    // favorites first (sorted by v1), then normals filtered by 5min threshold and sorted
//    favorites.sort(Comparator.comparing(CoinResponseDto::getVolume1m).reversed());
//    List<CoinResponseDto> filteredNormals = normals.stream()
//            .filter(c -> c.getVolume5m() >= MIN_5MIN_VOLUME_THRESHOLD)
//            .sorted(Comparator.comparing(CoinResponseDto::getVolume1m).reversed())
//            .collect(Collectors.toList());
//
//    List<CoinResponseDto> all = new ArrayList<>();
//    all.addAll(favorites);
//    all.addAll(filteredNormals);
//
//    //messagingTemplate.convertAndSend("/topic/market-data", all);
//    simpMessagingTemplate.convertAndSend("/marketData", marketData); //20250825 ADD
//    messagingTemplate.convertAndSend("/topic/buy-sell-ratio", buySellRatios);
//  }
//
//  //20250826 데이터가공
//  // !!!! 아래 getMarketData 메소드를 MarketDataService.java에 추가하거나 수정하세요 !!!!
//  // 이 메소드가 MarketDataController에서 호출됩니다.
//  // 기존 MarketDataService에 있는 getTicker 등을 활용하여 데이터 조합
////  public Map<String, Object> getMarketData() throws JsonProcessingException {
////    // 모든 KRW 마켓 코드 가져오기 (한번만 가져와 캐싱해두면 효율적)
////    // 여기서는 예시로 제한된 마켓 코드 사용
////    List<String> marketCodes = upbitClient.getAllKrwMarketCodes();
////    if (marketCodes.isEmpty()) {
////      marketCodes = List.of("KRW-BTC", "KRW-ETH", "KRW-XRP"); // 폴백
////    }
////
////    // Upbit REST API에서 티커 정보 가져오기
////    List<UpbitTickerResponse> tickers = upbitClient.getTicker(marketCodes);
////
////    // 프론트엔드 App.tsx의 Coin 타입에 맞게 데이터 가공
////    Map<String, Object> processedData = new HashMap<>();
////    for (UpbitTickerResponse ticker : tickers) {
////      Map<String, Object> coinData = new HashMap<>();
////      coinData.put("symbol", ticker.getMarket());
////      coinData.put("price", ticker.getTradePrice());
////      coinData.put("volume1m", ticker.getAccTradePrice24h()); // 임시로 24h 누적거래대금 사용 (1분봉 정보는 다른 API 필요)
////      coinData.put("change24h", ticker.getSignedChangeRate() * 100); // %로 변환
////
////      // 매수/매도 거래대금은 현재 ticker API에 없으므로 임시 값 또는 0
////      coinData.put("buyVolume", 0);
////      coinData.put("sellVolume", 0);
////
////      processedData.put(ticker.getMarket(), coinData);
////    }
////    return processedData;
////  }
//
//
//}