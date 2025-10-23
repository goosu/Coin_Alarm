package coinalarm.Coin_Alarm.upbit;

import coinalarm.Coin_Alarm.upbit.UpbitCandleResponse; // UpbitCandleResponse 임포트
import coinalarm.Coin_Alarm.upbit.UpbitMarketResponse; // UpbitMarketResponse 임포트
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono; // Mono 임포트

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors; // Collectors 임포트

import static coinalarm.Coin_Alarm.AccessingDataJpaApplication.log;

@Component
public class UpbitClient implements ExchangeClient {

  private final WebClient webClient;
  private final Map<String, Double> dailyVolumeCache = new ConcurrentHashMap<>();

  /*20251022 DEL STR*/
//  public UpbitClient(WebClient.Builder webClientBuilder) {
//    this.webClient = webClientBuilder.baseUrl("https://api.upbit.com/v1").build();
//  }
  /*20251022 DEL END*/

  /*20251022 ADD STR*/
  public UpbitClient(WebClient.Builder webClientBuilder, UpbitWSC upbitWSC) {
    this.webClient = webClientBuilder.baseUrl("https://api.upbit.com/v1").build();
    this.upbitWSC = upbitWSC;
  }

  @Override
  public String getExchangeId(){
    return "UPBIT";
  }
  /*20251022 ADD END*/
  // 모든 KRW 마켓 코드 목록을 가져오는 메소드
  public List<String> getAllKrwMarketCodes() {
    return webClient.get()
            .uri("/market/all")
            .retrieve()
            .bodyToFlux(UpbitMarketResponse.class)
            .filter(market -> market.getMarket().startsWith("KRW-"))
            .map(UpbitMarketResponse::getMarket)
            .collectList()
            .onErrorResume(e -> {
              log.error("Error fetching all market codes: {}", e.getMessage());
              return Mono.just(Collections.emptyList());
            })
            .block();
  }
  @Override
  public Flux<TickerSnapshot> seubscribeTickerStream(List<String> marketCodes) {
    //WebSocket Ticker 스트림을 TickerSnapshot으로 변환
    return Flux.create(sink->{
      upbitWSC.sonnect(marketCodes);
      upbitWSC.setOnTradeMessageReceived(ticker -> {
        //UpbitTickerResponse를 TickerSnapshot으로 변환
        TickerSnapshot snapshot = TickerSnapshot.builder()
                .exchangeId(getExchangeId())
                .marketCode(ticker.getMarketCode())
                .timestamp(Instant.now())
                .currentPrice(ticker.getTradePriceNormalized())
                .rolling24hVolume(ticker.getAccTradePrice24h())
                .build();
        sink.next(snapshot); //Flux로 데이터 발행
      });
    });
  }


  // [변경] getMinuteCandles 메소드의 시그니처를 MarketDataService에서 호출하는 것과 일치시킵니다.
  // market(String), unit(int, 분 단위: 1, 5, 15, 60), count(int, 가져올 개수)를 인자로 받습니다.
  public Mono<List<UpbitCandleResponse>> getMinuteCandles(String market, int unit, int count) {
    return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/candles/minutes/{unit}") // 경로 변수 {unit} 사용
                    .queryParam("market", market) // 쿼리 파라미터 market
                    .queryParam("count", count)   // 쿼리 파라미터 count
                    .build(unit)) // {unit} 경로 변수에 실제 unit 값 바인딩
            .retrieve()
            .bodyToFlux(UpbitCandleResponse.class)
            .collectList();
  }

  public Mono<List<UpbitCandleResponse>> getDayCandles(String market, int count){
    return webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/candles/days") // 경로 변수 {unit} 사용
                    .queryParam("market", market) // 쿼리 파라미터 market
                    .queryParam("count", count)   // 쿼리 파라미터 count
                    .build()) // {unit} 경로 변수에 실제 unit 값 바인딩
            .retrieve()
            .bodyToFlux(UpbitCandleResponse.class)
            .collectList();
  }


  @Override
  public Mono<List<CandleData>> getHistoricalCandles(String marketCode, int minutes, int count){
    String path = (minutes == 1440) ? "/candles/days" : "/candles/minutes/" + minutes;

      return webClient.get()
              .uri(uriBuilder -> uriBuilder.path(path) // 경로 변수 {unit} 사용
                      .queryParam("market", market) // 쿼리 파라미터 market
                      .queryParam("count", count)   // 쿼리 파라미터 count
                      .build()) // {unit} 경로 변수에 실제 unit 값 바인딩
              .retrieve()
              .bodyToFlux(UpbitCandleResponse.class)
              .map(this::convertToCandleData)  // UpbitCandleResponse → CandleData 변환
              .collectList();
  }
//20251023 *** [신규 추가] 시가총액 STR***
  public Mono<List<CandleData>> getMarketCap(String marketCode){
    retrun webClient.get()
            .uri(uriBuilder -> uriBuilder.path("/ticker")
                    .queryParam("markets", marketCode)
                    .build())
            .retrieve()
            .bodyToFlux(UpbitTickerResponse.class)
            .next()
            .map(ticker -> {
              //임시로 거래대금 기준으로 등급판정
              double mockMarketCap = ticker.getAccTradePrice24h() * 100; //임시사용

              return MarketCapInfo.builder()
                      .marketCode(marketCode)
                      .marketCap(mockMarketCap)
                      .tier(MarketCapTier.fromMarketCap(mockMarketCap))
                      .build();
            })
  }
//20251023 *** [신규 추가] 시가총액 END***

//20251023 *** [신규 추가] 거래소ID를 공통 CandleData로 변환 STR***
  private CandleData convertToCandleData(UpbitCandleResponse upbitCandle){
      return CandleData.builder()
              .exchangeId(getExchangeId())
              .marketCode(upbitCandle.getMarketCode())
              .timestamp(Instant.parse(upbitCandle.getCandleDateTimeUtc()+"Z"))
              .openPrice(upbitCandle.getOpeningPrice())
              .highPrice(upbitCandle.getHighPrice())
              .lowPrice(upbitCandle.getLowPrice())
              .closePrice(upbitCandle.getTradePrice())
              .volume(upbitCandle.getCandleAccTradeVolume())
              .build();
  }
//20251023 *** [신규 추가] 거래소ID를 공통 CandleData로 변환 END***
  //20250918 *** [신규 추가] 즐겨찾기 코인들의 일봉 거래대금 배치 수집 (비동기 처리) STR***
  public Mono<Void> updateDailyVolumesForFavorites(List<String> favoriteMarkets) {
    if (favoriteMarkets.isEmpty()) {
      return Mono.empty();
    }
    // 각 마켓에 대해 일봉 데이터를 비동기로 수집
    List<Mono<Void>> tasks = favoriteMarkets.stream()
            .map(market -> getDayCandles(market, 1)
                    .delayElement(Duration.ofMillis(100)) // Rate limit 방지를 위한 100ms 딜레이
                    .doOnNext(dailyCandles -> {
                      if (!dailyCandles.isEmpty()) {
                        double dailyVolume = dailyCandles.get(0).getCandleAccTradePrice();
                        dailyVolumeCache.put(market, dailyVolume);
                        System.out.println("일봉 데이터 업데이트: " + market + " = " + dailyVolume);
                      }
                    })
                    .doOnError(error ->
                            System.err.println("일봉 업데이트 실패 " + market + ": " + error.getMessage()))
                    .onErrorResume(error -> Mono.empty()) // 에러 발생 시 빈 Mono 반환
                    .then())
            .collect(Collectors.toList());

    // 모든 작업을 병렬로 실행하되 순차적으로 처리 (Rate limit 고려)
    return Flux.concat(tasks).then();
  }
  //20250918 *** [신규 추가] 즐겨찾기 코인들의 일봉 거래대금 배치 수집 (비동기 처리) END ***
  // *** [신규 추가] 캐시된 일봉 거래대금 조회 ***
  public Double getDailyVolumeFromCache(String market) {
    return dailyVolumeCache.get(market);
  }
}