import { useEffect, useState } from 'react';
import { Navigate, Route, Routes, useNavigate } from 'react-router-dom';
import { api, getToken } from './api';
import Auth from './Auth';
import Monitors from './Monitors';
import Admin from './Admin';
import Mail from './Mail';

function Home() {
  const navigate = useNavigate();
  const [ready, setReady] = useState(false);

  useEffect(() => {
    if (!getToken()) {
      navigate('/login');
      return;
    }
    api('/api/auth/me').then((result) => {
      if (!result.success) {
        navigate('/login');
        return;
      }
      navigate(result.data.admin ? '/admin' : '/monitors');
      setReady(true);
    });
  }, [navigate]);

  return ready ? null : <p className="page muted">加载中</p>;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<Auth mode="login" />} />
      <Route path="/register" element={<Auth mode="register" />} />
      <Route path="/monitors" element={<Monitors />} />
      <Route path="/mail" element={<Mail />} />
      <Route path="/admin" element={<Admin />} />
      <Route path="/" element={<Home />} />
      <Route path="*" element={<Navigate to="/" />} />
    </Routes>
  );
}
