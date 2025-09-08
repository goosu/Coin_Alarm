package coinalarm.Coin_Alarm.upbit;

import coinalarm.Coin_Alarm.upbit.UpbitCandleResponse; // UpbitCandleResponse 임포트
import coinalarm.Coin_Alarm.upbit.UpbitMarketResponse; // UpbitMarketResponse 임포트
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono; // Mono 임포트

import java.util.List;
import java.util.stream.Collectors; // Collectors 임포트

@Component
public class UpbitClient {

  private final WebClient webClient;

  public UpbitClient(WebClient.Builder webClientBuilder) {
    this.webClient = webClientBuilder.baseUrl("https://api.upbit.com/v1").build();
  }

  // 모든 KRW 마켓 코드 목록을 가져오는 메소드
  public List<String> getAllKrwMarketCodes() {
    return webClient.get()
            .uri("/market/all")
            .retrieve()
            .bodyToFlux(UpbitMarketResponse.class)
            .filter(market -> market.getMarket().startsWith("KRW-"))
            .map(UpbitMarketResponse::getMarket)
            .collectList()
            .block();
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
}