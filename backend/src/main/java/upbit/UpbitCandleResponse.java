package coinalarm.Coin_Alarm.upbit;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpbitCandleResponse {

  @JsonProperty("market")
  private String market;

  @JsonProperty("candle_date_time_utc")
  private String candleDateTimeUtc;

  @JsonProperty("candle_date_time_kst")
  private String candleDateTimeKst;

  @JsonProperty("opening_price")
  private Double openingPrice;

  @JsonProperty("high_price")
  private Double highPrice;

  @JsonProperty("low_price")
  private Double lowPrice;

  @JsonProperty("trade_price")
  private Double tradePrice;

  @JsonProperty("timestamp")
  private Long timestamp;

  @JsonProperty("candle_acc_trade_price")
  private Double candleAccTradePrice; // 누적 거래대금 (원화)

  @JsonProperty("candle_acc_trade_volume")
  private Double candleAccTradeVolume; // 누적 거래량 (코인 단위)
}