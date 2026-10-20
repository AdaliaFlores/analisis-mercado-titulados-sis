
(() => {
    'use strict';

    const API_FRECUENCIAS = '/api/reportes/hu12/frecuencias';
    const API_alumno = '/api/reportes/hu12/alumno';

    const COLORES = [
        '#0d6efd', '#198754', '#ffc107', '#dc3545',
        '#6f42c1', '#0dcaf0', '#fd7e14', '#20c997',
        '#6610f2', '#6c757d'
    ];

    const PREGUNTAS_DEMANDA = [
        { id: 54, titulo: 'Contratación de ingenieros en los últimos 5 años' },
        { id: 55, titulo: 'Posibilidad de futuras contrataciones' },
        { id: 56, titulo: 'Formación académica requerida' },
        { id: 57, titulo: 'Medios de convocatoria' },
        { id: 58, titulo: 'Cargos desempeñados por los titulados' },
        { id: 59, titulo: 'Áreas de conocimiento y tendencias emergentes' },
        { id: 60, titulo: 'Herramientas tecnológicas requeridas' },
        { id: 61, titulo: 'Habilidades y competencias fundamentales' }
    ];

    const PREGUNTAS_PERCEPCION = [
        { id: 62, titulo: 'Confianza en la formación profesional' },
        { id: 63, titulo: 'Consistencia del título con las competencias' },
        { id: 64, titulo: 'Consulta sobre el perfil profesional' },
        { id: 65, titulo: 'Conocimiento del perfil de egreso' },
        { id: 66, titulo: 'Coherencia del perfil con el desempeño laboral' },
        { id: 67, titulo: 'Formación y desempeño profesional integral' },
        { id: 68, titulo: 'Incorporación de necesidades del sector' },
        { id: 69, titulo: 'Comunicación, análisis y trabajo en equipo' },
        { id: 70, titulo: 'Ética, responsabilidad social e inclusión' },
        { id: 71, titulo: 'Participación en evaluación de titulados' },
        { id: 72, titulo: 'Vínculos con organizaciones y oportunidades laborales' },
        { id: 73, titulo: 'Actividades de vinculación laboral y tecnológica' },
        { id: 74, titulo: 'Difusión de oportunidades laborales y pasantías' },
        { id: 75, titulo: 'Encuentros entre empleadores y comunidad académica' },
        { id: 76, titulo: 'Retroalimentación sobre el desempeño profesional' },
        { id: 77, titulo: 'Consulta sobre la pertinencia de la formación' },
        { id: 78, titulo: 'La UMSS como fuente de talento profesional' },
        { id: 79, titulo: 'Participación en autoevaluación y mejora continua' }
    ];

    const $ = id => document.getElementById(id);
    const graficos = new Map();
    let versionCarga = 0;

    function escapar(valor) {
        return String(valor ?? '').replace(/[&<>"']/g, caracter => ({
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#39;'
        })[caracter]);
    }

    function numero(valor) {
        const n = Number(valor);
        return Number.isFinite(n) ? n : 0;
    }

    async function consultar(url) {
        const respuesta = await fetch(url);

        if (!respuesta.ok) {
            const detalle = await respuesta.text();
            throw new Error(detalle || `Error HTTP ${respuesta.status}`);
        }

        return respuesta.json();
    }

    function destruirGraficos() {
        for (const grafico of graficos.values()) {
            grafico.destroy();
        }
        graficos.clear();
    }

    function mensaje(contenedor, texto, tipo = 'info') {
        contenedor.innerHTML = `
            <div class="col-12">
                <div class="alert alert-${tipo} mb-0">
                    ${escapar(texto)}
                </div>
            </div>`;
    }

    // Admite las propiedades más habituales del JSON del endpoint.
    function normalizarCategorias(datos) {
        const categorias = Array.isArray(datos?.categorias)
            ? datos.categorias
            : [];

        return categorias.map(item => ({
            etiqueta: String(
                item.respuesta ??
                item.categoria ??
                item.valor ??
                item.nombre ??
                'Sin categoría'
            ),
            cantidad: numero(
                item.total ??
                item.cantidad ??
                item.frecuencia ??
                item.count
            ),
            porcentaje: item.porcentaje == null
                ? null
                : numero(item.porcentaje)
        })).filter(item => item.cantidad >= 0);
    }

    function crearTarjeta(pregunta, datos, indice, prefijo) {
        const categorias = normalizarCategorias(datos);
        const suma = categorias.reduce((total, c) => total + c.cantidad, 0);
        const total = numero(datos?.total) || suma;
        const idCanvas = `${prefijo}_grafico_${pregunta.id}`;

        const columna = document.createElement('div');
        columna.className = 'col-12 col-lg-6';

        columna.innerHTML = `
            <div class="card border-0 shadow-sm h-100">
                <div class="card-body">
                    <div class="d-flex justify-content-between gap-2 mb-3">
                        <h6 class="fw-bold mb-0">
                            ${pregunta.id}. ${escapar(pregunta.titulo)}
                        </h6>
                        <span class="badge text-bg-secondary align-self-start">
                            N = ${total}
                        </span>
                    </div>
                    ${
                        categorias.length
                        ? `
                            <canvas id="${idCanvas}" height="180"></canvas>
                            <div class="table-responsive mt-3">
                                <table class="table table-sm table-striped align-middle mb-0">
                                    <thead>
                                        <tr>
                                            <th>Respuesta</th>
                                            <th class="text-end">Cantidad</th>
                                            <th class="text-end">Porcentaje</th>
                                        </tr>
                                    </thead>
                                    <tbody>
                                        ${categorias.map(c => {
                                            const porcentaje = c.porcentaje == null
                                                ? (suma ? c.cantidad * 100 / suma : 0)
                                                : c.porcentaje;

                                            return `
                                                <tr>
                                                    <td>${escapar(c.etiqueta)}</td>
                                                    <td class="text-end">${c.cantidad}</td>
                                                    <td class="text-end">
                                                        ${porcentaje.toFixed(1)}%
                                                    </td>
                                                </tr>`;
                                        }).join('')}
                                    </tbody>
                                </table>
                            </div>`
                        : '<p class="text-muted mb-0">Sin respuestas para esta pregunta.</p>'
                    }
                </div>
            </div>`;

        if (categorias.length && window.Chart) {
            const canvas = columna.querySelector(`#${idCanvas}`);
            const esTextoAbierto = [59, 60, 61].includes(pregunta.id);

            // Para respuestas abiertas se limita el gráfico a las diez
            // categorías más frecuentes. La tabla conserva todas.
            const datosGrafico = esTextoAbierto
                ? [...categorias]
                    .sort((a, b) => b.cantidad - a.cantidad)
                    .slice(0, 10)
                : categorias;

            const grafico = new Chart(canvas, {
                type: 'bar',
                data: {
                    labels: datosGrafico.map(c => c.etiqueta),
                    datasets: [{
                        label: 'Respuestas',
                        data: datosGrafico.map(c => c.cantidad),
                        backgroundColor: datosGrafico.map(
                            (_, i) => COLORES[i % COLORES.length]
                        )
                    }]
                },
                options: {
                    indexAxis: 'y',
                    responsive: true,
                    maintainAspectRatio: true,
                    plugins: {
                        legend: { display: false },
                        tooltip: {
                            callbacks: {
                                label: contexto => {
                                    const item = datosGrafico[contexto.dataIndex];
                                    const pct = suma
                                        ? (item.cantidad * 100 / suma).toFixed(1)
                                        : '0.0';

                                    return `${item.cantidad} respuestas (${pct}%)`;
                                }
                            }
                        }
                    },
                    scales: {
                        x: {
                            beginAtZero: true,
                            ticks: { precision: 0 }
                        }
                    }
                }
            });

            graficos.set(idCanvas, grafico);
        }

        return columna;
    }

    async function cargarGrupo(preguntas, contenedor, prefijo, version) {
        contenedor.innerHTML = `
            <div class="col-12">
                <p class="text-muted">Cargando preguntas...</p>
            </div>`;

        const resultados = await Promise.all(
            preguntas.map(async pregunta => {
                try {
                    const url = `${API_FRECUENCIAS}?encuesta=2&pregunta=${pregunta.id}`;
                    const datos = await consultar(url);
                    return { pregunta, datos, error: null };
                } catch (error) {
                    return { pregunta, datos: null, error };
                }
            })
        );

        // Evita dibujar resultados de una carga anterior.
        if (version !== versionCarga) return;

        contenedor.innerHTML = '';

        let fallos = 0;

        resultados.forEach(({ pregunta, datos, error }, indice) => {
            if (error) {
                fallos++;
                const col = document.createElement('div');
                col.className = 'col-12 col-lg-6';
                col.innerHTML = `
                    <div class="alert alert-danger h-100 mb-0">
                        <strong>Pregunta ${pregunta.id}:</strong>
                        no se pudo cargar la información.
                        <div class="small mt-1">${escapar(error.message)}</div>
                    </div>`;
                contenedor.appendChild(col);
                return;
            }

            contenedor.appendChild(
                crearTarjeta(pregunta, datos, indice, prefijo)
            );
        });

        if (fallos) {
            const aviso = document.createElement('div');
            aviso.className = 'col-12';
            aviso.innerHTML = `
                <div class="alert alert-warning mb-0">
                    No se pudieron cargar ${fallos} pregunta(s).
                    Comprueba la conexión con el servidor y vuelve a intentarlo.
                </div>`;
            contenedor.prepend(aviso);
        }
    }

    // Muestra los datos del endpoint alumno sin asumir nombres de propiedades
    // que todavía no hemos confirmado en su respuesta JSON.
    function mostraralumno(datos, contenedor) {
        contenedor.innerHTML = '';

        if (datos == null || typeof datos !== 'object') {
            mensaje(contenedor, 'El endpoint alumno no devolvió datos válidos.', 'warning');
            return;
        }

        const resumen = datos.resumen &&
                        typeof datos.resumen === 'object' &&
                        !Array.isArray(datos.resumen)
            ? datos.resumen
            : datos;

        const excluidos = new Set([
            'contactos', 'alumno', 'listado', 'personas', 'detalle',
            'resumen'
        ]);

        const indicadores = Object.entries(resumen).filter(
            ([clave, valor]) =>
                !excluidos.has(clave.toLowerCase()) &&
                (typeof valor !== 'object' || valor === null)
        );

        const seccionResumen = document.createElement('div');
        seccionResumen.className = 'row g-3 mb-4';

        if (indicadores.length) {
            seccionResumen.innerHTML = indicadores.map(([clave, valor]) => `
                <div class="col-6 col-md-4">
                    <div class="card border-0 shadow-sm h-100">
                        <div class="card-body">
                            <div class="small text-muted">${escapar(clave)}</div>
                            <div class="fs-4 fw-bold text-primary">
                                ${escapar(valor ?? '—')}
                            </div>
                        </div>
                    </div>
                </div>
            `).join('');
            contenedor.appendChild(seccionResumen);
        }

        const listaContactos = [
            datos.contactos,
            datos.alumno,
            datos.listado,
            datos.personas
        ].find(Array.isArray);

        if (listaContactos?.length) {
            const aviso = document.createElement('div');
            aviso.className = 'alert alert-info small';
            aviso.textContent =
                'Los datos de contacto deben mostrarse únicamente a usuarios autorizados.';
            contenedor.appendChild(aviso);

            const titulo = document.createElement('h6');
            titulo.className = 'fw-bold mt-3';
            titulo.textContent = 'Listado alumno';
            contenedor.appendChild(titulo);

            const tabla = document.createElement('div');
            tabla.className = 'table-responsive';

            const columnas = [...new Set(
                listaContactos.flatMap(persona =>
                    Object.keys(persona || {})
                )
            )];

            tabla.innerHTML = `
                <table class="table table-bordered table-sm align-middle">
                    <thead class="table-light">
                        <tr>${columnas.map(c =>
                            `<th>${escapar(c)}</th>`
                        ).join('')}</tr>
                    </thead>
                    <tbody>
                        ${listaContactos.map(persona => `
                            <tr>${columnas.map(c => `
                                <td>${escapar(persona?.[c] ?? '—')}</td>
                            `).join('')}</tr>
                        `).join('')}
                    </tbody>
                </table>`;
            contenedor.appendChild(tabla);
        } else if (!indicadores.length) {
            const pre = document.createElement('pre');
            pre.className = 'small bg-light border rounded p-3';
            pre.textContent = JSON.stringify(datos, null, 2);
            contenedor.appendChild(pre);
        }

        if (!indicadores.length && !listaContactos?.length) {
            mensaje(
                contenedor,
                'No se encontraron indicadores ni contactos en la respuesta alumno.',
                'warning'
            );
        }
    }

    async function cargaralumno(version) {
        const contenedor = $('hu12Contenidoalumno');
        contenedor.innerHTML = '<p class="text-muted">Cargando información alumno...</p>';

        try {
            const datos = await consultar(API_alumno);
            if (version !== versionCarga) return;
            mostraralumno(datos, contenedor);
        } catch (error) {
            if (version !== versionCarga) return;
            mensaje(
                contenedor,
                `No se pudo cargar la información alumno: ${error.message}`,
                'danger'
            );
        }
    }

    window.cargarReporteHu12 = async function () {
        const version = ++versionCarga;
        destruirGraficos();

        const demanda = $('hu12ContenidoDemanda');
        const percepcion = $('hu12ContenidoPercepcion');

        if (!demanda || !percepcion || !$('hu12Contenidoalumno')) {
            console.error('No se encontraron los contenedores del módulo HU12.');
            return;
        }

        demanda.innerHTML = '<p class="text-muted">Cargando demanda empresarial...</p>';
        percepcion.innerHTML = '<p class="text-muted">Cargando percepción de empleadores...</p>';

        await Promise.all([
            cargarGrupo(PREGUNTAS_DEMANDA, demanda, 'demanda', version),
            cargarGrupo(PREGUNTAS_PERCEPCION, percepcion, 'percepcion', version),
            cargaralumno(version)
        ]);
    };
})();
