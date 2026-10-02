import { useEffect, useId, useRef, useState } from 'react';
import './SelectEstilizado.css';

export function SelectEstilizado({
  ariaLabel,
  className = '',
  disabled = false,
  onChange,
  options,
  placeholder,
  value,
}) {
  const id = useId();
  const rootRef = useRef(null);
  const buttonRef = useRef(null);
  const selectedIndex = options.findIndex((option) => String(option.value) === String(value));
  const [abierto, setAbierto] = useState(false);
  const [indiceActivo, setIndiceActivo] = useState(Math.max(selectedIndex, 0));
  const opcionSeleccionada = selectedIndex >= 0 ? options[selectedIndex] : null;

  useEffect(() => {
    const cerrarAlHacerClickFuera = (event) => {
      if (rootRef.current && !rootRef.current.contains(event.target)) setAbierto(false);
    };
    document.addEventListener('pointerdown', cerrarAlHacerClickFuera);
    return () => document.removeEventListener('pointerdown', cerrarAlHacerClickFuera);
  }, []);

  const seleccionar = (option) => {
    onChange(option.value);
    setAbierto(false);
    buttonRef.current?.focus();
  };

  const manejarTeclado = (event) => {
    if (disabled) return;
    if (event.key === 'Escape') {
      setAbierto(false);
      buttonRef.current?.focus();
      return;
    }
    if (event.key === 'ArrowDown' || event.key === 'ArrowUp') {
      event.preventDefault();
      const desplazamiento = event.key === 'ArrowDown' ? 1 : -1;
      if (!abierto) {
        setIndiceActivo(selectedIndex >= 0 ? selectedIndex : 0);
        setAbierto(true);
      } else if (options.length > 0) {
        setIndiceActivo((indiceActual) => (
          (indiceActual + desplazamiento + options.length) % options.length
        ));
      }
      return;
    }
    if ((event.key === 'Enter' || event.key === ' ') && abierto && options[indiceActivo]) {
      event.preventDefault();
      seleccionar(options[indiceActivo]);
    }
  };

  return (
    <div
      className={`select-estilizado ${className}`}
      ref={rootRef}
      onBlur={(event) => {
        if (!event.currentTarget.contains(event.relatedTarget)) setAbierto(false);
      }}
    >
      <button
        ref={buttonRef}
        type="button"
        className="campo-input select-estilizado-boton"
        role="combobox"
        aria-label={ariaLabel}
        aria-expanded={abierto}
        aria-controls={`${id}-opciones`}
        aria-haspopup="listbox"
        aria-activedescendant={abierto
          ? (options[indiceActivo] ? `${id}-opcion-${indiceActivo}` : `${id}-opcion-placeholder`)
          : undefined}
        disabled={disabled}
        onClick={() => {
          setIndiceActivo(selectedIndex >= 0 ? selectedIndex : 0);
          setAbierto((actual) => !actual);
        }}
        onKeyDown={manejarTeclado}
      >
        <span className={opcionSeleccionada ? '' : 'select-estilizado-placeholder'}>
          {opcionSeleccionada?.label || placeholder}
        </span>
        <svg aria-hidden="true" viewBox="0 0 20 20">
          <path d="m5 7.5 5 5 5-5" />
        </svg>
      </button>
      {abierto && (
        <div id={`${id}-opciones`} className="select-estilizado-lista" role="listbox" aria-label={ariaLabel}>
          {placeholder && (
            <button
              id={`${id}-opcion-placeholder`}
              type="button"
              className={`select-estilizado-opcion ${!opcionSeleccionada ? 'seleccionada' : ''}`}
              role="option"
              aria-selected={!opcionSeleccionada}
              tabIndex={-1}
              onClick={() => seleccionar({ value: '', label: placeholder })}
            >
              {placeholder}
            </button>
          )}
          {options.map((option, index) => (
            <button
              id={`${id}-opcion-${index}`}
              key={String(option.value)}
              type="button"
              className={`select-estilizado-opcion ${index === indiceActivo ? 'activa' : ''} ${String(option.value) === String(value) ? 'seleccionada' : ''}`}
              role="option"
              aria-selected={String(option.value) === String(value)}
              tabIndex={-1}
              onMouseEnter={() => setIndiceActivo(index)}
              onClick={() => seleccionar(option)}
            >
              <span>{option.label}</span>
              {String(option.value) === String(value) && <span className="select-estilizado-check" aria-hidden="true">✓</span>}
            </button>
          ))}
        </div>
      )}
    </div>
  );
}
