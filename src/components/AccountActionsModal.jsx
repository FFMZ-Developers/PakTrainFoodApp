import { useState } from "react";
import { httpsCallable } from "firebase/functions";
import { functions } from "../firebase/config";
import "./AccountActionsModal.css";

/**
 * Module: account management actions - shared across Restaurant.jsx,
 * Riders.jsx, and Passengers.jsx so the disable/restrict/delete/message
 * UI (and the confirmation flow around the destructive ones) only exists
 * in one place.
 *
 * @param user  the row's data - needs at minimum { id, name } plus
 *              whatever accountDisabled/isRestricted flags it already has
 * @param role  "Restaurant" | "Delivery" | "Passenger"
 * @param onClose  called when the modal should close
 * @param onChanged  called after any successful action, so the parent can
 *                    refresh its listener-driven state if needed
 */
const AccountActionsModal = ({ user, role, onClose, onChanged }) => {
  const [mode, setMode] = useState("menu"); // menu | disable | restrict | delete | message
  const [reason, setReason] = useState("");
  const [messageBody, setMessageBody] = useState("");
  const [messageTitle, setMessageTitle] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  const isDisabled = !!user.accountDisabled;
  const isRestricted = !!user.isRestricted;

  const call = async (fnName, payload) => {
    setBusy(true);
    setError("");
    try {
      await httpsCallable(functions, fnName)(payload);
      onChanged && onChanged();
      onClose();
    } catch (e) {
      setError(e.message || "Something went wrong.");
    } finally {
      setBusy(false);
    }
  };

  const toggleDisabled = () => call("setAccountDisabled", {
    uid: user.id, role, disabled: !isDisabled, reason,
  });

  const toggleRestricted = () => call("setAccountRestricted", {
    uid: user.id, role, restricted: !isRestricted, reason,
  });

  const doDelete = () => call("deleteAccount", { uid: user.id, role });

  const sendMessage = () => call("sendAdminMessage", {
    uid: user.id, role, title: messageTitle, body: messageBody,
  });

  return (
    <div className="aam-overlay" onClick={onClose}>
      <div className="aam-sheet" onClick={(e) => e.stopPropagation()}>
        <div className="aam-header">
          <h3>{user.name || user.restaurantName || "Account"}</h3>
          <button className="aam-close" onClick={onClose}>&times;</button>
        </div>

        {error && <div className="aam-error">{error}</div>}

        {mode === "menu" && (
          <div className="aam-menu">
            <div className="aam-status-row">
              <span className={`aam-pill ${isDisabled ? "pill-red" : "pill-green"}`}>
                {isDisabled ? "Disabled" : "Active"}
              </span>
              {isRestricted && <span className="aam-pill pill-orange">Restricted</span>}
            </div>

            <button className="aam-action" onClick={() => setMode(isDisabled ? "enable-confirm" : "disable")}>
              {isDisabled ? "Re-enable Account" : "Disable Account"}
            </button>

            <button className="aam-action" onClick={() => setMode(isRestricted ? "unrestrict-confirm" : "restrict")}>
              {isRestricted ? "Lift Restriction" : "Restrict Account"}
            </button>

            <button className="aam-action" onClick={() => setMode("message")}>
              Send Message
            </button>

            <button className="aam-action aam-danger" onClick={() => setMode("delete")}>
              Delete Account
            </button>
          </div>
        )}

        {mode === "disable" && (
          <ReasonForm
            title="Disable this account?"
            note="They will not be able to log in at all until re-enabled."
            reason={reason} setReason={setReason}
            busy={busy}
            onConfirm={toggleDisabled}
            onBack={() => setMode("menu")}
            confirmLabel="Disable Account"
            confirmClass="aam-danger"
          />
        )}

        {mode === "enable-confirm" && (
          <ConfirmOnly
            title="Re-enable this account?"
            note="They will be able to log in again immediately."
            busy={busy}
            onConfirm={toggleDisabled}
            onBack={() => setMode("menu")}
            confirmLabel="Re-enable"
          />
        )}

        {mode === "restrict" && (
          <ReasonForm
            title="Restrict this account?"
            note="They can still log in and view their orders/wallet, but can't place or accept new orders until the restriction is lifted."
            reason={reason} setReason={setReason}
            busy={busy}
            onConfirm={toggleRestricted}
            onBack={() => setMode("menu")}
            confirmLabel="Restrict Account"
          />
        )}

        {mode === "unrestrict-confirm" && (
          <ConfirmOnly
            title="Lift this restriction?"
            note="They'll be able to place/accept orders normally again."
            busy={busy}
            onConfirm={toggleRestricted}
            onBack={() => setMode("menu")}
            confirmLabel="Lift Restriction"
          />
        )}

        {mode === "delete" && (
          <div className="aam-form">
            <p className="aam-warning">
              This permanently deletes their login and profile. Their past orders,
              wallet history, and chat records are kept. This cannot be undone.
            </p>
            <label className="aam-label">Type DELETE to confirm</label>
            <input className="aam-input" value={reason} onChange={(e) => setReason(e.target.value)} placeholder="DELETE" />
            <div className="aam-form-actions">
              <button className="aam-secondary" onClick={() => setMode("menu")}>Back</button>
              <button className="aam-danger" disabled={busy || reason !== "DELETE"} onClick={doDelete}>
                {busy ? "Deleting..." : "Permanently Delete"}
              </button>
            </div>
          </div>
        )}

        {mode === "message" && (
          <div className="aam-form">
            <label className="aam-label">Title (optional)</label>
            <input className="aam-input" value={messageTitle} onChange={(e) => setMessageTitle(e.target.value)} placeholder="Message from PakTrainFood" />
            <label className="aam-label">Message</label>
            <textarea className="aam-textarea" rows={4} value={messageBody} onChange={(e) => setMessageBody(e.target.value)} placeholder="Type your message..." />
            <div className="aam-form-actions">
              <button className="aam-secondary" onClick={() => setMode("menu")}>Back</button>
              <button className="aam-primary" disabled={busy || !messageBody.trim()} onClick={sendMessage}>
                {busy ? "Sending..." : "Send Message"}
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};

const ReasonForm = ({ title, note, reason, setReason, busy, onConfirm, onBack, confirmLabel, confirmClass = "aam-primary" }) => (
  <div className="aam-form">
    <p className="aam-note-title">{title}</p>
    <p className="aam-note">{note}</p>
    <label className="aam-label">Reason (shown to the user)</label>
    <textarea className="aam-textarea" rows={3} value={reason} onChange={(e) => setReason(e.target.value)} placeholder="e.g. Repeated late deliveries" />
    <div className="aam-form-actions">
      <button className="aam-secondary" onClick={onBack}>Back</button>
      <button className={confirmClass} disabled={busy} onClick={onConfirm}>
        {busy ? "Working..." : confirmLabel}
      </button>
    </div>
  </div>
);

const ConfirmOnly = ({ title, note, busy, onConfirm, onBack, confirmLabel }) => (
  <div className="aam-form">
    <p className="aam-note-title">{title}</p>
    <p className="aam-note">{note}</p>
    <div className="aam-form-actions">
      <button className="aam-secondary" onClick={onBack}>Back</button>
      <button className="aam-primary" disabled={busy} onClick={onConfirm}>
        {busy ? "Working..." : confirmLabel}
      </button>
    </div>
  </div>
);

export default AccountActionsModal;
