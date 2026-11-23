(function () {
  "use strict";

  const API = "/api";

  async function apiRequest(path, options) {
    const response = await fetch(API + path, {
      credentials: "include",
      headers: { "Content-Type": "application/json", ...(options && options.headers ? options.headers : {}) },
      ...options
    });

    if (response.status === 401) {
      window.location.href = "login.html";
      throw new Error("Please sign in again.");
    }

    let body = null;
    if (response.status !== 204) {
      body = await response.json().catch(function () { return null; });
    }
    if (!response.ok) {
      const error = new Error(body && body.message ? body.message : "The request could not be completed.");
      error.status = response.status;
      error.body = body;
      throw error;
    }
    return body;
  }

  function istDateToUtc(value) {
    if (!value) return null;
    const normalized = value.length === 16 ? value + ":00" : value;
    return new Date(normalized + "+05:30").toISOString();
  }

  function formatIst(value) {
    if (!value) return "—";
    return new Date(value).toLocaleString("en-IN", {
      timeZone: "Asia/Kolkata",
      month: "short",
      day: "numeric",
      year: "numeric",
      hour: "numeric",
      minute: "2-digit"
    });
  }

  function statusClass(status) {
    return "status-" + String(status).toLowerCase().replaceAll("_", "-");
  }

  function setFieldError(field, message) {
    const error = document.getElementById(field.id + "-error");
    field.setAttribute("aria-invalid", message ? "true" : "false");
    if (error) {
      error.textContent = message;
      error.classList.toggle("d-block", Boolean(message));
    }
  }

  function clearFieldError(field) {
    setFieldError(field, "");
  }

  function setText(id, value) {
    const element = document.getElementById(id);
    if (element) element.textContent = value;
  }

  async function loadProfile() {
    const me = await apiRequest("/auth/me");
    if (me.role !== "DONOR") {
      window.location.href = me.role === "NGO" ? "ngo-dashboard.html" : "admin-dashboard.html";
      return null;
    }

    const profileName = me.name || "Donor";
    setText("profile-name", profileName);
    const avatar = document.querySelector(".profile-avatar");
    if (avatar) {
      avatar.textContent = profileName.split(/\s+/).slice(0, 2).map(function (part) {
        return part.charAt(0);
      }).join("").toUpperCase();
    }

    if (me.latitude != null && me.longitude != null) {
      document.getElementById("latitude").value = me.latitude;
      document.getElementById("longitude").value = me.longitude;
      setText("location-feedback", "Using your saved location. Geolocation can replace it.");
    }
    return me;
  }

  let donations = [];
  let donationsLoaded = false;

  function setDonationStatsUnavailable(message) {
    ["stat-total-donations", "stat-open-donations", "stat-completed-donations", "stat-pickups"]
      .forEach(function (id) { setText(id, "—"); });
    setText("stat-meals-rescued-caption", message);
  }

  async function loadDonations() {
    const tableBody = document.getElementById("donations-table-body");
    const empty = document.getElementById("donations-empty");
    if (!tableBody) return;
    if (!donationsLoaded) {
      tableBody.replaceChildren();
      empty.hidden = true;
      setDonationStatsUnavailable("Loading donation totals…");
      setText("donation-count", "Loading your donations...");
    }

    try {
      const result = await apiRequest("/donations/mine");
      if (!Array.isArray(result)) {
        throw new Error("The donation list response was invalid.");
      }
      donations = result;
      donationsLoaded = true;
      renderDonations();
    } catch (error) {
      donations = [];
      donationsLoaded = false;
      empty.hidden = false;
      empty.querySelector("strong").textContent = "Could not load donations";
      empty.querySelector("span").textContent = error.message;
      setText("donation-count", "Unable to load your donations");
      setDonationStatsUnavailable("Donation totals unavailable");
    }
  }

  async function loadNotifications() {
    const list = document.getElementById("donor-notification-list");
    const empty = document.getElementById("donor-notifications-empty");
    const errorElement = document.getElementById("donor-notifications-error");
    if (!list) return;

    errorElement.hidden = true;
    try {
      const results = await Promise.all([
        apiRequest("/notifications?page=0&size=20"),
        apiRequest("/notifications/unread-count")
      ]);
      renderNotifications(results[0]);
      const unreadCount = Number(results[1]) || 0;
      const badge = document.getElementById("donor-notification-count");
      badge.textContent = String(unreadCount);
      badge.hidden = unreadCount === 0;
    } catch (error) {
      empty.hidden = true;
      errorElement.textContent = error.message;
      errorElement.hidden = false;
    }
  }

  function renderNotifications(notifications) {
    const list = document.getElementById("donor-notification-list");
    const empty = document.getElementById("donor-notifications-empty");
    list.replaceChildren();
    empty.hidden = notifications.length !== 0;

    notifications.forEach(function (notification) {
      const item = document.createElement("article");
      item.className = "notification-item" + (notification.read ? " read" : " unread");
      item.dataset.notificationId = String(notification.id);
      const iconWrap = document.createElement("span");
      iconWrap.className = "notification-icon";
      const icon = document.createElement("i");
      const iconNames = {
        CLAIM_CREATED: "bi-hand-thumbs-up",
        CLAIM_CANCELLED: "bi-bag-dash",
        CLAIM_PICKED_UP: "bi-truck",
        CLAIM_DELIVERED: "bi-check2-circle"
      };
      icon.className = "bi " + (iconNames[notification.type] || "bi-bell");
      icon.setAttribute("aria-hidden", "true");
      iconWrap.append(icon);

      const copy = document.createElement("div");
      const title = document.createElement("strong");
      title.textContent = notification.title;
      const message = document.createElement("p");
      message.textContent = notification.message;
      const time = document.createElement("time");
      time.className = "notification-time";
      time.dateTime = notification.createdAt;
      time.textContent = formatIst(notification.createdAt);
      copy.append(title, message, time);
      item.append(iconWrap, copy);
      list.append(item);
    });
  }

  async function markNotificationRead(notificationId) {
    await apiRequest("/notifications/" + encodeURIComponent(notificationId) + "/read", {
      method: "PATCH",
      body: "{}"
    });
    await loadNotifications();
  }

  async function markAllNotificationsRead() {
    await apiRequest("/notifications/read-all", { method: "PATCH", body: "{}" });
    await loadNotifications();
  }

  function renderDonations() {
    const tableBody = document.getElementById("donations-table-body");
    const empty = document.getElementById("donations-empty");
    const search = document.getElementById("donation-search").value.trim().toLowerCase();
    const status = document.getElementById("status-filter").value;

    const filtered = donations.filter(function (donation) {
      const matchesSearch = (donation.foodName + " " + (donation.category || "")).toLowerCase().includes(search);
      const matchesStatus = status === "all" || donation.status === status;
      return matchesSearch && matchesStatus;
    });

    tableBody.replaceChildren();
    filtered.forEach(function (donation, index) {
      const row = document.createElement("tr");
      row.className = "mr-card-entrance mr-stagger";
      row.style.setProperty("--mr-stagger-delay", (index * 70) + "ms");

      const foodCell = document.createElement("td");
      const foodWrap = document.createElement("div");
      const foodText = document.createElement("div");
      const name = document.createElement("span");
      const category = document.createElement("span");
      name.className = "donation-name";
      name.textContent = donation.foodName;
      category.className = "donation-category";
      category.textContent = String(donation.category || "").replaceAll("_", " ");
      foodText.append(name, category);
      foodWrap.append(foodText);
      foodCell.append(foodWrap);

      const quantity = document.createElement("td");
      quantity.textContent = donation.quantity + " meals";
      const posted = document.createElement("td");
      posted.textContent = formatIst(donation.createdAt);
      const safeUntil = document.createElement("td");
      safeUntil.textContent = formatIst(donation.safeUntil);

      const statusCell = document.createElement("td");
      const badge = document.createElement("span");
      badge.className = "status-badge " + statusClass(donation.status);
      badge.textContent = String(donation.status).replaceAll("_", " ");
      statusCell.append(badge);

      const action = document.createElement("td");
      const button = document.createElement("button");
      button.type = "button";
      button.className = "view-details";
      button.dataset.donationId = donation.id;
      button.textContent = "View details";
      action.append(button);

      row.append(foodCell, quantity, posted, safeUntil, statusCell, action);
      tableBody.append(row);
    });

    empty.hidden = filtered.length !== 0;
    if (donations.length === 0) {
      empty.querySelector("strong").textContent = "No donations yet";
      empty.querySelector("span").textContent = "Your donations will appear here after you post them.";
      setText("donation-count", "You have not posted any donations yet.");
    } else {
      empty.querySelector("strong").textContent = "No matching donations";
      empty.querySelector("span").textContent = "Try another search or choose a different status filter.";
      setText("donation-count", "Showing " + filtered.length + " of " + donations.length + " donations");
    }
    setText("stat-total-donations", String(donations.length));
    setText("stat-open-donations", String(donations.filter(function (item) {
      return item.status === "AVAILABLE" || item.status === "DISPATCHING"
        || item.status === "RESERVED" || item.status === "PICKED_UP";
    }).length));
    const completedDonations = donations.filter(function (item) {
      return item.status === "DELIVERED";
    });
    setText("stat-completed-donations", String(completedDonations.length));
    setText("stat-meals-rescued-caption", completedDonations.reduce(function (total, item) {
      return total + Number(item.quantity || 0);
    }, 0) + " meals rescued");
    setText("stat-pickups", String(donations.filter(function (item) {
      return item.status === "RESERVED";
    }).length));
  }

  async function openDonationDetails(donation) {
    setText("detail-food", donation.foodName);
    setText("detail-quantity", donation.quantity + " meals");
    setText("detail-category", String(donation.category || "").replaceAll("_", " "));
    setText("detail-status", String(donation.status).replaceAll("_", " "));
    setText("detail-posted", formatIst(donation.createdAt));
    setText("detail-safe-until", formatIst(donation.safeUntil));
    setText("detail-description", donation.description || "No description provided");
    setText("detail-address", donation.pickupAddress);
    setText("detail-contact", donation.latitude + ", " + donation.longitude);

    const historyList = document.getElementById("detail-history");
    historyList.replaceChildren();
    try {
      const history = await apiRequest("/donations/" + donation.id + "/history");
      history.forEach(function (entry) {
        const item = document.createElement("li");
        const icon = document.createElement("i");
        const text = document.createElement("span");
        icon.className = "bi bi-check-circle";
        icon.setAttribute("aria-hidden", "true");
        text.textContent = entry.toStatus + " · " + entry.changedBy + " · " + formatIst(entry.changedAt);
        item.append(icon, text);
        historyList.append(item);
      });
    } catch (error) {
      const item = document.createElement("li");
      item.textContent = "Status history unavailable: " + error.message;
      historyList.append(item);
    }

    bootstrap.Modal.getOrCreateInstance(document.getElementById("donation-details-modal")).show();
  }

  function validateCoordinates() {
    const latitude = document.getElementById("latitude");
    const longitude = document.getElementById("longitude");
    const latValue = latitude.value.trim();
    const lngValue = longitude.value.trim();
    clearFieldError(latitude);
    clearFieldError(longitude);

    if (Boolean(latValue) !== Boolean(lngValue)) {
      setFieldError(latValue ? latitude : longitude, "Provide both latitude and longitude, or leave both blank.");
      return false;
    }
    if (!latValue && !lngValue) return true;

    const lat = Number(latValue);
    const lng = Number(lngValue);
    if (!Number.isFinite(lat) || lat < -90 || lat > 90) {
      setFieldError(latitude, "Latitude must be between -90 and 90.");
      return false;
    }
    if (!Number.isFinite(lng) || lng < -180 || lng > 180) {
      setFieldError(longitude, "Longitude must be between -180 and 180.");
      return false;
    }
    return true;
  }

  async function submitDonation(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const feedback = document.getElementById("donation-feedback");
    const preview = document.getElementById("donation-preview");
    feedback.textContent = "";
    feedback.className = "donation-feedback";
    preview.hidden = true;

    let firstInvalid = null;
    ["food-name", "food-category", "quantity", "safe-until", "pickup-address"].forEach(function (id) {
      const field = document.getElementById(id);
      if (!field.value.trim()) {
        setFieldError(field, "Please complete this field.");
        if (!firstInvalid) firstInvalid = field;
      }
    });

    const quantity = document.getElementById("quantity");
    if (quantity.value && (!Number.isInteger(Number(quantity.value)) || Number(quantity.value) < 1 || Number(quantity.value) > 1000)) {
      setFieldError(quantity, "Enter a whole-number quantity from 1 to 1000.");
      if (!firstInvalid) firstInvalid = quantity;
    }

    const readyFrom = document.getElementById("ready-from");
    const safeUntil = document.getElementById("safe-until");
    let readyIso = null;
    let safeIso = null;
    try {
      readyIso = istDateToUtc(readyFrom.value);
      safeIso = istDateToUtc(safeUntil.value);
      if (safeIso && new Date(safeIso).getTime() <= Date.now()) {
        setFieldError(safeUntil, "Choose a safe-until time in the future.");
        if (!firstInvalid) firstInvalid = safeUntil;
      } else if (readyIso && safeIso && new Date(safeIso) <= new Date(readyIso)) {
        setFieldError(safeUntil, "Safe until must be after ready from.");
        if (!firstInvalid) firstInvalid = safeUntil;
      }
    } catch (error) {
      setFieldError(safeUntil, "Choose valid IST date and time values.");
      if (!firstInvalid) firstInvalid = safeUntil;
    }

    if (!validateCoordinates() && !firstInvalid) firstInvalid = document.querySelector('[aria-invalid="true"]');
    if (firstInvalid) {
      firstInvalid.focus();
      return;
    }

    const latValue = document.getElementById("latitude").value.trim();
    const lngValue = document.getElementById("longitude").value.trim();
    const payload = {
      foodName: document.getElementById("food-name").value.trim(),
      category: document.getElementById("food-category").value,
      quantity: Number(quantity.value),
      description: document.getElementById("description").value.trim() || null,
      pickupAddress: document.getElementById("pickup-address").value.trim(),
      safeUntil: safeIso
    };
    if (readyIso) payload.readyFrom = readyIso;
    if (latValue && lngValue) {
      payload.latitude = Number(latValue);
      payload.longitude = Number(lngValue);
    }

    const submitButton = form.querySelector('[type="submit"]');
    submitButton.disabled = true;
    try {
      const created = await apiRequest("/donations", {
        method: "POST",
        body: JSON.stringify(payload)
      });
      setText("preview-food", created.foodName + " · " + created.category);
      setText("preview-quantity", created.quantity + " meals");
      setText("preview-safe-until", formatIst(created.safeUntil));
      setText("preview-address", created.pickupAddress);
      setText("preview-explanation", "Donation posted successfully.");
      preview.hidden = false;
      feedback.textContent = "Donation posted successfully.";
      feedback.classList.add("text-success");
      form.reset();
      await loadDonations();
      await loadNotifications();
      try {
        await loadProfile();
      } catch (profileError) {
        setText("location-feedback", "Donation posted, but profile details could not refresh: " + profileError.message);
      }
    } catch (error) {
      feedback.textContent = error.body && error.body.fieldErrors
        ? Object.entries(error.body.fieldErrors).map(function (entry) { return entry[0] + ": " + entry[1]; }).join(" ")
        : error.message;
      feedback.classList.add("text-danger");
    } finally {
      submitButton.disabled = false;
    }
  }

  function setupGeolocation() {
    const button = document.getElementById("use-location");
    button.addEventListener("click", function () {
      if (!navigator.geolocation) {
        setText("location-feedback", "Geolocation is not supported by this browser.");
        return;
      }
      setText("location-feedback", "Getting your location...");
      navigator.geolocation.getCurrentPosition(function (position) {
        document.getElementById("latitude").value = position.coords.latitude.toFixed(6);
        document.getElementById("longitude").value = position.coords.longitude.toFixed(6);
        clearFieldError(document.getElementById("latitude"));
        clearFieldError(document.getElementById("longitude"));
        setText("location-feedback", "Current location added.");
      }, function () {
        setText("location-feedback", "Location permission denied. Enter coordinates manually.");
      }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 300000 });
    });
  }

  async function initialize() {
    try {
      await loadProfile();
      await loadDonations();
    } catch (error) {
      setText("donation-count", error.message);
      setDonationStatsUnavailable("Donation totals unavailable");
      const empty = document.getElementById("donations-empty");
      empty.hidden = false;
      empty.querySelector("strong").textContent = "Could not load donor dashboard";
      empty.querySelector("span").textContent = error.message;
    }

    setupGeolocation();
    document.getElementById("profile-signout").addEventListener("click", function (event) {
      event.preventDefault();
      apiRequest("/auth/logout", { method: "POST", body: "{}" })
        .finally(function () { window.location.href = "login.html"; });
    });
    document.getElementById("donation-form").addEventListener("submit", submitDonation);
    document.getElementById("donation-search").addEventListener("input", renderDonations);
    document.getElementById("status-filter").addEventListener("change", renderDonations);
    document.getElementById("donations-table-body").addEventListener("click", function (event) {
      const button = event.target.closest("[data-donation-id]");
      if (!button) return;
      const donation = donations.find(function (item) { return String(item.id) === button.dataset.donationId; });
      if (donation) openDonationDetails(donation);
    });
    document.getElementById("donor-notification-list").addEventListener("click", function (event) {
      const item = event.target.closest("[data-notification-id]");
      if (!item || !item.classList.contains("unread")) return;
      markNotificationRead(item.dataset.notificationId).catch(function (error) {
        const errorElement = document.getElementById("donor-notifications-error");
        errorElement.textContent = error.message;
        errorElement.hidden = false;
      });
    });
    document.getElementById("donor-mark-all-read").addEventListener("click", function () {
      markAllNotificationsRead().catch(function (error) {
        const errorElement = document.getElementById("donor-notifications-error");
        errorElement.textContent = error.message;
        errorElement.hidden = false;
      });
    });

    window.setInterval(function () {
      return Promise.all([loadDonations(), loadNotifications()]);
    }, 30000);
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", initialize);
  } else {
    initialize();
  }
})();
