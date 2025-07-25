// backend/src/main/java/coinalarm/Coin_Alarm/market/MarketDataService.java
package coinalarm.Coin_Alarm.market;

import coinalarm.Coin_Alarm.coin.CoinResponseDto; // CoinResponseDto 재사용
import coinalarm.Coin_Alarm.upbit.UpbitClient; // UpbitClient 임포트
import coinalarm.Coin_Alarm.upbit.UpbitTickerResponse; // UpbitTickerResponse 임포트
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class MarketDataService {

  private final UpbitClient upbitClient;

  @Autowired
  public MarketDataService(UpbitClient upbitClient) {
    this.upbitClient = upbitClient;
  }

  public List<CoinResponseDto> getFilteredLiveMarketData(boolean all, boolean large, boolean mid, boolean small) {
    List<String> marketCodes;
    marketCodes = upbitClient.getAllKrwMarketCodes();
    List<UpbitTickerResponse> tickers = upbitClient.getTicker(marketCodes);

    List<UpbitTickerResponse> filteredTickers = new ArrayList<>();

    if (all) {
      filteredTickers.addAll(tickers);
    } else {
      if (large) {
        tickers.stream()
                .filter(t -> t.getMarket().equals("KRW-BTC") || t.getMarket().equals("KRW-ETH"))
                .forEach(filteredTickers::add);
      }
      if (mid) {
        tickers.stream()
                .filter(t -> t.getMarket().equals("KRW-XRP") || t.getMarket().equals("KRW-ADA") || t.getMarket().equals("KRW-SOL"))
                .forEach(filteredTickers::add);
      }
      if (small) {
        tickers.stream()
                .filter(t -> t.getMarket().equals("KRW-DOGE") || t.getMarket().equals("KRW-DOT") || t.getMarket().equals("KRW-AVAX"))
                .forEach(filteredTickers::add);
      }
    }

    List<UpbitTickerResponse> distinctFilteredTickers = filteredTickers.stream()
            .distinct()
            .collect(Collectors.toList());

    // UpbitTickerResponse를 새로운 CoinResponseDto로 변환합니다.
    return distinctFilteredTickers.stream().map(ticker -> {
      String[] marketParts = ticker.getMarket().split("-");
      String symbol = marketParts.length > 1 ? marketParts[1] : ticker.getMarket(); // KRW-BTC에서 BTC 추출

      return new CoinResponseDto(
              null, // id (실시간 데이터에서는 null)
              ticker.getMarket(), // name (KRW-BTC)
              symbol, // <--- 수정: 심볼 추가
              ticker.getTradePrice(), // <--- 수정: 현재가 추가
              String.format("%.2f%%", ticker.getChangeRate() * 100), // priceChange (전일대비)
              ticker.getTradeVolume().longValue(), // volume (24H 거래대금)
              List.of() // alarm (없으므로 빈 리스트)
      );
    }).collect(Collectors.toList());
  }
}