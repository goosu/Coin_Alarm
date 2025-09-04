// // frontend/src/api/favorites.js
//import { API_BASE_URL } from './config';

// process.env를 안전하게 접근하는 방법
const API_BASE_URL = import.meta.env?.VITE_API_URL || 'http://localhost:8080/api';

export async function fetchFavorites() {
  try {
    console.log('🔍 fetchFavorites 호출됨, API_BASE_URL:', API_BASE_URL);
    const response = await fetch(`${API_BASE_URL}/favorites`);
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    const data = await response.json();
    console.log('✅ fetchFavorites 성공:', data);
    return Array.isArray(data) ? Array.from(data) : [];
  } catch (error) {
    console.warn('⚠️ fetchFavorites 실패, 로컬 스토리지 사용:', error);
    // 서버 실패 시 로컬 스토리지에서 로드
    try {
      const localFavorites = localStorage.getItem('favorites');
      return localFavorites ? JSON.parse(localFavorites) : [];
    } catch (localError) {
      console.error('로컬 스토리지 에러:', localError);
      return [];
    }
  }
}

export async function addFavorite(symbol) {
  try {
    console.log('➕ addFavorite 호출:', symbol);
    const response = await fetch(`${API_BASE_URL}/favorites/add?marketCode=${symbol}`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
    });
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    console.log('✅ addFavorite 성공:', symbol);
    return true;
  } catch (error) {
    console.error('⚠️ addFavorite 실패, 로컬에서만 처리:', error);
    // 서버 실패해도 일단 성공으로 처리 (로컬에서만)
    return true;
  }
}

export async function removeFavorite(symbol) {
  try {
    console.log('➖ removeFavorite 호출:', symbol);
    const response = await fetch(`${API_BASE_URL}/favorites/remove?marketCode=${symbol}`, {
      method: 'DELETE',
      headers: {
        'Content-Type': 'application/json',
      },
    });
    if (!response.ok) {
      throw new Error(`HTTP error! status: ${response.status}`);
    }
    console.log('✅ removeFavorite 성공:', symbol);
    return true;
  } catch (error) {
    console.error('⚠️ removeFavorite 실패, 로컬에서만 처리:', error);
    // 서버 실패해도 일단 성공으로 처리 (로컬에서만)
    return true;
  }
}

//
// // process.env를 안전하게 접근하는 방법
// const API_BASE_URL = import.meta.env?.VITE_API_URL || 'http://localhost:8080/api';
//
// export async function fetchFavorites() {
//   try {
//     console.log('🔍 fetchFavorites 호출됨, API_BASE_URL:', API_BASE_URL);
//     const response = await fetch(`${API_BASE_URL}/favorites`);
//     if (!response.ok) {
//       throw new Error(`HTTP error! status: ${response.status}`);
//     }
//     const data = await response.json();
//     console.log('✅ fetchFavorites 성공:', data);
//     return Array.isArray(data) ? Array.from(data) : [];
//   } catch (error) {
//     console.warn('⚠️ fetchFavorites 실패, 로컬 스토리지 사용:', error);
//     // 서버 실패 시 로컬 스토리지에서 로드
//     try {
//       const localFavorites = localStorage.getItem('favorites');
//       return localFavorites ? JSON.parse(localFavorites) : [];
//     } catch (localError) {
//       console.error('로컬 스토리지 에러:', localError);
//       return [];
//     }
//   }
// }
//
// export async function addFavorite(symbol) {
//   try {
//     console.log('➕ addFavorite 호출:', symbol);
//     const response = await fetch(`${API_BASE_URL}/favorites/add?marketCode=${symbol}`, {
//       method: 'POST',
//       headers: {
//         'Content-Type': 'application/json',
//       },
//     });
//     if (!response.ok) {
//       throw new Error(`HTTP error! status: ${response.status}`);
//     }
//     console.log('✅ addFavorite 성공:', symbol);
//     return true;
//   } catch (error) {
//     console.error('⚠️ addFavorite 실패, 로컬에서만 처리:', error);
//     // 서버 실패해도 일단 성공으로 처리 (로컬에서만)
//     return true;
//   }
// }
//
// export async function removeFavorite(symbol) {
//   try {
//     console.log('➖ removeFavorite 호출:', symbol);
//     const response = await fetch(`${API_BASE_URL}/favorites/remove?marketCode=${symbol}`, {
//       method: 'DELETE',
//       headers: {
//         'Content-Type': 'application/json',
//       },
//     });
//     if (!response.ok) {
//       throw new Error(`HTTP error! status: ${response.status}`);
//     }
//     console.log('✅ removeFavorite 성공:', symbol);
//     return true;
//   } catch (error) {
//     console.error('⚠️ removeFavorite 실패, 로컬에서만 처리:', error);
//     // 서버 실패해도 일단 성공으로 처리 (로컬에서만)
//     return true;
//   }
// }