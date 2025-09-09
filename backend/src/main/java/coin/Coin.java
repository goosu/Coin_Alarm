package coinalarm.Coin_Alarm.coin;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Getter;
import lombok.NoArgsConstructor; // JPA 필요: 인자 없는 기본 생성자 자동 생성
import lombok.Setter;
import lombok.ToString;

import java.util.List;

@Entity // 이 클래스가 JPA 엔티티임을 명시
@Getter // Lombok: 모든 필드의 Getter 메소드 자동 생성
@Setter // Lombok: 모든 필드의 Setter 메소드 자동 생성
@ToString // Lombok: toString() 메소드 자동 생성 (객체 디버깅용)
@NoArgsConstructor // JPA 사용 시 인자 없는 기본 생성자가 필수입니다.
public class Coin {
  @Id // 기본 키(Primary Key)임을 명시
  @GeneratedValue(strategy = GenerationType.IDENTITY) // 기본 키 생성 전략 (데이터베이스가 ID 자동 생성)
  private Long id; // 코인의 고유 식별자

  // *** [추가] CoinService의 saveInitialCoins() 메소드와 일치하는 필드 순서와 타입을 지킵니다. ***
  private String name;   // 코인의 한글 이름 (예: 비트코인) - CoinService의 "Bitcoin" 위치
  private Long marketCap; // 시가총액 - CoinService의 800_000_000_000L 위치
  private String priceChange; // 가격 변동률 (예: "+2.3%") - CoinService의 "+2.3%" 위치
  private Long tradeVolume; // 거래량 - CoinService의 20_000_000_000L 위치

  @ElementCollection // 컬렉션 타입 필드를 엔티티에 매핑 (알람 메시지 리스트)
  private List<String> alarm; // 알람 메시지 리스트 - CoinService의 List.of(...) 위치

  // *** [추가] symbol 필드 (DB 저장용) ***
  private String symbol; // <-- [새롭게 추가] 코인 심볼 (예: KRW-BTC) - 나중에 CoinService의 saveInitialCoins()에 추가할 예정

  // *** [추가] CoinService의 saveInitialCoins() 메소드와 정확히 일치하는 생성자를 명시적으로 정의합니다. ***
  // 이 생성자에는 @GeneratedValue로 자동 생성되는 'id' 필드를 포함하지 않습니다.
  // 'symbol' 필드를 추가했으므로, CoinService의 new Coin() 호출 시에도 symbol을 마지막 인자로 전달할 것입니다.
  public Coin(String name, Long marketCap, String priceChange, Long tradeVolume, List<String> alarm, String symbol) {
    this.name = name;
    this.marketCap = marketCap;
    this.priceChange = priceChange;
    this.tradeVolume = tradeVolume;
    this.alarm = alarm;
    this.symbol = symbol; // <-- [추가] symbol 필드 초기화
  }
}