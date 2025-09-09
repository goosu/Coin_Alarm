// backend/src/main/java/market/MarketDataWSC.java
package coinalarm.Coin_Alarm.market; // 패키지 경로가 market 아래인지 확인

import coinalarm.Coin_Alarm.coin.CoinResponseDto;
import org.springframework.stereotype.Service; // 또는 @Component (해당 파일의 역할에 따라 선택)

import java.util.Map; // Map 타입을 사용하기 위해 임포트

/**
 * MarketDataWSC 클래스는 MarketDataService의 메소드를 호출하여 데이터를 처리하는 역할을 수행합니다.
 * 에러 로그에서 확인된 32번째 줄의 호출 문제가 해결된 버전입니다.
 */
@Service // 이 클래스가 Spring의 서비스 레이어 컴포넌트임을 명시
public class MarketDataWSC {

  private final MarketDataService marketDataService; // MarketDataService를 주입받음

  // 생성자: Spring이 자동으로 MarketDataService 인스턴스를 주입해줍니다.
  public MarketDataWSC(MarketDataService marketDataService) {
    this.marketDataService = marketDataService;
  }

  /**
   * 필터링된 라이브 시장 데이터를 MarketDataService로부터 가져오는 메소드.
   * 이 메소드가 에러를 발생시켰던 C:\Users\crc1190\IdeaProjects\Coin_Alarm\backend\src\main\java\market\MarketDataWSC.java:32 에 해당됩니다.
   *
   * @param large 시가총액 대형 코인 포함 여부
   * @param mid 시가총액 중형 코인 포함 여부
   * @param small 시가총액 소형 코인 포함 여부
   * @return 마켓 심볼을 키로 하는 CoinResponseDto 맵
   */
  // [핵심 수정] 반환 타입을 List<CoinResponseDto> 에서 Map<String, CoinResponseDto> 로 변경
  public Map<String, CoinResponseDto> getFilteredLiveMarketDataFromService(boolean large, boolean mid, boolean small) {
    // MarketDataService의 getFilteredLiveMarketData는 Map<String, CoinResponseDto>를 반환합니다.
    // 따라서 그 결과를 Map 타입 변수에 받아야 합니다.
    // 에러가 발생했던 32번째 줄 (예상 위치)
    Map<String, CoinResponseDto> data = marketDataService.getFilteredLiveMarketData(large, mid, small);
    return data;
  }

  // [참고] 만약 이 클래스에 다른 메소드들이 있었다면 여기에 추가해야 합니다.
  // 하지만 현재 에러 로그와 대화 내용상 이 메소드가 주요 에러 원인이었으므로 이 부분에 집중합니다.
}