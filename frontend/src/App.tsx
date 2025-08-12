// frontend/src/App.tsx (전체 덮어씌우기)
import React, { useState, useEffect, useRef } from 'react';
import './index.css';
import { useQuery } from '@tanstack/react-query';

import SockJS from 'sockjs-client';
import Stomp from 'stompjs';

// useClickOutside 훅은 기존과 동일
function useClickOutside(ref: React.RefObject<HTMLElement>, callback: () => void) {
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (ref.current && !ref.current.contains(event.target as Node)) {
        callback();
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [ref, callback]);
}

interface Coin {
  id: number | null;
  name: string;
  symbol: string;
  currentPrice: number | null;
  priceChange: string;
  volume: number; // 24H 거래대금
  volume1m: number;  // 1분봉 거래대금
  volume15m: number; // 15분봉 거래대금
  volume1h: number;  // 1시간봉 거래대금
  alarm: string[];
}

interface BuySellRatio {
  buyRatio: number;
  sellRatio: number;
}

export default function App() {
  const [filters, setFilters] = useState({
    all: true,
    large: false,
    mid: false,
    small: false,
  });

  const [selectedOption, setSelectedOption] = useState<number | null>(null);
  const [showOptionContent, setShowOptionContent] = useState(false);
  const optionContentRef = useRef<HTMLDivElement>(null);

  useClickOutside(optionContentRef, () => {
    if (showOptionContent) {
      setShowOptionContent(false);
      setSelectedOption(null);
    }
  });

  const handleOptionClick = (optionNum: number) => {
    if (selectedOption === optionNum && showOptionContent) {
      setShowOptionContent(false);
      setSelectedOption(null);
    } else {
      setSelectedOption(optionNum);
      setShowOptionContent(true);
    }
  };

  const handleFilterChange = (filterName: 'all' | 'large' | 'mid' | 'small') => {
    setFilters(prevFilters => {
      if (filterName === 'all') {
        if (prevFilters.all) {
          return { all: false, large: true, mid: true, small: true };
        } else {
          return { all: true, large: false, mid: false, small: false };
        }
      } else {
        const newIndividualState = { ...prevFilters, [filterName]: !prevFilters[filterName] };
        if (!newIndividualState.large && !newIndividualState.mid && !newIndividualState.small) {
          return { all: true, large: false, mid: false, small: false };
        } else {
          return { ...newIndividualState, all: false };
        }
      }
    });
  };

  // --- WebSocket 연결 및 실시간 데이터 처리 로직 ---
  const [liveCoins, setLiveCoins] = useState<Coin[]>([]);
  const [alarmLogs, setAlarmLogs] = useState<string[]>([]);
  const [favoriteMarkets, setFavoriteMarkets] = useState<Set<string>>(new Set()); // 즐겨찾기 상태 추가
  const [selectedCoinForRope, setSelectedCoinForRope] = useState<string | null>(null); // 줄다리기 선택 코인
  const [buySellRatios, setBuySellRatios] = useState<Map<string, BuySellRatio>>(new Map()); // 매수/매도 비율 데이터

  useEffect(() => {
    const socket = new SockJS('http://localhost:8080/ws');
    const stompClient = Stomp.over(socket);

    stompClient.connect({}, () => {
      console.log('Connected to WebSocket!');

      // 모든 코인 데이터 구독 (메인 테이블용)
      stompClient.subscribe('/topic/market-data', (message) => {
        const receivedCoins: Coin[] = JSON.parse(message.body);
        setLiveCoins(receivedCoins); // 백엔드에서 이미 필터링되어 옵니다.
      });

      // 알람 로그 구독
      stompClient.subscribe('/topic/alarm-log', (message) => {
        const newAlarm = message.body;
        setAlarmLogs(prevLogs => [newAlarm, ...prevLogs].slice(0, 50));
        try {
          new Audio('/sound/beep.mp3').play(); // sound/beep.mp3 파일 필요
        } catch (e) {
          console.error("Failed to play alarm sound:", e);
        }
      });

      // 매수/매도 비율 구독 (줄다리기용)
      stompClient.subscribe('/topic/buy-sell-ratio', (message) => {
        const ratios: { [key: string]: BuySellRatio } = JSON.parse(message.body);
        setBuySellRatios(new Map(Object.entries(ratios)));
      });

      // 초기 즐겨찾기 목록 불러오기
      fetchFavorites();

    }, (error) => {
      console.error("WebSocket connection error:", error);
    });

    return () => {
      if (stompClient.connected) {
        stompClient.disconnect(() => {
          console.log('Disconnected from WebSocket.');
        });
      }
    };
  }, []);

  const fetchFavorites = async () => {
    try {
      const response = await fetch('http://localhost:8080/api/favorites');
      if (response.ok) {
        const favoritesArray: string[] = await response.json();
        setFavoriteMarkets(new Set(favoritesArray));
      }
    } catch (error) {
      console.error("Error fetching favorites:", error);
    }
  };

  const toggleFavorite = async (marketCode: string) => {
    let newFavorites = new Set(favoriteMarkets);
    if (newFavorites.has(marketCode)) {
      // 즐겨찾기 해제
      newFavorites.delete(marketCode);
      try {
        await fetch(`http://localhost:8080/api/favorites/remove?marketCode=${marketCode}`, { method: 'DELETE' });
      } catch (error) {
        console.error("Error removing favorite:", error);
      }
    } else {
      // 즐겨찾기 추가
      newFavorites.add(marketCode);
      try {
        await fetch(`http://localhost:8080/api/favorites/add?marketCode=${marketCode}`, { method: 'POST' });
      } catch (error) {
        console.error("Error adding favorite:", error);
      }
    }
    setFavoriteMarkets(newFavorites);
  };

  const getStarIcon = (marketCode: string) => {
    const isFavorite = favoriteMarkets.has(marketCode);
    return isFavorite ? '★' : '☆'; // 채워진 별 vs 빈 별
  };

  const getStarClass = (marketCode: string) => {
    const isFavorite = favoriteMarkets.has(marketCode);
    return isFavorite ? 'text-yellow-400 animate-pulse-fast' : 'text-gray-400'; // 빛나는 별 효과
  };

  const handleCoinClick = (marketCode: string) => {
    setSelectedCoinForRope(marketCode === selectedCoinForRope ? null : marketCode);
  };


  // 이제 useQuery 훅은 초기 로딩용으로만 사용됩니다.
  const { data: initialCoins, isLoading, isError, error } = useQuery<Coin[], Error>({
    queryKey: ['initialCoins'],
    queryFn: async () => {
      const apiUrl = `http://localhost:8080/api/market-data?all=true&large=false&mid=false&small=false`;
      const response = await fetch(apiUrl);
      if (!response.ok) { throw new Error(`HTTP error! status: ${response.status}`); }
      return response.json();
    },
    staleTime: Infinity,
    enabled: liveCoins.length === 0 // liveCoins가 비어있을 때만 초기 로딩
  });

  const coinsToDisplay = liveCoins.length > 0 ? liveCoins : (initialCoins || []);

  const selectedCoinRatio = selectedCoinForRope ? buySellRatios.get(selectedCoinForRope) : null;
  const buyWidth = selectedCoinRatio ? selectedCoinRatio.buyRatio * 100 : 50;
  const sellWidth = selectedCoinRatio ? selectedCoinRatio.sellRatio * 100 : 50;


  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">

      {/* 상단 옵션 섹션 (기존과 동일) */}
      <div className="bg-white p-4 rounded-lg shadow-md m-4 relative">
        <h2 className="text-xl font-semibold mb-3">새로운 상단 옵션 섹션</h2>
        <div className="flex space-x-4">
          <button onClick={() => handleOptionClick(1)} className="p-2 bg-blue-100 rounded hover:bg-blue-200 active:bg-blue-300">옵션1 내용</button>
          <button onClick={() => handleOptionClick(2)} className="p-2 bg-green-100 rounded hover:bg-green-200 active:bg-green-300">옵션2 내용</button>
          <button onClick={() => handleOptionClick(3)} className="p-2 bg-purple-100 rounded hover:bg-purple-200 active:bg-purple-300">옵션3 내용</button>
        </div>

        {showOptionContent && selectedOption && (
          <div
            ref={optionContentRef}
            className="absolute top-full left-0 mt-2 p-4 bg-white border border-gray-300 rounded-lg shadow-lg z-10 w-full"
          >
            {selectedOption === 1 && (
              <div><h3 className="font-bold text-lg mb-2">옵션 1 컨텐츠</h3><p>여기는 옵션 1에 대한 상세 내용입니다.</p><ul className="list-disc list-inside"><li>항목 1-1</li><li>항목 1-2</li></ul></div>
            )}
            {selectedOption === 2 && (
              <div><h3 className="font-bold text-lg mb-2">옵션 2 컨텐츠</h3><p>여기는 옵션 2에 대한 상세 내용입니다.</p><p>더 많은 정보가 여기에 표시됩니다.</p></div>
            )}
            {selectedOption === 3 && (
              <div><h3 className="font-bold text-lg mb-2">옵션 3 컨텐츠</h3><p>옵션 3은 특별한 기능을 제공합니다.</p><button className="mt-2 px-4 py-2 bg-yellow-500 text-white rounded hover:bg-yellow-600">기능 실행</button></div>
            )}
          </div>
        )}
      </div>

      {/* --- 기존 상단 고정 코인 섹션 삭제 --- */}

      {/* 중앙 메인 영역 (코인목록)과 알람 전용 섹션 (알람 로그) */}
      <div className="flex flex-1 p-4 space-x-4">
        {/* 중앙 메인 영역 (코인목록) */}
        <div className="flex-1 bg-white p-4 rounded-lg shadow-md flex flex-col">
          <h2 className="text-xl font-semibold mb-3">코인목록</h2>

          <div className="space-x-4 mb-4">
            <label>
              <input type="checkbox" checked={filters.all} onChange={() => handleFilterChange('all')} />{' '}
              전체
            </label>
            <label>
              <input type="checkbox" checked={filters.large} onChange={() => handleFilterChange('large')} />{' '}
              대형(5조 이상)
            </label>
            <label>
              <input type="checkbox" checked={filters.mid} onChange={() => handleFilterChange('mid')} />{' '}
              중형(7000억 이상)
            </label>
            <label>
              <input type="checkbox" checked={filters.small} onChange={() => handleFilterChange('small')} />{' '}
              소형(500억 이상)
            </label>
          </div>

          {/* 로딩/에러/코인 그리드/코인 정보 출력 */}
          {(isLoading && coinsToDisplay.length === 0) && <p className="text-center text-gray-600">코인 데이터를 불러오는 중입니다...</p>}
          {isError && coinsToDisplay.length === 0 && <p className="text-center text-red-500">에러: {error?.message || "알 수 없는 오류 발생"}</p>}

          {!isLoading || coinsToDisplay.length > 0 && (
            <>
              <div className="grid grid-cols-5 gap-2 mb-4">
                <div className="bg-gray-100 p-2 text-center rounded">BTC</div>
                <div className="bg-gray-100 p-2 text-center rounded">ETH</div>
                <div className="bg-gray-100 p-2 text-center rounded">XRP</div>
                <div className="bg-gray-100 p-2 text-center rounded">ADA</div>
                <div className="bg-gray-100 p-2 text-center rounded">DOGE</div>
              </div>

              <div className="flex-1 overflow-y-auto border rounded bg-gray-50">
                {coinsToDisplay.length === 0 ? (
                  <p className="text-gray-500 p-4">조건에 맞는 코인이 없습니다.</p>
                ) : (
                  <table className="min-w-full table-auto text-left text-sm">
                    <thead className="bg-gray-200 sticky top-0">
                      <tr>
                        <th className="py-2 px-3 border-b">즐겨찾기</th> {/* 즐겨찾기 열 추가 */}
                        <th className="py-2 px-3 border-b">Symbol</th>
                        <th className="py-2 px-3 border-b">현재가</th>
                        <th className="py-2 px-3 border-b">24H 거래대금</th>
                        <th className="py-2 px-3 border-b">1분봉 거래대금</th>
                        <th className="py-2 px-3 border-b">15분봉 거래대금</th>
                        <th className="py-2 px-3 border-b">1시간봉 거래대금</th>
                        <th className="py-2 px-3 border-b">유지율</th>
                        <th className="py-2 px-3 border-b">전일대비</th>
                        <th className="py-2 px-3 border-b">시총</th>
                      </tr>
                    </thead>
                    <tbody>
                      {coinsToDisplay.map((coin) => (
                        <tr key={coin.name || coin.id} className="border-b hover:bg-gray-100 cursor-pointer" onClick={() => handleCoinClick(coin.name)}>
                          <td className="py-2 px-3"> {/* 즐겨찾기 별표 */}
                            <span className={`text-xl ${getStarClass(coin.name)}`}
                                  onClick={(e) => { e.stopPropagation(); toggleFavorite(coin.name); }}>
                              {getStarIcon(coin.name)}
                            </span>
                          </td>
                          <td className="py-2 px-3">{coin.symbol}</td>
                          <td className="py-2 px-3">{coin.currentPrice != null ? coin.currentPrice.toLocaleString() : 'N/A'}</td>
                          <td className="py-2 px-3">
                            {coin.volume != null ?
                              `${Math.floor(coin.volume / 1_000_000).toLocaleString()} 백만`
                              : 'N/A'}
                          </td>
                          <td className="py-2 px-3">
                            {coin.volume1m != null ?
                              `${Math.floor(coin.volume1m / 1_000_000).toLocaleString()} 백만`
                              : 'N/A'}
                          </td>
                          <td className="py-2 px-3">
                            {coin.volume15m != null ?
                              `${Math.floor(coin.volume15m / 1_000_000).toLocaleString()} 백만`
                              : 'N/A'}
                          </td>
                          <td className="py-2 px-3">
                            {coin.volume1h != null ?
                              `${Math.floor(coin.volume1h / 1_000_000).toLocaleString()} 백만`
                              : 'N/A'}
                          </td>
                          <td className="py-2 px-3">N/A</td>
                          <td className="py-2 px-3">{coin.priceChange}</td>
                          <td className="py-2 px-3">N/A</td>
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
            </>
          )}
        </div>

        {/* 알람 전용 섹션 (알람 로그) */}
        <div className="w-1/3 bg-white p-4 rounded-lg shadow-md flex flex-col">
          <h2 className="text-xl font-semibold mb-3">알람 로그 (조건 발생 시 소리)</h2>
          <div className="flex-1 overflow-y-auto border rounded p-2 space-y-1 bg-gray-50 text-sm">
            {alarmLogs.length === 0 ? (
              <p className="text-gray-500">조건에 맞는 알람이 없습니다.</p>
            ) : (
              alarmLogs.map((log, index) => (
                <div key={index} className="text-red-800 font-bold">
                  {log}
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* --- 줄다리기 힘겨루기 섹션 --- */}
      <div className="bg-white p-4 rounded-lg shadow-md m-4">
        <h2 className="text-xl font-semibold mb-3 text-center">
          {selectedCoinForRope ? `${selectedCoinForRope} ` : ''}줄다리기 힘겨루기
        </h2>
        <div className="flex items-center justify-center space-x-4 mb-2">
          <span className="text-red-600 font-bold">{'<---- [ 매도세 ]'}</span>
          <span className="text-yellow-600 font-bold">{'[ 매수세 ] ---->'}</span>
        </div>
        <div className="w-full h-8 bg-gray-200 rounded-full flex overflow-hidden">
          <div className="bg-red-600 h-full" style={{ width: `${buyWidth}%` }}></div>
          <div className="bg-gray-500 h-full w-1"></div>
          <div className="bg-yellow-500 h-full" style={{ width: `${sellWidth}%` }}></div>
        </div>
      </div>
    </div>
  );
}