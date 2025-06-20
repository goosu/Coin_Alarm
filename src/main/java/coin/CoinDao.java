// src/main/java/coinalarm/Coin_Alarm/coin/CoinDao.java
package coin; // <-- 실제 프로젝트 패키지 이름 + 하위 패키지

import java.util.List; // 여러 개의 코인 객체를 반환할 때 사용
import java.util.Optional; // 단일 객체를 찾을 때, 결과가 없을 수도 있으므로 Optional로 감싸서 반환합니다. (널 안전성 확보)

// JpaRepository를 상속받지 않고, 순수하게 우리가 필요한 데이터 접근 메서드만 정의합니다.
public interface CoinDao {

  // 코인 객체를 데이터베이스에 저장하거나 업데이트하는 메서드입니다.
  Coin save(Coin coin);

  // 주어진 ID를 가진 코인 객체를 데이터베이스에서 찾아 반환하는 메서드입니다.
  Optional<Coin> findById(Long id);

  // 데이터베이스에 저장된 모든 코인 객체를 리스트 형태로 반환하는 메서드입니다.
  List<Coin> findAll();

  // 주어진 시가총액 범위(minMarketCap ~ maxMarketCap)에 해당하는 코인 목록을 찾아 반환하는 메서드입니다.
  List<Coin> findByMarketCapBetween(Long minMarketCap, Long maxMarketCap);

  // 주어진 코인 객체를 데이터베이스에서 삭제하는 메서드입니다.
  void delete(Coin coin);

  // 주어진 ID를 가진 코인 객체를 데이터베이스에서 찾아 삭제하는 메서드입니다.
  void deleteById(Long id);

  // TODO: 나중에 필요하다면 다른 데이터 접근 메서드들을 여기에 추가할 수 있습니다.
  // 예: 이름으로 코인 찾기, 거래대금 순으로 정렬하기 등
}
