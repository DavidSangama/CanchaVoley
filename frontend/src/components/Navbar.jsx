import { useEffect, useState } from 'react';
import { Link, useLocation } from 'react-router-dom';
import '../styles/Navbar.css';

const SECCIONES = ['inicio', 'canchas', 'horarios'];

export const Navbar = ({ onContacto, onLogoDoubleClick }) => {
  const location = useLocation();
  const [menuAbierto, setMenuAbierto] = useState(false);
  const [conSombra, setConSombra] = useState(false);
  const [seccionActiva, setSeccionActiva] = useState('inicio');
  const esInicio = location.pathname === '/';

  useEffect(() => {
    const alScroll = () => {
      setConSombra(window.scrollY > 8);
      if (!esInicio) return;

      const posicion = window.scrollY + (document.getElementById('header')?.offsetHeight || 0) + 100;
      const seccion = [...SECCIONES]
        .reverse()
        .find((id) => document.getElementById(id)?.offsetTop <= posicion);
      if (seccion) setSeccionActiva(seccion);
    };

    window.addEventListener('scroll', alScroll, { passive: true });
    alScroll();
    return () => window.removeEventListener('scroll', alScroll);
  }, [esInicio]);

  const cerrarMenu = () => setMenuAbierto(false);
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
      className={`enlace ${seccionActiva === id && esInicio ? 'activo' : ''}`}
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
          onClick={cerrarMenu}
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
          <Link className="enlace enlace-boton" to="/#contacto" onClick={manejarContacto}>
            Contacto
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
