//
//package coinalarm.Coin_Alarm.upbit;
//
//import com.fasterxml.jackson.core.type.TypeReference;
//import com.fasterxml.jackson.databind.ObjectMapper;
//import okhttp3.OkHttpClient;
//import okhttp3.Request;
//import okhttp3.Response;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//import org.springframework.stereotype.Component;
//
//import java.util.ArrayList;
//import java.util.List;
//import java.util.stream.Collectors;
//
//@Component
//public class UpbitClient {
//
//  private static final Logger log = LoggerFactory.getLogger(UpbitClient.class);
//
//  private final OkHttpClient httpClient;
//  private final ObjectMapper objectMapper;
//
//  public UpbitClient() {
//    this.httpClient = new OkHttpClient();
//    this.objectMapper = new ObjectMapper();
//  }
//
//  // 모든 KRW 마켓 코드 조회
//  public List<String> getAllKrwMarketCodes() {
//    try {
//      Request request = new Request.Builder()
//              .url("https://api.upbit.com/v1/market/all")
//              .build();
//
//      try (Response response = httpClient.newCall(request).execute()) {
//        if (response.body() != null) {
//          String json = response.body().string();
//          List<UpbitMarketInfo> markets = objectMapper.readValue(json, new TypeReference<List<UpbitMarketInfo>>() {});
//
//          return markets.stream()
//                  .filter(m -> m.getMarket() != null && m.getMarket().startsWith("KRW-"))
//                  .map(UpbitMarketInfo::getMarket)
//                  .collect(Collectors.toList());
//        }
//      }
//    } catch (Exception e) {
//      log.error("Failed to fetch market codes from Upbit: {}", e.getMessage());
//    }
//
//    // 실패시 기본 마켓 코드들 반환
//    return List.of(
//            "KRW-BTC", "KRW-ETH", "KRW-XRP", "KRW-ADA", "KRW-SOL",
//            "KRW-DOGE", "KRW-DOT", "KRW-AVAX", "KRW-MATIC", "KRW-LTC"
//    );
//  }
//
//  // 티커 정보 조회
//  public List<UpbitTickerResponse> getTicker(List<String> marketCodes) {
//    try {
//      String markets = String.join(",", marketCodes);
//      Request request = new Request.Builder()
//              .url("https://api.upbit.com/v1/ticker?markets=" + markets)
//              .build();
//
//      try (Response response = httpClient.newCall(request).execute()) {
//        if (response.body() != null) {
//          String json = response.body().string();
//          return objectMapper.readValue(json, new TypeReference<List<UpbitTickerResponse>>() {});
//        }
//      }
//    } catch (Exception e) {
//      log.error("Failed to fetch ticker from Upbit: {}", e.getMessage());
//    }
//
//    return new ArrayList<>();
//  }
//
//  // 분봉 캔들 조회
//  public List<UpbitCandleResponse> getMinuteCandles(int unit, String market, int count) {
//    try {
//      String url = String.format("https://api.upbit.com/v1/candles/minutes/%d?market=%s&count=%d",
//              unit, market, count);
//
//      Request request = new Request.Builder()
//              .url(url)
//              .build();
//
//      try (Response response = httpClient.newCall(request).execute()) {
//        //20250822 소켓 json type에러 디버깅코드 str
//        if (!response.isSuccessful()) { // 응답 코드가 2xx가 아닐 경우
//          log.error("Upbit candles API call failed with code: {}, message: {}, body: {}",
//                  response.code(), response.message(), response.body() != null ? response.body().string() : "No body");
//          // 에러 응답인 경우 body를 한 번만 읽을 수 있으므로 주의. 여기서는 에러 로깅용
//          return new ArrayList<>(); // 빈 리스트 반환
//        }
//        //20250822 end
//        if (response.body() != null) {
//          String json = response.body().string();
//          log.info("Received candles JSON: {}", json); // 20250822 <--- 응답 JSON 로깅
//          return objectMapper.readValue(json, new TypeReference<List<UpbitCandleResponse>>() {});
//        }
//      }
//    } catch (Exception e) {
//      log.error("Failed to fetch candles from Upbit: {}", e.getMessage());
//    }
//
//    return new ArrayList<>();
//  }
//
//  // 마켓 정보용 내부 클래스
//  public static class UpbitMarketInfo {
//    private String market;
//    private String koreanName;
//    private String englishName;
//
//    // getters and setters
//    public String getMarket() { return market; }
//    public void setMarket(String market) { this.market = market; }
//
//    public String getKoreanName() { return koreanName; }
//    public void setKoreanName(String koreanName) { this.koreanName = koreanName; }
//
//    public String getEnglishName() { return englishName; }
//    public void setEnglishName(String englishName) { this.englishName = englishName; }
//  }
//}

package coinalarm.Coin_Alarm.upbit;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class UpbitClient {

  private final RestTemplate restTemplate;
  private final String UPBIT_API_BASE_URL = "https://api.upbit.com/v1";

  public UpbitClient(RestTemplateBuilder restTemplateBuilder) {
    this.restTemplate = restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(5))
            .setReadTimeout(Duration.ofSeconds(5))
            .build();
  }

  public List<String> getAllKrwMarketCodes() {
    URI uri = UriComponentsBuilder.fromUriString(UPBIT_API_BASE_URL)
            .path("/market/all")
            .queryParam("isDetails", "false")
            .build()
            .toUri();

    List<Map<String, Object>> responseMaps = restTemplate.exchange(
            uri,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<Map<String, Object>>>() {}
    ).getBody();

    if (responseMaps != null) {
      return responseMaps.stream()
              .filter(m -> m.get("market") instanceof String && m.get("market").toString().startsWith("KRW-"))
              .map(m -> m.get("market").toString())
              .toList();
    } else {
      return Collections.emptyList();
    }
  }

  public List<UpbitTickerResponse> getTicker(List<String> marketCodes) {
    if (marketCodes == null || marketCodes.isEmpty()) return Collections.emptyList();
    String marketsParam = String.join(",", marketCodes);
    URI uri = UriComponentsBuilder.fromUriString(UPBIT_API_BASE_URL)
            .path("/ticker")
            .queryParam("markets", marketsParam)
            .build()
            .toUri();

    List<UpbitTickerResponse> response = restTemplate.exchange(
            uri,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<UpbitTickerResponse>>() {}
    ).getBody();

    return response != null ? response : Collections.emptyList();
  }

  public List<UpbitCandleResponse> getMinuteCandles(int minute, String marketCode, int count) {
    URI uri = UriComponentsBuilder.fromUriString(UPBIT_API_BASE_URL)
            .path("/candles/minutes/" + minute)
            .queryParam("market", marketCode)
            .queryParam("count", count)
            .build()
            .toUri();

    List<UpbitCandleResponse> response = restTemplate.exchange(
            uri,
            HttpMethod.GET,
            null,
            new ParameterizedTypeReference<List<UpbitCandleResponse>>() {}
    ).getBody();

    return response != null ? response : Collections.emptyList();
  }
}