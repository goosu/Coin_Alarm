// src/main/java/coinalarm/Coin_Alarm/coin/CoinService.java
//package coin; // <-- 실제 프로젝트 패키지 이름 + 하위 패키지
package coin;

import coin.CoinDao;
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입을 위해 필요합니다.
import org.springframework.stereotype.Service; // 이 클래스가 Spring의 Service 컴포넌트임을 명시합니다. Spring Bean으로 등록됩니다.
import org.springframework.transaction.annotation.Transactional; // 트랜잭션 관리를 위해 필요합니다.

import java.util.ArrayList; // 결과를 담을 List 구현체
import java.util.List; // 결과를 List 형태로 반환

@Service // @Service 어노테이션을 붙여서 Spring이 이 클래스를 서비스 계층의 컴포넌트로 인식하게 합니다.
@Transactional // @Transactional 어노테이션을 붙여서 이 클래스의 모든 public 메서드가 트랜잭션 안에서 실행되도록 합니다.
// 서비스 계층에서 트랜잭션을 관리하는 것이 일반적입니다. 여러 DAO 호출이 하나의 작업으로 묶여야 할 때 유용합니다.
public class CoinService {

  // CoinDao 인터페이스 타입의 필드를 선언합니다.
  // Spring이 @Autowired를 통해 CoinDao 인터페이스의 구현체(CoinDaoImpl) 객체를 자동으로 주입해 줄 것입니다. (의존성 주입 - Dependency Injection)
  private final CoinDao coinDao;

  // 생성자 주입: Spring이 CoinDao 객체를 생성자 인자로 넘겨주면서 이 Service 객체를 생성합니다.
  // @Autowired 어노테이션은 생성자가 하나만 있을 때는 생략 가능하지만, 명시적으로 붙여주는 것이 좋습니다.
  @Autowired
  public CoinService(CoinDao coinDao) {
    this.coinDao = coinDao; // 주입받은 CoinDao 객체를 필드에 할당합니다.
  }

  /**
   * 시가총액 필터 조건에 따라 코인 목록을 조회하는 비즈니스 로직 메서드입니다.
   * Controller로부터 필터 조건을 받아 CoinDao를 호출하여 데이터를 가져옵니다.
   *
   * @param large 대형 코인 포함 여부 (true/false)
   * @param mid 중형 코인 포함 여부 (true/false)
   * @param small 소형 코인 포함 여부 (true/false)
   * @return 필터링된 코인 목록 (List<Coin>)
   */
  public List<coin.Coin> getFilteredCoins(boolean large, boolean mid, boolean small) {
    List<coin.Coin> filteredCoins = new ArrayList<>(); // 필터링된 코인을 담을 빈 리스트를 생성합니다.

    // 시가총액 기준 정의 (이전 코드와 동일한 예시 기준 사용)
    // 대형: 5조 원 (5e12) 이상
    // 중형: 7천억 원 (7e11) 이상 ~ 5조 원 (5e12) 미만
    // 소형: 5백억 원 (5e10) 이상 ~ 7천억 원 (7e11) 미만

    if (large) {
      // large가 true이면 대형 코인(5조 원 이상)을 조회합니다.
      // CoinDao의 findByMarketCapBetween 메서드를 호출하여 결과를 받아와 filteredCoins 리스트에 추가합니다.
      // Long.MAX_VALUE는 Long 타입이 가질 수 있는 가장 큰 값입니다.
      filteredCoins.addAll(coinDao.findByMarketCapBetween(5_000_000_000_000L, Long.MAX_VALUE));
    }
    if (mid) {
      // mid가 true이면 중형 코인(7천억 원 이상 ~ 5조 원 미만)을 조회합니다.
      filteredCoins.addAll(coinDao.findByMarketCapBetween(700_000_000_000L, 4_999_999_999_999L));
    }
    if (small) {
      // small이 true이면 소형 코인(5백억 원 이상 ~ 7천억 원 미만)을 조회합니다.
      filteredCoins.addAll(coinDao.findByMarketCapBetween(50_000_000_000L, 699_999_999_699L));
    }

    // TODO: 만약 여러 조건에 해당하는 코인이 중복될 수 있다면, Set 등을 사용하여 중복을 제거하는 로직을 추가해야 합니다.
    // 현재 예시 데이터와 시가총액 범위에서는 중복이 발생하지 않습니다.

    return filteredCoins; // 최종 필터링된 코인 목록을 반환합니다.
  }

  /**
   * 애플리케이션 시작 시 데이터베이스에 초기 코인 데이터를 저장하는 메서드입니다.
   * 이 메서드는 메인 애플리케이션 클래스에서 호출됩니다.
   */
  public void saveInitialCoins() {
    // 초기 데이터로 사용할 코인 객체 리스트를 생성합니다. (이전 코드의 allCoins 데이터)
    List<Coin> initialCoins = new ArrayList<>();
    initialCoins.add(new Coin("Bitcoin", 800_000_000_000L, "+2.3%", 20_000_000_000L, List.of("미체결", "1분거래대금10억")));
    initialCoins.add(new Coin("Ethereum", 300_000_000_000L, "-1.1%", 10_000_000_000L, List.of("1회체결3억")));
    initialCoins.add(new Coin("SmallCoin", 50_000_000_000L, "+0.5%", 30_000_000L, List.of())); // 시가총액 500억으로 수정 (소형 범위에 맞게)
    initialCoins.add(new Coin("LargeCoinExample", 6_000_000_000_000L, "+1.0%", 50_000_000_000L, List.of("대형코인알람"))); // 대형 예시 추가
    initialCoins.add(new Coin("MidCoinExample", 1_000_000_000_000L, "-0.5%", 15_000_000_000L, List.of("중형코인알람"))); // 중형 예시 추가

    // 반복문을 사용하여 각 코인 객체를 CoinDao를 통해 데이터베이스에 저장합니다.
    for (Coin coin : initialCoins) {
      coinDao.save(coin); // CoinDao의 save 메서드 호출
    }
  }

  // TODO: 나중에 필요하다면 다른 비즈니스 로직 메서드들을 여기에 추가할 수 있습니다.
  // 예: 특정 코인의 상세 정보를 가져오는 메서드, 알람 조건을 업데이트하는 메서드 등
}
