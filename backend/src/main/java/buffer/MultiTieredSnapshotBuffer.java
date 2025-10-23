package buffer;

package coinalarm.Coin_Alarm.buffer;

import coinalarm.Coin_Alarm.exchange.TickerSnapshot;
import coinalarm.Coin_Alarm.exchange.CandleData;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;


//**다계층 스냅샷 버퍼 시스템**
/*
 * ⭐⭐⭐ [핵심 신규 추가] 다계층 스냅샷 버퍼 시스템
 * 목적: 메모리 효율을 극대화하면서 정확한 롤링 계산 지원
 ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
 * 전략:
 * - Tier 1 (1~5분): 1초 간격 스냅샷 → 정밀한 단기 계산
 * - Tier 2 (5분~1시간): 10초 간격 스냅샷 → 중기 계산
 * - Tier 3 (1~4시간): 1분 간격 스냅샷 → 장기 계산
 ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
 * 메모리 절약 효과:
 * - 4시간 데이터를 1초 간격으로 저장 시: 14,400개 스냅샷
 * - 다계층 버퍼 사용 시: 약 810개 스냅샷
 * - 약 94% 메모리 절약!
 ㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡㅡ
 * 기술 설명:
 * - ConcurrentSkipListMap: 정렬된 Map + 동시성 보장
 *   장점: 시간순 정렬 자동, Thread-Safe, O(log N) 성능
 */
@Service
public class MultiTieredSnapshotBuffer {
  //거래소ID를 담을 버퍼
  private final Map<String, Map<String, ConcurrentSkipListMap<Instant, TickerSnapshot>>> buffejs;

  // ⭐ [핵심] 스냅샷 저장 간격 정의
  private static final Duration TIER1_INTERVAL = Duration.ofSeconds(1);  //1초
  private static final Duration TIER2_INTERVAL = Duration.ofSeconds(10); //10초
  private static final Duration TIER3_INTERVAL = Duration.ofMinutes(1);  //1분

  // ⭐ [핵심] 보관 기간 정의
  private static final Duration TIER1_RETENTION = Duration.ofMinutes(5);  // 5분
  private static final Duration TIER2_RETENTION = Duration.ofHours(1);    // 1시간
  private static final Duration TIER3_RETENTION = Duration.ofHours(4);    // 4시간

  public MultiTieredSnapshotBuffer(){
    this.buffers = new ConcurrentHashMap<>();
    Systemp.out.println("MultiTieredSnapshotBuffer 초기화 완료");
  }
  /*
   * ⭐⭐ [핵심 메서드] 스냅샷 추가
   * 동작:
   * 1. 마지막 스냅샷과의 시간 간격 확인
   * 2. 간격이 1초 이상이면 저장
   * 3. 자동으로 계층별 간격 유지
  */

  public void addSnapshot(TickerSnapshot snapshot){
    String exchangeId = snapshot.getExchangeId();
    String marketCode = snapshot.getMarketCode();
    Instant timestamp = snapshot.getTimestamp();

    //버퍼 가져오기 (없으면 생성)
  }

}
