// ============================================================================
// 4. 알람 설정 관리 (신규 추가)
// ============================================================================
package coinalarm.Coin_Alarm.alarm;

import coinalarm.Coin_Alarm.exchange.MarketCapTier;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
/**
 * ⭐ [신규 추가] 알람 설정 관리
 *
 * 목적: 시가총액 등급별로 다른 알람 임계값 설정
 *
 * 예시:
 * - MEGA (15조 이상): 10억원 이상 거래대금 시 알람
 * - LARGE (1조 초과): 3억원 이상 거래대금 시 알람
 * - MEDIUM (1조 미만): 1억원 이상 거래대금 시 알람
 */
public class AlarmThresholdManager {
  //**시가총액 등급별 알람**
  private final Map<MarketCapTier, Double> defaultThresholds;

  //**거래소별 알람 활성화**
  private final Map<String, Boolean> exchangeEnabled;

  //**마켓별 커스텀 임계값(선택적으로 바꿔야함)**
  private final Map<String, Double> customThresholds;

  public AlarmThresholdManager(){
    //기본 임계값설정 나중에 클라이언트에서 받아올지 판단
    this.defaultThresholds = new HashMap<>();
    defaultThresholds.put(MarketCapTier.MEGA, 10_000_000_000_000.0); //10조
    defaultThresholds.put(MarketCapTier.LARGE, 1_000_000_000_000.0); //1조
    defaultThresholds.put(MarketCapTier.MEDIUP, 999_999_999_999.0); //1조미만

    //거래소 활성화 상태
    this.exchangeEnabled = new ConcurrentHashMap<>();

    //커스텀 임계값
    this.customThresholds = new ConcurrentHashMap<>();

    System.out.println("✅ AlarmThresholdManager 초기화: " + defaultThresholds);
  }

  /**
   * ⭐ [핵심] 알람 발생 여부 판단
   *
   * @param exchangeId 거래소 ID
   * @param marketCode 마켓 코드
   * @param marketCapTier 시가총액 등급
   * @param volumeN 해당 N분 거래대금
   * @return boolean - 알람을 발생시킬지 여부
   */
  public boolean shouldTriggerAlarm(
          String exchangeId,
          String marketCode,
          MarketCapTier marketCapTier,
          double volumeN
  ){
    //거래소가 비활성화일 경우 알람X
    if(!isExchangeEnabled(exchangeId)){
      return false;
    }

    //임계값 가져오기(커스텀>기본)
    double threshold = getThreshold(exchangId, marketCode, marketCapTier);
    
    //거래대금이 임계값 이상이면 알람 발생
    return volumeN >= threshold;
  }
/**
 * 임계값 조회 (커스텀 우선, 없으면 기본값)
 */
  public double getThreshold(String exchangeId, String marketCode, MarketCapTier tier){
    String key = exchangeId + ":" + marketCode;

    //커스텀 임계값이 있으면 사용
    if(customThresholds.containsKey(key)){
      return customThresholds.get(key);
    }

    //없으면 시가총액 등급별 기본값
    return defaultThresholds.getOrDefault(tier, 1_000_000_000_000.0); //기본 1조
  }

  //**커스텀 임계값 설정**
  public void setCustomThreshold(String exchangeId, String marketCode, double threshold){
    String key = exchangeId + ":" + marketCode;
    customThresholds.put(key, threshold);
    System.out.println("📝 커스텀 임계값 설정: " + key + " = " + threshold);
  }

  //거래소 활성화/비활성화
  public void setExchangeEnabled(String exchangeId, boolean enabled){
    exchangeEnabled.put(exchangeId, enabled);
    System.out.println("🔔 거래소 알람 " + (enabled ? "활성화" : "비활성화") + ": " + exchangeId);
  }

  //거래소 활성화 상태 확인
  public boolean isExchangeEnabled(String exchangeId){
    return exchangeEnabled.getOrDefault(exchangeId, true); // 기본값: 활성화
  }

  //시가총액 등급별 기본 임계값 변경
  public void setDefaultThreshold(MarketCapTier tier, double threshold){
    defaultThresholds.put(tier, threshold);
    System.out.println("📝 기본 임계값 변경: " + tier + " = " + threshold);
  }
}

//대충 어떤구조인지 알겠네
