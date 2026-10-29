import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { api, setToken } from './api';

export default function Admin() {
  const navigate = useNavigate();
  const [intervalSeconds, setIntervalSeconds] = useState(300);
  const [targetPrice, setTargetPrice] = useState(199);
  const [count, setCount] = useState(1);
  const [codes, setCodes] = useState([]);
  const [status, setStatus] = useState('ALL');
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');

  async function loadCodes(nextStatus = status) {
    const result = await api(`/api/admin/invite-codes?status=${nextStatus}&page=0&size=50`);
    if (result.success) {
      setCodes(result.data.content || []);
    }
  }

  useEffect(() => {
    async function boot() {
      const me = await api('/api/auth/me');
      if (!me.success) {
        navigate('/login');
        return;
      }
      if (!me.data.admin) {
        navigate('/monitors');
        return;
      }
      const setting = await api('/api/admin/settings');
      if (setting.success) {
        setIntervalSeconds(setting.data.intervalSeconds);
        setTargetPrice(setting.data.targetPrice);
      }
      loadCodes('ALL');
    }
    boot();
  }, []);

  async function saveSettings(event) {
    event.preventDefault();
    setError('');
    setMessage('');
    const result = await api('/api/admin/settings', {
      method: 'PUT',
      body: JSON.stringify({ intervalSeconds: Number(intervalSeconds), targetPrice: Number(targetPrice) })
    });
    if (!result.success) {
      setError(result.message || '保存失败');
      return;
    }
    setMessage('参数已保存');
  }

  async function generate(event) {
    event.preventDefault();
    setError('');
    const result = await api('/api/admin/invite-codes', {
      method: 'POST',
      body: JSON.stringify({ count: Number(count) })
    });
    if (!result.success) {
      setError(result.message || '发码失败');
      return;
    }
    setMessage(`已生成 ${result.data.codes.length} 个邀请码`);
    loadCodes();
  }

  return (
    <div className="page">
      <div className="topbar">
        <h1>管理</h1>
        <button className="ghost" onClick={() => { setToken(null); navigate('/login'); }}>退出</button>
      </div>
      <form className="card" onSubmit={saveSettings}>
        <h2>监控参数</h2>
        <div className="row">
          <label>检查间隔（秒，60-3600）
            <input type="number" min="60" max="3600" value={intervalSeconds} onChange={(e) => setIntervalSeconds(e.target.value)} />
          </label>
          <label>目标价格
            <input type="number" min="1" value={targetPrice} onChange={(e) => setTargetPrice(e.target.value)} />
          </label>
          <button className="primary" type="submit">保存</button>
        </div>
        <p className="muted">经济舱价格等于目标价才算命中。余票数量会写在结果里。A 表示 9 张以上。</p>
      </form>
      <form className="card" onSubmit={generate}>
        <h2>邀请码</h2>
        <div className="row">
          <label>数量
            <input type="number" min="1" max="50" value={count} onChange={(e) => setCount(e.target.value)} />
          </label>
          <button className="primary" type="submit">生成</button>
          <label>状态
            <select value={status} onChange={(e) => { setStatus(e.target.value); loadCodes(e.target.value); }}>
              <option value="ALL">全部</option>
              <option value="UNUSED">未使用</option>
              <option value="USED">已使用</option>
            </select>
          </label>
        </div>
        {error && <p className="error">{error}</p>}
        {message && <p className="muted">{message}</p>}
        <table>
          <thead>
            <tr>
              <th>邀请码</th>
              <th>状态</th>
              <th>使用者</th>
            </tr>
          </thead>
          <tbody>
            {codes.map((item) => (
              <tr key={item.id}>
                <td>{item.code}</td>
                <td>{item.status === 'UNUSED' ? '未使用' : '已使用'}</td>
                <td>{item.usedBy || ''}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </form>
    </div>
  );
}
