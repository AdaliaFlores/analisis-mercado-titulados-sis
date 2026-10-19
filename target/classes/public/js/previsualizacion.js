// Autocontenido: no depende de utils.js y no crea variables globales
// (así no choca con components.js ni con scripts de otros módulos).
(() => {
  const estado = { encuestaId: null, pagina: 1, tamanio: 20, preguntas: [], envioActual: null };
  const byId = id => document.getElementById(id);
 
  async function getJson(url) {
    const res = await fetch(url);
    if (!res.ok) throw new Error((await res.text()) || `Error ${res.status}`);
    return res.json();
  }
 
  // Las respuestas son texto del usuario: escapar siempre antes de usar innerHTML
  function esc(texto) {
    return String(texto ?? '').replace(/[&<>"']/g, c => (
      { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]
    ));
  }
 
  function notificarError(e) {
    console.error(e);
    const msg = e.message || 'No se pudo cargar la información.';
    if (typeof mostrarNotificacion === 'function') mostrarNotificacion('Error', msg, false);
    else alert(msg);
  }
 
  async function init() {
    const select = byId('selectEncuesta');
    try {
      const encuestas = await getJson('/api/encuestas');
      select.innerHTML =
        '<option value="">-- Selecciona una encuesta --</option>' +
        encuestas.map(e => `<option value="${e.id}">${esc(e.titulo)} (${esc(e.destinatario)})</option>`).join('');
    } catch (e) {
      select.innerHTML = `<option value="">Error al cargar encuestas: ${esc(e.message)}</option>`;
      notificarError(e);
    }
  }
 
  async function seleccionarEncuesta() {
    const id = parseInt(byId('selectEncuesta').value);
    if (!id) { byId('panelDatos').classList.add('d-none'); return; }
    estado.encuestaId = id;
    estado.pagina = 1;
    try {
      estado.preguntas = await getJson(`/api/encuestas/${id}/preguntas`);
      renderCabecera();
      await cargarPagina();
      byId('panelDatos').classList.remove('d-none');
    } catch (e) { notificarError(e); }
  }
 
  function cambiarTamanio() {
    estado.tamanio = parseInt(byId('selectTamanio').value);
    estado.pagina = 1;
    if (estado.encuestaId) cargarPagina().catch(notificarError);
  }
 
  function irAPagina(delta) {
    estado.pagina += delta;
    cargarPagina().catch(notificarError);
  }
 
  async function cargarPagina() {
    const data = await getJson(
      `/api/encuestas/${estado.encuestaId}/envios?pagina=${estado.pagina}&tamanio=${estado.tamanio}`);
    renderFilas(data);
    renderPaginacion(data);
  }
 
  function renderCabecera() {
    const cols = estado.preguntas.map((p, i) =>
      `<th title="${esc(p.textoPregunta)}">P${i + 1}</th>`).join('');
    document.querySelector('#tablaDatos thead').innerHTML =
      `<tr><th>#</th><th>Fecha</th>${cols}<th></th></tr>`;
  }
 
  function renderFilas(data) {
    const tbody = document.querySelector('#tablaDatos tbody');
    const columnas = estado.preguntas.length + 3;
 
    if (data.items.length === 0) {
      tbody.innerHTML = `<tr><td colspan="${columnas}" class="text-center text-muted py-4">
        Aún no hay respuestas para esta encuesta.</td></tr>`;
      return;
    }
 
    const inicio = (data.pagina - 1) * data.tamanio;
    tbody.innerHTML = data.items.map((f, i) => {
      const celdas = estado.preguntas.map(p => {
        const valor = f.respuestas[p.id];
        return valor
          ? `<td class="celda text-truncate" title="${esc(valor)}">${esc(valor)}</td>`
          : '<td class="text-muted text-center">—</td>';
      }).join('');
      const fecha = f.fecha ? new Date(f.fecha).toLocaleString('es-BO') : '—';
      return `<tr>
        <td>${inicio + i + 1}</td>
        <td class="text-nowrap">${esc(fecha)}</td>
        ${celdas}
        <td><button class="btn btn-outline-primary btn-sm btn-detalle" data-envio="${esc(f.envioId)}">Ver</button></td>
      </tr>`;
    }).join('');
  }
 
  function renderPaginacion(data) {
    const desde = data.total === 0 ? 0 : (data.pagina - 1) * data.tamanio + 1;
    const hasta = Math.min(data.pagina * data.tamanio, data.total);
    byId('resumenPaginacion').textContent =
      `Mostrando ${desde}–${hasta} de ${data.total} envíos · Página ${data.pagina} de ${Math.max(1, data.totalPaginas)}`;
    byId('btnAnterior').disabled = data.pagina <= 1;
    byId('btnSiguiente').disabled = data.pagina >= data.totalPaginas;
  }
 
  // ---------- Detalle de un envío + corrección de respuestas de texto ----------
 
  function filaDetalle(d) {
    const pista = d.editable && d.formato === 'NUMERO'
      ? '<div class="form-text">Se espera un número</div>' : '';
    const accion = d.editable
      ? `<button class="btn btn-outline-warning btn-sm btn-corregir">Corregir</button>` : '';
    return `<tr data-id="${d.id}">
      <th class="w-50 fw-semibold">${esc(d.textoPregunta)}${pista}</th>
      <td class="valor">${esc(d.respuesta)}</td>
      <td class="text-end" style="width:110px">${accion}</td>
    </tr>`;
  }
 
  function tablaAuditoria(registros) {
    if (registros.length === 0) return '';
    return `<h6 class="fw-bold text-primary mt-4">Historial de correcciones</h6>
      <div class="table-responsive"><table class="table table-sm table-bordered mb-0">
        <thead class="table-light"><tr>
          <th>Fecha</th><th>Pregunta</th><th>Antes</th><th>Después</th><th>Motivo</th></tr></thead>
        <tbody>${registros.map(a => `<tr>
          <td class="text-nowrap">${esc(a.fecha ? new Date(a.fecha).toLocaleString('es-BO') : '—')}</td>
          <td>${esc(a.textoPregunta)}</td>
          <td class="text-danger">${esc(a.valorAnterior)}</td>
          <td class="text-success">${esc(a.valorNuevo)}</td>
          <td>${esc(a.motivo)}</td></tr>`).join('')}
        </tbody></table></div>`;
  }
 
  async function verDetalle(envioId) {
    try {
      estado.envioActual = envioId;
      const [detalle, auditoria] = await Promise.all([
        getJson(`/api/envios/${envioId}`),
        getJson(`/api/envios/${envioId}/auditoria`).catch(() => [])
      ]);
      byId('cuerpoDetalle').innerHTML = detalle.length === 0
        ? '<p class="text-muted mb-0">Este envío no tiene respuestas registradas.</p>'
        : `<table class="table table-sm table-bordered mb-0"><tbody>${detalle.map(filaDetalle).join('')}</tbody></table>`
          + tablaAuditoria(auditoria);
      bootstrap.Modal.getOrCreateInstance(byId('modalDetalle')).show();
    } catch (e) { notificarError(e); }
  }
 
  function abrirEdicion(fila) {
    const td = fila.querySelector('.valor');
    fila.dataset.original = td.textContent;
    td.innerHTML = `
      <input class="form-control form-control-sm mb-1 in-valor" value="${esc(td.textContent)}">
      <input class="form-control form-control-sm mb-1 in-motivo" maxlength="200"
             placeholder="Motivo de la corrección (obligatorio)">
      <div class="text-danger small mb-1 d-none msg-error"></div>
      <button class="btn btn-success btn-sm btn-guardar">Guardar</button>
      <button class="btn btn-outline-secondary btn-sm btn-cancelar">Cancelar</button>`;
    fila.querySelector('.btn-corregir').classList.add('d-none');
    fila.querySelector('.in-valor').focus();
  }
 
  function cerrarEdicion(fila) {
    fila.querySelector('.valor').textContent = fila.dataset.original;
    fila.querySelector('.btn-corregir').classList.remove('d-none');
  }
 
  async function guardarEdicion(fila) {
    const msg = fila.querySelector('.msg-error');
    const boton = fila.querySelector('.btn-guardar');
    msg.classList.add('d-none');
    boton.disabled = true;
    try {
      const res = await fetch(`/api/respuestas/${fila.dataset.id}`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          valor: fila.querySelector('.in-valor').value,
          motivo: fila.querySelector('.in-motivo').value
        })
      });
      if (!res.ok) throw new Error((await res.text()) || `Error ${res.status}`);
      await verDetalle(estado.envioActual);   // recarga el detalle con el historial
      await cargarPagina();                   // y la tabla de fondo
    } catch (e) {
      msg.textContent = e.message;
      msg.classList.remove('d-none');
      boton.disabled = false;
    }
  }
 
  // ---------- Eventos (sin atributos onclick/onchange en el HTML) ----------
  byId('selectEncuesta').addEventListener('change', seleccionarEncuesta);
  byId('selectTamanio').addEventListener('change', cambiarTamanio);
  byId('btnAnterior').addEventListener('click', () => irAPagina(-1));
  byId('btnSiguiente').addEventListener('click', () => irAPagina(1));
 
  document.querySelector('#tablaDatos tbody').addEventListener('click', e => {
    const btn = e.target.closest('.btn-detalle');
    if (btn) verDetalle(btn.dataset.envio);
  });
 
  byId('cuerpoDetalle').addEventListener('click', e => {
    const fila = e.target.closest('tr[data-id]');
    if (!fila) return;
    if (e.target.closest('.btn-corregir')) abrirEdicion(fila);
    else if (e.target.closest('.btn-cancelar')) cerrarEdicion(fila);
    else if (e.target.closest('.btn-guardar')) guardarEdicion(fila);
  });
  init();
})();