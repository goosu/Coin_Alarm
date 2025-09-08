package coinalarm.Coin_Alarm.upbit;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

/**
 * Upbit REST API /v1/market/all 응답 JSON을 Java 객체로 매핑하는 DTO
 * Lombok 어노테이션을 사용하여 Getter, Setter, toString()을 자동 생성합니다.
 */
@Getter // 모든 필드의 Getter 메소드를 자동 생성
@Setter // 모든 필드의 Setter 메소드를 자동 생성
@ToString // toString() 메소드를 자동 생성
public class UpbitMarketResponse {
  @JsonProperty("market") // JSON의 "market" 필드를 Java의 market 필드에 매핑
  private String market; // 마켓 코드 (예: KRW-BTC, BTC-ETH 등)

  @JsonProperty("korean_name") // JSON의 "korean_name" 필드를 Java의 koreanName 필드에 매핑
  private String koreanName; // 한글 이름 (예: 비트코인)

  @JsonProperty("english_name") // JSON의 "english_name" 필드를 Java의 englishName 필드에 매핑
  private String englishName; // 영문 이름 (예: Bitcoin)
}