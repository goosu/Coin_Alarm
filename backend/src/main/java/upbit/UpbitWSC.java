package coinalarm.Coin_Alarm.upbit;

import coinalarm.Coin_Alarm.upbit.UpbitTickerResponse; // UpbitTickerResponse 임포트
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 파싱을 위한 ObjectMapper
import jakarta.annotation.PreDestroy; // [수정] PreDestroy로 변경
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map; // Map 임포트
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer; // Consumer 임포트
import java.util.stream.Collectors; // Collectors 임포트

@Slf4j // 로깅을 위한 Lombok 어노테이션
@Component
public class UpbitWSC {

  private WebSocketClient webSocketClient;
  private final ObjectMapper objectMapper; // JSON 파싱용 ObjectMapper
  private Consumer<UpbitTickerResponse> onMessageCallback; // [추가] 메시지 콜백 함수
  private List<String> marketsToSubscribe; // [추가] 구독할 시장 목록
  private ScheduledExecutorService scheduler; // [추가] 스케줄러로 재연결 관리

  public UpbitWSC(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.scheduler = Executors.newSingleThreadScheduledExecutor(); // [추가] 스케줄러 초기화
  }

  // [변경] connectWebSocket 메소드의 시그니처를 MarketDataService에서 호출하는 것과 일치시킵니다.
  // 시장 목록(markets)과 메시지가 올 때 호출될 콜백 함수(onMessageCallback)를 인자로 받습니다.
  public void connectWebSocket(List<String> markets, Consumer<UpbitTickerResponse> onMessageCallback) {
    this.onMessageCallback = onMessageCallback;
    this.marketsToSubscribe = markets;
    doConnect(); // 실제 웹소켓 연결 시작
  }

  private void doConnect() {
    try {
      // [수정] Web URI 설정 (Upbit 웹소켓 주소)
      webSocketClient = new WebSocketClient(new URI("wss://api.upbit.com/websocket")) {
        @Override
        public void onOpen(ServerHandshake handshakedata) {
          log.info("Upbit WebSocket connected. Status: {}", handshakedata.getHttpStatusMessage());
          // [수정] 연결 성공 시 구독 메시지 전송
          sendSubscriptionMessage(marketsToSubscribe);
        }

        @Override
        public void onMessage(String message) {
          try {
            // [수정] JSON 파싱 및 콜백 호출
            UpbitTickerResponse ticker = objectMapper.readValue(message, UpbitTickerResponse.class);
            if (onMessageCallback != null) {
              onMessageCallback.accept(ticker);
            }
          } catch (Exception e) {
            log.error("WebSocket message parsing error: {}", e.getMessage(), e);
          }
        }

        @Override
        public void onMessage(ByteBuffer bytes) {
          // 바이너리 메시지 처리 (필요시 구현)
          onMessage(new String(bytes.array()));
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
          log.warn("Upbit WebSocket closed. Code: {}, Reason: {}, Remote: {}", code, reason, remote);
          // [수정] 연결 끊겼을 때 재연결 로직 추가
          if (code != 1000) { // 정상 종료(1000)가 아니면 재연결 시도
            log.info("Attempting to reconnect Upbit WebSocket in 5 seconds...");
            scheduler.schedule(UpbitWSC.this::doConnect, 5, TimeUnit.SECONDS);
          }
        }

        @Override
        public void onError(Exception ex) {
          log.error("Upbit WebSocket error: {}", ex.getMessage(), ex);
          // [수정] 에러 발생 시 재연결 시도
          log.info("Attempting to reconnect Upbit WebSocket in 5 seconds due to error...");
          scheduler.schedule(UpbitWSC.this::doConnect, 5, TimeUnit.SECONDS);
        }
      };
      webSocketClient.connect(); // 웹소켓 연결 시작
    } catch (URISyntaxException e) {
      log.error("Invalid WebSocket URI: {}", e.getMessage());
    }
  }

  // [추가] 구독 메시지 전송 메소드
  private void sendSubscriptionMessage(List<String> markets) {
    if (markets == null || markets.isEmpty()) {
      log.warn("No markets to subscribe to.");
      return;
    }

    List<Map<String, String>> marketCodes = markets.stream()
            .map(m -> Map.of("market", m))
            .collect(Collectors.toList());

    Map<String, Object> subscribeMessage = Map.of(
            "ticket", "test-ticket", // 고유한 티켓 값
            "type", "ticker", // [수정] 구독할 데이터 타입: ticker
            "codes", marketCodes, // 구독할 시장 코드 목록
            "isOnlySnapshot", false, // 스냅샷만 받을지 여부
            "isOnlyRealtime", true // 실시간 스트리밍만 받을지 여부
    );
    try {
      String jsonMessage = objectMapper.writeValueAsString(subscribeMessage);
      webSocketClient.send(jsonMessage);
      log.info("Subscription message sent: {}", jsonMessage);
    } catch (Exception e) {
      log.error("Failed to send subscription message: {}", e.getMessage());
    }
  }

  // 애플리케이션 종료 시 웹소켓 연결 종료
  @PreDestroy // [수정] PostConstruct가 아닌 PreDestroy로 변경해야 함.
  public void close() {
    if (webSocketClient != null && webSocketClient.isOpen()) {
      try {
        webSocketClient.closeBlocking(); // 블록킹 방식으로 웹소켓 연결 종료
        log.info("Upbit WebSocket connection closed.");
      }catch(InterruptedException e){
        //예외처리 발생시 로깅후 현재 스레드의 인터럽트 상태 재설정
        Thread.currentThread().interrupt(); //인터럽트 상태복원
        log.error("Failed to close Upbit WebSocket connection cleanly due to interruption: {}", e.getMessage());
      }
    }
    if (scheduler != null && !scheduler.isShutdown()) {

      //scheduler.shutdown(); //Del
      scheduler.shutdownNow(); // [수정] 스케줄러 즉시 종료 (인터럽트)
      log.info("WebSocket reconnection scheduler shut down.");
    }
  }
}