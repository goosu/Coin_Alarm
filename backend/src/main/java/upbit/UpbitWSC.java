package coinalarm.Coin_Alarm.upbit;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import okio.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;
import java.util.stream.Collectors;

@Component
public class UpbitWSC {

  private static final Logger log = LoggerFactory.getLogger(UpbitWSC.class);
  private final OkHttpClient client;
  private WebSocket webSocket;
  private final ObjectMapper objectMapper;
  private Consumer<UpbitTickerResponse> onTradeMessageReceived;

  @SuppressWarnings("unused")
  public UpbitWSC() {
    this.client = new OkHttpClient();
    this.objectMapper = new ObjectMapper();
  }

  public void setOnTradeMessageReceived(Consumer<UpbitTickerResponse> consumer) {
    this.onTradeMessageReceived = consumer;
  }

  public void connect(List<String> subscribeMarketCodes) {
    if (subscribeMarketCodes == null || subscribeMarketCodes.isEmpty()) {
      log.warn("No market codes to subscribe.");
      return;
    }

    Request request = new Request.Builder()
            .url("wss://api.upbit.com/websocket")
            .build();

    webSocket = client.newWebSocket(request, new WebSocketListener() {
      @Override
      public void onOpen(WebSocket webSocket, Response response) {
        log.info("Upbit WebSocket 연결 성공");
        String subscribeMessage = createSubscribeMessage(subscribeMarketCodes);
        webSocket.send(subscribeMessage);
      }

      @Override
      public void onMessage(WebSocket webSocket, String text) {
        log.trace("Received text: {}", text);
      }

      @Override
      public void onMessage(WebSocket webSocket, ByteString bytes) {
        try {
          UpbitTickerResponse ticker = objectMapper.readValue(bytes.utf8(), UpbitTickerResponse.class);
          if (onTradeMessageReceived != null) {
            onTradeMessageReceived.accept(ticker);
          }
        } catch (Exception e) {
          log.error("WebSocket message parsing error: {}", e.getMessage(), e);
        }
      }

      @Override
      public void onClosing(WebSocket webSocket, int code, String reason) {
        log.warn("Upbit WebSocket 연결 종료: code={}, reason={}", code, reason);
      }

      @Override
      public void onFailure(WebSocket webSocket, Throwable t, Response response) {
        log.error("Upbit WebSocket error: {}", t.getMessage(), t);
      }
    });
  }

  private String createSubscribeMessage(List<String> marketCodes) {
    String uuid = UUID.randomUUID().toString();
    String codesJson = marketCodes.stream().map(c -> "\"" + c + "\"").collect(Collectors.joining(","));
    return String.format("[{\"ticket\":\"%s\"},{\"type\":\"trade\",\"codes\":[%s],\"isOnlyRealtime\":true},{\"format\":\"DEFAULT\"}]", uuid, codesJson);
  }

  public void disconnect() {
    if (webSocket != null) {
      webSocket.close(1000, "Client disconnect");
      webSocket = null;
    }
  }
}