// backend/src/main/java/coinalarm/Coin_Alarm/coin/CoinController.java
//package coin; // <-- 코인 관련 클래스들의 패키지
package coinalarm.Coin_Alarm.coin;

import coinalarm.Coin_Alarm.coin.CoinService;
import coinalarm.Coin_Alarm.coin.CoinResponseDto;

import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입을 위해 필요
import org.springframework.web.bind.annotation.CrossOrigin; // CORS 설정을 위해 필요
import org.springframework.web.bind.annotation.GetMapping; // HTTP GET 요청 처리
import org.springframework.web.bind.annotation.RequestMapping; // 기본 URL 경로 설정
import org.springframework.web.bind.annotation.RequestParam; // 쿼리 파라미터 받기
import org.springframework.web.bind.annotation.RestController; // RESTful 웹 서비스 컨트롤러임을 명시

import java.util.List; // List 타입을 사용하기 위한 임포트
import java.util.ArrayList; // ArrayList를 사용하기 위한 임포트 (Stream API 대신 for 루프 사용 시)

@RestController // Spring Bean으로 등록되며, RESTful API 요청을 처리합니다.
@RequestMapping("/api") // 이 컨트롤러의 모든 메서드는 "/api" 경로 아래에서 시작합니다.
@CrossOrigin(origins = "http://localhost:5173") // 프론트엔드 개발 서버의 요청을 허용합니다. (실제 배포 시 변경 필요)
public class CoinController {

  private final CoinService coinService; // CoinService를 주입받습니다.

  @Autowired // 생성자 주입
  public CoinController(CoinService coinService) {
    this.coinService = coinService;
  }

  /**
   * 시가총액 필터 조건에 맞는 코인 목록을 반환하는 API 엔드포인트입니다.
   * 엔티티 대신 CoinResponseDto 리스트를 반환하여 데이터 계약을 명확히 합니다.
   * 예: GET http://localhost:8080/api/coins?large=true&mid=false&small=true
   */
  @GetMapping("/coins") // "/api/coins" 경로로 들어오는 GET 요청을 처리합니다.
  public List<CoinResponseDto> getFilteredCoins( // 반환 타입을 CoinResponseDto 리스트로 지정
                                                 @RequestParam(defaultValue = "true") boolean large, // 'large' 쿼리 파라미터 (기본값 true)
                                                 @RequestParam(defaultValue = "true") boolean mid,   // 'mid' 쿼리 파라미터 (기본값 true)
                                                 @RequestParam(defaultValue = "true") boolean small  // 'small' 쿼리 파라미터 (기본값 true)
  ) {
    // CoinService로부터 Coin 엔티티 리스트를 받아옵니다.
    List<Coin> coins = coinService.getFilteredCoins(large, mid, small);

    // 전통적인 for 루프를 사용하여 Coin 엔티티 리스트를 CoinResponseDto 리스트로 변환합니다.
    // Stream API의 타입 추론 문제 회피를 위해 사용합니다.
    List<CoinResponseDto> responseDtos = new ArrayList<>();
    for (Coin coin : coins) {
      CoinResponseDto dto = CoinResponseDto.fromEntity(coin);
      responseDtos.add(dto);
    }

    return responseDtos; // 변환된 DTO 리스트를 반환합니다.
  }
}
