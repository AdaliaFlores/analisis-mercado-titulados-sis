(function () {
  'use strict';

  const COLORES = ['#0d6efd', '#198754', '#ffc107', '#dc3545', '#6f42c1',
                   '#0dcaf0', '#fd7e14', '#20c997', '#6610f2', '#d63384'];

  let charts = [];

  window.cargarReporteBrechas = async function () {
    const cont = document.getElementById('contenedorBrechas');
    cont.innerHTML = '<p class="text-muted">Cargando reporte...</p>';
    charts.forEach(c => { try { c.destroy(); } catch (e) {} });
    charts = [];

    let d;
    try {
      const res = await fetch('/api/reportes/brechas');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      d = await res.json();
    } catch (e) {
      cont.innerHTML = '<p class="text-danger">No se pudo cargar el reporte: ' + e.message + '</p>';
      return;
    }

    cont.innerHTML = '';

    cont.insertAdjacentHTML('beforeend', `
      <div class="col-12">
        <div class="alert alert-light border small mb-0">
          <strong class="text-warning">Marco del análisis.</strong>
          <span class="badge bg-danger-subtle text-danger-emphasis border border-danger-subtle">Estado Objetivo</span>
          = demanda del sector empleador (tendencias, herramientas y competencias).
          <span class="badge bg-primary-subtle text-primary-emphasis border border-primary-subtle">Estado Actual</span>
          = percepción del titulado (escala de acuerdo de 4 puntos y asignaturas).
          <strong>Brecha</strong> = Objetivo − Actual (valores en %).
          &nbsp;·&nbsp; Titulados: <strong>${d.totalTitulados}</strong>
          &nbsp;·&nbsp; Empleadores: <strong>${d.totalEmpleadores}</strong>
        </div>
      </div>`);

    renderIndicadores(cont, d.indicadores);
    renderRadar(cont, d.radar);
    renderAsignaturas(cont, d.asignaturasVentaja, d.asignaturasPocaUtilidad);
    renderHeatmaps(cont, d);
    renderDemanda(cont, d);
    renderContexto(cont, d.contextoEmpleador);
    renderResumen(cont, d);
  };

  //media y desviacion estandar
  function renderIndicadores(cont, indicadores) {
    if (!indicadores || !indicadores.length) return;
    cont.insertAdjacentHTML('beforeend',
      `<div class="col-12 mt-2"><h6 class="fw-bold text-warning mb-0">Indicadores estadísticos del Estado Actual (escala 1 = totalmente en desacuerdo · 4 = totalmente de acuerdo)</h6></div>`);

    indicadores.forEach(ind => {
      const pct = Math.round((ind.media / 4) * 100);
      const dist = (ind.distribucion || []).map(x =>
        `<span class="badge text-bg-light border me-1 mb-1">${escapar(x.respuesta)}: <strong>${x.total}</strong> (${x.porcentaje}%)</span>`
      ).join('');
      cont.insertAdjacentHTML('beforeend', `
        <div class="col-md-6 col-xl-3">
          <div class="card border-0 shadow-sm h-100">
            <div class="card-body">
              <div class="small text-muted">${escapar(ind.etiqueta)}</div>
              <div class="d-flex align-items-baseline gap-2 my-1">
                <span class="fs-3 fw-bold">${ind.media}</span>
                <span class="text-muted small">/ 4</span>
                <span class="badge text-bg-secondary ms-auto">n = ${ind.n}</span>
              </div>
              <div class="progress" style="height:8px"><div class="progress-bar bg-warning" style="width:${pct}%"></div></div>
              <div class="small text-muted mt-1">Desviación estándar: <strong>± ${ind.desviacion}</strong></div>
              <div class="mt-2">${dist}</div>
            </div>
          </div>
        </div>`);
    });
  }

  // Radar: Estado Objetivo vs Estado Actual
  function renderRadar(cont, radar) {
    if (!radar || !radar.length) return;

    cont.insertAdjacentHTML('beforeend', `
      <div class="col-lg-7">
        <div class="card border-0 shadow-sm h-100">
          <div class="card-body">
            <h6 class="fw-bold text-warning">Diagrama de araña · Brecha de competencias y áreas tecnológicas</h6>
            <canvas id="radarBrechas" height="240"></canvas>
          </div>
        </div>
      </div>`);

    const filas = radar.map(r => {
      const b = r.brecha;
      const cls = b > 10 ? 'text-danger' : (b < -10 ? 'text-success' : 'text-secondary');
      const signo = b > 0 ? '+' : '';
      return `<tr>
        <td class="text-start">${escapar(r.dimension)}</td>
        <td>${r.objetivo}%</td>
        <td>${r.actual}%</td>
        <td class="fw-bold ${cls}">${signo}${b}%</td>
      </tr>`;
    }).join('');

    cont.insertAdjacentHTML('beforeend', `
      <div class="col-lg-5">
        <div class="card border-0 shadow-sm h-100">
          <div class="card-body">
            <h6 class="fw-bold text-warning">Brecha cuantitativa por dimensión</h6>
            <div class="table-responsive">
              <table class="table table-sm table-bordered text-center align-middle mb-0">
                <thead class="table-light">
                  <tr><th class="text-start">Dimensión</th><th>Objetivo</th><th>Actual</th><th>Brecha</th></tr>
                </thead>
                <tbody>${filas}</tbody>
              </table>
            </div>
            <div class="small text-muted mt-2">Brecha positiva = el mercado demanda más de lo que el titulado reporta.</div>
          </div>
        </div>
      </div>`);

    charts.push(new Chart(document.getElementById('radarBrechas'), {
      type: 'radar',
      data: {
        labels: radar.map(r => r.dimension),
        datasets: [
          { label: 'Estado Objetivo (empleador)', data: radar.map(r => r.objetivo),
            borderColor: '#dc3545', backgroundColor: 'rgba(220,53,69,.20)', pointBackgroundColor: '#dc3545' },
          { label: 'Estado Actual (titulado)', data: radar.map(r => r.actual),
            borderColor: '#0d6efd', backgroundColor: 'rgba(13,110,253,.20)', pointBackgroundColor: '#0d6efd' }
        ]
      },
      options: {
        scales: { r: { beginAtZero: true, suggestedMax: 100, ticks: { callback: v => v + '%' } } },
        plugins: { tooltip: { callbacks: { label: c => c.dataset.label + ': ' + c.parsed.r + '%' } } }
      }
    }));
  }

  // Frecuencias de asignaturas (barras horizontales)
  function renderAsignaturas(cont, ventaja, poca) {
    const bloque = (id, titulo, datos, color, nota) => {
      if (!datos || !datos.length) return;
      cont.insertAdjacentHTML('beforeend', `
        <div class="col-lg-6">
          <div class="card border-0 shadow-sm h-100">
            <div class="card-body">
              <h6 class="fw-bold text-warning">${titulo}</h6>
              <canvas id="${id}" height="260"></canvas>
              <div class="small text-muted mt-2">${nota}</div>
            </div>
          </div>
        </div>`);
      charts.push(new Chart(document.getElementById(id), {
        type: 'bar',
        data: {
          labels: datos.map(x => x.respuesta),
          datasets: [{ label: '% de titulados', data: datos.map(x => x.porcentaje),
            backgroundColor: color, total: datos.map(x => x.total) }]
        },
        options: {
          indexAxis: 'y',
          scales: { x: { beginAtZero: true, max: 100, ticks: { callback: v => v + '%' } } },
          plugins: {
            legend: { display: false },
            tooltip: { callbacks: { label: c => c.parsed.x + '%  (' + c.dataset.total[c.dataIndex] + ' titulados)' } }
          }
        }
      }));
    };

    bloque('gAsigVentaja', 'Asignaturas que brindaron ventaja competitiva', ventaja, '#198754',
      'Cada titulado eligió hasta 5 asignaturas.');
    bloque('gAsigPoca', 'Asignaturas de poca utilidad profesional', poca, '#dc3545',
      'Cada titulado eligió hasta 5 asignaturas.');
  }

  // Mapas de calor (tablas cruzadas)
  function renderHeatmaps(cont, d) {
    cont.insertAdjacentHTML('beforeend',
      `<div class="col-12 mt-2"><h6 class="fw-bold text-warning mb-0">Tablas de frecuencia cruzada y mapas de calor</h6></div>`);

    insertarHeatmap(cont, 'Asignaturas con ventaja competitiva × situación laboral',
      d.cruceVentajaSituacion, 'col-12', 'Escala de color según el número de titulados que coinciden en la celda.');
    insertarHeatmap(cont, 'Asignaturas de poca utilidad × situación laboral',
      d.crucePocaSituacion, 'col-lg-6', 'Escala de color según el número de titulados.');
    insertarHeatmap(cont, 'Asignaturas de poca utilidad × razón de inactividad laboral',
      d.crucePocaRazon, 'col-lg-6', 'Solo titulados que declararon una razón de no trabajar.');
  }

  function insertarHeatmap(cont, titulo, celdas, colClase, nota) {
    if (!celdas || !celdas.length) {
      cont.insertAdjacentHTML('beforeend', `
        <div class="${colClase}">
          <div class="card border-0 shadow-sm"><div class="card-body">
            <h6 class="fw-bold text-warning">${titulo}</h6>
            <p class="text-muted small mb-0">Sin datos suficientes para cruzar.</p>
          </div></div>
        </div>`);
      return;
    }

    // filas (asignaturas) y columnas (variable)
    const totalFila = {}, totalCol = {};
    celdas.forEach(c => {
      totalFila[c.valorX] = (totalFila[c.valorX] || 0) + c.total;
      totalCol[c.valorY] = (totalCol[c.valorY] || 0) + c.total;
    });
    const filas = Object.keys(totalFila).sort((a, b) => totalFila[b] - totalFila[a]);
    const cols = Object.keys(totalCol).sort((a, b) => totalCol[b] - totalCol[a]);

    const valor = (x, y) => {
      const c = celdas.find(c => c.valorX === x && c.valorY === y);
      return c ? c.total : 0;
    };
    const max = Math.max(1, ...celdas.map(c => c.total));

    const encabezado = cols.map(col => `<th class="small align-bottom">${escapar(col)}</th>`).join('');
    const cuerpo = filas.map(fila => {
      const celdasHtml = cols.map(col => {
        const v = valor(fila, col);
        const alfa = v === 0 ? 0 : 0.12 + 0.78 * (v / max);
        const texto = alfa > 0.55 ? '#fff' : '#212529';
        return `<td style="background:rgba(220,53,69,${alfa});color:${texto};font-weight:${v ? 600 : 400}">${v || ''}</td>`;
      }).join('');
      return `<tr><th class="text-start small">${escapar(fila)}</th>${celdasHtml}
        <td class="table-secondary fw-bold">${totalFila[fila]}</td></tr>`;
    }).join('');

    cont.insertAdjacentHTML('beforeend', `
      <div class="${colClase}">
        <div class="card border-0 shadow-sm h-100">
          <div class="card-body">
            <h6 class="fw-bold text-warning">${titulo}</h6>
            <div class="table-responsive">
              <table class="table table-sm table-bordered text-center align-middle mb-0">
                <thead class="table-light">
                  <tr><th class="text-start">Asignatura \\ Variable</th>${encabezado}<th class="table-secondary">Total</th></tr>
                </thead>
                <tbody>${cuerpo}</tbody>
              </table>
            </div>
            <div class="small text-muted mt-2">${nota}</div>
          </div>
        </div>
      </div>`);
  }

  // Demanda del empleador (texto abierto -> palabras clave)
  function renderDemanda(cont, d) {
    cont.insertAdjacentHTML('beforeend',
      `<div class="col-12 mt-2"><h6 class="fw-bold text-warning mb-0">Estado Objetivo · Demanda del sector empleador </h6></div>`);

    listaDemanda(cont, 'Nuevas áreas de conocimiento / tendencias', d.demandaTendencias, '#dc3545');
    listaDemanda(cont, 'Nuevas herramientas tecnológicas', d.demandaHerramientas, '#0d6efd');
    listaDemanda(cont, 'Habilidades y competencias fundamentales', d.demandaCompetencias, '#198754');
  }

  function listaDemanda(cont, titulo, datos, color) {
    const filas = (datos && datos.length)
      ? datos.map(x => `
          <div class="d-flex justify-content-between small">
            <span>${escapar(x.palabra)}</span>
            <span class="text-muted">${x.total} menciones · ${x.porcentaje}%</span>
          </div>
          <div class="progress mb-2" style="height:6px">
            <div class="progress-bar" style="width:${x.porcentaje}%;background:${color}"></div>
          </div>`).join('')
      : '<p class="text-muted small mb-0">Sin menciones registradas.</p>';

    cont.insertAdjacentHTML('beforeend', `
      <div class="col-lg-4">
        <div class="card border-0 shadow-sm h-100">
          <div class="card-body">
            <h6 class="fw-bold text-warning">${titulo}</h6>
            ${filas}
          </div>
        </div>
      </div>`);
  }

  // Contexto del empleador (rubro, contratación, nivel de formación)
  function renderContexto(cont, contexto) {
    if (!contexto || !contexto.length) return;
    const NOMBRES = { 53: 'Rubro / sector', 55: 'Posibilidad de incorporar', 56: 'Nivel de formación demandado' };
    const porPregunta = {};
    contexto.forEach(c => { (porPregunta[c.preguntaId] = porPregunta[c.preguntaId] || []).push(c); });

    let html = '';
    Object.keys(porPregunta).forEach(pid => {
      const items = porPregunta[pid];
      html += `<div class="mb-3">
        <div class="fw-semibold small text-muted">${NOMBRES[pid] || ('Pregunta ' + pid)}</div>
        ${items.map(x => `<div class="d-flex justify-content-between small border-bottom py-1">
            <span>${escapar(x.respuesta)}</span><span class="badge text-bg-light border">${x.total}</span>
          </div>`).join('')}
      </div>`;
    });

    cont.insertAdjacentHTML('beforeend', `
      <div class="col-12">
        <div class="card border-0 shadow-sm">
          <div class="card-body">
            <h6 class="fw-bold text-warning">Contexto del sector empleador</h6>
            <div class="row">${html}</div>
          </div>
        </div>
      </div>`);
  }

  // Resumen textual
  function renderResumen(cont, d) {
    const ind = d.indicadores || [];
    const radar = d.radar || [];
    const ventaja = d.asignaturasVentaja || [];
    const poca = d.asignaturasPocaUtilidad || [];

    const mediaMin = ind.length ? ind.reduce((a, b) => a.media < b.media ? a : b) : null;
    const objTop = radar.filter(r => r.objetivo > 0).sort((a, b) => b.objetivo - a.objetivo).slice(0, 5);
    const brechaTop = radar.slice().sort((a, b) => b.brecha - a.brecha).slice(0, 5);

    let html = '<ul class="mb-0 small">';
    html += `<li>Se analizaron <strong>${d.totalTitulados}</strong> titulados y <strong>${d.totalEmpleadores}</strong> empleadores.</li>`;

    if (ind.length) {
      const prom = (ind.reduce((a, b) => a + b.media, 0) / ind.length).toFixed(2);
      html += `<li>El <strong>Estado Actual</strong> (percepción del titulado) promedia <strong>${prom}/4</strong> en las escalas de formación. `;
      if (mediaMin) html += `El indicador más bajo (<strong>${mediaMin.media}/4</strong>) es “${escapar(mediaMin.etiqueta)}”, con una desviación de ± ${mediaMin.desviacion} (dispersión ${mediaMin.desviacion >= 1 ? 'alta' : 'moderada'}).`;
      html += `</li>`;
    }

    if (objTop.length) {
      html += `<li><strong>Estado Objetivo</strong>: el mercado demanda principalmente ${objTop.map(o => `<strong>${escapar(o.dimension)}</strong>${o.objetivo > 0 ? ` (${o.objetivo}% de los empleadores)` : ''}`).join(', ')}.</li>`;
    }

    if (brechaTop.length) {
      html += `<li><strong>Brechas más críticas</strong> (Objetivo − Actual): ${brechaTop.map(b => `${escapar(b.dimension)} <strong>${b.brecha > 0 ? '+' : ''}${b.brecha}%</strong>`).join(', ')}.</li>`;
    }

    if (ventaja.length) {
      html += `<li>Los titulados perciben mayor preparación en ${ventaja.slice(0, 3).map(v => `<strong>${escapar(v.respuesta)}</strong> (${v.porcentaje}%)`).join(', ')}.</li>`;
    }
    if (poca.length) {
      html += `<li>Las asignaturas señaladas como de <strong>poca utilidad</strong> son ${poca.slice(0, 3).map(v => `<strong>${escapar(v.respuesta)}</strong> (${v.porcentaje}%)`).join(', ')}; conviene revisar su enfoque o intensidad.</li>`;
    }

    html += `<li class="text-muted">Recomendación: reforzar las áreas donde la brecha es positiva (demanda del empleador sin preparación reportada) e integrar tendencias emergentes y herramientas actuales en las asignaturas mejor valoradas.</li>`;
    html += '</ul>';

    cont.insertAdjacentHTML('beforeend', `
      <div class="col-12">
        <div class="card border-0 shadow-sm">
          <div class="card-body">
            <h6 class="fw-bold text-warning">Resumen y recomendaciones</h6>
            ${html}
          </div>
        </div>
      </div>`);
  }

  function escapar(s) {
    return String(s == null ? '' : s)
      .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;').replaceAll("'", '&#39;');
  }
})();
