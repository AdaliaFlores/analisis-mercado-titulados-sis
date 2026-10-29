const META = {
  30: { titulo: 'Origen del emprendimiento',        tipo: 'pie' },
  31: { titulo: 'Tipo de entregable',               tipo: 'pie' },
  32: { titulo: 'Financiamiento externo',           tipo: 'pie' },
  33: { titulo: 'Satisfacción con el rendimiento',  tipo: 'bar' },
  34: { titulo: 'Importancia de la formación',      tipo: 'bar' }
};
const COLORES = ['#0d6efd', '#198754', '#ffc107', '#dc3545', '#6f42c1', '#0dcaf0'];

async function cargarReporteEmprendimiento() {
  const cont = document.getElementById('contenedorGraficos');
  cont.innerHTML = '<p class="text-muted">Cargando reporte...</p>';
  try {
    const res = await fetch('/api/reportes/emprendimiento');
    if (!res.ok) throw new Error();
    const filas = await res.json();
    cont.innerHTML = '';
    dibujarTodo(filas);
  } catch (e) {
    cont.innerHTML = '<p class="text-danger">No se pudo cargar el reporte.</p>';
  }
}

function dibujarTodo(filas) {
  const porPregunta = {};
  filas.forEach(f => {
    (porPregunta[f.preguntaId] = porPregunta[f.preguntaId] || []).push(f);
  });

  Object.keys(META).forEach(pid => {
    const datos = porPregunta[pid] || [];
    const { titulo, tipo } = META[pid];

    const col = document.createElement('div');
    col.className = 'col-md-6';
    col.innerHTML = `<div class="card border-0 shadow-sm h-100"><div class="card-body">
        <h6 class="fw-bold text-primary">${titulo}</h6>
        <canvas id="grafico_${pid}" height="230"></canvas>
      </div></div>`;
    document.getElementById('contenedorGraficos').appendChild(col);

    if (datos.length === 0) {
      col.querySelector('canvas').outerHTML = '<p class="text-muted small mb-0">Sin respuestas todavía.</p>';
      return;
    }
    dibujar('grafico_' + pid, tipo, datos);
  });

  // Comparativo Spin-offs vs Startups
    const d30 = porPregunta[30] || [];
    const spin  = d30.find(d => d.respuesta.toLowerCase().includes('spin'));
    const start = d30.find(d => d.respuesta.toLowerCase().includes('start'));
    if (spin || start) {
        const col = document.createElement('div');
        col.className = 'col-md-6';
        col.innerHTML = `<div class="card border-0 shadow-sm h-100"><div class="card-body">
            <h6 class="fw-bold text-primary">Comparativo: Spin-offs vs Startups</h6>
            <canvas id="grafico_comparativo" height="230"></canvas></div></div>`;
        document.getElementById('contenedorGraficos').appendChild(col);
        new Chart(document.getElementById('grafico_comparativo'), {
            type: 'bar',
            data: {
            labels: ['Spin Off', 'StartUp'],
            datasets: [{ data: [spin ? spin.total : 0, start ? start.total : 0],
                        backgroundColor: ['#6f42c1', '#0dcaf0'] }]
            },
            options: { plugins: { legend: { display: false } }, scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } } }
        });
    }

  renderResumen();
}

// El resumen se calcula en el servidor (CalculadoraEmprendimiento) para que la
// interfaz, el CSV, el Excel y el PDF muestren exactamente los mismos resultados.
function renderResumen() {
  const cont = document.getElementById('contenedorGraficos');
  if (!cont) return;

  fetch('/api/reportes/emprendimiento/resumen')
    .then(res => {
      if (!res.ok) throw new Error('HTTP ' + res.status);
      return res.json();
    })
    .then(datos => {
      const bullets = (datos.bullets || []).map(b => `<li>${escapar(b)}</li>`).join('');
      const recomendaciones = datos.recomendaciones || [];

      const ulRecom = recomendaciones.length
        ? `<ul class="mb-0 small">${recomendaciones.map(r => `<li>${escapar(r)}</li>`).join('')}</ul>`
        : '<p class="small text-muted mb-0">Sin recomendaciones por ahora (faltan datos).</p>';

      cont.insertAdjacentHTML('beforeend', `
        <div class="col-12 mt-3">
          <div class="card border-0 shadow-sm">
            <div class="card-body">
              <h6 class="fw-bold text-primary">Resumen y recomendación</h6>
              <ul class="mb-3 small">${bullets || '<li class="text-muted">Sin respuestas todavía.</li>'}</ul>
              <h6 class="fw-bold text-primary">Recomendaciones para el Plan de Desarrollo Estratégico</h6>
              ${ulRecom}
            </div>
          </div>
        </div>`);
    })
    .catch(() => {
      cont.insertAdjacentHTML('beforeend', `
        <div class="col-12 mt-3">
          <div class="alert alert-warning mb-0">No se pudo calcular el resumen del reporte.</div>
        </div>`);
    });
}

function escapar(s) {
  return String(s == null ? '' : s)
    .replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;').replaceAll("'", '&#39;');
}

function dibujar(canvasId, tipo, datos) {
  const total = datos.reduce((a, d) => a + d.total, 0);
  new Chart(document.getElementById(canvasId), {
    type: tipo,
    data: {
      labels: datos.map(d => d.respuesta),
      datasets: [{ data: datos.map(d => d.total), backgroundColor: COLORES }]
    },
    options: {
      plugins: {
        legend: { display: tipo === 'pie', position: 'bottom' },
        tooltip: {
          callbacks: {
            label: c => {
              const pct = total ? (c.raw * 100 / total).toFixed(1) : 0;
              return `${c.label}: ${c.raw} (${pct}%)`;   // ← el % aquí
            }
          }
        }
      },
      scales: tipo === 'bar' ? { y: { beginAtZero: true, ticks: { stepSize: 1 } } } : {}
    }
  });
}