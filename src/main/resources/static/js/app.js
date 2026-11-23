(function () {
  "use strict";

  var prefersReduced = window.matchMedia("(prefers-reduced-motion: reduce)");

  /* ---------- Page entrance ---------- */
  function initPageEntrance() {
    var body = document.body;
    if (!body) return;

    body.classList.add("mr-entrance");

    function onAnimationEnd() {
      body.classList.add("mr-entered");
      body.removeEventListener("animationend", onAnimationEnd);
    }

    if (prefersReduced.matches) {
      body.classList.add("mr-entered");
      body.classList.remove("mr-entrance");
    } else if ("onanimationend" in window) {
      body.addEventListener("animationend", onAnimationEnd, { once: true });
      setTimeout(function () {
        body.classList.add("mr-entered");
        body.classList.remove("mr-entrance");
      }, 900);
    } else {
      body.classList.add("mr-entered");
      body.classList.remove("mr-entrance");
    }
  }

  /* ---------- Scroll reveal ---------- */
  function initScrollReveal() {
    if (prefersReduced.matches) {
      document.querySelectorAll(".mr-reveal, .mr-stagger").forEach(function (el) {
        el.classList.add("mr-revealed");
      });
      return;
    }

    var observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (entry.isIntersecting) {
            entry.target.classList.add("mr-revealed");
            observer.unobserve(entry.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: "0px 0px -10% 0px" }
    );

    document.querySelectorAll(".mr-reveal, .mr-stagger").forEach(function (el, index) {
      el.classList.add("mr-stagger-item");
      el.style.setProperty("--mr-stagger-delay", index * 90 + "ms");
      observer.observe(el);
    });
  }

  /* ---------- Animated counters ---------- */
  function initCounters() {
    var counters = document.querySelectorAll("[data-count]");
    if (prefersReduced.matches) {
      counters.forEach(function (el) {
        var target = Number(el.getAttribute("data-count"));
        var suffix = el.getAttribute("data-suffix") || "";
        var decimal = Number(el.getAttribute("data-decimal") || "0");
        el.textContent = (decimal ? target.toFixed(decimal) : Math.round(target).toLocaleString("en-IN")) + suffix;
      });
      return;
    }

    var observer = new IntersectionObserver(
      function (entries) {
        entries.forEach(function (entry) {
          if (!entry.isIntersecting) return;
          var el = entry.target;
          var target = Number(el.getAttribute("data-count"));
          var suffix = el.getAttribute("data-suffix") || "";
          var decimal = Number(el.getAttribute("data-decimal") || "0");
          var duration = 1200;
          var start = performance.now();

          function step(now) {
            var progress = Math.min((now - start) / duration, 1);
            var eased = 1 - Math.pow(1 - progress, 3);
            var value = eased * target;
            el.textContent = (decimal ? value.toFixed(decimal) : Math.floor(value).toLocaleString("en-IN")) + suffix;
            if (progress < 1) {
              requestAnimationFrame(step);
            } else {
              el.textContent = (decimal ? target.toFixed(decimal) : Math.round(target).toLocaleString("en-IN")) + suffix;
            }
          }
          requestAnimationFrame(step);
          observer.unobserve(el);
        });
      },
      { threshold: 0.4 }
    );

    counters.forEach(function (el) { observer.observe(el); });
  }

  /* ---------- Button click feedback ---------- */
  function initButtonFeedback() {
    document.addEventListener("click", function (event) {
      var button = event.target.closest("button, .mr-btn, a.btn, .ngo-button, .admin-action-button");
      if (!button || button.matches("[disabled], .disabled")) return;
      button.classList.add("mr-clicked");
      setTimeout(function () {
        button.classList.remove("mr-clicked");
      }, 170);
    });
  }

  /* ---------- Initialize ---------- */
  function init() {
    initPageEntrance();
    initScrollReveal();
    initCounters();
    initButtonFeedback();
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", init);
  } else {
    init();
  }
})();
