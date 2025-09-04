package coinalarm.Coin_Alarm.config;

import org.slf4j.Logger; // Logger 임포트
import org.slf4j.LoggerFactory; // LoggerFactory 임포트
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

// static import log 제거 (잘못된 로깅 방식)
// import static coinalarm.Coin_Alarm.AccessingDataJpaApplication.log; // 이 라인 제거!

@Configuration
@EnableWebSocketMessageBroker // WebSocket 메시지 브로커를 활성화합니다.
public class MarketDataConfig implements WebSocketMessageBrokerConfigurer {

  // 이 클래스 내에서 사용할 로거 인스턴스 생성
  private static final Logger log = LoggerFactory.getLogger(MarketDataConfig.class);

  @Override
  public void configureMessageBroker(MessageBrokerRegistry config) {
    config.enableSimpleBroker("/topic");
    config.setApplicationDestinationPrefixes("/app");
    log.info("✅ Message Broker 설정 완료: /topic (구독), /app (발행)");
  }

  @Override
  public void registerStompEndpoints(StompEndpointRegistry registry) {
    log.info("🔧 STOMP 엔드포인트 등록 중: /ws");
    // 클라이언트가 WebSocket 연결을 맺을 때 사용할 엔드포인트를 설정합니다.
    // http://localhost:8080/ws 에 연결하여 WebSocket 핸드셰이크를 시작합니다.
    // setAllowedOrigins("*")로 모든 도메인에서의 접속을 허용하며,
    // .withSockJS()를 제거하여 순수 WebSocket 클라이언트(App.tsx)와 일치시킵니다.
    registry.addEndpoint("/ws")
            //.setAllowedOrigins("*") // 모든 도메인에서의 접속 허용  ==> EROOR
            //20250827 * 대신 허용할 Origin을 명시적으로 나열하면`allowCredentials is true` 일 때 `*`을 사용할 수 없다는 오류를 해결
            .setAllowedOrigins("http://localhost:5173", "http://127.0.0.1:5173")
            .withSockJS();

    log.info("✅ STOMP 엔드포인트 등록 완료: /ws (순수 WebSocket)");
    // 정확한 Origin을 로깅하려면 여기서 허용된 Origin을 문자열로 직접 출력해야 합니다.
    // 현재 설정은 "*" 이므로 모든 Origin이 허용됩니다.
    log.info("📡 허용된 Origin: 모든 도메인 (*)");
  }
}