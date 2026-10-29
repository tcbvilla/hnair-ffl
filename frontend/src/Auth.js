import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, setToken } from './api';

export default function Auth({ mode }) {
  const navigate = useNavigate();
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [inviteCode, setInviteCode] = useState('');
  const [error, setError] = useState('');

  async function submit(event) {
    event.preventDefault();
    setError('');
    const path = mode === 'register' ? '/api/auth/register' : '/api/auth/login';
    const body = { username, password };
    if (mode === 'register') {
      body.inviteCode = inviteCode;
    }
    const result = await api(path, { method: 'POST', body: JSON.stringify(body) });
    if (!result.success) {
      setError(result.message || '失败');
      return;
    }
    setToken(result.data.token);
    navigate(result.data.admin ? '/admin' : '/monitors');
  }

  return (
    <form className="card auth" onSubmit={submit}>
      <h1>{mode === 'register' ? '注册' : '登录'}</h1>
      <div className="row">
        <label>
          用户名
          <input value={username} onChange={(e) => setUsername(e.target.value)} required />
        </label>
        <label>
          密码
          <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {mode === 'register' && (
          <label>
            邀请码
            <input value={inviteCode} onChange={(e) => setInviteCode(e.target.value)} required />
          </label>
        )}
      </div>
      {error && <p className="error">{error}</p>}
      <p>
        <button className="primary" type="submit">{mode === 'register' ? '注册' : '登录'}</button>
      </p>
      {mode === 'register' ? (
        <Link to="/login">已有账号，去登录</Link>
      ) : (
        <Link to="/register">没有账号，去注册</Link>
      )}
    </form>
  );
}
