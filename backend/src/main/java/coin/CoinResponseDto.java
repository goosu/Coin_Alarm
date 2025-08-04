// backend/src/main/java/coinalarm/Coin_Alarm/coin/CoinResponseDto.java
package coinalarm.Coin_Alarm.coin;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter; // Setter 어노테이션 임포트 추가 (필요한 경우)

import java.util.List; // java.util.List 임포트 필요!

@Getter
@Setter // MarketDataService에서 값을 설정해야 하므로 @Setter 추가
@NoArgsConstructor
@AllArgsConstructor
public class CoinResponseDto {
  private Long id;
  private String name;
  private String symbol;
  private Double currentPrice;
  private String priceChange;
  private Double volume; // 24H 거래대금 (총 누적 거래대금)

  private Double volume1m;  // 1분봉 거래대금
  private Double volume15m; // 15분봉 거래대금
  private Double volume1h;  // 1시간봉 거래대금

  private List<String> alarm;

  /**
   * Coin 엔티티를 CoinResponseDto로 변환하는 정적 팩토리 메서드.
   * 주로 DB에서 가져온 Coin 데이터를 프론트엔드에 전달할 때 사용됩니다.
   * Coin 엔티티에 없는 필드(예: currentPrice, volume, 캔들 거래대금)는 0.0 등의 기본값으로 초기화됩니다.
   */
  public static CoinResponseDto fromEntity(Coin coin) {
    // 이 List.of()를 사용하려면 상단에 import java.util.List; 가 있어야 합니다.
    return new CoinResponseDto(
            coin.getId(),                                     // 1. id
            coin.getName(),                                   // 2. name
            // 3. symbol: 'KRW-BTC' -> 'BTC' (임시 추출)
            coin.getName().split("-").length > 1 ? coin.getName().split("-")[1] : coin.getName(),
            0.0,                                              // 4. currentPrice (Coin 엔티티에는 이 필드 없음)
            coin.getPriceChange(),                            // 5. priceChange
            0.0,                                              // 6. volume (24H 거래대금, Coin 엔티티에는 이 필드 없음)
            0.0,                                              // 7. volume1m (Coin 엔티티에는 이 필드 없음)
            0.0,                                              // 8. volume15m (Coin 엔티티에는 이 필드 없음)
            0.0,                                              // 9. volume1h (Coin 엔티티에는 이 필드 없음)
            coin.getAlarm()                                   // 10. alarm (List<String>)
    );
  }
}