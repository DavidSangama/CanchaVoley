export function esHorarioPasado(fecha, hora, ahora = new Date()) {
  if (typeof fecha !== 'string' || typeof hora !== 'string') return false;

  const coincidencia = /^(\d{4})-(\d{2})-(\d{2})$/.exec(fecha);
  const partesHora = /^(\d{1,2}):(\d{2})(?::(\d{2})(?:\.\d+)?)?$/.exec(hora);
  if (!coincidencia || !partesHora) return false;

  const [anio, mes, dia] = coincidencia.slice(1).map(Number);
  const [horaInicio, minutoInicio, segundoInicio = '0'] = partesHora.slice(1);
  if (
    Number(horaInicio) > 23
    || Number(minutoInicio) > 59
    || Number(segundoInicio) > 59
  ) {
    return false;
  }
  const fechaTurno = new Date(anio, mes - 1, dia);
  if (
    fechaTurno.getFullYear() !== anio
    || fechaTurno.getMonth() !== mes - 1
    || fechaTurno.getDate() !== dia
  ) {
    return false;
  }

  const inicio = new Date(
    anio,
    mes - 1,
    dia,
    Number(horaInicio),
    Number(minutoInicio),
    Number(segundoInicio)
  );
  return inicio.getTime() <= ahora.getTime();
}
