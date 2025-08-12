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
public class UpbitTickerResponse {

  @JsonProperty("ty")
  private String ty;

  @JsonProperty("cd")
  private String code; // WebSocket에서 주로 사용하는 마켓코드 필드

  @JsonProperty("market")
  private String market; // REST API에서 오는 필드

  @JsonProperty("tp")
  private Double tradePrice; // WS field tp

  @JsonProperty("trade_price")
  private Double tradePriceRest; // REST field

  @JsonProperty("tv")
  private Double tradeVolume; // WS field tv

  @JsonProperty("trade_volume")
  private Double tradeVolumeRest; // REST field

  @JsonProperty("change_rate")
  private Double changeRate;

  @JsonProperty("acc_trade_price_24h")
  private Double accTradePrice24h;

  @JsonProperty("ab")
  private String askBid; // ASK/BID

  @JsonProperty("tms")
  private Long tms;

  // Normalized getters
  public Double getTradePriceNormalized() {
    return tradePrice != null ? tradePrice : tradePriceRest;
  }
  public Double getTradeVolumeNormalized() {
    return tradeVolume != null ? tradeVolume : tradeVolumeRest;
  }
  public String getMarketCode() {
    if (code != null && !code.isEmpty()) return code;
    if (market != null && !market.isEmpty()) return market;
    return null;
  }
}