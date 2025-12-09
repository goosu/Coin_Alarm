// ToggleSwitch.tsx (또는 App.tsx 파일 내부)

import React from 'react';

interface ToggleSwitchProps {
  isOn: boolean; // 스위치의 현재 상태 (켜짐/꺼짐)
  handleToggle: () => void; // 스위치 클릭 시 실행될 함수
  label?: string; // 스위치 옆에 표시될 라벨 (선택 사항)
}

function ToggleSwitch({ isOn, handleToggle, label }: ToggleSwitchProps) {
  // 고유한 ID를 생성하여 label과 input을 연결합니다.
  // 실제 프로젝트에서는 'useId' 훅 (React 18+)을 사용하는 것이 좋습니다.
  // 또는 label이 없거나 고유하지 않은 경우를 대비하여 폴백 ID를 사용합니다.
  const id = React.useId ? React.useId() : `toggle-switch-${Math.random().toString(36).substring(2, 9)}`;

  return (
    <div className="flex items-center space-x-2"> {/* 라벨과 스위치를 수평 정렬 */}
      {/* 라벨이 있다면 표시합니다 */}
      {label && (
        <label htmlFor={id} className="text-gray-300 cursor-pointer select-none">
          {label}
        </label>
      )}

      {/* 실제 체크박스는 숨기고, 시각적인 스위치 역할을 하는 버튼을 만듭니다 */}
      <button
        type="button"
        id={id} // label과 연결될 ID
        onClick={handleToggle}
        // ---------- 트랙 (스위치 배경) 스타일 ----------
        // relative: 내부 썸의 absolute 위치 기준
        // inline-flex flex-shrink-0: 인라인 플렉스 박스로 만들고 내용 축소 방지
        // h-6 w-11: 높이 24px, 너비 44px (전체 알약 형태 크기)
        // border-2 border-transparent: 테두리 2px 투명 (실제론 없지만 크기 유지)
        // rounded-full: 완전히 둥글게 (알약 형태)
        // cursor-pointer: 클릭 가능 표시
        // transition-colors ease-in-out duration-200: 색상 전환 애니메이션
        // focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-lime-600: 포커스 시 접근성 및 시각 피드백
        // ---------- 색상 조건부 적용 ----------
        // bg-lime-500: isOn일 때 연두색 배경 (켜짐)
        // bg-white: isOn이 아닐 때 흰색 배경 (꺼짐)
        className={`relative inline-flex flex-shrink-0 h-6 w-11 border-2 border-gray-300 rounded-full cursor-pointer transition-colors ease-in-out duration-200 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-lime-600
          ${isOn ? 'bg-lime-500' : 'bg-white'}`}
        role="switch"
        aria-checked={isOn}
      >
        {/* 스크린 리더용 텍스트 (시각적으로는 숨김) */}
        <span className="sr-only">Use setting</span>

        {/* ---------- 썸 (움직이는 동그라미) 스타일 ----------
        // pointer-events-none: 썸 자체 클릭 방지 (버튼 클릭만 허용)
        // inline-block h-5 w-5: 높이 20px, 너비 20px (트랙보다 약간 작게)
        // rounded-full: 완전한 원형
        // bg-white: 썸의 색상 (항상 흰색)
        // shadow: 그림자 효과
        // transform ring-0 transition ease-in-out duration-200: 위치 변경 애니메이션
        // ---------- 위치 조건부 적용 ----------
        // translate-x-5: isOn일 때 오른쪽으로 20px 이동
        // translate-x-0: isOn이 아닐 때 왼쪽 (기본 위치)
        */}
        <span
          aria-hidden="true" // 스크린 리더에서 숨김
          className={`pointer-events-none inline-block h-5 w-5 rounded-full bg-white shadow transform ring-0 transition ease-in-out duration-200
            ${isOn ? 'translate-x-5' : 'translate-x-0'}`}
        />
      </button>
    </div>
  );
}

export default ToggleSwitch;