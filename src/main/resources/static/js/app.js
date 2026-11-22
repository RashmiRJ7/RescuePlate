/*
 * Landing page script: confirms the same-origin REST API is reachable.
 * Uses relative URLs and credentials: 'same-origin' so the session cookie
 * (JSESSIONID) is sent automatically once login exists (ARCHITECTURE.md §7).
 */
(function () {
    const el = document.getElementById('health-status');
    if (!el) return;

    fetch('/api/health', { credentials: 'same-origin' })
        .then(res => {
            if (!res.ok) throw new Error('HTTP ' + res.status);
            return res.json();
        })
        .then(body => {
            el.textContent = 'API is ' + body.status;
            el.className = 'mb-0 status-up';
        })
        .catch(err => {
            el.textContent = 'API unreachable (' + err.message + ')';
            el.className = 'mb-0 status-down';
        });
})();
