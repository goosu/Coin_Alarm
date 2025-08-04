// frontend/src/App.tsx

import React, { useState, useEffect, useRef } from 'react';
import './index.css';
// useQuery 훅은 초기 로딩용으로 남겨두고, 실시간 업데이트는 WebSocket으로 처리
import { useQuery } from '@tanstack/react-query';

// SockJS와 StompJS 임포트
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

// Coin 인터페이스는 기존과 동일 (백엔드 CoinResponseDto와 일치)
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

export default function App() {
  // 필터 상태 (전체, 대형, 중형, 소형)
  const [filters, setFilters] = useState({
    all: true,
    large: false,
    mid: false,
    small: false,
  });

  // 옵션 컨텐츠 관련 상태 및 함수들은 기존과 동일
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
  const [liveCoins, setLiveCoins] = useState<Coin[]>([]); // 실시간 코인 데이터 상태
  const [alarmLogs, setAlarmLogs] = useState<string[]>([]); // 실시간 알람 로그

  useEffect(() => {
    // WebSocket 연결 설정
    const socket = new SockJS('http://localhost:8080/ws'); // 백엔드 WebSocket 엔드포인트
    const stompClient = Stomp.over(socket);

    stompClient.connect({}, () => {
      console.log('Connected to WebSocket!');

      // 1. 코인 데이터 구독
      stompClient.subscribe('/topic/market-data', (message) => {
        const receivedCoins: Coin[] = JSON.parse(message.body);
        // 클라이언트 필터링 로직 (백엔드에서 필터링해서 보내주지 않는다면 클라이언트에서 다시 필터링)
        const filteredClientCoins = receivedCoins.filter(coin => {
          if (filters.all) return true;
          // 이 필터 로직은 백엔드의 MarketDataService와 유사하게 정의해야 함
          const currentMarketCap = coin.volume; // 여기서는 volume(24H거래대금)을 임시 시총으로 사용
          let isIncluded = false;
          if (filters.large && currentMarketCap >= 5_000_000_000_000) isIncluded = true;
          if (filters.mid && currentMarketCap >= 700_000_000_000 && currentMarketCap < 5_000_000_000_000) isIncluded = true;
          if (filters.small && currentMarketCap >= 50_000_000_000 && currentMarketCap < 700_000_000_000) isIncluded = true;
          return isIncluded;
        });
        setLiveCoins(filteredClientCoins); // 상태 업데이트
      });

      // 2. 알람 로그 구독
      stompClient.subscribe('/topic/alarm-log', (message) => {
        const newAlarm = message.body;
        setAlarmLogs(prevLogs => [newAlarm, ...prevLogs].slice(0, 50)); // 최신 50개 유지
        // 알람 소리 재생 로직 추가 (추후 구현)
        new Audio('/sound/beep.mp3').play(); // 예시: sound/beep.mp3 파일 필요
      });

      // (선택 사항) 초기 데이터 요청
      // stompClient.send("/app/request-market-data", {}, JSON.stringify({}));
    });

    // 컴포넌트 언마운트 시 WebSocket 연결 종료
    return () => {
      if (stompClient.connected) {
        stompClient.disconnect(() => {
          console.log('Disconnected from WebSocket.');
        });
      }
    };
  }, [filters]); // filters 상태가 변경될 때마다 재연결하여 필터 다시 적용 (또는 클라이언트 필터링만 진행)

  // --- 기존 useQuery 훅은 제거하거나 초기 로딩용으로만 사용 ---
  // 여기서는 WebSocket이 주요 데이터 소스이므로 useQuery는 더 이상 필요 없습니다.
  // 필요한 경우, 초기 데이터를 로드하고 WebSocket이 연결될 때까지 보여주는 용도로는 사용 가능
  const { data: initialCoins, isLoading, isError, error } = useQuery<Coin[], Error>({
    queryKey: ['initialCoins'],
    queryFn: async () => {
      // 초기 로딩 시 기존 REST API를 호출
      const apiUrl = `http://localhost:8080/api/market-data?all=true&large=false&mid=false&small=false`;
      const response = await fetch(apiUrl);
      if (!response.ok) { throw new Error(`HTTP error! status: ${response.status}`); }
      return response.json();
    },
    staleTime: Infinity, // 초기 로드 후 변경되지 않음
    enabled: liveCoins.length === 0 // liveCoins가 비어있을 때만 초기 로딩
  });

  // 만약 초기 로딩 데이터가 있으면 그걸 먼저 보여줍니다.
  const coinsToDisplay = liveCoins.length > 0 ? liveCoins : (initialCoins || []);

  // filteredAlarms 로직은 알람 로그로 대체되거나, 다른 방식으로 사용됩니다.
  // WebSocket에서 받은 알람 메시지를 직접 사용할 예정
  // const filteredAlarms = coinsToDisplay?.filter(coin => coin.alarm && coin.alarm.length > 0) || [];


  // --- Top 5 코인 상태 (WebSocket으로 받을 예정) ---
  const [top5Coins, setTop5Coins] = useState<Coin[]>([]);
  useEffect(() => {
    // Top 5 코인 구독
    if (Stomp.over(new SockJS('http://localhost:8080/ws')).connected) { // 이미 연결된 Stomp 클라이언트 사용이 안전
      Stomp.over(new SockJS('http://localhost:8080/ws')).subscribe('/topic/top-5-market-data', (message) => {
        const receivedTop5: Coin[] = JSON.parse(message.body);
        setTop5Coins(receivedTop5);
      });
    }
  }, []);

  return (
    <div className="min-h-screen bg-gray-100 flex flex-col">

      {/* 새로운 상단 옵션 섹션은 기존과 동일 (옵션 버튼들은 WebSocket과 직접 연관 없음) */}
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
              <div>
                <h3 className="font-bold text-lg mb-2">옵션 1 컨텐츠</h3>
                <p>여기는 옵션 1에 대한 상세 내용입니다.</p>
                <ul className="list-disc list-inside">
                  <li>항목 1-1</li>
                  <li>항목 1-2</li>
                </ul>
              </div>
            )}
            {selectedOption === 2 && (
              <div>
                <h3 className="font-bold text-lg mb-2">옵션 2 컨텐츠</h3>
                <p>여기는 옵션 2에 대한 상세 내용입니다.</p>
                <p>더 많은 정보가 여기에 표시됩니다.</p>
              </div>
            )}
            {selectedOption === 3 && (
              <div>
                <h3 className="font-bold text-lg mb-2">옵션 3 컨텐츠</h3>
                <p>옵션 3은 특별한 기능을 제공합니다.</p>
                <button className="mt-2 px-4 py-2 bg-yellow-500 text-white rounded hover:bg-yellow-600">기능 실행</button>
              </div>
            )}
          </div>
        )}
      </div>

      {/* --- 상단 고정 코인 섹션 --- */}
      <div className="bg-white p-4 rounded-lg shadow-md m-4">
        <h2 className="text-xl font-semibold mb-3">상단 고정 (1분 거래대금 Top 5)</h2>
        {top5Coins.length === 0 ? (
          <p className="text-gray-500">상단 고정 코인이 없습니다.</p>
        ) : (
          <table className="min-w-full table-auto text-left text-sm">
            <thead>
              <tr className="bg-gray-200">
                <th className="py-2 px-3 border-b">Symbol</th>
                <th className="py-2 px-3 border-b">현재가</th>
                <th className="py-2 px-3 border-b">1분봉 거래대금</th>
              </tr>
            </thead>
            <tbody>
              {top5Coins.map((coin) => (
                <tr key={coin.name || coin.id} className="border-b hover:bg-gray-100">
                  <td className="py-2 px-3">{coin.symbol}</td>
                  <td className="py-2 px-3">{coin.currentPrice != null ? coin.currentPrice.toLocaleString() : 'N/A'}</td>
                  <td className="py-2 px-3">
                    {coin.volume1m != null ?
                      `${Math.floor(coin.volume1m / 1_000_000).toLocaleString()} 백만`
                      : 'N/A'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* 중앙 메인 영역 (코인목록)과 알람 전용 섹션 (알람 로그) */}
      <div className="flex flex-1 p-4 space-x-4">
        {/* 중앙 메인 영역 (코인목록) */}
        <div className="flex-1 bg-white p-4 rounded-lg shadow-md flex flex-col">
          <h2 className="text-xl font-semibold mb-3">코인목록</h2>

          {/* 필터링 체크박스는 기존과 동일 */}
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

          {/* 로딩/에러/코인 그리드/코인 정보 출력은 이제 liveCoins를 사용 */}
          {(isLoading && liveCoins.length === 0) && <p className="text-center text-gray-600">코인 데이터를 불러오는 중입니다...</p>}
          {isError && liveCoins.length === 0 && <p className="text-center text-red-500">에러: {error?.message || "알 수 없는 오류 발생"}</p>}

          {/* liveCoins 상태를 사용하여 코인 목록 테이블 렌더링 */}
          {(!isLoading || liveCoins.length > 0) && (
            <>
              {/* 코인 그리드: BTC, ETH 등 주요 코인 이름 박스 */}
              <div className="grid grid-cols-5 gap-2 mb-4">
                <div className="bg-gray-100 p-2 text-center rounded">BTC</div>
                <div className="bg-gray-100 p-2 text-center rounded">ETH</div>
                <div className="bg-gray-100 p-2 text-center rounded">XRP</div>
                <div className="bg-gray-100 p-2 text-center rounded">ADA</div>
                <div className="bg-gray-100 p-2 text-center rounded">DOGE</div>
              </div>

              {/* 코인 정보 출력 (테이블 형태) */}
              <div className="flex-1 overflow-y-auto border rounded bg-gray-50">
                {coinsToDisplay.length === 0 ? (
                  <p className="text-gray-500 p-4">조건에 맞는 코인이 없습니다.</p>
                ) : (
                  <table className="min-w-full table-auto text-left text-sm">
                    <thead className="bg-gray-200 sticky top-0">
                      <tr>
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
                        <tr key={coin.name || coin.id} className="border-b hover:bg-gray-100">
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

        {/* 알람 전용 섹션 (알람 로그): 이제 liveAlarmLogs 사용 */}
        <div className="w-1/3 bg-white p-4 rounded-lg shadow-md flex flex-col">
          <h2 className="text-xl font-semibold mb-3">알람 로그 (조건 발생 시 소리)</h2>
          <div className="flex-1 overflow-y-auto border rounded p-2 space-y-1 bg-gray-50 text-sm">
            {alarmLogs.length === 0 ? (
              <p className="text-gray-500">조건에 맞는 알람이 없습니다.</p>
            ) : (
              alarmLogs.map((log, index) => (
                <div key={index} className="text-red-600"> {/* 로그 자체가 문자열이므로 index를 key로 사용 */}
                  {log}
                </div>
              ))
            )}
          </div>
        </div>
      </div>

      {/* 하단 섹션은 기존과 동일합니다. */}
      <div className="bg-white p-4 rounded-lg shadow-md m-4">
        <h2 className="text-xl font-semibold mb-3 text-center">줄다리기 힘겨루기</h2>
        <div className="flex items-center justify-center space-x-4 mb-2">
          <span className="text-red-600 font-bold">{'<---- [ 팀 레드 ]'}</span>
          <span className="text-yellow-600 font-bold">{'[ 팀 옐로우 ] ---->'}</span>
        </div>
        <div className="w-full h-8 bg-gray-200 rounded-full flex overflow-hidden">
          <div className="bg-red-600 h-full" style={{ width: '40%' }}></div>
          <div className="bg-gray-500 h-full w-1"></div>
          <div className="bg-yellow-500 h-full" style={{ width: '59%' }}></div>
        </div>
      </div>
    </div>
  );
}