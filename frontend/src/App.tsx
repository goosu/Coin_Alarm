// frontend/src/App.tsx
import React, { useState } from 'react';
import './index.css'; // TailwindCSS 임포트

// React Query의 useQuery 훅 임포트
import { useQuery } from '@tanstack/react-query';

// 백엔드에서 받아올 코인 데이터의 타입 정의 (백엔드의 CoinResponseDto와 일치해야 합니다.)
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
  const { data: coins, isLoading, isError, error } = useQuery<Coin[], Error>({
    queryKey: ['coins', filters], // 'coins'는 쿼리 이름, filters 객체가 변경되면 이 쿼리가 다시 실행됩니다.
    queryFn: async () => {
      // 백엔드 API 엔드포인트 URL 구성
      // 백엔드가 http://localhost:8080에서 실행된다고 가정합니다.
      const apiUrl = `http://localhost:8080/api/coins?large=${filters.large}&mid=${filters.mid}&small=${filters.small}`;
      const response = await fetch(apiUrl); // fetch API를 사용하여 백엔드에 GET 요청을 보냅니다.

      // HTTP 응답 상태 코드가 200번대(성공)가 아니면 에러 발생
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }

      return response.json(); // 응답 본문을 JSON 형태로 파싱하여 반환합니다.
    },
    // staleTime: 쿼리 데이터가 'stale' 상태로 간주되기 전까지의 시간 (ms).
    // 여기서는 5분(1000ms * 60초 * 5분)으로 설정하여 불필요한 API 호출을 줄입니다.
    staleTime: 1000 * 60 * 5,
    // refetchOnWindowFocus: 윈도우가 다시 포커스될 때 쿼리를 다시 실행할지 여부.
    refetchOnWindowFocus: false,
  });

  // 알람이 있는 코인만 필터링
  const filteredAlarms = coins?.filter(
    (coin) => coin.alarm && coin.alarm.length > 0
  ) || [];

  return (
    // min-h-screen: 최소 화면 높이, bg-gray-100: 배경색, flex flex-col: 세로 방향 플렉스 컨테이너
    <div className="min-h-screen bg-gray-100 flex flex-col">

      {/* 새로운 상단 옵션 섹션 */}
      <div className="bg-white p-4 rounded-lg shadow-md m-4">
        <h2 className="text-xl font-semibold mb-3">새로운 상단 옵션 섹션</h2>
        {/* 옵션 1,2,3을 수평으로 배치 */}
        <div className="flex space-x-4">
          <div className="p-2 bg-blue-100 rounded">옵션1 내용</div>
          <div className="p-2 bg-green-100 rounded">옵션2 내용</div>
          <div className="p-2 bg-purple-100 rounded">옵션3 내용</div>
        </div>
      </div>

      {/* 중앙 메인 영역 (코인목록)과 알람 전용 섹션 (알람 로그)을 포함하는 플렉스 컨테이너 */}
      {/* flex-1: 남은 공간 모두 차지, p-4: 패딩, space-x-4: 자식 요소 간 가로 간격 */}
      <div className="flex flex-1 p-4 space-x-4">

        {/* 중앙 메인 영역 (코인목록) */}
        {/* flex-1: 남은 공간 모두 차지, bg-white: 배경색, p-4: 패딩, rounded-lg: 둥근 모서리, shadow-md: 그림자, flex flex-col: 세로 방향 플렉스 컨테이너 */}
        <div className="flex-1 bg-white p-4 rounded-lg shadow-md flex flex-col">
          <h2 className="text-xl font-semibold mb-3">코인목록</h2>

          {/* 필터링 체크박스 (기존 ① 알람 맞춤설정 내용) */}
          <div className="space-x-4 mb-4">
            <label>
              <input type="checkbox" checked={filters.large} onChange={() => setFilters({ ...filters, large: !filters.large })} />{' '}
              대형(5조 이상)
            </label>
            <label>
              <input type="checkbox" checked={filters.mid} onChange={() => setFilters({ ...filters, mid: !filters.mid })} />{' '}
              중형(7000억 이상)
            </label>
            <label>
              <input type="checkbox" checked={filters.small} onChange={() => setFilters({ ...filters, small: !filters.small })} />{' '}
              소형(500억 이상)
            </label>
          </div>

          {/* 로딩 중이거나 에러 발생 시 메시지 표시 */}
          {isLoading && <p className="text-center text-gray-600">코인 데이터를 불러오는 중입니다...</p>}
          {isError && <p className="text-center text-red-500">에러: {error?.message || "알 수 없는 오류 발생"}</p>}

          {/* 로딩이 끝나고 에러가 없을 때만 코인 정보 표시 */}
          {!isLoading && !isError && (
            <>
              {/* 코인 그리드 */}
              <div className="grid grid-cols-5 gap-2 mb-4">
                <div className="bg-gray-100 p-2 text-center rounded">BTC</div>
                <div className="bg-gray-100 p-2 text-center rounded">ETH</div>
                <div className="bg-gray-100 p-2 text-center rounded">XRP</div>
                <div className="bg-gray-100 p-2 text-center rounded">ADA</div>
                <div className="bg-gray-100 p-2 text-center rounded">DOGE</div>
              </div>

              {/* 코인 정보 출력 (리스트박스 형태) */}
              {/* flex-1: 남은 공간 모두 차지, overflow-y-auto: 세로 스크롤, border: 테두리, rounded: 둥근 모서리, p-2: 패딩, space-y-1: 자식 요소 간 세로 간격 */}
              <div className="flex-1 overflow-y-auto border rounded p-2 space-y-1">
                {coins?.length === 0 ? (
                  <p className="text-gray-500">조건에 맞는 코인이 없습니다.</p>
                ) : (
                  coins?.map((coin) => (
                    <div key={coin.id} className="p-2 bg-yellow-50 rounded shadow-sm text-sm">
                      <span className="font-medium">{coin.name}:</span> 변동률 {coin.priceChange}, 거래대금 {coin.volume.toLocaleString()}원
                    </div>
                  ))
                )}
              </div>
            </>
          )}
        </div>

        {/* 알람 전용 섹션 (알람 로그) */}
        {/* w-1/3: 너비 1/3, bg-white: 배경색, p-4: 패딩, rounded-lg: 둥근 모서리, shadow-md: 그림자, flex flex-col: 세로 방향 플렉스 컨테이너 */}
        <div className="w-1/3 bg-white p-4 rounded-lg shadow-md flex flex-col">
          <h2 className="text-xl font-semibold mb-3">알람 로그 (조건 발생 시 소리)</h2>
          {/* 알람 로그 출력 영역 */}
          {/* flex-1: 남은 공간 모두 차지, overflow-y-auto: 세로 스크롤, border: 테두리, rounded: 둥근 모서리, p-2: 패딩, space-y-1: 자식 요소 간 세로 간격, bg-gray-50: 연한 회색 배경, text-sm: 작은 글씨 */}
          <div className="flex-1 overflow-y-auto border rounded p-2 space-y-1 bg-gray-50 text-sm">
            {filteredAlarms.length === 0 ? (
              <p className="text-gray-500">조건에 맞는 알람이 없습니다.</p>
            ) : (
              filteredAlarms.map((coin) => (
                coin.alarm.map((alarmMsg, i) => (
                  <div key={`${coin.id}-${i}`} className="text-red-600">
                    {/* 타임스탬프 (현재 시간 사용, 실제 알람 발생 시간은 백엔드에서 받아와야 함) */}
                    <span className="text-gray-500">[{new Date().toLocaleTimeString()}]</span>{' '}
                    <span className="font-medium">{coin.name}:</span> {alarmMsg} (삐빅!) {/* 소리 암시 */}
                  </div>
                ))
              ))
            )}
          </div>
        </div>
      </div>

      {/* 새로운 하단 섹션 (줄다리기 힘겨루기) */}
      <div className="bg-white p-4 rounded-lg shadow-md m-4">
        <h2 className="text-xl font-semibold mb-3 text-center">줄다리기 힘겨루기</h2>
        <div className="flex items-center justify-center space-x-4 mb-2">
          <span className="text-red-600 font-bold">{'<---- [ 팀 레드 ]'}</span>
          <span className="text-yellow-600 font-bold">{'[ 팀 옐로우 ] ---->'}</span>
        </div>
        {/* 힘겨루기 바 */}
        {/* w-full: 너비 100%, h-8: 높이, bg-gray-200: 배경색, rounded-full: 둥근 모양, flex overflow-hidden: 내부 요소 플렉스 및 오버플로우 숨김 */}
        <div className="w-full h-8 bg-gray-200 rounded-full flex overflow-hidden">
          {/* 빨강팀 바 (예시: 40% 승리) */}
          <div className="bg-red-600 h-full" style={{ width: '40%' }}></div>
          {/* 중앙 구분선 (얇은 회색) */}
          <div className="bg-gray-500 h-full w-1"></div>
          {/* 노랑팀 바 (예시: 60% 승리) */}
          {/* 남은 너비 계산: 100% - 빨강팀 너비 - 구분선 너비 = 100 - 40 - (1/전체너비) */}
          {/* 여기서는 간단하게 59%로 설정하여 합이 100%가 되도록 합니다. */}
          <div className="bg-yellow-500 h-full" style={{ width: '59%' }}></div>
        </div>
      </div>
    </div>
  );
}