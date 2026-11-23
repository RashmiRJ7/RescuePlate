(function () {
  "use strict";

  const API = "/api";
  let feed = [];
  let claims = [];
  let activeDonationId = null;
  let feedTimer = null;

  async function apiRequest(path, options) {
    const response = await fetch(API + path, {
      credentials: "include",
      headers: { "Content-Type": "application/json", ...(options && options.headers ? options.headers : {}) },
      ...options
    });

    if (response.status === 401) {
      window.location.href = "/login.html";
      throw new Error("Please sign in again.");
    }

    let body = null;
    if (response.status !== 204) body = await response.json().catch(function () { return null; });
    if (!response.ok) {
      const error = new Error(body && body.message ? body.message : "The request could not be completed.");
      error.status = response.status;
      error.body = body;
      throw error;
    }
    return body;
  }

  function formatIst(value) {
    if (!value) return "—";
    return new Date(value).toLocaleString("en-IN", {
      timeZone: "Asia/Kolkata",
      month: "short",
      day: "numeric",
      hour: "numeric",
      minute: "2-digit"
    });
  }

  function categoryLabel(category) {
    return String(category || "").replaceAll("_", " ");
  }

  function createIcon(name) {
    const icon = document.createElement("i");
    icon.className = "bi " + name;
    icon.setAttribute("aria-hidden", "true");
    return icon;
  }

  function showMessage(message, isError) {
    const element = document.getElementById("live-message");
    if (!element) return;
    element.textContent = message;
    element.hidden = false;
    element.classList.toggle("text-danger", Boolean(isError));
  }

  function showToast(title, message) {
    const toast = document.getElementById("claim-success-toast");
    if (!toast) return;
    document.getElementById("claim-success-title").textContent = title;
    document.getElementById("claim-success-message").textContent = message;
    toast.classList.add("show");
    window.clearTimeout(toast.timer);
    toast.timer = window.setTimeout(function () { toast.classList.remove("show"); }, 3500);
  }

  async function loadProfile() {
    const me = await apiRequest("/auth/me");
    if (me.role !== "NGO") {
      window.location.href = me.role === "DONOR" ? "/donor-dashboard.html" : "/admin-dashboard.html";
      return null;
    }
    const nameElements = document.querySelectorAll(".ngo-sidebar-profile strong, .ngo-topbar strong");
    nameElements.forEach(function (element) { element.textContent = me.name || "NGO"; });
    return me;
  }

  async function loadNgoSettings(me) {
    const profile = await apiRequest("/ngo/profile");
    document.getElementById("ngo-capacity").value = profile.capacityMeals;
    document.getElementById("ngo-radius").value = profile.serviceRadiusKm;
    document.querySelectorAll('input[name="acceptedCategories"]').forEach(function (input) {
      input.checked = profile.acceptedCategories.includes(input.value);
    });
    if (me.latitude != null && me.longitude != null) {
      document.getElementById("ngo-latitude").value = me.latitude;
      document.getElementById("ngo-longitude").value = me.longitude;
      document.getElementById("ngo-location-status").textContent = "Using your saved collection location.";
    } else {
      document.getElementById("ngo-location-status").textContent = "Add a location to enable nearby donation matching.";
    }
  }

  async function saveNgoSettings(event) {
    event.preventDefault();
    const form = event.currentTarget;
    const feedback = document.getElementById("ngo-profile-feedback");
    const latitude = document.getElementById("ngo-latitude").value.trim();
    const longitude = document.getElementById("ngo-longitude").value.trim();
    const categories = Array.from(document.querySelectorAll('input[name="acceptedCategories"]:checked'))
      .map(function (input) { return input.value; });
    feedback.textContent = "";
    feedback.className = "ngo-settings-feedback";

    if (Boolean(latitude) !== Boolean(longitude)) {
      feedback.textContent = "Enter both latitude and longitude, or leave both blank.";
      feedback.classList.add("text-danger");
      return;
    }
    if (categories.length === 0) {
      feedback.textContent = "Select at least one accepted food category.";
      feedback.classList.add("text-danger");
      return;
    }
    if (!form.reportValidity()) return;

    const payload = {
      capacityMeals: Number(document.getElementById("ngo-capacity").value),
      serviceRadiusKm: Number(document.getElementById("ngo-radius").value),
      acceptedCategories: categories,
      latitude: latitude ? Number(latitude) : null,
      longitude: longitude ? Number(longitude) : null
    };
    const button = form.querySelector('[type="submit"]');
    button.disabled = true;
    feedback.textContent = "Saving your settings...";
    try {
      await apiRequest("/ngo/profile", { method: "PUT", body: JSON.stringify(payload) });
      feedback.textContent = "NGO settings saved.";
      feedback.classList.add("text-success");
      document.getElementById("ngo-location-status").textContent = latitude
        ? "Collection location saved and ready for nearby matching."
        : "Location is not set yet. Add one to see nearby donations.";
      await loadFeed();
    } catch (error) {
      feedback.textContent = error.message;
      feedback.classList.add("text-danger");
    } finally {
      button.disabled = false;
    }
  }

  function setupNgoGeolocation() {
    document.getElementById("ngo-use-location").addEventListener("click", function () {
      const status = document.getElementById("ngo-location-status");
      if (!navigator.geolocation) {
        status.textContent = "Geolocation is not supported by this browser.";
        return;
      }
      status.textContent = "Getting your location...";
      navigator.geolocation.getCurrentPosition(function (position) {
        document.getElementById("ngo-latitude").value = position.coords.latitude.toFixed(6);
        document.getElementById("ngo-longitude").value = position.coords.longitude.toFixed(6);
        status.textContent = "Current location added. Save NGO settings to apply it.";
      }, function () {
        status.textContent = "Location permission denied. Enter your coordinates manually.";
      }, { enableHighAccuracy: true, timeout: 10000, maximumAge: 300000 });
    });
  }

  function setFeedLoading() {
    const grid = document.getElementById("food-grid");
    grid.replaceChildren();
    const loading = document.createElement("div");
    loading.className = "ngo-empty";
    loading.innerHTML = "<i class='bi bi-hourglass-split' aria-hidden='true'></i><h3>Loading available meals</h3><p>Checking current donations near you.</p>";
    loading.hidden = false;
    grid.append(loading);
  }

  async function loadFeed() {
    const maxDistance = document.getElementById("max-distance-filter").value.trim();
    if (maxDistance && (!Number.isFinite(Number(maxDistance)) || Number(maxDistance) <= 0)) {
      showMessage("Enter a distance greater than 0 km.", true);
      return;
    }
    setFeedLoading();
    try {
      const category = document.getElementById("category-filter").value;
      const params = new URLSearchParams();
      if (category !== "all") params.set("category", category);
      if (maxDistance) params.set("maxDistanceKm", maxDistance);

      const query = params.toString();
      feed = await apiRequest("/donations/feed" + (query ? "?" + query : ""));
      renderFeed();
    } catch (error) {
      feed = [];
      renderFeed(error.message);
    }
  }

  function renderFeed(loadError) {
    const grid = document.getElementById("food-grid");
    const empty = document.getElementById("food-empty");
    const search = document.getElementById("food-search").value.trim().toLowerCase();
    grid.replaceChildren();

    const filtered = feed.filter(function (item) {
      return !search || (item.foodName + " " + (item.pickupAddress || "") + " " + categoryLabel(item.category)).toLowerCase().includes(search);
    });

    if (loadError) {
      empty.hidden = false;
      empty.querySelector("h3").textContent = "Could not load available meals";
      empty.querySelector("p").textContent = loadError;
    } else if (filtered.length === 0) {
      empty.hidden = false;
      empty.querySelector("h3").textContent = "No available meals";
      empty.querySelector("p").textContent = "No current donations match your feed and filters.";
    } else {
      empty.hidden = true;
    }

    filtered.forEach(function (donation, index) {
      const card = document.createElement("article");
      card.className = "ngo-food-card mr-card-entrance mr-stagger";
      card.style.setProperty("--mr-stagger-delay", (index * 70) + "ms");

      const photo = document.createElement("div");
      photo.className = "ngo-food-photo";
      const image = document.createElement("img");
      image.src = categoryImage(donation.category);
      image.alt = categoryLabel(donation.category);
      image.loading = "lazy";
      const category = document.createElement("span");
      category.className = "ngo-food-category";
      category.textContent = categoryLabel(donation.category);
      const urgency = document.createElement("span");
      urgency.className = "ngo-urgency" + (donation.urgencyScore >= 70 ? " high" : "");
      urgency.textContent = "Urgency " + Number(donation.urgencyScore).toFixed(1);
      photo.append(image, category, urgency);

      if (donation.exceedsCapacity) {
        const warning = document.createElement("span");
        warning.className = "status-badge status-reserved";
        warning.textContent = "Exceeds capacity";
        photo.append(warning);
      }

      const copy = document.createElement("div");
      copy.className = "ngo-food-copy";
      const titleRow = document.createElement("div");
      titleRow.className = "ngo-food-title-row";
      const title = document.createElement("h3");
      title.textContent = donation.foodName;
      const quantity = document.createElement("span");
      quantity.className = "ngo-food-quantity";
      quantity.textContent = donation.quantity + " meals";
      titleRow.append(title, quantity);

      const meta = document.createElement("div");
      meta.className = "ngo-food-meta";
      const location = document.createElement("span");
      location.append(createIcon("bi-geo-alt"), document.createTextNode(donation.pickupAddress));
      const distance = document.createElement("span");
      distance.append(createIcon("bi-signpost"), document.createTextNode(donation.distanceKm != null ? donation.distanceKm.toFixed(1) + " km away" : "Distance unavailable"));
      meta.append(location, distance);

      const safeUntil = document.createElement("div");
      safeUntil.className = "ngo-safe-until";
      safeUntil.append(document.createTextNode("Safe until "), Object.assign(document.createElement("strong"), { textContent: formatIst(donation.safeUntil) }));
      const timeLeft = document.createElement("p");
      timeLeft.textContent = donation.minutesLeft != null ? donation.minutesLeft + " minutes left" : "";

      const actions = document.createElement("div");
      actions.className = "ngo-food-actions";
      const details = document.createElement("button");
      details.type = "button";
      details.className = "ngo-button ngo-button-outline";
      details.dataset.donationDetails = donation.id;
      details.textContent = "View Details";
      const claim = document.createElement("button");
      claim.type = "button";
      claim.className = "ngo-button ngo-button-primary";
      claim.dataset.claimDonation = donation.id;
      claim.textContent = "Claim Food";
      actions.append(details, claim);
      copy.append(titleRow, meta, safeUntil, timeLeft, actions);
      card.append(photo, copy);
      grid.append(card);
    });

    document.getElementById("listing-count").textContent = String(filtered.length);
    document.getElementById("available-count").textContent = String(feed.length);
    document.getElementById("available-stat").textContent = String(feed.length);
  }

  function categoryImage(category) {
    const images = {
      COOKED_MEAL: "https://images.unsplash.com/photo-1585937421612-70a008356fbe?auto=format&fit=crop&w=900&q=82",
      BAKERY: "https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=900&q=82",
      FRESH_PRODUCE: "https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=900&q=82",
      PACKAGED: "https://images.unsplash.com/photo-1601050690597-df0568f70950?auto=format&fit=crop&w=900&q=82"
    };
    return images[category] || images.PACKAGED;
  }

  async function claimDonation(id) {
    try {
      await apiRequest("/donations/" + id + "/claim", { method: "POST", body: "{}" });
      showToast("Donation claimed", "The donation was added to My Claims.");
      showMessage("Donation claimed successfully.");
      await Promise.all([loadFeed(), loadClaims()]);
    } catch (error) {
      if (error.status === 409) showToast("No longer available", "This donation has already been claimed or is no longer claimable.");
      else if (error.status === 400) showToast("Not eligible", "This donation is outside your profile eligibility.");
      else showToast("Could not claim donation", error.message);
      showMessage(error.message, true);
      await loadFeed();
    }
  }

  async function loadClaims() {
    const list = document.getElementById("claims-list");
    list.replaceChildren();
    try {
      claims = await apiRequest("/claims/mine");
      renderClaims();
    } catch (error) {
      claims = [];
      renderClaims(error.message);
    }
  }

  function renderClaims(loadError) {
    const list = document.getElementById("claims-list");
    const empty = document.getElementById("claims-empty");
    list.replaceChildren();

    if (loadError) {
      empty.hidden = false;
      empty.querySelector("h3").textContent = "Could not load claims";
      empty.querySelector("p").textContent = loadError;
    } else {
      empty.hidden = claims.length !== 0;
    }

    claims.forEach(function (claim) {
      const item = document.createElement("article");
      item.className = "ngo-claim-card mr-card-entrance";
      const copy = document.createElement("div");
      copy.className = "ngo-claim-copy";
      const name = document.createElement("h3");
      name.textContent = "Donation #" + claim.donationId;
      const details = document.createElement("p");
      details.textContent = claim.donation ? claim.donation.pickupAddress : "Pickup address unavailable";
      const status = document.createElement("span");
      status.className = "ngo-claim-status";
      status.append(createIcon(claim.status === "COMPLETED" ? "bi-check-circle-fill" : "bi-bag-check"));
      status.append(document.createTextNode(String(claim.status).replaceAll("_", " ")));
      copy.append(name, details, status);

      const actions = document.createElement("div");
      actions.className = "ngo-claim-actions";
      if (claim.status === "ACTIVE") {
        addClaimAction(actions, "Cancel", "cancelClaim", claim.id, "ngo-button-outline");
        addClaimAction(actions, "Mark picked up", "pickupClaim", claim.id, "ngo-button-primary");
      } else if (claim.status === "PICKED_UP") {
        addClaimAction(actions, "Mark delivered", "deliverClaim", claim.id, "ngo-button-primary");
      }
      item.append(copy, actions);
      list.append(item);
    });

    document.getElementById("claims-count").textContent = String(claims.length);
    document.getElementById("claimed-stat").textContent = String(claims.length);
  }

  function addClaimAction(container, label, action, claimId, style) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "ngo-button " + style;
    button.dataset.claimAction = action;
    button.dataset.claimId = claimId;
    button.textContent = label;
    container.append(button);
  }

  async function runClaimAction(action, id) {
    const endpoint = action === "cancelClaim" ? "cancel" : action === "pickupClaim" ? "pickup" : "deliver";
    try {
      await apiRequest("/claims/" + id + "/" + endpoint, {
        method: "PATCH",
        body: action === "cancelClaim" ? JSON.stringify({ reason: "Cancelled by NGO" }) : "{}"
      });
      showToast("Claim updated", endpoint === "deliver" ? "The donation was marked delivered." : "The claim status was updated.");
      await Promise.all([loadClaims(), loadFeed()]);
    } catch (error) {
      showToast("Could not update claim", error.message);
      showMessage(error.message, true);
    }
  }

  function openDetails(id) {
    const donation = feed.find(function (item) { return String(item.id) === String(id); });
    if (!donation) return;
    activeDonationId = donation.id;
    const image = document.getElementById("modal-food-image");
    image.src = categoryImage(donation.category);
    image.alt = categoryLabel(donation.category);
    document.getElementById("modal-food-name").textContent = donation.foodName;
    document.getElementById("modal-category").textContent = categoryLabel(donation.category);
    document.getElementById("modal-description").textContent = donation.donorName ? "Donor: " + donation.donorName : "";
    document.getElementById("modal-quantity").textContent = donation.quantity + " meals";
    document.getElementById("modal-location").textContent = donation.pickupAddress;
    document.getElementById("modal-safe-until").textContent = formatIst(donation.safeUntil);
    bootstrap.Modal.getOrCreateInstance(document.getElementById("donation-modal")).show();
  }

  async function initialize() {
    document.getElementById("meals-rescued").textContent = "0";
    document.getElementById("notification-count").hidden = true;
    document.getElementById("notifications-empty").hidden = false;
    try {
      const me = await loadProfile();
      if (!me) return;
      try {
        await loadNgoSettings(me);
      } catch (error) {
        const feedback = document.getElementById("ngo-profile-feedback");
        feedback.textContent = error.message;
        feedback.classList.add("text-danger");
      }
      await Promise.all([loadFeed(), loadClaims()]);
    } catch (error) {
      showMessage(error.message, true);
    }

    document.getElementById("food-search").addEventListener("input", renderFeed);
    document.getElementById("category-filter").addEventListener("change", loadFeed);
    document.getElementById("food-grid").addEventListener("click", function (event) {
      const details = event.target.closest("[data-donation-details]");
      if (details) openDetails(details.dataset.donationDetails);
      const claim = event.target.closest("[data-claim-donation]");
      if (claim) claimDonation(claim.dataset.claimDonation);
    });
    document.getElementById("modal-claim-button").addEventListener("click", function () {
      if (!activeDonationId) return;
      bootstrap.Modal.getOrCreateInstance(document.getElementById("donation-modal")).hide();
      claimDonation(activeDonationId);
      activeDonationId = null;
    });
    document.getElementById("claims-list").addEventListener("click", function (event) {
      const button = event.target.closest("[data-claim-action]");
      if (button) runClaimAction(button.dataset.claimAction, button.dataset.claimId);
    });

    document.getElementById("ngo-profile-form").addEventListener("submit", saveNgoSettings);
    setupNgoGeolocation();
    document.getElementById("max-distance-filter").addEventListener("change", loadFeed);

    const signout = document.querySelector(".ngo-signout");
    if (signout) signout.addEventListener("click", function (event) {
      event.preventDefault();
      apiRequest("/auth/logout", { method: "POST", body: "{}" }).finally(function () {
        window.location.href = "/login.html";
      });
    });

    feedTimer = window.setInterval(loadFeed, 30000);
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", initialize);
  else initialize();
})();
