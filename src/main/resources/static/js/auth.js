document.addEventListener('DOMContentLoaded', async () => {
    const loginForm = document.getElementById('loginForm');
    const alertBox = document.getElementById('alertBox');
    const loginBtn = document.getElementById('loginBtn');

    // Check if already logged in
    try {
        const response = await API.getCurrentUser();
        if (response.success) {
            window.location.href = '/dashboard.html';
            return;
        }
    } catch (e) {
        // Not logged in, stay on login page
    }

    loginForm.addEventListener('submit', async (e) => {
        e.preventDefault();
        alertBox.classList.add('d-none');

        const username = document.getElementById('username').value.trim();
        const password = document.getElementById('password').value;

        loginBtn.disabled = true;
        loginBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Signing in...';

        try {
            const response = await API.login(username, password);
            if (response.success) {
                window.location.href = '/dashboard.html';
            } else {
                showError(response.message || 'Login failed');
            }
        } catch (error) {
            showError('Unable to connect to server. Please try again.');
        } finally {
            loginBtn.disabled = false;
            loginBtn.innerHTML = '<i class="bi bi-box-arrow-in-right me-2"></i>Sign In';
        }
    });

    function showError(message) {
        alertBox.textContent = message;
        alertBox.classList.remove('d-none');
    }
});
