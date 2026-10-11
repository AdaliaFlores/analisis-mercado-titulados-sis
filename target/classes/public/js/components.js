// Inyecta el HTML del Modal dinámicamente al cargar la página
document.addEventListener('DOMContentLoaded', () => {
    const modalHTML = `
    <div class="modal fade" id="customAlertModal" tabindex="-1" aria-hidden="true">
      <div class="modal-dialog modal-dialog-centered">
        <div class="modal-content text-center p-4 border-0 shadow">
          <div class="modal-body">
            <div id="customModalIcon" class="mb-3"></div>
            <h4 id="customModalTitle" class="fw-bold mb-2"></h4>
            <p id="customModalMessage" class="text-secondary mb-4"></p>
            <button type="button" class="btn btn-primary px-4 py-2 rounded-pill fw-semibold" data-bs-dismiss="modal">
                Aceptar
            </button>
          </div>
        </div>
      </div>
    </div>`;
    
    document.body.insertAdjacentHTML('beforeend', modalHTML);
});

// Función global reutilizable para mostrar notificaciones
function mostrarNotificacion(titulo, mensaje, esExito = true) {
    const titleEl = document.getElementById('customModalTitle');
    const msgEl = document.getElementById('customModalMessage');
    const iconEl = document.getElementById('customModalIcon');

    if (!titleEl || !msgEl || !iconEl) return;

    titleEl.innerText = titulo;
    msgEl.innerText = mensaje;

    if (esExito) {
        iconEl.innerHTML = `
            <svg class="text-success" width="64" height="64" fill="currentColor" viewBox="0 0 16 16">
                <path d="M16 8A8 8 0 1 1 0 8a8 8 0 0 1 16 0zm-3.97-3.03a.75.75 0 0 0-1.08.022L7.477 9.417 5.384 7.323a.75.75 0 0 0-1.06 1.06L6.97 11.03a.75.75 0 0 0 1.079-.02l4.992-5.99a.75.75 0 0 0-.018-1.047z"/>
            </svg>`;
    } else {
        iconEl.innerHTML = `
            <svg class="text-danger" width="64" height="64" fill="currentColor" viewBox="0 0 16 16">
                <path d="M16 8A8 8 0 1 1 0 8a8 8 0 0 1 16 0zM5.354 4.646a.5.5 0 1 0-.708.708L7.293 8l-2.647 2.646a.5.5 0 0 0 .708.708L8 8.707l2.646 2.647a.5.5 0 0 0 .708-.708L8.707 8l2.647-2.646a.5.5 0 0 0-.708-.708L8 7.293 5.354 4.646z"/>
            </svg>`;
    }

    const modalElement = document.getElementById('customAlertModal');
    const bsModal = new bootstrap.Modal(modalElement);
    bsModal.show();
}