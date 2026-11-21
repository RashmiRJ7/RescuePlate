(function () {
  "use strict";

  const imageUrls = {
    thali: "https://images.unsplash.com/photo-1585937421612-70a008356fbe?auto=format&fit=crop&w=900&q=82",
    bakery: "https://images.unsplash.com/photo-1509440159596-0249088772ff?auto=format&fit=crop&w=900&q=82",
    biryani: "https://images.unsplash.com/photo-1589302168068-964664d93dc0?auto=format&fit=crop&w=900&q=82",
    snacks: "https://images.unsplash.com/photo-1601050690597-df0568f70950?auto=format&fit=crop&w=900&q=82"
  };

  function hoursFromNow(hours) {
    return new Date(Date.now() + hours * 60 * 60 * 1000);
  }

  const donations = [
    {
      id: "food-101",
      name: "Rice, dal, roti and seasonal sabzi",
      category: "Cooked Meals",
      quantity: "40 plates",
      location: "Shivaji College Hostel Mess, Deccan, Pune 411004",
      safeUntil: hoursFromNow(3),
      description: "Fresh hostel mess food, packed safely and ready for same-day collection.",
      image: imageUrls.thali,
      alt: "An Indian thali with rice, dal, roti and vegetable dishes",
      donor: "Shivaji College Hostel Mess",
      meals: 40
    },
    {
      id: "food-102",
      name: "Bread and buns",
      category: "Bakery",
      quantity: "25 packets",
      location: "Asha Bakery & Sweets, Kothrud, Pune 411038",
      safeUntil: hoursFromNow(6),
      description: "Fresh bread and buns from today's bake, packed in clean food-safe bags.",
      image: imageUrls.bakery,
      alt: "Fresh bread and buns from a local bakery",
      donor: "Asha Bakery & Sweets",
      meals: 0
    },
    {
      id: "food-103",
      name: "Wedding buffet meals",
      category: "Cooked Meals",
      quantity: "120 portions",
      location: "Saanvi Wedding Hall, Baner, Pune 411045",
      safeUntil: hoursFromNow(2),
      description: "Fresh surplus from a wedding buffet, covered and ready for prompt collection.",
      image: imageUrls.biryani,
      alt: "A freshly prepared Indian rice meal for a community pickup",
      donor: "Saanvi Wedding Caterers",
      meals: 120
    },
    {
      id: "food-104",
      name: "Vegetable pulao",
      category: "Cooked Meals",
      quantity: "35 portions",
      location: "Deccan Heritage Hotel, Shivajinagar, Pune 411005",
      safeUntil: hoursFromNow(5),
      description: "Hotel kitchen surplus prepared today and packed for a community meal.",
      image: imageUrls.thali,
      alt: "A prepared Indian rice and vegetable meal",
      donor: "Deccan Heritage Hotel",
      meals: 35
    },
    {
      id: "food-105",
      name: "College event snack boxes",
      category: "Packaged Snacks",
      quantity: "50 boxes",
      location: "Campus Utsav, Fergusson College Road, Pune 411004",
      safeUntil: hoursFromNow(4),
      description: "Sealed snack boxes from a college event, ready for local pickup.",
      image: imageUrls.snacks,
      alt: "Indian savoury snacks packed for collection",
      donor: "Campus Utsav Team",
      meals: 0
    }
  ];

  const claims = [
    {
      id: "claim-201",
      name: "Bread and buns",
      quantity: "10 packets",
      location: "Asha Bakery & Sweets, Kothrud, Pune 411038",
      donor: "Asha Bakery & Sweets",
      status: "Reserved",
      image: imageUrls.bakery,
      meals: 0
    },
    {
      id: "claim-202",
      name: "Rice, dal, roti and seasonal sabzi",
      quantity: "20 plates",
      location: "Shivaji College Hostel Mess, Deccan, Pune 411004",
      donor: "Shivaji College Hostel Mess",
      status: "Picked Up",
      image: imageUrls.thali,
      meals: 20
    },
    {
      id: "claim-203",
      name: "Vegetable pulao",
      quantity: "35 portions",
      location: "Deccan Heritage Hotel, Shivajinagar, Pune 411005",
      donor: "Deccan Heritage Hotel",
      status: "Delivered",
      image: imageUrls.thali,
      meals: 35
    }
  ];

  const notifications = [
    {
      id: "notice-201",
      kind: "claim",
      icon: "bi-bag-check",
      title: "Pickup reminder",
      message: "Bread and buns is reserved in this local demo.",
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
    return date.toLocaleString("en-IN", {
      timeZone: "Asia/Kolkata",
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
    filtered.forEach(function (donation, index) {
      const urgency = urgencyFor(donation.safeUntil);
      const card = document.createElement("article");
      card.className = "ngo-food-card mr-card-entrance mr-stagger";
      card.style.setProperty("--mr-stagger-delay", (index * 70) + "ms");

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
      claimButton.textContent = "Claim Food";
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
      document.getElementById("meals-rescued").textContent = mealsRescued.toLocaleString("en-IN");
      addNotification("Claim delivered", claim.name + " was marked as delivered in this local demo.", "claim");
    }
    renderClaims();
    showMessage("Claim status updated locally. No backend was contacted.");
  }

  function renderClaims() {
    els.claimsList.replaceChildren();
    claims.forEach(function (claim) {
      const item = document.createElement("article");
      item.className = "ngo-claim-card mr-card-entrance";
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
      time.textContent = notification.createdAt.toLocaleString("en-IN", {
        timeZone: "Asia/Kolkata",
        month: "short",
        day: "numeric",
        hour: "numeric",
        minute: "2-digit"
      }) + " · demo";
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
      meals: donation.meals || 0
    });
    donations.splice(index, 1);
    renderDonations();
    renderClaims();
    setTimeout(function () {
      var firstClaim = els.claimsList.querySelector(".ngo-claim-card");
      if (firstClaim) {
        firstClaim.classList.add("mr-success-bounce");
        setTimeout(function () {
          firstClaim.classList.remove("mr-success-bounce");
        }, 550);
      }
    }, 0);
    addNotification("Demo claim added", donation.name + " is now in My Claims. No real food was reserved.", "claim");
    showMessage("Food rescued successfully! Added to My Claims (demo only).");
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

  var prefersReducedMotion = window.matchMedia("(prefers-reduced-motion: reduce)").matches;

  function playArrivalAnimation(onComplete) {
    var success = document.getElementById("arrival-success");
    if (success) success.hidden = true;
    els.arrival.classList.remove("is-animating");
    void els.arrival.offsetWidth;
    els.arrival.classList.add("is-animating");
    window.clearTimeout(arrivalAnimationTimer);
    var delay = prefersReducedMotion ? 0 : 2000;
    arrivalAnimationTimer = window.setTimeout(function () {
      if (success) success.hidden = false;
      if (typeof onComplete === "function") onComplete();
    }, delay);
  }

  function revealDemoDonation(isPreview) {
    var newDonation = {
      id: "food-arrival-105",
      name: "Idli and chutney breakfast plates",
      category: "Cooked Meals",
      quantity: "30 plates",
      location: "Prerna Student Residence, Aundh, Pune 411007",
      safeUntil: hoursFromNow(5),
      description: "Fresh idli and chutney from the hostel breakfast service, packed for prompt pickup.",
      image: imageUrls.thali,
      alt: "Fresh idli and chutney breakfast plates ready for pickup",
      donor: "Prerna Student Residence",
      meals: 30
    };

    var alreadyExists = donations.some(function (d) { return d.id === newDonation.id; });
    if (alreadyExists) {
      els.arrivalMessage.textContent = newDonation.name + " is already waiting in Available Meals.";
      playArrivalAnimation();
      return;
    }

    els.arrivalMessage.textContent = isPreview ? "Preview: a new donation is arriving..." : "A fresh donation is arriving...";
    playArrivalAnimation(function () {
      donations.unshift(newDonation);
      renderDonations();
      addNotification("New food donation arrived!", newDonation.name + " — 30 plates, safe until " + formatDate(newDonation.safeUntil) + ".", "new");
      els.arrivalMessage.textContent = newDonation.name + " is now available for rescue.";
      // Highlight the newly added card
      var firstCard = els.foodGrid.querySelector(".ngo-food-card");
      if (firstCard) {
        firstCard.classList.add("ngo-new-arrival");
        setTimeout(function () { firstCard.classList.remove("ngo-new-arrival"); }, 1600);
      }
    });
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

  document.getElementById("meals-rescued").textContent = mealsRescued.toLocaleString("en-IN");

  els.search.addEventListener("input", renderDonations);
  els.category.addEventListener("change", renderDonations);
  document.getElementById("preview-arrival").addEventListener("click", function () { revealDemoDonation(true); });

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
    var claimBtn = document.getElementById("modal-claim-button");
    claimBtn.classList.add("mr-success-bounce");
    setTimeout(function () {
      claimBtn.classList.remove("mr-success-bounce");
    }, 550);
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

  window.setTimeout(function () { revealDemoDonation(false); }, 1200);
}());
