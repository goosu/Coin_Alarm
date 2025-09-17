package coinalarm.Coin_Alarm.upbit; // 이 패키지 경로가 정확한지 확인하세요!

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties; // <-- [추가]
/**
 * Upbit REST API /v1/ticker 응답 JSON을 Java 객체로 매핑하는 DTO
 * Lombok 어노테이션을 사용하여 Getter, Setter, toString()을 자동 생성합니다.
 */
@Getter // 모든 필드의 Getter 메소드를 자동 생성
@Setter // 모든 필드의 Setter 메소드를 자동 생성
@ToString // toString() 메소드를 자동 생성
@JsonIgnoreProperties(ignoreUnknown = true)
public class UpbitTickerResponse {

  // 20250905 Upbit에서 받아오는 type형태 추가(무시해도 된다고 안함) STR
  @JsonProperty("type")
  private String type; // Upbit 웹소켓 메시지 타입 (예: "ticker", "trade")
  // 20250905 Upbit에서 받아오는 type형태 추가(무시해도 된다고 안함) END

//  @JsonProperty("market") market => code 업비트 API
  @JsonProperty("code")
  private String market; // 마켓명 (예: KRW-BTC)

  @JsonProperty("trade_date")
  private String tradeDate; // 최근 거래 일자 (UTC)

  @JsonProperty("trade_time")
  private String tradeTime; // 최근 거래 시각 (UTC)

  @JsonProperty("trade_date_kst")
  private String tradeDateKst; // 최근 거래 일자 (KST)

  @JsonProperty("trade_time_kst")
  private String tradeTimeKst; // 최근 거래 시각 (KST)

  @JsonProperty("trade_timestamp")
  private Long tradeTimestamp; // 최근 거래 일시 (Unix Time Stamp, milliseconds)

  @JsonProperty("opening_price")
  private Double openingPrice; // 시가

  @JsonProperty("high_price")
  private Double highPrice; // 고가

  @JsonProperty("low_price")
  private Double lowPrice; // 저가

  @JsonProperty("trade_price")
  private Double tradePrice; // 현재가 (종가)

  @JsonProperty("prev_closing_price")
  private Double prevClosingPrice; // 전일 종가

  @JsonProperty("change")
  private String change; // 전일 대비 (RISE, FALL, EVEN)

  @JsonProperty("change_price")
  private Double changePrice; // 전일 대비 값

  @JsonProperty("change_rate")
  private Double changeRate; // 전일 대비 등락률

  @JsonProperty("signed_change_price")
  private Double signedChangePrice; // 부호 있는 전일 대비 값 (절대값은 changePrice와 동일)

  @JsonProperty("signed_change_rate")
  private Double signedChangeRate; // 부호 있는 전일 대비 등락률 (절대값은 changeRate와 동일)

  @JsonProperty("trade_volume")
  private Double tradeVolume; // 가장 최근 거래량

  @JsonProperty("acc_trade_price_24h")
  private Double accTradePrice24h; // 24시간 누적 거래대금

  @JsonProperty("acc_trade_volume_24h")
  private Double accTradeVolume24h; // 24시간 누적 거래량

  @JsonProperty("acc_trade_price")
  private Double accTradePrice; // 누적 거래대금 (UTC 기준)

  @JsonProperty("acc_trade_volume")
  private Double accTradeVolume; // 누적 거래량 (UTC 기준)

  @JsonProperty("highest_52_week_price")
  private Double highest52WeekPrice; // 52주 신고가

  @JsonProperty("highest_52_week_date")
  private String highest52WeekDate; // 52주 신고가 달성일 (YYYY-MM-DD)

  @JsonProperty("lowest_52_week_price")
  private Double lowest52WeekPrice; // 52주 신저가

  @JsonProperty("lowest_52_week_date")
  private String lowest52WeekDate; // 52주 신저가 달성일 (YYYY-MM-DD)

  @JsonProperty("timestamp")
  private Long timestamp; // 타임스탬프 (밀리초)

  // *** [중요] getAskBid() 메소드 추가 (MarketDataService에서 호출되고 있었음) ***
  // Ticker API에는 직접적인 "AskBid" 필드가 없으므로 null을 반환하거나, 필요에 따라 구현
  public String getAskBid() {
    return null; // Ticker API에서는 Ask/Bid 구분을 직접 제공하지 않음
  }

  // *** [중요] MarketDataService에서 호출하는 "Normalized" Getter들 (추가됨) ***
  // UpbitClient, MarketDataService에서 필요한 'Normalized' 값들을 위한 헬퍼 메소드
  // Lombok의 @Getter가 이들을 자동 생성할 수도 있으나, 명시적으로 선언하여 명확히 합니다.
  public String getMarketCode() { return market; }
  public Double getTradePrice() { return tradePrice; }
  public Double getSignedChangeRate() { return signedChangeRate; }
  public Double getAccTradePrice24h() { return accTradePrice24h; }
  public Double getTradeVolumeNormalized() { return tradeVolume; }
}