package coinalarm.Coin_Alarm.market; // 정확한 패키지 경로를 사용하세요!

import coinalarm.Coin_Alarm.coin.CoinResponseDto; // CoinResponseDto DTO를 사용하기 위해 필요
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입을 위해 필요
import org.springframework.http.ResponseEntity; // HTTP 응답을 위해 필요
import org.springframework.web.bind.annotation.CrossOrigin; // CORS 설정을 위해 필요
import org.springframework.web.bind.annotation.DeleteMapping; // HTTP DELETE 요청 처리
import org.springframework.web.bind.annotation.GetMapping; // HTTP GET 요청 처리
import org.springframework.web.bind.annotation.PostMapping; // HTTP POST 요청 처리
import org.springframework.web.bind.annotation.RequestMapping; // 기본 URL 경로 설정
import org.springframework.web.bind.annotation.RequestParam; // 쿼리 파라미터 받기
import org.springframework.web.bind.annotation.RestController; // RESTful 웹 서비스 컨트롤러임을 명시

import java.util.List; // List 타입을 사용하기 위해 필요
import java.util.Set; // Set 타입을 사용하기 위해 필요

/**
 * MarketDataController
 * - Upbit에서 가져온 시장 데이터를 프론트엔드에 REST API 형태로 제공합니다.
 * - 즐겨찾기 코인 관리 API를 제공합니다.
 * - 실시간 WebSocket 데이터 발행은 MarketDataService의 @Scheduled 메소드에서 담당합니다. (이 컨트롤러에서는 제거)
 */
@RestController // <-- [수정] @Controller 대신 @RestController 사용 (REST API만 제공하므로)
@RequestMapping("/api") // 이 컨트롤러의 모든 메서드는 "/api" 경로 아래에서 시작합니다.
@CrossOrigin(origins = "http://localhost:5173") // 프론트엔드 개발 서버의 요청을 허용합니다. (실제 배포 시 변경 필요)
public class MarketDataController {

  private final MarketDataService marketDataService; // MarketDataService를 주입받습니다.

  // *** [수정] SimpMessagingTemplate 주입 제거 ***
  // private final SimpMessagingTemplate simpMessagingTemplate; // <-- [기존] 추가!

  @Autowired // 생성자를 통한 의존성 주입. 스프링이 MarketDataService 인스턴스를 자동으로 주입합니다.
  // *** [수정] SimpMessagingTemplate 의존성 제거 ***
  public MarketDataController(MarketDataService marketDataService) { // <-- [기존] (..., SimpMessagingTemplate simpMessagingTemplate) 이었음
    this.marketDataService = marketDataService;
    // this.simpMessagingTemplate = simpMessagingTemplate; // <-- [기존] 주입받은 객체 할당
  }

  /**
   * 라이브 시장 데이터를 필터링하여 반환하는 API 엔드포인트입니다.
   * MarketDataService에서 처리된 데이터를 가져와 CoinResponseDto 리스트 형태로 반환합니다.
   * 예: GET http://localhost:8080/api/market-data?all=true
   */
  @GetMapping("/market-data") // "/api/market-data" 경로로 들어오는 GET 요청을 처리합니다.
  public ResponseEntity<List<CoinResponseDto>> getLiveMarketData(
          @RequestParam(defaultValue = "true") boolean all, // 'all' 쿼리 파라미터 (기본값 true)
          @RequestParam(defaultValue = "false") boolean large, // 'large' 쿼리 파라미터 (기본값 false)
          @RequestParam(defaultValue = "false") boolean mid,   // 'mid' 쿼리 파라미터 (기본값 false)
          @RequestParam(defaultValue = "false") boolean small  // 'small' 쿼리 파라미터 (기본값 false)
  ) {
    // MarketDataService의 getFilteredLiveMarketData 메소드를 호출하여 필터링된 데이터를 가져옵니다.
    List<CoinResponseDto> data = marketDataService.getFilteredLiveMarketData(all, large, mid, small);
    // HTTP 200 OK 상태 코드와 함께 데이터 리스트를 응답합니다.
    return ResponseEntity.ok(data);
  }

  // =========================================================
  // 즐겨찾기 관리 API
  // =========================================================

  /**
   * 지정된 마켓 코드를 즐겨찾기에 추가합니다.
   * 예: POST http://localhost:8080/api/favorites/add?marketCode=KRW-BTC
   */
  @PostMapping("/favorites/add") // "/api/favorites/add" 경로로 들어오는 POST 요청을 처리합니다.
  public ResponseEntity<String> addFavorite(@RequestParam String marketCode) {
    boolean added = marketDataService.addFavoriteMarket(marketCode); // MarketDataService를 통해 즐겨찾기 추가
    if (added) {
      return ResponseEntity.ok(marketCode + " added to favorites."); // 성공 응답
    } else {
      return ResponseEntity.badRequest().body(marketCode + " already in favorites or invalid."); // 실패 응답 (이미 있거나 유효하지 않음)
    }
  }

  /**
   * 지정된 마켓 코드를 즐겨찾기에서 제거합니다.
   * 예: DELETE http://localhost:8080/api/favorites/remove?marketCode=KRW-BTC
   */
  @DeleteMapping("/favorites/remove") // "/api/favorites/remove" 경로로 들어오는 DELETE 요청을 처리합니다.
  public ResponseEntity<String> removeFavorite(@RequestParam String marketCode) {
    boolean removed = marketDataService.removeFavoriteMarket(marketCode); // MarketDataService를 통해 즐겨찾기 제거
    if (removed) {
      return ResponseEntity.ok(marketCode + " removed from favorites."); // 성공 응답
    } else {
      return ResponseEntity.badRequest().body(marketCode + " not found in favorites."); // 실패 응답 (찾을 수 없음)
    }
  }

  /**
   * 현재 저장된 모든 즐겨찾기 마켓 코드 목록을 반환합니다.
   * 예: GET http://localhost:8080/api/favorites
   */
  @GetMapping("/favorites") // "/api/favorites" 경로로 들어오는 GET 요청을 처리합니다.
  public ResponseEntity<Set<String>> getFavorites() {
    // MarketDataService의 getFavoriteMarkets 메소드를 호출하여 즐겨찾기 목록을 가져옵니다.
    return ResponseEntity.ok(marketDataService.getFavoriteMarkets()); // 즐겨찾기 목록 응답
  }

  // =========================================================
  // 실시간 시장 데이터 WebSocket 발행 로직 (!!! 이 컨트롤러에서는 제거됩니다 !!!)
  // =========================================================
//  @Scheduled(fixedRate = 3000) // [기존 주석 처리된 코드] @Scheduled 어노테이션
//  public void sendMarketData() { // [기존 주석 처리된 코드] 메소드 선언
//    try {
//      // ... (기존 주석 처리된 WebSocket 발행 로직 내용) ...
//      Map<String, Object> marketData = marketDataService.getMarketData(); // <-- [문제] MarketDataService에 없는 메소드 호출
//      simpMessagingTemplate.convertAndSend("/topic/marketData", marketData); // <-- [기존 주석 처리된 코드] simpMessagingTemplate 사용
//    } catch (JsonProcessingException e) {
//      // ...
//    } catch (Exception e) {
//      // ...
//    }
//  }
  // 참고: 이전에 MarketDataController에 있던 @Scheduled 메소드는 MarketDataService의 pushLatestMarketDataToClients() 메소드로 완전히 이전되었습니다.
  // 이 컨트롤러는 이제 순수 REST API 엔드포인트만 제공합니다.
}



//현재 코드로하면 역할과 책임이 불분명해지고, 데이터가 꼬일위험이 커진다
//package coinalarm.Coin_Alarm.market;
//
//import coinalarm.Coin_Alarm.coin.CoinResponseDto;
//import com.fasterxml.jackson.core.JsonProcessingException;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.http.ResponseEntity;
//import org.springframework.messaging.simp.SimpMessagingTemplate; // <-- 추가!
//import org.springframework.scheduling.annotation.Scheduled; // <-- 추가!
//import org.springframework.stereotype.Controller; // <-- @RestController 대신 @Controller로 변경!
//import org.springframework.web.bind.annotation.*;
//
//import java.util.List;
//import java.util.Map; // Map 임포트 (sendMarketData에서 사용)
//import java.util.Set; // Set 임포트
//
//@Controller // <-- @RestController 대신 @Controller 사용
//@RequestMapping("/api")
//@CrossOrigin(origins = "http://localhost:5173") // 프론트엔드 URL에 맞게 설정
//public class MarketDataController {
//
//  private final MarketDataService marketDataService;
//  private final SimpMessagingTemplate simpMessagingTemplate; // <-- 추가!
//
//  @Autowired
//  public MarketDataController(MarketDataService marketDataService, SimpMessagingTemplate simpMessagingTemplate) { // <-- SimpMessagingTemplate 주입
//    this.marketDataService = marketDataService;
//    this.simpMessagingTemplate = simpMessagingTemplate; // <-- 주입받은 객체 할당
//  }
//
//  // 기존 REST API 엔드포인트들은 그대로 유지
//  @GetMapping("/market-data")
//  public ResponseEntity<List<CoinResponseDto>> getLiveMarketData(
//          @RequestParam(defaultValue = "true") boolean all,
//          @RequestParam(defaultValue = "false") boolean large,
//          @RequestParam(defaultValue = "false") boolean mid,
//          @RequestParam(defaultValue = "false") boolean small) {
//    List<CoinResponseDto> data = marketDataService.getFilteredLiveMarketData(all, large, mid, small);
//    return ResponseEntity.ok(data);
//  }
//
//  @PostMapping("/favorites/add")
//  public ResponseEntity<String> addFavorite(@RequestParam String marketCode) {
//    boolean added = marketDataService.addFavoriteMarket(marketCode);
//    if (added) {
//      return ResponseEntity.ok(marketCode + " added to favorites.");
//    } else {
//      return ResponseEntity.badRequest().body(marketCode + " already in favorites or invalid.");
//    }
//  }
//
//  @DeleteMapping("/favorites/remove")
//  public ResponseEntity<String> removeFavorite(@RequestParam String marketCode) {
//    boolean removed = marketDataService.removeFavoriteMarket(marketCode);
//    if (removed) {
//      return ResponseEntity.ok(marketCode + " removed from favorites.");
//    } else {
//      return ResponseEntity.badRequest().body(marketCode + " not found in favorites.");
//    }
//  }
//
//  @GetMapping("/favorites")
//  public ResponseEntity<Set<String>> getFavorites() {
//    return ResponseEntity.ok(marketDataService.getFavoriteMarkets());
//  }
//
//  // =========================================================
//  // 실시간 시장 데이터 WebSocket 발행 로직 (!!! 중요 !!!)
//  // =========================================================
////  @Scheduled(fixedRate = 3000) // 3초마다 실행 (Upbit API Rate Limit 고려)
////  public void sendMarketData() { // throws JsonProcessingException 제거
////    try {
////      // 시장 데이터 가져오기 (MarketDataService.getMarketData가 Upbit API 호출)
////      // 이 메소드가 Map<String, Object> 또는 List<CoinResponseDto> 등을 반환하도록 수정되어야 함
////      // 현재 Gist의 MarketDataService.java에는 이 메소드가 없음.
////      // MarketDataService에 getLiveMarketData 또는 getMarketData와 같은 메소드 추가 필요.
////      Map<String, Object> marketData = marketDataService.getMarketData(); // 이 메소드 필요!
////
////      // WebSocket으로 데이터 전송
////      simpMessagingTemplate.convertAndSend("/topic/marketData", marketData); // STOMP Topic 발행
////      System.out.println("✅ WebSocket 데이터 발행 성공: " + marketData.size() + "개 코인 데이터"); // 콘솔 로그 추가
////    } catch (JsonProcessingException e) {
////      System.err.println("❌ MarketDataController - JSON 처리 오류: " + e.getMessage());
////    } catch (Exception e) {
////      System.err.println("❌ MarketDataController - 데이터 발행 중 알 수 없는 오류: " + e.getMessage());
////      e.printStackTrace();
////    }
////  }
//}