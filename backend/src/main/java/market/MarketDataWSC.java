// backend/src/main/java/coinalarm/Coin_Alarm/market/MarketDataWebSocketController.java
package coinalarm.Coin_Alarm.market;

import coinalarm.Coin_Alarm.coin.CoinResponseDto;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.List;

@Controller
public class MarketDataWSC {

  private final MarketDataService marketDataService; // 기존 서비스 사용
  private final SimpMessagingTemplate messagingTemplate; // WebSocket 메시지를 클라이언트로 보낼 때 사용

  @Autowired
  public MarketDataWSC(MarketDataService marketDataService, SimpMessagingTemplate messagingTemplate) {
    this.marketDataService = marketDataService;
    this.messagingTemplate = messagingTemplate;
  }

  // 클라이언트가 /app/request-market-data 로 메시지를 보내면 이 메서드가 호출됩니다.
  // 현재는 데이터를 직접 반환하기보다, MarketDataService에서 주기적으로 데이터를 푸시할 예정이므로 이 메서드는 단순화됩니다.
  @MessageMapping("/request-market-data")
  @SendTo("/topic/market-data") // 이 주소를 구독하는 모든 클라이언트에게 응답을 보냅니다.
  public List<CoinResponseDto> sendInitialMarketData() {
    // 초기 데이터를 한번 전송할 때 사용하거나, 클라이언트가 명시적으로 데이터를 요청할 때 사용합니다.
    // 모든 필터를 true로 설정하여 초기에는 모든 코인을 반환하도록 예시
    return marketDataService.getFilteredLiveMarketData(true, true, true, true);
  }

  // MarketDataService에서 실시간 데이터를 받아와 클라이언트에 푸시하는 메서드
  public void pushLiveMarketData(List<CoinResponseDto> coinData) {
    // /topic/market-data 주소를 구독하는 모든 클라이언트에게 데이터를 푸시합니다.
    messagingTemplate.convertAndSend("/topic/market-data", coinData);
  }

  /**
   * 외부에서 알람 메시지를 프론트엔드로 푸시하도록 요청하는 PUBLIC 메서드
   * 이 메서드를 MarketDataService에서 호출합니다.
   */
  public void pushAlarmMessage(String alarmMessage) {
    messagingTemplate.convertAndSend("/topic/alarm-log", alarmMessage);
  }

  /**
   * 외부에서 Top 5 코인 데이터를 프론트엔드로 푸시하도록 요청하는 PUBLIC 메서드
   * 이 메서드를 MarketDataService에서 호출합니다.
   */
  public void pushTop5MarketData(List<CoinResponseDto> top5Coins) { // <--- 새로 추가할 메서드!
    messagingTemplate.convertAndSend("/topic/top-5-market-data", top5Coins);
  }
}