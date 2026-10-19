(function () {
  'use strict';

  const COLORES = ['#0d6efd', '#198754', '#ffc107', '#dc3545', '#6f42c1', '#0dcaf0', '#fd7e14', '#20c997', '#6610f2', '#d63384'];

  let charts = [];

  window.cargarReporteEmpleabilidad = async function () {
    const cont = document.getElementById('contenedorEmpleabilidad');
    cont.innerHTML = '<p class="text-muted">Cargando reporte...</p>';
    charts.forEach(c => { try { c.destroy(); } catch (e) {} });
    charts = [];

    let d;
    try {
      const res = await fetch('/api/reportes/empleabilidad');
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
          <strong class="text-primary">Análisis de empleabilidad y ofertas con poca demanda.</strong>
          <span class="badge bg-primary-subtle text-primary-emphasis border border-primary-subtle">Áreas con mayor empleabilidad</span>
          = (Demanda empleador + Interés de titulados) / y asignaturas de ventaja.
          <span class="badge bg-danger-subtle text-danger-emphasis border border-danger-subtle">Oferta con poca demanda</span>
          = causas críticas, asignaturas de poca utilidad y rubros sin contratación prevista.
        </div>
      </div>`);

    renderEmpleabilidadBarras(cont, d.areasEmpleabilidad);
    renderPocaDemandaEjes(cont, d);
    renderResumen(cont, d);
  };

  // Barras verticales: Áreas con mayor empleabilidad
  function renderEmpleabilidadBarras(cont, areas) {
    if (!areas || !areas.length) return;

    // Top 10 para visualización clara
    let datos = areas.slice();
    if (datos.length > 10) datos = datos.slice(0, 10);

    cont.insertAdjacentHTML('beforeend', `
      <div class="col-12">
        <div class="card border-0 shadow-sm">
          <div class="card-body">
            <h6 class="fw-bold text-primary">Áreas con mayor empleabilidad (barras verticales)</h6>
            <canvas id="graficoEmpleabilidad" height="160"></canvas>
            <div class="table-responsive mt-3">
              <table class="table table-sm table-bordered text-center align-middle mb-0">
                <thead class="table-light">
                  <tr><th class="text-start">Área</th><th>Demanda empleador (%)</th><th>Interés titulados (%)</th><th>Empleabilidad (%)</th></tr>
                </thead>
                <tbody>
                  ${datos.map(a => `
                    <tr>
                      <td class="text-start">${escapar(a.area)}</td>
                      <td>${a.demandaEmpleador}</td>
                      <td>${a.interesTitulados}</td>
                      <td class="fw-bold">${a.empleabilidad}</td>
                    </tr>
                  `).join('')}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>`);

    charts.push(new Chart(document.getElementById('graficoEmpleabilidad'), {
      type: 'bar',
      data: {
        labels: datos.map(a => a.area),
        datasets: [
          { label: 'Demanda empleador (%)', data: datos.map(a => a.demandaEmpleador), backgroundColor: '#dc3545aa' },
          { label: 'Interés titulados (%)', data: datos.map(a => a.interesTitulados), backgroundColor: '#0d6efd99' },
          { label: 'Empleabilidad (%)', data: datos.map(a => a.empleabilidad), backgroundColor: '#198754' }
        ]
      },
      options: {
        scales: {
          x: { ticks: { maxRotation: 40, minRotation: 30 } },
          y: { beginAtZero: true, max: 100, ticks: { callback: v => v + '%' } }
        },
        plugins: { tooltip: { callbacks: { label: c => c.dataset.label + ': ' + c.parsed.y + '%' } } }
      }
    }));
  }

  // Eje cartesiano / vista agrupada: Oferta con poca demanda
  function renderPocaDemandaEjes(cont, d) {
    const criticas = (d.razonesCriticas || []).slice(0, 6);
    const asign = (d.asignaturasPocaUtilidadTop || []).slice(0, 6);
    const contr = (d.contratacionEmpleador || []).slice(0, 5);

    cont.insertAdjacentHTML('beforeend', `
      <div class="col-12 mt-2">
        <div class="card border-0 shadow-sm">
          <div class="card-body">
            <h6 class="fw-bold text-danger">Oferta con poca demanda · Vista en eje cartesiano</h6>
            <canvas id="graficoPocaDemanda" height="150"></canvas>
          </div>
        </div>
      </div>`);

    const labels = [];
    const datosCriticas = [];
    const datosAsign = [];
    const datosSinContrat = [];

    // Eje X: combinar indicadores relevantes
    criticas.forEach(c => {
      labels.push('Razón: ' + acortar(c.respuesta, 28));
      datosCriticas.push(c.porcentaje);
      datosAsign.push(0);
      datosSinContrat.push(0);
    });

    asign.forEach(a => {
      labels.push('Poca utilidad: ' + acortar(a.respuesta, 28));
      datosCriticas.push(0);
      datosAsign.push(a.porcentaje);
      datosSinContrat.push(0);
    });

    contr.forEach(x => {
      const v = (x.respuesta || '').toLowerCase();
      if (v.includes('no') || v.includes('baja') || v.includes('limitada') || v.includes('depende') || v.includes('dudosa')) {
        labels.push('Contratación: ' + acortar(x.respuesta, 28));
        datosCriticas.push(0);
        datosAsign.push(0);
        datosSinContrat.push(x.porcentaje);
      }
    });

    if (labels.length === 0) {
      // fallback
      (d.razonesCriticas || []).slice(0, 4).forEach(c => { labels.push(acortar(c.respuesta,26)); datosCriticas.push(c.porcentaje); });
      (d.asignaturasPocaUtilidadTop || []).slice(0, 4).forEach(a => { labels.push(acortar(a.respuesta,26)); datosAsign.push(a.porcentaje); });
    }

    charts.push(new Chart(document.getElementById('graficoPocaDemanda'), {
      type: 'bar',
      data: {
        labels: labels,
        datasets: [
          { label: 'Razones críticas (%)', data: datosCriticas, backgroundColor: '#dc3545' },
          { label: 'Asignaturas poca utilidad (%)', data: datosAsign, backgroundColor: '#fd7e14' },
          { label: 'Contratación con baja/limitada previsión (%)', data: datosSinContrat, backgroundColor: '#6c757d' }
        ]
      },
      options: {
        indexAxis: 'y',
        scales: {
          x: { beginAtZero: true, max: 100, ticks: { callback: v => v + '%' } }
        },
        plugins: { tooltip: { callbacks: { label: c => c.dataset.label + ': ' + c.parsed.x + '%' } } }
      }
    }));

    // Tablas complementarias
    cont.insertAdjacentHTML('beforeend', `
      <div class="col-md-6 mt-3">
        <div class="card border-0 shadow-sm h-100">
          <div class="card-body">
            <h6 class="fw-bold text-danger">Razones críticas </h6>
            <div class="table-responsive">
              <table class="table table-sm table-bordered text-center align-middle mb-0">
                <thead class="table-light"><tr><th class="text-start">Razón</th><th>Total</th><th>%</th></tr></thead>
                <tbody>
                  ${(criticas.length ? criticas : (d.razonesCriticas||[]).slice(0,5)).map(c => `
                    <tr><td class="text-start">${escapar(c.respuesta)}</td><td>${c.total}</td><td>${c.porcentaje}%</td></tr>
                  `).join('')}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
      <div class="col-md-6 mt-3">
        <div class="card border-0 shadow-sm h-100">
          <div class="card-body">
            <h6 class="fw-bold text-danger">Asignaturas de poca utilidad · Top</h6>
            <div class="table-responsive">
              <table class="table table-sm table-bordered text-center align-middle mb-0">
                <thead class="table-light"><tr><th class="text-start">Asignatura</th><th>Total</th><th>%</th></tr></thead>
                <tbody>
                  ${asign.map(a => `
                    <tr><td class="text-start">${escapar(a.respuesta)}</td><td>${a.total}</td><td>${a.porcentaje}%</td></tr>
                  `).join('')}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      </div>
      <div class="col-12 mt-3">
        <div class="card border-0 shadow-sm">
          <div class="card-body">
            <h6 class="fw-bold text-danger">Rubros / posibilidad de contratación (empleadores)</h6>
            <div class="row g-3">
              <div class="col-md-6">
                <div class="small text-muted">Rubro principal </div>
                ${(d.rubroEmpleador||[]).map(x => `
                  <div class="d-flex justify-content-between small border-bottom py-1">
                    <span>${escapar(x.respuesta)}</span><span class="badge text-bg-light border">${x.total}</span>
                  </div>
                `).join('') || '<p class="text-muted small mb-0">Sin datos.</p>'}
              </div>
              <div class="col-md-6">
                <div class="small text-muted">Posibilidad de incorporar</div>
                ${(d.contratacionEmpleador||[]).map(x => `
                  <div class="d-flex justify-content-between small border-bottom py-1">
                    <span>${escapar(x.respuesta)}</span><span class="badge text-bg-light border">${x.total}</span>
                  </div>
                `).join('') || '<p class="text-muted small mb-0">Sin datos.</p>'}
              </div>
            </div>
          </div>
        </div>
      </div>`);
  }

  function renderResumen(cont, d) {
    const areas = (d.areasEmpleabilidad || []).slice(0, 5);
    let html = '<ul class="mb-0 small">';
    if (areas.length) {
      html += `<li><strong>Áreas con mayor empleabilidad</strong>: ${areas.map(a => `${escapar(a.area)} <strong>${a.empleabilidad}%</strong>`).join(', ')}.</li>`;
    }
    html += `<li><strong>Oferta con poca demanda</strong>: priorizar revisión de las razones críticas, las asignaturas de poca utilidad y la posibilidad real de contratación, cruzando con las áreas de mayor empleabilidad.</li>`;
    html += '</ul>';
    cont.insertAdjacentHTML('beforeend', `
      <div class="col-12 mt-3">
        <div class="card border-0 shadow-sm">
          <div class="card-body">
            <h6 class="fw-bold text-primary">Resumen y recomendación</h6>
            ${html}
          </div>
        </div>
      </div>`);
  }

  function acortar(s, n) {
    if (!s) return '';
    return s.length > n ? s.substring(0, n - 1) + '…' : s;
  }

  function escapar(s) {
    return String(s == null ? '' : s)
      .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
      .replaceAll('"', '&quot;').replaceAll("'", '&#39;');
  }
})();