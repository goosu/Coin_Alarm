// src/main/java/coinalarm/Coin_Alarm/coin/CoinController.java
//package coin; // <-- 실제 프로젝트 패키지 이름 + 하위 패키지
//package coinalarm.Coin_Alarm;
package coin;

import coin.Coin;
import coin.CoinService;
import org.springframework.beans.factory.annotation.Autowired; // 의존성 주입을 위해 필요합니다.
import org.springframework.web.bind.annotation.CrossOrigin; // CORS(Cross-Origin Resource Sharing) 설정을 위해 필요합니다.
import org.springframework.web.bind.annotation.GetMapping; // HTTP GET 요청을 처리하는 메서드임을 나타냅니다.
import org.springframework.web.bind.annotation.RequestMapping; // 컨트롤러 또는 메서드의 기본 URL 경로를 설정합니다.
import org.springframework.web.bind.annotation.RequestParam; // HTTP 요청의 쿼리 파라미터 값을 메서드 인자로 받을 때 사용합니다.
import org.springframework.web.bind.annotation.RestController; // 이 클래스가 RESTful 웹 서비스 컨트롤러임을 나타냅니다.

import java.util.List; // 반환 타입으로 사용할 List
import java.util.stream.Collectors;

@RestController // @RestController 어노테이션을 붙여서 Spring이 이 클래스를 RESTful API 요청을 처리하는 컨트롤러로 인식하게 합니다.
// 이 어노테이션이 붙으면 메서드의 반환 값이 자동으로 JSON 또는 XML 형태로 변환되어 HTTP 응답 본문에 담깁니다. (@Controller + @ResponseBody 역할)
@RequestMapping("/api") // @RequestMapping("/api") 어노테이션을 붙여서 이 컨트롤러의 모든 메서드는 "/api" 경로 아래에서 시작하도록 설정합니다.
@CrossOrigin(origins = "http://localhost:3000") // @CrossOrigin: 다른 출처(Origin)에서의 요청을 허용합니다.
// 여기서는 프론트엔드 개발 서버가 실행되는 "http://localhost:3000"에서의 요청을 허용하도록 설정했습니다.
// 실제 배포 시에는 프론트엔드의 실제 도메인으로 변경해야 합니다. 보안상 * (모든 출처 허용)은 지양하는 것이 좋습니다.
public class CoinController {

  // CoinService 타입의 필드를 선언합니다.
  // Spring이 @Autowired를 통해 CoinService 객체를 자동으로 주입해 줄 것입니다.
  private final CoinService coinService;

  // 생성자 주입: Spring이 CoinService 객체를 생성자 인자로 넘겨주면서 이 Controller 객체를 생성합니다.
  @Autowired
  public CoinController(coin.CoinService coinService) {
    this.coinService = coinService; // 주입받은 CoinService 객체를 필드에 할당합니다.
  }

  /**
   * 시가총액 필터 조건에 맞는 코인 목록을 반환하는 API 엔드포인트입니다.
   * 클라이언트는 이 엔드포인트로 HTTP GET 요청을 보냅니다.
   * 예: GET http://localhost:8080/api/coins?large=true&mid=false&small=true
   *
   * @param large 대형 코인 포함 여부 (쿼리 파라미터 'large' 값, 기본값 true)
   * @param mid 중형 코인 포함 여부 (쿼리 파라미터 'mid' 값, 기본값 true)
   * @param small 소형 코인 포함 여부 (쿼리 파라미터 'small' 값, 기본값 true)
   * @return 필터링된 코인 목록 (List<Coin> 객체 리스트, Spring이 자동으로 JSON으로 변환하여 응답)
   */
  @GetMapping("/coins") // @GetMapping("/coins"): "/api" 경로에 "/coins"를 더한 "/api/coins" 경로로 들어오는 HTTP GET 요청을 이 메서드가 처리하도록 매핑합니다.
  public List<coin.Coin> getFilteredCoins(
          @RequestParam(defaultValue = "true") boolean large, // @RequestParam: HTTP 요청의 쿼리 파라미터 'large' 값을 받아서 boolean 타입의 large 변수에 할당합니다. defaultValue = "true"는 파라미터가 없을 경우 기본값을 true로 설정합니다.
          @RequestParam(defaultValue = "true") boolean mid,   // 쿼리 파라미터 'mid' 값을 받습니다.
          @RequestParam(defaultValue = "true") boolean small  // 쿼리 파라미터 'small' 값을 받습니다.
  ) {
    // 주입받은 coinService 객체의 getFilteredCoins 메서드를 호출하여 비즈니스 로직을 수행하고 결과를 받아옵니다.
//    return coinService.getFilteredCoins(large, mid, small); //2025.6.24 del
    List<coin.Coin> coins = coinService.getFilteredCoins(large, mid, small);

    // 메서드가 List<Coin> 객체를 반환하면, @RestController에 의해 Spring이 이 객체 리스트를 자동으로 JSON 형태로 변환하여 HTTP 응답 본문에 담아 클라이언트에게 보냅니다.

    // Stream API를 사용하여 각 Coin 엔티티를 CoinResponseDto로 변환합니다.
    // .stream(): 리스트를 스트림으로 변환합니다.
    // .map(CoinResponseDto::fromEntity): 각 Coin 객체에 대해 CoinResponseDto.fromEntity 메서드를 적용하여 변환합니다.
    // .collect(Collectors.toList()): 변환된 DTO들을 다시 리스트로 수집합니다.
    return coins.stream()
            .map(coinalarm.Coin_Alarm.coin.CoinResponseDto::fromEntity)
            .collect(Collectors.toList());

  }

  // TODO: 나중에 필요하다면 코인 상세 정보 조회, 알람 설정/해제 등의 API 엔드포인트를 추가할 수 있습니다.
  // 예: @GetMapping("/coins/{id}") public Coin getCoinDetails(@PathVariable Long id) { ... }
}
