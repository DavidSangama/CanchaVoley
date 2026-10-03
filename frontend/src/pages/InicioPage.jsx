import { useEffect, useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { Navbar } from '../components/Navbar';
import { CanchaService } from '../services/CanchaService';
import { HorarioService } from '../services/HorarioService';
import { iniciarSesionAdmin } from '../services/api';
import '../styles/InicioPage.css';

const getImageUrl = (name) => {
  return new URL(`../assets/${name}`, import.meta.url).href;
};

export default function InicioPage() {
  const navigate = useNavigate();
  const location = useLocation();
  const [canchas, setCanchas] = useState([]);
  const [horarios, setHorarios] = useState([]);
  const [cargandoDatos, setCargandoDatos] = useState(true);
  const [errorDatos, setErrorDatos] = useState('');

  // Estados para el modal de contacto y animación de salida
  const [modalContactoAbierto, setModalContactoAbierto] = useState(false);
  const [cerrandoModal, setCerrandoModal] = useState(false);

  // Estados para el login oculto de administrador
  const [mostrarLoginAdmin, setMostrarLoginAdmin] = useState(false);
  const [cerrandoLoginModal, setCerrandoLoginModal] = useState(false);
  const [loginAdmin, setLoginAdmin] = useState({ usuario: '', clave: '' });
  const [errorLoginAdmin, setErrorLoginAdmin] = useState('');
  const [verificandoLoginAdmin, setVerificandoLoginAdmin] = useState(false);
  const [mostrarClaveAdmin, setMostrarClaveAdmin] = useState(false);

  useEffect(() => {
    let cancelado = false;

    Promise.all([CanchaService.obtenerTodas(), HorarioService.obtenerTodos()])
      .then(([listaCanchas, listaHorarios]) => {
        if (cancelado) return;
        setCanchas(listaCanchas);
        setHorarios(listaHorarios);
      })
      .catch((error) => {
        console.error('Error al cargar canchas y horarios:', error);
        if (!cancelado) setErrorDatos('No se pudieron cargar las canchas y los precios. Intenta de nuevo más tarde.');
      })
      .finally(() => {
        if (!cancelado) setCargandoDatos(false);
      });

    return () => {
      cancelado = true;
    };
  }, []);

  useEffect(() => {
    if (!location.hash) return;
    if (location.hash === '#contacto') return;

    const id = location.hash.slice(1);
    requestAnimationFrame(() => {
      document.getElementById(id)?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    });
  }, [location.hash]);

  const irASeccion = (e, id) => {
    e.preventDefault();

    if (id === 'inicio') {
      window.scrollTo({ top: 0, behavior: 'smooth' });
      return;
    }

    const elem = document.getElementById(id);
    if (elem) {
      const elemPosition = elem.getBoundingClientRect().top + window.pageYOffset;
      window.scrollTo({
        top: elemPosition - (document.getElementById('header')?.offsetHeight || 0),
        behavior: 'smooth'
      });
    }
  };

  const abrirContacto = () => {
    setCerrandoModal(false);
    setModalContactoAbierto(true);
  };

  const cerrarContacto = () => {
    setCerrandoModal(true);
    setTimeout(() => {
      setModalContactoAbierto(false);
      setCerrandoModal(false);
      if (location.hash === '#contacto') navigate('/', { replace: true });
    }, 200);
  };

  const abrirLoginAdmin = () => {
    setLoginAdmin({ usuario: '', clave: '' });
    setErrorLoginAdmin('');
    setMostrarLoginAdmin(true);
  };

  const cerrarLoginAdmin = () => {
    setCerrandoLoginModal(true);
    setTimeout(() => {
      setMostrarLoginAdmin(false);
      setCerrandoLoginModal(false);
    }, 200);
  };

  const intentarLoginAdmin = async (e) => {
    e.preventDefault();
    setVerificandoLoginAdmin(true);
    setErrorLoginAdmin('');
    try {
      await iniciarSesionAdmin(loginAdmin.usuario.trim(), loginAdmin.clave);
      setMostrarLoginAdmin(false);
      navigate('/admin');
    } catch (error) {
      console.error('Error al autenticar administrador:', error);
      if (error.status === 401) {
        setErrorLoginAdmin('Usuario o contraseña incorrectos.');
      } else {
        setErrorLoginAdmin('No se pudo conectar con el servidor. Inténtalo de nuevo.');
      }
    } finally {
      setVerificandoLoginAdmin(false);
    }
  };

  const horariosOrdenados = [...horarios].sort((a, b) => a.hora.localeCompare(b.hora));
  const precioMinimo = horarios.length
    ? Math.min(...horarios.map((horario) => Number(horario.precio)))
    : null;
  const contactoAbierto = modalContactoAbierto || location.hash === '#contacto';

  return (
    <div className="inicio-container">
      <Navbar
        onContacto={abrirContacto}
        onLogoDoubleClick={abrirLoginAdmin}
        contactoAbierto={contactoAbierto}
      />

      <main>
        {/* HERO SECTION */}
        <section className="hero" id="inicio">
          <div className="container hero-in">
            <div className="hero-texto">
              <span className="etiqueta">Reserva en línea</span>
              <h1>Reserva tu cancha de vóley en minutos</h1>
              <p className="hero-sub">
                Elige la fecha, la cancha y tu horario. Confirma tu reserva sin llamadas ni esperas.
              </p>

              <div className="hero-botones">
                <Link className="btn btn-primario" to="/reservar">
                  Reservar ahora
                </Link>
                <a className="btn btn-secundario" href="#canchas" onClick={(e) => irASeccion(e, 'canchas')}>
                  Ver canchas
                </a>
              </div>

              <div className="datos">
                <div className="dato">
                  <b>{cargandoDatos ? '…' : canchas.length}</b>
                  <span>canchas</span>
                </div>
                <div className="dato">
                  <b>
                    {cargandoDatos || horariosOrdenados.length === 0
                      ? '—'
                      : `${horariosOrdenados[0].hora.slice(0, 5)} – ${horariosOrdenados.at(-1).hora.slice(0, 5)}`}
                  </b>
                  <span>horarios disponibles</span>
                </div>
                <div className="dato">
                  <b>{precioMinimo === null ? '—' : `Desde S/ ${precioMinimo}`}</b>
                  <span>por hora</span>
                </div>
              </div>
            </div>

            <div className="hero-foto">
              <img src={getImageUrl('hero-voley.jpg')} alt="Cancha principal de vóley" width="954" height="616" />
            </div>
          </div>
        </section>

        {/* CANCHAS SECTION */}
        <section className="canchas" id="canchas">
          <div className="container">
            <div className="seccion-titulo">
              <h2>Nuestras canchas</h2>
              <p>Desliza para ver todas y elige la tuya.</p>
            </div>

            {cargandoDatos && <p role="status">Cargando canchas…</p>}
            {errorDatos && <p className="estado-error" role="alert">{errorDatos}</p>}
            {!cargandoDatos && !errorDatos && (
              canchas.length ? (
                <div className="carrusel" tabIndex="0" aria-label="Lista de canchas">
                  {canchas.map((cancha) => (
                    <article className="cancha" key={cancha.idCancha}>
                      <div className="cancha-foto">
                        <img
                          src={getImageUrl(`Cancha ${cancha.numeroCancha}.png`)}
                          alt={`Cancha ${cancha.numeroCancha}`}
                          loading="lazy"
                          onError={(event) => { event.currentTarget.src = getImageUrl('hero-voley.jpg'); }}
                        />
                      </div>
                      <div className="cancha-detalle">
                        <h3>Cancha {cancha.numeroCancha}</h3>
                        <p>{precioMinimo === null ? 'Sin horarios disponibles' : `Desde S/ ${precioMinimo} por hora`}</p>
                        <Link className="btn btn-secundario" to={`/reservar?cancha=${cancha.idCancha}`}>
                          Reservar
                        </Link>
                      </div>
                    </article>
                  ))}
                </div>
              ) : <p>No hay canchas registradas por el momento.</p>
            )}
          </div>
        </section>

        {/* HORARIOS Y PRECIOS SECTION */}
        <section className="horarios" id="horarios">
          <div className="container">
            <div className="seccion-titulo">
              <h2>Horarios y precios</h2>
              <p>Precio por hora según el horario que elijas.</p>
            </div>

            <ul className="chips">
              {cargandoDatos && <li role="status">Cargando horarios…</li>}
              {!cargandoDatos && !errorDatos && horariosOrdenados.map((horario) => (
                <li className="chip" key={horario.idHorario}>
                  <b>{horario.hora.slice(0, 5)}</b>
                  <span>S/ {horario.precio}</span>
                </li>
              ))}
            </ul>
            {errorDatos && <p className="estado-error" role="alert">{errorDatos}</p>}
            {!cargandoDatos && !errorDatos && horariosOrdenados.length === 0 && (
              <p>No hay horarios registrados por el momento.</p>
            )}
          </div>
        </section>
      </main>

      {/* FOOTER */}
      <footer className="footer">
        <div className="container footer-in">
          <strong>CanchaVóley</strong>
          <small>© CanchaVóley · Reserva de canchas de vóley</small>
        </div>
      </footer>

      {/* MODAL CONTACTO */}
      {contactoAbierto && (
        <div
          className={`modal-overlay ${cerrandoModal ? 'salida' : ''}`}
          onClick={cerrarContacto}
        >
          <div
            className={`modal-contenido ${cerrandoModal ? 'salida' : ''}`}
            onClick={(e) => e.stopPropagation()}
          >
            <button
              className="modal-cerrar"
              type="button"
              onClick={cerrarContacto}
              aria-label="Cerrar modal"
            >
              ✕
            </button>

            <span className="etiqueta">Atención al cliente</span>
            <h2>Contacto y Soporte</h2>
            <p className="modal-sub">
              Para consultar, reprogramar o cancelar una solicitud, usa el enlace privado que recibiste al reservar.
            </p>

            <button
              className="btn btn-primario modal-btn"
              onClick={cerrarContacto}
            >
              Entendido
            </button>
          </div>
        </div>
      )}

      {/* MODAL LOGIN ADMIN (oculto, doble clic en el logo) */}
      {mostrarLoginAdmin && (
        <div
          className={`modal-overlay ${cerrandoLoginModal ? 'salida' : ''}`}
          onClick={cerrarLoginAdmin}
        >
          <div
            className={`modal-contenido ${cerrandoLoginModal ? 'salida' : ''}`}
            onClick={(e) => e.stopPropagation()}
          >
            <button
              className="modal-cerrar"
              type="button"
              onClick={cerrarLoginAdmin}
              aria-label="Cerrar modal"
            >
              ✕
            </button>

            <span className="etiqueta">Acceso restringido</span>
            <h2>Acceso administrador</h2>
            <p className="modal-sub">Ingresa tus credenciales para continuar.</p>

            <form onSubmit={intentarLoginAdmin} className="form-login-admin">
              <div className="campo-grupo">
                <label className="campo-label" htmlFor="admin-usuario">Usuario</label>
                <input
                  id="admin-usuario"
                  type="text"
                  className="campo-input"
                  autoComplete="username"
                  value={loginAdmin.usuario}
                  onChange={(e) => setLoginAdmin((prev) => ({ ...prev, usuario: e.target.value }))}
                  autoFocus
                />
              </div>

              <div className="campo-grupo">
                <label className="campo-label" htmlFor="admin-clave">Contraseña</label>
                <input
                  id="admin-clave"
                  type={mostrarClaveAdmin ? 'text' : 'password'}
                  className="campo-input"
                  autoComplete="current-password"
                  value={loginAdmin.clave}
                  onChange={(e) => setLoginAdmin((prev) => ({ ...prev, clave: e.target.value }))}
                />
                <button
                  type="button"
                  className="login-admin-mostrar-clave"
                  aria-pressed={mostrarClaveAdmin}
                  onClick={() => setMostrarClaveAdmin((mostrar) => !mostrar)}
                >
                  {mostrarClaveAdmin ? 'Ocultar contraseña' : 'Mostrar contraseña'}
                </button>
              </div>

              {errorLoginAdmin && <span className="campo-ayuda-error">{errorLoginAdmin}</span>}

              <button type="submit" className="btn btn-primario modal-btn" disabled={verificandoLoginAdmin}>
                {verificandoLoginAdmin ? 'Verificando…' : 'Ingresar'}
              </button>
            </form>
          </div>
        </div>
      )}
    </div>
  );
}