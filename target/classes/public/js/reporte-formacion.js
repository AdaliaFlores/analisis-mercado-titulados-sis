(function () {
  'use strict';

  let charts = [];

  window.cargarReporteFormacion = async function () {
    const cont = document.getElementById('contenedorFormacion');
    cont.innerHTML = '<p class="text-muted">Cargando reporte...</p>';
    charts.forEach(c => { try { c.destroy(); } catch (e) {} });
    charts = [];

    let d;
    try {
      const res = await fetch('/api/reportes/formacion-financiamiento');
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
          <strong class="text-info">Formación continua y mecanismos de financiamiento.</strong>
          <span class="badge bg-info-subtle text-info-emphasis border border-info-subtle">Formación continua</span>
          = áreas de interés de posgrado y nivel de formación deseado.
          <span class="badge bg-secondary-subtle text-dark border border-secondary-subtle">Financiamiento</span>
          = matriz de contingencia Nivel × Fuente de financiamiento + diagnóstico χ² (df, p-valor).
        </div>
      </div>`);

    renderFormacionContinua(cont, d);
    renderFinanciamiento(cont, d.financiamiento);
    renderResumen(cont, d);
  };

  function renderFormacionContinua(cont, d) {
    const areas = d.areasInteres || [];
    const alerta = d.alertaConcentracion;
    const niveles = d.nivelesFormacion || [];

    // Alerta
    if (alerta) {
      cont.insertAdjacentHTML('beforeend', `
        <div class="col-12">
          <div class="alert alert-danger border-danger d-flex align-items-start">
            <div class="me-2 fs-4">⚠</div>
            <div>
              <strong>Concentración Crítica de Mercado</strong>
              <div class="small">La suma de las dos áreas principales es ≥ 70%. Se recomienda diversificar la oferta de posgrado.</div>
            </div>
          </div>
        </div>`);
    }

    // Barras verticales áreas de interés
    cont.insertAdjacentHTML('beforeend', `
      <div class="col-lg-7">
        <div class="card border-0 shadow-sm h-100">
          <div class="card-body">
            <h6 class="fw-bold text-info">Áreas de interés o especialidad de posgrado (frecuencias y porcentajes)</h6>
            <canvas id="graficoAreasInteres" height="200"></canvas>
                        <div class="small text-muted mt-2">Áreas de interés declaradas por el titulado . n_i = número de titulados que seleccionaron el área. % = f_i = (n_i/N) times100.</div>
          </div>
        </div>
      </div>`);

    // Tabla
    cont.insertAdjacentHTML('beforeend', `
      <div class="col-lg-5">
        <div class="card border-0 shadow-sm h-100">
          <div class="card-body">
            <h6 class="fw-bold text-info">Tabla de frecuencias · Áreas de interés</h6>
            <div class="table-responsive">
              <table class="table table-sm table-bordered text-center align-middle mb-0">
                <thead class="table-light"><tr><th class="text-start">Área de interés</th><th>$n_i$</th><th>%</th></tr></thead>
                <tbody>
                  ${areas.map(a => `
                    <tr><td class="text-start">${escapar(a.area)}</td><td>${a.n}</td><td>${a.porcentaje}%</td></tr>
                  `).join('') || '<tr><td colspan="3" class="text-muted">Sin datos</td></tr>'}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>`);

    if (areas.length) {
      charts.push(new Chart(document.getElementById('graficoAreasInteres'), {
        type: 'bar',
        data: {
          labels: areas.map(a => a.area),
          datasets: [
            { label: 'Frecuencia $n_i$', data: areas.map(a => a.n), backgroundColor: '#0dcaf099' },
            { label: 'Porcentaje %', data: areas.map(a => a.porcentaje), backgroundColor: '#0d6efd' }
          ]
        },
        options: {
          scales: {
            x: { ticks: { maxRotation: 40, minRotation: 30 } },
            y: { beginAtZero: true }
          },
          plugins: { tooltip: { callbacks: { label: c => c.dataset.label + ': ' + c.parsed.y + (c.datasetIndex === 1 ? '%' : '') } } }
        }
      }));
    }

    // Nivel de formación deseado
    cont.insertAdjacentHTML('beforeend', `
      <div class="col-12 mt-3">
        <div class="card border-0 shadow-sm">
          <div class="card-body">
            <h6 class="fw-bold text-info">Nivel de formación deseado · Frecuencias</h6>
            <div class="row g-3">
              ${(niveles || []).map(x => `
                <div class="col-md-4 col-lg-3">
                  <div class="border rounded p-2 h-100">
                    <div class="small text-muted">${escapar(x.respuesta)}</div>
                    <div class="fs-4 fw-bold">${x.total}</div>
                    <div class="small text-muted">${x.porcentaje}%</div>
                  </div>
                </div>
              `).join('') || '<div class="col-12 text-muted small">Sin datos.</div>'}
            </div>
          </div>
        </div>
      </div>`);
  }

  function renderFinanciamiento(cont, fin) {
    const chi2 = fin.chi2 || {};
    const filas = fin.filas || [];
    const columnas = fin.columnas || [];
    const celdas = fin.celdas || [];
    const tieneDatos = fin.tieneDatos;

    const valor = (f, c) => {
      const cel = celdas.find(x => x.valorX === f && x.valorY === c);
      return cel ? cel.total : 0;
    };
    const conteoFilas = fin.conteoFilas || {};
    const conteoCols = fin.conteoCols || {};
    let totalN = 0; Object.values(conteoFilas).forEach(v => totalN += v); Object.values(conteoCols).forEach(v => totalN += v);
    if (tieneDatos) {
      totalN = 0; celdas.forEach(x => totalN += x.total);
    }

    cont.insertAdjacentHTML('beforeend', `
      <div class="col-12 mt-3">
        <div class="card border-0 shadow-sm">
          <div class="card-body">
            <h6 class="fw-bold text-secondary">Mecanismos de financiamiento · Matriz de contingencia (Nivel × Fuente de financiamiento)</h6>
            <div class="table-responsive mt-2">
              <table class="table table-sm table-bordered text-center align-middle mb-0">
                <thead class="table-light">
                  <tr>
                    <th>Nivel / Financiamiento</th>
                    ${columnas.map(c => `<th>${escapar(c)}</th>`).join('') || '<th>(Sin columnas)</th>'}
                    <th class="table-secondary">Total fila</th>
                  </tr>
                </thead>
                <tbody>
                  ${filas.map(f => `
                    <tr>
                      <th class="text-start">${escapar(f)}</th>
                      ${(columnas.length ? columnas.map(c => `<td>${valor(f,c)}</td>`).join('') : '<td>0</td>')}
                      <td class="table-secondary fw-bold">${conteoFilas[f] || 0}</td>
                    </tr>
                  `).join('') || '<tr><td colspan="2" class="text-muted">Sin datos de filas</td></tr>'}
                  <tr class="table-light">
                    <th class="text-start">Total columna</th>
                    ${(columnas.length ? columnas.map(c => `<td class="fw-bold">${conteoCols[c] || 0}</td>`).join('') : '')}
                    <td class="table-secondary fw-bold">${totalN}</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <div class="small text-muted mt-2">
              ${tieneDatos
                ? 'Matriz de contingencia observada. Las celdas muestran frecuencias absolutas para cada cruce.'
                : 'Sin datos suficientes para construir la matriz (no existe pregunta de mecanismo de financiamiento en la encuesta actual).'}
            </div>
          </div>
        </div>
      </div>`);

    // Tarjetas de diagnóstico χ²
    cont.insertAdjacentHTML('beforeend', `
      <div class="col-12 mt-3">
        <div class="card border-0 shadow-sm">
          <div class="card-body">
            <h6 class="fw-bold text-secondary">Diagnóstico estadístico · Chi-cuadrado (chi^2)</h6>
            <div class="row g-3 mt-1">
              <div class="col-md-3">
                <div class="border rounded p-2 h-100">
                  <div class="small text-muted"> chi^2 calculado</div>
                  <div class="fs-4 fw-bold">${Number(chi2.chiCuadrado || 0).toFixed(3)}</div>
                </div>
              </div>
              <div class="col-md-3">
                <div class="border rounded p-2 h-100">
                  <div class="small text-muted">Grados de libertad (df)</div>
                  <div class="fs-4 fw-bold">${chi2.gradosLibertad ?? 0}</div>
                  <!-- <div class="small text-muted">df = (filas-1)(cols-1)</div> -->
                </div>
              </div>
              <div class="col-md-3">
                <div class="border rounded p-2 h-100">
                  <div class="small text-muted">Valor p (p-value)</div>
                  <div class="fs-4 fw-bold">${Number(chi2.valorP || 1).toFixed(3)}</div>
                </div>
              </div>
              <div class="col-md-3">
                <div class="border rounded p-2 h-100">
                  <div class="small text-muted">Resultado de independencia</div>
                  <div class="fw-bold ${chi2.independiente ? 'text-success' : 'text-danger'}">
                    ${chi2.independiente ? 'No se rechaza H₀ (independiente)' : 'Se rechaza H₀ (dependiente)'}
                  </div>
                  <div class="small text-muted">Umbral: p < 0.05</div>
                </div>
              </div>
            </div>
            <div class="alert alert-light border mt-3 mb-0 small">
              <strong>Interpretación:</strong> ${escapar(chi2.interpretacion || '')}
            </div>
          </div>
        </div>
      </div>`);
  }

  function renderResumen(cont, d) {
    const areas = (d.areasInteres || []).slice(0, 3);
    const alerta = d.alertaConcentracion;
    let html = '<ul class="mb-0 small">';
    if (areas.length) {
      const top2 = areas.slice(0, 2);
      const suma = (top2[0]?.porcentaje || 0) + (top2[1]?.porcentaje || 0);
      html += `<li><strong>Áreas con mayor interés de posgrado</strong>: ${areas.map(a => `${escapar(a.area)} (${a.porcentaje}%)`).join(', ')}.</li>`;
      html += `<li>Suma de las 2 principales: <strong>${suma.toFixed(1)}%</strong>. ${alerta ? '<span class="text-danger fw-semibold">Alerta: Concentración Crítica de Mercado (≥ 70%).</span>' : 'No supera el umbral de concentración crítica (≥ 70%).'}</li>`;
    } else {
      html += '<li>No hay datos suficientes para identificar áreas de interés de posgrado.</li>';
    }
    cont.insertAdjacentHTML('beforeend', `
      <div class="col-12 mt-3">
        <div class="card border-0 shadow-sm">
          <div class="card-body">
            <h6 class="fw-bold text-info">Resumen y recomendación</h6>
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