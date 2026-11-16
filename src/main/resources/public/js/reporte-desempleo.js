// Reporte: Desempleo y Barreras de Inserción Laboral
(function () {

  const COLORES = ['#dc3545', '#fd7e14', '#ffc107', '#198754', '#0d6efd', '#6f42c1', '#20c997', '#0dcaf0'];

  function esc(valor) {
    return String(valor ?? '')
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function card(titulo, inner) {
    const col = document.createElement('div');
    col.className = 'col-12';
    col.innerHTML = `
      <div class="card border-0 shadow-sm">
        <div class="card-body">
          <h6 class="fw-bold text-danger mb-3">${titulo}</h6>
          ${inner}
        </div>
      </div>`;
    return col;
  }

  function suma(filas) {
    return (filas || []).reduce((s, f) => s + (f.total || 0), 0);
  }

  function pct(parte, total) {
    return total ? (Math.round(1000 * parte / total) / 10) : 0;
  }

  function tablaFrecuencia(filas, etiqueta) {
    const total = suma(filas);
    return `
      <div class="table-responsive mt-3">
        <table class="table table-sm table-bordered align-middle mb-0">
          <thead class="table-light"><tr>
            <th>${esc(etiqueta || 'Opción')}</th>
            <th class="text-center">n</th>
            <th class="text-center">%</th>
          </tr></thead>
          <tbody>
            ${(filas || []).map(f => `<tr>
              <td>${esc(f.etiqueta)}</td>
              <td class="text-center">${f.total}</td>
              <td class="text-center">${f.porcentaje}%</td>
            </tr>`).join('')}
          </tbody>
          <tfoot class="table-light"><tr>
            <th>Total</th>
            <th class="text-center">${total}</th>
            <th class="text-center">100%</th>
          </tr></tfoot>
        </table>
      </div>`;
  }

  function addFrecuencia(cont, id, titulo, filas, horizontal) {
    const total = suma(filas);
    if (!filas || !filas.length) {
      cont.appendChild(card(`${esc(titulo)} · n=0`, '<p class="text-muted mb-0">Sin datos.</p>'));
      return;
    }
    const canvas = `<canvas id="${id}" height="${horizontal ? Math.max(160, filas.length * 34) : 150}"></canvas>`;
    cont.appendChild(card(`${esc(titulo)} <span class="badge text-bg-light">n=${total}</span>`,
      canvas + tablaFrecuencia(filas, 'Opción')));
    new Chart(document.getElementById(id), {
      type: 'bar',
      data: {
        labels: filas.map(f => f.etiqueta),
        datasets: [{
          data: filas.map(f => f.total),
          backgroundColor: filas.map((_, i) => COLORES[i % COLORES.length])
        }]
      },
      options: {
        indexAxis: horizontal ? 'y' : 'x',
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: { label: (c) => `${c.raw} (${filas[c.dataIndex].porcentaje}%)` }
          }
        },
        scales: { [horizontal ? 'x' : 'y']: { beginAtZero: true, ticks: { stepSize: 1 } } }
      }
    });
  }

  function tablaCruce(tabla, etiquetaFila, porcentajeFila) {
    if (!tabla || !tabla.filas || !tabla.filas.length || !tabla.columnas || !tabla.columnas.length) {
      return '<p class="text-muted mb-0">Sin datos suficientes para este cruce.</p>';
    }
    return `
      <div class="table-responsive">
        <table class="table table-bordered table-sm text-center align-middle mb-0">
          <thead class="table-light"><tr>
            <th class="text-start">${esc(etiquetaFila)}</th>
            ${tabla.columnas.map(c => `<th>${esc(c)}</th>`).join('')}
            <th class="table-secondary">Total</th>
          </tr></thead>
          <tbody>
            ${tabla.filas.map((fila, i) => `<tr>
              <th class="text-start">${esc(fila)}</th>
              ${tabla.columnas.map((col, j) => {
                const valor = tabla.celdas[i][j];
                const den = porcentajeFila ? tabla.totalesFila[i] : tabla.total;
                return `<td>${valor}<br><small class="text-muted">${pct(valor, den)}%</small></td>`;
              }).join('')}
              <td class="table-secondary fw-bold">${tabla.totalesFila[i]}</td>
            </tr>`).join('')}
          </tbody>
          <tfoot class="table-light"><tr>
            <th class="text-start">Total</th>
            ${tabla.totalesColumna.map(t => `<th>${t}</th>`).join('')}
            <th>${tabla.total}</th>
          </tr></tfoot>
        </table>
      </div>`;
  }

  function addCruce(cont, titulo, tabla, etiquetaFila, porcentajeFila) {
    const n = tabla ? tabla.total : 0;
    cont.appendChild(card(`${esc(titulo)} <span class="badge text-bg-light">n=${n}</span>`,
      tablaCruce(tabla, etiquetaFila, porcentajeFila)));
  }

  function addBloques(cont, bloques) {
    if (!bloques || !bloques.length) {
      cont.appendChild(card('Cruce de tres variables · n=0',
        '<p class="text-muted mb-0">Sin casos completos para el cruce de tres variables.</p>'));
      return;
    }
    bloques.forEach((bloque, i) => {
      cont.appendChild(card(
        `${esc(bloque.razon)} <span class="badge text-bg-light">n=${bloque.total}</span>`,
        `<p class="text-muted small mb-2">Años hasta el primer empleo × años de vida profesional</p>
         <div id="bloqueDesempleo${i}">${tablaCruce(bloque.tabla, 'Primer empleo', false)}</div>`));
    });
  }

  function addDemandas(cont, demandas) {
    if (!demandas || !demandas.length) {
      cont.appendChild(card('Demandas del empleador', '<p class="text-muted mb-0">Sin respuestas de empleadores.</p>'));
      return;
    }
    const inner = `
      <p class="text-muted small mb-3">Conteo de palabras clave en las respuestas de texto abierto del empleador.</p>
      <div class="row g-3">
        ${demandas.map(d => `
          <div class="col-md-4">
            <h6 class="fw-bold text-danger">${esc(d.titulo)} <span class="badge text-bg-light">n=${d.total}</span></h6>
            ${d.palabras && d.palabras.length ? `
              <table class="table table-sm table-bordered mb-0">
                <thead class="table-light"><tr>
                  <th>Palabra clave</th><th class="text-center">n</th><th class="text-center">%</th>
                </tr></thead>
                <tbody>
                  ${d.palabras.map(p => `<tr>
                    <td>${esc(p.palabra)}</td>
                    <td class="text-center">${p.total}</td>
                    <td class="text-center">${p.porcentaje}%</td>
                  </tr>`).join('')}
                </tbody>
              </table>` : '<p class="text-muted small mb-0">Sin coincidencias de palabras clave.</p>'}
          </div>`).join('')}
      </div>`;
    cont.appendChild(card('Demandas del empleador', inner));
  }

  function addResumen(cont, lineas) {
    if (!lineas || !lineas.length) return;
    cont.appendChild(card('Resumen', `
      <ul class="mb-0 small ps-3">
        ${lineas.map(linea => `<li>${esc(linea)}</li>`).join('')}
      </ul>`));
  }

  window.cargarReporteDesempleo = async function () {
    const cont = document.getElementById('contenedorDesempleo');
    if (!cont) return;
    cont.innerHTML = '<div class="col-12"><p class="text-muted">Cargando reporte...</p></div>';
    try {
      const res = await fetch('/api/reportes/desempleo');
      if (!res.ok) throw new Error('HTTP ' + res.status);
      const d = await res.json();
      cont.innerHTML = '';

      cont.insertAdjacentHTML('beforeend', `
        <div class="col-12">
          <div class="d-flex flex-wrap justify-content-between align-items-center gap-2">
            <div>
              <h6 class="fw-bold text-danger mb-1">Análisis exploratorio</h6>
              <p class="text-muted small mb-0">Desempleo, trayectoria y barreras · ${d.total} envíos · ${d.conTresVariables} con las tres variables</p>
            </div>
          </div>
        </div>`);

      addFrecuencia(cont, 'situacionDesempleoCanvas', 'Situación laboral actual', d.situacion || [], true);
      addFrecuencia(cont, 'tiempoDesempleoCanvas', 'Tiempo total de desempleo (años)', d.tiempoDesempleo || [], true);
      addFrecuencia(cont, 'razonDesempleoCanvas', 'Razón por la que no trabaja', d.razon || [], true);
      addFrecuencia(cont, 'primerEmpleoCanvas', 'Años hasta el primer empleo', d.primerEmpleo || [], true);
      addFrecuencia(cont, 'vidaProfesionalCanvas', 'Años de vida profesional', d.vidaProfesional || [], true);

      addCruce(cont, 'Situación laboral × tiempo de desempleo', d.situacionTiempo, 'Situación laboral', true);
      addCruce(cont, 'Matriz de barreras: razón de no trabajar × experiencia previa', d.razonExperiencia, 'Razón', true);
      addCruce(cont, 'Razón de no trabajar × años hasta el primer empleo', d.razonPrimerEmpleo, 'Razón', true);
      addCruce(cont, 'Razón de no trabajar × años de vida profesional', d.razonVidaProfesional, 'Razón', true);

      addBloques(cont, d.bloques || []);
      addDemandas(cont, d.demandas || []);
      addResumen(cont, d.resumen || []);
    } catch (e) {
      cont.innerHTML = '<div class="col-12"><p class="text-danger">No se pudo cargar el reporte: ' + esc(e.message) + '</p></div>';
    }
  };

})();
