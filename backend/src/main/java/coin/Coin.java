// src/main/java/coinalarm/Coin_Alarm/coin/Coin.java
package coin; // <-- 이 패키지 선언이 정확해야 합니다.

// Jakarta Persistence API (JPA) 관련 임포트 (Spring Boot 3.x부터 'jakarta' 사용)
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import java.util.List; // java.util.List 임포트

import lombok.Getter; // Lombok 어노테이션
import lombok.NoArgsConstructor; // Lombok 어노테이션
import lombok.AllArgsConstructor; // Lombok 어노테이션
import lombok.Setter; // Lombok 어노테이션

@Entity // 이 클래스가 JPA 엔티티임을 선언합니다.
@Getter // @Getter 어노테이션으로 모든 필드의 Getter 메서드를 자동으로 생성합니다.
@Setter // @Setter 어노테이션으로 모든 필드의 Setter 메서드를 자동으로 생성합니다.
@NoArgsConstructor // @NoArgsConstructor 어노테이션으로 기본 생성자를 자동으로 생성합니다.
@AllArgsConstructor // @AllArgsConstructor 어노테이션으로 모든 필드를 인자로 받는 생성자를 자동으로 생성합니다.
public class Coin { // <-- 클래스 시작 괄호

  @Id // @Id 어노테이션을 붙여서 'id' 필드를 기본 키로 지정합니다.
  @GeneratedValue(strategy = GenerationType.IDENTITY) // @GeneratedValue: 기본 키 값을 자동으로 생성합니다.
  private Long id; // 코인의 고유 식별자입니다.

  private String name; // 코인 이름
  private Long marketCap; // 시가총액
  private String priceChange; // 가격 변동률
  private Long volume; // 거래대금

  @ElementCollection // @ElementCollection: 자바 객체의 컬렉션 타입 필드를 데이터베이스에 저장할 때 사용합니다.
  private List<String> alarm; // 현재 코인에 해당하는 알람 목록입니다.

  // --- 추가할 생성자 ---
  // ID 필드를 제외한 나머지 필드들만 인자로 받는 생성자를 수동으로 추가합니다.
  // CoinService에서 new Coin(...) 호출 시 이 생성자를 사용하게 됩니다.
  public Coin(String name, Long marketCap, String priceChange, Long volume, List<String> alarm) {
    this.name = name;
    this.marketCap = marketCap;
    this.priceChange = priceChange;
    this.volume = volume;
    this.alarm = alarm;
  }
  // --- 추가할 생성자 끝 ---

  // Lombok 어노테이션을 사용했으므로, 수동으로 작성된 생성자나 Getter/Setter는 모두 삭제되어야 합니다.
} // <-- 클래스 끝 괄호
