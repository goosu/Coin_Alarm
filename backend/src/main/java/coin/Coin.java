// backend/src/main/java/coinalarm/Coin_Alarm/coin/Coin.java
//package coin; // <-- 코인 관련 클래스들의 패키지
package coinalarm.Coin_Alarm.coin;
// Jakarta Persistence API (JPA) 관련 임포트 (Spring Boot 3.x부터 'jakarta' 사용)
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.List; // List 타입을 사용하기 위한 임포트

// Lombok 어노테이션: Getter, Setter, 생성자 등을 자동으로 생성하여 코드를 간결하게 만듭니다.
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter;

@Entity // 이 클래스가 JPA 엔티티임을 선언합니다. 데이터베이스 테이블과 매핑됩니다.
@Getter // @Getter 어노테이션으로 모든 필드의 Getter 메서드를 자동으로 생성합니다.
@Setter // @Setter 어노테이션으로 모든 필드의 Setter 메서드를 자동으로 생성합니다.
@NoArgsConstructor // @NoArgsConstructor 어노테이션으로 인자 없는 기본 생성자를 자동으로 생성합니다.
@AllArgsConstructor // @AllArgsConstructor 어노테이션으로 모든 필드를 인자로 받는 생성자를 자동으로 생성합니다.
public class Coin {

  @Id // 'id' 필드를 기본 키로 지정합니다.
  @GeneratedValue(strategy = GenerationType.IDENTITY) // 기본 키 값을 데이터베이스의 자동 증가 기능으로 생성합니다.
  private Long id; // 코인의 고유 식별자 (Primary Key)

  private String name; // 코인 이름
  private Long marketCap; // 시가총액
  private String priceChange; // 가격 변동률
  private Long volume; // 거래대금

  @ElementCollection // 'alarm' 리스트를 별도의 보조 테이블로 저장합니다.
  private List<String> alarm; // 현재 코인에 해당하는 알람 목록

  // --- 추가된 생성자 ---
  // CoinService에서 초기 데이터를 생성할 때 ID 필드를 제외한 나머지 필드들만 인자로 받기 위해 수동으로 추가합니다.
  // Lombok의 @AllArgsConstructor는 모든 필드(id 포함)를 인자로 받는 생성자만 생성합니다.
  public Coin(String name, Long marketCap, String priceChange, Long volume, List<String> alarm) {
    this.name = name;
    this.marketCap = marketCap;
    this.priceChange = priceChange;
    this.volume = volume;
    this.alarm = alarm;
  }
  // --- 추가된 생성자 끝 ---
}

