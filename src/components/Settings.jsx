import { useEffect, useState } from "react";
import "./Settings.css";

import { auth } from "../firebase/config";

const FUNCTIONS_BASE = "https://us-central1-paktrainfoodservice.cloudfunctions.net";

// Field definitions drive both the form layout and the save payload, so
// adding a new tunable setting later only means adding one entry here -
// the form, labels, and grouping all follow automatically.
const FIELD_GROUPS = [
  {
    title: "Rider dispatch",
    description: "How the app searches for a delivery rider once a restaurant marks an order ready.",
    fields: [
      { key: "riderSearchRadiiKm", label: "Search radius steps (km, comma separated)", type: "intList" },
      { key: "riderSearchStepDelaySeconds", label: "Delay between each radius step (seconds)", type: "int" },
      { key: "riderSearchTimeoutSeconds", label: "Total search timeout (seconds)", type: "int" },
    ],
  },
  {
    title: "Order timing",
    description: "When an order becomes visible to the restaurant, and how much prep time they get.",
    fields: [
      { key: "orderDispatchEtaThresholdMinutes", label: "Send order to restaurant when ETA reaches (minutes)", type: "int" },
      { key: "riderTransitBufferMinutes", label: "Rider transit buffer subtracted from prep time (minutes)", type: "int" },
      { key: "fallbackTrainSpeedKmph", label: "Fallback train speed before enough GPS data exists (km/h)", type: "number" },
    ],
  },
  {
    title: "Reliability scoring",
    description: "Strike limits and score adjustments for restaurants and riders.",
    fields: [
      { key: "reliabilityStartingScore", label: "Starting reliability score", type: "int" },
      { key: "reliabilityStrikePenalty", label: "Points deducted per strike", type: "int" },
      { key: "reliabilityCompletionBonus", label: "Points added per completed order", type: "int" },
      { key: "restaurantReliabilityStrikeLimit", label: "Restaurant strikes before auto-pause", type: "int" },
      { key: "restaurantReliabilityWindowDays", label: "Restaurant strike window (days)", type: "int" },
      { key: "riderReliabilityStrikeLimit", label: "Rider strikes before auto-pause", type: "int" },
      { key: "riderReliabilityWindowDays", label: "Rider strike window (days)", type: "int" },
    ],
  },
  {
    title: "Failure handling",
    description: "Compensation and detection thresholds for the order failure scenarios.",
    fields: [
      { key: "riderAttemptedDeliveryFeePercent", label: "Rider fee % paid for an attempted (undelivered) order", type: "int" },
      { key: "journeyStallMinutesBeforeCancel", label: "Minutes of no progress before auto-cancelling an order", type: "int" },
    ],
  },
  {
    title: "Cities",
    description: "City list shown in the restaurant/rider signup wizard's city dropdown. Add or remove a city here without needing a new app release.",
    fields: [
      { key: "cities", label: "Cities (comma separated)", type: "stringList" },
    ],
  },
];

const Settings = () => {

  const [values, setValues] = useState({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [successMessage, setSuccessMessage] = useState("");
  const [isDefault, setIsDefault] = useState(false);

  useEffect(() => {
    loadConfig();
  }, []);

  const loadConfig = async () => {

    setLoading(true);
    setError("");

    try {
      const response = await fetch(`${FUNCTIONS_BASE}/getOrderConfig`, {
        method: "GET",
        headers: { "Content-Type": "application/json" },
      });

      const data = await response.json();

      if (!data.success) throw new Error(data.error || "Failed to load settings.");

      setValues(data.config || {});
      setIsDefault(!!data.isDefault);

    } catch (err) {
      console.error("Load settings error:", err);
      setError("Could not load settings: " + err.message);
    } finally {
      setLoading(false);
    }
  };

  const handleFieldChange = (key, type, rawValue) => {

    let parsed = rawValue;

    if (type === "int") {
      parsed = rawValue === "" ? "" : parseInt(rawValue, 10);
    } else if (type === "number") {
      parsed = rawValue === "" ? "" : parseFloat(rawValue);
    } else if (type === "intList") {
      // Kept as a raw string while typing (e.g. "1, 2, 3") - converted to
      // a real number array only on save, so the user can type freely
      // without the field fighting them mid-edit.
      parsed = rawValue;
    }

    setValues((prev) => ({ ...prev, [key]: parsed }));
  };

  const handleSave = async () => {

    setSaving(true);
    setError("");
    setSuccessMessage("");

    try {
      const user = auth.currentUser;

      if (!user) {
        throw new Error("You must be signed in as an admin.");
      }

      const idToken = await user.getIdToken();

      // Normalise the comma-separated radius field into a real number
      // array right before sending, regardless of what type it's
      // currently holding in local state.
      const payload = { ...values };

      if (typeof payload.riderSearchRadiiKm === "string") {
        payload.riderSearchRadiiKm = payload.riderSearchRadiiKm
            .split(",")
            .map((s) => parseInt(s.trim(), 10))
            .filter((n) => !Number.isNaN(n));
      }

      if (typeof payload.cities === "string") {
        payload.cities = payload.cities
            .split(",")
            .map((s) => s.trim())
            .filter((s) => s.length > 0);
      }

      const response = await fetch(`${FUNCTIONS_BASE}/updateOrderConfig`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${idToken}`,
        },
        body: JSON.stringify(payload),
      });

      const data = await response.json();

      if (!data.success) throw new Error(data.error || "Save failed.");

      setSuccessMessage("Settings saved. Changes apply to the app in real time.");
      setIsDefault(false);

      // Re-fetch so the form reflects exactly what's now stored (e.g. the
      // radius list rendered back out as a clean comma-separated string).
      await loadConfig();

    } catch (err) {
      console.error("Save settings error:", err);
      setError("Could not save settings: " + err.message);
    } finally {
      setSaving(false);
    }
  };

  const displayValue = (field) => {

    const raw = values[field.key];

    if ((field.type === "intList" || field.type === "stringList") && Array.isArray(raw)) {
      return raw.join(", ");
    }

    return raw === undefined || raw === null ? "" : raw;
  };

  if (loading) {
    return (
      <div className="settings-container">
        <p>Loading settings…</p>
      </div>
    );
  }

  return (
    <div className="settings-container">
      <div className="settings-header">
        <div>
          <h2>App settings</h2>
          <p>
            These thresholds control the live order pipeline - rider search,
            ETA-gated dispatch, and reliability scoring. Changes take effect
            immediately, no app release needed.
          </p>
        </div>
        <button
          className="btn-primary"
          onClick={handleSave}
          disabled={saving}
        >
          {saving ? "Saving…" : "Save changes"}
        </button>
      </div>

      {isDefault && (
        <div className="settings-banner settings-banner-info">
          No settings have been saved yet - showing default values. Save once to create the config document.
        </div>
      )}

      {error && <div className="settings-banner settings-banner-error">{error}</div>}
      {successMessage && <div className="settings-banner settings-banner-success">{successMessage}</div>}

      {FIELD_GROUPS.map((group) => (
        <div className="settings-group" key={group.title}>
          <h3>{group.title}</h3>
          <p className="settings-group-desc">{group.description}</p>

          <div className="settings-fields">
            {group.fields.map((field) => (
              <label
                className={field.type === "stringList" ? "settings-field settings-field-wide" : "settings-field"}
                key={field.key}
              >
                <span>{field.label}</span>
                {field.type === "stringList" ? (
                  <textarea
                    rows={4}
                    value={displayValue(field)}
                    onChange={(e) => handleFieldChange(field.key, field.type, e.target.value)}
                  />
                ) : (
                  <input
                    type="text"
                    value={displayValue(field)}
                    onChange={(e) => handleFieldChange(field.key, field.type, e.target.value)}
                  />
                )}
              </label>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
};

export default Settings;
