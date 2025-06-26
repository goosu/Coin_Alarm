package coin;// src/main/java/coinalarm/Coin_Alarm/coin/CoinResponseDto.java

// ... (다른 임포트들)
import coin.Coin;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Getter
@NoArgsConstructor // <-- 이 어노테이션이 인자 없는 생성자를 만들어 줍니다.
@AllArgsConstructor // <-- 이 어노테이션이 모든 필드를 인자로 받는 생성자를 만들어 줍니다.
public class CoinResponseDto {
  // ... (필드들)

  // 엔티티(Coin) 객체를 DTO(CoinResponseDto) 객체로 변환하는 정적 팩토리 메서드는 그대로 두세요.
  // 이 메서드는 Lombok이 생성하는 생성자와는 다릅니다.
//  public static CoinResponseDto fromEntity(Coin coin) {
//    return new CoinResponseDto(
//            coin.getId(),
//            coin.getName(),
//            coin.getMarketCap(),
//            coin.getPriceChange(),
//            coin.getVolume(),
//            coin.getAlarm()
//    );
//  }

  // Lombok 어노테이션을 사용했으므로, 아래의 수동 작성된 생성자는 삭제해야 합니다.
  // 이 부분이 에러의 원인입니다.

    public CoinResponseDto() { // <-- 이 부분을 삭제하세요.
    }

    public CoinResponseDto(Long id, String name, Long marketCap, String priceChange, Long volume, List<String> alarm) { // <-- 이 부분을 삭제하세요.
//        this.id = id;
//        this.name = name;
//        this.marketCap = marketCap;
//        this.priceChange = priceChange;
//        this.volume = volume;
//        this.alarm = alarm;
    }

}
