import { useEffect, useState, useCallback } from "react";
import { collection, query, where, onSnapshot } from "firebase/firestore";
import { httpsCallable } from "firebase/functions";
import { db, functions } from "../firebase/config";
import "./Payments.css"; // reuses the same card/table styling

const formatCurrency = (amount) =>
  new Intl.NumberFormat("en-PK", { style: "currency", currency: "PKR", minimumFractionDigits: 0 }).format(Number(amount || 0));


/** Matches the app's OrderNumberUtils - sequential number, id as fallback. */
const orderRef = (o) =>
  o && typeof o.orderNumber === "number" && o.orderNumber > 0
    ? "Order #" + String(o.orderNumber).padStart(4, "0")
    : "Order #" + String(o?.id || "").slice(0, 6).toUpperCase();

const formatTime = (ms) => (ms ? new Date(ms).toLocaleString() : "—");

const locationText = (loc) => (loc ? `${loc.lat.toFixed(5)}, ${loc.lng.toFixed(5)}` : "Not available");

const mapLink = (loc) => (loc ? `https://www.google.com/maps?q=${loc.lat},${loc.lng}` : null);

/**
 * Module - the admin's decision screen for rider-reported delivery
 * failures. onDeliveryFailed.js (main app backend) freezes these orders
 * with orderStatus: "disputed" instead of auto-computing a split - this
 * is the ONLY place that split gets decided, since it genuinely needs a
 * human to weigh conflicting claims (rider vs passenger).
 */
const Disputes = () => {
  const [disputes, setDisputes] = useState([]);
  const [loading, setLoading] = useState(true);
  const [selected, setSelected] = useState(null);

  const [restaurantShare, setRestaurantShare] = useState("");
  const [riderShare, setRiderShare] = useState("");
  const [passengerRefund, setPassengerRefund] = useState("");
  const [restaurantReason, setRestaurantReason] = useState("");
  const [riderReason, setRiderReason] = useState("");
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    const q = query(collection(db, "Orders"), where("disputeStatus", "==", "pending_review"));

    const unsub = onSnapshot(
      q,
      (snap) => {
        const list = snap.docs.map((d) => ({ id: d.id, ...d.data() }));
        list.sort((a, b) => (b.disputeCreatedAt || 0) - (a.disputeCreatedAt || 0));
        setDisputes(list);
        setLoading(false);
      },
      (err) => {
        console.error("Disputes listener failed:", err);
        setLoading(false);
      }
    );

    return () => unsub();
  }, []);

  const openDispute = (order) => {
    setSelected(order);

    // Default suggestion: full food cost to restaurant (they made it),
    // nothing to rider yet, rest refunded to passenger - admin adjusts
    // from here based on what actually happened.
    setRestaurantShare(String(Math.round(order.subtotal || 0)));
    setRiderShare("0");
    setPassengerRefund(String(Math.round((order.totalPrice || 0) - (order.subtotal || 0))));
    setRestaurantReason("");
    setRiderReason("");
  };

  const closeModal = () => setSelected(null);

  const totalAllocated = useCallback(() => {
    return (Number(restaurantShare) || 0) + (Number(riderShare) || 0) + (Number(passengerRefund) || 0);
  }, [restaurantShare, riderShare, passengerRefund]);

  const handleResolve = async () => {
    if (!selected) return;

    setSubmitting(true);

    try {
      const resolveDispute = httpsCallable(functions, "resolveDispute");

      await resolveDispute({
        orderId: selected.id,
        restaurantShare: Number(restaurantShare) || 0,
        riderShare: Number(riderShare) || 0,
        passengerRefund: Number(passengerRefund) || 0,
        restaurantReason: restaurantReason.trim(),
        riderReason: riderReason.trim(),
      });

      alert("Dispute resolved - restaurant/rider credited, passenger refund initiated.");
      closeModal();
    } catch (err) {
      console.error("resolveDispute failed:", err);
      alert("Failed to resolve: " + (err.message || ""));
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="payments-container">
      <div className="payments-table-card">
        <h3 style={{ padding: "20px" }}>
          Order Disputes ({disputes.length}) - awaiting your decision
        </h3>

        <table className="payments-table">
          <thead>
            <tr>
              <th>Order ID</th>
              <th>Reported By</th>
              <th>Reason</th>
              <th>Pickup Happened?</th>
              <th>Reported At</th>
              <th>Action</th>
            </tr>
          </thead>
          <tbody>
            {loading ? (
              <tr><td colSpan="6">Loading...</td></tr>
            ) : disputes.length === 0 ? (
              <tr><td colSpan="6">No disputes awaiting review 🎉</td></tr>
            ) : (
              disputes.map((d) => (
                <tr key={d.id}>
                  <td>{orderRef(d)}</td>
                  <td>{d.failureReportedBy === "restaurant" ? "Restaurant" : "Rider"}</td>
                  <td>{d.failureReason || "No reason given"}</td>
                  <td>{d.pickupHadHappened ? "Yes" : "No"}</td>
                  <td>{formatTime(d.timelineFailureReportedAt)}</td>
                  <td>
                    <button className="pay-now-btn" onClick={() => openDispute(d)}>
                      Review
                    </button>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      {selected && (
        <div className="payout-modal-overlay">
          <div className="payout-modal-sheet" style={{ maxWidth: "640px" }}>
            <h3>Resolve Dispute - {orderRef(selected)}</h3>

            <div style={{ background: "#f7f7f7", padding: "12px", borderRadius: "8px", margin: "12px 0" }}>
              <p><b>Reported by:</b> {selected.failureReportedBy === "restaurant" ? "Restaurant" : "Rider"}
                {selected.failureReportType ? ` (${selected.failureReportType.replace("_", " ")})` : ""}</p>
              <p><b>Reason given:</b> {selected.failureReason || "No reason given"}</p>
              <p><b>Food picked up from restaurant?</b> {selected.pickupHadHappened ? "Yes" : "No"}</p>

              {selected.failurePhotoUrl && (
                <div style={{ marginTop: "10px" }}>
                  <p style={{ marginBottom: "6px" }}><b>Photo evidence:</b></p>
                  <a href={selected.failurePhotoUrl} target="_blank" rel="noreferrer">
                    <img
                      src={selected.failurePhotoUrl}
                      alt="Reported problem evidence"
                      style={{ maxWidth: "100%", maxHeight: "260px", borderRadius: "6px", border: "1px solid #ddd" }}
                    />
                  </a>
                </div>
              )}
            </div>

            <div style={{ marginBottom: "16px" }}>
              <h4 style={{ marginBottom: "6px" }}>Timeline</h4>
              <table className="payments-table">
                <tbody>
                  <tr><td>Order placed</td><td>{formatTime(selected.timelineOrderPlacedAt)}</td></tr>
                  <tr><td>Restaurant accepted</td><td>{formatTime(selected.timelineAcceptedAt)}</td></tr>
                  <tr><td>Rider assigned</td><td>{formatTime(selected.timelineRiderAssignedAt)}</td></tr>
                  <tr><td>Rider arrived at restaurant</td><td>{formatTime(selected.timelineRiderArrivedAt)}</td></tr>
                  <tr><td>Food picked up</td><td>{formatTime(selected.timelinePickupAt)}</td></tr>
                  <tr><td>Failure reported</td><td>{formatTime(selected.timelineFailureReportedAt)}</td></tr>
                </tbody>
              </table>
            </div>

            <div style={{ marginBottom: "16px" }}>
              <h4 style={{ marginBottom: "6px" }}>Locations at time of report</h4>
              <p>
                Rider: {locationText(selected.disputeRiderLocation)}
                {mapLink(selected.disputeRiderLocation) && (
                  <> - <a href={mapLink(selected.disputeRiderLocation)} target="_blank" rel="noreferrer">View on map</a></>
                )}
              </p>
              <p>
                Passenger: {locationText(selected.disputePassengerLocation)}
                {mapLink(selected.disputePassengerLocation) && (
                  <> - <a href={mapLink(selected.disputePassengerLocation)} target="_blank" rel="noreferrer">View on map</a></>
                )}
              </p>
            </div>

            <div style={{ marginBottom: "16px" }}>
              <h4 style={{ marginBottom: "6px" }}>
                Total order value: {formatCurrency(selected.totalPrice)}
                {" "}(food: {formatCurrency(selected.subtotal)}, delivery: {formatCurrency(selected.deliveryFee)})
              </h4>

              <label>Restaurant share (Rs)</label>
              <input type="number" value={restaurantShare} onChange={(e) => setRestaurantShare(e.target.value)} style={{ width: "100%", marginBottom: "6px", padding: "8px" }} />
              <input type="text" placeholder="Reason if reduced (optional)" value={restaurantReason} onChange={(e) => setRestaurantReason(e.target.value)} style={{ width: "100%", marginBottom: "12px", padding: "8px" }} />

              <label>Rider share (Rs)</label>
              <input type="number" value={riderShare} onChange={(e) => setRiderShare(e.target.value)} style={{ width: "100%", marginBottom: "6px", padding: "8px" }} />
              <input type="text" placeholder="Reason if reduced (optional)" value={riderReason} onChange={(e) => setRiderReason(e.target.value)} style={{ width: "100%", marginBottom: "12px", padding: "8px" }} />

              <label>Passenger refund (Rs)</label>
              <input type="number" value={passengerRefund} onChange={(e) => setPassengerRefund(e.target.value)} style={{ width: "100%", marginBottom: "6px", padding: "8px" }} />

              <p style={{
                marginTop: "10px",
                fontWeight: "bold",
                color: totalAllocated() === Math.round(selected.totalPrice || 0) ? "#2e7d32" : "#c62828"
              }}>
                Allocated total: {formatCurrency(totalAllocated())} / Order total: {formatCurrency(selected.totalPrice)}
                {totalAllocated() !== Math.round(selected.totalPrice || 0) && " ⚠️ Doesn't add up to the order total"}
              </p>
            </div>

            <div className="payout-modal-footer">
              <button className="confirm-payout-btn" onClick={handleResolve} disabled={submitting}>
                {submitting ? "Resolving..." : "Resolve & Send Payments"}
              </button>
              <button className="cancel-action-btn" onClick={closeModal} disabled={submitting}>
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Disputes;
