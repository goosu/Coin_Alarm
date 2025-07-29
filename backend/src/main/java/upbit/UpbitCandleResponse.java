// backend/src/main/java/coinalarm/Coin_Alarm/upbit/UpbitCandleResponse.java
package coinalarm.Coin_Alarm.upbit; // <-- 정확한 패키지 경로

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonProperty;

// Upbit API의 Minute Candle, Hour Candle 응답을 매핑할 DTO입니다.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpbitCandleResponse {

  @JsonProperty("market")
  private String market; // 마켓 코드 (예: KRW-BTC)

  @JsonProperty("candle_date_time_kst") // KST 기준 캔들 기준 시각
  private String candleDateTimeKst;

  @JsonProperty("opening_price")
  private Double openingPrice; // 시가

  @JsonProperty("high_price")
  private Double highPrice; // 고가

  @JsonProperty("low_price")
  private Double lowPrice; // 저가

  @JsonProperty("trade_price")
  private Double tradePrice; // 종가

  @JsonProperty("candle_acc_trade_price") // 해당 캔들(1분, 15분, 1시간)의 누적 거래 금액 (거래대금)
  private Double candleAccTradePrice;

  @JsonProperty("candle_acc_volume") // 해당 캔들(1분, 15분, 1시간)의 누적 거래량 (코인 개수)
  private Double candleAccVolume;

  // 필요한 경우 다른 필드를 추가할 수 있습니다.
  // Upbit API 문서: https://docs.upbit.com/reference/%EB%B6%84minute-%EC%BA%94%EB%93%B1-1
}