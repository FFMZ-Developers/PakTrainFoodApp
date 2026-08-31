import { useEffect, useState } from "react";
import { collection, onSnapshot } from "firebase/firestore";
import { db } from "../firebase/config";
import AccountActionsModal from "./AccountActionsModal";
import "./Restaurant.css";
import "./Passengers.css";

/**
 * Module: passenger management. Unlike restaurants/riders, passengers
 * never go through a verification wizard - there was previously no admin
 * screen for them at all. This is deliberately simple: a searchable list
 * with the same disable/restrict/delete/message actions the other two
 * roles have, via the same shared AccountActionsModal.
 */
const Passengers = () => {
  const [passengers, setPassengers] = useState([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState("");
  const [managingUser, setManagingUser] = useState(null);

  useEffect(() => {
    const unsub = onSnapshot(
      collection(db, "Users", "Passenger", "Register"),
      (snap) => {
        setPassengers(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
        setLoading(false);
      },
      (err) => {
        console.error("Passengers listener failed:", err);
        setLoading(false);
      }
    );
    return () => unsub();
  }, []);

  const filtered = passengers.filter((p) => {
    const q = searchQuery.toLowerCase();
    return (
      !q ||
      (p.name || "").toLowerCase().includes(q) ||
      (p.email || "").toLowerCase().includes(q) ||
      (p.phone || "").toLowerCase().includes(q)
    );
  });

  const restrictedCount = passengers.filter((p) => p.isRestricted).length;
  const disabledCount = passengers.filter((p) => p.accountDisabled).length;

  return (
    <div className="restaurant-page-container">
      <div className="page-header">
        <div>
          <h2>Passengers</h2>
          <p>Every registered passenger account on the platform.</p>
        </div>
      </div>

      <div className="metrics-row">
        <div className="metric-mini-card">
          <h4>{loading ? "..." : passengers.length}</h4>
          <span>Total Passengers</span>
        </div>
        <div className="metric-mini-card">
          <h4 style={{ color: "#d97706" }}>{restrictedCount}</h4>
          <span>Restricted</span>
        </div>
        <div className="metric-mini-card">
          <h4 style={{ color: "#dc2626" }}>{disabledCount}</h4>
          <span>Disabled</span>
        </div>
      </div>

      <div className="table-toolbar-row">
        <input
          type="text"
          className="search-input"
          placeholder="Search by name, email, or phone..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
        />
      </div>

      <div className="table-responsive">
        <table className="rest-table">
          <thead>
            <tr>
              <th>NAME</th>
              <th>EMAIL</th>
              <th>PHONE</th>
              <th>STATUS</th>
              <th className="text-right">ACTIONS</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan={5} className="empty-table-cell">Loading...</td></tr>
            ) : filtered.length === 0 ? (
              <tr><td colSpan={5} className="empty-table-cell">No passengers found.</td></tr>
            ) : (
              filtered.map((p) => (
                <tr key={p.id}>
                  <td className="primary-text">{p.name || "Unnamed"}</td>
                  <td className="secondary-text">{p.email || "\u2014"}</td>
                  <td className="secondary-text">{p.phone || "\u2014"}</td>
                  <td>
                    {p.accountDisabled ? (
                      <span className="status-badge status-inactive"><span className="dot"></span>Disabled</span>
                    ) : p.isRestricted ? (
                      <span className="status-badge status-pending"><span className="dot"></span>Restricted</span>
                    ) : (
                      <span className="status-badge status-active"><span className="dot"></span>Active</span>
                    )}
                  </td>
                  <td className="text-right">
                    <button className="btn-action-outline" onClick={() => setManagingUser(p)}>Manage</button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {managingUser && (
        <AccountActionsModal
          user={managingUser}
          role="Passenger"
          onClose={() => setManagingUser(null)}
        />
      )}
    </div>
  );
};

export default Passengers;
