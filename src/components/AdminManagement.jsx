import { useEffect, useState } from "react";
import { httpsCallable } from "firebase/functions";
import { initializeApp, deleteApp } from "firebase/app";
import { getAuth, signInWithEmailAndPassword, sendEmailVerification, signOut } from "firebase/auth";
import { functions, firebaseConfig } from "../firebase/config";
import { getAllRoles } from "../utils/permissions";
import "./AdminManagement.css";

// This whole page only ever talks to 4 Cloud Functions (all defined in
// functions/index.js, all of which re-check server-side that the caller
// is a super-admin before doing anything):
//   listAdmins          -> loads the table below
//   createAdminAccount  -> the "Add Admin" form
//   updateAdminRole     -> the role <select> in each row
//   deleteAdminAccount  -> the "Remove" button in each row

/**
 * createAdminAccount (Cloud Function, Admin SDK) creates the new admin's
 * Auth user directly - it can't trigger Firebase's own "verify your
 * email" email, because that email is only ever sent by the CLIENT SDK's
 * sendEmailVerification(), which needs an actual signed-in session for
 * that user.
 *
 * So right after the new account is created, we sign into it for a split
 * second on a throwaway SECOND Firebase app instance (not the `auth` the
 * super-admin is already signed into), fire sendEmailVerification(), then
 * sign out and tear the throwaway app down. The super-admin's own session
 * on the main `auth` instance is never touched.
 */
const sendVerificationEmailFor = async (email, password) => {
  const tempApp = initializeApp(firebaseConfig, `admin-verify-${Date.now()}`);
  const tempAuth = getAuth(tempApp);
  try {
    const cred = await signInWithEmailAndPassword(tempAuth, email, password);
    await sendEmailVerification(cred.user);
  } finally {
    await signOut(tempAuth).catch(() => {});
    await deleteApp(tempApp).catch(() => {});
  }
};

const AdminManagement = () => {
  const [admins, setAdmins] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // Add-admin form state
  const [form, setForm] = useState({ name: "", email: "", password: "", role: "support" });
  const [creating, setCreating] = useState(false);
  const [formError, setFormError] = useState("");
  const [formSuccess, setFormSuccess] = useState("");

  // Per-row busy state so only the row being changed shows a spinner/disable
  const [busyUid, setBusyUid] = useState(null);

  const roles = getAllRoles();

  const loadAdmins = async () => {
    setLoading(true);
    setError("");
    try {
      const listAdmins = httpsCallable(functions, "listAdmins");
      const result = await listAdmins();
      setAdmins(result.data.admins || []);
    } catch (err) {
      console.error("listAdmins error:", err);
      setError(err.message || "Could not load admins.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    loadAdmins();
  }, []);

  const handleFormChange = (key, value) => {
    setForm((prev) => ({ ...prev, [key]: value }));
  };

  const handleCreateAdmin = async (e) => {
    e.preventDefault();
    setFormError("");
    setFormSuccess("");

    if (!form.email || !form.password || !form.role) {
      setFormError("Email, password and role are required.");
      return;
    }

    setCreating(true);
    try {
      const trimmedEmail = form.email.trim();

      const createAdminAccount = httpsCallable(functions, "createAdminAccount");
      await createAdminAccount({
        email: trimmedEmail,
        password: form.password,
        name: form.name.trim(),
        role: form.role,
      });

      // Account exists now but isVerified is false by default (Firebase
      // Auth's own emailVerified flag) - send the verification link
      // before telling the super-admin it's done, so the popup only ever
      // says "sent" once the email has actually gone out.
      try {
        await sendVerificationEmailFor(trimmedEmail, form.password);
        window.alert(
          `Admin account created. A verification link has been sent to ${trimmedEmail} - ` +
          `they must open it from that Gmail/email inbox and click the link before they can log in.`
        );
      } catch (verifyErr) {
        // The account itself was created fine - only the verification
        // email failed to send. Don't lose that fact silently.
        console.error("sendEmailVerification error:", verifyErr);
        window.alert(
          `Admin account created for ${trimmedEmail}, but the verification email could not be sent ` +
          `(${verifyErr.message || "unknown error"}). They won't be able to log in until they're verified - ` +
          `ask them to use "Forgot password" on the login screen to trigger a fresh verification email, or recreate the account.`
        );
      }

      setFormSuccess(`Admin account created for ${trimmedEmail}. Verification link sent.`);
      setForm({ name: "", email: "", password: "", role: "support" });
      loadAdmins();
    } catch (err) {
      console.error("createAdminAccount error:", err);
      setFormError(err.message || "Could not create admin account.");
    } finally {
      setCreating(false);
    }
  };

  const handleRoleChange = async (uid, newRole) => {
    setBusyUid(uid);
    setError("");
    try {
      const updateAdminRole = httpsCallable(functions, "updateAdminRole");
      await updateAdminRole({ uid, role: newRole });
      setAdmins((prev) => prev.map((a) => (a.uid === uid ? { ...a, role: newRole } : a)));
    } catch (err) {
      console.error("updateAdminRole error:", err);
      setError(err.message || "Could not update role.");
    } finally {
      setBusyUid(null);
    }
  };

  const handleDelete = async (admin) => {
    const ok = window.confirm(`Remove admin "${admin.email}"? This can't be undone.`);
    if (!ok) return;

    setBusyUid(admin.uid);
    setError("");
    try {
      const deleteAdminAccount = httpsCallable(functions, "deleteAdminAccount");
      await deleteAdminAccount({ uid: admin.uid });
      setAdmins((prev) => prev.filter((a) => a.uid !== admin.uid));
    } catch (err) {
      console.error("deleteAdminAccount error:", err);
      setError(err.message || "Could not remove admin.");
    } finally {
      setBusyUid(null);
    }
  };

  return (
    <div className="admin-mgmt-container">
      <div className="page-header">
        <div>
          <h2>Admin Management</h2>
          <p>Add new admin accounts and control what each one can access.</p>
        </div>
      </div>

      {/* Add Admin form */}
      <div className="admin-mgmt-card">
        <h3>Add a new admin</h3>

        {formError && <div className="admin-mgmt-error">{formError}</div>}
        {formSuccess && <div className="admin-mgmt-success">{formSuccess}</div>}

        <form onSubmit={handleCreateAdmin} className="admin-mgmt-form">
          <div className="admin-mgmt-field">
            <label>Name</label>
            <input
              type="text"
              value={form.name}
              onChange={(e) => handleFormChange("name", e.target.value)}
              placeholder="Full name"
            />
          </div>

          <div className="admin-mgmt-field">
            <label>Email</label>
            <input
              type="email"
              value={form.email}
              onChange={(e) => handleFormChange("email", e.target.value)}
              placeholder="name@example.com"
              required
            />
          </div>

          <div className="admin-mgmt-field">
            <label>Password</label>
            <input
              type="password"
              value={form.password}
              onChange={(e) => handleFormChange("password", e.target.value)}
              placeholder="At least 6 characters"
              required
            />
          </div>

          <div className="admin-mgmt-field">
            <label>Role (privileges)</label>
            <select value={form.role} onChange={(e) => handleFormChange("role", e.target.value)}>
              {roles.map((r) => (
                <option key={r.value} value={r.value}>{r.label}</option>
              ))}
            </select>
          </div>

          <button type="submit" className="admin-mgmt-submit" disabled={creating}>
            {creating ? "Creating..." : "Add Admin"}
          </button>
        </form>
      </div>

      {/* Existing admins table */}
      <div className="admin-mgmt-card">
        <h3>Existing admins</h3>

        {error && <div className="admin-mgmt-error">{error}</div>}

        {loading ? (
          <p className="admin-mgmt-muted">Loading admins...</p>
        ) : admins.length === 0 ? (
          <p className="admin-mgmt-muted">No admin accounts found.</p>
        ) : (
          <div className="admin-mgmt-table-wrap">
            <table className="admin-mgmt-table">
              <thead>
                <tr>
                  <th>Name</th>
                  <th>Email</th>
                  <th>Role (privileges)</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {admins.map((a) => (
                  <tr key={a.uid}>
                    <td>{a.name || "\u2014"}</td>
                    <td>{a.email || "\u2014"}</td>
                    <td>
                      <select
                        value={a.role}
                        disabled={busyUid === a.uid}
                        onChange={(e) => handleRoleChange(a.uid, e.target.value)}
                      >
                        {roles.map((r) => (
                          <option key={r.value} value={r.value}>{r.label}</option>
                        ))}
                      </select>
                    </td>
                    <td className="admin-mgmt-actions">
                      <button
                        className="admin-mgmt-remove"
                        disabled={busyUid === a.uid}
                        onClick={() => handleDelete(a)}
                      >
                        Remove
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
};

export default AdminManagement;