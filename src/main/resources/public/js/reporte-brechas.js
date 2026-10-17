// Nombre legible de cada pregunta
const ETIQUETAS = {
  28: 'Razón por la que no trabaja en su área',
  29: '¿Ha tenido algún trabajo antes?',
  36: 'Tiempo en conseguir el primer empleo',
  37: 'Cantidad de empleos desde que se tituló'
};

// Orden "humano" de las opciones (no alfabético)
const ORDEN = {
  36: ['Ya trabajaba antes de titularse','Menos de 1 mes','Entre 1-4 meses','Entre 4-8 meses','Entre 8-12 meses','Más de 12 meses'],
  37: ['1','2','3','Más de 3'],
  29: ['SI','NO'],
  28: ['Demanda laboral inexistente','Incumplimiento de requisitos de experiencia previa',
       'Incumplimiento de requisitos en habilidades específicas','Red de contactos limitada',
       'Problemas de presentación del CV','Habilidades deficientes en entrevistas',
       'Ofertas laborales con remuneración muy baja','Otro']
};

// NOTA: se llama COLORES_CRUCE (no COLORES) para no chocar con reporte-emprendimiento.js
const COLORES_CRUCE = ['#0d6efd','#198754','#ffc107','#dc3545','#6f42c1','#0dcaf0','#fd7e14','#20c997'];

async function cargarReporteBrechas() {
  const cont = document.getElementById('contenedorBrechas');
  cont.innerHTML = '<p class="text-muted">Cargando reporte...</p>';
  try {
    cont.innerHTML = '';
    // Cruce B: sin filtros
    await renderCruce(36, 37, cont);

    // Cruce A ESTRICTO: Q14 = "Actualmente NO trabajo" Y Q29 = "NO" (sin experiencia)
    const filtroEstricto =
      '&fp1=14&fv1=' + encodeURIComponent('Actualmente NO trabajo') +
      '&fp2=29&fv2=' + encodeURIComponent('NO');

    // x = razón (Q28), y = experiencia (Q29)
    await renderCruce(28, 29, cont, filtroEstricto);
  } catch (e) {
    cont.innerHTML = '<p class="text-danger">No se pudo cargar el reporte.</p>';
  }
}

// Dibuja UNA tabla de contingencia + sus barras agrupadas
async function renderCruce(x, y, cont, filtros = '') {
  const res = await fetch(`/api/reportes/cruce?x=${x}&y=${y}${filtros}`);
  if (!res.ok) throw new Error();
  const celdas = await res.json();

  // Valores presentes en cada eje, respetando el orden definido
  let ordenX = (ORDEN[x] || []).filter(v => celdas.some(c => c.valorX === v));
  let ordenY = (ORDEN[y] || []).filter(v => celdas.some(c => c.valorY === v));
  celdas.forEach(c => { if (!ordenX.includes(c.valorX)) ordenX.push(c.valorX); });
  celdas.forEach(c => { if (!ordenY.includes(c.valorY)) ordenY.push(c.valorY); });

  const celda = (vx, vy) => {
    const c = celdas.find(c => c.valorX === vx && c.valorY === vy);
    return c ? c.total : 0;
  };

  const canvasId = `cruce_${x}_${y}`;
  const col = document.createElement('div');
  col.className = 'col-12';
  col.innerHTML = `
    <div class="card border-0 shadow-sm">
      <div class="card-body">
        <h6 class="fw-bold text-warning">${ETIQUETAS[x]} × ${ETIQUETAS[y]}</h6>
        <canvas id="${canvasId}" height="110"></canvas>
        <div class="table-responsive mt-3">
          <table class="table table-bordered table-sm text-center align-middle mb-0">
            <thead class="table-light">
              <tr>
                <th class="text-start">${ETIQUETAS[x]} \\ ${ETIQUETAS[y]}</th>
                ${ordenY.map(vy => `<th>${vy}</th>`).join('')}
                <th class="table-secondary">Total</th>
              </tr>
            </thead>
            <tbody>
              ${ordenX.map(vx => {
                const totalFila = ordenY.reduce((s, vy) => s + celda(vx, vy), 0);
                return `<tr>
                  <th class="text-start">${vx}</th>
                  ${ordenY.map(vy => `<td>${celda(vx, vy)}</td>`).join('')}
                  <td class="table-secondary fw-bold">${totalFila}</td>
                </tr>`;
              }).join('')}
            </tbody>
          </table>
        </div>
      </div>
    </div>`;
  cont.appendChild(col);

  new Chart(document.getElementById(canvasId), {
    type: 'bar',
    data: {
      labels: ordenX,
      datasets: ordenY.map((vy, i) => ({
        label: vy,
        data: ordenX.map(vx => celda(vx, vy)),
        backgroundColor: COLORES_CRUCE[i % COLORES_CRUCE.length]
      }))
    },
    options: {
      scales: { y: { beginAtZero: true, ticks: { stepSize: 1 } } }
    }
  });
}
