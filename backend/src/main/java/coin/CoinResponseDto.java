package coin;// src/main/java/coinalarm/Coin_Alarm/coin/CoinResponseDto.java
//package coinalarm.Coin_Alarm.coin;

import coin.Coin;
import lombok.Getter; // Lombok 어노테이션: 모든 필드에 대한 Getter 메서드를 자동으로 생성합니다.
import lombok.NoArgsConstructor; // Lombok 어노테이션: 인자 없는 기본 생성자를 자동으로 생성합니다.
import lombok.AllArgsConstructor; // Lombok 어노테이션: 모든 필드를 인자로 받는 생성자를 자동으로 생성합니다.

import java.util.List;

// Coin 엔티티의 데이터를 프론트엔드로 전송하기 위한 DTO (Data Transfer Object) 클래스입니다.
// 엔티티를 직접 노출하지 않고 DTO를 사용함으로써, 데이터 계약을 명확히 하고 보안 및 유연성을 높입니다.
@Getter // @Getter 어노테이션으로 모든 필드의 Getter 메서드를 자동으로 생성합니다.
@NoArgsConstructor // @NoArgsConstructor 어노테이션으로 기본 생성자를 자동으로 생성합니다.
@AllArgsConstructor // @AllArgsConstructor 어노테이션으로 모든 필드를 인자로 받는 생성자를 자동으로 생성합니다.
public class CoinResponseDto {
  private Long id; // 코인 고유 식별자
  private String name; // 코인 이름
  private Long marketCap; // 시가총액
  private String priceChange; // 가격 변동률
  private Long volume; // 거래대금
  private List<String> alarm; // 알람 목록

  public CoinResponseDto(Long id, String name, Long marketCap, String priceChange, Long volume, List<String> alarm) {
  }

  // 엔티티(Coin) 객체를 DTO(CoinResponseDto) 객체로 변환하는 정적 팩토리 메서드입니다.
  // 이렇게 하면 변환 로직이 DTO 클래스 내부에 캡슐화되어 관리하기 용이합니다.
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
