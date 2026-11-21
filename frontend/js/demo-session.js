(function () {
  const pendingRoleKey = "rescuePlateDemoPendingRole";
  const sessionRoleKey = "rescuePlateDemoRole";
  const roles = ["donor", "ngo", "admin"];

  function isRole(role) {
    return roles.includes(role);
  }

  function getPendingRole() {
    const role = localStorage.getItem(pendingRoleKey);
    return isRole(role) ? role : "";
  }

  function getSessionRole() {
    const role = localStorage.getItem(sessionRoleKey);
    return isRole(role) ? role : "";
  }

  function setPendingRole(role) {
    if (!isRole(role)) {
      throw new Error("Cannot save an unsupported RescuePlate demo role.");
    }
    localStorage.setItem(pendingRoleKey, role);
  }

  function startSession(role) {
    if (!isRole(role)) {
      throw new Error("Cannot start an unsupported RescuePlate demo role.");
    }
    localStorage.setItem(sessionRoleKey, role);
    localStorage.removeItem(pendingRoleKey);
  }

  function endSession() {
    localStorage.removeItem(sessionRoleKey);
    localStorage.removeItem(pendingRoleKey);
  }

  document.addEventListener("click", function (event) {
    const target = event.target;
    const logoutLink = target && target.closest ? target.closest("[data-demo-logout]") : null;
    if (logoutLink) {
      endSession();
    }
  });

  window.RescuePlateDemo = Object.freeze({
    endSession: endSession,
    getPendingRole: getPendingRole,
    getSessionRole: getSessionRole,
    setPendingRole: setPendingRole,
    startSession: startSession
  });
})();
