package exchange;

import lombok.Builder;
import lombok.Data;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.Instant;
import java.util.List;


public class ExchangeClient {

  /*거래소 고유 식별자 반환
    업비트, 바이낸스 현물, 바이낸스 선물 등등*/

  String getExchangeId();

  /*해당 거래소의 모든 거래 가능한 마켓코드 조회*/
  Mono<List<String>> getAllMarketCodes();

  /*WebSocket으로 실시간 Ticker 데이터 스트림 구독*/
  Flux<TickerSnapshot> subscribeTickerStream(List<String> marketCodes);

  /*REST API로 과거 캔들데이터 조회(즐겨찾기 추가시 스냅샷 버퍼를 과거 데이터로 채우기위함*/
  Mono<List<CandleData>> getHistoricalCandles(
          String marketCode,
          int minutes,
          int count
  );

  /*시가총액 정보조회*/
  Mono<MarketCapInfo> getMarketCap(String marketCode);
}

//실시간 Ticker 스냅샷

@Data
@Builder
public class TickerSnapshot{
  private String exchangeId; //거래소ID
  private String marketCode; //거래페어
  private Instant timestamp; //스냅샷 생성 시간
  private Double currentPrice; //현재가
  private Double rolling24hVolume; //코인게코에서 가져오게 변경할 예정
}

@Data
@Builder
public class CandleData{
  //캔들데이터
  private String marketCode;
  private Instant timestamp;
  private Double openPrice;
  private Double closePrice;
  private Double highPrice;
  private Double lowPrice;
  private Double accTradePrice;
  private double accTradeVolume;
}

@Data
@Builder
public class MarketCapInfo{
  private String marketCode;
  private Double marketCap;
  private MarketCapTier tier; //아 이런식으로 코딩하는게 있구나 C랑 비슷하네 구조체
}
public enum MarketCapTier{
  MEGA(20_000_000_000_000L, "15조 이상"),
  LARGE(1_000_000_000_000L, "1조 이상"),
  MEDIUM(0L, "1조 미만");

  private final long threshold;
  private final String description;

  MarketCapTier(long threshold, String description){
    this.threshold = threshold;
    this.description = description;
  }

  public static MarketCapTier fromMarketCap(double marketCap) {
   if(marketCap >= MEGA.threshold) return MEGA;
   if(marketCap >= LARGE.threshold) return LARGE;
   return MEDIUM;
   //일부러 순서도 이렇게 한이유 큰값-> 작은값
  }

  public long getThreshold(){
    return threshold;
  }

  public String getDescription(){
    return description;
  }
}


