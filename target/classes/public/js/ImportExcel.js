document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('formImportarExcel');
    const input = document.getElementById('inputExcel');
    const boton = document.getElementById('btnSubirExcel');
    const divMensaje = document.getElementById('mensajeImportacion');
    if (form) subir(form, input, boton, divMensaje, '/api/import/excel', 'Subir e Importar Titulados');
});

document.addEventListener('DOMContentLoaded', () => {
    const form = document.getElementById('formImportarEmpleadores');
    const input = document.getElementById('inputExcelEmpleadores');
    const boton = document.getElementById('btnSubirEmpleadores');
    const divMensaje = document.getElementById('mensajeImportacionEmpleadores');
    if (form) subir(form, input, boton, divMensaje, '/api/import/excel/empleadores', 'Subir e Importar Empleadores');
});

function subir(form, input, boton, divMensaje, url, etiquetaBoton) {
    form.addEventListener('submit', async (event) => {
        event.preventDefault();

        const archivo = input.files[0];
        if (!archivo) {
            mostrar(divMensaje, 'Por favor, seleccione un archivo Excel.', 'danger');
            return;
        }

        const formData = new FormData();
        formData.append('archivoExcel', archivo);

        boton.disabled = true;
        boton.innerHTML = 'Importando...';
        divMensaje.style.display = 'none';

        try {
            const response = await fetch(url, { method: 'POST', body: formData });
            const resultado = await response.json();

            if (response.ok && (resultado.status === 'success' || resultado.status === 'warning')) {
                mostrar(divMensaje, resultado.mensaje, resultado.status === 'warning' ? 'warning' : 'success');
                if (resultado.status === 'success') form.reset();
            } else {
                mostrar(divMensaje, resultado.mensaje || 'Ocurrió un error al procesar el archivo.', 'danger');
            }
        } catch (error) {
            console.error('Error al subir el archivo:', error);
            mostrar(divMensaje, 'Error de conexión con el servidor.', 'danger');
        } finally {
            boton.disabled = false;
            boton.innerHTML = etiquetaBoton;
        }
    });
}

function mostrar(div, texto, tipo) {
    div.className = 'alert alert-' + tipo + ' mt-3';
    div.textContent = texto;
    div.style.display = 'block';
}
