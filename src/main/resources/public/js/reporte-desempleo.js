// Reporte: Desempleo y Barreras de Inserción Laboral
(function () {

  const COLORES = ['#dc3545', '#fd7e14', '#ffc107', '#198754', '#0d6efd', '#6f42c1', '#20c997', '#0dcaf0'];

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

  function tablaSimple(filas) {
    return `
      <div class="table-responsive mt-3">
        <table class="table table-sm table-bordered mb-0">
          <thead class="table-light"><tr>
            <th>Opción</th><th class="text-center">Frecuencia</th><th class="text-center">%</th>
          </tr></thead>
          <tbody>
            ${filas.map(f => `<tr><td>${f.respuesta}</td>
              <td class="text-center">${f.total}</td>
              <td class="text-center">${f.porcentaje}%</td></tr>`).join('')}
          </tbody>
        </table>
      </div>`;
  }

  function addBarra(cont, id, titulo, filas, horizontal) {
    if (!filas.length) { cont.appendChild(card(titulo, '<p class="text-muted mb-0">Sin datos.</p>')); return; }
    const canvas = `<canvas id="${id}" height="${horizontal ? Math.max(160, filas.length * 34) : 120}"></canvas>`;
    cont.appendChild(card(titulo, canvas + tablaSimple(filas)));
    new Chart(document.getElementById(id), {
      type: 'bar',
      data: {
        labels: filas.map(f => f.respuesta),
        datasets: [{ data: filas.map(f => f.total), backgroundColor: filas.map((_, i) => COLORES[i % COLORES.length]) }]
      },
      options: {
        indexAxis: horizontal ? 'y' : 'x',
        plugins: {
          legend: { display: false },
          tooltip: { callbacks: { label: (c) => `${c.raw} (${filas[c.dataIndex].porcentaje}%)` } }
        },
        scales: { [horizontal ? 'x' : 'y']: { beginAtZero: true, ticks: { stepSize: 1 } } }
      }
    });
  }

  function addMatriz(cont, titulo, celdas, nota) {
    if (!celdas || celdas.length === 0) {
      cont.appendChild(card(titulo, '<p class="text-muted mb-0">Sin datos suficientes para este cruce.</p>')); return;
    }
    const xs = [...new Set(celdas.map(c => c.valorX))];
    const ys = [...new Set(celdas.map(c => c.valorY))];
    const val = (x, y) => { const c = celdas.find(c => c.valorX === x && c.valorY === y); return c ? c.total : 0; };
    const max = Math.max(...celdas.map(c => c.total));
    const color = (v) => v === 0 ? '#ffffff' : `rgba(220,53,69,${0.12 + 0.78 * (v / max)})`;

    const inner = `
      ${nota ? `<p class="text-muted small">${nota}</p>` : ''}
      <div class="table-responsive">
        <table class="table table-bordered table-sm text-center align-middle mb-0">
          <thead class="table-light"><tr>
            <th class="text-start">Razón \\ Situación</th>
            ${ys.map(y => `<th>${y}</th>`).join('')}
            <th class="table-secondary">Total</th>
          </tr></thead>
          <tbody>
            ${xs.map(x => {
              const tot = ys.reduce((s, y) => s + val(x, y), 0);
              return `<tr><th class="text-start">${x}</th>
                ${ys.map(y => `<td style="background:${color(val(x,y))}">${val(x,y)}</td>`).join('')}
                <td class="table-secondary fw-bold">${tot}</td></tr>`;
            }).join('')}
          </tbody>
        </table>
      </div>`;
    cont.appendChild(card(titulo, inner));
  }

  function addDemandas(cont, d) {
    const bloque = (titulo, filas, crudas) => `
      <div class="col-md-4">
        <h6 class="fw-bold text-danger">${titulo}</h6>
        ${filas.length ? `<table class="table table-sm table-bordered">
          <thead class="table-light"><tr><th>Palabra clave</th><th class="text-center">n</th><th class="text-center">%</th></tr></thead>
          <tbody>${filas.map(f => `<tr><td>${f.palabra}</td><td class="text-center">${f.total}</td><td class="text-center">${f.porcentaje}%</td></tr>`).join('')}</tbody>
        </table>` : '<p class="text-muted small">Sin coincidencias de palabras clave.</p>'}
        <!-- <div class="small text-muted"><b>Respuestas crudas:</b> ${crudas.length ? crudas.map(c => `"${c}"`).join(', ') : '—'}</div> -->
      </div>`;

    const inner = `
      <p class="text-muted small mb-3">Agrupación por frecuencia de palabras clave en las respuestas de texto abierto del empleador.</p>
      <div class="row g-3">
        ${bloque('Tendencias emergentes', d.tendenciasEmpleador, d.respuestasTendencias)}
        ${bloque('Herramientas tecnológicas', d.herramientasEmpleador, d.respuestasHerramientas)}
        ${bloque('Competencias fundamentales', d.competenciasEmpleador, d.respuestasCompetencias)}
      </div>`;
    cont.appendChild(card('Demandas del empleador (conteo de frecuencias)', inner));
  }

  function addKpis(cont, d) {
    const pct = (n, t) => t ? Math.round(1000 * n / t) / 10 : 0;
    const kpi = (titulo, valor, sub, color) => `
      <div class="col-6 col-md-3">
        <div class="card border-0 shadow-sm h-100">
          <div class="card-body text-center">
            <div class="fs-3 fw-bold text-${color}">${valor}</div>
            <div class="small fw-semibold">${titulo}</div>
            <div class="text-muted small">${sub}</div>
          </div>
        </div>
      </div>`;
    const col = document.createElement('div');
    col.className = 'col-12';
    col.innerHTML = `<div class="row g-3">
      ${kpi('Titulados encuestados', d.totalTitulados, 'encuesta de titulados', 'primary')}
      ${kpi('No trabajan', d.noTrabaja, pct(d.noTrabaja, d.totalTitulados) + '% del total', 'danger')}
      ${kpi('Con tiempo desempleado', d.conTiempoDesempleo, pct(d.conTiempoDesempleo, d.totalTitulados) + '% del total', 'warning')}
      ${kpi('Población objetivo', d.poblacionObjetivo, 'no trabaja o tiempo > 0', 'dark')}
    </div>`;
    cont.appendChild(col);
  }

  function addResumen(cont, d) {
    const pct = (n, t) => t ? Math.round(1000 * n / t) / 10 : 0;
    const razones = d.razones || [];
    const barrera = razones.filter(r => /experiencia|habilidades/i.test(r.respuesta)).reduce((s, r) => s + r.total, 0);
    const listTop = (arr) => (arr || []).slice(0, 3).map(x => `${x.palabra} (${x.total})`).join(', ') || 'sin coincidencias';
    const nEmp = (d.respuestasTendencias || []).length;

    const p1 = `De ${d.totalTitulados} titulados encuestados, ${d.noTrabaja} no trabajan (${pct(d.noTrabaja, d.totalTitulados)}%) y ${d.conTiempoDesempleo} reportaron tiempo de desempleo mayor a 0. La población objetivo (no trabaja o con tiempo de desempleo) es de ${d.poblacionObjetivo} titulados.`;
    const p2 = razones.length
      ? `Las razones más citadas para no trabajar son: ${razones.map(r => r.respuesta + ' (' + r.total + ')').join(', ')}. De ellas, ${barrera} corresponden a barreras de competencias o experiencia previa.`
      : `No se registraron razones de inactividad.`;
    const p3 = nEmp
      ? `Los ${nEmp} empleadores participantes señalan como demandas emergentes: ${listTop(d.tendenciasEmpleador)}; como herramientas: ${listTop(d.herramientasEmpleador)}; y como competencias: ${listTop(d.competenciasEmpleador)}.`
      : `La encuesta de empleadores aún no tiene respuestas.`;
    const p4 = `Contraste: mientras los titulados reportan barreras de experiencia o habilidades específicas, los empleadores priorizan herramientas como ${listTop(d.herramientasEmpleador)} y tendencias como ${listTop(d.tendenciasEmpleador)}, lo que sugiere una brecha entre la formación y las competencias tecnológicas demandadas.`;

    cont.appendChild(card('Resumen', [p1, p2, p3, p4].map(p => `<p class="mb-2">${p}</p>`).join('')));
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

      addKpis(cont, d);
      addBarra(cont, 'situacionCanvas', 'Situación laboral actual', d.situacionActual || [], false);
      addBarra(cont, 'tiempoCanvas', 'Tiempo total desempleado — años', d.tiempoDesempleo || [], false);
      addBarra(cont, 'razonesCanvas', 'Razón por la que no trabaja', d.razones || [], true);
      addMatriz(cont, 'Matriz de barreras: Razón de no trabajar × Experiencia previa', d.matrizBarreras || []);
      addMatriz(cont, 'Matriz de inserción: Tiempo al primer empleo × Cantidad de empleos', d.matrizInsercion || []);
      addDemandas(cont, d);
      addResumen(cont, d);
    } catch (e) {
      cont.innerHTML = '<div class="col-12"><p class="text-danger">No se pudo cargar el reporte: ' + e.message + '</p></div>';
    }
  };

})();