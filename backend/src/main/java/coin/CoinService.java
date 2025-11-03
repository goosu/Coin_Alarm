//package coinalarm.Coin_Alarm.coin;
//
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.stereotype.Service;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.ArrayList;
//import java.util.List;
//
//@Service
//@Transactional
//public class CoinService {
//
//  private final CoinDao coinDao;
//
//  @Autowired
//  public CoinService(CoinDao coinDao) {
//    this.coinDao = coinDao;
//  }
//
//  /**
//   * 시가총액 필터 조건에 따라 코인 목록을 조회하는 비즈니스 로직 메서드입니다.
//   */
//  public List<Coin> getFilteredCoins(boolean large, boolean mid, boolean small) {
//    List<Coin> filteredCoins = new ArrayList<>();
//
//    // 시가총액 기준 정의
//    // 대형: 5조 원 (5e12) 이상
//    // 중형: 7천억 원 (7e11) 이상 ~ 5조 원 (5e12) 미만
//    // 소형: 5백억 원 (5e10) 이상 ~ 7천억 원 (7e11) 미만
//
//    if (large) {
//      filteredCoins.addAll(coinDao.findByMarketCapBetween(5_000_000_000_000L, Long.MAX_VALUE));
//    }
//    if (mid) {
//      filteredCoins.addAll(coinDao.findByMarketCapBetween(700_000_000_000L, 4_999_999_999_999L));
//    }
//    if (small) {
//      filteredCoins.addAll(coinDao.findByMarketCapBetween(50_000_000_000L, 699_999_999_699L));
//    }
//
//    return filteredCoins;
//  }
//
//  /**
//   * 애플리케이션 시작 시 데이터베이스에 초기 코인 데이터를 저장하는 메서드입니다.
//   * Coin 엔티티에 symbol 필드를 추가함에 따라 saveInitialCoins()에서도 해당 필드를 추가합니다.
//   */
//  public void saveInitialCoins() {
//    List<Coin> initialCoins = new ArrayList<>();
//    // Coin 객체 생성 시, Coin.java에 정의된 생성자의 인자 순서와 타입을 정확히 맞춥니다.
//    // 기존 데이터에 적절한 symbol을 할당합니다.
//    initialCoins.add(new Coin("Bitcoin", 800_000_000_000L, "+2.3%", 20_000_000_000L, List.of("미체결", "1분거래대금10억"), "KRW-BTC")); // <-- [수정] symbol 추가
//    initialCoins.add(new Coin("Ethereum", 300_000_000_000L, "-1.1%", 10_000_000_000L, List.of("1회체결3억"), "KRW-ETH")); // <-- [수정] symbol 추가
//    initialCoins.add(new Coin("SmallCoin", 50_000_000_000L, "+0.5%", 30_000_000L, List.of(), "KRW-XRP")); // <-- [수정] symbol 추가 (예시)
//    initialCoins.add(new Coin("LargeCoinExample", 6_000_000_000_000L, "+1.0%", 50_000_000_000L, List.of("대형코인알람"), "KRW-SOL")); // <-- [수정] symbol 추가 (예시)
//    initialCoins.add(new Coin("MidCoinExample", 1_000_000_000_000L, "-0.5%", 15_000_000_000L, List.of("중형코인알람"), "KRW-DOGE")); // <-- [수정] symbol 추가 (예시)
//
//    // 각 코인 객체를 CoinDao를 통해 데이터베이스에 저장합니다.
//    for (Coin coin : initialCoins) {
//      coinDao.save(coin);
//    }
//  }
//}