// ============================================================
// Role-based access control for the admin panel.
//
// Role comes from Firestore: admins/{uid}.role  (a plain string)
// Login.jsx reads that field at sign-in and stores it in
// localStorage("role"). Everything here just reads that value back -
// nothing in this file talks to Firestore directly.
//
// ------------------------------------------------------------
// HOW TO ADD A NEW ROLE LATER (this is the only file you touch):
//   1. Add a new key to ROLES below, e.g. SUPPORT: "support".
//   2. Add a matching entry to ROLE_PERMISSIONS with the list of
//      tab names that role is allowed to open.
//   3. (Optional) Add a friendly display label to ROLE_LABELS.
//   4. In Firestore, set that admin's `role` field to the same
//      string value ("support"). That's it - Dashboard.jsx,
//      the sidebar, and the backend all pick it up automatically.
//
// Tab names below MUST exactly match the `name` field used in
// Dashboard.jsx's menuItems array.
// ============================================================

export const ROLES = {
  SUPER_ADMIN: "super-admin",
  MANAGER: "manager",
  SUPPORT: "support",
  FINANCE: "finance",
};

// Which tabs each role is allowed to see/open.
// super-admin always gets everything by definition (see canAccessTab).
const ROLE_PERMISSIONS = {
  [ROLES.SUPER_ADMIN]: [
    "Dashboard", "Restaurants", "Delivery Riders", "Payments",
    "Passengers", "Orders", "Disputes", "Settings", "Admin Management",
  ],
  [ROLES.MANAGER]: [
    "Dashboard", "Restaurants", "Delivery Riders",
    "Passengers", "Orders", "Disputes",
  ],
  [ROLES.SUPPORT]: [
    "Dashboard", "Orders", "Disputes", "Passengers",
  ],
  [ROLES.FINANCE]: [
    "Dashboard", "Payments", "Orders",
  ],
};

// Friendly labels shown in the header (e.g. "Super Admin" instead of
// the raw Firestore string "super-admin"). Falls back to the raw role
// string in upper-case if a role has no label defined.
const ROLE_LABELS = {
  [ROLES.SUPER_ADMIN]: "Super Admin",
  [ROLES.MANAGER]: "Manager",
  [ROLES.SUPPORT]: "Support",
  [ROLES.FINANCE]: "Finance",
};

/**
 * Reads the role stored at login.
 * Defaults to the LEAST-privileged role if nothing was stored -
 * fail-safe, not fail-open.
 */
export function getCurrentRole() {
  return localStorage.getItem("role") || ROLES.SUPPORT;
}

export function isSuperAdmin(role = getCurrentRole()) {
  return role === ROLES.SUPER_ADMIN;
}

/** Can the given role open this tab? */
export function canAccessTab(tabName, role = getCurrentRole()) {
  if (isSuperAdmin(role)) return true;
  const allowed = ROLE_PERMISSIONS[role];
  if (!allowed) return false; // unknown/unrecognised role -> no access
  return allowed.includes(tabName);
}

/** Human-friendly label for the header, e.g. "Manager". */
export function roleLabel(role = getCurrentRole()) {
  return ROLE_LABELS[role] || role.toUpperCase();
}

/** All known roles as {value, label} pairs, for populating a <select>. */
export function getAllRoles() {
  return Object.values(ROLES).map((value) => ({ value, label: ROLE_LABELS[value] || value }));
}
