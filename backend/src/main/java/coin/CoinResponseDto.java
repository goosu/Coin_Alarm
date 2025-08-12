package coinalarm.Coin_Alarm.coin;

import java.util.List;
import java.util.Objects;
import java.util.Collections;

public class CoinResponseDto {

  private Long id;
  private String name;
  private String symbol;
  private Double currentPrice;
  private String priceChange;
  private Double volume;

  private Double volume1m;
  private Double volume5m;
  private Double volume15m;
  private Double volume1h;

  private List<String> alarm;

  public CoinResponseDto() {}

  public CoinResponseDto(Long id, String name, String symbol, Double currentPrice,
                         String priceChange, Double volume,
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

  public static CoinResponseDto fromEntity(coinalarm.Coin_Alarm.coin.Coin coin) {
    if (coin == null) return null;

    Long id = null;
    try { id = (Long) coin.getClass().getMethod("getId").invoke(coin); } catch (Exception ignored) {}

    String name = null;
    try { name = (String) coin.getClass().getMethod("getName").invoke(coin); } catch (Exception ignored) {}

    String symbol = "";
    if (name != null && name.contains("-")) {
      String[] parts = name.split("-");
      symbol = parts.length > 1 ? parts[1] : parts[0];
    } else {
      try { Object s = coin.getClass().getMethod("getSymbol").invoke(coin); if (s != null) symbol = s.toString(); } catch (Exception ignored) {}
    }

    Double currentPrice = null;
    try {
      Object p = coin.getClass().getMethod("getCurrentPrice").invoke(coin);
      if (p instanceof Number) currentPrice = ((Number) p).doubleValue();
    } catch (Exception ignored) {
      // 안전하게 무시 — currentPrice는 null로 둠
    }

    String priceChange = null;
    try { Object pc = coin.getClass().getMethod("getPriceChange").invoke(coin); if (pc != null) priceChange = pc.toString(); } catch (Exception ignored) {}

    Double acc24 = null;
    try { Object a = coin.getClass().getMethod("getAccTradePrice24h").invoke(coin); if (a instanceof Number) acc24 = ((Number)a).doubleValue(); } catch (Exception ignored) {}

    return new CoinResponseDto(id, name, symbol, currentPrice, priceChange, acc24,
            0.0, 0.0, 0.0, 0.0, java.util.Collections.emptyList());
  }

  // getters and setters...
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
  public Double getVolume5m() { return volume5m; }
  public void setVolume5m(Double volume5m) { this.volume5m = volume5m; }
  public Double getVolume15m() { return volume15m; }
  public void setVolume15m(Double volume15m) { this.volume15m = volume15m; }
  public Double getVolume1h() { return volume1h; }
  public void setVolume1h(Double volume1h) { this.volume1h = volume1h; }
  public List<String> getAlarm() { return alarm; }
  public void setAlarm(List<String> alarm) { this.alarm = alarm; }
}