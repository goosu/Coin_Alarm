package config;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig {
  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic"); //서버->클라이언트
    config.setApplicationDestinationPrefixes("/app");    //클라이언트 -> 서버
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) { //클라이언트가 WebSocket 연결을 맺을 주소를 정의
    registry.addEndpoint("/ws") //클라이언트에서 여기로 등록하면 통로열어줄게 하는것
            .setAllowedOrigins("http://localhost:5173")
            .withSockJS();
  }
}
