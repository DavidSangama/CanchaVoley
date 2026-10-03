import { BrowserRouter, Navigate, Route, Routes } from 'react-router-dom';
import InicioPage from './pages/InicioPage';
import ReservaPage from './pages/ReservaPage';
import { AdminPage } from './pages/AdminPage';
import { GestionReservaPage } from './pages/GestionReservaPage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<InicioPage />} />
        <Route path="/canchas" element={<Navigate to="/#canchas" replace />} />
        <Route path="/horarios" element={<Navigate to="/#horarios" replace />} />
        <Route path="/contacto" element={<Navigate to="/#contacto" replace />} />
        <Route path="/reservar" element={<ReservaPage />} />
        <Route path="/mis-reservas/:id?" element={<GestionReservaPage />} />
        <Route path="/admin" element={<AdminPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;