// backend/src/main/java/coinalarm/Coin_Alarm/upbit/UpbitClient.java
package coinalarm.Coin_Alarm.upbit; // <-- 정확한 패키지 경로

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
import java.util.Map; // Map을 사용하기 위해 필요
import java.util.stream.Collectors;

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
              .collect(Collectors.toList());
    } else {
      return Collections.emptyList();
    }
  }

  public List<coinalarm.Coin_Alarm.upbit.UpbitTickerResponse> getTicker(List<String> marketCodes) {
    String marketsParam = marketCodes.stream()
            .collect(Collectors.joining(","));

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


  /**
   * 특정 마켓 코드의 캔들 데이터를 가져옵니다. (분봉/시간봉 통합)
   * @param unit 캔들 단위 문자열 (예: "minutes/1", "minutes/15", "minutes/60")
   * @param marketCode 조회할 마켓 코드 (예: "KRW-BTC")
   * @param count 가져올 캔들 개수 (기본값 1개, 가장 최신 캔들)
   * @return UpbitCandleResponse 리스트 (보통 1개만 반환됨)
   */
  public List<UpbitCandleResponse> getCandles(String unit, String marketCode, int count) {
    URI uri = UriComponentsBuilder.fromUriString(UPBIT_API_BASE_URL)
            .path("/candles/" + unit) // 예: /candles/minutes/1
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

  /**
   * 특정 분봉 캔들 가져오기 편의 메서드
   * @param minute 분 단위 (예: 1, 15, 60)
   * @param marketCode 마켓 코드
   * @param count 가져올 개수
   * @return UpbitCandleResponse 리스트
   */
  public List<UpbitCandleResponse> getMinuteCandles(int minute, String marketCode, int count) {
    return getCandles("minutes/" + minute, marketCode, count);
  }

  /**
   * 1시간봉 캔들 가져오기 편의 메서드 (내부적으로 60분봉 호출)
   * @param marketCode 마켓 코드
   * @param count 가져올 개수
   * @return UpbitCandleResponse 리스트
   */
  public List<UpbitCandleResponse> getHourCandles(String marketCode, int count) {
    return getCandles("minutes/60", marketCode, count); // 60분봉 = 1시간봉
  }
}