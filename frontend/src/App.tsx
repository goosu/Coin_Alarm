 import React, { useEffect, useRef, useState, useCallback } from "react";
import { fetchFavorites, addFavorite, removeFavorite } from "./api/favorites"; // 새로 생성한 파일 import
import "./index.css"; // 스타일시트 import

// *** [신규] STOMP / SockJS 관련 임포트 (이전 순수 WebSocket 대신 사용) ***
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';


// ============================================================================
// Types & Constants
// ============================================================================

// 코인 데이터 타입 정의 (백엔드 CoinResponseDto와 App.tsx UI/로직 매핑에 맞춰 수정됨)
type Coin = {
  symbol: string;
  price: number;
  marketCap?: number; // 시가총액 (선택적, 현재 백엔드에서 0으로 전달될 수 있음)

  // *** [신규] CoinResponseDto에서 오는 필드들에 맞춤 (추가됨) ***
  volume1m: number; // 1분봉 거래대금
  volume5m: number; // 5분봉 거래대금
  volume15m?: number; // 15분봉 거래대금
  volume1h?: number; // 1시간봉 거래대금

  volume24h: number; // 24시간 누적 거래대금 (CoinResponseDto의 accTradePrice24h와 매핑)

  buyVolume: number; // 매수 거래대금 (줄다리기용, 현재 백엔드에서 0으로 전달)
  sellVolume: number; // 매도 거래대금 (줄다리기용, 현재 백엔드에서 0으로 전달)

  change24h: number; // 전일대비 (필수, 백엔드에서 %로 계산되어 옴)
  maintenanceRate?: number; // 유지율 (선택적, 현재 백엔드에서 0으로 전달)
  //timestamp?: number; // 데이터 수신 시간 (CoinResponseDto에 timestamp 필드가 없다면 현재 시간 사용)

    // *** [신규 추가] 즐겨찾기 여부 ***
  isFavorite?: boolean; // 백엔드에서 전달되는 즐겨찾기 여부

};


// *** [수정] 환경 변수 접근 방식 (Vite에서 사용하는 import.meta.env 방식) ***
const WS_URL = import.meta.env?.VITE_WS_URL || "ws://localhost:8080/ws";

const ALARM_THRESHOLD = 300_000_000; // 1분봉 거래대금 알람 임계값 (3억)
const SOUND_SRC = "/alarm.mp3"; // 알람 소리 파일 경로. public 폴더에 넣어주세요.

// *** [신규] 알람 쿨타임 상수 (Refactor: useCallback/useEffect 내에서 사용될 상수) ***
const ALARM_COOLDOWN_SECONDS = 3;

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
function formatMoney(n: number): string {  //이런게 무슨문법인지는 나중에 학습
  // 매수/매도/거래대금 등 큰 숫자에만 적용 (임계값 1000만원)
  if (Math.abs(n) >= 1_000_000) { // 1000만원 이상
//     // 조 단위
//     if (Math.abs(n) >= 1_000_000_000_000) {
//       return (n / 1_000_000_000_000).toLocaleString(undefined, { maximumFractionDigits: 2 }) + '조원';
//     }
//     // 억 단위
//     if (Math.abs(n) >= 1_000_000_000) {
//       return (n / 1_000_000_000).toLocaleString(undefined, { maximumFractionDigits: 2 }) + '억원';
//     }
    // 백만원 단위 (천만원 단위부터 백만원으로 표현 시작)
    return (n / 1_000_000).toLocaleString(undefined, { maximumFractionDigits: 0 }) + '백만';
  }
  // 그 외 작은 금액은 원 단위로 표시
  return n.toLocaleString(undefined, { maximumFractionDigits: 0 }) + '원'; // 원 단위 명시
}

//20250918 전일대비 % 색상결정 STR
function getChangeColor(change: number) : string{
  if(change > 0) return 'text-red-600';
  if(change < 0) return 'text-blue-600';
  return 'text-gray-600'; //보합
}

//20250919 전일대비 % 포맷 함수 ***
function formatChange(change: number): string{금요일 팝송
  const prefix = change > 0 ? '+' : '';
  return `&{prefix}${change.toFixed(2)}%`;
}


//

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

  // *** [기존] WebSocket 인스턴스 참조 (전체 주석 처리) ***
  // const wsRef = useRef<WebSocket | null>(null);
  // *** [신규] STOMP 클라이언트 인스턴스 참조 ***
  const stompClientRef = useRef<Client | null>(null);

  const alarmAudioRef = useRef<HTMLAudioElement | null>(null); // Audio 객체 참조
  // *** [수정] lastAlarmTimestamps useRef 초기화 및 사용 방식 (ReferenceError 해결) ***
  const lastAlarmTimestamps = useRef<Record<string, number>>({}); // 심볼별 마지막 알람 시간 (쿨타임용)

  // ============================================================================
  // WebSocket Connection & Data Handling (STOMP 버전 - 크게 수정됨)
  // ============================================================================

  useEffect(() => {
    console.log("App: WebSocket effect running, attempting to connect to:", WS_URL);

    // *** [신규] STOMP 클라이언트 생성 및 설정 시작 ***
    const client = new Client({
      // SockJS를 사용하여 WebSocket 연결을 시도합니다.
      // Spring Boot 백엔드에 MarketDataConfig.java의 addEndpoint().withSockJS()가 설정되어 있어야 합니다.
      webSocketFactory: () => {
        // WS_URL (예: ws://localhost:8080/ws)에서 'ws'를 'http'로 변경하여 SockJS URL 생성
        const sockJsUrl = WS_URL.replace(/^ws/, 'http');
        console.log("App: Connecting to SockJS URL:", sockJsUrl);
        return new SockJS(sockJsUrl); // SockJS 클라이언트 생성
      },
      reconnectDelay: 5000, // 재연결 딜레이 5초
      heartbeatIncoming: 4000, // 서버에서 하트비트 메시지 대기 시간 (ms)
      heartbeatOutgoing: 4000, // 서버로 하트비트 메시지 전송 시간 (ms)

      onConnect: (frame) => {
        console.log('🚀 STOMP Connected to broker:', frame); // 연결 성공 로그

        // '/topic/marketData' 토픽을 구독하여 메시지를 받습니다.
        // 백엔드 MarketDataService에서 List<CoinResponseDto>를 JSON 배열 형태로 보냄
        client.subscribe('/topic/marketData', (message) => {
          try {
            console.log("🚀 Received raw STOMP message:", message.body); // 메시지 본문 로그
            const receivedData = JSON.parse(message.body); // JSON 파싱

            let coinsArray: Coin[] = [];

            // *** [수정] 백엔드에서 보내는 Map<String, CoinResponseDto> 형태 (즉, JS에서 객체 {})를 처리하는 로직 ***
            if (typeof receivedData === 'object' && receivedData !== null && !Array.isArray(receivedData)) {
                // receivedData는 이제 { "KRW-BTC": {...}, "KRW-ETH": {...} } 형태의 객체입니다.
                // 이 객체의 값(value)들만 뽑아서 배열로 만듭니다.
                coinsArray = Object.values(receivedData).map((dto: any) => {
                    return {
                        symbol: dto.symbol, // 이 dto.symbol이 'KRW-BTC'와 같은 심볼 값입니다.
                        price: Number(dto.price ?? 0),
                        volume1m: Number(dto.volume1m ?? 0), // CoinResponseDto의 volume1m 필드
                        volume5m: Number(dto.volume5m ?? 0), // CoinResponseDto의 volume5m 필드
                        volume15m: Number(dto.volume15m ?? 0),
                        volume1h: Number(dto.volume1h ?? 0),

                        // CoinResponseDto에서 accTradePrice24h는 String/Double으로 올 수 있음
                        // 이를 App.tsx Coin 타입의 volume24h로 매핑
                        volume24h: Number(dto.accTradePrice24h ?? 0),

                        change24h: Number(dto.change24h ?? 0), // 백엔드에서 %로 계산되어 옴 (소수점 유지)

                        buyVolume: Number(dto.buyVolume ?? 0), // 현재 백엔드에서 0으로 옴
                        sellVolume: Number(dto.sellVolume ?? 0), // 현재 백엔드에서 0으로 옴

                        // 현재 CoinResponseDto에 없는 필드들은 0 또는 기본값으로 초기화
                        marketCap: 0,
                        maintenanceRate: 0,
                        // CoinResponseDto에 timestamp 필드가 있다면 dto.timestamp 사용, 없으면 현재 시간
                        timestamp: dto.timestamp ? Number(dto.timestamp) : Date.now(),
                        isFavorite: Boolean(dto.isFavorite), //20250919 즐겨찾기 여부
                    };
                });
            }
            // *** [기존] 백엔드에서 List<CoinResponseDto> (JSON 배열)를 보낼 경우의 처리 로직 (주석 처리) ***
            // else if (Array.isArray(receivedData)) {
            //     coinsArray = receivedData.map((dto: any) => { /* ... 기존 배열 매핑 로직 ... */ return {}; });
            // }
            else { // 객체도 아니고 배열도 아닌 예상치 못한 형식인 경우
                console.error("❌ Received WebSocket message is neither an object nor an array (unexpected format):", receivedData);
                return; // 처리하지 않고 종료
            }

            let hasNewData = false;
            const updatedCoins = { ...liveCoins }; // 이전 상태 복사

            coinsArray.forEach(coin => {
                updatedCoins[coin.symbol] = coin; // symbol을 키로 사용하여 맵에 저장
                hasNewData = true;

                // 알람 조건 검사 (ReferenceError: lastAlarmTimestamps 해결 포함)
                const now = Date.now();
                // *** [수정] lastAlarmTimestamps 사용 방식 (ReferenceError 해결) ***
                // useRef의 .current 속성을 통해 직접 접근 및 업데이트
                const lastAlertTimeForSymbol = lastAlarmTimestamps.current[coin.symbol] || 0;
                if (coin.volume1m >= ALARM_THRESHOLD && (now - lastAlertTimeForSymbol) > (ALARM_COOLDOWN_SECONDS * 1000)) {
                  lastAlarmTimestamps.current[coin.symbol] = now; // 레퍼런스 업데이트
                  pushAlarm(
                    `${coin.symbol} 1분 체결대금 ${formatMoney(coin.volume1m)}원 도달!`,
                    coin.symbol,
                    now
                  );
                }
            });

            if (hasNewData) {
              setLiveCoins(updatedCoins);
              console.log("🎉 liveCoins updated, total:", Object.keys(updatedCoins).length, "coins.");
            }

          } catch (error) {
            console.error("❌ STOMP message parsing or processing error:", error, "Raw body:", message.body);
          }
        });

        // '/topic/buy-sell-ratio' 토픽 구독 (MarketDataService에 해당 토픽 발행 로직이 있다면 활성화)
        // MarketDataService의 pushLatestMarketDataToClients 메소드 하단을 확인하여 해당 토픽을 발행하는지 확인하세요.
        client.subscribe('/topic/buy-sell-ratio', (message) => {
           try {
             const ratios = JSON.parse(message.body);
             console.log("Received buy-sell ratios:", ratios);
             // TODO: 이 데이터를 UI (예: 디테일 패널의 매수/매도 비율)에 반영하는 로직 추가
             // 이 로직은 `liveCoins` 상태를 업데이트할 때 buyRatio/sellRatio를 함께 업데이트하는 방식으로 구현할 수 있습니다.
           } catch (error) {
             console.error("Error parsing buy-sell ratio message:", error, "Raw body:", message.body);
           }
         });
      },


      : (frame) => {
        // STOMP 프로토콜 에러 발생 시 (연결 에러, 메시지 에러 등)
        console.error('❌ STOMP Broker reported error:', frame.headers['message'], 'Details:', frame.body);
      },

      onWebSocketError: (event) => {
        // 하위 WebSocket 계층에서 에러 발생 시
        console.error('❌ WebSocket error at STOMP client layer:', event);
      },

      onDisconnect: () => {
        console.warn('⚠️ STOMP Disconnected from broker.');
      },
    });

    // STOMP 클라이언트 활성화
    client.activate();
    
    stompClientRef.current = client; // Ref에 클라이언트 인스턴스 저장

    // 컴포넌트 언마운트 시 STOMP 클라이언트 비활성화
    return () => {
      console.log("STOMP client cleanup: deactivating connection.");
      if (stompClientRef.current) {
        stompClientRef.current.deactivate();
        stompClientRef.current = null;
      }
    };
  }, []); // 마운트 시 한 번만 실행


  // ============================================================================
  // Favorites Management (Server Sync)
  // ============================================================================

  // 컴포넌트 마운트 시 서버에서 즐겨찾기 목록 로드
  useEffect(() => {
    let componentMounted = true;
    console.log("App: Favorites effect running.");
    (async () => {
      try {
        const serverFavorites = await fetchFavorites();
        if (componentMounted) setFavorites(serverFavorites);
      } catch (error) {
        console.warn("Failed to load favorites from server. Falling back to local storage.", error);
        try {
          const localStoredFavorites = localStorage.getItem("favorites");
          if (localStoredFavorites) {
            setFavorites(JSON.parse(localStoredFavorites));
            console.log("App: Favorites loaded from local storage.");
          }
        } catch (localError) {
          console.error("Failed to parse local storage favorites:", localError);
        }
      }
    })();
    return () => {
      componentMounted = false;
      console.log("App: Favorites effect cleanup.");
    };
  }, []);

  // 즐겨찾기 목록이 변경될 때마다 로컬 스토리지에 저장
  useEffect(() => {



    try {
      localStorage.setItem("favorites", JSON.stringify(favorites));
    } catch (error) {
      console.error("Failed to save favorites to local storage:", error);
    }
  }, [favorites]);

  // 즐겨찾기 추가/제거 토글 함수 (서버와 동기화, Optimistic UI 업데이트 적용)
  const toggleFavorite = useCallback(async (symbol: string) => {
    const isCurrentlyFavorite = favorites.includes(symbol);
    console.log(`App: Toggling favorite for ${symbol}. Current: ${isCurrentlyFavorite}`);

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
  }, [favorites]);

  // ============================================================================
  // Alarm & Notification Logic
  // ============================================================================

  // 알람 발생 시 로그에 추가하고 사운드 재생/브라우저 알림 표시
  const pushAlarm = useCallback((message: string, symbol: string, timestamp: number) => {
    const alarmId = `${Date.now()}-${Math.random().toString(36).substring(2, 9)}`; // 고유 ID 생성
    setAlerts(prevAlerts => [{ id: alarmId, symbol, msg: message, ts: timestamp }, ...prevAlerts].slice(0, 100)); // 최대 100개 알람 유지
    console.log("App: New alarm triggered:", message);

    // 브라우저 알림 (권한 필요)
    if ("Notification" in window && Notification.permission === "granted") {
      new Notification("코인 알람", { body: message, icon: "/coin-icon.png" });
      console.log("App: Browser notification shown.");
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
    console.log("App: Sound enabled via user gesture.");
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
    console.log("App: Filtering coins by:", key);
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
    console.log("App: Recalculating displayed coins.");
    const allCoins = Object.values(liveCoins);

    // 1차 필터: 시가총액 필터 적용 (현재 백엔드에서 marketCap을 0으로 줌. 필터링 안 될 수 있음)
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

      if (aIsFavorite && !bIsFavorite) return -1;
      if (!aIsFavorite && bIsFavorite) return 1;

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
          {/* 음원 재생을 위한 Audio 태그. display: none으로 숨겨둠. */}
          <audio ref={alarmAudioRef} src={SOUND_SRC} preload="auto" style={{ display: 'none' }} />
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
            {displayedCoins.length === 0 ? (
              <p className="no-coins-message">조건에 맞는 코인이 없습니다.</p>
            ) : (
              <table className="coin-table">
                <thead>
                  <tr>
                    <th>심볼</th>
                    <th>현재가</th>
                    <th>24H 거래대금</th>
                    <th>전일대비</th> {/* 유지율, 전일대비 통합 */}
                    <th>Vol 1m</th>
                    <th>Vol 5m</th> {/* 새로 추가 */}
                    <th>Vol 15m</th>
                    <th>Vol 1h</th>
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
                      </td>  {/*20250911 서버에서 백만원을 스트링으로 받음 price에는 나누는거없이 해야할듯*/}
                      <td>{coin.price}원</td>
                      <td>{formatMoney(coin.volume24h ?? 0)}</td>
                      <td>{coin.change24h ? `${coin.change24h.toFixed(2)}%` : '-'}</td>
                      <td>{formatMoney(coin.volume1m)}</td>
                      <td>{formatMoney(coin.volume5m ?? 0)}</td> {/* 새로 추가 */}
                      <td>{formatMoney(coin.volume15m ?? 0)}</td>
                      <td>{formatMoney(coin.volume1h ?? 0)}</td>
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
  // 매수/매도 거래대금은 현재 백엔드에서 0으로 오기 때문에 바는 제대로 그려지지 않을 수 있음
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