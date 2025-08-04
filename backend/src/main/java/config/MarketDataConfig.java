// backend/src/main/java/coinalarm/Coin_Alarm/config/MarketDataConfig.java
package coinalarm.Coin_Alarm.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker // WebSocket 메시지 브로커를 활성화합니다.
public class MarketDataConfig implements WebSocketMessageBrokerConfigurer {

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    // 클라이언트에게 메시지를 푸시할 때 사용할 접두사를 설정합니다.
    // 클라이언트는 /topic/ 으로 시작하는 주소를 구독하여 메시지를 받습니다.
    config.enableSimpleBroker("/topic");
    // 클라이언트가 서버로 메시지를 보낼 때 사용할 접두사를 설정합니다.
    // 클라이언트는 /app/ 으로 시작하는 주소로 메시지를 보냅니다.
    config.setApplicationDestinationPrefixes("/app");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    // 클라이언트가 WebSocket 연결을 맺을 때 사용할 엔드포인트를 설정합니다.
    // http://localhost:8080/ws 에 연결하여 WebSocket 핸드셰이크를 시작합니다.
    // setAllowedOrigins("*")는 모든 도메인에서의 접속을 허용합니다 (CORS).
    registry.addEndpoint("/ws").setAllowedOrigins("http://localhost:5173").withSockJS(); // SockJS는 WebSocket 지원하지 않는 브라우저를 위한 대체 옵션
  }
}