document.addEventListener('DOMContentLoaded', async () => {
    const loadingState = document.getElementById('loadingState');
    const invalidState = document.getElementById('invalidState');
    const successState = document.getElementById('successState');
    const formState = document.getElementById('formState');

    const form = document.getElementById('setupPasswordForm');
    const alertBox = document.getElementById('alertBox');
    const setupBtn = document.getElementById('setupBtn');

    function showOnly(element) {
        [loadingState, invalidState, successState, formState].forEach(el => el.classList.add('d-none'));
        element.classList.remove('d-none');
    }

    function showError(message) {
        alertBox.textContent = message;
        alertBox.classList.remove('d-none');
    }

    const params = new URLSearchParams(window.location.search);
    const token = params.get('token');

    if (!token) {
        showOnly(invalidState);
        return;
    }

    // Validate the token up front (without consuming it) so an expired/used
    // link immediately shows "Invalid or Expired Link" instead of a form
    // that will only fail once submitted.
    try {
        const response = await fetch('/api/auth/validate-setup-token?token=' + encodeURIComponent(token), {
            method: 'GET',
            credentials: 'include',
            cache: 'no-store'
        });
        const result = await response.json();

        if (!response.ok || !result.success || result.data !== true) {
            showOnly(invalidState);
            return;
        }
    } catch (error) {
        showOnly(invalidState);
        return;
    }

    showOnly(formState);

    form.addEventListener('submit', async (e) => {
        e.preventDefault();
        alertBox.classList.add('d-none');

        const newPassword = document.getElementById('newPassword').value;
        const confirmPassword = document.getElementById('confirmPassword').value;

        if (newPassword.length < 8) {
            showError('Password must be at least 8 characters long.');
            return;
        }
        if (newPassword !== confirmPassword) {
            showError('New Password and Confirm Password do not match.');
            return;
        }

        setupBtn.disabled = true;
        setupBtn.innerHTML = '<span class="spinner-border spinner-border-sm me-2"></span>Creating password...';

        try {
            const response = await fetch('/api/auth/setup-password', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                credentials: 'include',
                cache: 'no-store',
                body: JSON.stringify({ token, newPassword, confirmPassword })
            });
            const result = await response.json();

            if (response.ok && result.success) {
                showOnly(successState);
                setTimeout(() => {
                    window.location.href = '/index.html';
                }, 2500);
            } else if (response.status === 400 && result.data && typeof result.data === 'object') {
                // Field-level validation errors from @Valid
                const firstError = Object.values(result.data)[0];
                showError(firstError || result.message || 'Unable to set password. Please try again.');
            } else if (response.status === 400) {
                // A bad-request at this point (token consumed/expired between
                // page-load validation and submission) means the link is no
                // longer usable.
                showOnly(invalidState);
            } else {
                showError(result.message || 'Unable to set password. Please try again.');
            }
        } catch (error) {
            showError('Unable to connect to server. Please try again.');
        } finally {
            setupBtn.disabled = false;
            setupBtn.innerHTML = '<i class="bi bi-check2-circle me-2"></i>Create Password';
        }
    });
});
