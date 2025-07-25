// backend/src/main/java/coinalarm/Coin_Alarm/upbit/UpbitClient.java
package coinalarm.Coin_Alarm.upbit; // <-- 정확한 패키지 경로

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.ParameterizedTypeReference; // List<DTO> 타입을 처리하기 위해 필요
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration; // 시간 단위를 사용하기 위해 필요 (JDK 8 이상)
import java.util.Arrays; // 배열을 리스트로 변환하기 위해 필요
import java.util.Collections; // 빈 리스트를 반환하기 위해 필요
import java.util.List;
import java.util.stream.Collectors; // 스트림 API 사용을 위해 필요

@Component // Spring Bean으로 등록됩니다.
public class UpbitClient {

  private final RestTemplate restTemplate; // HTTP 요청을 보낼 RestTemplate
  private final String UPBIT_API_BASE_URL = "https://api.upbit.com/v1"; // 업비트 API 기본 URL

  // 생성자 주입: RestTemplateBuilder를 통해 RestTemplate을 생성하고 타임아웃을 설정합니다.
  public UpbitClient(RestTemplateBuilder restTemplateBuilder) {
    this.restTemplate = restTemplateBuilder
            .setConnectTimeout(Duration.ofSeconds(5)) // 연결 타임아웃 5초
            .setReadTimeout(Duration.ofSeconds(5))    // 읽기 타임아웃 5초
            .build();
  }

  /**
   * 모든 KRW 마켓 코드(예: KRW-BTC, KRW-ETH)를 가져옵니다.
   * 주의: 실제 Upbit API는 List<Map<String, Object>>를 반환하며, 거기서 "market" 필드를 추출해야 합니다.
   * 현재 코드는 예시 및 편의를 위해 주요 KRW 마켓 코드를 하드코딩하여 반환합니다.
   * 실제 서비스에서는 아래 주석 처리된 API 호출 로직을 사용해야 합니다.
   * @return KRW 마켓 코드 리스트
   */
  public List<String> getAllKrwMarketCodes() {
    // 실제 API 호출 로직 (주석 처리됨)
        /*
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

        return responseMaps != null ? responseMaps.stream()
                .filter(m -> m.get("market").toString().startsWith("KRW-"))
                .map(m -> m.get("market").toString())
                .collect(Collectors.toList()) : Collections.emptyList();
        */

    // 임시 방편으로 주요 KRW 마켓만 하드코딩 (실제 서비스에서는 위 주석 처리된 코드로 API 호출)
    return Arrays.asList("KRW-BTC", "KRW-ETH", "KRW-XRP", "KRW-ADA", "KRW-DOGE", "KRW-SOL", "KRW-DOT", "KRW-AVAX");
  }

  /**
   * 특정 마켓 코드를 가진 코인들의 실시간 시세 정보를 가져옵니다.
   * @param marketCodes 조회할 마켓 코드 리스트 (예: ["KRW-BTC", "KRW-ETH"])
   * @return UpbitTickerResponse 리스트
   */
  public List<coinalarm.Coin_Alarm.upbit.UpbitTickerResponse> getTicker(List<String> marketCodes) {
    // marketCodes 리스트를 콤마로 구분된 문자열로 변환합니다.
    // (예: "KRW-BTC,KRW-ETH")
    String marketsParam = marketCodes.stream()
            .collect(Collectors.joining(","));

    // URI 빌더를 사용하여 URL에 쿼리 파라미터를 추가합니다.
    URI uri = UriComponentsBuilder.fromUriString(UPBIT_API_BASE_URL)
            .path("/ticker")
            .queryParam("markets", marketsParam)
            .build()
            .toUri();

    // API 호출 및 응답을 List<UpbitTickerResponse>로 매핑
    // ParameterizedTypeReference를 사용하여 제네릭 타입을 올바르게 처리합니다.
    List<coinalarm.Coin_Alarm.upbit.UpbitTickerResponse> response = restTemplate.exchange(
            uri,
            HttpMethod.GET,
            null, // 요청 바디 없음
            new ParameterizedTypeReference<List<coinalarm.Coin_Alarm.upbit.UpbitTickerResponse>>() {} // 응답 타입을 List<UpbitTickerResponse>로 지정
    ).getBody();

    return response != null ? response : Collections.emptyList();
  }
}