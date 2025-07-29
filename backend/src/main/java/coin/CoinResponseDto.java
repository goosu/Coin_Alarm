// backend/src/main/java/coinalarm/Coin_Alarm/coin/CoinResponseDto.java
//package coin; // <-- 코인 관련 클래스들의 패키지
package coinalarm.Coin_Alarm.coin;

// Lombok 어노테이션
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter; // Setter 어노테이션 임포트 추가 (필요한 경우)

import java.util.List; // List 타입을 사용하기 위한 임포트

// Coin 엔티티의 데이터를 프론트엔드로 전송하기 위한 DTO (Data Transfer Object) 클래스입니다.
// 엔티티를 직접 노출하지 않고 DTO를 사용함으로써, 데이터 계약을 명확히 하고 보안 및 유연성을 높입니다.
@Getter // 모든 필드의 Getter 메서드를 자동으로 생성합니다.
@NoArgsConstructor // 인자 없는 기본 생성자를 자동으로 생성합니다.
@AllArgsConstructor // 모든 필드를 인자로 받는 생성자를 자동으로 생성합니다.
@Setter
public class CoinResponseDto {
  //현재가 뿐만 아니라 임의로 바꿀값도 필요함
  private Long id;        // 고유 ID (기존 Coin 엔티티의 ID)
  private String name;    // 전체 마켓명 (예: KRW-BTC)
  private String symbol;  // <--- 새로 추가: 코인 심볼 (예: BTC)
  private Double currentPrice; // <--- 새로 추가: 현재가
  // private Long marketCap; // <-- 삭제: 시가총액 필드는 직접 제공하지 않아 제거
  private String priceChange; // 전일대비 변동률 (예: +2.33%)
  private Double volume;    // 24H 거래대금
  private List<String> alarm; // 알람 목록

  //임시방편 나중에 변수로 처리해서 값을 받아 처리해야겠음
  private Double volume1m;  // 1분봉 거래대금
  private Double volume15m; // 15분봉 거래대금
  private Double volume1h;  // 1시간봉 거래대금
//  private Long id; // 코인 고유 식별자
//  private String name; // 코인 이름
//  private Long marketCap; // 시가총액
//  private String priceChange; // 가격 변동률
//  private Long volume; // 거래대금
//  private List<String> alarm; // 알람 목록

  // 엔티티(Coin) 객체를 DTO(CoinResponseDto) 객체로 변환하는 정적 팩토리 메서드입니다.
  // 이렇게 하면 변환 로직이 DTO 클래스 내부에 캡슐화되어 관리하기 용이합니다.
  public static CoinResponseDto fromEntity(Coin coin) {
    return new CoinResponseDto(
            coin.getId(),
            coin.getName(),
            // fromEntity에서는 symbol, currentPrice를 직접 설정하기 어려우므로 임의값 할당
            coin.getName().split("-").length > 1 ? coin.getName().split("-")[1] : coin.getName(), // 임시 심볼 추출
            0.0, // DB 엔티티에는 현재가 필드가 없음
            // coin.getMarketCap(), // <-- 기존 marketCap 필드 사용
            coin.getPriceChange(),
//            coin.getVolume(),
            0.0, // <-- 24H 거래대금은 DB 엔티티에 없으므로 0.0으로 설정
            //추가되는 1,15,1h
            0.0, 0.0, 0.0,
            coin.getAlarm()
//            coin.getId(),
//            coin.getName(),
//            coin.getMarketCap(),
//            coin.getPriceChange(),
//            coin.getVolume(),
//            coin.getAlarm()
    );
  }
}
