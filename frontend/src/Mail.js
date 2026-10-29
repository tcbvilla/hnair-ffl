import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, setToken } from './api';

export default function Mail() {
  const navigate = useNavigate();
  const [host, setHost] = useState('');
  const [port, setPort] = useState('465');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [recipient, setRecipient] = useState('');
  const [ssl, setSsl] = useState(true);
  const [configured, setConfigured] = useState(false);
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  useEffect(() => {
    async function boot() {
      const me = await api('/api/auth/me');
      if (!me.success) {
        navigate('/login');
        return;
      }
      const mail = await api('/api/mail');
      if (mail.success && mail.data) {
        setHost(mail.data.host || '');
        setPort(mail.data.port || 465);
        setUsername(mail.data.username || '');
        setRecipient(mail.data.recipient || '');
        setSsl(mail.data.ssl !== false);
        setConfigured(!!mail.data.configured);
      }
    }
    boot();
  }, [navigate]);

  async function save(event) {
    event.preventDefault();
    setError('');
    setMessage('');
    const result = await api('/api/mail', {
      method: 'PUT',
      body: JSON.stringify({
        host,
        port: Number(port),
        username,
        password,
        recipient,
        ssl
      })
    });
    if (!result.success) {
      setError(result.message || '保存失败');
      return;
    }
    setPassword('');
    setConfigured(!!result.data.configured);
    setMessage('邮箱已保存');
  }

  return (
    <div className="page">
      <div className="topbar">
        <h1>邮箱</h1>
        <div className="row">
          <Link to="/monitors">返回监控</Link>
          <button className="ghost" onClick={() => { setToken(null); navigate('/login'); }}>退出</button>
        </div>
      </div>
      <form className="card" onSubmit={save}>
        <p className="muted">用发信账号登录 SMTP 把信发出去，接收邮箱是实际收提醒的地址。两个可以相同，也可以不同。</p>
        <div className="row">
          <label>服务器
            <input value={host} onChange={(e) => setHost(e.target.value)} placeholder="smtp.yeah.net" required />
          </label>
          <label>端口
            <input type="number" min="1" max="65535" value={port} onChange={(e) => setPort(e.target.value)} required />
          </label>
          <label className="check">
            <input type="checkbox" checked={ssl} onChange={(e) => setSsl(e.target.checked)} />
            SSL
          </label>
        </div>
        <div className="row">
          <label>发信账号
            <input value={username} onChange={(e) => setUsername(e.target.value)} placeholder="name@yeah.net" required />
          </label>
          <label>授权码
            <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} placeholder={configured ? '留空则不修改' : ''} />
          </label>
          <label>接收邮箱
            <input value={recipient} onChange={(e) => setRecipient(e.target.value)} placeholder="notify@example.com" required />
          </label>
          <button className="primary" type="submit">保存</button>
        </div>
        {error && <p className="error">{error}</p>}
        {message && <p className="muted">{message}</p>}
      </form>
    </div>
  );
}
