package coinalarm.Coin_Alarm.coin; // 이 패키지 경로가 정확한지 확인하세요!

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString; // <-- Lombok @ToString 어노테이션을 위해 필요합니다.

/**
 * 프론트엔드(App.tsx)로 전송될 코인 데이터 응답 객체
 * MarketDataService에서 WebSocket을 통해 전송되는 데이터의 포맷을 정의합니다.
 * Lombok 어노테이션으로 Getter, Setter, Builder, ToString을 자동으로 생성합니다.
 */
@Getter // 모든 필드의 Getter 메소드를 컴파일 시 자동 생성
@Setter // 모든 필드의 Setter 메소드를 컴파일 시 자동 생성
@Builder // 빌더 패턴을 사용하여 객체 생성 (CoinResponseDto.builder()...)
@ToString // 객체의 필드 값을 포함하는 toString() 메소드 자동 생성 (로그, 디버깅 용이)
public class CoinResponseDto {

  private String symbol;      // 코인 심볼 (예: KRW-BTC) - App.tsx의 symbol, MarketDataService에서 mc
  private Double price;       // 현재가 - App.tsx의 price, MarketDataService에서 t.getTradePriceNormalized()
  private Double volume1m;    // 1분봉 거래대금 - App.tsx의 volume1m
  /*Rest API에 volume5m, volume15m, volume1h,일봉 필드가 있음*/
  private Double volume5m;    // 5분봉 거래대금 - App.tsx의 volume5m
  private Double volume15m;   // 15분봉 거래대금 - App.tsx의 volume15m
  private Double volume1h;    // 1시간봉 거래대금 - App.tsx의 volume1h

  // Upbit Ticker API의 acc_trade_price_24h 필드와 매핑되며,
  // 프론트엔드 App.tsx에서는 'volume24h'로 사용됩니다.
  private Double accTradePrice24h; // 24시간 누적 거래대금

  // Upbit Ticker API의 signed_change_rate를 백엔드에서 100 곱해 %로 변환한 값
  private Double change24h;   // 전일대비 (%)

  // 매수/매도 거래대금 (MarketDataService에서 계산되나 현재는 0으로 전달될 수 있음)
  private Double buyVolume;
  private Double sellVolume;

  // Upbit Ticker API의 trade_timestamp 또는 데이터 발생 시점의 타임스탬프 (밀리초)
  private Long timestamp;

  //20250917 임시 *** [신규 추가] 즐겨찾기 여부 필드 ***
  private Boolean isFavorite; // 해당 코인이 즐겨찾기인지 여부

}