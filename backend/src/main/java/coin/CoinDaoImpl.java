// backend/src/main/java/coinalarm/Coin_Alarm/coin/CoinDaoImpl.java
//package coin; // <-- 코인 관련 클래스들의 패키지
package coinalarm.Coin_Alarm.coin;

import coinalarm.Coin_Alarm.coin.CoinDao;

import org.springframework.stereotype.Repository; // Spring의 Repository 컴포넌트임을 명시
import org.springframework.transaction.annotation.Transactional; // 트랜잭션 관리를 위해 필요

// Jakarta Persistence API (JPA) 관련 임포트
import jakarta.persistence.EntityManager; // JPA의 핵심 객체
import jakarta.persistence.PersistenceContext; // EntityManager 주입
import jakarta.persistence.TypedQuery; // 타입이 지정된 JPQL 쿼리 생성

import java.util.List; // List 타입을 사용하기 위한 임포트
import java.util.Optional; // Optional 타입을 사용하기 위한 임포트

@Repository // 이 클래스가 Spring의 Repository(DAO) 역할을 함을 명시합니다. Spring Bean으로 등록됩니다.
@Transactional // 이 클래스의 모든 public 메서드가 트랜잭션 안에서 실행되도록 설정합니다.
public class CoinDaoImpl implements CoinDao { // CoinDao 인터페이스를 구현합니다.

  @PersistenceContext // Spring으로부터 EntityManager 객체를 주입받습니다.
  private EntityManager em;

  @Override
  public Coin save(Coin coin) {
    // ID가 없으면 새로운 엔티티로 보고 저장 (INSERT)
    if (coin.getId() == null) {
      em.persist(coin);
      return coin;
    } else {
      // ID가 있으면 기존 엔티티로 보고 병합 (UPDATE)
      return em.merge(coin);
    }
  }

  @Override
  public Optional<Coin> findById(Long id) {
    // ID로 엔티티를 찾고, 결과를 Optional로 감싸서 반환합니다.
    Coin coin = em.find(Coin.class, id);
    return Optional.ofNullable(coin);
  }

  @Override
  public List<Coin> findAll() {
    // JPQL을 사용하여 모든 Coin 엔티티를 조회합니다.
    TypedQuery<Coin> query = em.createQuery("SELECT c FROM Coin c", Coin.class);
    return query.getResultList();
  }

  @Override
  public List<Coin> findByMarketCapBetween(Long minMarketCap, Long maxMarketCap) {
    // JPQL을 사용하여 시가총액 범위로 코인을 조회합니다.
    TypedQuery<Coin> query = em.createQuery(
            "SELECT c FROM Coin c WHERE c.marketCap BETWEEN :minMarketCap AND :maxMarketCap", Coin.class);
    // 쿼리 파라미터에 실제 값을 바인딩합니다.
    query.setParameter("minMarketCap", minMarketCap);
    query.setParameter("maxMarketCap", maxMarketCap);
    return query.getResultList();
  }

  @Override
  public void delete(Coin coin) {
    // 엔티티를 삭제합니다. (영속 상태 확인 후)
    em.remove(em.contains(coin) ? coin : em.merge(coin));
  }

  @Override
  public void deleteById(Long id) {
    // ID로 엔티티를 찾아서 삭제합니다.
    findById(id).ifPresent(this::delete);
  }

  // TODO: CoinDao 인터페이스에 정의한 다른 메서드들도 여기에 구현해야 합니다.
}
