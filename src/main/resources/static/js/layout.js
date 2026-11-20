/*
 * Shared layout: renders the same navbar and footer on every page.
 * A page opts in by including <header id="app-navbar"> and <footer id="app-footer">.
 * Role-aware navigation links are added in task F5.
 */
(function () {
    const navbar = document.getElementById('app-navbar');
    if (navbar) {
        navbar.innerHTML = `
            <nav class="navbar navbar-expand-lg navbar-dark bg-success">
                <div class="container">
                    <a class="navbar-brand" href="/">RescuePlate</a>
                </div>
            </nav>`;
    }

    const footer = document.getElementById('app-footer');
    if (footer) {
        footer.innerHTML = `
            <div class="border-top py-3 text-center small text-body-secondary">
                RescuePlate &middot; Time-Sensitive Surplus Food Rescue
            </div>`;
    }
})();
