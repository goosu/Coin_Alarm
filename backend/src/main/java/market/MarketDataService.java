// backend/src/main/java/coinalarm/Coin_Alarm/market/MarketDataService.java
package coinalarm.Coin_Alarm.market;

import coinalarm.Coin_Alarm.coin.CoinResponseDto; // CoinResponseDto 재사용
import coinalarm.Coin_Alarm.upbit.UpbitClient; // UpbitClient 임포트
import coinalarm.Coin_Alarm.upbit.UpbitTickerResponse; // UpbitTickerResponse 임포트
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import coinalarm.Coin_Alarm.upbit.UpbitCandleResponse;


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
      //왜 없앤지 나중에 확인해보기
//      String symbol = marketParts.length > 1 ? marketParts[1] : ticker.getMarket(); // KRW-BTC에서 BTC 추출
      String symbol = marketParts.length > 1 ? marketParts[1] : marketParts[0];

      // --- 캔들 데이터 가져오기 ---
      List<UpbitCandleResponse> candles1m = upbitClient.getMinuteCandles(1, ticker.getMarket(), 1);
      List<UpbitCandleResponse> candles15m = upbitClient.getMinuteCandles(15, ticker.getMarket(), 1);
      List<UpbitCandleResponse> candles1h = upbitClient.getHourCandles(ticker.getMarket(), 1);

//      return new CoinResponseDto(
//              null, // id (실시간 데이터에서는 null)
//              ticker.getMarket(), // name (KRW-BTC)
//              symbol, // <--- 수정: 심볼 추가
//              ticker.getTradePrice(), // <--- 수정: 현재가 추가
//              String.format("%.2f%%", ticker.getChangeRate() * 100), // priceChange (전일대비)
//              ticker.getAccTradePrice24h(), // volume (24H 거래대금)
//              List.of() // alarm (없으므로 빈 리스트)
//      );
      CoinResponseDto dto = new CoinResponseDto(
              null,
              ticker.getMarket(),
              symbol,
              ticker.getTradePrice(),
              String.format("%.2f%%", ticker.getChangeRate() * 100),
              ticker.getAccTradePrice24h(), // 24H 거래대금
              // 캔들 거래대금 할당 (시고저종 필드는 여기에 포함시키지 않음)
              candles1m.isEmpty() ? 0.0 : candles1m.get(0).getCandleAccTradePrice(),
              candles15m.isEmpty() ? 0.0 : candles15m.get(0).getCandleAccTradePrice(),
              candles1h.isEmpty() ? 0.0 : candles1h.get(0).getCandleAccTradePrice(),
              List.of() // alarm
      );
      return dto;
    }).collect(Collectors.toList());
  }
}