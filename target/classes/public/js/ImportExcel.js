document.addEventListener('DOMContentLoaded', () => {
    const formImportar = document.getElementById('formImportarExcel');
    const inputExcel = document.getElementById('inputExcel');
    const btnSubir = document.getElementById('btnSubirExcel');
    const mensajeDiv = document.getElementById('mensajeImportacion');

    if (formImportar) {
        formImportar.addEventListener('submit', async (event) => {
            event.preventDefault();

            const archivo = inputExcel.files[0];
            if (!archivo) {
                mostrarMensaje('Por favor, seleccione un archivo Excel.', 'danger');
                return;
            }

            // Preparar FormData (el campo "archivoExcel" debe coincidir con ctx.uploadedFile("archivoExcel") en Javalin)
            const formData = new FormData();
            formData.append('archivoExcel', archivo);

            // Feedback visual: deshabilitar botón mientras procesa
            btnSubir.disabled = true;
            btnSubir.innerHTML = 'Procesando e importando...';
            mensajeDiv.style.display = 'none';

            try {
                const response = await fetch('/api/import/excel', {
                    method: 'POST',
                    body: formData
                });

                const resultado = await response.json();

                if (response.ok && resultado.status === 'success') {
                    mostrarMensaje(resultado.mensaje, 'success');
                    formImportar.reset();
                    
                    // Si tienes funciones para recargar los gráficos en archivo.js, invócalas aquí:
                    // p.ej., cargarGraficosDashboard();
                } else {
                    mostrarMensaje(resultado.mensaje || 'Ocurrió un error al procesar el archivo.', 'danger');
                }
            } catch (error) {
                console.error('Error al subir el archivo:', error);
                mostrarMensaje('Error de conexión con el servidor.', 'danger');
            } finally {
                btnSubir.disabled = false;
                btnSubir.innerHTML = 'Subir e Importar';
            }
        });
    }

    function mostrarMensaje(texto, tipo) {
        mensajeDiv.className = `alert alert-${tipo} mt-3`;
        mensajeDiv.textContent = texto;
        mensajeDiv.style.display = 'block';
    }
});

document.addEventListener('DOMContentLoaded', () => {
    const formImportarEmp = document.getElementById('formImportarEmpleadores');
    const inputExcelEmp = document.getElementById('inputExcelEmpleadores');
    const btnSubirEmp = document.getElementById('btnSubirEmpleadores');
    const mensajeDivEmp = document.getElementById('mensajeImportacionEmpleadores');

    if (formImportarEmp) {
        formImportarEmp.addEventListener('submit', async (event) => {
            event.preventDefault();

            const archivo = inputExcelEmp.files[0];
            if (!archivo) {
                mostrarMensaje('Por favor, seleccione un archivo Excel.', 'danger');
                return;
            }

            const formData = new FormData();
            formData.append('archivoExcel', archivo);

            btnSubirEmp.disabled = true;
            btnSubirEmp.innerHTML = 'Procesando e importando...';
            mensajeDivEmp.style.display = 'none';

            try {
                const response = await fetch('/api/import/excel/empleadores', {
                    method: 'POST',
                    body: formData
                });

                const resultado = await response.json();

                if (response.ok && resultado.status === 'success') {
                    mostrarMensaje(resultado.mensaje, 'success');
                    formImportarEmp.reset();
                    
                    // Si tienes funciones para recargar los gráficos de empleadores, invócalas aquí:
                    // p.ej., cargarGraficosEmpleadores();
                } else {
                    mostrarMensaje(resultado.mensaje || 'Error al procesar el archivo de empleadores.', 'danger');
                }
            } catch (error) {
                console.error('Error al subir el archivo:', error);
                mostrarMensaje('Error de conexión con el servidor.', 'danger');
            } finally {
                btnSubirEmp.disabled = false;
                btnSubirEmp.innerHTML = 'Subir e Importar Empleadores';
            }
        });
    }

    function mostrarMensaje(texto, tipo) {
        mensajeDivEmp.className = `alert alert-${tipo} mt-3`;
        mensajeDivEmp.textContent = texto;
        mensajeDivEmp.style.display = 'block';
    }
});