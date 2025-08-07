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

  @JsonProperty("ty") // WebSocket Trade/Ticker 메시지의 "ty" 필드
  private String ty;

  @JsonProperty("cd") // WebSocket Trade/Ticker 메시지의 "cd" 필드 (마켓 코드)
  private String code;

  @JsonProperty("market") // <-- REST API Ticker 응답용 (getMarket()을 위해 다시 살림!)
  private String market;

  @JsonProperty("tp") // WebSocket Trade 메시지의 "tp" 필드 (체결 가격)
  private Double tradePrice;

  @JsonProperty("tv") // WebSocket Trade 메시지의 "tv" 필드 (체결량)
  private Double tradeVolume;

  @JsonProperty("change_rate") // REST/WebSocket Ticker 메시지 (변동률)
  private Double changeRate;

  @JsonProperty("acc_trade_price_24h") // REST API Ticker 응답용 (24H 누적 거래대금)
  private Double accTradePrice24h;

  @JsonProperty("trade_timestamp") // WebSocket Trade 메시지
  private Long tradeTimestamp;

  @JsonProperty("seq") // WebSocket Trade/Ticker 메시지
  private Long seq;

  @JsonProperty("tms") // WebSocket Trade 메시지
  private Long tms;
}