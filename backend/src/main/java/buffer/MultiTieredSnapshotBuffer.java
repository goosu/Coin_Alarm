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

import static coinalarm.Coin_Alarm.AccessingDataJpaApplication.log;
import static jdk.internal.net.http.common.Utils.accumulateBuffers;
import static jdk.internal.net.http.common.Utils.getBuffer;


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
    CouncurrentSkipListMap<Instant, TickerSnapshot> buffer = getOrCreateBuffer(exchangeId, marketCode);

    //마지막 스냅샷 확인
    Map.Entry<Instant, TickerSnapshot> lastEntry = buffer.lastEntry();

    if(lastEntry ==null){
      //첫번째 스냅샷은 무조건 저장
      buffer.put(timestamp, snapshot);
      return;
    }

    //마지막 스냅샷과의 시간간격 확인
    Duration elapsed = Duration.between(lastEntry.getKey(), timestamp);

    //1초이상 경과 시 저장(Tier 1 기준) // => 뭐가 1초이상 경과?
    if(elapsed.compareTo(TIER1_INTERVAL) >=0){
      buffer.put(timestamp.snapshot);
    }
  }
  /**
   * ⭐⭐ [핵심 메서드] N분 전 스냅샷 조회
   * 동작:
   * 1. 현재 시간 - N분 = 목표 시간
   * 2. 목표 시간 이전의 가장 가까운 스냅샷 찾기
   *
   * 기술 설명:
   * - floorEntry(targetTime): targetTime 이하의 가장 큰 키를 가진 엔트리 반환
   *   (ConcurrentSkipListMap의 메서드, O(log N) 성능)
   *
   * @param exchangeId 거래소 ID
   * @param marketCode 마켓 코드
   * @param currentTime 현재 시간
   * @param minutesAgo 몇 분 전인지
   * @return Optional<TickerSnapshot> - 찾은 스냅샷 (없으면 Empty)
   */
  public Optional<TickerSnapshot> getSnapshotBefore(
          String exchangeId,
          String marketCode,
          Instant currentTime,
          int minutesAgo
  ){
    //거래소별 마켓코드 담기
    ConcurrentSkipListMap<Instant, TickerSnapshot> buffer = getBuffer(exchangeId, marketCode);
    if(buffer == null || buffer.isEmpty()){
      return Optional.empty();
    }

    //목표시간 계산
    Instant targetTime = currentTime.minus(Duration.ofMinutes(minutesAgo));

    //TargetTime 이전의 가장 가까운 스냅샷
    Map.Entry<Instant, TickerSnapshot> entry = buffer.floorEntry(targetTime);

    return entry != null ? Optional.of(entry.getValue()) : Optional.empty();
  }
  /**
   * ⭐⭐⭐ [핵심 메서드] 롤링 N분 거래대금 계산
   *
   * 공식: N분 거래대금 = 현재 rolling24h - N분 전 rolling24h
   *
   * 원리:
   * - 업비트 Ticker의 acc_trade_price_24h는 "롤링 24시간 누적 거래대금"
   * - 이 값의 차이를 구하면 정확한 N분 거래대금을 계산할 수 있음
   *
   * 예시:
   * - 현재 rolling24h = 1000억
   * - 5분 전 rolling24h = 998억
   * - 5분 거래대금 = 2억
   *
   * @param exchangeId 거래소 ID
   * @param marketCode 마켓 코드
   * @param minutes N분
   * @return Double - N분 거래대금 (원화)
   */
   public Double calculateRollingVolume(
           String exchangeId,
           String marketCode,
           int minutes
   ){
     ConcurrentSkipListMap<Instant, TickerSnapshot> buffer = getBuffer(exchangeId, marketCode);
     if(buffer == null || buffer.isEmpty()){
       return 0.0;
     }

     //현재 스냅샷(가장 최신)
     TickerSnapshot current = buffer.lastEntry().getValue();

     //n분전 스냅샷
     Optional<TickerSnapshot> beforeOpt = getSnapshotBefore(
             exchangeId, marketCode, current.getTimestamp(), minutes
     );
     if(beforeOpt.isEmpty()){
       return 0.0;
     }
     TickerSnapshot before = beforeOpt.get();
     //N분 거래대금 계산
     Double rollingVolume = current.getRolling24hVolume() - before.getRolling24hVolume();

     return Math.max(0.0, rollingVolume); //음수방지
   }

   /**
   * ⭐⭐⭐ [핵심 메서드] 롤링 N분 가격 변화율 계산
   *
   * 공식: N분 변화율 = ((현재가격 - N분 전 가격) / N분 전 가격) * 100
   *
   * @param exchangeId 거래소 ID
   * @param marketCode 마켓 코드
   * @param minutes N분
   * @return Double - N분 가격 변화율 (%)
   */
   public Double calculateRollingPriceChange(
           String exchangeId,
           String marketCode,
           int minutes
   ){
     ConcurrentSkipListMap<Instant, TickerSnapshot> buffer = getBuffer(exchangeId, marketCode);
     if(buffer == null || buffer.isEmpty()){
       return 0.0;
     }

     TickerSnapshot current = buffer.lastEntry().getValue();
     Optional<TickerSnapshot> beforeOpt = getSnapshotBefore(
             exchangeId, marketCode, current.getTimestamp(), minutes
     );
     if(beforeOpt.isEmpty()){
       return 0.0;
     }
     TickerSnapshot before = beforeOpt.get();

     //N분 가격 변화율 계산
     Double priceChange = (current.getCurrentPrice() - before.getCurrentPrice()) / before.getCurrentPrice() * 100;

     return priceChange;
   }
 /**
 * ⭐⭐ [프라이밍] REST API로 가져온 과거 데이터로 버퍼 초기화
 *
 * 목적: 즐겨찾기 추가 시 과거 4시간 데이터를 미리 채워서
 *       사용자가 즉시 과거 데이터를 확인할 수 있게 함
 *
 * 동작:
 * 1. REST API로 과거 240분(4시간) 캔들 데이터 조회
 * 2. 각 캔들을 TickerSnapshot으로 변환
 * 3. 스냅샷 버퍼에 저장
 * 4. 이후 실시간 데이터가 들어와도 연속성 유지
 *
 * @param exchangeId 거래소 ID
 * @param marketCode 마켓 코드
 * @param historicalData 과거 캔들 데이터 리스트
 */
  public void primeBuffer(
          String exchangeId,
          String marketCode,
          List<CandleData> historicalData)
  {
    if(historicalData == null || historicalData.isEmpty()) {
      //log.info("⚠️ 프라이밍 데이터 없음: " + exchangeId + "/" + marketCode);
      System.out.println("⚠️ 프라이밍 데이터 없음: " + exchangeId + "/" + marketCode);
      return;
    }

    ConcurrentSkipListMap<Instant, TickerSnapshot> buffer = getBuffer(exchangeId, marketCode);
    System.out.println("🔄 프라이밍 시작: " + exchangeId + "/" + marketCode
            + " (데이터 " + historicalData.size() + "개)");

    int primeCount = 0;

    //과거 캔들 데이터를 스냅샷으로 변환하여 저장
    for(CandleData candle : historicalData){
      TickerSnapshot snapshot = TickerSnapshot.builder()
              .exchangeId(exchangeId)
              .marketCode(marketCode)
              .timestamp(candle.getTimestamp())
              .currentPrice(candle.getClosePrice())
              .rolling24hVolume(candle.getAccTradeVolume())
              .build();
      buffer.put(snapshot.getTimestamp(), snapshot);
      primeCount++;
    }
    System.out.println("✅ 프라이밍 완료: " + primedCount + "개 스냅샷 저장, 버퍼 크기 = " + buffer.size());
  }

  /**
   * ⭐ [메모리 관리] 오래된 스냅샷 자동 제거
   *
   * 동작: 1분마다 실행되어 4시간(Tier 3 보관기간) 이전 데이터 삭제
   *
   * 기술 설명:
   * - @Scheduled(fixedRate = 60000): Spring의 스케줄러, 60초마다 실행
   * - headMap(cutoff): cutoff 이전의 모든 엔트리를 포함하는 부분 맵 반환
   * - clear(): 해당 부분 맵의 모든 엔트리 삭제
   */
  @Scheduled(fixedRate = 60000)
  public void purgeOldSnapshots(){
    Instant now = Instant.now();
    Instant cutoff = now.minus(TIER3_RETENTION);

    int totalPurged = 0;

  //모든 거래소, 모든 마켓의 버퍼를 순회
  for(Map.Entry<String, Map<String, ConcurrentSkipListMap<Instant, TickerSnapshot>>> exchangeEntry : buffers.entrySet())
  {
    String exchangeId = exchangeEntry.getKey();
    Map<String, ConcurrentSkipListMap<Instant, TickerSnapshot>> marketBuffer = exchangeEntry.getValue();

    for(Map.Entry<String, ConcurrentSkipListMap<Instant, TickerSnapshot>>marketEntry : marketBuffers.entrySet())
    {
      String marketCode = marketEntry.getKey();
      ConcurrentSkipListMap<Instant, TickerSnapshot> buffer = marketEntry.getValue();

      //4시간 이전 데이터 제거
      int beforeSize = buffer.size();
      buffer.headMap(cutoff).clear(); // cutoff 이전 데이터 모두 삭제
      int afterSize = buffer.size();
      int purged = beforeSize - afterSize; //과거 기록 남는거 아닌가? 살짝 이해 안되넹 =? 아 cutoff할떄 이미 clear 했구나

      if(purged > 0){
        totalPurged += purged;
        System.out.println("🗑️ 스냅샷 정리: " + exchangeId + "/" + marketCode
                + " (" + purged + "개 삭제, 남은 개수: " + afterSize + ")")
      }
    }
  }
  if (totalPurged > 0) {
    System.out.println("✅ 전체 스냅샷 정리 완료: " + totalPurged + "개 삭제");
  }
}

/*버퍼 가져오기 - 없을경우 생성*/
private ConcurrentSkipListMap<Instant, TickerSnapshot> getOrCreateBuffer(
        String exchangeId,
        String marketCode
){
  return buffers
          .computeIfAbsent(exchangeId, k-> new ConcurrentHashMap<>())
          .computeIfAbsent(marketCode, k-> new ConcurrentSkipListMap<>());
  //compteIfAbsent: 키가 없으면 새로운 값을 생성하여 저장하고 반환
  //Thread->Safe하게 동작함
}
}

/*버퍼 가져오기2 - 없을경우 null*/
private ConcurrentSkipListMap<Instant, TickerSnapshot> getBuffer(
        String exchangeId,
        String marketCode
){
  Map<String, ConcurrentSkipListMap<Instant, TickerSnapshot>> marketBuffers = buffers.get(exchangeId);
  if(marketBuffers == null){
    return null;
  }
  return marketBuffers.get(MarketCode);
}
/*버퍼 상태 조회(디버깅용)*/  //20251028 나중에 로직확인
public Map<String, Object> getBufferStatus(){
  Map<String, Object> status = new HashMap<>();
  int totalSnapshots = 0;
  int totalMarkets = 0;

  for(Map.Entry<String, Map<String, ConcurrentSkipListMap<Instant, TickerSnapshot>>> exchangeEntry : buffers.entrySet())
  {
    String exchangId = exchangeEntry.getKey();
    Map<String, ConcurrentSkipListMap<Instant, TickerSnapshot>> marketBuffers = exchangeEntry.getValue();

    totalMarkets += marketBuffers.size();

    for(ConcurrentSkipListMap<Instant, TickerSnapshot> buffer : marketBuffers.values()){
      totalSnapshots += buffer.size();
    }
  }

  status.put("totalExchange", buffers.size());
  status.put("totalMarkets", totalMarkets);
  status.put("totalSnapshots",totalSnapshots);

  return status;

}
}

