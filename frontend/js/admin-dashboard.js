(function () {
  "use strict";

  const API = "/api";
  let users = [];
  let donations = [];
  let claims = [];
  const userModal = bootstrap.Modal.getOrCreateInstance(document.getElementById("user-details-modal"));
  const toast = document.getElementById("admin-toast");
  let toastTimer;

  async function apiRequest(path, options) {
    const request = options || {};
    const response = await fetch(API + path, {
      credentials: "include",
      headers: { "Content-Type": "application/json", ...(request.headers || {}) },
      ...request
    });

    if (response.status === 401) {
      window.location.href = "/login.html";
      throw new Error("Please sign in again.");
    }

    const body = response.status === 204 ? null : await response.json().catch(function () { return null; });
    if (!response.ok) {
      const fieldErrors = body && body.fieldErrors
        ? Object.entries(body.fieldErrors).map(function (entry) { return entry[0] + ": " + entry[1]; }).join(" ")
        : "";
      const error = new Error(fieldErrors || (body && body.message) || "The request could not be completed.");
      error.status = response.status;
      throw error;
    }
    return body;
  }

  function setText(id, value) {
    const element = document.getElementById(id);
    if (element) element.textContent = value == null ? "" : String(value);
  }

  function displayEnum(value) {
    return String(value || "").replaceAll("_", " ").toLowerCase().replace(/\b\w/g, function (letter) {
      return letter.toUpperCase();
    });
  }

  function displayDate(value) {
    if (!value) return "—";
    return new Date(value).toLocaleString("en-IN", {
      timeZone: "Asia/Kolkata",
      dateStyle: "medium",
      timeStyle: "short"
    });
  }

  function showToast(message, isError) {
    toast.textContent = message;
    toast.hidden = false;
    toast.classList.toggle("text-danger", Boolean(isError));
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(function () { toast.hidden = true; }, 4200);
  }

  function renderUsers() {
    const term = document.getElementById("user-search").value.trim().toLowerCase();
    const selectedRole = document.getElementById("user-role-filter").value;
    const visibleUsers = users.filter(function (user) {
      return (selectedRole === "all" || user.role === selectedRole)
        && (user.name + " " + user.email).toLowerCase().includes(term);
    });
    const body = document.getElementById("users-table-body");
    body.replaceChildren();

    visibleUsers.forEach(function (user) {
      const row = document.createElement("tr");
      const nameCell = document.createElement("td");
      const name = document.createElement("span");
      name.className = "admin-user-name";
      name.textContent = user.name;
      nameCell.append(name);

      const email = document.createElement("td");
      email.className = "admin-user-email";
      email.textContent = user.email;

      const roleCell = document.createElement("td");
      const role = document.createElement("span");
      role.className = "admin-role" + (user.role === "NGO" ? " ngo" : "");
      role.textContent = displayEnum(user.role);
      roleCell.append(role);

      const statusCell = document.createElement("td");
      const status = document.createElement("span");
      status.className = "admin-status " + (user.active ? "active" : "inactive");
      status.textContent = user.active ? "Active" : "Inactive";
      statusCell.append(status);

      const actionsCell = document.createElement("td");
      const actions = document.createElement("div");
      actions.className = "admin-row-actions";
      const details = document.createElement("button");
      details.type = "button";
      details.className = "admin-action-button";
      details.dataset.userDetails = String(user.id);
      details.textContent = "View details";
      actions.append(details);
      if (user.role !== "ADMIN") {
        const toggle = document.createElement("button");
        toggle.type = "button";
        toggle.className = "admin-action-button" + (user.active ? " deactivate" : "");
        toggle.dataset.toggleUser = String(user.id);
        toggle.textContent = user.active ? "Deactivate" : "Activate";
        actions.append(toggle);
      }
      actionsCell.append(actions);
      row.append(nameCell, email, roleCell, statusCell, actionsCell);
      body.append(row);
    });

    setText("user-result-count", visibleUsers.length);
    document.getElementById("users-empty").hidden = visibleUsers.length !== 0;
  }

  function renderDonations() {
    const term = document.getElementById("donation-search").value.trim().toLowerCase();
    const selectedStatus = document.getElementById("donation-status-filter").value.toUpperCase().replaceAll(" ", "_");
    const visible = donations.filter(function (donation) {
      const search = [donation.foodName, donation.donorName, donation.quantity,
        donation.ngoName, donation.status].join(" ").toLowerCase();
      return search.includes(term) && (selectedStatus === "ALL" || donation.status === selectedStatus);
    });
    const body = document.getElementById("donations-table-body");
    body.replaceChildren();

    visible.forEach(function (donation) {
      const row = document.createElement("tr");
      [donation.foodName, donation.donorName, donation.quantity, donation.ngoName || "—"].forEach(function (value) {
        const cell = document.createElement("td");
        cell.textContent = value == null ? "—" : String(value);
        row.append(cell);
      });
      const statusCell = document.createElement("td");
      const badge = document.createElement("span");
      badge.className = "admin-status " + donation.status.toLowerCase().replaceAll("_", "-");
      badge.textContent = displayEnum(donation.status);
      statusCell.append(badge);
      const safeUntil = document.createElement("td");
      safeUntil.textContent = displayDate(donation.safeUntil);
      row.append(statusCell, safeUntil);
      body.append(row);
    });

    setText("donation-result-count", visible.length);
    document.getElementById("donations-empty").hidden = visible.length !== 0;
  }

  function renderClaims() {
    const body = document.getElementById("claims-table-body");
    body.replaceChildren();
    claims.forEach(function (claim) {
      const row = document.createElement("tr");
      [claim.foodName, claim.donorName, claim.ngoName].forEach(function (value) {
        const cell = document.createElement("td");
        cell.textContent = value || "—";
        row.append(cell);
      });
      const statusCell = document.createElement("td");
      const badge = document.createElement("span");
      badge.className = "admin-status " + String(claim.status).toLowerCase().replaceAll("_", "-");
      badge.textContent = displayEnum(claim.status);
      statusCell.append(badge);
      const claimedAt = document.createElement("td");
      claimedAt.textContent = displayDate(claim.claimedAt);
      const updatedAt = document.createElement("td");
      updatedAt.textContent = displayDate(claim.deliveredAt || claim.cancelledAt || claim.pickedUpAt);
      row.append(statusCell, claimedAt, updatedAt);
      body.append(row);
    });
    setText("claim-result-count", claims.length);
    document.getElementById("claims-empty").hidden = claims.length !== 0;
  }

  function showUserDetails(userId) {
    const user = users.find(function (item) { return String(item.id) === userId; });
    if (!user) return;
    setText("user-modal-name", user.name);
    setText("modal-user-email", user.email);
    setText("modal-user-role", displayEnum(user.role));
    setText("modal-user-status", user.active ? "Active" : "Inactive");
    setText("modal-user-organization", user.name);
    setText("modal-user-location", user.address || "Not provided");
    setText("modal-user-joined", displayDate(user.createdAt));
    userModal.show();
  }

  async function toggleUserStatus(userId, button) {
    button.disabled = true;
    try {
      const updated = await apiRequest("/admin/users/" + encodeURIComponent(userId) + "/toggle", { method: "PATCH" });
      users = users.map(function (user) { return user.id === updated.id ? updated : user; });
      renderUsers();
      showToast(updated.name + " is now " + (updated.active ? "active." : "inactive."));
    } catch (error) {
      showToast(error.message, true);
    }
  }

  async function createAdmin(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const feedback = document.getElementById("admin-account-feedback");
    const button = form.querySelector('[type="submit"]');
    feedback.textContent = "";
    feedback.className = "mt-2 mb-0";
    if (!form.reportValidity()) return;

    const payload = {
      name: document.getElementById("new-admin-name").value.trim(),
      email: document.getElementById("new-admin-email").value.trim(),
      phone: document.getElementById("new-admin-phone").value.trim() || null,
      password: document.getElementById("new-admin-password").value
    };

    button.disabled = true;
    try {
      const created = await apiRequest("/admin/users/admins", {
        method: "POST",
        body: JSON.stringify(payload)
      });
      form.reset();
      feedback.textContent = "Administrator " + created.email + " was created.";
      feedback.classList.add("text-success");
      users = await apiRequest("/admin/users");
      setText("stat-total-users", users.length);
      renderUsers();
    } catch (error) {
      feedback.textContent = error.message;
      feedback.classList.add("text-danger");
    } finally {
      button.disabled = false;
    }
  }

  async function loadDashboard() {
    const me = await apiRequest("/auth/me");
    if (me.role !== "ADMIN") {
      window.location.href = me.role === "DONOR" ? "/donor-dashboard.html" : "/ngo-dashboard.html";
      return;
    }
    setText("admin-profile-name", me.name || "Administrator");

    const results = await Promise.all([
      apiRequest("/admin/stats"),
      apiRequest("/admin/users"),
      apiRequest("/admin/donations"),
      apiRequest("/admin/claims")
    ]);
    setText("stat-total-users", results[0].totalUsers);
    setText("stat-total-donors", results[0].totalDonors);
    setText("stat-total-ngos", results[0].totalNgos);
    setText("stat-total-donations", results[0].totalDonations);
    users = results[1];
    donations = results[2];
    claims = results[3];
    renderUsers();
    renderDonations();
    renderClaims();
    setText("admin-live-status", "Live database data");
  }

  document.getElementById("user-search").addEventListener("input", renderUsers);
  document.getElementById("user-role-filter").addEventListener("change", renderUsers);
  document.getElementById("donation-search").addEventListener("input", renderDonations);
  document.getElementById("donation-status-filter").addEventListener("change", renderDonations);
  document.getElementById("users-table-body").addEventListener("click", function (event) {
    const details = event.target.closest("[data-user-details]");
    if (details) showUserDetails(details.dataset.userDetails);
    const toggle = event.target.closest("[data-toggle-user]");
    if (toggle) toggleUserStatus(toggle.dataset.toggleUser, toggle);
  });
  document.getElementById("admin-account-form").addEventListener("submit", createAdmin);
  document.getElementById("admin-signout").addEventListener("click", function (event) {
    event.preventDefault();
    apiRequest("/auth/logout", { method: "POST", body: "{}" })
      .then(function () { window.location.href = "/login.html"; })
      .catch(function (error) { showToast(error.message, true); });
  });
  document.querySelectorAll(".admin-nav-link").forEach(function (link) {
    link.addEventListener("click", function () {
      if (window.innerWidth < 992) {
        const sidebar = document.getElementById("admin-sidebar");
        const offcanvas = bootstrap.Offcanvas.getInstance(sidebar);
        if (offcanvas) offcanvas.hide();
      }
    });
  });

  loadDashboard().catch(function (error) {
    setText("admin-live-status", error.message);
    showToast(error.message, true);
  });
})();
