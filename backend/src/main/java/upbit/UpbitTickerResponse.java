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
  private String code; // WebSocket 'cd' 또는 일부 응답에서 사용

  @JsonProperty("market")
  private String market; // REST API 'market' 필드

  @JsonProperty("tp")
  private Double tradePrice; // WebSocket 'tp'

  @JsonProperty("trade_price")
  private Double tradePriceRest; // REST 'trade_price'

  @JsonProperty("tv")
  private Double tradeVolume; // WebSocket 'tv'

  @JsonProperty("trade_volume")
  private Double tradeVolumeRest; // REST 'trade_volume'

  @JsonProperty("change_rate")
  private Double changeRate;

  @JsonProperty("acc_trade_price_24h")
  private Double accTradePrice24h;

  @JsonProperty("ab")
  private String askBid; // "ASK" or "BID"

  @JsonProperty("tms")
  private Long tms;

  // normalized getters for safety
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

  public String getAskBid() {
    return askBid;
  }
}