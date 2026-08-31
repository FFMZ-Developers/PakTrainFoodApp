import { useEffect, useState, useMemo } from "react";
import { collection, onSnapshot, query, orderBy, limit, getDocs } from "firebase/firestore";
import { db } from "../firebase/config";
import "./OrderReports.css";
import "./Payments.css";

const money = (a) =>
  new Intl.NumberFormat("en-PK", { style: "currency", currency: "PKR", minimumFractionDigits: 0 })
    .format(Number(a || 0));

const when = (v) => {
  if (!v) return "\u2014";
  const ms = typeof v === "number" ? v : Date.parse(v);
  return isNaN(ms) ? String(v) : new Date(ms).toLocaleString();
};

const whenShort = (v) => {
  if (!v) return "\u2014";
  const ms = typeof v === "number" ? v : Date.parse(v);
  return isNaN(ms) ? String(v) : new Date(ms).toLocaleDateString();
};

const STATUS_LABEL = {
  Active: "Awaiting Restaurant",
  Accepted: "Preparing",
  ready_for_delivery: "Waiting for Rider",
  accepted_by_rider: "Rider Assigned",
  arrive_rider_at_resturent: "Rider Arrived",
  dropped: "Handed to Rider",
  pick_up: "On The Way",
  completed: "Delivered",
  Cancelled: "Cancelled",
  Rejected: "Rejected",
  delivery_failed: "Delivery Failed",
  disputed: "Under Review",
};

const BUCKET_OF = (status) => {
  if (status === "completed") return "Delivered";
  if (["Cancelled", "Rejected", "delivery_failed", "disputed"].includes(status)) return "Cancelled";
  if (!status || status === "Active") return "Pending";
  return "In Transit";
};

const BUCKET_CLASS = {
  Delivered: "status-delivered",
  "In Transit": "status-transit",
  Pending: "status-pending",
  Cancelled: "status-cancelled",
};

const initials = (name) => {
  if (!name) return "?";
  const parts = name.trim().split(/\s+/);
  return (parts[0][0] + (parts[1] ? parts[1][0] : "")).toUpperCase();
};

const AVATAR_COLOURS = [
  { bg: "#e0e7ff", fg: "#4f46e5" },
  { bg: "#fce7f3", fg: "#db2777" },
  { bg: "#d1fae5", fg: "#059669" },
  { bg: "#fef3c7", fg: "#d97706" },
];

const avatarFor = (name) => AVATAR_COLOURS[(name || "").length % AVATAR_COLOURS.length];

const PAGE_SIZE = 20;

/**
 * Module: consolidated Orders + Reports.
 *
 * This replaces two separate admin nav entries that both claimed to show
 * "orders" - one (formerly "Orders") was wired to real Firestore data but
 * plainly styled; the other (formerly "Order Reports") had a nicer visual
 * design but was pure mock data with hardcoded fake customers and dollar
 * amounts, never actually connected to anything. This keeps the nicer
 * design and wires it to the real data, and adds the filtering/report/
 * print capability that was asked for on top.
 */
const Orders = () => {
  const [orders, setOrders] = useState([]);
  const [loading, setLoading] = useState(true);

  const [searchTerm, setSearchTerm] = useState("");
  const [bucketFilter, setBucketFilter] = useState("All");
  const [cityFilter, setCityFilter] = useState("All");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [page, setPage] = useState(1);

  const [selected, setSelected] = useState(null);
  const [restaurantChat, setRestaurantChat] = useState([]);
  const [passengerChat, setPassengerChat] = useState([]);
  const [chatLoading, setChatLoading] = useState(false);

  useEffect(() => {
    const q = query(collection(db, "Orders"), orderBy("timestamp", "desc"), limit(500));

    const unsub = onSnapshot(
      q,
      (snap) => {
        setOrders(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
        setLoading(false);
      },
      (err) => {
        console.error("Orders listener failed, falling back:", err);
        onSnapshot(collection(db, "Orders"), (snap2) => {
          setOrders(snap2.docs.map((d) => ({ id: d.id, ...d.data() })));
          setLoading(false);
        });
      }
    );

    return () => unsub();
  }, []);

  const cities = useMemo(() => {
    const set = new Set();
    orders.forEach((o) => {
      if (o.restaurantCityNormalized) set.add(o.restaurantCityNormalized);
    });
    return Array.from(set).sort();
  }, [orders]);

  const filtered = useMemo(() => {
    const fromMs = dateFrom ? new Date(dateFrom + "T00:00:00").getTime() : null;
    const toMs = dateTo ? new Date(dateTo + "T23:59:59").getTime() : null;

    return orders.filter((o) => {
      const bucket = BUCKET_OF(o.orderStatus);

      const matchSearch =
        !searchTerm ||
        String(o.orderNumber || "").includes(searchTerm) ||
        (o.id || "").toLowerCase().includes(searchTerm.toLowerCase()) ||
        (o.passengerName || "").toLowerCase().includes(searchTerm.toLowerCase()) ||
        (o.restaurantName || "").toLowerCase().includes(searchTerm.toLowerCase());

      const matchBucket = bucketFilter === "All" || bucket === bucketFilter;
      const matchCity = cityFilter === "All" || o.restaurantCityNormalized === cityFilter;

      const placedMs = typeof o.timestamp === "number" ? o.timestamp : Date.parse(o.timestamp || "");
      const matchFrom = !fromMs || (placedMs && placedMs >= fromMs);
      const matchTo = !toMs || (placedMs && placedMs <= toMs);

      return matchSearch && matchBucket && matchCity && matchFrom && matchTo;
    });
  }, [orders, searchTerm, bucketFilter, cityFilter, dateFrom, dateTo]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const pageSafe = Math.min(page, totalPages);
  const pageItems = filtered.slice((pageSafe - 1) * PAGE_SIZE, pageSafe * PAGE_SIZE);

  const totalRevenue = orders
    .filter((o) => o.orderStatus === "completed")
    .reduce((sum, o) => sum + Number(o.totalPrice || 0), 0);

  const countBucket = (b) => orders.filter((o) => BUCKET_OF(o.orderStatus) === b).length;

  const orderRef = (o) =>
    o && typeof o.orderNumber === "number" && o.orderNumber > 0
      ? "#" + String(o.orderNumber).padStart(4, "0")
      : "#" + String(o?.id || "").slice(0, 6).toUpperCase();

  const openOrder = async (order) => {
    setSelected(order);
    setRestaurantChat([]);
    setPassengerChat([]);
    setChatLoading(true);

    try {
      const [rSnap, pSnap] = await Promise.all([
        getDocs(collection(db, "Orders", order.id, "chats_restaurant")),
        getDocs(collection(db, "Orders", order.id, "chats_passenger")),
      ]);

      const sortByTime = (docs) =>
        docs.map((d) => ({ id: d.id, ...d.data() })).sort((a, b) => (a.timestamp || 0) - (b.timestamp || 0));

      setRestaurantChat(sortByTime(rSnap.docs));
      setPassengerChat(sortByTime(pSnap.docs));
    } catch (e) {
      console.error("Couldn't load chats:", e);
    } finally {
      setChatLoading(false);
    }
  };

  const printReport = () => {
    const win = window.open("", "_blank", "width=900,height=700");
    if (!win) {
      alert("Please allow pop-ups to print the report.");
      return;
    }

    const rangeLabel =
      dateFrom || dateTo ? `${dateFrom || "start"} to ${dateTo || "today"}` : "All time";

    const rows = filtered
      .map(
        (o) => `
        <tr>
          <td>${orderRef(o)}</td>
          <td>${o.passengerName || "-"}</td>
          <td>${o.restaurantName || "-"}</td>
          <td>${o.riderName || "-"}</td>
          <td>${STATUS_LABEL[o.orderStatus] || o.orderStatus || "-"}</td>
          <td>${money(o.totalPrice)}</td>
          <td>${whenShort(o.timestamp)}</td>
        </tr>`
      )
      .join("");

    win.document.write(`
      <html>
        <head>
          <title>PakTrainFood - Order Report</title>
          <style>
            body { font-family: Arial, sans-serif; padding: 24px; color: #111; }
            h1 { font-size: 20px; margin-bottom: 2px; }
            p.meta { color: #555; margin-top: 0; margin-bottom: 20px; font-size: 13px; }
            table { width: 100%; border-collapse: collapse; font-size: 12px; }
            th, td { text-align: left; padding: 6px 8px; border-bottom: 1px solid #ddd; }
            th { background: #f4f4f4; }
            .summary { display: flex; gap: 24px; margin-bottom: 18px; font-size: 13px; }
            .summary b { display: block; font-size: 16px; }
          </style>
        </head>
        <body>
          <h1>PakTrainFood - Order Report</h1>
          <p class="meta">
            Range: ${rangeLabel} &nbsp;|&nbsp;
            City: ${cityFilter} &nbsp;|&nbsp;
            Status: ${bucketFilter} &nbsp;|&nbsp;
            Generated: ${new Date().toLocaleString()}
          </p>
          <div class="summary">
            <div>Total Orders<b>${filtered.length}</b></div>
            <div>Delivered<b>${filtered.filter((o) => o.orderStatus === "completed").length}</b></div>
            <div>Total Value<b>${money(filtered.reduce((s, o) => s + Number(o.totalPrice || 0), 0))}</b></div>
          </div>
          <table>
            <thead>
              <tr><th>Order</th><th>Passenger</th><th>Restaurant</th><th>Rider</th><th>Status</th><th>Amount</th><th>Date</th></tr>
            </thead>
            <tbody>${rows || '<tr><td colspan="7">No orders in this range.</td></tr>'}</tbody>
          </table>
          <script>window.onload = () => window.print();</script>
        </body>
      </html>
    `);
    win.document.close();
  };

  const renderChat = (messages, label) => (
    <div style={{ marginTop: "14px" }}>
      <h4 style={{ marginBottom: "6px" }}>{label}</h4>
      {messages.length === 0 ? (
        <p style={{ color: "#888", fontSize: "14px" }}>No messages in this thread.</p>
      ) : (
        <div style={{ maxHeight: "200px", overflowY: "auto", background: "#f7f7f7", borderRadius: "8px", padding: "10px" }}>
          {messages.map((m) => (
            <div key={m.id} style={{ marginBottom: "8px" }}>
              <div style={{ fontSize: "12px", fontWeight: "bold", color: "#00695c" }}>
                {m.senderName || "Unknown"}
                <span style={{ color: "#999", fontWeight: "normal", marginLeft: "8px" }}>{when(m.timestamp)}</span>
              </div>
              <div style={{ fontSize: "14px" }}>{m.text}</div>
            </div>
          ))}
        </div>
      )}
    </div>
  );

  return (
    <div className="reports-container animate-fade-in">
      <div className="reports-header">
        <div>
          <h2>Orders</h2>
          <p>Every order end-to-end - status, timeline, participants, and chat transcripts.</p>
        </div>
        <div className="header-actions">
          <button className="btn-primary" onClick={printReport}>
            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 17h2a2 2 0 002-2v-4a2 2 0 00-2-2H5a2 2 0 00-2 2v4a2 2 0 002 2h2m2 4h6a2 2 0 002-2v-4H7v4a2 2 0 002 2zm8-12V5a2 2 0 00-2-2H7a2 2 0 00-2 2v4h14z" />
            </svg>
            Print Report
          </button>
        </div>
      </div>

      <div className="report-metrics-grid">
        <SummaryCard title="Total Orders" value={orders.length} icon="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" iconColor="#4f46e5" bg="#e0e7ff" trend={`${filtered.length} match current filters`} trendPos={null} />
        <SummaryCard title="Delivered" value={countBucket("Delivered")} icon="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" iconColor="#059669" bg="#d1fae5" trend={`Revenue: ${money(totalRevenue)}`} trendPos={true} />
        <SummaryCard title="In Progress" value={countBucket("In Transit") + countBucket("Pending")} icon="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" iconColor="#d97706" bg="#fef3c7" trend="Live status" trendPos={null} />
        <SummaryCard title="Cancelled / Failed" value={countBucket("Cancelled")} icon="M10 14l2-2m0 0l2-2m-2 2l-2-2m2 2l2 2m7-2a9 9 0 11-18 0 9 9 0 0118 0z" iconColor="#dc2626" bg="#fee2e2" trend="Disputes + rejections" trendPos={null} />
      </div>

      <div className="reports-table-card">
        <div className="table-toolbar" style={{ flexWrap: "wrap", gap: "10px" }}>
          <div className="search-bar">
            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input
              type="text"
              placeholder="Search by order #, passenger, or restaurant..."
              value={searchTerm}
              onChange={(e) => { setSearchTerm(e.target.value); setPage(1); }}
            />
          </div>

          <div className="status-tabs">
            {["All", "Pending", "In Transit", "Delivered", "Cancelled"].map((s) => (
              <button
                key={s}
                onClick={() => { setBucketFilter(s); setPage(1); }}
                className={`tab-btn ${bucketFilter === s ? "active" : ""}`}
              >
                {s}
              </button>
            ))}
          </div>
        </div>

        <div className="table-toolbar" style={{ flexWrap: "wrap", gap: "10px", paddingTop: 0 }}>
          <select value={cityFilter} onChange={(e) => { setCityFilter(e.target.value); setPage(1); }} style={{ padding: "8px 12px", borderRadius: "6px", border: "1px solid #ccc" }}>
            <option value="All">All Cities</option>
            {cities.map((c) => (
              <option key={c} value={c}>{c}</option>
            ))}
          </select>

          <label style={{ fontSize: "13px", color: "#555" }}>
            From{" "}
            <input type="date" value={dateFrom} onChange={(e) => { setDateFrom(e.target.value); setPage(1); }} style={{ padding: "7px", borderRadius: "6px", border: "1px solid #ccc" }} />
          </label>

          <label style={{ fontSize: "13px", color: "#555" }}>
            To{" "}
            <input type="date" value={dateTo} onChange={(e) => { setDateTo(e.target.value); setPage(1); }} style={{ padding: "7px", borderRadius: "6px", border: "1px solid #ccc" }} />
          </label>

          {(dateFrom || dateTo || cityFilter !== "All") && (
            <button
              className="btn-secondary"
              onClick={() => { setDateFrom(""); setDateTo(""); setCityFilter("All"); setPage(1); }}
            >
              Clear filters
            </button>
          )}
        </div>

        <div className="table-responsive">
          <table className="orders-report-table">
            <thead>
              <tr>
                <th>ORDER</th>
                <th>PASSENGER</th>
                <th>RESTAURANT</th>
                <th>RIDER</th>
                <th>AMOUNT</th>
                <th>STATUS</th>
                <th>DATE</th>
                <th className="text-right">ACTIONS</th>
              </tr>
            </thead>
            <tbody>
              {loading ? (
                <tr><td colSpan={8} className="empty-row">Loading...</td></tr>
              ) : pageItems.length === 0 ? (
                <tr><td colSpan={8} className="empty-row">No orders match your filter.</td></tr>
              ) : (
                pageItems.map((o) => {
                  const bucket = BUCKET_OF(o.orderStatus);
                  const av = avatarFor(o.passengerName);
                  return (
                    <tr key={o.id}>
                      <td className="order-id">{orderRef(o)}</td>
                      <td>
                        <div className="flex-cell">
                          <div className="customer-avatar" style={{ backgroundColor: av.bg, color: av.fg }}>
                            {initials(o.passengerName)}
                          </div>
                          <span className="primary-text">{o.passengerName || "\u2014"}</span>
                        </div>
                      </td>
                      <td className="secondary-text">{o.restaurantName || "\u2014"}</td>
                      <td className="secondary-text">{o.riderName || "\u2014"}</td>
                      <td className="font-semibold">{money(o.totalPrice)}</td>
                      <td>
                        <span className={`order-badge ${BUCKET_CLASS[bucket]}`}>
                          <span className="dot"></span>
                          {STATUS_LABEL[o.orderStatus] || o.orderStatus || bucket}
                        </span>
                      </td>
                      <td className="secondary-text">{whenShort(o.timestamp)}</td>
                      <td className="text-right">
                        <button className="btn-view" onClick={() => openOrder(o)}>View</button>
                      </td>
                    </tr>
                  );
                })
              )}
            </tbody>
          </table>
        </div>

        <div className="table-footer-pagination">
          <span className="showing-text">
            Showing {pageItems.length ? (pageSafe - 1) * PAGE_SIZE + 1 : 0}-{(pageSafe - 1) * PAGE_SIZE + pageItems.length} of {filtered.length} orders
          </span>
          <div className="pagination-controls">
            <button className="page-btn text-btn" disabled={pageSafe <= 1} onClick={() => setPage(pageSafe - 1)}>Previous</button>
            <span className="page-btn active">{pageSafe}</span>
            <span className="page-dots">of {totalPages}</span>
            <button className="page-btn text-btn" disabled={pageSafe >= totalPages} onClick={() => setPage(pageSafe + 1)}>Next</button>
          </div>
        </div>
      </div>

      {selected && (
        <div className="payout-modal-overlay">
          <div className="payout-modal-sheet" style={{ maxWidth: "680px", maxHeight: "85vh", display: "flex", flexDirection: "column" }}>
            <h3 style={{ flexShrink: 0 }}>Order {orderRef(selected)}</h3>

            <div style={{ overflowY: "auto", flex: 1, paddingRight: "4px" }}>
              <div style={{ background: "#f7f7f7", padding: "12px", borderRadius: "8px", margin: "12px 0" }}>
                <p><b>Status:</b> {STATUS_LABEL[selected.orderStatus] || selected.orderStatus}</p>
                <p><b>Train:</b> {selected.trainName || "\u2014"} &nbsp;|&nbsp; <b>Station:</b> {selected.mealStation || "\u2014"}</p>
                <p><b>Coach/Seat:</b> {selected.coachNumber || "\u2014"} / {selected.seatNumber || "\u2014"}</p>
                <p><b>Passenger:</b> {selected.passengerName || "\u2014"} ({selected.phone || "no phone"})</p>
                <p><b>Restaurant:</b> {selected.restaurantName || "\u2014"} ({selected.restaurantPhone || "no phone"})</p>
                <p><b>Rider:</b> {selected.riderName || "Not assigned"} ({selected.riderPhone || "no phone"})</p>
                <p>
                  <b>Total:</b> {money(selected.totalPrice)} &nbsp;|&nbsp;
                  <b>Food:</b> {money(selected.subtotal)} &nbsp;|&nbsp;
                  <b>Delivery:</b> {money(selected.deliveryFee)}
                </p>
                <p><b>Payment:</b> {selected.paymentCaptured ? "Captured" : "Held / not captured"}</p>
              </div>

              <h4 style={{ marginBottom: "6px" }}>Timeline</h4>
              <table className="payments-table">
                <tbody>
                  <tr><td>Order placed</td><td>{when(selected.timestamp)}</td></tr>
                  <tr><td>Restaurant accepted</td><td>{when(selected.acceptedAt)}</td></tr>
                  <tr><td>Rider assigned</td><td>{when(selected.riderAssignedAt)}</td></tr>
                  <tr><td>Rider at restaurant</td><td>{when(selected.riderArrivedAt)}</td></tr>
                  <tr><td>Picked up</td><td>{when(selected.pickupConfirmedAt)}</td></tr>
                  <tr><td>Completed</td><td>{when(selected.completedAt)}</td></tr>
                </tbody>
              </table>

              {chatLoading ? (
                <p style={{ marginTop: "14px" }}>Loading conversations...</p>
              ) : (
                <>
                  {renderChat(restaurantChat, "Rider \u2194 Restaurant")}
                  {renderChat(passengerChat, "Rider \u2194 Passenger")}
                </>
              )}
            </div>

            <div className="payout-modal-footer" style={{ flexShrink: 0 }}>
              <button className="cancel-action-btn" onClick={() => setSelected(null)}>Close</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

const SummaryCard = ({ title, value, icon, iconColor, bg, trend, trendPos }) => (
  <div className="summary-card">
    <div className="summary-info">
      <p className="summary-title">{title}</p>
      <h4 className="summary-value">{value}</h4>
      <p className={`summary-trend ${trendPos === true ? "text-green" : trendPos === false ? "text-red" : "text-gray"}`}>
        {trend}
      </p>
    </div>
    <div className="summary-icon" style={{ backgroundColor: bg }}>
      <svg viewBox="0 0 24 24" fill="none" stroke={iconColor}>
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d={icon} />
      </svg>
    </div>
  </div>
);

export default Orders;
