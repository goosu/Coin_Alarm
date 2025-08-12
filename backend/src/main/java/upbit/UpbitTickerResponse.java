// backend/src/main/java/coinalarm/Coin_Alarm/upbit/UpbitTickerResponse.java
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

  // WebSocket 메시지 타입 (예: "trade", "ticker" 등) - 일부 메시지에 포함될 수 있음
  @JsonProperty("ty")
  private String ty;

  // WebSocket: 'cd' 필드에 마켓코드가 오는 경우가 많음 (KRW-BTC 등)
  @JsonProperty("cd")
  private String code;

  // REST API /ticker 에서는 'market' 필드가 있음 -> REST와 호환되게 유지
  @JsonProperty("market")
  private String market;

  // WebSocket trade 메시지에서 체결가 필드명: tp (trade price) 또는 trade_price (REST)
  @JsonProperty("tp")
  private Double tradePrice;

  @JsonProperty("trade_price")
  private Double tradePriceFromRest; // REST 응답용 보조필드 (optional)

  // WebSocket trade 메시지에서 체결량 필드명: tv (trade volume) 또는 trade_volume (REST)
  @JsonProperty("tv")
  private Double tradeVolume;

  @JsonProperty("trade_volume")
  private Double tradeVolumeFromRest; // REST 응답용 보조필드

  // 24H 누적 거래대금 (REST ticker에서 제공)
  @JsonProperty("acc_trade_price_24h")
  private Double accTradePrice24h;

  @JsonProperty("change_rate")
  private Double changeRate;

  // WebSocket timestamps / seq 등
  @JsonProperty("tms")
  private Long tms;

  @JsonProperty("trade_timestamp")
  private Long tradeTimestamp;

  @JsonProperty("seq")
  private Long seq;

  // 매수/매도 구분(ASK/BID) - WebSocket trade에 있을 수 있음
  @JsonProperty("ab")
  private String askBid;

  // Helper getters to normalize between WS/REST field names
  public Double getTradePriceNormalized() {
    if (tradePrice != null) return tradePrice;
    return tradePriceFromRest;
  }

  public Double getTradeVolumeNormalized() {
    if (tradeVolume != null) return tradeVolume;
    return tradeVolumeFromRest;
  }

  // Provide a unified market code getter (code preferred, fallback to market)
  public String getMarketCode() {
    if (code != null && !code.isEmpty()) return code;
    if (market != null && !market.isEmpty()) return market;
    return null;
  }
}