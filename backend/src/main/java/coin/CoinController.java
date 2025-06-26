package coin;// src/main/java/coinalarm/Coin_Alarm/coin/CoinController.java


import coin.Coin;
import coin.CoinResponseDto;
import coin.CoinService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.ArrayList; // ArrayList를 사용하기 위해 추가

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:3000")
public class CoinController {

  private final CoinService coinService;

  @Autowired
  public CoinController(CoinService coinService) {
    this.coinService = coinService;
  }

  /**
   * 시가총액 필터 조건에 맞는 코인 목록을 반환하는 API 엔드포인트입니다.
   * 엔티티 대신 CoinResponseDto 리스트를 반환하여 데이터 계약을 명확히 합니다.
   */
  @GetMapping("/coins")
  public List<CoinResponseDto> getFilteredCoins( // 반환 타입을 CoinResponseDto 리스트로 변경
                                                 @RequestParam(defaultValue = "true") boolean large,
                                                 @RequestParam(defaultValue = "true") boolean mid,
                                                 @RequestParam(defaultValue = "true") boolean small
  ) {
    // CoinService로부터 Coin 엔티티 리스트를 받아옵니다.
    List<Coin> coins = coinService.getFilteredCoins(large, mid, small);

    // --- 수정할 부분: Stream API 대신 전통적인 for 루프 사용 ---
    List<CoinResponseDto> responseDtos = new ArrayList<>(); // CoinResponseDto를 담을 새 리스트 생성

    // 받아온 Coin 엔티티 리스트를 순회합니다.
    for (Coin coin : coins) {
      // 각 Coin 엔티티를 CoinResponseDto.fromEntity 메서드를 사용하여 DTO로 변환합니다.
      CoinResponseDto dto = CoinResponseDto.fromEntity(coin);
      // 변환된 DTO를 리스트에 추가합니다.
      responseDtos.add(dto);
    }

    return responseDtos; // 변환된 DTO 리스트를 반환합니다.
    // --- 수정 끝 ---
  }
}
