(function () {
  "use strict";

  const form = document.getElementById("admin-setup-form");
  const unavailable = document.getElementById("setup-unavailable");
  const feedback = document.getElementById("setup-feedback");
  const submitButton = form.querySelector('[type="submit"]');

  async function checkSetupStatus() {
    try {
      const response = await fetch("/api/auth/admin-setup/status", {
        credentials: "include",
        headers: { "Accept": "application/json" }
      });
      const body = await response.json();
      if (!response.ok) throw new Error(body.message || "Unable to check administrator setup.");

      if (body.available) {
        form.hidden = false;
        return;
      }

      unavailable.textContent = "Initial administrator setup is unavailable. An administrator may already exist, or the setup key is not configured.";
      unavailable.hidden = false;
    } catch (error) {
      unavailable.textContent = error.message || "Unable to check administrator setup.";
      unavailable.hidden = false;
    }
  }

  form.addEventListener("submit", async function (event) {
    event.preventDefault();
    feedback.textContent = "";
    feedback.className = "mt-3";
    if (!form.reportValidity()) return;

    const body = {
      name: document.getElementById("admin-name").value.trim(),
      email: document.getElementById("admin-email").value.trim(),
      phone: document.getElementById("admin-phone").value.trim() || null,
      password: document.getElementById("admin-password").value,
      confirmPassword: document.getElementById("admin-confirm-password").value,
      setupKey: document.getElementById("admin-setup-key").value
    };

    submitButton.disabled = true;
    try {
      const response = await fetch("/api/auth/admin-setup", {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", "Accept": "application/json" },
        body: JSON.stringify(body)
      });
      const result = await response.json().catch(function () { return null; });
      if (!response.ok) {
        const fields = result && result.fieldErrors
          ? Object.entries(result.fieldErrors).map(function (entry) {
            return entry[0] + ": " + entry[1];
          }).join(" ")
          : "";
        throw new Error(fields || (result && result.message) || "Administrator setup failed.");
      }

      form.hidden = true;
      unavailable.textContent = "The initial administrator account was created. Sign in with the new account.";
      unavailable.className = "alert alert-success";
      unavailable.hidden = false;
      feedback.textContent = "";
    } catch (error) {
      feedback.textContent = error.message;
      feedback.classList.add("text-danger");
    } finally {
      submitButton.disabled = false;
    }
  });

  checkSetupStatus();
})();
