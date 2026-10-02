import { useState } from "react";
import { Link, useLocation } from "react-router-dom";

export const Navbar = () => {
  const location = useLocation();
  const [menuAbierto, setMenuAbierto] = useState(false);

  const isActive = (path) => location.pathname === path;
  const cerrarMenu = () => setMenuAbierto(false);

  return (
    <header className="bg-white border-b border-slate-100 sticky top-0 z-50">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 h-16 md:h-20 flex items-center justify-between relative">
        {/* Logo */}
        <Link to="/" onClick={cerrarMenu} className="flex items-center gap-2 sm:gap-3 min-w-0">
          <div className="w-10 h-10 bg-blue-600 text-white font-black text-xl rounded-xl flex items-center justify-center shadow-md shadow-blue-500/20">
            V
          </div>
          <span className="text-base sm:text-xl font-extrabold text-slate-900 tracking-tight whitespace-nowrap">
            CanchaVóley
          </span>
        </Link>

        {/* Navegación Desktop */}
        <nav className={`${menuAbierto ? "flex" : "hidden"} md:flex flex-col md:flex-row items-stretch md:items-center gap-1 md:gap-8 text-sm font-medium text-slate-600 absolute md:static left-0 right-0 top-full md:top-auto bg-white md:bg-transparent border-b md:border-0 border-slate-100 shadow-lg md:shadow-none px-4 pb-3 md:p-0 z-50`}>
          <Link
            onClick={cerrarMenu}
            to="/"
            className={`rounded-lg px-3 py-3 md:px-0 md:py-0 transition-colors hover:text-blue-600 ${
              isActive("/") ? "text-slate-900 font-semibold" : ""
            }`}
          >
            Inicio
          </Link>
          <Link
            onClick={cerrarMenu}
            to="/canchas"
            className={`rounded-lg px-3 py-3 md:px-0 md:py-0 transition-colors hover:text-blue-600 ${
              isActive("/canchas") ? "text-slate-900 font-semibold" : ""
            }`}
          >
            Canchas
          </Link>
          <Link
            onClick={cerrarMenu}
            to="/horarios"
            className={`rounded-lg px-3 py-3 md:px-0 md:py-0 transition-colors hover:text-blue-600 ${
              isActive("/horarios") ? "text-slate-900 font-semibold" : ""
            }`}
          >
            Horarios
          </Link>
          <Link
            onClick={cerrarMenu}
            to="/contacto"
            className={`rounded-lg px-3 py-3 md:px-0 md:py-0 transition-colors hover:text-blue-600 ${
              isActive("/contacto") ? "text-slate-900 font-semibold" : ""
            }`}
          >
            Contacto
          </Link>
        </nav>

        {/* Botón Acción */}
        <Link
          onClick={cerrarMenu}
          to="/reserva"
          className="hidden md:inline-flex bg-blue-600 hover:bg-blue-700 text-white font-bold text-sm px-6 py-2.5 rounded-xl shadow-md shadow-blue-600/20 transition-all hover:shadow-lg"
        >
          Reservar
        </Link>
        <div className="flex items-center gap-2 md:hidden">
          <Link
            onClick={cerrarMenu}
            to="/reserva"
            className="rounded-lg bg-blue-600 px-3 py-2 text-xs font-bold text-white"
          >
            Reservar
          </Link>
          <button
            type="button"
            aria-label={menuAbierto ? "Cerrar menú" : "Abrir menú"}
            aria-expanded={menuAbierto}
            onClick={() => setMenuAbierto((abierto) => !abierto)}
            className="flex h-10 w-10 flex-col items-center justify-center gap-1.5 rounded-lg text-slate-700 hover:bg-slate-100"
          >
            <span className={`h-0.5 w-5 rounded bg-current transition-transform ${menuAbierto ? "translate-y-2 rotate-45" : ""}`} />
            <span className={`h-0.5 w-5 rounded bg-current transition-opacity ${menuAbierto ? "opacity-0" : ""}`} />
            <span className={`h-0.5 w-5 rounded bg-current transition-transform ${menuAbierto ? "-translate-y-2 -rotate-45" : ""}`} />
          </button>
        </div>
      </div>
    </header>
  );
};