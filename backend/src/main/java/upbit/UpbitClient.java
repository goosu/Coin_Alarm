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