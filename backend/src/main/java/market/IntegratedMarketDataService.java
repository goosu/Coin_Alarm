package coinalarm.Coin_Alarm.market;

import coinalarm.Coin_Alarm.alarm.AlarmThresholdManager;
import coinalarm.Coin_Alarm.buffer.MultiTieredSnapshotBuffer;
import coinalarm.Coin_Alarm.coin.CoinResponseDto;
import coinalarm.Coin_Alarm.exchange.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ⭐⭐⭐ [대폭 수정] 통합 마켓 데이터 서비스
 * 변경사항:
 * - [삭제] 기존의 latest1MinuteVolume, latest5MinuteVolume 등 캐시
 *   → MultiTieredSnapshotBuffer로 통합
 *
 * - [삭제] 기존의 fixedRate로 캔들 수집하는 로직
 *   → WebSocket으로 실시간 Ticker를 받아 스냅샷 버퍼에 저장
 *
 * - [추가] 다중 거래소 지원
 * - [추가] 시가총액 기반 알람
 * - [추가] 동적 N분 계산
 * - [추가] REST API 프라이밍
 *
 * 새로운 플로우:
 * 1. WebSocket으로 실시간 Ticker 수신
 * 2. 스냅샷 버퍼에 저장
 * 3. 사용자가 N분 요청 시 버퍼에서 동적 계산
 * 4. 알람 조건 체크하여 프론트엔드 전송
 */

//마켓 통합 운용서비스 변환
public class IntegratedMarketDataService {
  private final MultiTieredSnapshotBuffer snapshotBuffer;  // 스냅샷 버퍼
  private final AlarmThresholdManager alarmManager;        // 알람 관리
  private final SimpMessagingTemplate messagingTemplate;   // WebSocket 전송

  //모든 거래소 클라이언트 관리
  //Spring이 ExchangeClient 인터페이스를 구현한 모든 Bean을 자동으로 주입
  private final List<ExchangeClient> exchangeClients;
  
  //거래소별 즐겨찾기 관리
  //구조:Map<거래소ID, Set<마켓코드> 형태  예: {"UPBIT" -> ["KRW-BTC", "KRW-ETH", ...], "BINANCE_SPOT" -> [...]}
  private final Map<String, Set<String>> favoritesByExchange;
  
  //거래소별 시가총액 정보캐시
  private final Map<String, Map<String, MarketCapInfo>> marketCapCache;

  @Autowired
  public IntegatedMarketDataService(
          MultiTieredSnapshotBuffer snapshotBuffer,
          AlarmThresholdManager alarmManager,
          SimpMessagingTemplate messagingTemplate,
          List<ExchangeClient> exchangeClients //모든 거래소 클라이언트 자동주입
  ){
    this.snapshotBuffer = snapshotBuffer;
    this.alarmManager = alarmManager;
    this.messagingTemplate = messagingTemplate;
    this.exchangeClients = exchangeClients;
    this.favoritesByExchange = new ConcurrentHashMap<>();
    this.marketCapCache = new ConcurrentHashMap<>();

    System.out.println("✅ IntegratedMarketDataService 초기화");
    System.out.println("🔌 연결된 거래소: " + exchangeClients.size() + "개");

    // 각 거래소별로 실시간 데이터 구독 시작
    initializeExchangeStreams();
  }
}
