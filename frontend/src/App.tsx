// frontend/src/App.tsx
import React, { useState, useEffect, useRef } from 'react'; // React 훅 임포트: 상태 관리, 부수 효과, DOM 참조
import './index.css'; // TailwindCSS를 포함한 전역 스타일시트 임포트

// React Query의 useQuery 훅 임포트: 서버 데이터 fetching 및 관리
import { useQuery } from '@tanstack/react-query';

/**
 * useClickOutside 커스텀 훅:
 * 특정 DOM 요소(ref로 참조) 외부를 클릭했을 때 지정된 콜백 함수를 실행합니다.
 * 팝오버, 드롭다운 메뉴 등을 외부 클릭 시 닫는 데 유용합니다.
 * @param ref - 외부 클릭을 감지할 대상 DOM 요소의 React Ref 객체
 * @param callback - 외부 클릭 시 실행될 함수
 */
function useClickOutside(ref: React.RefObject<HTMLElement>, callback: () => void) {
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      // ref.current가 존재하고, 클릭된 요소가 ref.current 내부에 포함되지 않을 때
      if (ref.current && !ref.current.contains(event.target as Node)) {
        callback(); // 콜백 함수 실행
      }
    };
    // 컴포넌트 마운트 시 document에 마우스 클릭 이벤트 리스너 등록
    document.addEventListener('mousedown', handleClickOutside);
    // 컴포넌트 언마운트 시 이벤트 리스너 제거 (클린업 함수)
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [ref, callback]); // ref와 callback이 변경될 때만 이펙트 재실행
}

/**
 * Coin 인터페이스:
 * 백엔드에서 받아올 코인 데이터의 타입을 정의합니다.
 * 백엔드 CoinResponseDto의 필드와 일치해야 합니다.
 */
interface Coin {
  id: number | null;
  name: string;
  simbol: string;
  currentPrice: number | null;
  //marketCap: number;
  priceChange: string;
  volume: number; // 24H 거래대금 (총 누적 거래대금)
  volume1m: number;  // 1분봉 거래대금
  volume15m: number; // 15분봉 거래대금
  volume1h: number;  // 1시간봉 거래대금

  alarm: string[]; // 백엔드 DTO의 필드명 'alarm'과 일치
}

/**
 * App 컴포넌트:
 * 애플리케이션의 메인 UI를 렌더링하고, 코인 데이터 필터링 및 옵션 컨텐츠를 관리합니다.
 */
export default function App() {
  // 코인 시가총액 필터 상태: 대형, 중형, 소형 코인 선택 여부
  const [filters, setFilters] = useState({
    all: true,
    large: false,
    mid: false,
    small: false,
  });

  // 선택된 옵션 번호 (1, 2, 3 또는 null): 어떤 옵션의 컨텐츠 박스를 보여줄지 결정
  const [selectedOption, setSelectedOption] = useState<number | null>(null);
  // 옵션 컨텐츠 박스를 화면에 보여줄지 여부를 제어하는 상태
  const [showOptionContent, setShowOptionContent] = useState(false);
  // 옵션 컨텐츠 박스 DOM 요소를 참조하고, 외부 클릭 감지에 사용될 ref
  const optionContentRef = useRef<HTMLDivElement>(null);

  // useClickOutside 훅을 사용하여 optionContentRef 외부 클릭 시 박스를 닫도록 설정
  useClickOutside(optionContentRef, () => {
    if (showOptionContent) { // 컨텐츠 박스가 열려있을 때만 닫기 로직 실행
      setShowOptionContent(false); // 박스 숨기기
      setSelectedOption(null); // 선택된 옵션 초기화
    }
  });

  /**
   * handleOptionClick 함수:
   * 상단 옵션 버튼 클릭 시 호출되어 selectedOption과 showOptionContent 상태를 업데이트합니다.
   * @param optionNum - 클릭된 옵션의 번호 (1, 2, 3)
   */
  const handleOptionClick = (optionNum: number) => {
    // 같은 옵션을 다시 클릭하고 컨텐츠 박스가 열려있다면 닫기
    if (selectedOption === optionNum && showOptionContent) {
      setShowOptionContent(false);
      setSelectedOption(null);
    } else {
      // 다른 옵션을 클릭했거나 닫혀있다면 열기
      setSelectedOption(optionNum);
      setShowOptionContent(true);
    }
  };


  // 6. 새로운 필터 변경 핸들러 함수 추가
  /**
   * handleFilterChange 함수:
   * 코인 필터 체크박스(전체, 대형, 중형, 소형)의 상태를 관리합니다.
   * '전체'와 나머지 필터 간의 상호 배타적인 선택 로직을 처리합니다.
   * @param filterName - 변경된 필터의 이름 ('all' | 'large' | 'mid' | 'small')
   */
  const handleFilterChange = (filterName: 'all' | 'large' | 'mid' | 'small') => {
    setFilters(prevFilters => {
      if (filterName === 'all') {
        // '전체' 체크박스가 클릭된 경우
        if (prevFilters.all) {
          // '전체'가 이미 선택되어 있었다면, 이제 '전체'를 해제하고 모든 개별 필터를 선택
          return { all: false, large: true, mid: true, small: true };
        } else {
          // '전체'가 선택되어 있지 않았다면, '전체'를 선택하고 모든 개별 필터를 해제
          return { all: true, large: false, mid: false, small: false };
        }
      } else {
        // '대형', '중형', '소형' 중 하나가 클릭된 경우
        const newIndividualState = { ...prevFilters, [filterName]: !prevFilters[filterName] };

        // 모든 개별 필터가 해제되면 자동으로 '전체'를 선택
        if (!newIndividualState.large && !newIndividualState.mid && !newIndividualState.small) {
          return { all: true, large: false, mid: false, small: false };
        } else {
          // 하나라도 개별 필터가 선택되면 '전체'는 해제
          return { ...newIndividualState, all: false };
        }
      }
    });
  };


  /**
   * useQuery 훅:
   * 백엔드 API에서 코인 데이터를 비동기적으로 가져옵니다.
   * 로딩 상태 (isLoading), 에러 상태 (isError), 데이터 (coins)를 자동으로 관리합니다.
   */
  const { data: coins, isLoading, isError, error } = useQuery<Coin[], Error>({
    queryKey: ['coins', filters], // filters 객체가 변경되면 이 쿼리가 다시 실행됩니다.
    queryFn: async () => {
      // 백엔드 API 엔드포인트 URL 구성 (백엔드가 http://localhost:8080에서 실행 가정)
//       const apiUrl = `http://localhost:8080/api/coins?large=${filters.large}&mid=${filters.mid}&small=${filters.small}`;
      const apiUrl = `http://localhost:8080/api/market-data?all=${filters.all}&large=${filters.large}&mid=${filters.mid}&small=${filters.small}`;
      const response = await fetch(apiUrl); // fetch API로 GET 요청

      // HTTP 응답 상태 코드가 200번대(성공)가 아니면 에러 발생
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      return response.json(); // 응답 본문을 JSON 형태로 파싱하여 반환
    },
    staleTime: 1000 * 60 * 5, // 쿼리 데이터가 5분 동안 'stale' 상태로 간주되지 않음
    refetchOnWindowFocus: false, // 윈도우 포커스 시 자동으로 데이터 재요청하지 않음
  });

  /**
   * filteredAlarms 변수:
   * useQuery로 받아온 코인 데이터 중 알람이 설정된 코인들만 필터링합니다.
   * coins가 undefined일 수 있으므로 옵셔널 체이닝 (?) 사용 및 기본값 [] 설정.
   */
  const filteredAlarms = coins?.filter(
    (coin) => coin.alarm && coin.alarm.length > 0
  ) || [];

  return (
    // 메인 컨테이너: 최소 화면 높이, 회색 배경, 세로 방향 플렉스 컨테이너
    <div className="min-h-screen bg-gray-100 flex flex-col">

      {/* 새로운 상단 옵션 섹션 */}
      {/* bg-white: 흰색 배경, p-4: 패딩, rounded-lg: 둥근 모서리, shadow-md: 그림자, m-4: 마진 */}
      {/* relative: 자식 요소인 옵션 컨텐츠 박스의 absolute 포지셔닝 기준점이 됩니다. */}
      <div className="bg-white p-4 rounded-lg shadow-md m-4 relative">
        <h2 className="text-xl font-semibold mb-3">새로운 상단 옵션 섹션</h2>
        <div className="flex space-x-4">
          {/* 옵션 버튼들: 클릭 시 handleOptionClick 호출, 호버/클릭 시 스타일 변경 */}
          <button onClick={() => handleOptionClick(1)} className="p-2 bg-blue-100 rounded hover:bg-blue-200 active:bg-blue-300">옵션1 내용</button>
          <button onClick={() => handleOptionClick(2)} className="p-2 bg-green-100 rounded hover:bg-green-200 active:bg-green-300">옵션2 내용</button>
          <button onClick={() => handleOptionClick(3)} className="p-2 bg-purple-100 rounded hover:bg-purple-200 active:bg-purple-300">옵션3 내용</button>
        </div>

        {/* 옵션 컨텐츠 박스: showOptionContent가 true이고 selectedOption이 유효할 때만 렌더링 */}
        {showOptionContent && selectedOption && (
          <div
            ref={optionContentRef} // useClickOutside 훅에 연결될 ref
            // absolute: 부모 요소(relative)를 기준으로 위치 지정
            // top-full: 부모 요소의 높이만큼 아래로, left-0: 왼쪽에서 0px, mt-2: 위쪽 마진
            // p-4: 패딩, bg-white: 배경색, border: 테두리, shadow-lg: 큰 그림자, z-10: 다른 요소 위에 표시
            // w-full: 부모 요소의 너비 전체를 차지합니다.
            className="absolute top-full left-0 mt-2 p-4 bg-white border border-gray-300 rounded-lg shadow-lg z-10 w-full"
          >
            {/* selectedOption 값에 따라 다른 컨텐츠를 조건부 렌더링 */}
            {selectedOption === 1 && (
              <div>
                <h3 className="font-bold text-lg mb-2">옵션 1 컨텐츠</h3>
                <p>전체 현물 선물</p> //해당칸 버튼식에 따라 거래소가 보이게
                //미체결의 경우에는 선물만 표시
                <ul className="list-disc-1 list-inside">
                  //거래소들은 체크박스 두어서 체크로 해당 거래소에서 받는 api만 보이게
                  <li>거래소 선택 </li>
                  <li>항목 1-1 현물거래소들 선택하게 </li>
                  <li>항목 1-2 선물거래소들 선택가능하게</li>
                </ul>
              </div>
            )}
            {selectedOption === 2 && (
              <div>
                <h3 className="font-bold text-lg mb-2">옵션 2 컨텐츠</h3>
                <p>여기는 옵션 2에 대한 상세 내용입니다.</p>
                <ul className="list-disc-2 list-inside">
                  <li>알람로그에 대한 옵션</li>
                  <li>미체결,뉴스,지갑이동,해당심볼관련만?,소리,트위터카톡알람(등록),휴대폰알람(등록)</li>
                </ul>
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

      {/* 중앙 메인 영역 (코인목록)과 알람 전용 섹션 (알람 로그)을 포함하는 플렉스 컨테이너 */}
      {/* flex-1: 남은 공간 모두 차지, p-4: 패딩, space-x-4: 자식 요소 간 가로 간격 */}
      <div className="flex flex-1 p-4 space-x-4">

        {/* 중앙 메인 영역 (코인목록) */}
        {/* flex-1: 남은 공간 모두 차지, bg-white: 배경색, p-4: 패딩, rounded-lg: 둥근 모서리, shadow-md: 그림자, flex flex-col: 세로 방향 플렉스 컨테이너 */}
        <div className="flex-1 bg-white p-4 rounded-lg shadow-md flex flex-col">
          <h2 className="text-xl font-semibold mb-3">코인목록</h2>

          {/* 필터링 체크박스: 대형, 중형, 소형 코인 선택 */}
          <div className="space-x-4 mb-4">
            <label>
              <input type="checkbox" checked={filters.all} onChange={() => setFilters({ ...filters, all: !filters.all })} />{' '}
              전체
            </label>
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

          {/* 데이터 로딩/에러 상태 메시지 */}
          {isLoading && <p className="text-center text-gray-600">코인 데이터를 불러오는 중입니다...</p>}
          {isError && <p className="text-center text-red-500">에러: {error?.message || "알 수 없는 오류 발생"}</p>}

          {/* 로딩이 끝나고 에러가 없을 때만 코인 정보 표시 */}
          {!isLoading && !isError && (
            <>
              {/* 코인 그리드: BTC, ETH 등 주요 코인 이름 박스 */}
              <div className="grid grid-cols-5 gap-2 mb-4">
                <div className="bg-gray-100 p-2 text-center rounded">BTC</div>
                <div className="bg-gray-100 p-2 text-center rounded">ETH</div>
                <div className="bg-gray-100 p-2 text-center rounded">XRP</div>
                <div className="bg-gray-100 p-2 text-center rounded">ADA</div>
                <div className="bg-gray-100 p-2 text-center rounded">DOGE</div>
              </div>

              {/* --- 코인 정보 출력 (테이블 형태) 수정 시작 --- */}
              <div className="flex-1 overflow-y-auto border rounded bg-gray-50"> {/* 배경색 추가 */}
                {coins?.length === 0 ? (
                  <p className="text-gray-500 p-4">조건에 맞는 코인이 없습니다.</p>
                ) : (
                  <table className="min-w-full table-auto text-left text-sm"> {/* 최소 너비, 자동 테이블 레이아웃, 좌측 정렬, 작은 글씨 */}
                    <thead className="bg-gray-200 sticky top-0"> {/* 헤더 고정 */}
                      <tr>
                        <th className="py-2 px-3 border-b">Symbol</th>
                        <th className="py-2 px-3 border-b">현재가</th> {/* FreeCH 대신 현재가로 변경 */}
                        <th className="py-2 px-3 border-b">24H 거래대금</th> {/* FreeVOL 대신 24H 거래대금으로 변경 */}
                        <th className="py-2 px-3 border-b">1분봉 거래대금</th>
                        <th className="py-2 px-3 border-b">15분봉 거래대금</th>
                        <th className="py-2 px-3 border-b">1시간봉 거래대금</th>
                        <th className="py-2 px-3 border-b">유지율</th>
                        <th className="py-2 px-3 border-b">전일대비</th>
                        <th className="py-2 px-3 border-b">시총</th> {/* 시가총액 */}
                      </tr>
                    </thead>
                    <tbody>
                      {coins?.map((coin) => (
                        <tr key={coin.name || coin.id} className="border-b hover:bg-gray-100"> {/* name 또는 id를 key로 사용 */}
                          <td className="py-2 px-3">{coin.symbol}</td> {/* <--- 심볼 */}
                          <td className="py-2 px-3">{coin.currentPrice?.toLocaleString()}</td> {/* <--- 현재가 */}
                          <td className="py-2 px-3">
                            {coin.volume != null ?
                              `${Math.floor(coin.volume / 1_000_000).toLocaleString()} 백만` // 백만 단위로 나눈 후 소수점 이하 버림, 콤마 추가, "백만" 단위 표시
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
                          <td className="py-2 px-3">N/A</td> {/* <--- 유지율 (Upbit API에서 직접 제공 안함) */}
                          <td className="py-2 px-3">{coin.priceChange != null ?
                            `${Math.floor(coin.priceChange * 10000) / 100}%`
                            : 'N/A'}
                          </td> {/* <--- 전일대비 */}
                          <td className="py-2 px-3">N/A</td> {/* <--- 시총 (Upbit API에서 직접 제공 안함) */}
                        </tr>
                      ))}
                    </tbody>
                  </table>
                )}
              </div>
              {/* --- 코인 정보 출력 (테이블 형태) 수정 끝 --- */}
            </>
          )}
        </div>

        {/* 알람 전용 섹션 (알람 로그): 조건 발생 시 소리가 날 수 있음을 암시 */}
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
                    {/* 현재 시간 사용 (실제 알람 발생 시간은 백엔드에서 받아와야 함) */}
                    <span className="text-gray-500">[{new Date().toLocaleTimeString()}]</span>{' '}
                    <span className="font-medium">{coin.name}:</span> {alarmMsg} (삐빅!)
                  </div>
                ))
              ))
            )}
          </div>
        </div>
      </div>

      {/* 새로운 하단 섹션 (줄다리기 힘겨루기) */}
      {/* bg-white: 흰색 배경, p-4: 패딩, rounded-lg: 둥근 모서리, shadow-md: 그림자, m-4: 마진 */}
      <div className="bg-white p-4 rounded-lg shadow-md m-4">
        <h2 className="text-xl font-semibold mb-3 text-center">줄다리기 힘겨루기</h2>
        {/* 팀 이름 표시: 팀 레드와 팀 옐로우 */}
        <div className="flex items-center justify-center space-x-4 mb-2">
          <span className="text-red-600 font-bold">{'<---- [ 팀 레드 ]'}</span>
          <span className="text-yellow-600 font-bold">{'[ 팀 옐로우 ] ---->'}</span>
        </div>
        {/* 힘겨루기 바: 빨간색과 노란색으로 힘의 균형 표현 */}
        {/* w-full: 너비 100%, h-8: 높이, bg-gray-200: 배경색, rounded-full: 둥근 모양 */}
        {/* flex overflow-hidden: 내부 요소 플렉스 및 오버플로우 숨김 (바가 넘치지 않도록) */}
        <div className="w-full h-8 bg-gray-200 rounded-full flex overflow-hidden">
          {/* 빨강팀 바 (예시: 40% 승리) */}
          <div className="bg-red-600 h-full" style={{ width: '40%' }}></div>
          {/* 중앙 구분선 (얇은 회색) */}
          <div className="bg-gray-500 h-full w-1"></div>
          {/* 노랑팀 바 (예시: 59% 승리, 1%는 구분선) */}
          <div className="bg-yellow-500 h-full" style={{ width: '59%' }}></div>
        </div>
      </div>
    </div>
  );
}

// // frontend/src/App.tsx
// import React, { useState } from 'react';
// import './index.css'; // TailwindCSS 임포트
//
// // React Query의 useQuery 훅 임포트
// import { useQuery } from '@tanstack/react-query';
//
//
// /*
//  * useClickOutside 커스텀 훅:
//  * 특정 DOM 요소(ref로 참조) 외부를 클릭했을 때 지정된 콜백 함수를 실행합니다.
//  * 팝오버, 드롭다운 메뉴 등을 외부 클릭 시 닫는 데 유용합니다.
//  * @param ref - 외부 클릭을 감지할 대상 DOM 요소의 React Ref 객체
//  * @param callback - 외부 클릭 시 실행될 함수
// */
// function useClickOutside(ref: React.RefObject<HTMLElement>, callback: () => void) {
//   useEffect(() => {
//     const handleClickOutside = (event: MouseEvent) => {
//       // ref.current가 존재하고, 클릭된 요소가 ref.current 내부에 포함되지 않을 때
//       if (ref.current && !ref.current.contains(event.target as Node)) {
//         callback(); // 콜백 함수 실행
//       }
//     };
//     // 컴포넌트 마운트 시 document에 마우스 클릭 이벤트 리스너 등록
//     document.addEventListener('mousedown', handleClickOutside);
//     // 컴포넌트 언마운트 시 이벤트 리스너 제거 (클린업 함수)
//     return () => {
//       document.removeEventListener('mousedown', handleClickOutside);
//     };
//   }, [ref, callback]); // ref와 callback이 변경될 때만 이펙트 재실행
// }
//
//
// // 백엔드에서 받아올 코인 데이터의 타입 정의 (백엔드의 CoinResponseDto와 일치해야 합니다.)
// interface Coin {
//   id: number;
//   name: string;
//   marketCap: number;
//   priceChange: string;
//   volume: number;
//   alarm: string[]; // 백엔드 DTO의 필드명 'alarm'과 일치
// }
//
// export default function App() {
//   // 필터 상태 (대형, 중형, 소형 코인 선택 여부)
//   const [filters, setFilters] = useState({
//     large: true,
//     mid: true,
//     small: true,
//   });
//
//   // useQuery 훅을 사용하여 백엔드 API에서 코인 데이터를 가져옵니다.
//   // useQuery는 데이터 fetching, 캐싱, 로딩/에러 상태 관리 등을 자동으로 처리해줍니다.
//   const { data: coins, isLoading, isError, error } = useQuery<Coin[], Error>({
//     queryKey: ['coins', filters], // 'coins'는 쿼리 이름, filters 객체가 변경되면 이 쿼리가 다시 실행됩니다.
//     queryFn: async () => {
//       // 백엔드 API 엔드포인트 URL 구성
//       // 백엔드가 http://localhost:8080에서 실행된다고 가정합니다.
//       const apiUrl = `http://localhost:8080/api/coins?large=${filters.large}&mid=${filters.mid}&small=${filters.small}`;
//       const response = await fetch(apiUrl); // fetch API를 사용하여 백엔드에 GET 요청을 보냅니다.
//
//       // HTTP 응답 상태 코드가 200번대(성공)가 아니면 에러 발생
//       if (!response.ok) {
//         throw new Error(`HTTP error! status: ${response.status}`);
//       }
//
//       return response.json(); // 응답 본문을 JSON 형태로 파싱하여 반환합니다.
//     },
//     // staleTime: 쿼리 데이터가 'stale' 상태로 간주되기 전까지의 시간 (ms).
//     // 여기서는 5분(1000ms * 60초 * 5분)으로 설정하여 불필요한 API 호출을 줄입니다.
//     staleTime: 1000 * 60 * 5,
//     // refetchOnWindowFocus: 윈도우가 다시 포커스될 때 쿼리를 다시 실행할지 여부.
//     refetchOnWindowFocus: false,
//   });
//
//   // 알람이 있는 코인만 필터링
//   const filteredAlarms = coins?.filter(
//     (coin) => coin.alarm && coin.alarm.length > 0
//   ) || [];
//
//   return (
//     // min-h-screen: 최소 화면 높이, bg-gray-100: 배경색, flex flex-col: 세로 방향 플렉스 컨테이너
//     <div className="min-h-screen bg-gray-100 flex flex-col">
//
//       {/* 새로운 상단 옵션 섹션 */}
//       <div className="bg-white p-4 rounded-lg shadow-md m-4">
//         <h2 className="text-xl font-semibold mb-3">새로운 상단 옵션 섹션</h2>
//         {/* 옵션 1,2,3을 수평으로 배치 */}
//         <div className="flex space-x-4">
//           <div className="p-2 bg-blue-100 rounded">옵션1 내용</div>
//           <div className="p-2 bg-green-100 rounded">옵션2 내용</div>
//           <div className="p-2 bg-purple-100 rounded">옵션3 내용</div>
//         </div>
//       </div>
//
//       {/* 중앙 메인 영역 (코인목록)과 알람 전용 섹션 (알람 로그)을 포함하는 플렉스 컨테이너 */}
//       {/* flex-1: 남은 공간 모두 차지, p-4: 패딩, space-x-4: 자식 요소 간 가로 간격 */}
//       <div className="flex flex-1 p-4 space-x-4">
//
//         {/* 중앙 메인 영역 (코인목록) */}
//         {/* flex-1: 남은 공간 모두 차지, bg-white: 배경색, p-4: 패딩, rounded-lg: 둥근 모서리, shadow-md: 그림자, flex flex-col: 세로 방향 플렉스 컨테이너 */}
//         <div className="flex-1 bg-white p-4 rounded-lg shadow-md flex flex-col">
//           <h2 className="text-xl font-semibold mb-3">코인목록</h2>
//
//           {/* 필터링 체크박스 (기존 ① 알람 맞춤설정 내용) */}
//           <div className="space-x-4 mb-4">
//             <label>
//               <input type="checkbox" checked={filters.large} onChange={() => setFilters({ ...filters, large: !filters.large })} />{' '}
//               대형(5조 이상)
//             </label>
//             <label>
//               <input type="checkbox" checked={filters.mid} onChange={() => setFilters({ ...filters, mid: !filters.mid })} />{' '}
//               중형(7000억 이상)
//             </label>
//             <label>
//               <input type="checkbox" checked={filters.small} onChange={() => setFilters({ ...filters, small: !filters.small })} />{' '}
//               소형(500억 이상)
//             </label>
//           </div>
//
//           {/* 로딩 중이거나 에러 발생 시 메시지 표시 */}
//           {isLoading && <p className="text-center text-gray-600">코인 데이터를 불러오는 중입니다...</p>}
//           {isError && <p className="text-center text-red-500">에러: {error?.message || "알 수 없는 오류 발생"}</p>}
//
//           {/* 로딩이 끝나고 에러가 없을 때만 코인 정보 표시 */}
//           {!isLoading && !isError && (
//             <>
//               {/* 코인 그리드 */}
//               <div className="grid grid-cols-5 gap-2 mb-4">
//                 <div className="bg-gray-100 p-2 text-center rounded">BTC</div>
//                 <div className="bg-gray-100 p-2 text-center rounded">ETH</div>
//                 <div className="bg-gray-100 p-2 text-center rounded">XRP</div>
//                 <div className="bg-gray-100 p-2 text-center rounded">ADA</div>
//                 <div className="bg-gray-100 p-2 text-center rounded">DOGE</div>
//               </div>
//
//               {/* 코인 정보 출력 (리스트박스 형태) */}
//               {/* flex-1: 남은 공간 모두 차지, overflow-y-auto: 세로 스크롤, border: 테두리, rounded: 둥근 모서리, p-2: 패딩, space-y-1: 자식 요소 간 세로 간격 */}
//               <div className="flex-1 overflow-y-auto border rounded p-2 space-y-1">
//                 {coins?.length === 0 ? (
//                   <p className="text-gray-500">조건에 맞는 코인이 없습니다.</p>
//                 ) : (
//                   coins?.map((coin) => (
//                     <div key={coin.id} className="p-2 bg-yellow-50 rounded shadow-sm text-sm">
//                       <span className="font-medium">{coin.name}:</span> 변동률 {coin.priceChange}, 거래대금 {coin.volume.toLocaleString()}원
//                     </div>
//                   ))
//                 )}
//               </div>
//             </>
//           )}
//         </div>
//
//         {/* 알람 전용 섹션 (알람 로그) */}
//         {/* w-1/3: 너비 1/3, bg-white: 배경색, p-4: 패딩, rounded-lg: 둥근 모서리, shadow-md: 그림자, flex flex-col: 세로 방향 플렉스 컨테이너 */}
//         <div className="w-1/3 bg-white p-4 rounded-lg shadow-md flex flex-col">
//           <h2 className="text-xl font-semibold mb-3">알람 로그 (조건 발생 시 소리)</h2>
//           {/* 알람 로그 출력 영역 */}
//           {/* flex-1: 남은 공간 모두 차지, overflow-y-auto: 세로 스크롤, border: 테두리, rounded: 둥근 모서리, p-2: 패딩, space-y-1: 자식 요소 간 세로 간격, bg-gray-50: 연한 회색 배경, text-sm: 작은 글씨 */}
//           <div className="flex-1 overflow-y-auto border rounded p-2 space-y-1 bg-gray-50 text-sm">
//             {filteredAlarms.length === 0 ? (
//               <p className="text-gray-500">조건에 맞는 알람이 없습니다.</p>
//             ) : (
//               filteredAlarms.map((coin) => (
//                 coin.alarm.map((alarmMsg, i) => (
//                   <div key={`${coin.id}-${i}`} className="text-red-600">
//                     {/* 타임스탬프 (현재 시간 사용, 실제 알람 발생 시간은 백엔드에서 받아와야 함) */}
//                     <span className="text-gray-500">[{new Date().toLocaleTimeString()}]</span>{' '}
//                     <span className="font-medium">{coin.name}:</span> {alarmMsg} (삐빅!) {/* 소리 암시 */}
//                   </div>
//                 ))
//               ))
//             )}
//           </div>
//         </div>
//       </div>
//
//       {/* 새로운 하단 섹션 (줄다리기 힘겨루기) */}
//       <div className="bg-white p-4 rounded-lg shadow-md m-4">
//         <h2 className="text-xl font-semibold mb-3 text-center">줄다리기 힘겨루기</h2>
//         <div className="flex items-center justify-center space-x-4 mb-2">
//           <span className="text-red-600 font-bold">{'<---- [ 팀 레드 ]'}</span>
//           <span className="text-yellow-600 font-bold">{'[ 팀 옐로우 ] ---->'}</span>
//         </div>
//         {/* 힘겨루기 바 */}
//         {/* w-full: 너비 100%, h-8: 높이, bg-gray-200: 배경색, rounded-full: 둥근 모양, flex overflow-hidden: 내부 요소 플렉스 및 오버플로우 숨김 */}
//         <div className="w-full h-8 bg-gray-200 rounded-full flex overflow-hidden">
//           {/* 빨강팀 바 (예시: 40% 승리) */}
//           <div className="bg-red-600 h-full" style={{ width: '40%' }}></div>
//           {/* 중앙 구분선 (얇은 회색) */}
//           <div className="bg-gray-500 h-full w-1"></div>
//           {/* 노랑팀 바 (예시: 60% 승리) */}
//           {/* 남은 너비 계산: 100% - 빨강팀 너비 - 구분선 너비 = 100 - 40 - (1/전체너비) */}
//           {/* 여기서는 간단하게 59%로 설정하여 합이 100%가 되도록 합니다. */}
//           <div className="bg-yellow-500 h-full" style={{ width: '59%' }}></div>
//         </div>
//       </div>
//     </div>
//   );
// }