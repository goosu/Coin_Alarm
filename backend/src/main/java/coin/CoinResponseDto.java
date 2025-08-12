package coinalarm.Coin_Alarm.coin;

import java.util.List;
import java.util.Objects;

public class CoinResponseDto {

  private Long id;
  private String name;
  private String symbol;
  private Double currentPrice;
  private String priceChange;
  private Double volume; // 24H 거래대금

  private Double volume1m;
  private Double volume5m;
  private Double volume15m;
  private Double volume1h;

  private List<String> alarm;

  public CoinResponseDto() {
  }

  public CoinResponseDto(Long id, String name, String symbol,
                         Double currentPrice, String priceChange, Double volume,
                         Double volume1m, Double volume5m, Double volume15m, Double volume1h,
                         List<String> alarm) {
    this.id = id;
    this.name = name;
    this.symbol = symbol;
    this.currentPrice = currentPrice;
    this.priceChange = priceChange;
    this.volume = volume;
    this.volume1m = volume1m;
    this.volume5m = volume5m;
    this.volume15m = volume15m;
    this.volume1h = volume1h;
    this.alarm = alarm;
  }

  // getters & setters (명시적)
  public Long getId() { return id; }
  public void setId(Long id) { this.id = id; }

  public String getName() { return name; }
  public void setName(String name) { this.name = name; }

  public String getSymbol() { return symbol; }
  public void setSymbol(String symbol) { this.symbol = symbol; }

  public Double getCurrentPrice() { return currentPrice; }
  public void setCurrentPrice(Double currentPrice) { this.currentPrice = currentPrice; }

  public String getPriceChange() { return priceChange; }
  public void setPriceChange(String priceChange) { this.priceChange = priceChange; }

  public Double getVolume() { return volume; }
  public void setVolume(Double volume) { this.volume = volume; }

  public Double getVolume1m() { return volume1m; }
  public void setVolume1m(Double volume1m) { this.volume1m = volume1m; }

  public Double getVolume5m() { return volume5m; } // 중요: 이 getter가 문제였음
  public void setVolume5m(Double volume5m) { this.volume5m = volume5m; }

  public Double getVolume15m() { return volume15m; }
  public void setVolume15m(Double volume15m) { this.volume15m = volume15m; }

  public Double getVolume1h() { return volume1h; }
  public void setVolume1h(Double volume1h) { this.volume1h = volume1h; }

  public List<String> getAlarm() { return alarm; }
  public void setAlarm(List<String> alarm) { this.alarm = alarm; }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CoinResponseDto)) return false;
    CoinResponseDto that = (CoinResponseDto) o;
    return Objects.equals(id, that.id) && Objects.equals(name, that.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(id, name);
  }
}