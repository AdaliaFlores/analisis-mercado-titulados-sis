// HU11: Pertinencia curricular, geografía y fuga de talentos.
// Encapsulado: no crea variables globales (solo cargarReporteUbicacion) para no chocar con otros reportes.
(() => {
  // ---- Configuración: IDs de las preguntas de la encuesta de titulados ----
  const CFG = {
    encuestaId: 1,
    p: { departamento: 19, sector: 18, rubro: 16, cargo: 22, pertinencia: 24, salario: 27 },
    grupoBase: 'Cochabamba',      // nombres de grupo definidos en agrupacion_opcion (pregunta 19)
    grupoExtranjero: 'Extranjero',
    umbralN: 5                    // debe coincidir con MIN_N_GRUPO del backend
  };
  const COLORES = ['#0d6efd', '#198754', '#ffc107', '#dc3545', '#6f42c1', '#0dcaf0', '#fd7e14', '#20c997', '#6c757d'];

  const graficos = {};
  let version = 0;          // evita que una respuesta lenta pise a una más reciente
  let filtrosListos = false;

  const $ = id => document.getElementById(id);

  function esc(texto) {
    return String(texto ?? '').replace(/[&<>"']/g, c => (
      { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
  }
  const num = (v, d = 2) => v == null ? '—'
    : Number(v).toLocaleString('es-BO', { minimumFractionDigits: d, maximumFractionDigits: d });
  const fmtP = p => p == null ? '—' : (p < 0.0001 ? '< 0.0001' : p.toFixed(4));
  function normalizarTexto(texto) {
    return String(texto ?? '')
      .trim()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLocaleLowerCase('es');
  }
  async function api(url) {
    const res = await fetch(url);
    if (!res.ok) throw new Error((await res.text()) || `Error ${res.status}`);
    return res.json();
  }

  function dibujar(id, config) {
    if (graficos[id]) graficos[id].destroy();
    graficos[id] = new Chart($(id), config);
  }
  function limpiarGraficos() {
    Object.keys(graficos).forEach(k => { graficos[k].destroy(); delete graficos[k]; });
  }

  // ---------- Filtros combinados ----------
  function filtrosActuales() {
    const f = [];
    const sector = $('uFiltroSector')?.value;
    const rubro = $('uFiltroRubro')?.value;
    if (sector) f.push([CFG.p.sector, sector]);
    if (rubro) f.push([CFG.p.rubro, rubro]);
    return f;
  }
  const qsFiltros = f => f.map(([p, v], i) => `&fp${i + 1}=${p}&fv${i + 1}=${encodeURIComponent(v)}`).join('');

  async function prepararFiltros() {
    const preguntas = await api(`/api/encuestas/${CFG.encuestaId}/preguntas`);
    const opcionesDe = id => {
      const q = preguntas.find(p => p.id === id);
      try { return q && q.opciones ? JSON.parse(q.opciones) : []; } catch { return []; }
    };
    const select = (id, etiqueta, opciones) => `
      <div class="col-md-4">
        <label class="form-label small fw-bold">${etiqueta}</label>
        <select id="${id}" class="form-select form-select-sm">
          <option value="">Todos</option>
          ${opciones.map(o => `<option value="${esc(o)}">${esc(o)}</option>`).join('')}
        </select>
      </div>`;
    $('filtrosUbicacion').innerHTML =
      select('uFiltroSector', 'Sector', opcionesDe(CFG.p.sector)) +
      select('uFiltroRubro', 'Rubro de la organización', opcionesDe(CFG.p.rubro)) +
      `<div class="col-md-4 d-flex align-items-end">
         <div class="small text-muted">Los filtros se combinan: solo se consideran los titulados que cumplen ambos.</div>
       </div>`;
    $('uFiltroSector').addEventListener('change', actualizar);
    $('uFiltroRubro').addEventListener('change', actualizar);
  }

  // ---------- Piezas reutilizables ----------
  const carta = (titulo, nota, cuerpo) => `
    <div class="card border-0 shadow-sm"><div class="card-body">
      <h6 class="fw-bold text-primary mb-1">${esc(titulo)}</h6>
      ${nota ? `<p class="text-muted small mb-3">${nota}</p>` : '<div class="mb-3"></div>'}
      ${cuerpo}
    </div></div>`;

  const sinDatos = (titulo, nota) => ({
    html: carta(titulo, nota, '<p class="text-muted mb-0">Sin datos para este filtro.</p>')
  });

  function tabla(filas, cols, matriz, o = {}) {
    const d = o.dec ?? 0;
    const cabecera = cols.map(c => `<th>${esc(c)}</th>`).join('');
    const cuerpo = filas.map((f, i) => `<tr>
        <th class="text-start">${esc(f)}</th>
        ${matriz[i].map(v => `<td>${num(v, d)}</td>`).join('')}
        ${o.totalesFila ? `<td class="table-secondary fw-bold">${o.totalesFila[i]}</td>` : ''}
      </tr>`).join('');
    const pie = o.totalesColumna ? `<tr class="table-secondary fw-bold"><th class="text-start">Total</th>
        ${o.totalesColumna.map(v => `<td>${v}</td>`).join('')}<td>${o.n}</td></tr>` : '';
    return `<div class="table-responsive"><table class="table table-bordered table-sm text-center align-middle mb-3">
        <thead class="table-light"><tr><th class="text-start">${esc(o.esquina || '')}</th>${cabecera}
        ${o.totalesFila ? '<th class="table-secondary">Total</th>' : ''}</tr></thead>
        <tbody>${cuerpo}${pie}</tbody></table></div>`;
  }

  // Ejecuta una sección: muestra "Cargando", la dibuja y aísla sus errores
  async function correr(id, mi, fn) {
    $(id).innerHTML = '<p class="text-muted small">Cargando…</p>';
    try {
      const r = await fn();
      if (mi !== version) return;
      $(id).innerHTML = r.html;
      if (r.dibujar) r.dibujar();
    } catch (e) {
      if (mi === version)
        $(id).innerHTML = `<div class="alert alert-danger mb-0">No se pudo cargar esta sección: ${esc(e.message)}</div>`;
    }
  }

  // ---------- Secciones ----------
  async function kpis(qs) {
    const g = await api(`/api/analisis/frecuencias?pregunta=${CFG.p.departamento}&agrupada=true${qs}`);
    const total = g.reduce((s, x) => s + x.total, 0);
    if (!total) return { html: '<div class="col-12"><div class="alert alert-warning mb-0">No hay respuestas con ese filtro.</div></div>' };
    const cant = nombre =>
  g.find(x => normalizarTexto(x.respuesta) === normalizarTexto(nombre))?.total ?? 0;

    const base = cant(CFG.grupoBase);
    const ext = cant(CFG.grupoExtranjero);
    const fuga = total - base;
    const pct = v => (100 * v / total).toFixed(1) + '%';
    const k = (titulo, valor, detalle, color) => `
      <div class="col-md-3"><div class="card border-0 shadow-sm h-100"><div class="card-body text-center">
        <div class="small text-muted">${titulo}</div>
        <div class="fs-2 fw-bold text-${color}">${valor}</div>
        <div class="small text-muted">${detalle}</div>
      </div></div></div>`;
    return { html:
      k('Titulados analizados', total, 'con departamento registrado', 'primary') +
      k(`Trabajan en ${esc(CFG.grupoBase)}`, pct(base), `${base} de ${total}`, 'success') +
      k('Fuera del departamento base (fuga)', pct(fuga), `${fuga} de ${total}`, 'danger') +
      k('En el extranjero', pct(ext), `${ext} de ${total}`, 'warning') };
  }

  async function dondeTrabajan(qs) {
    const titulo = '¿Dónde trabajan los titulados?';
    const nota = 'Cantidad de titulados por departamento.';
    const d = await api(`/api/analisis/frecuencias?pregunta=${CFG.p.departamento}${qs}`);
    if (!d.length) return sinDatos(titulo, nota);
    return {
      html: carta(titulo, nota, '<canvas id="gDonde" height="110"></canvas>'),
      dibujar: () => dibujar('gDonde', {
        type: 'bar',
        data: { labels: d.map(x => x.respuesta),
                datasets: [{ data: d.map(x => x.total), backgroundColor: COLORES[0] }] },
        options: {
          indexAxis: 'y',
          plugins: { legend: { display: false },
                     tooltip: { callbacks: { label: c => `${c.raw} titulados (${d[c.dataIndex].porcentaje}%)` } } },
          scales: { x: { beginAtZero: true, ticks: { stepSize: 1 } } }
        }
      })
    };
  }

  // Promedio de una pregunta (valor) agrupado por otra (grupo), con n, suma y desviación
  async function promedio(titulo, nota, valor, grupo, qs, o) {
    const r = await api(`/api/analisis/promedio?valor=${valor}&grupo=${grupo}${qs}`);
    if (!r.grupos.length) return sinDatos(titulo, nota);
    const dec = o.decimales ?? 2;
    const fmt = v => v == null ? '—' : (o.prefijo || '') + num(v, dec);
    const filas = r.grupos.map(g => `
      <tr class="${g.muestraSuficiente ? '' : 'table-warning'}">
        <td class="text-start">${esc(g.grupo)}</td>
        <td>${g.n}</td>
        <td>${num(g.suma, dec)}</td>
        <td class="fw-bold">${fmt(g.media)}</td>
        <td>${g.desviacion == null ? '—' : num(g.desviacion, dec)}</td>
        <td class="small text-start">${g.muestraSuficiente ? '' : `n &lt; ${CFG.umbralN}: poco confiable. `}${g.sinCodificar ? `${g.sinCodificar} sin valor asignado` : ''}</td>
      </tr>`).join('');
    const cuerpo = `
      <canvas id="${o.canvas}" height="110"></canvas>
      <div class="table-responsive mt-3"><table class="table table-bordered table-sm text-center align-middle mb-0">
        <thead class="table-light"><tr>
          <th class="text-start">${esc(o.columna)}</th><th>n</th><th>Suma (Σ)</th><th>Media (Σ ÷ n)</th><th>Desv. estándar</th><th></th>
        </tr></thead>
        <tbody>${filas}
          <tr class="table-secondary fw-bold"><td class="text-start">General</td><td>${r.n}</td>
            <td>${num(r.suma, dec)}</td><td>${fmt(r.mediaGlobal)}</td><td></td><td></td></tr>
        </tbody></table></div>
      <p class="small text-muted mt-2 mb-0">Filas en amarillo: grupos con menos de ${CFG.umbralN} respuestas.</p>`;
    return {
      html: carta(titulo, nota, cuerpo),
      dibujar: () => dibujar(o.canvas, {
        type: 'bar',
        data: { labels: r.grupos.map(g => g.grupo),
                datasets: [{ label: 'Media', data: r.grupos.map(g => g.media),
                             backgroundColor: r.grupos.map(g => g.muestraSuficiente ? COLORES[0] : COLORES[2]) }] },
        options: {
          plugins: { legend: { display: false },
                     tooltip: { callbacks: { label: c => `Media: ${fmt(c.raw)} (n = ${r.grupos[c.dataIndex].n})` } } },
          scales: { y: { beginAtZero: true } }
        }
      })
    };
  }

  // Cruce X × Y: barras apiladas (tabla completa) + prueba Chi-cuadrado con sus pasos
  async function cruce(titulo, nota, x, y, nombreX, nombreY, qs, canvas) {
    const base = `/api/analisis/chi-cuadrado?x=${x}&y=${y}${qs}`;
    const [completa, p] = await Promise.all([api(base + '&modo=completa'), api(base + '&modo=auto')]);
    if (!completa.n) return sinDatos(titulo, nota);

    let color, texto;
    if (p.chiCuadrado == null) {
      color = 'secondary'; texto = 'No se pudo calcular la prueba con estos datos.';
    } else if (p.rechazaIndependencia) {
      color = p.cumpleSupuestos ? 'success' : 'warning';
      texto = `χ² (${num(p.chiCuadrado, 3)}) supera el valor crítico (${num(p.valorCritico, 3)}): se rechaza la independencia. `
            + `Hay evidencia de relación entre «${nombreX}» y «${nombreY}».`;
    } else {
      color = 'info';
      texto = `χ² (${num(p.chiCuadrado, 3)}) no supera el valor crítico (${num(p.valorCritico, 3)}): `
            + `no hay evidencia suficiente de relación entre «${nombreX}» y «${nombreY}».`;
    }
    if (p.chiCuadrado != null && !p.cumpleSupuestos) texto += ' Interprételo con cautela (ver advertencias).';

    const resumen = [['N', p.n], ['Grados de libertad', p.gradosLibertad], ['χ² calculado', num(p.chiCuadrado, 3)],
                     [`Valor crítico (α = ${p.alfa})`, num(p.valorCritico, 3)], ['Valor p', fmtP(p.pValor)]]
      .map(([t, v]) => `<div class="col"><div class="small text-muted">${t}</div><div class="fw-bold">${v}</div></div>`).join('');
    const alertas = p.advertencias.map(a => `<div class="alert alert-warning py-2 small mb-2">${esc(a)}</div>`).join('');
    const modo = p.modoAplicado === 'AGRUPADA' ? 'Tabla agrupada (categorías unidas)' : 'Tabla completa';
    const nf = p.etiquetasFila.length, nc = p.etiquetasColumna.length;

    const cuerpo = `
      <canvas id="${canvas}" height="120"></canvas>
      <hr>
      <div class="d-flex justify-content-between align-items-center mb-2">
        <span class="fw-semibold">Prueba de independencia Chi-cuadrado</span>
        <span class="badge text-bg-secondary">${modo}</span>
      </div>
      <div class="row row-cols-2 row-cols-md-5 g-2 text-center mb-3">${resumen}</div>
      <div class="alert alert-${color} py-2 mb-2">${texto}</div>
      ${alertas}
      <details class="mt-2">
        <summary class="fw-semibold">Ver pasos para verificar a mano</summary>
        <p class="small mt-2 mb-1">E = (total fila × total columna) ÷ N &nbsp;·&nbsp; χ² = Σ (O − E)² ÷ E
          &nbsp;·&nbsp; gl = (filas − 1) × (columnas − 1) = (${nf} − 1) × (${nc} − 1) = ${p.gradosLibertad}</p>
        <p class="fw-semibold mb-1">1. Frecuencias observadas (O)</p>
        ${tabla(p.etiquetasFila, p.etiquetasColumna, p.observada,
                { esquina: `${nombreX} \\ ${nombreY}`, totalesFila: p.totalesFila, totalesColumna: p.totalesColumna, n: p.n })}
        <p class="fw-semibold mb-1">2. Frecuencias esperadas (E)</p>
        ${tabla(p.etiquetasFila, p.etiquetasColumna, p.esperada, { dec: 2 })}
        <p class="fw-semibold mb-1">3. Contribución de cada celda (O − E)² ÷ E &nbsp;→&nbsp; su suma es χ²</p>
        ${tabla(p.etiquetasFila, p.etiquetasColumna, p.contribucion, { dec: 3 })}
      </details>`;
    return {
      html: carta(titulo, nota, cuerpo),
      dibujar: () => dibujar(canvas, {
        type: 'bar',
        data: { labels: completa.etiquetasFila,
                datasets: completa.etiquetasColumna.map((c, j) => ({
                  label: c, data: completa.observada.map(fila => fila[j]),
                  backgroundColor: COLORES[j % COLORES.length] })) },
        options: {
          plugins: { legend: { position: 'bottom' } },
          scales: { x: { stacked: true, title: { display: true, text: nombreX } },
                    y: { stacked: true, beginAtZero: true, ticks: { stepSize: 1 } } }
        }
      })
    };
  }

  // ---------- Orquestación ----------
  function esqueleto() {
    $('contenedorUbicacion').innerHTML =
      '<div id="uKpis" class="row g-3 mb-4"></div>' +
      ['uDonde', 'uSalario', 'uCargoPert', 'uCruceSalario', 'uCruceCargo', 'uCruceUbic']
        .map(id => `<div id="${id}" class="mb-4"></div>`).join('');
  }

  async function actualizar() {
    const mi = ++version;
    limpiarGraficos();
    esqueleto();
    const qs = qsFiltros(filtrosActuales());
    const P = CFG.p;
    await Promise.all([
      correr('uKpis', mi, () => kpis(qs)),
      correr('uDonde', mi, () => dondeTrabajan(qs)),
      correr('uSalario', mi, () => promedio(
        'Salario promedio mensual por departamento',
        'Estimación con el punto medio de cada rango salarial (el rango «Más de Bs. 10.000» se toma como Bs. 12.000).',
        P.salario, P.departamento, qs, { prefijo: 'Bs. ', decimales: 0, columna: 'Departamento', canvas: 'gSalario' })),
      correr('uCargoPert', mi, () => promedio(
        'Pertinencia promedio por cargo',
        'Escala de 1 (totalmente en desacuerdo) a 4 (totalmente de acuerdo) sobre si las labores son pertinentes a la formación.',
        P.pertinencia, P.cargo, qs, { decimales: 2, columna: 'Cargo', canvas: 'gCargoPert' })),
      correr('uCruceSalario', mi, () => cruce(
        'Ubicación × nivel salarial',
        '¿El nivel salarial depende de dónde trabaja el titulado?',
        P.departamento, P.salario, 'Departamento', 'Rango salarial', qs, 'gCruceSalario')),
      correr('uCruceCargo', mi, () => cruce(
        'Cargo × pertinencia de la formación',
        'Barras apiladas: cómo se distribuye la pertinencia dentro de cada cargo.',
        P.cargo, P.pertinencia, 'Cargo', 'Pertinencia', qs, 'gCruceCargo')),
      correr('uCruceUbic', mi, () => cruce(
        'Ubicación × pertinencia de la formación',
        '¿La pertinencia del puesto cambia según el lugar de trabajo?',
        P.departamento, P.pertinencia, 'Departamento', 'Pertinencia', qs, 'gCruceUbic'))
    ]);
  }

  window.cargarReporteUbicacion = async function () {
    try {
      if (!filtrosListos) { await prepararFiltros(); filtrosListos = true; }
    } catch (e) {
      $('contenedorUbicacion').innerHTML =
        `<div class="alert alert-danger">No se pudo cargar el reporte: ${esc(e.message)}</div>`;
      return;
    }
    await actualizar();
  };
})();