// frontend/src/main.tsx
// 이 파일을 열어 기존 내용을 모두 지우고 아래 코드를 붙여넣으세요.

import React from 'react';
import ReactDOM from 'react-dom/client';
import App from './App.tsx';
import './index.css'; // Tailwind CSS 및 기본 스타일

// React Query 관련 임포트
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ReactQueryDevtools } from '@tanstack/react-query-devtools'; // 개발자 도구 (선택 사항)

// QueryClient 인스턴스 생성: React Query의 핵심 클라이언트 객체입니다.
// 여기에 전역적인 쿼리 옵션 등을 설정할 수 있습니다.
const queryClient = new QueryClient();

// ReactDOM.createRoot를 사용하여 React 애플리케이션을 렌더링합니다.
// document.getElementById('root')!는 index.html에 있는 'root' ID를 가진 DOM 요소를 찾습니다.
ReactDOM.createRoot(document.getElementById('root')!).render(
  // React.StrictMode: 개발 모드에서 잠재적인 문제를 감지하기 위한 도구입니다.
  <React.StrictMode>
    {/* QueryClientProvider로 App 컴포넌트를 감싸서 React Query 기능을 전역적으로 사용할 수 있게 합니다. */}
    <QueryClientProvider client={queryClient}>
      <App />
      {/* React Query 개발자 도구: 개발 모드에서 쿼리 상태를 시각적으로 확인할 수 있게 해줍니다. */}
      {/* process.env.NODE_ENV === 'development' 조건으로 개발 모드에서만 활성화하는 것이 일반적입니다. */}
      <ReactQueryDevtools initialIsOpen={false} />
    </QueryClientProvider>
  </React.StrictMode>,
);

Coin_Alarm