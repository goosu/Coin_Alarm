// frontend/src/App.tsx
import React, { useEffect, useRef, useState, useCallback } from "react";
import { fetchFavorites, addFavorite, removeFavorite } from "./api/favorites"; // 새로 생성한 파일 import
import "./index.css"; // 스타일시트 import

// ============================================================================
// Types & Constants
// ============================================================================

// 코인 데이터 타입 정의 (백엔드에서 오는 데이터 포맷에 맞춤)
type Coin = {
  symbol: string;
  price: number;
  marketCap?: number; // 시가총액 (선택적)
  volume1m: number; // 1분봉 거래대금 (필수)
  volume24h?: number; // 24시간 거래대금 (선택적)
  volume15m?: number; // 15분봉 거래대금 (선택적)
  volume1h?: number; // 1시간봉 거래대금 (선택적)
  buyVolume?: number; // 매수 거래대금 (줄다리기용)
  sellVolume?: number; // 매도 거래대금 (줄다리기용)
  maintenanceRate?: number; // 유지율 (선택적)
  change24h?: number; // 전일대비 (선택적)
  timestamp?: number; // 데이터 수신 시간
};

// WebSocket URL. .env 파일에 REACT_APP_WS_URL=ws://localhost:8080/ws 등으로 설정하세요.
const WS_URL = process.env.REACT_APP_WS_URL || "ws://localhost:8080/ws";
const ALARM_THRESHOLD = 300_000_000; // 1분봉 거래대금 알람 임계값 (3억)
const SOUND_SRC = "/alarm.mp3"; // 알람 소리 파일 경로. public 폴더에 넣어주세요.

// ============================================================================
// Custom Hooks & Utilities
// ============================================================================

// localStorage를 사용하는 Custom Hook (soundEnabled, filters, showAll 등 저장용)
function useLocalStorage<T>(key: string, initial: T): [T, React.Dispatch<React.SetStateAction<T>>] {
  const [state, setState] = useState<T>(() => {
    try {
      const storedValue = localStorage.getItem(key);
      return storedValue ? (JSON.parse(storedValue) as T) : initial;
    } catch (error) {
      console.error(`Error reading localStorage key “${key}”:`, error);
      return initial;
    }
  });

  useEffect(() => {
    try {
      localStorage.setItem(key, JSON.stringify(state));
    } catch (error) {
      console.error(`Error writing localStorage key “${key}”:`, error);
    }
  }, [key, state]);

  return [state, setState];
}

// 숫자를 화폐 형식으로 포맷 (예: 1,000,000)
function formatMoney(n: number): string {
  return n.toLocaleString();
}

// ============================================================================
// Main App Component
// ============================================================================

export default function App() {
  // 앱 실행 확인용 로그: 이 로그가 찍히지 않으면 App 컴포넌트가 마운트조차 안 된 것
  console.log("App component rendered/re-rendered.");

  const [liveCoins, setLiveCoins] = useState<Record<string, Coin>>({}); // 실시간 코인 데이터를 객체 형태로 저장
  const [favorites, setFavorites] = useState<string[]>([]); // 즐겨찾기 코인 심볼 배열
  const [soundEnabled, setSoundEnabled] = useLocalStorage<boolean>("soundEnabled", false); // 알람 사운드 활성화 여부
  const [alerts, setAlerts] = useState<{ id: string; symbol: string; msg: string; ts: number }[]>([]); // 알람 로그
  const [selectedCoinSymbol, setSelectedCoinSymbol] = useState<string | null>(null); // 현재 선택된 코인 심볼 (디테일 패널용)

  // 코인 목록 필터 상태 (시가총액 기준)
  const [filters, setFilters] = useLocalStorage('coinFilters', {
    all: true, large: false, mid: false, small: false
  });
  const [showAllCoins, setShowAllCoins] = useLocalStorage<boolean>("showAllCoins", false); // '모든 종목 보기' 토글

  const wsRef = useRef<WebSocket | null>(null); // WebSocket 인스턴스 참조
  const alarmAudioRef = useRef<HTMLAudioElement | null>(null); // Audio 객체 참조
  const lastAlertTimestamps = useRef<Record<string, number>>({}); // 심볼별 마지막 알람 시간 (쿨타임용)

  // ============================================================================
  // WebSocket Connection & Data Handling
  // ============================================================================

  // WebSocket 연결 설정 및 메시지 수신 처리
  useEffect(() => {
    console.log("App: WebSocket effect running, attempting to connect to:", WS_URL); // WebSocket 연결 시도 로그

    const ws = new WebSocket(WS_URL);
    wsRef.current = ws;

    ws.onopen = () => console.log("WebSocket connected:", WS_URL);

    ws.onmessage = (event) => {
      try {
        // 백엔드에서 받은 데이터는 JSON 형태라고 가정
        const receivedData = JSON.parse(event.data);
        // 데이터가 단일 객체일 수도, 여러 코인 정보가 담긴 배열일 수도 있음
        const items: any[] = Array.isArray(receivedData) ? receivedData : [receivedData];

        let hasNewData = false;
        // console.log("WS received items count:", items.length); // 받은 데이터 개수 로그
        const updatedCoins = { ...liveCoins }; // liveCoins는 이전 상태 기반이므로 deps에 넣지 않음.

        for (const item of items) {
          if (!item || !item.symbol) {
            // console.warn("Received invalid WS item:", item); // 유효하지 않은 항목 경고
            continue; // 유효하지 않은 데이터는 건너뜀
          }

          const symbol = item.symbol;
          const prevCoin = updatedCoins[symbol] || { symbol, price: 0, volume1m: 0 }; // 이전 데이터 또는 기본값

          // 새 코인 데이터 객체 생성
          const newCoin: Coin = {
            symbol: symbol,
            price: Number(item.price ?? prevCoin.price),
            marketCap: Number(item.marketCap ?? prevCoin.marketCap),
            volume1m: Number(item.volume1m ?? prevCoin.volume1m),
            volume24h: Number(item.volume24h ?? prevCoin.volume24h),
            volume15m: Number(item.volume15m ?? prevCoin.volume15m),
            volume1h: Number(item.volume1h ?? prevCoin.volume1h),
            buyVolume: Number(item.buyVolume ?? prevCoin.buyVolume ?? 0),
            sellVolume: Number(item.sellVolume ?? prevCoin.sellVolume ?? 0),
            maintenanceRate: Number(item.maintenanceRate ?? prevCoin.maintenanceRate),
            change24h: Number(item.change24h ?? prevCoin.change24h),
            timestamp: Number(item.timestamp ?? Date.now()),
          };

          updatedCoins[symbol] = newCoin; // 코인 데이터 업데이트
          hasNewData = true;

          // 알람 조건 검사 (1분봉 거래대금 3억 이상)
          const now = Date.now();
          const lastAlertTime = lastAlertTimestamps.current[symbol] ?? 0;
          if (newCoin.volume1m >= ALARM_THRESHOLD && (now - lastAlertTime) > 3000) { // 3초 쿨타임
            lastAlertTimestamps.current[symbol] = now; // 마지막 알람 시간 업데이트
            pushAlarm(
              `${symbol} 1분 체결대금 ${formatMoney(newCoin.volume1m)}원 도달!`,
              symbol,
              now
            );
          }
        }

        if (hasNewData) {
          setLiveCoins(updatedCoins); // 상태 업데이트
        }
      } catch (error) {
        console.error("WebSocket message parsing error:", error, event.data);
      }
    };

    ws.onclose = () => {
      console.log("WebSocket disconnected. Attempting to reconnect in 3s...");
      // 재연결 로직: 현재 wsRef가 이 인스턴스를 참조하고 있다면 재연결 시도
      // (이전에 setTimeout으로 감싸져 있었는데, 즉시 시도 로직으로 변경. 필요 시 setTimeout 다시 추가)
      if (wsRef.current === ws) {
        wsRef.current = null; // 이전 참조 제거
        // 여기서는 간단히 useEffect가 마운트될 때만 연결 시도하므로, 페이지 새로고침 등으로 재시작 권장.
        // 또는 외부에서 reconnectionManager 같은 모듈로 재연결 로직 구현 권장.
      }
      setTimeout(() => { // 3초 후 재연결 시도 로직 다시 추가. 이펙트가 한 번 더 실행되도록 트리거 (wsRef.current가 null이므로 새 WS 생성)
        if (!wsRef.current) { // 현재 WS가 없으면 다시 연결 시도
          console.log("App: Retrying WebSocket connection...");
          const retryWs = new WebSocket(WS_URL);
          wsRef.current = retryWs;
          // 재연결 로직 내부에서 다시 onopen, onmessage 등 설정 (또는 이펙트 자체 재실행 유도)
          // 하지만 지금은 useEffect의 클린업이 ws를 닫고 wsRef를 null로 만든 후 다시 useEffect가 트리거되는 방식으로 동작함
        }
      }, 3000);
    };

    ws.onerror = (error) => console.error("WebSocket error:", error);

    // 컴포넌트 언마운트 시 WebSocket 연결 종료
    return () => {
      console.log("WebSocket cleanup: closing connection."); // 클린업 로그
      ws.close();
      wsRef.current = null;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []); // 마운트 시 한 번만 실행, WS_URL 변경될 때 재실행


  // ============================================================================
  // Favorites Management (Server Sync)
  // ============================================================================

  // 컴포넌트 마운트 시 서버에서 즐겨찾기 목록 로드
  useEffect(() => {
    let componentMounted = true;
    console.log("App: Favorites effect running."); // 즐겨찾기 로드 시도 로그
    (async () => {
      try {
        const serverFavorites = await fetchFavorites();
        if (componentMounted) setFavorites(serverFavorites);
      } catch (error) {
        console.warn("Failed to load favorites from server. Falling back to local storage.", error);
        // 서버 로드 실패 시, 로컬 스토리지에 저장된 즐겨찾기 목록 사용
        try {
          const localStoredFavorites = localStorage.getItem("favorites");
          if (localStoredFavorites) {
            setFavorites(JSON.parse(localStoredFavorites));
            console.log("App: Favorites loaded from local storage."); // 로컬 스토리지 로드 로그
          }
        } catch (localError) {
          console.error("Failed to parse local storage favorites:", localError);
        }
      }
    })();
    return () => {
      componentMounted = false;
      console.log("App: Favorites effect cleanup."); // 즐겨찾기 이펙트 클린업
    };
  }, []);

  // 즐겨찾기 목록이 변경될 때마다 로컬 스토리지에 저장
  useEffect(() => {
    // console.log("App: Favorites changed, saving to local storage."); // 즐겨찾기 저장 로그
    try {
      localStorage.setItem("favorites", JSON.stringify(favorites));
    } catch (error) {
      console.error("Failed to save favorites to local storage:", error);
    }
  }, [favorites]);

  // 즐겨찾기 추가/제거 토글 함수 (서버와 동기화, Optimistic UI 업데이트 적용)
  const toggleFavorite = useCallback(async (symbol: string) => {
    const isCurrentlyFavorite = favorites.includes(symbol);
    console.log(`App: Toggling favorite for ${symbol}. Current: ${isCurrentlyFavorite}`); // 즐겨찾기 토글 로그

    // Optimistic Update: UI를 먼저 업데이트하고 서버 요청
    setFavorites(prev => isCurrentlyFavorite ? prev.filter(s => s !== symbol) : [symbol, ...prev]);

    try {
      if (isCurrentlyFavorite) {
        await removeFavorite(symbol); // 서버에서 제거
        console.log(`App: Removed ${symbol} from favorites on server.`);
      } else {
        await addFavorite(symbol); // 서버에 추가
        console.log(`App: Added ${symbol} to favorites on server.`);
      }
    } catch (error) {
      console.error(`Failed to sync favorite for ${symbol}. Rolling back UI.`, error);
      // Rollback: 서버 요청 실패 시 UI 상태를 원래대로 되돌림
      setFavorites(prev => {
        const currentSet = new Set(prev);
        if (isCurrentlyFavorite) { // 제거 실패 -> 다시 추가
          currentSet.add(symbol);
        } else { // 추가 실패 -> 다시 제거
          currentSet.delete(symbol);
        }
        return Array.from(currentSet);
      });
    }
  }, [favorites]); // favorites 배열이 변경될 때만 함수 재생성

  // ============================================================================
  // Alarm & Notification Logic
  // ============================================================================

  // 알람 발생 시 로그에 추가하고 사운드 재생/브라우저 알림 표시
  const pushAlarm = useCallback((message: string, symbol: string, timestamp: number) => {
    const alarmId = `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`; // 고유 ID 생성
    setAlerts(prevAlerts => [{ id: alarmId, symbol, msg: message, ts: timestamp }, ...prevAlerts].slice(0, 100)); // 최대 100개 알람 유지
    console.log("App: New alarm triggered:", message); // 알람 로그

    // 브라우저 알림 (권한 필요)
    if ("Notification" in window && Notification.permission === "granted") {
      new Notification("코인 알람", { body: message, icon: "/coin-icon.png" }); // 알림 아이콘 경로
      console.log("App: Browser notification shown."); // 브라우저 알림 표시 로그
    }

    // 알람 사운드 재생
    if (soundEnabled && alarmAudioRef.current) {
      alarmAudioRef.current.currentTime = 0; // 재생 위치를 처음으로
      alarmAudioRef.current.play().catch(e => console.warn("Audio playback failed:", e)); // 재생 실패 시 경고
    }
  }, [soundEnabled]);

  // 사운드 활성화 (사용자 제스처 필요)
  const enableSoundGesture = useCallback(() => {
    setSoundEnabled(true);
    console.log("App: Sound enabled via user gesture."); // 사운드 활성화 로그
    if (alarmAudioRef.current) {
      // 사용자 제스처를 통해 오디오 재생 컨텍스트 활성화 시도
      alarmAudioRef.current.play().then(() => {
        alarmAudioRef.current?.pause(); // 즉시 일시정지하여 소리 안나게 함
        alarmAudioRef.current!.currentTime = 0; // 재생 위치 초기화
        console.log("App: Audio context activated successfully.");
      }).catch(e => console.warn("Failed to enable sound via gesture:", e));
    }
  }, []);

  // ============================================================================
  // Coin List Filtering & Sorting
  // ============================================================================

  // 시가총액 기반 코인 필터 로직
  const passesMarketCapFilter = useCallback((coin: Coin) => {
    if (filters.all) return true;
    const marketCap = coin.marketCap ?? 0;
    if (filters.large && marketCap >= 5_000_000_000_000) return true; // 5조 이상
    if (filters.mid && marketCap >= 700_000_000_000) return true;     // 7천억 이상
    if (filters.small && marketCap >= 50_000_000_000) return true;    // 5백억 이상
    return false;
  }, [filters]);

  // 코인 목록 필터 변경 핸들러
  const handleCoinFilterChange = useCallback((key: 'all' | 'large' | 'mid' | 'small') => {
    console.log("App: Filtering coins by:", key); // 필터 변경 로그
    setFilters(prev => {
      if (key === 'all') { // '전체' 선택 시 나머지 필터 해제
        return { all: true, large: false, mid: false, small: false };
      }
      const newFilters = { ...prev, [key]: !prev[key], all: false };
      // 모든 개별 필터가 해제되면 '전체'를 자동으로 선택
      if (!newFilters.large && !newFilters.mid && !newFilters.small) {
        newFilters.all = true;
      }
      return newFilters;
    });
  }, []);

  // 화면에 표시될 코인 목록 계산 (필터링 및 정렬 적용)
  const displayedCoins = React.useMemo(() => {
    console.log("App: Recalculating displayed coins."); // 표시 코인 계산 로그
    const allCoins = Object.values(liveCoins);

    // 1차 필터: 시가총액 필터 적용
    let filtered = allCoins.filter(passesMarketCapFilter);

    // 2차 필터: '모든 종목 보기' 토글이 꺼져있으면 즐겨찾기 또는 1분 거래대금 3억 이상만 표시
    if (!showAllCoins) {
      filtered = filtered.filter(coin =>
        favorites.includes(coin.symbol) || coin.volume1m >= ALARM_THRESHOLD
      );
    }

    // 3차 정렬: 즐겨찾기 코인 우선, 그 다음 1분 거래대금 내림차순
    return filtered.sort((a, b) => {
      const aIsFavorite = favorites.includes(a.symbol);
      const bIsFavorite = favorites.includes(b.symbol);

      if (aIsFavorite && !bIsFavorite) return -1; // a가 즐겨찾기, b가 아니면 a 먼저
      if (!aIsFavorite && bIsFavorite) return 1;  // b가 즐겨찾기, a가 아니면 b 먼저

      return b.volume1m - a.volume1m; // 1분봉 거래대금 내림차순
    });
  }, [liveCoins, filters, showAllCoins, favorites, passesMarketCapFilter]);

  // ============================================================================
  // UI Rendering
  // ============================================================================

  return (
    <div className="app-root">
      {/* ======================= Header ======================= */}
      <header className="app-header">
        <h1>Coin Alarm Dashboard</h1>
        <div className="header-controls">
          <button onClick={() => {
            // 브라우저 알림 권한 요청
            if ("Notification" in window && Notification.permission !== "granted") {
              Notification.requestPermission();
            }
            alert("알림/사운드는 브라우저 권한 허용 및 사용자 첫 클릭이 필요합니다.");
          }}>
            알림 권한 요청
          </button>
          {!soundEnabled ? (
            <button onClick={enableSoundGesture}>사운드 허용 (클릭)</button>
          ) : (
            <button onClick={() => setSoundEnabled(false)}>사운드 끄기</button>
          )}
        </div>
      </header>

      {/* ======================= 상단 옵션 섹션 (시가총액 필터 & 모든 종목 보기 토글) ======================= */}
      <div className="top-options-section">
        <h2 className="section-title">코인 필터 & 보기 옵션</h2>
        <div className="flex space-x-4 mb-4 filter-checkboxes">
          {/* 시가총액 필터 체크박스 */}
          <label>
            <input type="checkbox" checked={filters.all} onChange={() => handleCoinFilterChange('all')} />{' '}
            전체
          </label>
          <label>
            <input type="checkbox" checked={filters.large} onChange={() => handleCoinFilterChange('large')} />{' '}
            대형(5조 이상)
          </label>
          <label>
            <input type="checkbox" checked={filters.mid} onChange={() => handleCoinFilterChange('mid')} />{' '}
            중형(7천억 이상)
          </label>
          <label>
            <input type="checkbox" checked={filters.small} onChange={() => handleCoinFilterChange('small')} />{' '}
            소형(5백억 이상)
          </label>
        </div>

        {/* '모든 종목 보기' 토글 버튼 */}
        <button
          onClick={() => setShowAllCoins(prev => !prev)}
          className="toggle-all-coins-btn"
        >
          {showAllCoins ? "필터 적용 보기" : "모든 종목 보기"}
        </button>
      </div>

      {/* ======================= 중앙 메인 영역 (코인목록 및 알람 로그) ======================= */}
      <main className="main-content-area">
        {/* 코인 목록 섹션 */}
        <section className="coin-list-section">
          <h2 className="section-title">실시간 코인 목록</h2>
          <div className="coin-grid-summary">
            {/* 이 부분은 현재 데이터에서는 구현이 어렵습니다. (BTC, ETH 등 특정 코인만 표시하는 부분)
                나중에 liveCoins 객체에서 key값을 가지고 특정 코인들만 추출해서 보여줄 수 있습니다.
            <div className="bg-gray-100 p-2 text-center rounded">BTC</div>
            */}
          </div>
          <div className="coin-table-container">
            <h2 className="section-title">코인 목록</h2> {/* 중복된 h2 태그 제거 또는 용도 명확화 */}
            {displayedCoins.length === 0 ? (
              <p className="no-coins-message">조건에 맞는 코인이 없습니다.</p>
            ) : (
              <table className="coin-table">
                <thead>
                  <tr>
                    <th>심볼</th>
                    <th>현재가</th>
                    <th>24H 거래대금</th>
                    <th>1분봉 거래대금</th>
                    <th>15분봉 거래대금</th>
                    <th>1시간봉 거래대금</th>
                    <th>유지율</th>
                    <th>전일대비</th>
                  </tr>
                </thead>
                <tbody>
                  {displayedCoins.map((coin) => (
                    <tr
                      key={coin.symbol}
                      className={favorites.includes(coin.symbol) ? "is-favorite" : ""}
                      onClick={() => setSelectedCoinSymbol(coin.symbol)} // 클릭 시 디테일 패널 열기
                    >
                      <td>
                        <button
                          onClick={(e) => { e.stopPropagation(); toggleFavorite(coin.symbol); }}
                          className="favorite-toggle-btn"
                          aria-label="즐겨찾기 토글"
                          title={favorites.includes(coin.symbol) ? "즐겨찾기 해제" : "즐겨찾기 추가"}
                        >
                          {favorites.includes(coin.symbol) ? "★" : "☆"}
                        </button>
                        {coin.symbol}
                      </td>
                      <td>{formatMoney(coin.price)}원</td>
                      <td>{formatMoney(coin.volume24h ?? 0)}원</td>
                      <td>{formatMoney(coin.volume1m)}원</td>
                      <td>{formatMoney(coin.volume15m ?? 0)}원</td>
                      <td>{formatMoney(coin.volume1h ?? 0)}원</td>
                      <td>{coin.maintenanceRate ? `${coin.maintenanceRate}%` : '-'}</td>
                      <td>{coin.change24h ? `${coin.change24h}%` : '-'}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </section>

        {/* 상세(줄다리기) 및 알람 로그 섹션 */}
        <aside className="detail-and-alerts-section">
          {/* 상세(줄다리기) 패널 */}
          <div className="detail-panel-container">
            <h2 className="section-title">상세 (매수/매도 비율)</h2>
            {selectedCoinSymbol ? (
              <DetailPanel
                symbol={selectedCoinSymbol}
                coin={liveCoins[selectedCoinSymbol]}
                onClose={() => setSelectedCoinSymbol(null)}
              />
            ) : (
              <p className="no-coin-selected-message">
                목록에서 코인을 클릭하면 상세 정보를 볼 수 있습니다.
              </p>
            )}
          </div>

          {/* 알람 로그 */}
          <div className="alarm-log-container">
            <h2 className="section-title">알람 로그 (1분봉 ≥ 3억)</h2>
            <div className="alarm-list">
              {alerts.length === 0 ? (
                <p className="no-alarms-message">새로운 알람이 없습니다.</p>
              ) : (
                alerts.map(alarm => (
                  <div key={alarm.id} className="alarm-item">
                    <span className="alarm-time">[{new Date(alarm.ts).toLocaleTimeString()}]</span>
                    <span className="alarm-message">{alarm.msg}</span>
                  </div>
                ))
              )}
            </div>
          </div>
        </aside>
      </main>
    </div>
  );
}

// ============================================================================
// Detail Panel Component (매수/매도 비율 시각화)
// ============================================================================
type DetailPanelProps = {
  symbol: string;
  coin?: Coin; // 코인 데이터 (선택적: 없을 수도 있음)
  onClose: () => void;
};

function DetailPanel({ symbol, coin, onClose }: DetailPanelProps) {
  // 매수/매도 비율 계산
  const buyVolume = coin?.buyVolume ?? 0;
  const sellVolume = coin?.sellVolume ?? 0;
  const totalVolume = buyVolume + sellVolume;

  const buyPercent = totalVolume === 0 ? 0 : Math.round((buyVolume / totalVolume) * 100);
  const sellPercent = 100 - buyPercent;

  return (
    <div className="detail-panel">
      <div className="detail-panel-header">
        <h3>{symbol} 상세 정보</h3>
        <button onClick={onClose} className="close-detail-btn">닫기</button>
      </div>
      <div className="detail-panel-content">
        <p>현재가: {formatMoney(coin?.price ?? 0)}원</p>
        <p>1분봉 거래대금: {formatMoney(coin?.volume1m ?? 0)}원</p>

        {/* 매수/매도 줄다리기 바 */}
        <div className="tug-of-war-bar-container">
          <div className="buy-bar" style={{ width: `${buyPercent}%` }}>
            매수: {buyPercent}%
          </div>
          <div className="sell-bar" style={{ width: `${sellPercent}%` }}>
            매도: {sellPercent}%
          </div>
        </div>
        <div className="tug-of-war-volumes">
          <span>매수량: {formatMoney(buyVolume)}원</span>
          <span>매도량: {formatMoney(sellVolume)}원</span>
          <span>총량: {formatMoney(totalVolume)}원</span>
        </div>
      </div>
    </div>
  );
}