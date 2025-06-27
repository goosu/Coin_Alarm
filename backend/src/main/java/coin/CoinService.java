// backend/src/main/java/coinalarm/Coin_Alarm/coin/CoinService.java
package coin; // <-- 코인 관련 클래스들의 패키지

import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입을 위해 필요
import org.springframework.stereotype.Service; // Spring의 Service 컴포넌트임을 명시
import org.springframework.transaction.annotation.Transactional; // 트랜잭션 관리를 위해 필요

import java.util.ArrayList; // 결과를 담을 List 구현체
import java.util.List; // 결과를 List 형태로 반환

@Service // Spring Bean으로 등록됩니다.
@Transactional // 이 클래스의 모든 public 메서드가 트랜잭션 안에서 실행되도록 설정합니다.
public class CoinService {

  // CoinDao 인터페이스 타입의 필드를 선언하고 Spring으로부터 주입받습니다.
  private final CoinDao coinDao;

  @Autowired // 생성자 주입
  public CoinService(CoinDao coinDao) {
    this.coinDao = coinDao;
  }

  /**
   * 시가총액 필터 조건에 따라 코인 목록을 조회하는 비즈니스 로직 메서드입니다.
   */
  public List<Coin> getFilteredCoins(boolean large, boolean mid, boolean small) {
    List<Coin> filteredCoins = new ArrayList<>();

    // 시가총액 기준 정의
    // 대형: 5조 원 (5e12) 이상
    // 중형: 7천억 원 (7e11) 이상 ~ 5조 원 (5e12) 미만
    // 소형: 5백억 원 (5e10) 이상 ~ 7천억 원 (7e11) 미만

    if (large) {
      filteredCoins.addAll(coinDao.findByMarketCapBetween(5_000_000_000_000L, Long.MAX_VALUE));
    }
    if (mid) {
      filteredCoins.addAll(coinDao.findByMarketCapBetween(700_000_000_000L, 4_999_999_999_999L));
    }
    if (small) {
      filteredCoins.addAll(coinDao.findByMarketCapBetween(50_000_000_000L, 699_999_999_699L));
    }

    return filteredCoins;
  }

  /**
   * 애플리케이션 시작 시 데이터베이스에 초기 코인 데이터를 저장하는 메서드입니다.
   */
  public void saveInitialCoins() {
    List<Coin> initialCoins = new ArrayList<>();
    // Coin 객체 생성 시, Coin.java에 추가한 ID를 제외한 생성자를 사용합니다.
    initialCoins.add(new Coin("Bitcoin", 800_000_000_000L, "+2.3%", 20_000_000_000L, List.of("미체결", "1분거래대금10억")));
    initialCoins.add(new Coin("Ethereum", 300_000_000_000L, "-1.1%", 10_000_000_000L, List.of("1회체결3억")));
    // SmallCoin의 marketCap을 500억으로 수정하여 필터 조건에 맞도록 합니다.
    initialCoins.add(new Coin("SmallCoin", 50_000_000_000L, "+0.5%", 30_000_000L, List.of()));
    initialCoins.add(new Coin("LargeCoinExample", 6_000_000_000_000L, "+1.0%", 50_000_000_000L, List.of("대형코인알람")));
    initialCoins.add(new Coin("MidCoinExample", 1_000_000_000_000L, "-0.5%", 15_000_000_000L, List.of("중형코인알람")));

    // 각 코인 객체를 CoinDao를 통해 데이터베이스에 저장합니다.
    for (Coin coin : initialCoins) {
      coinDao.save(coin);
    }
  }
}
