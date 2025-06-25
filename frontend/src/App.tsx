// src/App.tsx
import React, { useState } from 'react';
import './index.css'; // TailwindCSS

// React Query의 useQuery 훅 임포트
import { useQuery } from '@tanstack/react-query';

// 백엔드에서 받아올 코인 데이터의 타입 정의 (CoinResponseDto와 일치)
interface Coin {
  id: number;
  name: string;
  marketCap: number;
  priceChange: string;
  volume: number;
  alarm: string[]; // 백엔드 DTO의 필드명과 일치
}

export default function App() {
  const [filters, setFilters] = useState({
    large: true,
    mid: true,
    small: true,
  });

  // useQuery 훅을 사용하여 백엔드 API에서 코인 데이터를 가져옵니다.
  // queryKey: 쿼리를 고유하게 식별하는 키입니다. 의존성 배열처럼 사용되어 필터가 변경될 때마다 쿼리를 다시 실행합니다.
  // queryFn: 데이터를 실제로 가져오는 비동기 함수입니다.
  // data: 쿼리 결과 데이터 (성공 시)
  // isLoading: 데이터 로딩 중인지 여부 (boolean)
  // isError: 데이터 로딩 중 에러가 발생했는지 여부 (boolean)
  // error: 발생한 에러 객체
  const { data: coins, isLoading, isError, error } = useQuery<Coin[], Error>({
    queryKey: ['coins', filters], // filters 객체가 변경되면 이 쿼리가 다시 실행됩니다.
    queryFn: async () => {
      // 백엔드 API 엔드포인트 URL 구성
      const apiUrl = `http://localhost:8080/api/coins?large=${filters.large}&mid=${filters.mid}&small=${filters.small}`;
      const response = await fetch(apiUrl);
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      return response.json();
    },
    // staleTime: 쿼리 데이터가 'stale' 상태로 간주되기 전까지의 시간 (ms).
    // 이 시간 동안은 캐시된 데이터를 즉시 반환하고 백그라운드에서 업데이트를 시도하지 않습니다.
    // 여기서는 5분으로 설정하여 자주 데이터가 변경되지 않는다고 가정합니다.
    staleTime: 1000 * 60 * 5, // 5분
    // refetchOnWindowFocus: 윈도우가 다시 포커스될 때 쿼리를 다시 실행할지 여부.
    // false로 설정하여 불필요한 API 호출을 줄입니다.
    refetchOnWindowFocus: false,
  });

  // 알람이 있는 코인만 필터링 (useQuery의 data를 사용)
  // data가 undefined일 수 있으므로 옵셔널 체이닝 (?) 사용
  const filteredAlarms = coins?.filter(
    (coin) => coin.alarm && coin.alarm.length > 0
  ) || []; // 데이터가 없으면 빈 배열 반환

  return (
    <div className="min-h-screen bg-gray-100 p-6 space-y-6">
      {/* 1번 알람 맞춤설정 */}
      <div className="bg-white p-4 rounded-lg shadow-md">
        <h2 className="text-xl font-semibold mb-3">① 알람 맞춤 설정</h2>
        <div className="space-x-4">
          <label>
            <input
              type="checkbox"
              checked={filters.large}
              onChange={() => setFilters({ ...filters, large: !filters.large })}
            />{' '}
            대형(5조 이상)
          </label>
          <label>
            <input
              type="checkbox"
              checked={filters.mid}
              onChange={() => setFilters({ ...filters, mid: !filters.mid })}
            />{' '}
            중형(7000억 이상)
          </label>
          <label>
            <input
              type="checkbox"
              checked={filters.small}
              onChange={() => setFilters({ ...filters, small: !filters.small })}
            />{' '}
            소형(500억 이상)
          </label>
        </div>
      </div>

      {/* 로딩 중이거나 에러 발생 시 메시지 표시 */}
      {isLoading && <p className="text-center text-gray-600">코인 데이터를 불러오는 중입니다...</p>}
      {isError && <p className="text-center text-red-500">에러: {error?.message || "알 수 없는 오류 발생"}</p>}

      {/* 데이터가 로딩되었고 에러가 없을 때만 코인 정보와 알람 정보 표시 */}
      {!isLoading && !isError && (
        <>
          {/* 2번 코인 정보창 */}
          <div className="bg-white p-4 rounded-lg shadow-md">
            <h2 className="text-xl font-semibold mb-3">② 코인 정보</h2>
            {/* coins가 undefined일 수 있으므로 옵셔널 체이닝 (?) 사용 */}
            {coins?.length === 0 ? (
              <p className="text-gray-500">조건에 맞는 코인이 없습니다.</p>
            ) : (
              <table className="w-full table-auto text-left">
                <thead>
                  <tr className="text-sm text-gray-600 border-b">
                    <th className="py-2">이름</th>
                    <th>변동률</th>
                    <th>거래대금 (원)</th>
                  </tr>
                </thead>
                <tbody>
                  {/* coins가 undefined일 수 있으므로 옵셔널 체이닝 (?) 사용 */}
                  {coins?.map((coin) => (
                    <tr key={coin.id} className="border-b text-sm">
                      <td className="py-2">{coin.name}</td>
                      <td>{coin.priceChange}</td>
                      <td>{coin.volume.toLocaleString()}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {/* 3번 알람 정보창 */}
          <div className="bg-white p-4 rounded-lg shadow-md">
            <h2 className="text-xl font-semibold mb-3">③ 알람 정보</h2>
            {filteredAlarms.length === 0 ? (
              <p className="text-gray-500">조건에 맞는 알람이 없습니다.</p>
            ) : (
              filteredAlarms.map((coin) => (
                <div key={coin.id} className="mb-4">
                  <div className="font-medium">{coin.name}</div>
                  <ul className="list-disc list-inside text-red-600 text-sm">
                    {coin.alarm.map((alarm, i) => (
                      <li key={i}>⚠ {alarm}</li>
                    ))}
                  </ul>
                </div>
              ))
            )}
          </div>
        </>
      )}
    </div>
  );
}
