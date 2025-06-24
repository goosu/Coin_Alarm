// src/main/java/coinalarm/Coin_Alarm/coin/CoinDaoImpl.java
package coin; // <-- 실제 프로젝트 패키지 이름 + 하위 패키지

import org.springframework.stereotype.Repository; // 이 클래스가 Spring의 Repository(DAO) 역할을 함을 명시합니다. Spring Bean으로 등록됩니다.
import org.springframework.transaction.annotation.Transactional; // 이 클래스의 메서드들이 트랜잭션 안에서 실행되도록 설정합니다. 데이터 변경 작업 시 필수적입니다.

// Jakarta Persistence API (JPA) 관련 임포트 ('jakarta' 사용)
import jakarta.persistence.EntityManager; // JPA의 핵심 객체입니다. 엔티티를 관리(저장, 조회, 수정, 삭제)하는 역할을 합니다.
import jakarta.persistence.PersistenceContext; // EntityManager 객체를 Spring으로부터 주입받을 때 사용합니다.
import jakarta.persistence.TypedQuery; // 결과 타입이 명확한 JPQL 쿼리를 생성할 때 사용합니다.

import java.util.List;
import java.util.Optional;

@Repository // @Repository 어노테이션을 붙여서 Spring이 이 클래스를 데이터 접근 계층의 컴포넌트로 인식하게 합니다.
@Transactional // @Transactional 어노테이션을 붙여서 이 클래스의 모든 public 메서드가 실행될 때 트랜잭션이 시작되고, 메서드 종료 시 커밋 또는 롤백되도록 합니다.
public class CoinDaoImpl implements CoinDao { // CoinDao 인터페이스를 구현합니다.

  @PersistenceContext // @PersistenceContext 어노테이션을 사용하여 Spring으로부터 EntityManager 객체를 주입받습니다.
  // EntityManager는 영속성 컨텍스트(Persistence Context)를 통해 엔티티 객체들을 관리합니다.
  private EntityManager em;

  @Override
  public Coin save(Coin coin) {
    // save 메서드 구현: 코인 객체를 데이터베이스에 저장하거나 업데이트합니다.
    if (coin.getId() == null) {
      // 만약 코인 객체의 ID가 null이면, 데이터베이스에 아직 없는 새로운 엔티티로 간주합니다.
      em.persist(coin); // persist(): 새로운 엔티티를 영속성 컨텍스트에 추가하고 데이터베이스에 저장(INSERT)합니다.
      return coin; // 저장된 엔티티 객체를 반환합니다.
    } else {
      // 만약 코인 객체의 ID가 null이 아니면, 데이터베이스에 이미 있는 엔티티로 간주하고 업데이트합니다.
      return em.merge(coin); // merge(): 영속성 컨텍스트에 있는 엔티티와 병합하거나, 새로운 영속 엔티티를 만들고 데이터베이스에 업데이트(UPDATE)합니다. 병합된(또는 새로 만들어진) 영속 엔티티를 반환합니다.
    }
  }

  @Override
  public Optional<Coin> findById(Long id) {
    // findById 메서드 구현: 주어진 ID로 코인 객체를 찾습니다.
    // em.find(엔티티 클래스 타입, 기본 키 값): 기본 키 값을 사용하여 엔티티를 직접 조회합니다.
    Coin coin = em.find(Coin.class, id);
    // Optional.ofNullable(): 객체가 null이 아니면 Optional로 감싸고, null이면 Optional.empty()를 반환합니다.
    // 이렇게 하면 호출하는 쪽에서 널 체크를 강제하여 널 포인터 예외를 방지할 수 있습니다.
    return Optional.ofNullable(coin);
  }

  @Override
  public List<Coin> findAll() {
    // findAll 메서드 구현: 데이터베이스의 모든 코인 객체를 조회합니다.
    // JPQL(Java Persistence Query Language): 객체 지향 쿼리 언어입니다. SQL과 유사하지만 테이블 대신 엔티티 객체와 필드를 대상으로 쿼리합니다.
    // "SELECT c FROM Coin c": 'Coin' 엔티티 타입의 모든 객체 'c'를 선택하라는 의미입니다.
    TypedQuery<Coin> query = em.createQuery("SELECT c FROM Coin c", Coin.class);
    // query.getResultList(): 쿼리 실행 결과를 List 형태로 가져옵니다.
    return query.getResultList();
  }

  @Override
  public List<Coin> findByMarketCapBetween(Long minMarketCap, Long maxMarketCap) {
    // findByMarketCapBetween 메서드 구현: 시가총액 범위로 코인 객체를 조회합니다.
    // "SELECT c FROM Coin c WHERE c.marketCap BETWEEN :minMarketCap AND :maxMarketCap":
    // 'Coin' 엔티티 중에서 'marketCap' 필드 값이 :minMarketCap과 :maxMarketCap 사이인 객체 'c'를 선택하라는 의미입니다.
    // ':minMarketCap', ':maxMarketCap'은 쿼리 파라미터입니다.
    TypedQuery<Coin> query = em.createQuery(
            "SELECT c FROM Coin c WHERE c.marketCap BETWEEN :minMarketCap AND :maxMarketCap", Coin.class);
    // query.setParameter(파라미터 이름, 실제 값): 쿼리 파라미터에 실제 값을 바인딩합니다.
    query.setParameter("minMarketCap", minMarketCap);
    query.setParameter("maxMarketCap", maxMarketCap);
    // 쿼리 실행 결과를 List 형태로 가져옵니다.
    return query.getResultList();
  }

  @Override
  public void delete(Coin coin) {
    // delete 메서드 구현: 주어진 코인 객체를 데이터베이스에서 삭제합니다.
    // em.remove(엔티티 객체): 영속성 컨텍스트에서 엔티티를 제거하고 데이터베이스에서 삭제(DELETE)합니다.
    // remove 메서드는 영속 상태의 엔티티에 대해서만 작동합니다.
    // em.contains(coin): 현재 영속성 컨텍스트가 해당 엔티티를 관리하고 있는지 확인합니다.
    // em.merge(coin): 만약 영속 상태가 아니라면(준영속 또는 비영속 상태), merge를 통해 영속 상태로 만든 후 삭제합니다.
    em.remove(em.contains(coin) ? coin : em.merge(coin));
  }

  @Override
  public void deleteById(Long id) {
    // deleteById 메서드 구현: 주어진 ID를 가진 코인 객체를 찾아 삭제합니다.
    // findById(id): 먼저 ID로 코인 객체를 찾습니다. 결과는 Optional<Coin> 입니다.
    // .ifPresent(this::delete): Optional에 값이 존재하면(코인을 찾았으면), 해당 코인 객체를 인자로 delete 메서드를 호출합니다.
    findById(id).ifPresent(this::delete);
  }

  // TODO: CoinDao 인터페이스에 다른 메서드를 추가했다면 여기에 구현해야 합니다.
}
