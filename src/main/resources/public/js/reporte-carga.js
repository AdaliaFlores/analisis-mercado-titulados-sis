/*
 * Utilidades compartidas por los reportes del dashboard.
 *
 * Centraliza tres cosas para que todos los modales se comporten igual:
 *   - el estado visual de carga mientras se espera la respuesta,
 *   - un tiempo máximo de espera para el fetch,
 *   - un mensaje de error amigable (sin detalles técnicos) con botón "Reintentar".
 *
 * Neon puede tardar varios segundos en despertar; el backend ya reintenta la
 * conexión JDBC, por eso el cliente espera más que ese tiempo de conexión.
 */
(function () {
  'use strict';

  const TIEMPO_MAX_ESPERA_MS = 60000;

  const MENSAJE_REINTENTABLE =
    'No se pudo obtener la información del reporte en este momento. ' +
    'La base de datos puede estar iniciándose; por favor intenta nuevamente.';

  const MENSAJE_SESION =
    'Tu sesión expiró. Vuelve a iniciar sesión para consultar los reportes.';

  class ErrorCargaReporte extends Error {
    constructor(mensaje, tipo, reintentable) {
      super(mensaje);
      this.name = 'ErrorCargaReporte';
      this.tipo = tipo;
      this.reintentable = reintentable !== false;
    }
  }

  /**
   * Realiza un GET y devuelve el JSON. Cualquier fallo (red, timeout, 5xx,
   * respuesta no JSON) se transforma en un ErrorCargaReporte con un mensaje
   * apto para mostrarse al usuario.
   */
  async function solicitarJson(url, opciones) {
    const config = Object.assign({}, opciones || {});
    const timeoutMs = config.timeoutMs || TIEMPO_MAX_ESPERA_MS;
    delete config.timeoutMs;

    const controlador = new AbortController();
    const temporizador = setTimeout(() => controlador.abort(), timeoutMs);

    let respuesta;
    try {
      respuesta = await fetch(url, Object.assign({}, config, {
        signal: controlador.signal
      }));
    } catch (error) {
      if (error && error.name === 'AbortError') {
        throw new ErrorCargaReporte(
          'El servidor está tardando más de lo esperado en responder. ' +
          'La base de datos puede estar iniciándose; por favor intenta nuevamente.',
          'tiempo',
          true
        );
      }
      throw new ErrorCargaReporte(MENSAJE_REINTENTABLE, 'red', true);
    } finally {
      clearTimeout(temporizador);
    }

    if (respuesta.status === 401) {
      throw new ErrorCargaReporte(MENSAJE_SESION, 'sesion', false);
    }

    if (!respuesta.ok) {
      // 500, 503, 504, etc. No exponemos el cuerpo técnico del servidor.
      throw new ErrorCargaReporte(MENSAJE_REINTENTABLE, 'servidor', true);
    }

    try {
      return await respuesta.json();
    } catch (error) {
      throw new ErrorCargaReporte(MENSAJE_REINTENTABLE, 'datos', true);
    }
  }

  function escapar(valor) {
    return String(valor == null ? '' : valor)
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;')
      .replaceAll("'", '&#39;');
  }

  function esFila(contenedor) {
    return !!(contenedor && contenedor.classList
      && contenedor.classList.contains('row'));
  }

  /** Envuelve el nodo en una columna si el contenedor es una fila Bootstrap. */
  function envolver(contenedor, nodo) {
    if (!esFila(contenedor)) return nodo;
    const columna = document.createElement('div');
    columna.className = 'col-12';
    columna.appendChild(nodo);
    return columna;
  }

  /** Reemplaza el contenido del contenedor por un estado de carga limpio. */
  function mostrarCargando(contenedor, texto, color) {
    if (!contenedor) return;
    const clase = esFila(contenedor)
      ? 'col-12 text-center py-5'
      : 'text-center py-5';
    contenedor.innerHTML = `
      <div class="${clase}">
        <div class="spinner-border text-${color || 'primary'} mb-3"
             role="status" aria-hidden="true"></div>
        <p class="text-muted mb-0">${escapar(texto || 'Cargando datos del reporte...')}</p>
      </div>`;
  }

  /**
   * Crea un aviso (con botón Reintentar opcional) listo para insertarse.
   * El botón queda deshabilitado mientras se ejecuta la recarga.
   */
  function crearAviso(contenedor, opciones) {
    const opt = opciones || {};
    const tipo = opt.tipo || 'warning';
    const mensaje = opt.mensaje || MENSAJE_REINTENTABLE;

    let accion = '';
    if (opt.alReintentar) {
      const claseBoton = tipo === 'danger' ? 'btn-danger' : 'btn-warning';
      accion = `
        <button type="button" class="btn btn-sm ${claseBoton} fw-semibold mt-3">
          <span class="spinner-border spinner-border-sm me-2 d-none"
                role="status" aria-hidden="true"></span>
          Reintentar
        </button>`;
    } else if (opt.mostrarLogin) {
      accion = '<a class="btn btn-sm btn-primary fw-semibold mt-3" href="/login.html">Iniciar sesión</a>';
    }

    const icono = opt.mostrarLogin ? '🔒' : '⚠';

    const alerta = document.createElement('div');
    alerta.className = `alert alert-${tipo} border mb-0`;
    alerta.setAttribute('role', 'alert');
    alerta.innerHTML = `
      <div class="d-flex align-items-start gap-2">
        <div class="fs-5 lh-1">${icono}</div>
        <div class="flex-grow-1">
          <strong>${escapar(opt.titulo || 'No se pudo cargar el reporte')}</strong>
          <div class="small mt-1">${escapar(mensaje)}</div>
          ${accion}
        </div>
      </div>`;

    if (opt.alReintentar) {
      const boton = alerta.querySelector('button');
      if (boton) {
        boton.addEventListener('click', async () => {
          boton.disabled = true;
          const spinner = boton.querySelector('.spinner-border');
          if (spinner) spinner.classList.remove('d-none');
          try {
            await opt.alReintentar();
          } finally {
            // Si la vista se reemplazó por el estado de carga, el botón ya no
            // existe; solo lo restauramos cuando el aviso sigue visible.
            if (document.body.contains(boton)) {
              boton.disabled = false;
              if (spinner) spinner.classList.add('d-none');
            }
          }
        });
      }
    }

    return envolver(contenedor, alerta);
  }

  /**
   * Muestra un error amigable dentro del contenedor del reporte.
   * Nunca imprime mensajes técnicos del servidor.
   */
  function mostrarError(contenedor, opciones) {
    const opt = opciones || {};
    const error = opt.error;
    const esErrorConocido = error instanceof ErrorCargaReporte;
    const esSesion = !!(error && error.tipo === 'sesion');

    let mensaje = opt.mensaje;
    if (!mensaje) {
      mensaje = esErrorConocido ? error.message : MENSAJE_REINTENTABLE;
    }

    let alReintentar = opt.alReintentar;
    if (error && error.reintentable === false) {
      alReintentar = null;
    }

    if (error) {
      // Queda en la consola del desarrollador, no en la interfaz.
      console.error('[Reporte] Error de carga:', error);
    }

    if (!contenedor) return;
    contenedor.innerHTML = '';
    contenedor.appendChild(crearAviso(contenedor, {
      tipo: opt.tipo || 'warning',
      titulo: opt.titulo,
      mensaje: mensaje,
      alReintentar: alReintentar,
      mostrarLogin: esSesion
    }));
  }

  window.ReporteCarga = {
    TIEMPO_MAX_ESPERA_MS: TIEMPO_MAX_ESPERA_MS,
    MENSAJE_REINTENTABLE: MENSAJE_REINTENTABLE,
    ErrorCargaReporte: ErrorCargaReporte,
    solicitarJson: solicitarJson,
    mostrarCargando: mostrarCargando,
    crearAviso: crearAviso,
    mostrarError: mostrarError
  };
})();
