(function () {
  "use strict";

  function hoursFromNow(hours) {
    return new Date(Date.now() + hours * 60 * 60 * 1000);
  }

  const users = [
    { id: "user-101", name: "Samantha Kitchen", email: "hello@samanthaskitchen.example", role: "Donor", status: "Active", organization: "Samantha's Kitchen", location: "Riverside", joined: "Jan 12, 2026" },
    { id: "user-102", name: "Riverbend Market", email: "team@riverbend.example", role: "Donor", status: "Active", organization: "Riverbend Market", location: "Riverside Market District", joined: "Feb 03, 2026" },
    { id: "user-103", name: "Morningside Bakery", email: "contact@morningside.example", role: "Donor", status: "Inactive", organization: "Morningside Bakery", location: "Old Town", joined: "Mar 18, 2026" },
    { id: "user-104", name: "Riverside Table", email: "hello@riversidetable.example", role: "NGO", status: "Active", organization: "Riverside Table Community NGO", location: "Riverside", joined: "Jan 20, 2026" },
    { id: "user-105", name: "Eastside Community Pantry", email: "team@eastsidepantry.example", role: "NGO", status: "Active", organization: "Eastside Community Pantry", location: "East Riverside", joined: "Feb 11, 2026" },
    { id: "user-106", name: "Willow Street Outreach", email: "contact@willowoutreach.example", role: "NGO", status: "Inactive", organization: "Willow Street Outreach", location: "West Riverside", joined: "Apr 06, 2026" }
  ];

  const donations = [
    { name: "Seasonal citrus & apples", donor: "Riverbend Market", quantity: "18 kg", ngo: "", status: "Available", expiry: hoursFromNow(2) },
    { name: "Bakery breakfast packs", donor: "Morningside Bakery", quantity: "24 packets", ngo: "Riverside Table", status: "Reserved", expiry: hoursFromNow(5) },
    { name: "Community lunch bowls", donor: "Green Spoon Kitchen", quantity: "32 meals", ngo: "Eastside Community Pantry", status: "Picked Up", expiry: hoursFromNow(1) },
    { name: "Vegetable curry portions", donor: "Green Spoon Kitchen", quantity: "28 meals", ngo: "Riverside Table", status: "Delivered", expiry: hoursFromNow(-22) },
    { name: "Fresh garden vegetables", donor: "Maple Street Growers", quantity: "12 kg", ngo: "", status: "Available", expiry: hoursFromNow(18) },
    { name: "Garden herb boxes", donor: "Maple Street Growers", quantity: "8 boxes", ngo: "Willow Street Outreach", status: "Expired", expiry: hoursFromNow(-4) }
  ];

  const userSearch = document.getElementById("user-search");
  const userRoleFilter = document.getElementById("user-role-filter");
  const donationSearch = document.getElementById("donation-search");
  const donationStatusFilter = document.getElementById("donation-status-filter");
  const userTableBody = document.getElementById("users-table-body");
  const donationTableBody = document.getElementById("donations-table-body");
  const userModalElement = document.getElementById("user-details-modal");
  const userModal = bootstrap.Modal.getOrCreateInstance(userModalElement);
  const toast = document.getElementById("admin-toast");
  let toastTimer;

  function formatDate(date) {
    return date.toLocaleDateString(undefined, { month: "short", day: "numeric", year: "numeric" });
  }

  function statusClass(status) {
    return status.toLowerCase().replace(/\s+/g, "-");
  }

  function setText(id, value) {
    document.getElementById(id).textContent = value;
  }

  function showToast(message) {
    toast.textContent = message;
    toast.hidden = false;
    window.clearTimeout(toastTimer);
    toastTimer = window.setTimeout(function () {
      toast.hidden = true;
    }, 4200);
  }

  function renderUsers() {
    const searchTerm = userSearch.value.trim().toLowerCase();
    const selectedRole = userRoleFilter.value;
    const filteredUsers = users.filter(function (user) {
      const matchesSearch = (user.name + " " + user.email).toLowerCase().includes(searchTerm);
      return matchesSearch && (selectedRole === "all" || user.role === selectedRole);
    });

    userTableBody.replaceChildren();
    filteredUsers.forEach(function (user) {
      const row = document.createElement("tr");
      const nameCell = document.createElement("td");
      const userName = document.createElement("span");
      userName.className = "admin-user-name";
      userName.textContent = user.name;
      nameCell.append(userName);

      const emailCell = document.createElement("td");
      emailCell.className = "admin-user-email";
      emailCell.textContent = user.email;

      const roleCell = document.createElement("td");
      const roleBadge = document.createElement("span");
      roleBadge.className = "admin-role" + (user.role === "NGO" ? " ngo" : "");
      roleBadge.textContent = user.role;
      roleCell.append(roleBadge);

      const statusCell = document.createElement("td");
      const statusBadge = document.createElement("span");
      statusBadge.className = "admin-status " + statusClass(user.status);
      statusBadge.textContent = user.status;
      statusCell.append(statusBadge);

      const actionsCell = document.createElement("td");
      const actions = document.createElement("div");
      actions.className = "admin-row-actions";
      const detailsButton = document.createElement("button");
      detailsButton.className = "admin-action-button";
      detailsButton.type = "button";
      detailsButton.dataset.userDetails = user.id;
      detailsButton.textContent = "View details";
      const toggleButton = document.createElement("button");
      toggleButton.className = "admin-action-button" + (user.status === "Active" ? " deactivate" : "");
      toggleButton.type = "button";
      toggleButton.dataset.toggleUser = user.id;
      toggleButton.textContent = user.status === "Active" ? "Deactivate" : "Activate";
      actions.append(detailsButton, toggleButton);
      actionsCell.append(actions);
      row.append(nameCell, emailCell, roleCell, statusCell, actionsCell);
      userTableBody.append(row);
    });

    setText("user-result-count", String(filteredUsers.length));
    document.getElementById("users-empty").hidden = filteredUsers.length !== 0;
  }

  function renderDonations() {
    const searchTerm = donationSearch.value.trim().toLowerCase();
    const selectedStatus = donationStatusFilter.value;
    const filteredDonations = donations.filter(function (donation) {
      const searchText = [donation.name, donation.donor, donation.quantity, donation.ngo, donation.status].join(" ").toLowerCase();
      return searchText.includes(searchTerm) && (selectedStatus === "all" || donation.status === selectedStatus);
    });

    donationTableBody.replaceChildren();
    filteredDonations.forEach(function (donation) {
      const row = document.createElement("tr");
      const foodCell = document.createElement("td");
      const foodName = document.createElement("span");
      foodName.className = "admin-food-name";
      foodName.textContent = donation.name;
      foodCell.append(foodName);

      const donorCell = document.createElement("td");
      donorCell.textContent = donation.donor;
      const quantityCell = document.createElement("td");
      quantityCell.textContent = donation.quantity;
      const ngoCell = document.createElement("td");
      ngoCell.textContent = donation.ngo || "—";

      const statusCell = document.createElement("td");
      const statusBadge = document.createElement("span");
      statusBadge.className = "admin-status " + statusClass(donation.status);
      statusBadge.textContent = donation.status;
      statusCell.append(statusBadge);

      const expiryCell = document.createElement("td");
      expiryCell.textContent = formatDate(donation.expiry);
      row.append(foodCell, donorCell, quantityCell, ngoCell, statusCell, expiryCell);
      donationTableBody.append(row);
    });

    setText("donation-result-count", String(filteredDonations.length));
    document.getElementById("donations-empty").hidden = filteredDonations.length !== 0;
  }

  function renderReportSummary() {
    const statuses = ["Available", "Reserved", "Picked Up", "Delivered", "Expired"];
    const summary = document.getElementById("donation-status-summary");
    summary.replaceChildren();
    statuses.forEach(function (status) {
      const item = document.createElement("div");
      const label = document.createElement("dt");
      label.textContent = status;
      const count = document.createElement("dd");
      count.textContent = String(donations.filter(function (donation) {
        return donation.status === status;
      }).length);
      item.append(label, count);
      summary.append(item);
    });
  }

  function showUserDetails(userId) {
    const user = users.find(function (item) {
      return item.id === userId;
    });
    if (!user) return;
    setText("user-modal-name", user.name);
    setText("modal-user-email", user.email);
    setText("modal-user-role", user.role);
    setText("modal-user-status", user.status);
    setText("modal-user-organization", user.organization);
    setText("modal-user-location", user.location);
    setText("modal-user-joined", user.joined);
    userModal.show();
  }

  function toggleUserStatus(userId) {
    const user = users.find(function (item) {
      return item.id === userId;
    });
    if (!user) return;
    user.status = user.status === "Active" ? "Inactive" : "Active";
    renderUsers();
    showToast(user.name + " is now " + user.status.toLowerCase() + " in this demo only. No real account was changed.");
  }

  function setActiveNavigation(sectionId) {
    document.querySelectorAll(".admin-nav-link").forEach(function (link) {
      const active = link.getAttribute("href") === "#" + sectionId;
      link.classList.toggle("active", active);
      if (active) link.setAttribute("aria-current", "page");
      else link.removeAttribute("aria-current");
    });
  }

  userSearch.addEventListener("input", renderUsers);
  userRoleFilter.addEventListener("change", renderUsers);
  donationSearch.addEventListener("input", renderDonations);
  donationStatusFilter.addEventListener("change", renderDonations);

  userTableBody.addEventListener("click", function (event) {
    const detailsButton = event.target.closest("[data-user-details]");
    if (detailsButton) {
      showUserDetails(detailsButton.dataset.userDetails);
      return;
    }
    const toggleButton = event.target.closest("[data-toggle-user]");
    if (toggleButton) toggleUserStatus(toggleButton.dataset.toggleUser);
  });

  document.querySelectorAll(".admin-nav-link").forEach(function (link) {
    link.addEventListener("click", function () {
      setActiveNavigation(link.getAttribute("href").slice(1));
      if (window.innerWidth < 992) {
        const sidebar = document.getElementById("admin-sidebar");
        const offcanvas = bootstrap.Offcanvas.getInstance(sidebar);
        if (offcanvas) offcanvas.hide();
      }
    });
  });

  if ("IntersectionObserver" in window) {
    const observer = new IntersectionObserver(function (entries) {
      const visibleSections = entries.filter(function (entry) {
        return entry.isIntersecting;
      }).sort(function (first, second) {
        return second.intersectionRatio - first.intersectionRatio;
      });
      if (visibleSections.length) setActiveNavigation(visibleSections[0].target.id);
    }, { rootMargin: "-15% 0px -70% 0px", threshold: [0, 0.1, 0.35] });
    document.querySelectorAll("#overview, #users, #donations, #reports").forEach(function (section) {
      observer.observe(section);
    });
  }

  renderUsers();
  renderDonations();
  renderReportSummary();
}());
