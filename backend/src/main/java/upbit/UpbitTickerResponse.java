// backend/src/main/java/coinalarm/Coin_Alarm/upbit/UpbitTickerResponse.java
package coinalarm.Coin_Alarm.upbit; // <-- 정확한 패키지 경로

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;
import com.fasterxml.jackson.annotation.JsonProperty; // JSON 필드명을 매핑하기 위해 필요

// Upbit API의 ticker(시세) 응답을 매핑할 DTO입니다.
// 필요한 필드만 정의했습니다.
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UpbitTickerResponse {

  @JsonProperty("market") // JSON의 'market' 필드를 자바의 'market'에 매핑
  private String market; // 마켓 코드 (예: KRW-BTC)

  @JsonProperty("trade_price") // JSON의 'trade_price' 필드를 'tradePrice'에 매핑
  private Double tradePrice; // 현재가

//  @JsonProperty("trade_volume") // JSON의 'trade_volume' 필드를 'tradeVolume'에 매핑
//  private Double tradeVolume; // 최근 24시간 거래량
  @JsonProperty("acc_trade_price_24h") // JSON의 'acc_trade_price_24h' 필드를 'accTradePrice24h'에 매핑
  private Double accTradePrice24h; // 24시간 누적 거래 가격

  @JsonProperty("change_rate") // JSON의 'change_rate' 필드를 'changeRate'에 매핑
  private Double changeRate; // 24시간 대비 변화율 (0.005 = 0.5%)

  // 필요한 경우 다른 필드를 추가할 수 있습니다. (예: `high_price`, `low_price` 등)
  // Upbit API 문서: https://docs.upbit.com/reference/시세-조회
}