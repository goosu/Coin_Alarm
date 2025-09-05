package coinalarm.Coin_Alarm.coin; // 정확한 패키지 경로를 사용하세요!

// 필요한 임포트들 (Lombok DTO를 사용하는 이상 더 이상 coinalarm.Coin_Alarm.coin.Coin이 필요 없을 수 있습니다.
// 여기서는 CoinService의 List<Coin>을 받으므로 필요합니다.)
import coinalarm.Coin_Alarm.coin.Coin; // <-- [중요] Coin 엔티티 클래스를 정확히 임포트!
import coinalarm.Coin_Alarm.coin.CoinService;
import coinalarm.Coin_Alarm.coin.CoinResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;


@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class CoinController {

  private final CoinService coinService;

  @Autowired
  public CoinController(CoinService coinService) {
    this.coinService = coinService;
  }

  /**
   * 시가총액 필터 조건에 맞는 코인 목록을 반환하는 API 엔드포인트입니다.
   * CoinService가 DB에서 가져온 Coin 엔티티(List<Coin>)를 CoinResponseDto 리스트로 변환하여 반환합니다.
   * 예: GET http://localhost:8080/api/coins?large=true&mid=false&small=true
   */
  @GetMapping("/coins")
  public List<CoinResponseDto> getFilteredCoins(
          @RequestParam(defaultValue = "true") boolean large,
          @RequestParam(defaultValue = "true") boolean mid,
          @RequestParam(defaultValue = "true") boolean small
  ) {
    // *** [수정] CoinService에서 반환하는 타입이 List<Coin> 이므로, 그대로 List<Coin>으로 받습니다! ***
    //기존에는 String 처리로 인한 에러상태였음
    List<Coin> coins = coinService.getFilteredCoins(large, mid, small); // CoinService는 List<Coin>을 반환함

    // Stream API를 사용하여 Coin 엔티티를 CoinResponseDto로 변환합니다.
    return coins.stream()
            .map(coin -> {
              // Coin 엔티티의 필드를 CoinResponseDto의 빌더에 맞게 매핑합니다.
              // 롱터우님 Gist의 Coin.java를 보면 id, symbol, name 필드가 있습니다.
              // 그 외 volume, price 등 실시간 정보는 Coin 엔티티에 없으므로 0.0으로 초기화합니다.
              return CoinResponseDto.builder()
                      .symbol(coin.getSymbol()) // <-- Coin 엔티티의 getSymbol() 사용
                      // Coin Controller는 DB의 코인 '기본 정보'를 제공하는 역할.
                      // 가격, 거래대금 등은 실시간 데이터가 아니므로 0.0으로 초기화합니다.
                      // 프론트엔드에서는 MarketDataService의 WebSocket으로 이 값을 채울 것입니다.
                      .price(0.0)
                      .volume1m(0.0)
                      .volume5m(0.0)
                      .volume15m(0.0)
                      .volume1h(0.0)
                      .accTradePrice24h(0.0)
                      .change24h(0.0)
                      .buyVolume(0.0)
                      .sellVolume(0.0)
                      .timestamp(0L)
                      .build();
            })
            .collect(Collectors.toList()); // 변환된 DTO 리스트를 반환
  }
}