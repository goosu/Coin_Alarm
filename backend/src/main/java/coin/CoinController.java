package coinalarm.Coin_Alarm.coin; // 정확한 패키지 경로를 사용하세요!

// 필요한 임포트들
import coinalarm.Coin_Alarm.coin.CoinService;
import coinalarm.Coin_Alarm.coin.CoinResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity; // ResponseEntity 사용시 필요 (기존 GetMapping /market-data에 있었음)
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors; // Stream API 사용을 위해 추가


@RestController // Spring Bean으로 등록되며, RESTful API 요청을 처리합니다.
@RequestMapping("/api") // 이 컨트롤러의 모든 메서드는 "/api" 경로 아래에서 시작합니다.
@CrossOrigin(origins = "http://localhost:5173") // 프론트엔드 개발 서버의 요청을 허용합니다.
public class CoinController {

  private final CoinService coinService; // CoinService를 주입받습니다.

  @Autowired // 생성자 주입
  public CoinController(CoinService coinService) {
    this.coinService = coinService;
  }

  /**
   * 시가총액 필터 조건에 맞는 코인 목록을 반환하는 API 엔드포인트입니다.
   * Coin 엔티티 리스트를 CoinResponseDto 리스트로 변환하여 반환합니다.
   * 예: GET http://localhost:8080/api/coins?large=true&mid=false&small=true
   */
  @GetMapping("/coins")
  public List<CoinResponseDto> getFilteredCoins( // 반환 타입을 CoinResponseDto 리스트로 지정
                                                 @RequestParam(defaultValue = "true") boolean large, // 'large' 쿼리 파라미터
                                                 @RequestParam(defaultValue = "true") boolean mid,   // 'mid' 쿼리 파라미터
                                                 @RequestParam(defaultValue = "true") boolean small  // 'small' 쿼리 파라미터
  ) {
    // CoinService로부터 Coin 엔티티 리스트를 받아옵니다.
    // 롱터우님 Gist의 CoinService는 getFilteredCoins가 UpbitClient.getAllKrwMarketCodes를 호출합니다.
    // 따라서 List<String> 형태의 마켓 코드 리스트가 반환될 것으로 보입니다.
    // 만약 CoinService.getFilteredCoins가 List<Coin> 엔티티를 반환한다면 타입 수정 필요.
    List<Coin> coins = coinService.getFilteredCoins(large, mid, small); // 반환 타입은 List<Coin> 이여야 합니다.

    // Stream API를 사용하여 Coin 엔티티 리스트를 CoinResponseDto 리스트로 변환합니다.
    return coins.stream()
            .map(coin -> {
              // *** [수정] Coin 엔티티의 필드를 CoinResponseDto의 빌더에 맞게 매핑합니다. ***
              // Coin 엔티티는 id, symbol, name 필드를 가집니다.
              // CoinResponseDto에는 id, name 필드가 없으므로, 필요 없으면 매핑에서 제외합니다.
              // volume1m, volume5m 등은 Coin 엔티티에 없으므로 기본값(0.0)으로 설정합니다.
              return CoinResponseDto.builder()
                      .symbol(coin.getSymbol()) // Coin 엔티티의 symbol (예: KRW-BTC)
                      // 이 컨트롤러는 코인 '기본 정보'를 제공하므로, 동적인 'price'는 보통 DB에 없음.
                      // UpbitTickerResponse의 price 필드와 혼동하지 않도록 0.0으로 초기화
                      .price(0.0)

                      // DB에 이 정보가 없다면 기본값 0.0으로 설정합니다.
                      .volume1m(0.0)
                      .volume5m(0.0)
                      .volume15m(0.0)
                      .volume1h(0.0)
                      .accTradePrice24h(0.0)
                      .change24h(0.0)
                      .buyVolume(0.0)
                      .sellVolume(0.0)
                      .timestamp(0L) // DB에 타임스탬프 정보가 없다면 0L 또는 null
                      .build();
            })
            .collect(Collectors.toList()); // 변환된 DTO 리스트를 반환
  }
}