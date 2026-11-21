(function () {
  "use strict";

  const imageUrls = {
    produce: "https://images.unsplash.com/photo-1542838132-92c53300491e?auto=format&fit=crop&w=900&q=82",
    bakery: "https://images.unsplash.com/photo-1466637574441-749b8f19452f?auto=format&fit=crop&w=900&q=82",
    meals: "https://images.unsplash.com/photo-1593113598332-cd288d649433?auto=format&fit=crop&w=900&q=82"
  };

  function hoursFromNow(hours) {
    return new Date(Date.now() + hours * 60 * 60 * 1000);
  }

  const donations = [
    {
      id: "food-101",
      name: "Seasonal citrus & apples",
      category: "Fruits",
      quantity: "18 kg",
      location: "Riverside Market District",
      safeUntil: hoursFromNow(2),
      description: "A colorful mix of apples, pears, and citrus, sorted and ready for a quick handoff.",
      image: imageUrls.produce,
      alt: "Fresh fruit and vegetables ready to share",
      donor: "Riverbend Market"
    },
    {
      id: "food-102",
      name: "Bakery breakfast packs",
      category: "Bakery",
      quantity: "24 packets",
      location: "Old Town, near the library",
      safeUntil: hoursFromNow(7),
      description: "Assorted rolls and muffins from this morning's bake, packed and ready for collection.",
      image: imageUrls.bakery,
      alt: "Fresh bakery items prepared for sharing",
      donor: "Morningside Bakery"
    },
    {
      id: "food-103",
      name: "Vegetable curry portions",
      category: "Cooked Meals",
      quantity: "32 meals",
      location: "West Riverside",
      safeUntil: hoursFromNow(10),
      description: "Mild vegetable curry with rice, chilled and sealed in individual containers.",
      image: imageUrls.meals,
      alt: "Prepared meals packed for a community handoff",
      donor: "Green Spoon Kitchen"
    },
    {
      id: "food-104",
      name: "Fresh garden vegetables",
      category: "Vegetables",
      quantity: "12 kg",
      location: "Maple Street Community Market",
      safeUntil: hoursFromNow(23),
      description: "Washed carrots, leafy greens, and squash, ready for a same-day community kitchen.",
      image: imageUrls.produce,
      alt: "Fresh market vegetables packed for sharing",
      donor: "Maple Street Growers"
    }
  ];

  const claims = [
    {
      id: "claim-201",
      name: "Bakery breakfast packs",
      quantity: "10 packets",
      location: "7 Mill Street, Old Town",
      donor: "Morningside Bakery",
      status: "Reserved",
      image: imageUrls.bakery,
      meals: 0
    },
    {
      id: "claim-202",
      name: "Community lunch bowls",
      quantity: "20 meals",
      location: "18 Willow Avenue, Riverside",
      donor: "Green Spoon Kitchen",
      status: "Picked Up",
      image: imageUrls.meals,
      meals: 20
    },
    {
      id: "claim-203",
      name: "Harvest vegetable soup",
      quantity: "32 meals",
      location: "3 Willow Street, Riverside",
      donor: "Willow Street Cafe",
      status: "Delivered",
      image: imageUrls.produce,
      meals: 32
    }
  ];

  const notifications = [
    {
      id: "notice-201",
      kind: "claim",
      icon: "bi-bag-check",
      title: "Pickup reminder",
      message: "Bakery breakfast packs is reserved in this local demo.",
      createdAt: new Date(Date.now() - 60 * 60 * 1000)
    },
    {
      id: "notice-202",
      kind: "new",
      icon: "bi-basket2",
      title: "New food donations are available",
      message: "Browse the current demo listings and their pickup windows.",
      createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000)
    }
  ];

  const els = {
    foodGrid: document.getElementById("food-grid"),
    search: document.getElementById("food-search"),
    category: document.getElementById("category-filter"),
    foodEmpty: document.getElementById("food-empty"),
    claimsList: document.getElementById("claims-list"),
    claimsEmpty: document.getElementById("claims-empty"),
    notificationList: document.getElementById("notification-list"),
    notificationEmpty: document.getElementById("notifications-empty"),
    arrival: document.querySelector(".ngo-arrival"),
    arrivalMessage: document.getElementById("arrival-message"),
    liveMessage: document.getElementById("live-message"),
    modal: document.getElementById("donation-modal")
  };

  const donationModal = bootstrap.Modal.getOrCreateInstance(els.modal);
  let activeDonationId = null;
  let arrivalAdded = false;
  let arrivalAnimationTimer;
  let messageTimer;
  let mealsRescued = 1284;

  function formatDate(date) {
    return date.toLocaleString(undefined, {
      month: "short",
      day: "numeric",
      hour: "numeric",
      minute: "2-digit"
    });
  }

  function urgencyFor(date) {
    const remainingHours = (date.getTime() - Date.now()) / 3600000;
    if (remainingHours <= 3) return { label: "Soon", className: "high" };
    if (remainingHours <= 12) return { label: "Today", className: "" };
    return { label: "Plenty of time", className: "" };
  }

  function createIcon(name) {
    const icon = document.createElement("i");
    icon.className = "bi " + name;
    icon.setAttribute("aria-hidden", "true");
    return icon;
  }

  function showMessage(message) {
    els.liveMessage.textContent = message;
    els.liveMessage.hidden = false;
    window.clearTimeout(messageTimer);
    messageTimer = window.setTimeout(function () {
      els.liveMessage.hidden = true;
    }, 4200);
  }

  function renderDonations() {
    const searchTerm = els.search.value.trim().toLowerCase();
    const category = els.category.value;
    const filtered = donations.filter(function (donation) {
      const searchText = [donation.name, donation.category, donation.location, donation.donor].join(" ").toLowerCase();
      return searchText.includes(searchTerm) && (category === "all" || donation.category === category);
    });

    els.foodGrid.replaceChildren();
    filtered.forEach(function (donation) {
      const urgency = urgencyFor(donation.safeUntil);
      const card = document.createElement("article");
      card.className = "ngo-food-card";

      const photo = document.createElement("div");
      photo.className = "ngo-food-photo";
      const image = document.createElement("img");
      image.src = donation.image;
      image.alt = donation.alt;
      image.loading = "lazy";
      image.width = 900;
      image.height = 600;
      const category = document.createElement("span");
      category.className = "ngo-food-category";
      category.textContent = donation.category;
      const urgencyBadge = document.createElement("span");
      urgencyBadge.className = "ngo-urgency" + (urgency.className ? " " + urgency.className : "");
      urgencyBadge.textContent = urgency.label;
      photo.append(image, category, urgencyBadge);

      const content = document.createElement("div");
      content.className = "ngo-food-copy";
      const titleRow = document.createElement("div");
      titleRow.className = "ngo-food-title-row";
      const title = document.createElement("h3");
      title.textContent = donation.name;
      const quantity = document.createElement("span");
      quantity.className = "ngo-food-quantity";
      quantity.textContent = donation.quantity;
      titleRow.append(title, quantity);

      const meta = document.createElement("div");
      meta.className = "ngo-food-meta";
      const location = document.createElement("span");
      location.append(createIcon("bi-geo-alt"), document.createTextNode(donation.location));
      meta.append(location);

      const safeUntil = document.createElement("div");
      safeUntil.className = "ngo-safe-until";
      safeUntil.append(document.createTextNode("Safe until "));
      const safeTime = document.createElement("strong");
      safeTime.textContent = formatDate(donation.safeUntil);
      safeUntil.append(safeTime);

      const actions = document.createElement("div");
      actions.className = "ngo-food-actions";
      const detailsButton = document.createElement("button");
      detailsButton.className = "ngo-button ngo-button-outline";
      detailsButton.type = "button";
      detailsButton.dataset.donationDetails = donation.id;
      detailsButton.textContent = "View Details";
      const claimButton = document.createElement("button");
      claimButton.className = "ngo-button ngo-button-primary";
      claimButton.type = "button";
      claimButton.dataset.claimDonation = donation.id;
      claimButton.textContent = "Claim";
      actions.append(detailsButton, claimButton);
      content.append(titleRow, meta, safeUntil, actions);
      card.append(photo, content);
      els.foodGrid.append(card);
    });

    els.foodEmpty.hidden = filtered.length !== 0;
    document.getElementById("listing-count").textContent = String(filtered.length);
    document.getElementById("available-count").textContent = String(donations.length);
    document.getElementById("available-stat").textContent = String(donations.length);
  }

  function updateClaim(claimId) {
    const claim = claims.find(function (item) {
      return item.id === claimId;
    });
    if (!claim) return;

    if (claim.status === "Reserved") {
      claim.status = "Picked Up";
      addNotification("Claim updated", claim.name + " was marked as picked up in this local demo.", "claim");
    } else if (claim.status === "Picked Up") {
      claim.status = "Delivered";
      mealsRescued += claim.meals;
      document.getElementById("meals-rescued").textContent = mealsRescued.toLocaleString();
      addNotification("Claim delivered", claim.name + " was marked as delivered in this local demo.", "claim");
    }
    renderClaims();
    showMessage("Claim status updated locally. No backend was contacted.");
  }

  function renderClaims() {
    els.claimsList.replaceChildren();
    claims.forEach(function (claim) {
      const item = document.createElement("article");
      item.className = "ngo-claim-card";
      const image = document.createElement("img");
      image.src = claim.image;
      image.alt = "";
      image.loading = "lazy";

      const copy = document.createElement("div");
      copy.className = "ngo-claim-copy";
      const name = document.createElement("h3");
      name.textContent = claim.name;
      const details = document.createElement("p");
      details.textContent = claim.quantity + " · " + claim.donor + " · " + claim.location;
      const status = document.createElement("span");
      status.className = "ngo-claim-status" + (claim.status === "Picked Up" ? " picked-up" : claim.status === "Delivered" ? " delivered" : "");
      status.append(createIcon(claim.status === "Delivered" ? "bi-check-circle-fill" : "bi-bag-check"));
      status.append(document.createTextNode(claim.status));
      copy.append(name, details, status);

      const actions = document.createElement("div");
      actions.className = "ngo-claim-actions";
      if (claim.status !== "Delivered") {
        const button = document.createElement("button");
        button.className = "ngo-button ngo-button-outline";
        button.type = "button";
        button.dataset.advanceClaim = claim.id;
        button.textContent = claim.status === "Reserved" ? "Mark picked up" : "Mark delivered";
        actions.append(button);
      }
      const localTag = document.createElement("span");
      localTag.className = "ngo-local-tag";
      localTag.textContent = "LOCAL DEMO";
      actions.append(localTag);
      item.append(image, copy, actions);
      els.claimsList.append(item);
    });
    els.claimsEmpty.hidden = claims.length !== 0;
    document.getElementById("claims-count").textContent = String(claims.length);
    document.getElementById("claimed-stat").textContent = String(claims.length);
  }

  function addNotification(title, message, kind) {
    notifications.unshift({
      id: "notice-" + Date.now() + "-" + Math.random().toString(16).slice(2),
      kind: kind,
      icon: kind === "new" ? "bi-basket2" : "bi-bag-check",
      title: title,
      message: message,
      createdAt: new Date()
    });
    renderNotifications();
  }

  function renderNotifications() {
    els.notificationList.replaceChildren();
    notifications.forEach(function (notification) {
      const item = document.createElement("article");
      item.className = "ngo-notification-item";
      item.dataset.kind = notification.kind;
      const icon = document.createElement("span");
      icon.className = "ngo-notification-icon";
      icon.append(createIcon(notification.icon));
      const copy = document.createElement("div");
      const title = document.createElement("h3");
      title.textContent = notification.title;
      const message = document.createElement("p");
      message.textContent = notification.message;
      const time = document.createElement("time");
      time.dateTime = notification.createdAt.toISOString();
      time.textContent = notification.createdAt.toLocaleString(undefined, { month: "short", day: "numeric", hour: "numeric", minute: "2-digit" }) + " · demo";
      copy.append(title, message, time);
      item.append(icon, copy);
      els.notificationList.append(item);
    });
    els.notificationEmpty.hidden = notifications.length !== 0;
    const count = notifications.length;
    const badge = document.getElementById("notification-count");
    badge.textContent = String(count);
    badge.hidden = count === 0;
    document.getElementById("topbar-notification-dot").hidden = count === 0;
  }

  function claimDonation(donationId) {
    const index = donations.findIndex(function (donation) {
      return donation.id === donationId;
    });
    if (index === -1) return;
    const donation = donations[index];
    claims.unshift({
      id: "claim-" + donation.id,
      name: donation.name,
      quantity: donation.quantity,
      location: donation.location,
      donor: donation.donor,
      status: "Reserved",
      image: donation.image,
      meals: donation.quantity.indexOf("meals") !== -1 ? Number.parseInt(donation.quantity, 10) : 0
    });
    donations.splice(index, 1);
    renderDonations();
    renderClaims();
    addNotification("Demo claim added", donation.name + " is now in My Claims. No real food was reserved.", "claim");
    showMessage("Added to My Claims · demo only. No real donation was reserved.");
  }

  function openDetails(donationId) {
    const donation = donations.find(function (item) {
      return item.id === donationId;
    });
    if (!donation) return;
    activeDonationId = donation.id;
    const image = document.getElementById("modal-food-image");
    image.src = donation.image;
    image.alt = donation.alt;
    document.getElementById("modal-food-name").textContent = donation.name;
    document.getElementById("modal-category").textContent = donation.category;
    document.getElementById("modal-description").textContent = donation.description;
    document.getElementById("modal-quantity").textContent = donation.quantity;
    document.getElementById("modal-location").textContent = donation.location;
    document.getElementById("modal-safe-until").textContent = formatDate(donation.safeUntil);
    donationModal.show();
  }

  function playArrivalAnimation() {
    els.arrival.classList.remove("is-animating");
    void els.arrival.offsetWidth;
    els.arrival.classList.add("is-animating");
    window.clearTimeout(arrivalAnimationTimer);
    arrivalAnimationTimer = window.setTimeout(function () {
      els.arrival.classList.remove("is-animating");
    }, 1500);
  }

  function revealDemoDonation() {
    if (!arrivalAdded) {
      arrivalAdded = true;
      const newDonation = {
        id: "food-arrival-105",
        name: "Warm lentil & rice bowls",
        category: "Cooked Meals",
        quantity: "24 meals",
        location: "East Riverside",
        safeUntil: hoursFromNow(5),
        description: "Fresh lentil and rice bowls packed in compostable containers and ready for local pickup.",
        image: imageUrls.meals,
        alt: "A warm meal ready for a community pickup",
        donor: "Little Grove Community Kitchen"
      };
      donations.unshift(newDonation);
      renderDonations();
      addNotification("New food donation arrived!", newDonation.name + " is a simulated listing for this demo.", "new");
      els.arrivalMessage.textContent = newDonation.name + " is now available in the local demo listings.";
    } else {
      els.arrivalMessage.textContent = "The demo donation is already waiting in Available Food.";
    }
    playArrivalAnimation();
  }

  function setActiveNavigation(sectionId) {
    document.querySelectorAll(".ngo-nav-link").forEach(function (link) {
      const active = link.getAttribute("href") === "#" + sectionId;
      link.classList.toggle("active", active);
      if (active) link.setAttribute("aria-current", "page");
      else link.removeAttribute("aria-current");
    });
  }

  document.getElementById("greeting-time").textContent =
    new Date().getHours() < 12 ? "morning" : new Date().getHours() < 18 ? "afternoon" : "evening";
  renderDonations();
  renderClaims();
  renderNotifications();

  document.getElementById("meals-rescued").textContent = mealsRescued.toLocaleString();

  els.search.addEventListener("input", renderDonations);
  els.category.addEventListener("change", renderDonations);
  document.getElementById("preview-arrival").addEventListener("click", revealDemoDonation);

  els.foodGrid.addEventListener("click", function (event) {
    const detailsButton = event.target.closest("[data-donation-details]");
    if (detailsButton) {
      openDetails(detailsButton.dataset.donationDetails);
      return;
    }
    const claimButton = event.target.closest("[data-claim-donation]");
    if (claimButton) claimDonation(claimButton.dataset.claimDonation);
  });

  document.getElementById("modal-claim-button").addEventListener("click", function () {
    if (!activeDonationId) return;
    donationModal.hide();
    claimDonation(activeDonationId);
    activeDonationId = null;
  });

  els.claimsList.addEventListener("click", function (event) {
    const button = event.target.closest("[data-advance-claim]");
    if (button) updateClaim(button.dataset.advanceClaim);
  });

  document.querySelectorAll(".ngo-nav-link").forEach(function (link) {
    link.addEventListener("click", function () {
      setActiveNavigation(link.getAttribute("href").slice(1));
      if (window.innerWidth < 992) {
        const sidebar = document.getElementById("ngo-sidebar");
        const offcanvas = bootstrap.Offcanvas.getInstance(sidebar);
        if (offcanvas) offcanvas.hide();
      }
    });
  });

  if ("IntersectionObserver" in window) {
    const observer = new IntersectionObserver(function (entries) {
      const visible = entries.filter(function (entry) {
        return entry.isIntersecting;
      }).sort(function (first, second) {
        return second.intersectionRatio - first.intersectionRatio;
      });
      if (visible.length) setActiveNavigation(visible[0].target.id);
    }, { rootMargin: "-15% 0px -70% 0px", threshold: [0, 0.1, 0.35] });
    document.querySelectorAll("#dashboard, #available-food, #my-claims, #notifications").forEach(function (section) {
      observer.observe(section);
    });
  }

  window.setTimeout(revealDemoDonation, 900);
}());
