// frontend/src/App.tsx
// 이 파일을 열어 기존 내용을 모두 지우고 아래 코드를 붙여넣으세요.

import React, { useState } from 'react';
import './index.css'; // TailwindCSS

// React Query의 useQuery 훅 임포트
import { useQuery } from '@tanstack/react-query';

// 백엔드에서 받아올 코인 데이터의 타입 정의 (CoinResponseDto와 일치)
// 백엔드의 coinalarm.Coin_Alarm.coin.CoinResponseDto 클래스의 필드와 정확히 일치해야 합니다.
interface Coin {
  id: number;
  name: string;
  marketCap: number;
  priceChange: string;
  volume: number;
  alarm: string[]; // 백엔드 DTO의 필드명 'alarm'과 일치
}

export default function App() {
  // 필터 상태 (대형, 중형, 소형 코인 선택 여부)
  const [filters, setFilters] = useState({
    large: true,
    mid: true,
    small: true,
  });

  // useQuery 훅을 사용하여 백엔드 API에서 코인 데이터를 가져옵니다.
  // useQuery는 데이터 fetching, 캐싱, 로딩/에러 상태 관리 등을 자동으로 처리해줍니다.
  // queryKey: 쿼리를 고유하게 식별하는 키입니다. 배열 형태로 사용되며, 배열 안의 값이 변경되면 쿼리를 다시 실행합니다.
  // queryFn: 데이터를 실제로 가져오는 비동기 함수입니다.
  // data: 쿼리 결과 데이터 (성공 시)
  // isLoading: 데이터 로딩 중인지 여부 (boolean)
  // isError: 데이터 로딩 중 에러가 발생했는지 여부 (boolean)
  // error: 발생한 에러 객체 (Error 타입)
  const { data: coins, isLoading, isError, error } = useQuery<Coin[], Error>({
    queryKey: ['coins', filters], // 'coins'는 쿼리 이름, filters 객체가 변경되면 이 쿼리가 다시 실행됩니다.
    queryFn: async () => {
      // 백엔드 API 엔드포인트 URL 구성
      // 백엔드가 http://localhost:8080에서 실행된다고 가정합니다.
      // 쿼리 파라미터로 현재 필터 상태를 전달합니다.
      const apiUrl = `http://localhost:8080/api/coins?large=${filters.large}&mid=${filters.mid}&small=${filters.small}`;
      const response = await fetch(apiUrl); // fetch API를 사용하여 백엔드에 GET 요청을 보냅니다.

      // HTTP 응답 상태 코드가 200번대(성공)가 아니면 에러 발생
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return response.json(); // 응답 본문을 JSON 형태로 파싱하여 반환합니다.
    },
    // staleTime: 쿼리 데이터가 'stale' 상태로 간주되기 전까지의 시간 (ms).
    // 이 시간 동안은 캐시된 데이터를 즉시 반환하고 백그라운드에서 업데이트를 시도하지 않습니다.
    // 여기서는 5분(1000ms * 60초 * 5분)으로 설정하여 불필요한 API 호출을 줄입니다.
    staleTime: 1000 * 60 * 5,
    // refetchOnWindowFocus: 윈도우가 다시 포커스될 때 쿼리를 다시 실행할지 여부.
    // false로 설정하여 불필요한 API 호출을 줄입니다. (사용자가 직접 새로고침하지 않는 한)
    refetchOnWindowFocus: false,
  });

  // 알람이 있는 코인만 필터링 (useQuery의 data를 사용)
  // 'coins' 데이터가 아직 로딩 중이거나 에러로 인해 undefined일 수 있으므로 옵셔널 체이닝 (?)을 사용합니다.
  // 데이터가 없으면 빈 배열([])을 반환하여 렌더링 오류를 방지합니다.
  const filteredAlarms = coins?.filter(
    (coin) => coin.alarm && coin.alarm.length > 0 // 'alarm' 필드가 존재하고 길이가 0보다 큰 경우
  ) || [];

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
            {/* coins가 undefined일 수 있으므로 옵셔널 체이닝 (?)을 사용합니다. */}
            {/* coins 배열의 길이가 0이면 "조건에 맞는 코인이 없습니다." 메시지를 표시합니다. */}
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
                  {/* coins 배열을 순회하며 각 코인 정보를 테이블 행으로 렌더링합니다. */}
                  {/* key 속성은 React에서 리스트를 렌더링할 때 각 항목을 고유하게 식별하는 데 사용됩니다. */}
                  {/* 백엔드에서 제공하는 coin.id를 key로 사용합니다. */}
                  {coins?.map((coin) => (
                    <tr key={coin.id} className="border-b text-sm">
                      <td className="py-2">{coin.name}</td>
                      <td>{coin.priceChange}</td>
                      <td>{coin.volume.toLocaleString()}</td> {/* 거래대금을 현지화된 문자열로 포맷합니다. */}
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>

          {/* 3번 알람 정보창 */}
          <div className="bg-white p-4 rounded-lg shadow-md">
            <h2 className="text-xl font-semibold mb-3">③ 알람 정보</h2>
            {/* filteredAlarms 배열의 길이가 0이면 "조건에 맞는 알람이 없습니다." 메시지를 표시합니다. */}
            {filteredAlarms.length === 0 ? (
              <p className="text-gray-500">조건에 맞는 알람이 없습니다.</p>
            ) : (
              // 알람이 있는 코인들을 순회하며 각 코인의 알람 정보를 표시합니다.
              filteredAlarms.map((coin) => (
                <div key={coin.id} className="mb-4">
                  <div className="font-medium">{coin.name}</div>
                  <ul className="list-disc list-inside text-red-600 text-sm">
                    {/* 각 코인의 알람 목록을 순회하며 리스트 항목으로 렌더링합니다. */}
                    {/* 알람 내용이 고유하지 않을 수 있으므로 인덱스(i)를 key로 사용합니다. */}
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
