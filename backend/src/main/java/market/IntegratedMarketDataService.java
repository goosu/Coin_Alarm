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
@Service
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
  public IntegratedMarketDataService(
          MultiTieredSnapshotBuffer snapshotBuffer,
          AlarmThresholdManager alarmManager,
          SimpMessagingTemplate messagingTemplate,
          List<ExchangeClient> exchangeClients //모든 거래소 클라이언트 자동주입
  ) {
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

  //모든 거래소의 실시간 Ticker 스틀미 구독
  private void initializeExchangeStreams() {
    for (ExchangeClient exchange : exchangeClients) {
      String exchangeId = exchange.getExchangeId();
      System.out.println("🔄 " + exchangeId + " 실시간 스트림 구독 중...");

      //모든 마켓 코드조회
      exchange.getAllMarketCodes()
              .subscribe(marketCodes -> {
                System.out.println("✅ " + exchangeId + ": " + marketCodes.size() + "개 마켓 발견");

                //실시간 Ticker 스트림 구독
                exchange.subscribeTickerStream(marketCodes)
                        .subscribe(
                                this::handleTickerSnapshot
                                , error -> System.err.println("❌ " + exchangeId + " 스트림 에러: " + error.getMessage())
                                , () -> System.out.println("⚠️ " + exchangeId + " 스트림 종료")
                        );
              });
    }
  }

  /**
   * ⭐⭐⭐ [핵심] 실시간 Ticker 스냅샷 처리
   * <p>
   * 동작:
   * 1. 스냅샷 버퍼에 저장
   * 2. 알람 조건 체크
   * 3. 조건 만족 시 프론트엔드로 전송
   */
  private void handleTickerSnapshot(TickerSnapshot snapshot) {
    //스냅샷 버퍼에 저장
    snapshotBuffer.addSnapshot(snapshot);

    //알람 조건 체크
    checkAndTriggerAlarm(snapshot.getExchangeId(), snapshot.getMarkeCode());
  }

  /**
   * ⭐⭐ [핵심] 알람 조건 체크 및 발송
   * <p>
   * 동작:
   * 1. 1분 거래대금 계산
   * 2. 시가총액 조회
   * 3. 알람 임계값과 비교
   * 4. 조건 만족 시 프론트엔드로 전송
   */
  private void checkAndTriggerAlarm(String exchangeId, String marketCode) {
    Double volume1m = snapshotBuffer.calculateRollingVolume(exchangeId, marketCode, 1); //1분봉

    if (volume1m == null || volume1m == 0) {
      return;
    }

    //시가총액 정보조회
    MarketCapInfo marketCapInfo = getMarketCapInfo(exchangeId, marketCode);
    if (marketCapInfo == null) {
      return;
    }

    //알람조건 체크
    boolean shouldAlarm = alarmManager.shouldTriggerAlarm(
            exchangeId,
            marketCode,
            marketCapInfo.getTier(),
            volume1m
    );

    if (shouldAlarm) {
      //알람발생
      sendAlarmToFrontend(exchangeId, marketCode, volume1m, marketCapInfo);
    }
  }

  //프론트엔드 알람 전송
  private void sendAlarmToFrontend(
          String exchangeId,
          String marketCode,
          double volume1m,
          MarketCapInfo marketCapInfo
  ){
    //CoinResponseDto 생성
    CoinResponseDto dto = CoinResponseDto.builder()
            .exchangeId(exchageId)
            .symbol(marketCode)
            .volume1m(volume1m)
            .marketCapTier(marketCapInfo.getTier())
            .timestamp(System.currentTimeMillis())
            .build();

    //WebSocket으로 전송
    messagingTemplate.convertAndSend("/topic/alarm", dto);

    System.out.println("🔔 알람 발송: " + exchangeId + "/" + marketCode
            + " (1분봉: " + formatVolume(volume1m) + ", 등급: " + marketCapInfo.getTier() + ")");
  }

  /**
   * ⭐⭐ [즐겨찾기] 즐겨찾기 추가 + REST API 프라이밍
   *
   * 동작:
   * 1. 즐겨찾기 목록에 추가
   * 2. REST API로 과거 4시간 캔들 조회
   * 3. 스냅샷 버퍼 프라이밍
   * 4. 프론트엔드로 즉시 데이터 전송
   */
  public void addFavorite(String exchangeId, String marketCode){
    //즐겨찾기 목록에 추가
    favoritesByExchange
            .computeIfAbsent(exchangeId, k -> ConcurrentHashMap.newKeySet())
            .add(marketCode);

    System.out.println("⭐ 즐겨찾기 추가: " + exchangeId + "/" + marketCode);

    //해당 거래소 클라리언트 찾기
    ExchangeClient exchange = findExchangeClient(exchangeId);
    if(exchange = null){
      System.err.println("❌ 거래소 클라이언트 없음: " + exchangeId);
      return;
    }

    //REST API로 과거 4시간 캔들 조회
    exchange.getHistoricalCandles(maketcode,1,240) //1분봉 240개 = 4시간
            .subscribe(candles->{
              if(candles.isEmpty()){
                System.out.println("⚠️ 과거 데이터 없음: " + exchangeId + "/" + marketCode);
                return;
              }

              //스냅샷 버퍼 프라이밍
              snapshotBuffer.primeBuffer(exchangeId, marketCode, candles);

              //프론트엔드로 즉시 데이터 전송
              sendFavoriteDataToFrontend(exchangeId, marketCode);
            });
  }
  /**
   * ⭐ [즐겨찾기] 프론트엔드로 즐겨찾기 데이터 전송
   *
   * 동작: 사용자가 요청한 N분들에 대해 롤링 계산하여 전송
   */
  private void sendFavoriteDataToFrontend(String exchangeId, String marketCode) {
    // 여러 N분 데이터 계산
    Map<String, Double> volumeData = new HashMap<>();
    volumeData.put("volume1m", snapshotBuffer.calculateRollingVolume(exchangeId, marketCode, 1));
    volumeData.put("volume5m", snapshotBuffer.calculateRollingVolume(exchangeId, marketCode, 5));
    volumeData.put("volume15m", snapshotBuffer.calculateRollingVolume(exchangeId, marketCode, 15));
    volumeData.put("volume1h", snapshotBuffer.calculateRollingVolume(exchangeId, marketCode, 60));
    volumeData.put("volume24h", snapshotBuffer.calculateRollingVolume(exchangeId, marketCode, 1440));

    // 가격 변화율도 계산
    Map<String, Double> priceChangeData = new HashMap<>();
    priceChangeData.put("change1m", snapshotBuffer.calculateRollingPriceChange(exchangeId, marketCode, 1));
    priceChangeData.put("change5m", snapshotBuffer.calculateRollingPriceChange(exchangeId, marketCode, 5));
    priceChangeData.put("change24h", snapshotBuffer.calculateRollingPriceChange(exchangeId, marketCode, 1440));

    // CoinResponseDto 생성
    CoinResponseDto dto = CoinResponseDto.builder()
            .exchangeId(exchangeId)
            .symbol(marketCode)
            .volume1m(volumeData.get("volume1m"))
            .volume5m(volumeData.get("volume5m"))
            .volume15m(volumeData.get("volume15m"))
            .volume1h(volumeData.get("volume1h"))
            .volume24h(volumeData.get("volume24h"))
            .change1m(priceChangeData.get("change1m"))
            .change5m(priceChangeData.get("change5m"))
            .change24h(priceChangeData.get("change24h"))
            .isFavorite(true)
            .timestamp(System.currentTimeMillis())
            .build();

    // WebSocket으로 전송
    messagingTemplate.convertAndSend("/topic/favoriteUpdate", dto);

    System.out.println("🌟 즐겨찾기 데이터 전송 완료: " + exchangeId + "/" + marketCode);
  }

  //즐겨찾기 제거
  public void removeFavorite(String exchangeId, String marketCode){
    Set<String> favorites = favoritesByExchange.get(exchangeId);
    if(favorites != null){
      favorites.remove(marketCode);
      System.out.println("🗑️ 즐겨찾기 제거: " + exchangeId + "/" + marketCode);
    }
  }

  //시가총액 정보조회
  private MarketCapInfo getMarketCapInfo(String exchangeId, String marketCode){
    return marketCapCache
            .computeIfAbsent(exchangeId, k->new ConcurrentHashMap<>())
            .computeIfAbsent(marketCode, k->{
              //캐시에 없으면 API 호출
              ExchangeClient exchange = findExchangeClient(exchangeId);
              if(exchange == null) return null;

              return exchange.getMarketCap(maketCode).block();
              //블록거는건 위험하긴 한것같은데 나중에 확인해보기
            });
  }

  //거래소 클라이언트 찾기
  private ExchaneClient findExchangeClient(String exchangeId){
    return exchangeClient.stream()
            .filter(clinet->clint.getExchangeId().equals(exchangeId))
            .findFirst()
            .orElse(null);
  }

  //거래대금 포멧팅
  private String formatVolume(double volume) {
    if (volume >= 1_000_000_000_000L) {
      return String.format("%.1f조", volume / 1_000_000_000_000.0);
    } else if (volume >= 100_000_000_000L) {
      return String.format("%.0f억", volume / 100_000_000.0);
    } else if (volume >= 1_000_000L) {
      return String.format("%.0f백만", volume / 1_000_000.0);
    }
    return String.format("%.0f만", volume / 10_000.0);
  }
}
