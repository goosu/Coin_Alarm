// backend/src/main/java/coinalarm/Coin_Alarm/coin/CoinDao.java
//package coin; // <-- 코인 관련 클래스들의 패키지
package coinalarm.Coin_Alarm.coin;

import java.util.List; // List 타입을 사용하기 위한 임포트
import java.util.Optional; // Optional 타입을 사용하기 위한 임포트

// JpaRepository를 상속받지 않고, 순수하게 우리가 필요한 데이터 접근 메서드만 정의하는 인터페이스입니다.
public interface CoinDao {

  // 코인 객체를 데이터베이스에 저장하거나 업데이트하는 메서드
  Coin save(Coin coin);

  // 주어진 ID를 가진 코인 객체를 데이터베이스에서 찾아 반환하는 메서드
  Optional<Coin> findById(Long id);

  // 데이터베이스에 저장된 모든 코인 객체를 리스트 형태로 반환하는 메서드
  List<Coin> findAll();

  // 주어진 시가총액 범위에 해당하는 코인 목록을 찾아 반환하는 메서드
  List<Coin> findByMarketCapBetween(Long minMarketCap, Long maxMarketCap);

  // 주어진 코인 객체를 데이터베이스에서 삭제하는 메서드
  void delete(Coin coin);

  // 주어진 ID를 가진 코인 객체를 데이터베이스에서 찾아 삭제하는 메서드
  void deleteById(Long id);

  // TODO: 나중에 필요하다면 다른 데이터 접근 메서드들을 여기에 정의할 수 있습니다.
}
