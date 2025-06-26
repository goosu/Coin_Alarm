package coin;// src/main/java/coinalarm/Coin_Alarm/coin/Coin.java

// ... (다른 임포트들)
import jakarta.persistence.Entity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Setter; // @Setter도 사용하셨다면

@Entity
@Getter
@Setter // @Setter도 사용하셨다면
@NoArgsConstructor // <-- 이 어노테이션이 인자 없는 생성자를 만들어 줍니다.
@AllArgsConstructor // <-- 이 어노테이션이 모든 필드를 인자로 받는 생성자를 만들어 줍니다.
public class Coin {

  // ... (필드들)

  // Lombok 어노테이션을 사용했으므로, 아래의 수동 작성된 생성자는 삭제해야 합니다.
  // 이 부분이 에러의 원인입니다.
    /*
    public Coin() { // <-- 이 부분을 삭제하세요.
    }

    public Coin(String name, Long marketCap, String priceChange, Long volume, List<String> alarm) { // <-- 이 부분을 삭제하세요.
        this.name = name;
        this.marketCap = marketCap;
        this.priceChange = priceChange;
        this.volume = volume;
        this.alarm = alarm;
    }
    */

  // ... (Getter 메서드들 - @Getter를 사용했다면 이 부분도 삭제 가능)
    /*
    public Long getId() { return id; }
    public String getName() { return name; }
    public Long getMarketCap() { return marketCap; }
    public String getPriceChange() { return priceChange; }
    public Long getVolume() { return volume; }
    public List<String> getAlarm() { return alarm; }
    */
}
