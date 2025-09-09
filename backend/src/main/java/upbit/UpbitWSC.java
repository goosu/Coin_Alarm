package coinalarm.Coin_Alarm.upbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import okhttp3.*;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Map; // Map 임포트 추가 (필요한 경우)
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class UpbitWSC {

  private static final Logger log = LoggerFactory.getLogger(UpbitWSC.class);
  private static final String UPBIT_WS_URL = "wss://api.upbit.com/websocket/v1";

  private OkHttpClient client;
  private WebSocket webSocket;
  private final ObjectMapper objectMapper;
  private Consumer<UpbitTickerResponse> onTradeMessageReceived; // 콜백 함수
  private List<String> marketsToSubscribe; // 구독할 시장 목록
  private ScheduledExecutorService scheduler; // 재연결 스케줄러

  private boolean connected = false; // 현재 웹소켓 연결 상태
  private static final long RECONNECT_INTERVAL_SECONDS = 5; // 재연결 시도 간격

  public UpbitWSC(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
    this.client = new OkHttpClient.Builder()
            .readTimeout(Duration.ZERO)
            .pingInterval(Duration.ofSeconds(20))
            .build();
    this.scheduler = Executors.newSingleThreadScheduledExecutor();
  }

  // MarketDataService에서 호출하는 메소드. 이름, 인자 개수, 타입 일치시켜야 함.
  // [핵심1] 이 메소드 이름과 시그니처를 `MarketDataService` 호출부와 일치시킴.
  public void connectWebSocket(List<String> subscribeMarketCodes, Consumer<UpbitTickerResponse> onMessageCallback) {
    this.marketsToSubscribe = subscribeMarketCodes;
    this.onTradeMessageReceived = onMessageCallback; // 받아온 콜백 함수를 내부 필드에 설정
    doConnect(); // 실제 연결 시작
  }

  private void doConnect() {
    if (connected) {
      log.info("Upbit WebSocket is already connected.");
      return;
    }
    if (marketsToSubscribe == null || marketsToSubscribe.isEmpty()) {
      log.warn("Cannot connect to Upbit WebSocket: No market codes to subscribe.");
      return;
    }

    Request request = new Request.Builder()
            .url(UPBIT_WS_URL)
            .build();

    webSocket = client.newWebSocket(request, new WebSocketListener() {
      @Override
      public void onOpen(WebSocket ws, Response response) {
        log.info("Upbit WebSocket 연결 성공!");
        connected = true;
        // 연결 성공 시, 구독 메시지 전송
        String subscribeMessage = createSubscribeMessage(marketsToSubscribe);
        ws.send(subscribeMessage);
        // 재연결 스케줄러가 있다면 종료 (연결되었으므로 더이상 필요 없음)
        scheduler.shutdownNow();
        scheduler = Executors.newSingleThreadScheduledExecutor(); // 새로운 스케줄러 인스턴스 생성
      }

      @Override
      public void onMessage(WebSocket ws, String text) {
        log.trace("Received text message: {}", text);
      }

      @Override
      public void onMessage(WebSocket ws, ByteString bytes) {
        try {
          // 체결 데이터는 바이너리로 옴: utf8 디코딩 후 UpbitTickerResponse로 파싱
          UpbitTickerResponse ticker = objectMapper.readValue(bytes.utf8(), UpbitTickerResponse.class);
          if (onTradeMessageReceived != null) {
            onTradeMessageReceived.accept(ticker);
          }
        } catch (Exception e) {
          log.error("WebSocket message parsing error: {}", e.getMessage(), e);
        }
      }

      @Override
      public void onClosing(WebSocket ws, int code, String reason) {
        log.warn("Upbit WebSocket is closing. Code: {}, Reason: {}", code, reason);
      }

      @Override
      public void onClosed(WebSocket ws, int code, String reason) {
        log.warn("Upbit WebSocket closed. Code: {}, Reason: {}", code, reason);
        connected = false;
        // 비정상 종료 (1000이 아님) 시 재연결 시도
        if (code != 1000) {
          log.info("Attempting to reconnect Upbit WebSocket in {} seconds...", RECONNECT_INTERVAL_SECONDS);
          scheduleReconnect();
        } else {
          log.info("Upbit WebSocket closed normally.");
        }
      }

      @Override
      public void onFailure(WebSocket ws, Throwable t, Response response) {
        String responseInfo = (response != null) ? "Code: " + response.code() + ", Message: " + response.message() : "No HTTP Response";
        log.error("Upbit WebSocket connection failed! Error: {}, Response: {}", t.getMessage(), responseInfo, t);
        connected = false;
        // 연결 실패 시 재연결 시도
        log.info("Attempting to reconnect Upbit WebSocket in {} seconds due to failure...", RECONNECT_INTERVAL_SECONDS);
        scheduleReconnect();
      }
    });
  }

  // [핵심2] 구독 메시지 JSON 생성: 롱터우님 오리지널 코드 로직으로 완벽 복원!
  private String createSubscribeMessage(List<String> marketCodes) {
    String uuid = UUID.randomUUID().toString();
    // marketCodes 리스트를 JSON 배열 문자열로 변환 (예: "KRW-BTC","KRW-ETH")
    String codesJson = marketCodes.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(","));

    // 롱터우님 오리지널 코드의 구독 메시지 형식 그대로 사용
    return String.format("[{\"ticket\":\"%s\"},{\"type\":\"trade\",\"codes\":[%s],\"isOnlyRealtime\":true},{\"format\":\"DEFAULT\"}]", uuid, codesJson);
  }

  // 재연결 스케줄링
  private void scheduleReconnect() {
    if (scheduler.isShutdown()) {
      // 스케줄러가 종료되었으면 새로 시작 (shutdownNow 후 다시 사용 불가)
      scheduler = Executors.newSingleThreadScheduledExecutor();
    }
    scheduler.schedule(this::doConnect, RECONNECT_INTERVAL_SECONDS, TimeUnit.SECONDS);
  }

  @PreDestroy
  public void disconnect() {
    if (webSocket != null) {
      webSocket.close(1000, "Client disconnect");
    }
    // OkHttpClient 내부 스레드 풀 및 스케줄러 종료
    if (client != null) {
      client.dispatcher().executorService().shutdown();
      client.connectionPool().evictAll();
    }
    if (scheduler != null && !scheduler.isShutdown()) {
      scheduler.shutdownNow();
    }
    log.info("Upbit WebSocket resources released.");
  }
}

//upbit 주소지가 잘못되어있었음 음... 어떻게 찾아야했지?
//package coinalarm.Coin_Alarm.upbit;
//
//import coinalarm.Coin_Alarm.upbit.UpbitTickerResponse;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import jakarta.annotation.PostConstruct; // @PostConstruct
//import jakarta.annotation.PreDestroy; // @PreDestroy
//import lombok.extern.slf4j.Slf4j;
//import okhttp3.OkHttpClient; // okhttp 클라이언트
//import okhttp3.Request;     // okhttp 요청
//import okhttp3.Response;    // okhttp 응답
//import okhttp3.WebSocket;   // okhttp 웹소켓
//import okhttp3.WebSocketListener; // okhttp 웹소켓 리스너
//import okio.ByteString;     // okhttp 바이트 스트링
//
//import org.springframework.stereotype.Component;
//
//import java.time.Duration; // 연결 재시도 딜레이용
//import java.util.List;
//import java.util.Map;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;
//import java.util.function.Consumer;
//import java.util.stream.Collectors;
//
//@Slf4j
//@Component
//public class UpbitWSC {
//
//  private WebSocket webSocket; // okhttp의 WebSocket 객체
//  private OkHttpClient okHttpClient; // okhttp 클라이언트 객체
//  private final ObjectMapper objectMapper;
//  private Consumer<UpbitTickerResponse> onMessageCallback;
//  private List<String> marketsToSubscribe;
//  private ScheduledExecutorService scheduler; // 재연결 스케줄러
//
//  private boolean connected = false; // 연결 상태 추적 (옵션)
//  private static final long RECONNECT_INTERVAL_SECONDS = 5; // 재연결 시도 간격
//
//  public UpbitWSC(ObjectMapper objectMapper) {
//    this.objectMapper = objectMapper;
//    // OkHttpClient 초기화: 연결 타임아웃 및 WebSocket 관련 설정
//    this.okHttpClient = new OkHttpClient.Builder()
//            .readTimeout(Duration.ZERO) // WebSocket은 read timeout이 없어야 함
//            .pingInterval(Duration.ofSeconds(20)) // 핑-퐁 간격
//            .build();
//    this.scheduler = Executors.newSingleThreadScheduledExecutor();
//  }
//
//  // 초기화 메소드 (MarketDataService에서 호출)
//  public void connectWebSocket(List<String> markets, Consumer<UpbitTickerResponse> onMessageCallback) {
//    this.marketsToSubscribe = markets;
//    this.onMessageCallback = onMessageCallback;
//    doConnect(); // 실제 연결 시작
//  }
//
//  private void doConnect() {
//    if (connected) {
//      log.info("WebSocket is already connected.");
//      return;
//    }
//
//    // 요청 빌드: Upbit WebSocket URI 사용
//    Request request = new Request.Builder()
//            .url("wss://api.upbit.com/websocket") // [확인] 이 주소가 정확한지 다시 한번 확인
//            .build();
//
//    // WebSocketListener 구현
//    webSocket = okHttpClient.newWebSocket(request, new WebSocketListener() {
//      @Override
//      public void onOpen(WebSocket webSocket, Response response) {
//        log.info("Upbit WebSocket connected successfully! Response: {}", response.code());
//        connected = true;
//        // 연결 성공 시 구독 메시지 전송
//        sendSubscriptionMessage(marketsToSubscribe);
//        // 재연결 스케줄러가 있다면 종료 (이미 연결되었으므로)
//        scheduler.shutdownNow(); // 이전에 스케줄된 모든 재연결 시도를 취소
//        scheduler = Executors.newSingleThreadScheduledExecutor(); // 새로운 스케줄러 인스턴스 생성 (선택 사항)
//      }
//
//      @Override
//      public void onMessage(WebSocket webSocket, String text) {
//        try {
//          UpbitTickerResponse ticker = objectMapper.readValue(text, UpbitTickerResponse.class);
//          if (onMessageCallback != null) {
//            onMessageCallback.accept(ticker);
//          }
//        } catch (Exception e) {
//          log.error("WebSocket message parsing error: {}", e.getMessage(), e);
//        }
//      }
//
//      @Override
//      public void onMessage(WebSocket webSocket, ByteString bytes) {
//        // 바이너리 메시지 처리 (Upbit은 주로 텍스트 메시지)
//        log.warn("Received binary message: {}", bytes.hex());
//      }
//
//      @Override
//      public void onClosing(WebSocket webSocket, int code, String reason) {
//        log.warn("Upbit WebSocket is closing. Code: {}, Reason: {}", code, reason);
//      }
//
//      @Override
//      public void onClosed(WebSocket webSocket, int code, String reason) {
//        log.warn("Upbit WebSocket closed. Code: {}, Reason: {}", code, reason);
//        connected = false;
//        // 비정상 종료 시 재연결 스케줄
//        if (code != 1000) { // 1000은 정상 종료 코드
//          log.info("Attempting to reconnect Upbit WebSocket in {} seconds...", RECONNECT_INTERVAL_SECONDS);
//          scheduleReconnect();
//        } else {
//          log.info("Upbit WebSocket closed normally.");
//        }
//      }
//
//      @Override
//      public void onFailure(WebSocket webSocket, Throwable t, Response response) {
//        String responseInfo = (response != null) ? "Code: " + response.code() + ", Message: " + response.message() : "No HTTP Response";
//        log.error("Upbit WebSocket connection failed! Error: {}, Response: {}", t.getMessage(), responseInfo, t);
//        connected = false;
//        // 실패 시 재연결 스케줄
//        log.info("Attempting to reconnect Upbit WebSocket in {} seconds due to failure...", RECONNECT_INTERVAL_SECONDS);
//        scheduleReconnect();
//      }
//    });
//  }
//
//  // 구독 메시지 전송
//  private void sendSubscriptionMessage(List<String> markets) {
//    if (!connected || webSocket == null) {
//      log.warn("Cannot send subscription message: WebSocket not connected.");
//      return;
//    }
//    if (markets == null || markets.isEmpty()) {
//      log.warn("No markets to subscribe to.");
//      return;
//    }
//
//    List<Map<String, String>> codes = markets.stream()
//            .map(m -> Map.of("market", m))
//            .collect(Collectors.toList());
//
//    Map<String, Object> subscribeMessage = Map.of(
//            "ticket", "test-ticket",
//            "type", "ticker", // Upbit 티커 스트림
//            "codes", codes,
//            "isOnlySnapshot", false,
//            "isOnlyRealtime", true
//    );
//    try {
//      String jsonMessage = objectMapper.writeValueAsString(subscribeMessage);
//      webSocket.send(jsonMessage); // okhttp의 send 메소드
//      log.info("Subscription message sent: {}", jsonMessage);
//    } catch (Exception e) {
//      log.error("Failed to send subscription message: {}", e.getMessage());
//    }
//  }
//
//  // 재연결 스케줄러
//  private void scheduleReconnect() {
//    if (!scheduler.isShutdown()) {
//      scheduler.schedule(this::doConnect, RECONNECT_INTERVAL_SECONDS, TimeUnit.SECONDS);
//    }
//  }
//
//  @PreDestroy
//  public void close() {
//    if (webSocket != null) {
//      log.info("Closing Upbit WebSocket connection...");
//      webSocket.cancel(); // 연결 종료 및 리소스 해제
//    }
//    if (scheduler != null && !scheduler.isShutdown()) {
//      scheduler.shutdownNow();
//      log.info("WebSocket reconnection scheduler shut down.");
//    }
//    if (okHttpClient != null) {
//      okHttpClient.dispatcher().executorService().shutdown(); // OkHttpClient 내부 스레드풀 종료
//      okHttpClient.connectionPool().evictAll(); // 연결 풀의 모든 연결 제거
//    }
//    connected = false;
//  }
//}




//java WebSocketClient전용 주석처리
//package coinalarm.Coin_Alarm.upbit;
//
//import coinalarm.Coin_Alarm.upbit.UpbitTickerResponse; // UpbitTickerResponse 임포트
//import com.fasterxml.jackson.databind.ObjectMapper; // JSON 파싱을 위한 ObjectMapper
//import jakarta.annotation.PreDestroy; // [수정] PreDestroy로 변경
//import lombok.extern.slf4j.Slf4j;
//import org.java_websocket.client.WebSocketClient;
//import org.java_websocket.handshake.ServerHandshake;
//import org.springframework.stereotype.Component;
//
//import java.net.URI;
//import java.net.URISyntaxException;
//import java.nio.ByteBuffer;
//import java.util.List;
//import java.util.Map; // Map 임포트
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;
//import java.util.function.Consumer; // Consumer 임포트
//import java.util.stream.Collectors; // Collectors 임포트
//
//@Slf4j // 로깅을 위한 Lombok 어노테이션
//@Component
//public class UpbitWSC {
//
//  private WebSocketClient webSocketClient;
//  private final ObjectMapper objectMapper; // JSON 파싱용 ObjectMapper
//  private Consumer<UpbitTickerResponse> onMessageCallback; // [추가] 메시지 콜백 함수
//  private List<String> marketsToSubscribe; // [추가] 구독할 시장 목록
//  private ScheduledExecutorService scheduler; // [추가] 스케줄러로 재연결 관리
//
//  public UpbitWSC(ObjectMapper objectMapper) {
//    this.objectMapper = objectMapper;
//    this.scheduler = Executors.newSingleThreadScheduledExecutor(); // [추가] 스케줄러 초기화
//  }
//
//  // [변경] connectWebSocket 메소드의 시그니처를 MarketDataService에서 호출하는 것과 일치시킵니다.
//  // 시장 목록(markets)과 메시지가 올 때 호출될 콜백 함수(onMessageCallback)를 인자로 받습니다.
//  public void connectWebSocket(List<String> markets, Consumer<UpbitTickerResponse> onMessageCallback) {
//    this.onMessageCallback = onMessageCallback;
//    this.marketsToSubscribe = markets;
//    doConnect(); // 실제 웹소켓 연결 시작
//  }
//
//  private void doConnect() {
//    try {
//      // [수정] Web URI 설정 (Upbit 웹소켓 주소)
//      webSocketClient = new WebSocketClient(new URI("wss://api.upbit.com/websocket")) {
//        @Override
//        public void onOpen(ServerHandshake handshakedata) {
//          log.info("Upbit WebSocket connected. Status: {}", handshakedata.getHttpStatusMessage());
//          // [수정] 연결 성공 시 구독 메시지 전송
//          sendSubscriptionMessage(marketsToSubscribe);
//        }
//
//        @Override
//        public void onMessage(String message) {
//          try {
//            // [수정] JSON 파싱 및 콜백 호출
//            UpbitTickerResponse ticker = objectMapper.readValue(message, UpbitTickerResponse.class);
//            if (onMessageCallback != null) {
//              onMessageCallback.accept(ticker);
//            }
//          } catch (Exception e) {
//            log.error("WebSocket message parsing error: {}", e.getMessage(), e);
//          }
//        }
//
//        @Override
//        public void onMessage(ByteBuffer bytes) {
//          // 바이너리 메시지 처리 (필요시 구현)
//          onMessage(new String(bytes.array()));
//        }
//
//        @Override
//        public void onClose(int code, String reason, boolean remote) {
//          log.warn("Upbit WebSocket closed. Code: {}, Reason: {}, Remote: {}", code, reason, remote);
//          // [수정] 연결 끊겼을 때 재연결 로직 추가
//          if (code != 1000) { // 정상 종료(1000)가 아니면 재연결 시도
//            log.info("Attempting to reconnect Upbit WebSocket in 5 seconds...");
//            scheduler.schedule(UpbitWSC.this::doConnect, 5, TimeUnit.SECONDS);
//          }
//        }
//
//        @Override
//        public void onError(Exception ex) {
//          log.error("Upbit WebSocket error: {}", ex.getMessage(), ex);
//          // [수정] 에러 발생 시 재연결 시도
//          log.info("Attempting to reconnect Upbit WebSocket in 5 seconds due to error...");
//          scheduler.schedule(UpbitWSC.this::doConnect, 5, TimeUnit.SECONDS);
//        }
//      };
//      webSocketClient.connect(); // 웹소켓 연결 시작
//    } catch (URISyntaxException e) {
//      log.error("Invalid WebSocket URI: {}", e.getMessage());
//    }
//  }
//
//  // [추가] 구독 메시지 전송 메소드
//  private void sendSubscriptionMessage(List<String> markets) {
//    if (markets == null || markets.isEmpty()) {
//      log.warn("No markets to subscribe to.");
//      return;
//    }
//
//    List<Map<String, String>> marketCodes = markets.stream()
//            .map(m -> Map.of("market", m))
//            .collect(Collectors.toList());
//
//    Map<String, Object> subscribeMessage = Map.of(
//            "ticket", "test-ticket", // 고유한 티켓 값
//            "type", "ticker", // [수정] 구독할 데이터 타입: ticker
//            "codes", marketCodes, // 구독할 시장 코드 목록
//            "isOnlySnapshot", false, // 스냅샷만 받을지 여부
//            "isOnlyRealtime", true // 실시간 스트리밍만 받을지 여부
//    );
//    try {
//      String jsonMessage = objectMapper.writeValueAsString(subscribeMessage);
//      webSocketClient.send(jsonMessage);
//      log.info("Subscription message sent: {}", jsonMessage);
//    } catch (Exception e) {
//      log.error("Failed to send subscription message: {}", e.getMessage());
//    }
//  }
//
//  // 애플리케이션 종료 시 웹소켓 연결 종료
//  @PreDestroy // [수정] PostConstruct가 아닌 PreDestroy로 변경해야 함.
//  public void close() {
//    if (webSocketClient != null && webSocketClient.isOpen()) {
//      try {
//        webSocketClient.closeBlocking(); // 블록킹 방식으로 웹소켓 연결 종료
//        log.info("Upbit WebSocket connection closed.");
//      }catch(InterruptedException e){
//        //예외처리 발생시 로깅후 현재 스레드의 인터럽트 상태 재설정
//        Thread.currentThread().interrupt(); //인터럽트 상태복원
//        log.error("Failed to close Upbit WebSocket connection cleanly due to interruption: {}", e.getMessage());
//      }
//    }
//    if (scheduler != null && !scheduler.isShutdown()) {
//
//      //scheduler.shutdown(); //Del
//      scheduler.shutdownNow(); // [수정] 스케줄러 즉시 종료 (인터럽트)
//      log.info("WebSocket reconnection scheduler shut down.");
//    }
//  }
//}
