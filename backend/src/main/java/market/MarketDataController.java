package coinalarm.Coin_Alarm.market;

import coinalarm.Coin_Alarm.coin.CoinResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate; // <-- 추가!
import org.springframework.scheduling.annotation.Scheduled; // <-- 추가!
import org.springframework.stereotype.Controller; // <-- @RestController 대신 @Controller로 변경!
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map; // Map 임포트 (sendMarketData에서 사용)
import java.util.Set; // Set 임포트

@Controller // <-- @RestController 대신 @Controller 사용
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173") // 프론트엔드 URL에 맞게 설정
public class MarketDataController {

  private final MarketDataService marketDataService;
  private final SimpMessagingTemplate simpMessagingTemplate; // <-- 추가!

  @Autowired
  public MarketDataController(MarketDataService marketDataService, SimpMessagingTemplate simpMessagingTemplate) { // <-- SimpMessagingTemplate 주입
    this.marketDataService = marketDataService;
    this.simpMessagingTemplate = simpMessagingTemplate; // <-- 주입받은 객체 할당
  }

  // 기존 REST API 엔드포인트들은 그대로 유지
  @GetMapping("/market-data")
  public ResponseEntity<List<CoinResponseDto>> getLiveMarketData(
          @RequestParam(defaultValue = "true") boolean all,
          @RequestParam(defaultValue = "false") boolean large,
          @RequestParam(defaultValue = "false") boolean mid,
          @RequestParam(defaultValue = "false") boolean small) {
    List<CoinResponseDto> data = marketDataService.getFilteredLiveMarketData(all, large, mid, small);
    return ResponseEntity.ok(data);
  }

  @PostMapping("/favorites/add")
  public ResponseEntity<String> addFavorite(@RequestParam String marketCode) {
    boolean added = marketDataService.addFavoriteMarket(marketCode);
    if (added) {
      return ResponseEntity.ok(marketCode + " added to favorites.");
    } else {
      return ResponseEntity.badRequest().body(marketCode + " already in favorites or invalid.");
    }
  }

  @DeleteMapping("/favorites/remove")
  public ResponseEntity<String> removeFavorite(@RequestParam String marketCode) {
    boolean removed = marketDataService.removeFavoriteMarket(marketCode);
    if (removed) {
      return ResponseEntity.ok(marketCode + " removed from favorites.");
    } else {
      return ResponseEntity.badRequest().body(marketCode + " not found in favorites.");
    }
  }

  @GetMapping("/favorites")
  public ResponseEntity<Set<String>> getFavorites() {
    return ResponseEntity.ok(marketDataService.getFavoriteMarkets());
  }

  // =========================================================
  // 실시간 시장 데이터 WebSocket 발행 로직 (!!! 중요 !!!)
  // =========================================================
  @Scheduled(fixedRate = 3000) // 3초마다 실행 (Upbit API Rate Limit 고려)
  public void sendMarketData() { // throws JsonProcessingException 제거
    try {
      // 시장 데이터 가져오기 (MarketDataService.getMarketData가 Upbit API 호출)
      // 이 메소드가 Map<String, Object> 또는 List<CoinResponseDto> 등을 반환하도록 수정되어야 함
      // 현재 Gist의 MarketDataService.java에는 이 메소드가 없음.
      // MarketDataService에 getLiveMarketData 또는 getMarketData와 같은 메소드 추가 필요.
      Map<String, Object> marketData = marketDataService.getMarketData(); // 이 메소드 필요!

      // WebSocket으로 데이터 전송
      simpMessagingTemplate.convertAndSend("/topic/marketData", marketData); // STOMP Topic 발행
      System.out.println("✅ WebSocket 데이터 발행 성공: " + marketData.size() + "개 코인 데이터"); // 콘솔 로그 추가
    } catch (JsonProcessingException e) {
      System.err.println("❌ MarketDataController - JSON 처리 오류: " + e.getMessage());
    } catch (Exception e) {
      System.err.println("❌ MarketDataController - 데이터 발행 중 알 수 없는 오류: " + e.getMessage());
      e.printStackTrace();
    }
  }
}