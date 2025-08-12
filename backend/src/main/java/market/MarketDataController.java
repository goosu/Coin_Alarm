package coinalarm.Coin_Alarm.market;

import coinalarm.Coin_Alarm.coin.CoinResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set; // Set 임포트

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173") // 프론트엔드 URL에 맞게 설정
public class MarketDataController {

  private final MarketDataService marketDataService;

  @Autowired
  public MarketDataController(MarketDataService marketDataService) {
    this.marketDataService = marketDataService;
  }

  @GetMapping("/market-data")
  public ResponseEntity<List<CoinResponseDto>> getLiveMarketData(
          @RequestParam(defaultValue = "true") boolean all,
          @RequestParam(defaultValue = "false") boolean large,
          @RequestParam(defaultValue = "false") boolean mid,
          @RequestParam(defaultValue = "false") boolean small) {
    List<CoinResponseDto> data = marketDataService.getFilteredLiveMarketData(all, large, mid, small);
    return ResponseEntity.ok(data);
  }

  // =========================================================
  // 즐겨찾기 관리 API 추가
  // =========================================================
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
}