// backend/src/main/java/coinalarm/Coin_Alarm/upbit/UpbitWSC.java
package coinalarm.Coin_Alarm.upbit;

import com.fasterxml.jackson.databind.ObjectMapper; // JSON 파싱용
import okhttp3.*; // OkHttp WebSocket 클라이언트
import okio.ByteString; // <--- 이 줄을 이렇게 수정하세요! (okhttp3 대신 okio 사용)
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.stream.Collectors;


import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer; // Consumer 인터페이스 임포트

// Upbit WebSocket으로부터 실시간 데이터를 받아오는 클라이언트
@Component
public class UpbitWSC {

  private static final Logger log = LoggerFactory.getLogger(UpbitWSC.class);
  private final OkHttpClient client;
  private WebSocket webSocket;
  private final ObjectMapper objectMapper;
  private final UpbitClient upbitClient; // REST API 클라이언트를 사용하여 마켓 코드 가져오기

  // 수신된 데이터를 MarketDataService로 전달하기 위한 콜백
  private Consumer<UpbitTickerResponse> onTradeMessageReceived;

  // 실시간 누적 거래량 계산을 위한 임시 저장소 (코인 심볼 -> Accumulated Volume Map)
  private ConcurrentMap<String, Double> oneMinuteVolumeMap = new ConcurrentHashMap<>();
  private ConcurrentMap<String, Long> lastUpdateTimeMap = new ConcurrentHashMap<>(); // 마지막 업데이트 시간 (ms)

  @Autowired
  public UpbitWSC(UpbitClient upbitClient) {
    this.client = new OkHttpClient();
    this.objectMapper = new ObjectMapper();
    this.upbitClient = upbitClient;
  }

  // 외부에서 메시지 수신 시 호출될 콜백 설정
  public void setOnTradeMessageReceived(Consumer<UpbitTickerResponse> consumer) {
    this.onTradeMessageReceived = consumer;
  }

  /**
   * Upbit WebSocket 연결을 수립하고 실시간 데이터를 구독합니다.
   * @param subscribeMarketCodes 구독할 마켓 코드 리스트 (예: ["KRW-BTC", "KRW-ETH"])
   */
  public void connect(List<String> subscribeMarketCodes) {
    if (subscribeMarketCodes.isEmpty()) {
      log.warn("구독할 마켓 코드가 없습니다.");
      return;
    }

    Request request = new Request.Builder()
            .url("wss://api.upbit.com/websocket")
            .build();

    webSocket = client.newWebSocket(request, new WebSocketListener() {
      @Override
      public void onOpen(WebSocket webSocket, Response response) {
        log.info("Upbit WebSocket 연결 성공");
        // 연결 성공 시, 구독 요청 메시지 전송
        String subscribeMessage = createSubscribeMessage(subscribeMarketCodes);
        webSocket.send(subscribeMessage);
      }

      @Override
      public void onMessage(WebSocket webSocket, String text) {
        // 텍스트 메시지는 보통 Ping/Pong 또는 오류 메시지일 수 있습니다.
        // Upbit 실시간 데이터는 바이너리 메시지로 옵니다.
        log.trace("Received text: {}", text);
      }

      @Override
      public void onMessage(WebSocket webSocket, ByteString bytes) {
        // 바이너리 메시지 처리 (실시간 데이터)
        try {
          UpbitTickerResponse ticker = objectMapper.readValue(bytes.utf8(), UpbitTickerResponse.class);
          // onTradeMessageReceived 콜백이 설정되어 있다면 전달
          if (onTradeMessageReceived != null) {
            //log.info("Received real-time trade for {}: {} {}", ticker.getMarket(), ticker.getTradePrice(), ticker.getTradeVolume());
            onTradeMessageReceived.accept(ticker);
          }
        } catch (Exception e) {
          log.error("WebSocket message parsing error: {}", e.getMessage(), e);
        }
      }

      @Override
      public void onClosing(WebSocket webSocket, int code, String reason) {
        log.warn("Upbit WebSocket 연결 종료 중: code={}, reason={}", code, reason);
      }

      @Override
      public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        log.error("Upbit WebSocket 연결 실패/오류: {}", t.getMessage(), t);
        // 연결 실패 시 재연결 로직 추가 고려
      }

      @Override
      public void onClosed(WebSocket webSocket, int code, String reason) {
        log.info("Upbit WebSocket 연결 닫힘: code={}, reason={}", code, reason);
      }
    });
  }

  /**
   * WebSocket 구독 요청 메시지 생성
   * @param marketCodes 구독할 마켓 코드 리스트
   * @return JSON 형식의 구독 메시지 문자열
   */
  private String createSubscribeMessage(List<String> marketCodes) {
    String uuid = UUID.randomUUID().toString(); // 각 요청에 대한 고유 ID
    String codesJson = marketCodes.stream()
            .map(code -> String.format("\"%s\"", code))
            .collect(Collectors.joining(","));

    // trade: 실시간 체결, ticker: 실시간 시세 (UpbitTickerResponse는 ticker 메시지에도 해당)
    // isOnlyRealtime: true 설정 시 과거 데이터 제외하고 실시간 데이터만 받음
    return String.format("[{\"ticket\":\"%s\"},{\"type\":\"ticker\",\"codes\":[%s],\"isOnlyRealtime\":true},{\"format\":\"DEFAULT\"}]", uuid, codesJson);
  }


  /**
   * WebSocket 연결을 종료합니다.
   */
  public void disconnect() {
    if (webSocket != null) {
      webSocket.close(1000, "Client disconnect"); // 정상 종료 코드 1000
      webSocket = null;
    }
  }
}