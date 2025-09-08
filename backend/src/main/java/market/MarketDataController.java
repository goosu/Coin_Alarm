package coinalarm.Coin_Alarm.market;

import coinalarm.Coin_Alarm.coin.CoinResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map; // List가 아닌 Map으로 반환
import java.util.Set; // Set 임포트

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class MarketDataController {

  private final MarketDataService marketDataService;

  @Autowired
  public MarketDataController(MarketDataService marketDataService) {
    this.marketDataService = marketDataService;
  }

  /**
   * 필터링된 라이브 시장 데이터를 가져오는 엔드포인트
   * MarketDataService.getFilteredLiveMarketData는 boolean 인자 3개(large, mid, small)만 받음.
   * 반환 타입은 Map<String, CoinResponseDto>.
   */
  @GetMapping("/market-data")
  public Map<String, CoinResponseDto> getFilteredLiveMarketData( // [변경] Map 반환
                                                                 @RequestParam(defaultValue = "false") boolean large, // 디폴트 값을 false로 변경 (필터링되지 않게)
                                                                 @RequestParam(defaultValue = "false") boolean mid,
                                                                 @RequestParam(defaultValue = "false") boolean small
                                                                 // [삭제] all 파라미터는 MarketDataService에서 처리하지 않음
  ) {
    // MarketDataService의 getFilteredLiveMarketData는 large, mid, small만 인자로 받음
    return marketDataService.getFilteredLiveMarketData(large, mid, small);
  }


  /**
   * 즐겨찾기 마켓 추가 (void 반환이므로, 변수에 할당하지 않음)
   */
  @PostMapping("/favorites/add")
  public ResponseEntity<String> addFavoriteMarket(@RequestParam String marketCode) {
    marketDataService.addFavoriteMarket(marketCode); // [수정] void 메소드이므로 반환값을 받지 않음
    return new ResponseEntity<>("Favorite market added: " + marketCode, HttpStatus.OK);
  }

  /**
   * 즐겨찾기 마켓 제거 (void 반환이므로, 변수에 할당하지 않음)
   */
  @DeleteMapping("/favorites/remove")
  public ResponseEntity<String> removeFavoriteMarket(@RequestParam String marketCode) {
    marketDataService.removeFavoriteMarket(marketCode); // [수정] void 메소드이므로 반환값을 받지 않음
    return new ResponseEntity<>("Favorite market removed: " + marketCode, HttpStatus.OK);
  }

  /**
   * 즐겨찾기 마켓 목록 조회
   */
  @GetMapping("/favorites")
  public Set<String> getFavoriteMarkets() {
    return marketDataService.getFavoriteMarkets();
  }
}