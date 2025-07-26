// backend/src/main/java/coinalarm/Coin_Alarm/market/MarketDataController.java
package coinalarm.Coin_Alarm.market; // <-- 정확한 패키지 경로

import coinalarm.Coin_Alarm.coin.CoinResponseDto; // CoinResponseDto 클래스 임포트
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입을 위해 필요
import org.springframework.web.bind.annotation.CrossOrigin; // CORS 설정을 위해 필요
import org.springframework.web.bind.annotation.GetMapping; // HTTP GET 요청 처리
import org.springframework.web.bind.annotation.RequestMapping; // 기본 URL 경로 설정
import org.springframework.web.bind.annotation.RequestParam; // 쿼리 파라미터 받기
import org.springframework.web.bind.annotation.RestController; // RESTful 웹 서비스 컨트롤러임을 명시

import java.util.List; // List 타입을 사용하기 위해 필요

@RestController // Spring Bean으로 등록되며, RESTful API 요청을 처리합니다.
@RequestMapping("/api") // 이 컨트롤러의 모든 메서드는 "/api" 경로 아래에서 시작합니다.
// @CrossOrigin 어노테이션은 이 컨트롤러에 대한 CORS(Cross-Origin Resource Sharing)를 설정합니다.
// "http://localhost:5173"에서 오는 요청을 허용하여, 프론트엔드 개발 서버와의 통신을 가능하게 합니다.
// 실제 배포 시에는 프론트엔드의 도메인으로 변경해야 합니다. (예: origins = "https://yourfrontend.com")
@CrossOrigin(origins = "http://localhost:5173") // <--- 이 부분이 중요합니다!
public class MarketDataController {

  private final coinalarm.Coin_Alarm.market.MarketDataService marketDataService; // MarketDataService를 주입받습니다.

  @Autowired // Spring이 MarketDataService 타입의 Bean을 찾아 이 생성자의 인자로 주입합니다.
  public MarketDataController(coinalarm.Coin_Alarm.market.MarketDataService marketDataService) {
    this.marketDataService = marketDataService;
  }

  /**
   * 실시간 코인 시장 데이터를 프론트엔드에 제공하는 엔드포인트입니다.
   * 이 엔드포인트는 업비트 API에서 데이터를 가져와 필터링 후 CoinResponseDto 리스트 형태로 반환합니다.
   * 기존의 /api/coins 엔드포인트를 대체하여 실시간 데이터를 제공합니다.
   *
   * 요청 예시: GET http://localhost:8080/api/market-data?all=true&large=false&mid=false&small=false
   *
   * @param all 전체 코인 조회 여부 (기본값 true)
   * @param large 대형 코인 조회 여부 (기본값 false)
   * @param mid 중형 코인 조회 여부 (기본값 false)
   * @param small 소형 코인 조회 여부 (기본값 false)
   * @return 필터 조건에 맞는 CoinResponseDto 리스트
   */
  @GetMapping("/market-data") // "/api/market-data" 경로로 들어오는 GET 요청을 처리합니다.
  public List<CoinResponseDto> getLiveMarketData(
          @RequestParam(defaultValue = "true") boolean all,    // 쿼리 파라미터 'all', 기본값 true
          @RequestParam(defaultValue = "false") boolean large, // 쿼리 파라미터 'large', 기본값 false
          @RequestParam(defaultValue = "false") boolean mid,   // 쿼리 파라미터 'mid', 기본값 false
          @RequestParam(defaultValue = "false") boolean small  // 쿼리 파라미터 'small', 기본값 false
  ) {
    // MarketDataService의 getFilteredLiveMarketData 메서드를 호출하여 필터링된 실시간 데이터를 가져와 반환합니다.
    return marketDataService.getFilteredLiveMarketData(all, large, mid, small);
  }
}