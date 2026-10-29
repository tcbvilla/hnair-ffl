const TOKEN_KEY = 'ffl_token';

export function getToken() {
  return localStorage.getItem(TOKEN_KEY);
}

export function setToken(token) {
  if (token) {
    localStorage.setItem(TOKEN_KEY, token);
  } else {
    localStorage.removeItem(TOKEN_KEY);
  }
}

export async function api(path, options = {}) {
  const headers = { ...(options.headers || {}) };
  if (options.body && !headers['Content-Type']) {
    headers['Content-Type'] = 'application/json';
  }
  const token = getToken();
  if (token) {
    headers.Authorization = `Bearer ${token}`;
  }
  const url = path.startsWith('/') ? '/ffl' + path : path;
  const response = await fetch(url, { ...options, headers });
  const data = await response.json().catch(() => ({ success: false, message: '请求失败' }));
  if (response.status === 401) {
    setToken(null);
  }
  return data;
}
