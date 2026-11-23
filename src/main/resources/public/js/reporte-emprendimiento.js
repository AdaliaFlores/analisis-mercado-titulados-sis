let cargandoEmprendimiento = false;

async function cargarReporteEmprendimiento() {
  if (cargandoEmprendimiento) return;

  const cont = document.getElementById('contenedorGraficos');
  if (!cont) return;

  cargandoEmprendimiento = true;
  ReporteCarga.mostrarCargando(cont, 'Cargando datos del ecosistema de emprendimiento...', 'primary');
  try {
    const datos = await ReporteCarga.solicitarJson('/api/reportes/emprendimiento');
    cont.innerHTML = '';
    renderReporteEmprendimiento(datos);
  } catch (e) {
    ReporteCarga.mostrarError(cont, {
      error: e,
      alReintentar: cargarReporteEmprendimiento
    });
  } finally {
    cargandoEmprendimiento = false;
  }
}

function renderReporteEmprendimiento(datos) {
  const cont = document.getElementById('contenedorGraficos');
  const n = Number(datos.completos ?? datos.total ?? 0);
  const incompletos = Number(datos.incompletos ?? 0);

  cont.insertAdjacentHTML('beforeend', `
    <div class="col-12">
      <div class="d-flex flex-wrap justify-content-between align-items-center gap-2">
        <div>
          <h6 class="fw-bold text-primary mb-1">Análisis exploratorio</h6>
          <p class="text-muted small mb-0">Origen, tipo de entregable y financiamiento · ${n} casos completos</p>
        </div>
        ${incompletos > 0 ? `<span class="badge text-bg-warning">${incompletos} caso(s) incompleto(s)</span>` : ''}
      </div>
    </div>`);

  renderFrecuencias(cont, datos, n);
  renderCrucesDosVariables(cont, datos);
  renderCrucesTresVariables(cont, datos);
  renderResumen(cont, datos);
}

function renderResumen(cont, datos) {
  const lineas = Array.isArray(datos.resumen) ? datos.resumen.filter(Boolean) : [];
  if (!lineas.length) return;

  cont.insertAdjacentHTML('beforeend', `
    <div class="col-12 mt-3">
      <div class="card border-0 shadow-sm">
        <div class="card-body py-3">
          <h6 class="fw-bold text-primary mb-2">Resumen</h6>
          <ul class="mb-0 small ps-3">
            ${lineas.map(linea => `<li>${escapar(linea)}</li>`).join('')}
          </ul>
        </div>
      </div>
    </div>`);
}

function renderFrecuencias(cont, datos, n) {
  insertarTituloSeccion(cont, 'Frecuencias', 'Distribución de cada variable');

  const variables = [
    { titulo: 'Origen', datos: datos.origen || [], color: '#0d6efd' },
    { titulo: 'Tipo de entregable', datos: datos.entregable || [], color: '#198754' },
    { titulo: 'Financiamiento', datos: datos.financiamiento || [], color: '#ffc107' }
  ];

  variables.forEach((variable, indice) => {
    const col = document.createElement('div');
    col.className = 'col-lg-4';
    col.innerHTML = `
      <div class="card border-0 shadow-sm h-100">
        <div class="card-body">
          <div class="d-flex justify-content-between align-items-start mb-2">
            <h6 class="fw-bold text-primary mb-0">${escapar(variable.titulo)}</h6>
            <span class="badge text-bg-light">n = ${n}</span>
          </div>
          <div style="height:230px">
            <canvas id="edaFrecuencia_${indice}"></canvas>
          </div>
        </div>
      </div>`;
    cont.appendChild(col);

    if (!variable.datos.length) {
      col.querySelector('div[style]').outerHTML = '<p class="text-muted small mb-0">Sin datos.</p>';
      return;
    }

    new Chart(document.getElementById('edaFrecuencia_' + indice), {
      type: 'bar',
      data: {
        labels: variable.datos.map(d => d.etiqueta),
        datasets: [{
          data: variable.datos.map(d => d.total),
          backgroundColor: variable.color,
          borderRadius: 4
        }]
      },
      options: {
        indexAxis: 'y',
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            callbacks: {
              label: ctx => `${ctx.raw} (${porcentaje(ctx.raw, n)}%)`
            }
          }
        },
        scales: {
          x: { beginAtZero: true, ticks: { precision: 0 } },
          y: { ticks: { autoSkip: false } }
        }
      }
    });
  });
}

function renderCrucesDosVariables(cont, datos) {
  insertarTituloSeccion(cont, 'Cruces de dos variables', 'Conteo y porcentaje por fila');

  const cruces = [
    {
      titulo: 'Origen × tipo de entregable',
      tabla: datos.origenEntregable,
      fila: 'Origen',
      clase: 'col-lg-6'
    },
    {
      titulo: 'Origen × financiamiento',
      tabla: datos.origenFinanciamiento,
      fila: 'Origen',
      clase: 'col-lg-6'
    },
    {
      titulo: 'Tipo de entregable × financiamiento',
      tabla: datos.entregableFinanciamiento,
      fila: 'Tipo de entregable',
      clase: 'col-lg-12'
    }
  ];

  cruces.forEach(cruce => {
    const col = document.createElement('div');
    col.className = cruce.clase;
    col.innerHTML = `
      <div class="card border-0 shadow-sm h-100">
        <div class="card-body">
          <h6 class="fw-bold text-primary mb-3">${escapar(cruce.titulo)}</h6>
          <div class="table-responsive">
            ${tablaCruceHTML(cruce.tabla, cruce.fila, 'fila')}
          </div>
        </div>
      </div>`;
    cont.appendChild(col);
  });
}

function renderCrucesTresVariables(cont, datos) {
  insertarTituloSeccion(
    cont,
    'Cruce de tres variables',
    'Cada bloque corresponde a un tipo de financiamiento · conteo y porcentaje del bloque'
  );

  const bloques = datos.bloques || [];
  if (!bloques.length) {
    cont.insertAdjacentHTML('beforeend', '<div class="col-12"><p class="text-muted small mb-0">Sin datos.</p></div>');
    return;
  }

  bloques.forEach(bloque => {
    const col = document.createElement('div');
    col.className = 'col-lg-4';
    col.innerHTML = `
      <div class="card border-0 shadow-sm h-100">
        <div class="card-body">
          <div class="d-flex justify-content-between align-items-start gap-2 mb-3">
            <h6 class="fw-bold text-primary mb-0">${escapar(bloque.financiamiento)}</h6>
            <span class="badge text-bg-light">n = ${bloque.total}</span>
          </div>
          <div class="table-responsive">
            ${tablaCruceHTML(bloque.tabla, 'Origen', 'bloque')}
          </div>
        </div>
      </div>`;
    cont.appendChild(col);
  });
}

function insertarTituloSeccion(cont, titulo, subtitulo) {
  cont.insertAdjacentHTML('beforeend', `
    <div class="col-12 mt-3">
      <h6 class="fw-bold text-primary mb-1">${escapar(titulo)}</h6>
      <p class="text-muted small mb-0">${escapar(subtitulo)}</p>
    </div>`);
}

function tablaCruceHTML(tabla, etiquetaFila, base) {
  if (!tabla || !tabla.filas || !tabla.columnas) {
    return '<p class="text-muted small mb-0">Sin datos.</p>';
  }

  const celdas = tabla.celdas || [];
  const totalesFila = tabla.totalesFila || [];
  const totalesColumna = tabla.totalesColumna || [];
  const total = Number(tabla.total || 0);
  const maximo = Math.max(0, ...celdas.flat().map(Number));

  let html = '<table class="table table-sm table-bordered align-middle mb-0">';
  html += '<thead class="table-light"><tr>';
  html += `<th>${escapar(etiquetaFila)}</th>`;
  tabla.columnas.forEach(columna => {
    html += `<th class="text-end">${escapar(columna)}</th>`;
  });
  html += '<th class="text-end">Total</th></tr></thead><tbody>';

  tabla.filas.forEach((fila, i) => {
    html += `<tr><th scope="row" class="fw-semibold">${escapar(fila)}</th>`;
    tabla.columnas.forEach((columna, j) => {
      const valor = Number(celdas[i]?.[j] ?? 0);
      const denominador = base === 'fila' ? Number(totalesFila[i] || 0) : total;
      html += `<td class="text-end" style="${fondoCalor(valor, maximo)}">${valor}`;
      html += `<br><small class="text-muted">${porcentaje(valor, denominador)}%</small></td>`;
    });
    html += `<td class="text-end fw-semibold">${Number(totalesFila[i] || 0)}</td></tr>`;
  });

  html += '<tr class="table-light"><th>Total</th>';
  tabla.columnas.forEach((columna, j) => {
    html += `<td class="text-end fw-semibold">${Number(totalesColumna[j] || 0)}</td>`;
  });
  html += `<td class="text-end fw-bold">${total}</td></tr>`;
  html += '</tbody></table>';
  return html;
}

function fondoCalor(valor, maximo) {
  if (!valor || !maximo) return '';
  const intensidad = Math.min(0.32, 0.06 + (valor / maximo) * 0.26);
  return `background: rgba(13, 110, 253, ${intensidad.toFixed(3)});`;
}

function porcentaje(parte, total) {
  if (!total) return '0.0';
  return (Number(parte) * 100 / Number(total)).toFixed(1);
}

function escapar(s) {
  return String(s == null ? '' : s)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
}
