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
  private String code;

  @JsonProperty("market")
  private String market;

  @JsonProperty("tp")
  private Double tradePrice;

  @JsonProperty("trade_price")
  private Double tradePriceRest;

  @JsonProperty("tv")
  private Double tradeVolume;

  @JsonProperty("trade_volume")
  private Double tradeVolumeRest;

  @JsonProperty("change_rate")
  private Double changeRate;

  @JsonProperty("acc_trade_price_24h")
  private Double accTradePrice24h;

  @JsonProperty("ab")
  private String askBid;

  @JsonProperty("tms")
  private Long tms;

  // 안전화된 getter들
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

  public String getAskBid() { return askBid; }
}