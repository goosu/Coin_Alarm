package coin;// src/main/java/coinalarm/Coin_Alarm/coin/CoinResponseDto.java
//package coinalarm.Coin_Alarm.coin; // <-- 이 줄을 정확하게 덮어씌우세요.

import coin.Coin;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CoinResponseDto {
  private Long id;
  private String name;
  private Long marketCap;
  private String priceChange;
  private Long volume;
  private List<String> alarm;

  public static CoinResponseDto fromEntity(Coin coin) {
    return new CoinResponseDto(
            coin.getId(),
            coin.getName(),
            coin.getMarketCap(),
            coin.getPriceChange(),
            coin.getVolume(),
            coin.getAlarm()
    );
  }
}
