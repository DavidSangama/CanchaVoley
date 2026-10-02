import React from 'react';
import { BrowserRouter, Routes, Route } from 'react-router-dom';
import InicioPage from './pages/InicioPage';
import { CanchasPage } from './pages/CanchasPage';
import { HorariosPage } from './pages/HorariosPage';
import { ContactoPage } from './pages/ContactoPage';
import ReservaPage from './pages/ReservaPage';
import { ConfirmacionPage } from './pages/ConfirmacionPage';
import { AdminPage } from './pages/AdminPage';
import { GestionReservaPage } from './pages/GestionReservaPage';

function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<InicioPage />} />
        <Route path="/canchas" element={<CanchasPage />} />
        <Route path="/horarios" element={<HorariosPage />} />
        <Route path="/contacto" element={<ContactoPage />} />
        <Route path="/reservar" element={<ReservaPage />} />
        <Route path="/confirmacion" element={<ConfirmacionPage />} />
        <Route path="/mis-reservas/:id" element={<GestionReservaPage />} />
        <Route path="/admin" element={<AdminPage />} />
      </Routes>
    </BrowserRouter>
  );
}

export default App;