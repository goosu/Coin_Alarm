// src/main/java/coinalarm/Coin_Alarm/coin/Coin.java
package coin;
// Jakarta Persistence API (JPA) 관련 임포트 (Spring Boot 3.x부터 'jakarta' 사용)
// JPA는 자바 객체와 관계형 데이터베이스 간의 매핑을 위한 표준 명세입니다.
import jakarta.persistence.ElementCollection; // 컬렉션 필드를 별도 테이블로 저장할 때 사용
import jakarta.persistence.Entity; // 이 클래스가 JPA 엔티티임을 나타냅니다. 데이터베이스 테이블과 매핑됩니다.
import jakarta.persistence.GeneratedValue; // 기본 키의 값을 자동으로 생성할 때 사용합니다.
import jakarta.persistence.GenerationType; // 기본 키 자동 생성 전략을 지정합니다.
import jakarta.persistence.Id; // 이 필드가 엔티티의 기본 키(Primary Key)임을 나타냅니다.

import java.util.List; // 알람 목록을 저장하기 위한 List 타입

@Entity // @Entity 어노테이션을 붙여서 이 클래스가 JPA 엔티티임을 선언합니다.
public class Coin {

  @Id // @Id 어노테이션을 붙여서 'id' 필드를 기본 키로 지정합니다.
  @GeneratedValue(strategy = GenerationType.IDENTITY) // @GeneratedValue: 기본 키 값을 자동으로 생성합니다.
  // GenerationType.IDENTITY: 데이터베이스의 자동 증가(AUTO_INCREMENT) 기능을 사용합니다. (MySQL, H2 등)
  private Long id; // 코인의 고유 식별자입니다. 데이터베이스 테이블의 Primary Key 컬럼과 매핑됩니다.

  private String name; // 코인 이름 (예: Bitcoin). 데이터베이스 테이블의 컬럼과 매핑됩니다.
  private Long marketCap; // 시가총액 (예: 800000000000). 데이터베이스 테이블의 컬럼과 매핑됩니다.
  private String priceChange; // 가격 변동률 (예: "+2.3%"). 데이터베이스 테이블의 컬럼과 매핑됩니다.
  private Long volume; // 거래대금 (예: 20000000000). 데이터베이스 테이블의 컬럼과 매핑됩니다.

  @ElementCollection // @ElementCollection: 자바 객체의 컬렉션 타입 필드를 데이터베이스에 저장할 때 사용합니다.
  // 이 경우, 'alarm' 리스트의 각 문자열이 별도의 보조 테이블에 저장됩니다.
  private List<String> alarm; // 현재 코인에 해당하는 알람 목록입니다.

  // JPA를 사용하려면 기본 생성자(인자가 없는 생성자)가 필수입니다.
  // JPA 구현체(Hibernate)가 리플렉션을 사용하여 객체를 생성할 때 이 생성자를 사용합니다.
  public Coin() {
  }

  // 코인 객체를 생성할 때 사용할 생성자입니다. (데이터를 초기화할 때 편리합니다.)
  public Coin(String name, Long marketCap, String priceChange, Long volume, List<String> alarm) {
    this.name = name;
    this.marketCap = marketCap;
    this.priceChange = priceChange;
    this.volume = volume;
    this.alarm = alarm;
  }

  // 각 필드의 값을 읽어오기 위한 Getter 메서드들입니다.
  // Spring이나 다른 프레임워크에서 객체의 데이터를 가져갈 때 사용합니다.
  public Long getId() { return id; }
  public String getName() { return name; }
  public Long getMarketCap() { return marketCap; }
  public String getPriceChange() { return priceChange; }
  public Long getVolume() { return volume; }
  public List<String> getAlarm() { return alarm; }

  // 필요하다면 필드의 값을 설정하기 위한 Setter 메서드들도 추가할 수 있습니다.
  // public void setName(String name) { this.name = name; }
  // public void setMarketCap(Long marketCap) { this.marketCap = marketCap; }
  // ...
}
