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

  // Comparativo Spin-offs vs Startups (pregunta 30)
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

  renderResumen(porPregunta);
}

function renderResumen(porPregunta) {
  const cont = document.getElementById('contenedorGraficos');
  if (!cont) return;

  const lista = (pid) => porPregunta[pid] || [];
  const suma = (arr) => arr.reduce((a, x) => a + x.total, 0);
  const buscar = (arr, txt) => arr.find(d => (d.respuesta || '').toLowerCase().includes(txt));
  const pct = (g, t) => (t ? (g * 100 / t) : 0);
  const f1 = (n) => n.toFixed(1) + '%';

  const d30 = lista(30);
  const t30 = suma(d30);
  const spin = buscar(d30, 'spin');
  const start = buscar(d30, 'start');
  const idea = buscar(d30, 'idea propia');

  const d31 = lista(31);
  const t31 = suma(d31);
  const top31 = [...d31].sort((a, b) => b.total - a.total)[0];

  const d32 = lista(32);
  const t32 = suma(d32);
  const externo = d32.filter(d => /^s[ií]/i.test((d.respuesta || '').trim())).reduce((a, x) => a + x.total, 0);
  const propio = t32 - externo;

  const d33 = lista(33);
  const t33 = suma(d33);
  const alta33 = d33.filter(d => Number(d.respuesta) >= 4).reduce((a, x) => a + x.total, 0);
  const prom33 = t33 ? d33.reduce((a, x) => a + (Number(x.respuesta) || 0) * x.total, 0) / t33 : 0;

  const d34 = lista(34);
  const t34 = suma(d34);
  const alta34 = d34.filter(d => Number(d.respuesta) >= 4).reduce((a, x) => a + x.total, 0);

  const bullets = [];
  if (t30) bullets.push(`<li><strong>Universo analizado:</strong> ${t30} emprendimiento(s) registrados por los titulados.</li>`);
  if (t30) {
    const partes = [];
    if (spin) partes.push(`Spin Off <strong>${f1(pct(spin.total, t30))}</strong>`);
    if (idea) partes.push(`Idea propia <strong>${f1(pct(idea.total, t30))}</strong>`);
    if (start) partes.push(`StartUp <strong>${f1(pct(start.total, t30))}</strong>`);
    if (partes.length) bullets.push(`<li><strong>Origen:</strong> ${partes.join(' · ')}. ${(spin && spin.total >= (start ? start.total : 0)) ? 'Predomina la iniciativa derivada de proyectos universitarios (Spin Off).' : 'Predomina la StartUp.'}</li>`);
  }
  if (top31) bullets.push(`<li><strong>Tipo de entregable predominante:</strong> ${escapar(top31.respuesta)} (${top31.total} de ${t31}, ${f1(pct(top31.total, t31))}).</li>`);
  if (t32) bullets.push(`<li><strong>Financiamiento externo:</strong> ${f1(pct(externo, t32))} accedió a financiamiento (inversores/banca) y ${f1(pct(propio, t32))} trabajó con recursos propios.</li>`);
  if (t33) bullets.push(`<li><strong>Satisfacción con el rendimiento:</strong> promedio <strong>${prom33.toFixed(1)}/5</strong>; ${f1(pct(alta33, t33))} calificó con 4 o 5.</li>`);
  if (t34) bullets.push(`<li><strong>Importancia de la formación:</strong> ${f1(pct(alta34, t34))} la valoró con 4 o 5, lo que respalda el fortalecimiento de competencias emprendedoras.</li>`);

  const recomendaciones = [];
  if (t32 && propio > externo) recomendaciones.push('Ampliar el acceso a fuentes de financiamiento externo (inversores ángeles, fondos y banca) ya que la mayoría depende de recursos propios.');
  if (spin) recomendaciones.push('Consolidar la vinculación universidad–emprendimiento para escalar los Spin Off surgidos de proyectos de la carrera.');
  if (start) recomendaciones.push('Reforzar el acompañamiento (mentoría e incubación) a las StartUps en etapas tempranas.');
  if (t33 && alta33 * 2 < t33) recomendaciones.push('Atender los casos de baja satisfacción con el rendimiento mediante seguimiento individualizado.');
  if (t34) recomendaciones.push('Incorporar formación práctica en emprendimiento e innovación en el plan de estudios.');

  const ulRecom = recomendaciones.length
    ? `<ul class="mb-0 small">${recomendaciones.map(r => `<li>${r}</li>`).join('')}</ul>`
    : '<p class="small text-muted mb-0">Sin recomendaciones por ahora (faltan datos).</p>';

  cont.insertAdjacentHTML('beforeend', `
    <div class="col-12 mt-3">
      <div class="card border-0 shadow-sm">
        <div class="card-body">
          <h6 class="fw-bold text-primary">Resumen y recomendación</h6>
          <ul class="mb-3 small">${bullets.join('') || '<li class="text-muted">Sin respuestas todavía.</li>'}</ul>
          <h6 class="fw-bold text-primary">Recomendaciones para el Plan de Desarrollo Estratégico</h6>
          ${ulRecom}
        </div>
      </div>
    </div>`);
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