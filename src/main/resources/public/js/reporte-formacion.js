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
          = matriz de contingencia Nivel × Fuente de financiamiento + diagnóstico χ² (gl, p-valor).
        </div>
      </div>`);

    renderFormacionContinua(cont, d);
    renderFinanciamiento(cont, d.financiamiento, d.nivelesFormacion);
    renderResumen(cont, d);
  };

  function renderFormacionContinua(cont, d) {
    const alerta = d.alertaConcentracion;

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

    // --- CRUCE SUPERIOR: ÁREA DE INTERÉS × NIVEL DE POSGRADO (Gráfico + Tabla cruzada) ---
    const cruceAreaNiv = d.cruceAreaNivel || [];
    const areasUnicas = [...new Set(cruceAreaNiv.map(x => x.area))];
    const nivelesAreaUnicos = [...new Set(cruceAreaNiv.map(x => x.nivel))];
    const coloresArea = ['#0d6efd', '#0dcaf0', '#198754', '#ffc107', '#6f42c1'];

    // 1. Gráfico centrado en la parte superior
    cont.insertAdjacentHTML('beforeend', `
      <div class="col-lg-10 mx-auto">
        <div class="card border-0 shadow-sm h-100">
          <div class="card-body">
            <h6 class="fw-bold text-info text-center mb-3">Áreas de interés × Nivel de posgrado deseado</h6>
            <canvas id="graficoAreasInteres" height="115"></canvas>
            <div class="small text-muted text-center mt-2">Distribución de titulados por especialidad y tipo de posgrado deseado (N = 105 respuestas completas).</div>
          </div>
        </div>
      </div>`);

    // 2. Tabla cruzada dinámica debajo a todo lo ancho (sin scroll horizontal)
    cont.insertAdjacentHTML('beforeend', `
      <div class="col-12 mt-3">
        <div class="card border-0 shadow-sm h-100">
          <div class="card-body">
            <h6 class="fw-bold text-info">Tabla cruzada · Área × Nivel</h6>
            <div class="table-responsive">
              <table class="table table-sm table-bordered text-center align-middle mb-0">
                <thead class="table-light">
                  <tr>
                    <th class="text-start">Área de interés</th>
                    ${nivelesAreaUnicos.map(n => `<th>${escapar(n)}</th>`).join('')}
                    <th class="table-secondary">Total</th>
                  </tr>
                </thead>
                <tbody>
                  ${areasUnicas.map(area => {
                    let sumaFila = 0;
                    const celdas = nivelesAreaUnicos.map(niv => {
                      const found = cruceAreaNiv.find(x => x.area === area && x.nivel === niv);
                      const val = found ? found.total : 0;
                      sumaFila += val;
                      return `<td>${val}</td>`;
                    }).join('');
                    return `<tr><td class="text-start fw-semibold">${escapar(area)}</td>${celdas}<td class="table-secondary fw-bold">${sumaFila}</td></tr>`;
                  }).join('') || '<tr><td colspan="5" class="text-muted">Sin datos</td></tr>'}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>`);

    if (cruceAreaNiv.length) {
      const datasetsArea = nivelesAreaUnicos.map((niv, idx) => ({
        label: niv,
        backgroundColor: coloresArea[idx % coloresArea.length],
        data: areasUnicas.map(area => {
          const found = cruceAreaNiv.find(x => x.area === area && x.nivel === niv);
          return found ? found.total : 0;
        })
      }));

      charts.push(new Chart(document.getElementById('graficoAreasInteres'), {
        type: 'bar',
        data: {
          labels: areasUnicas,
          datasets: datasetsArea
        },
        options: {
          scales: {
            x: { ticks: { maxRotation: 35, minRotation: 20 } },
            y: { beginAtZero: true }
          },
          plugins: {
            legend: { position: 'top' },
            tooltip: {
              callbacks: {
                label: c => `${c.dataset.label}: ${c.parsed.y} titulados`
              }
            }
          }
        }
      }));
    }

    // --- CRUCE DE 2 VARIABLES: NIVEL DE POSGRADO × ORGANIZACIÓN (Gráfico + Tabla sincronizados) ---
    const cruce2 = d.cruceNivelOrg || [];
    const nivelesUnicos = [...new Set(cruce2.map(x => x.nivel))];
    const orgsUnicas = [...new Set(cruce2.map(x => x.organizacion))];

    cont.insertAdjacentHTML('beforeend', `
      <div class="col-12 mt-3">
        <div class="card border-0 shadow-sm">
          <div class="card-body">
            <div class="d-flex flex-wrap justify-content-between align-items-center gap-2 mb-3">
              <div>
                <h6 class="fw-bold text-info mb-0">Preferencia institucional · Nivel de Posgrado × Organización</h6>
                <div class="small text-muted">Distribución de dónde desean realizar su posgrado los titulados según el nivel elegido.</div>
              </div>
              <div class="d-flex align-items-center gap-2">
                <label for="filtroNivelCruce2" class="small text-muted mb-0">Filtrar Nivel:</label>
                <select id="filtroNivelCruce2" class="form-select form-select-sm" style="width: auto;">
                  <option value="TODOS">Todos los niveles</option>
                  ${nivelesUnicos.map(n => `<option value="${escapar(n)}">${escapar(n)}</option>`).join('')}
                </select>
              </div>
            </div>

            <!-- Gráfico de barras encima -->
            <div class="mb-4">
              <canvas id="graficoNivelOrganizacion" height="110"></canvas>
            </div>

            <!-- Tabla compacta debajo -->
            <div class="table-responsive">
              <table class="table table-sm table-hover table-bordered align-middle mb-0">
                <thead class="table-light">
                  <tr>
                    <th>Nivel de Posgrado</th>
                    <th>Organización / Institución</th>
                    <th class="text-center">Frecuencia (nᵢ)</th>
                    <th class="text-center">%</th>
                  </tr>
                </thead>
                <tbody id="tbodyCruce2"></tbody>
              </table>
            </div>
          </div>
        </div>
      </div>`);

    // Lógica sincronizada para actualizar Gráfico y Tabla al cambiar el filtro
    let chartNivelOrg = null;
    const colores = ['#0d6efd', '#0dcaf0', '#198754', '#ffc107', '#6f42c1'];

    const actualizarVistaCruce2 = (nivelSeleccionado) => {
      // 1. Filtrar datos para la tabla
      const filtrados = nivelSeleccionado === 'TODOS'
        ? cruce2
        : cruce2.filter(r => r.nivel === nivelSeleccionado);

      const tbody = document.getElementById('tbodyCruce2');
      tbody.innerHTML = filtrados.map(r => `
        <tr>
          <td><span class="badge bg-info-subtle text-info-emphasis border border-info-subtle">${escapar(r.nivel)}</span></td>
          <td>${escapar(r.organizacion)}</td>
          <td class="text-center fw-bold">${r.total}</td>
          <td class="text-center">${r.porcentaje}%</td>
        </tr>
      `).join('') || '<tr><td colspan="4" class="text-center text-muted">Sin datos registrados para este cruce.</td></tr>';

      // 2. Destruir gráfico anterior si existe y crear el nuevo filtrado
      if (chartNivelOrg) {
        chartNivelOrg.destroy();
      }

      if (!cruce2.length) return;

      const nivelesAMostrar = nivelSeleccionado === 'TODOS' ? nivelesUnicos : [nivelSeleccionado];
      const etiquetasOrg = nivelSeleccionado === 'TODOS'
        ? orgsUnicas
        : filtrados.map(x => x.organizacion);

      const datasets = nivelesAMostrar.map((niv) => {
        const idxColor = nivelesUnicos.indexOf(niv);
        return {
          label: niv,
          backgroundColor: colores[idxColor % colores.length],
          data: etiquetasOrg.map(org => {
            const encontrado = cruce2.find(x => x.nivel === niv && x.organizacion === org);
            return encontrado ? encontrado.total : 0;
          })
        };
      });

      chartNivelOrg = new Chart(document.getElementById('graficoNivelOrganizacion'), {
        type: 'bar',
        data: {
          labels: etiquetasOrg,
          datasets: datasets
        },
        options: {
          responsive: true,
          scales: {
            x: { ticks: { maxRotation: 25, minRotation: 15 } },
            y: { beginAtZero: true, ticks: { stepSize: 5 } }
          },
          plugins: {
            legend: { position: 'top' },
            tooltip: {
              callbacks: {
                label: ctx => `${ctx.dataset.label}: ${ctx.parsed.y} titulados`
              }
            }
          }
        }
      });

      charts.push(chartNivelOrg);
    };

    // Inicializar vista completa y activar evento de cambio
    actualizarVistaCruce2('TODOS');
    document.getElementById('filtroNivelCruce2').addEventListener('change', (e) => {
      actualizarVistaCruce2(e.target.value);
    });
  }

  function renderFinanciamiento(cont, fin, niveles) {
    const chi2 = fin.chi2 || {};
    const filas = fin.filas || [];
    const columnas = fin.columnas || [];
    const celdas = fin.celdas || [];
    const tieneDatos = fin.tieneDatos;

    // Nivel de formación deseado · Frecuencias (unido con la matriz de financiamiento)
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

    // Tarjetas de diagnóstico χ² (Con validación de N > 0)
    if (!tieneDatos || totalN === 0) {
      cont.insertAdjacentHTML('beforeend', `
        <div class="col-12 mt-3">
          <div class="card border-0 shadow-sm">
            <div class="card-body">
              <h6 class="fw-bold text-secondary">Diagnóstico estadístico · Chi-cuadrado (χ²)</h6>
              <div class="alert alert-warning border mt-2 mb-0 small">
                <strong>Sin datos:</strong> No existen respuestas suficientes registradas para ejecutar la prueba de independencia Chi-cuadrado (N = 0).
              </div>
            </div>
          </div>
        </div>`);
    } else {
      cont.insertAdjacentHTML('beforeend', `
        <div class="col-12 mt-3">
          <div class="card border-0 shadow-sm">
            <div class="card-body">
              <h6 class="fw-bold text-secondary">Diagnóstico estadístico · Chi-cuadrado (χ²)</h6>
              <div class="small text-muted mb-3">
                Evalua si el nivel de posgrado deseado depende de la fuente de financiamiento (recursos propios o beca), permitiendo determinar si las diferencias en la tabla responden a una relación real o únicamente al azar.
              </div>
              <div class="row g-3 mt-1">
                <div class="col-md-3">
                  <div class="border rounded p-2 h-100">
                    <div class="small text-muted">χ² calculado</div>
                    <div class="fs-4 fw-bold">${Number(chi2.chiCuadrado || 0).toFixed(3)}</div>
                  </div>
                </div>
                <div class="col-md-3">
                  <div class="border rounded p-2 h-100">
                    <div class="small text-muted">Grados de libertad (gl)</div>
                    <div class="fs-4 fw-bold">${chi2.gradosLibertad ?? 0}</div>
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