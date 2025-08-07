// backend/src/main/java/coinalarm/Coin_Alarm/upbit/UpbitTickerResponse.java
package coinalarm.Coin_Alarm.upbit;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // <--- 이 임포트 추가

/**
 * Upbit API의 실시간 시세 (Ticker) 및 체결 (Trade) 응답을 매핑하기 위한 DTO.
 * WebSocket 메시지의 경우, 알 수 없는 필드를 무시하도록 설정합니다.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true) // <--- 이 어노테이션 추가: 알 수 없는 필드는 무시하도록 설정
public class UpbitTickerResponse {

  @JsonProperty("ty") // 메시지 타입 (예: "ticker", "trade")
  private String ty;

  @JsonProperty("cd") // 마켓 코드 (예: KRW-BTC)
  private String code; // <--- 이 필드 추가: WebSocket trade 메시지에서 "cd"로 마켓 코드 전달됨

  @JsonProperty("market") // Rest API 용 필드
  private String market;

  @JsonProperty("trade_price")
  private Double tradePrice;

  @JsonProperty("trade_volume")
  private Double tradeVolume;

  @JsonProperty("change_rate")
  private Double changeRate;

  @JsonProperty("acc_trade_price_24h")
  private Double accTradePrice24h;

  // 추가: 체결 시간 정보 (WebSocket Trade 메시지에서 자주 사용됨)
  @JsonProperty("trade_timestamp")
  private Long tradeTimestamp;

  @JsonProperty("seq") // 체결 일련번호
  private Long seq;

  @JsonProperty("tp") // 가격 변화량
  private String tp;

  @JsonProperty("tms") // 체결 타임스탬프 (ms)
  private Long tms;
}