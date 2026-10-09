import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import {
  obtenerTokenGestionGuardado,
  TOKEN_GESTION_UPDATED_EVENT,
} from '../services/gestionReservaSession';
import '../styles/Navbar.css';

const SECCIONES = ['inicio', 'canchas', 'horarios'];

export const Navbar = ({ onContacto, onLogoDoubleClick, contactoAbierto = false }) => {
  const location = useLocation();
  const navigate = useNavigate();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [conSombra, setConSombra] = useState(false);
  const [seccionActiva, setSeccionActiva] = useState('inicio');
  const [tokenGestion, setTokenGestion] = useState(obtenerTokenGestionGuardado);
  const esInicio = location.pathname === '/';

  useEffect(() => {
    const actualizarToken = () => setTokenGestion(obtenerTokenGestionGuardado());
    window.addEventListener(TOKEN_GESTION_UPDATED_EVENT, actualizarToken);
    return () => window.removeEventListener(TOKEN_GESTION_UPDATED_EVENT, actualizarToken);
  }, []);

  useEffect(() => {
    const alScroll = () => {
      setConSombra(window.scrollY > 8);
      if (!esInicio) return;

      const headerHeight = document.getElementById('header')?.offsetHeight || 0;
      const activationLine = headerHeight + Math.min(window.innerHeight * 0.55, 360);
      const seccion = window.innerHeight + window.scrollY >= document.documentElement.scrollHeight - 2
        ? SECCIONES.at(-1)
        : [...SECCIONES]
          .reverse()
          .find((id) => document.getElementById(id)?.getBoundingClientRect().top <= activationLine);
      setSeccionActiva(seccion || SECCIONES[0]);
    };

    window.addEventListener('scroll', alScroll, { passive: true });
    alScroll();
    return () => window.removeEventListener('scroll', alScroll);
  }, [esInicio]);

  const cerrarMenu = () => setMenuAbierto(false);
  const contactoActivo = contactoAbierto || location.hash === '#contacto';

  const manejarLogo = (event) => {
    cerrarMenu();
    if (!esInicio) {
      event.preventDefault();
      navigate('/');
      return;
    }

    event.preventDefault();
    if (location.hash) navigate('/', { replace: true });
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  const manejarSeccion = (event, id) => {
    cerrarMenu();
    if (!esInicio || id === 'contacto') return;

    const elemento = document.getElementById(id);
    if (!elemento) return;
    event.preventDefault();
    const alturaHeader = document.getElementById('header')?.offsetHeight || 0;
    const posicion = elemento.getBoundingClientRect().top + window.scrollY - alturaHeader;
    window.scrollTo({ top: posicion, behavior: 'smooth' });
  };

  const manejarContacto = (event) => {
    cerrarMenu();
    if (!esInicio || !onContacto) return;
    event.preventDefault();
    onContacto();
  };


  const enlaceSeccion = (id, texto) => (
    <Link
      key={id}
      className={`enlace ${seccionActiva === id && esInicio && !contactoActivo ? 'activo' : ''}`}
      to={`/#${id}`}
      onClick={(event) => manejarSeccion(event, id)}
    >
      {texto}
    </Link>
  );

  return (
    <header className={`header ${conSombra ? 'con-sombra' : ''}`} id="header">
      <div className="container header-in">
        <Link
          to="/"
          className="marca"
          onClick={manejarLogo}
          onDoubleClick={onLogoDoubleClick}
          aria-label="CanchaVóley, ir al inicio"
        >
          <span className="logo">V</span>
          <span className="marca-nombre">CanchaVóley</span>
        </Link>

        <nav className={`menu ${menuAbierto ? 'abierto' : ''}`} aria-label="Principal">
          {enlaceSeccion('inicio', 'Inicio')}
          {enlaceSeccion('canchas', 'Canchas')}
          {enlaceSeccion('horarios', 'Horarios')}
          <Link
            className={`enlace enlace-boton ${contactoActivo ? 'activo' : ''}`}
            to="/#contacto"
            onClick={manejarContacto}
          >
            Contacto
          </Link>
          <Link
            className={`enlace ${location.pathname === '/mis-reservas' ? 'activo' : ''}`}
            to={tokenGestion ? `/mis-reservas#${tokenGestion}` : '/mis-reservas'}
            onClick={cerrarMenu}
          >
            Mis reservas
          </Link>
          <Link className="btn btn-primario menu-cta" to="/reservar" onClick={cerrarMenu}>
            Reservar
          </Link>
        </nav>

        <Link className="btn btn-primario header-cta" to="/reservar" onClick={cerrarMenu}>
          Reservar
        </Link>

        <button
          className={`hamburguesa ${menuAbierto ? 'abierta' : ''}`}
          type="button"
          aria-label={menuAbierto ? 'Cerrar menú' : 'Abrir menú'}
          aria-expanded={menuAbierto}
          onClick={() => setMenuAbierto((abierto) => !abierto)}
        >
          <span></span>
          <span></span>
          <span></span>
        </button>
      </div>
    </header>
  );
};
