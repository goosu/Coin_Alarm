// frontend/src/api/favorites.ts

// 백엔드 API 기본 URL. .env 파일에 REACT_APP_API_BASE=http://localhost:8080 등으로 설정하세요.
const API_BASE = process.env.REACT_APP_API_BASE || "https://api.example.com";
const FAVORITES_API = `${API_BASE}/api/favorites`;

// 인증 헤더를 가져오는 함수 (예: localStorage에서 토큰을 가져와 Bearer 토큰으로 사용)
function getAuthHeaders() {
  const token = localStorage.getItem("token") || (process.env.REACT_APP_API_TOKEN ?? ""); // REACT_APP_API_TOKEN은 개발용 임시 토큰으로 사용 가능
  return token ? { Authorization: `Bearer ${token}` } : {};
}

// 서버에서 즐겨찾기 목록을 가져오는 함수
export async function fetchFavorites(): Promise<string[]> {
  const res = await fetch(FAVORITES_API, {
    method: "GET",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
  });
  if (!res.ok) {
    // 401 Unauthorized, 403 Forbidden 등 특정 에러 처리 필요 시 여기 추가
    console.error(`Failed to fetch favorites: ${res.status} ${res.statusText}`);
    throw new Error(`Failed to fetch favorites: ${res.status}`);
  }
  const json = await res.json();
  return Array.isArray(json.favorites) ? json.favorites : [];
}

// 즐겨찾기에 코인을 추가하는 함수
export async function addFavorite(symbol: string): Promise<void> {
  const res = await fetch(FAVORITES_API, {
    method: "POST",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
    body: JSON.stringify({ symbol }),
  });
  if (!res.ok) {
    console.error(`Failed to add favorite ${symbol}: ${res.status} ${res.statusText}`);
    throw new Error(`Failed to add favorite ${symbol}: ${res.status}`);
  }
}

// 즐겨찾기에서 코인을 삭제하는 함수
export async function removeFavorite(symbol: string): Promise<void> {
  const res = await fetch(`${FAVORITES_API}/${encodeURIComponent(symbol)}`, {
    method: "DELETE",
    headers: { "Content-Type": "application/json", ...getAuthHeaders() },
  });
  if (!res.ok) {
    console.error(`Failed to remove favorite ${symbol}: ${res.status} ${res.statusText}`);
    throw new Error(`Failed to remove favorite ${symbol}: ${res.status}`);
  }
}