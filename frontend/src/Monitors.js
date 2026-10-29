import { Fragment, useEffect, useRef, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { api, setToken } from './api';

const empty = { origin: '', destination: '', flightDate: '', flightNo: '', notifyPresence: false, notifyQuantity: false };

export default function Monitors() {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [form, setForm] = useState(empty);
  const [targetPrice, setTargetPrice] = useState(199);
  const [intervalSeconds, setIntervalSeconds] = useState(300);
  const [error, setError] = useState('');
  const [username, setUsername] = useState('');
  const [openId, setOpenId] = useState(null);
  const [history, setHistory] = useState([]);
  const openIdRef = useRef(null);

  async function load() {
    const me = await api('/api/auth/me');
    if (!me.success) {
      navigate('/login');
      return;
    }
    if (me.data.admin) {
      navigate('/admin');
      return;
    }
    setUsername(me.data.username);
    const setting = await api('/api/settings');
    if (setting.success) {
      setTargetPrice(setting.data.targetPrice);
      setIntervalSeconds(setting.data.intervalSeconds);
    }
    const list = await api('/api/monitors');
    if (list.success) {
      setItems(list.data || []);
    }
    const historyId = openIdRef.current;
    if (historyId) {
      const events = await api(`/api/monitors/${historyId}/history`);
      if (events.success && openIdRef.current === historyId) {
        setHistory(events.data || []);
      }
    }
  }

  useEffect(() => {
    load();
    const timer = setInterval(load, 15000);
    return () => clearInterval(timer);
  }, []);

  function update(name, value) {
    setForm((prev) => ({ ...prev, [name]: value }));
  }

  async function create(event) {
    event.preventDefault();
    setError('');
    const result = await api('/api/monitors', { method: 'POST', body: JSON.stringify(form) });
    if (!result.success) {
      setError(result.message || '创建失败');
      return;
    }
    setForm(empty);
    load();
  }

  function payload(item, patch) {
    return {
      origin: item.origin,
      destination: item.destination,
      flightDate: item.flightDate,
      flightNo: item.flightNo || '',
      enabled: item.enabled,
      notifyPresence: item.notifyPresence,
      notifyQuantity: item.notifyQuantity,
      ...patch
    };
  }

  async function saveItem(item, patch) {
    const result = await api(`/api/monitors/${item.id}`, {
      method: 'PUT',
      body: JSON.stringify(payload(item, patch))
    });
    if (!result.success) {
      setError(result.message || '更新失败');
      return;
    }
    load();
  }

  async function toggle(item) {
    saveItem(item, { enabled: !item.enabled });
  }

  async function remove(item) {
    const result = await api(`/api/monitors/${item.id}`, { method: 'DELETE' });
    if (!result.success) {
      setError(result.message || '删除失败');
      return;
    }
    load();
  }

  async function check(item) {
    setError('');
    const result = await api(`/api/monitors/${item.id}/check`, { method: 'POST' });
    if (!result.success) {
      setError(result.message || '查询失败');
      return;
    }
    load();
  }

  function setOpen(id) {
    openIdRef.current = id;
    setOpenId(id);
  }

  async function showHistory(item) {
    if (openId === item.id) {
      setOpen(null);
      return;
    }
    setError('');
    const id = item.id;
    setOpen(id);
    const result = await api(`/api/monitors/${id}/history`);
    if (openIdRef.current !== id) {
      return;
    }
    if (!result.success) {
      setError(result.message || '读取历史失败');
      return;
    }
    setHistory(result.data || []);
  }

  function kind(event) {
    const parts = [];
    if (event.initialRecord) {
      parts.push('首次');
    }
    if (event.presenceChanged) {
      parts.push('有无变动');
    }
    if (event.quantityChanged) {
      parts.push('库存变动');
    }
    return parts.join('、') || '记录';
  }

  return (
    <div className="page">
      <div className="topbar">
        <h1>票价监控</h1>
        <div className="row">
          <span className="muted">{username}</span>
          <button className="ghost" type="button" onClick={() => load()}>刷新</button>
          <Link to="/mail">邮箱</Link>
          <button className="ghost" onClick={() => { setToken(null); navigate('/login'); }}>退出</button>
        </div>
      </div>
      <p className="muted">目标价 {targetPrice} 元，检查间隔 {intervalSeconds} 秒。最多 10 条。航班号只填数字，例如 7428，不填则看全部航班。</p>
      <form className="card" onSubmit={create}>
        <div className="row">
          <label>出发
            <input value={form.origin} maxLength={3} onChange={(e) => update('origin', e.target.value.toUpperCase())} required />
          </label>
          <label>到达
            <input value={form.destination} maxLength={3} onChange={(e) => update('destination', e.target.value.toUpperCase())} required />
          </label>
          <label>日期
            <input type="date" value={form.flightDate} onChange={(e) => update('flightDate', e.target.value)} required />
          </label>
          <label>航班号
            <input value={form.flightNo} inputMode="numeric" maxLength={4} onChange={(e) => update('flightNo', e.target.value.replace(/\D/g, ''))} placeholder="7428" />
          </label>
          <label className="check">
            <input type="checkbox" checked={form.notifyPresence} onChange={(e) => update('notifyPresence', e.target.checked)} />
            有无变动提醒
          </label>
          <label className="check">
            <input type="checkbox" checked={form.notifyQuantity} onChange={(e) => update('notifyQuantity', e.target.checked)} />
            库存数字提醒
          </label>
          <button className="primary" type="submit">添加</button>
        </div>
        {error && <p className="error">{error}</p>}
      </form>
      <div className="card">
        <table className="sheet">
          <thead>
            <tr>
              <th>航线</th>
              <th>日期</th>
              <th>航班</th>
              <th>状态</th>
              <th>余票</th>
              <th>最近结果</th>
              <th>提醒</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            {items.length === 0 && (
              <tr>
                <td colSpan="8" className="muted">还没有监控</td>
              </tr>
            )}
            {items.map((item) => {
              const status = statusOf(item);
              const lines = resultLines(item);
              return (
                <Fragment key={item.id}>
                  <tr className="data">
                    <td className="route">{item.origin} → {item.destination}</td>
                    <td className="nowrap">{item.flightDate}</td>
                    <td>{item.flightNo || '全部'}</td>
                    <td>
                      <span className={`badge ${status.cls}`}>{status.text}</span>
                      {!item.enabled && <span className="badge pause">已暂停</span>}
                    </td>
                    <td>
                      <div className="stack">
                        {lines.map((line, index) => <StockCell key={index} row={line} />)}
                      </div>
                    </td>
                    <td>
                      <div className="stack">
                        {lines.map((line, index) => <span className="nowrap" key={index}>{line.text}</span>)}
                      </div>
                      <div className="muted">{clock(item.lastCheckedAt)}</div>
                    </td>
                    <td>
                      <div className="stack">
                        <label className="check">
                          <input type="checkbox" checked={!!item.notifyPresence} onChange={(e) => saveItem(item, { notifyPresence: e.target.checked })} />
                          有无变动
                        </label>
                        <label className="check">
                          <input type="checkbox" checked={!!item.notifyQuantity} onChange={(e) => saveItem(item, { notifyQuantity: e.target.checked })} />
                          库存数字
                        </label>
                      </div>
                    </td>
                    <td>
                      <div className="actions">
                        <button className="ghost" type="button" onClick={() => showHistory(item)}>历史</button>
                        <button className="ghost" type="button" onClick={() => check(item)}>立即查</button>
                        <button className="ghost" type="button" onClick={() => toggle(item)}>{item.enabled ? '暂停' : '启用'}</button>
                        <button className="danger" type="button" onClick={() => remove(item)}>删除</button>
                      </div>
                    </td>
                  </tr>
                  {openId === item.id && (
                    <tr className="history-row">
                      <td colSpan="8">
                        <HistoryTable events={history} kind={kind} />
                      </td>
                    </tr>
                  )}
                </Fragment>
              );
            })}
          </tbody>
        </table>
      </div>
    </div>
  );
}

function clock(value) {
  if (!value) {
    return '';
  }
  return String(value).replace('T', ' ').replace(/\.\d+$/, '').slice(0, 19);
}

function statusOf(item) {
  if (!item.lastCheckedAt) {
    return { cls: 'wait', text: '等待检查' };
  }
  if (item.lastSuccess === false) {
    return { cls: 'bad', text: '查询失败' };
  }
  if (item.lastHit) {
    return { cls: 'ok', text: '命中' };
  }
  return { cls: 'miss', text: '未命中' };
}

function resultLines(item) {
  if (!item.lastCheckedAt) {
    return [{ text: '尚未检查' }];
  }
  if (item.lastSuccess === false) {
    return [{ text: item.lastError || '查询失败' }];
  }
  const summary = item.lastSummary || '';
  const missed = /^未命中目标价\d+，/.test(summary);
  const body = summary.replace(/^未命中目标价\d+，/, '');
  if (!body || body === '无航班' || body === '无此航班') {
    return [{ empty: true, text: body || '无航班' }];
  }
  return body.split('；').filter(Boolean).map((part) => {
    const matched = part.trim().match(/^(?:HU)?(\d+)\s+(\d{2}:\d{2}-\d{2}:\d{2})\s+¥(\d+)\s+余([0-9A]+)$/);
    if (!matched) {
      return { empty: missed, raw: part.trim(), text: part.trim() };
    }
    return {
      empty: missed,
      stock: missed ? undefined : matched[4],
      text: `${matched[1]} ${matched[2]} ¥${matched[3]}`
    };
  });
}

function stockToken(token) {
  return token === 'A' ? '9+' : token;
}

function StockCell({ row }) {
  if (row.empty) {
    return <span className="stock-none">无票</span>;
  }
  if (row.from && row.stock) {
    return (
      <span className="stock-line">
        <span className="stock-num">{stockToken(row.from)}</span>
        <span className="muted">→</span>
        <span className="stock-num">{stockToken(row.stock)}</span>
      </span>
    );
  }
  if (row.stock) {
    return <span className="stock-num">{stockToken(row.stock)}</span>;
  }
  return <span className="muted">—</span>;
}

function splitDetail(detail) {
  const text = detail || '';
  const mark = text.search(/；(?:邮件发送失败|未配置邮箱)/);
  const body = mark < 0 ? text : text.slice(0, mark);
  const note = mark < 0 ? '' : text.slice(mark + 1);
  if (!body || body === '无航班') {
    return { rows: [{ empty: true, explain: '无航班' }], note };
  }
  const rows = body.split('；').filter(Boolean).map((part) => {
    let matched = part.match(/^(\d+)\s+从无到有\s+余([0-9A]+)$/);
    if (matched) {
      return { flight: matched[1], stock: matched[2], explain: '从无到有' };
    }
    matched = part.match(/^(\d+)\s+从有到无\s+原余([0-9A]+)$/);
    if (matched) {
      return { flight: matched[1], empty: true, was: matched[2], explain: '从有到无' };
    }
    matched = part.match(/^(\d+)\s+余([0-9A]+)→([0-9A]+)$/);
    if (matched) {
      return { flight: matched[1], from: matched[2], stock: matched[3], explain: '数字变化' };
    }
    matched = part.match(/^(\d+)\s+余([0-9A]+)$/);
    if (matched) {
      return { flight: matched[1], stock: matched[2], explain: '记录' };
    }
    matched = part.match(/^(\d+)\s+无$/);
    if (matched) {
      return { flight: matched[1], empty: true, explain: '无票' };
    }
    return { explain: part };
  });
  return { rows, note };
}

function HistoryTable({ events, kind }) {
  if (events.length === 0) {
    return <div className="muted">还没有记录</div>;
  }
  return (
    <table className="sheet inner">
      <thead>
        <tr>
          <th>时间</th>
          <th>类型</th>
          <th>航班</th>
          <th>余票</th>
          <th>说明</th>
        </tr>
      </thead>
      <tbody>
        {events.map((event) => {
          const parsed = splitDetail(event.detail);
          return (
            <tr key={event.id}>
              <td>{clock(event.createdAt)}</td>
              <td>{kind(event)}</td>
              <td>
                <div className="stack">
                  {parsed.rows.map((row, index) => <span key={index}>{row.flight || '—'}</span>)}
                </div>
              </td>
              <td>
                <div className="stack">
                  {parsed.rows.map((row, index) => <StockCell key={index} row={row} />)}
                </div>
              </td>
              <td>
                <div className="stack">
                  {parsed.rows.map((row, index) => (
                    <span key={index}>
                      {row.explain}
                      {row.was && <> ，原余 <span className="stock-num">{stockToken(row.was)}</span></>}
                    </span>
                  ))}
                </div>
                {parsed.note && <div className="muted">{parsed.note}</div>}
              </td>
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
