import { useState, useEffect } from 'react'; 
import { useNavigate } from 'react-router-dom';
import './Dashboard.css';
import Restaurant from './Restaurant';
import Riders from './Riders';
import Payments from './Payments';
import Disputes from './Disputes';
import Orders from './Orders';
import LiveMap from './LiveMap';
import Passengers from './Passengers';
import Settings from './Settings';
import AdminManagement from './AdminManagement';

// Firebase Firestore ke imports
import { db } from '../firebase/config'; 
import { collection, onSnapshot } from 'firebase/firestore';

// Role-based access (multiple roles, each with its own allowed tabs)
import { getCurrentRole, isSuperAdmin, canAccessTab, roleLabel } from '../utils/permissions';

const Dashboard = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('Dashboard');

  // Mobile: sidebar is hidden off-canvas until this is toggled on.
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // When the mobile sidebar opens, push a throwaway history entry so the
  // phone's hardware/browser "back" button closes the sidebar first,
  // instead of leaving the dashboard page (which would land on Login).
  useEffect(() => {
    if (!sidebarOpen) return;

    window.history.pushState({ mobileSidebar: true }, '');

    const handlePopState = () => setSidebarOpen(false);
    window.addEventListener('popstate', handlePopState);
    return () => window.removeEventListener('popstate', handlePopState);
  }, [sidebarOpen]);

  // Use this (not setSidebarOpen(false) directly) whenever the sidebar is
  // closed by something OTHER than the back button - e.g. tapping a menu
  // item or the overlay - so the throwaway history entry above gets
  // cleaned up immediately instead of lingering for the next back press.
  const closeSidebar = () => {
    setSidebarOpen(false);
    if (window.history.state && window.history.state.mobileSidebar) {
      window.history.back();
    }
  };

  // Role stored by Login.jsx after sign-in (e.g. "super-admin", "manager",
  // "support", "finance" - see src/utils/permissions.js for the full list).
  const [role] = useState(getCurrentRole());
  const superAdmin = isSuperAdmin(role);

  const menuItems = [
    { name: 'Dashboard', icon: 'M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z' },
    { name: 'Restaurants', icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6' },
    { name: 'Delivery Riders', icon: 'M12 19l9 2-9-18-9 18 9-2zm0 0v-8' },
    { name: 'Payments', icon: 'M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z' },
    { name: 'Passengers', icon: 'M16 7a4 4 0 11-8 0 4 4 0 018 0zm-8 8a6 6 0 00-6 6v1h12v-1a6 6 0 00-6-6z' },
    { name: 'Orders', icon: 'M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z' },
    { name: 'Disputes', icon: 'M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z' },
    { name: 'Settings', icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z' },
    { name: 'Admin Management', icon: 'M16 7a4 4 0 11-8 0 4 4 0 018 0zM12 14a7 7 0 00-7 7h14a7 7 0 00-7-7zM19 8v4m2-2h-4' },
  ];

  // Only show sidebar entries this role is allowed to open.
  const visibleMenuItems = menuItems.filter((item) => canAccessTab(item.name, role));

  const handleLogout = () => {
    localStorage.removeItem('role');
    navigate('/');
  };

  const renderContent = () => {
    // Defensive check: even if activeTab somehow got set to a restricted
    // tab (e.g. it was the last tab open before the role changed), a
    // limited admin never sees the actual content.
    if (!canAccessTab(activeTab, role)) {
      return (
        <div className="placeholder-content">
          <h2>Access Restricted</h2>
          <p>You need super-admin privileges to view {activeTab}.</p>
        </div>
      );
    }

    switch (activeTab) {
      case 'Dashboard':
        return <DashboardOverview setActiveTab={setActiveTab} />;
      case 'Restaurants':
        return <Restaurant />;
      case 'Delivery Riders':
        return <Riders />;
      case 'Payments':
        return <Payments />;
      case 'Passengers':
        return <Passengers />;
      case 'Orders':
        return <Orders />;
      case 'Disputes':
        return <Disputes />;
      case 'Settings':
        return <Settings />;
      case 'Admin Management':
        return <AdminManagement />;
      default:
        return (
          <div className="placeholder-content">
            <h2>{activeTab} Management</h2>
          </div>
        );
    }
  };

  return (
    <div className="dashboard-wrapper">
      {/* Dark backdrop behind the sidebar on mobile - tapping it closes the menu */}
      {sidebarOpen && <div className="sidebar-overlay" onClick={closeSidebar}></div>}

      {/* Sidebar */}
      <aside className={`sidebar ${sidebarOpen ? 'sidebar-open' : ''}`}>
        <div className="sidebar-header">
          <h1>PakTrain</h1>
          <p>Logistics Admin</p>
        </div>

        <nav className="sidebar-nav">
          {visibleMenuItems.map((item) => (
            <button
              key={item.name}
              onClick={() => { setActiveTab(item.name); closeSidebar(); }}
              className={`nav-item ${activeTab === item.name ? 'active' : ''}`}
            >
              <svg fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d={item.icon} />
              </svg>
              {item.name}
            </button>
          ))}
          
          <div style={{ height: '1px', backgroundColor: '#e0e4f1', margin: '1rem 1.25rem' }}></div>
          <span style={{ display: 'block', padding: '0 1.25rem', fontSize: '0.75rem', fontWeight: 'bold', color: '#a3aed1', textTransform: 'uppercase', marginBottom: '0.5rem' }}>Other Routes</span>
          
          <button
            onClick={() => { closeSidebar(); navigate('/train-routes'); }}
            className="nav-item"
          >
            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
            Train Routes
          </button>
        </nav>

        <div className="sidebar-footer">
          <button onClick={handleLogout} className="logout-button">
            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M17 16l4-4m0 0l-4-4m4 4H7m6 4v1a3 3 0 01-3 3H6a3 3 0 01-3-3V7a3 3 0 013-3h4a3 3 0 013 3v1" />
            </svg>
            Logout
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <main className="main-area">
        {/* Top Navbar */}
        <header className="topbar">
          <button className="hamburger-button" onClick={() => setSidebarOpen(true)} aria-label="Open menu">
            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 6h16M4 12h16M4 18h16" />
            </svg>
          </button>

          <div className="search-container">
            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M21 21l-6-6m2-5a7 7 0 11-14 0 7 7 0 0114 0z" />
            </svg>
            <input type="text" placeholder="Search operational data..." />
          </div>

          <div className="user-section">
            <button className="icon-button">
              <svg fill="none" viewBox="0 0 24 24" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 17h5l-1.405-1.405A2.032 2.032 0 0118 14.158V11a6.002 6.002 0 00-4-5.659V5a2 2 0 10-4 0v.341C7.67 6.165 6 8.388 6 11v3.159c0 .538-.214 1.055-.595 1.436L4 17h5m6 0v1a3 3 0 11-6 0v-1m6 0H9" />
              </svg>
            </button>
            <div className="profile-container">
              <div className="profile-text">
                <p className="profile-name">Admin User</p>
                <p className="profile-role">{roleLabel(role)}</p>
              </div>
              <div className="profile-avatar">AU</div>
            </div>
          </div>
        </header>

        {/* Scrollable Content */}
        <div className="content-container">
          {renderContent()}
        </div>
      </main>
    </div>
  );
};

const DashboardOverview = ({ setActiveTab }) => {
  // Chaaron metrics ke liye states
  const [orderCount, setOrderCount] = useState(0);
  const [passengerCount, setPassengerCount] = useState(0);
  const [restaurantCount, setRestaurantCount] = useState(0);
  const [riderCount, setRiderCount] = useState(0);

  // Chaaron ki loading states
  const [loadingOrders, setLoadingOrders] = useState(true);
  const [loadingPassengers, setLoadingPassengers] = useState(true);
  const [loadingRestaurants, setLoadingRestaurants] = useState(true);
  const [loadingRiders, setLoadingRiders] = useState(true);

  // Module: real order data for the trend chart + recent-orders table -
  // both of these used to be entirely hardcoded mock numbers/rows.
  const [allOrders, setAllOrders] = useState([]);
  const [chartMode, setChartMode] = useState("week"); // "week" | "month"

  useEffect(() => {
    // 1. Orders Real-time Listener (Main Root Collection)
    const orderCollectionRef = collection(db, 'Orders');
    const unsubscribeOrders = onSnapshot(orderCollectionRef, (snapshot) => {
      setOrderCount(snapshot.size);
      setAllOrders(snapshot.docs.map((d) => ({ id: d.id, ...d.data() })));
      setLoadingOrders(false);
    }, (error) => {
      console.error("Orders fetch error: ", error);
      setLoadingOrders(false);
    });

    // 2. Passenger Real-time Listener
    const passengerCollectionRef = collection(db, 'Users', 'Passenger', 'Register');
    const unsubscribePassengers = onSnapshot(passengerCollectionRef, (snapshot) => {
      setPassengerCount(snapshot.size);
      setLoadingPassengers(false);
    }, (error) => {
      console.error("Passenger fetch error: ", error);
      setLoadingPassengers(false);
    });

    // 3. Restaurant Real-time Listener
    const restaurantCollectionRef = collection(db, 'Users', 'Restaurant', 'VerifiedRegister');
    const unsubscribeRestaurants = onSnapshot(restaurantCollectionRef, (snapshot) => {
      setRestaurantCount(snapshot.size);
      setLoadingRestaurants(false);
    }, (error) => {
      console.error("Restaurant fetch error: ", error);
      setLoadingRestaurants(false);
    });

    // 4. Delivery Riders Real-time Listener
    const riderCollectionRef = collection(db, 'Users', 'Delivery', 'VerifiedRegister');
    const unsubscribeRiders = onSnapshot(riderCollectionRef, (snapshot) => {
      setRiderCount(snapshot.size);
      setLoadingRiders(false);
    }, (error) => {
      console.error("Rider fetch error: ", error);
      setLoadingRiders(false);
    });

    // Clean up all listeners
    return () => {
      unsubscribeOrders();
      unsubscribePassengers();
      unsubscribeRestaurants();
      unsubscribeRiders();
    };
  }, []);

  // ---- Real weekly / monthly order-volume bars ----
  const orderPlacedMs = (o) =>
    typeof o.timestamp === "number" ? o.timestamp : Date.parse(o.timestamp || "");

  const weeklyBars = () => {
    const days = ["SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT"];
    const counts = new Array(7).fill(0);
    const now = new Date();
    const weekAgo = now.getTime() - 7 * 24 * 60 * 60 * 1000;

    allOrders.forEach((o) => {
      const ms = orderPlacedMs(o);
      if (!ms || ms < weekAgo) return;
      counts[new Date(ms).getDay()] += 1;
    });

    const max = Math.max(1, ...counts);
    // Re-order so it reads Mon -> Sun rather than starting on whatever
    // getDay() index today happens to be.
    const order = [1, 2, 3, 4, 5, 6, 0];
    return order.map((i) => ({ label: days[i], value: counts[i], pct: (counts[i] / max) * 100 }));
  };

  const monthlyBars = () => {
    const now = new Date();
    const months = [];
    for (let i = 5; i >= 0; i--) {
      const d = new Date(now.getFullYear(), now.getMonth() - i, 1);
      months.push({ label: d.toLocaleString("default", { month: "short" }), year: d.getFullYear(), month: d.getMonth(), value: 0 });
    }

    allOrders.forEach((o) => {
      const ms = orderPlacedMs(o);
      if (!ms) return;
      const d = new Date(ms);
      const bucket = months.find((m) => m.year === d.getFullYear() && m.month === d.getMonth());
      if (bucket) bucket.value += 1;
    });

    const max = Math.max(1, ...months.map((m) => m.value));
    return months.map((m) => ({ label: m.label, value: m.value, pct: (m.value / max) * 100 }));
  };

  const bars = chartMode === "week" ? weeklyBars() : monthlyBars();

  // ---- Real 5 most recent orders ----
  const recentOrders = [...allOrders]
    .sort((a, b) => (orderPlacedMs(b) || 0) - (orderPlacedMs(a) || 0))
    .slice(0, 5);

  const orderRefOf = (o) =>
    o && typeof o.orderNumber === "number" && o.orderNumber > 0
      ? "#" + String(o.orderNumber).padStart(4, "0")
      : "#" + String(o?.id || "").slice(0, 6).toUpperCase();

  const STATUS_DISPLAY = {
    completed: { label: "Delivered", cls: "status-green", dot: "dot-green" },
    pick_up: { label: "On The Way", cls: "status-blue", dot: "dot-blue" },
    dropped: { label: "Handed to Rider", cls: "status-blue", dot: "dot-blue" },
    accepted_by_rider: { label: "Rider Assigned", cls: "status-blue", dot: "dot-blue" },
    arrive_rider_at_resturent: { label: "Rider Arrived", cls: "status-blue", dot: "dot-blue" },
    ready_for_delivery: { label: "Waiting for Rider", cls: "status-orange", dot: "dot-orange" },
    Accepted: { label: "Preparing", cls: "status-orange", dot: "dot-orange" },
    Active: { label: "Pending", cls: "status-gray", dot: "dot-gray" },
    Cancelled: { label: "Cancelled", cls: "status-gray", dot: "dot-gray" },
    Rejected: { label: "Rejected", cls: "status-gray", dot: "dot-gray" },
    delivery_failed: { label: "Failed", cls: "status-gray", dot: "dot-gray" },
    disputed: { label: "Disputed", cls: "status-orange", dot: "dot-orange" },
  };

  const money = (a) =>
    new Intl.NumberFormat("en-PK", { style: "currency", currency: "PKR", minimumFractionDigits: 0 }).format(Number(a || 0));

  const initialsOf = (name) => {
    if (!name) return "?";
    const parts = name.trim().split(/\s+/);
    return (parts[0][0] + (parts[1] ? parts[1][0] : "")).toUpperCase();
  };

  return (
    <div className="overview-container">
      {/* Header */}
      <div className="overview-header">
        <div className="header-text">
          <h2>Logistics Overview</h2>
          <p>Real-time performance metrics for the PakTrain network.</p>
        </div>
        <div className="header-actions">
          <button className="btn-primary" onClick={() => setActiveTab && setActiveTab('Orders')}>
            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
            </svg>
            View All Orders
          </button>
        </div>
      </div>

      {/* Metric Cards */}
      <div className="metrics-grid">
        <MetricCard
          title="TOTAL ORDERS"
          value={loadingOrders ? "Loading..." : orderCount.toLocaleString()}
          trend={`${allOrders.filter(o => o.orderStatus === 'completed').length} delivered`}
          trendClass="trend-positive"
          icon="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z"
          iconClass="icon-blue"
        />
        <MetricCard
          title="ACTIVE RESTAURANTS"
          value={loadingRestaurants ? "Loading..." : restaurantCount.toLocaleString()}
          trend="Verified & live"
          trendClass="trend-neutral"
          icon="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6"
          iconClass="icon-orange"
        />
        <MetricCard
          title="TOTAL PASSENGERS"
          value={loadingPassengers ? "Loading..." : passengerCount.toLocaleString()}
          trend="Registered users"
          trendClass="trend-positive"
          icon="M16 7a4 4 0 11-8 0 4 4 0 018 0zm-8 8a6 6 0 00-6 6v1h12v-1a6 6 0 00-6-6z"
          iconClass="icon-purple"
        />
        <MetricCard
          title="DELIVERY RIDERS"
          value={loadingRiders ? "Loading..." : riderCount.toLocaleString()}
          trend="Verified & live"
          trendClass="trend-positive"
          icon="M12 19l9 2-9-18-9 18 9-2zm0 0v-8"
          iconClass="icon-green"
        />
      </div>

      {/* Charts Section */}
      <div className="charts-grid">
        <div className="chart-card bar-chart-card">
          <div className="chart-header">
            <div>
              <h3>Order Trends</h3>
              <p>{chartMode === 'week' ? 'Orders placed each day, last 7 days' : 'Orders placed each month, last 6 months'}</p>
            </div>
            <div className="chart-toggle">
              <button className={chartMode === 'week' ? 'active' : ''} onClick={() => setChartMode('week')}>Week</button>
              <button className={chartMode === 'month' ? 'active' : ''} onClick={() => setChartMode('month')}>Month</button>
            </div>
          </div>
          <div className="bar-chart">
            {bars.map((b, i) => (
              <div key={i} className="bar-container group" title={`${b.label}: ${b.value} order(s)`}>
                <div
                  className={`bar ${b.value > 0 && b.pct === 100 ? 'active' : ''}`}
                  style={{ height: `${Math.max(4, b.pct)}%` }}
                ></div>
                <span className="bar-label">{b.label}</span>
              </div>
            ))}
          </div>
        </div>

        <LiveMap />
      </div>

      {/* Recent Orders Table */}
      <div className="table-card">
        <div className="table-header">
          <div>
            <h3>Recent Orders</h3>
            <p>Latest transactions across the platform</p>
          </div>
        </div>
        <div className="table-responsive">
          <table className="orders-table">
            <thead>
              <tr>
                <th>Order ID</th>
                <th>Customer</th>
                <th>Restaurant</th>
                <th>Status</th>
                <th>Amount</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {recentOrders.length === 0 ? (
                <tr><td colSpan={6} style={{ textAlign: 'center', padding: '20px', color: '#888' }}>No orders yet.</td></tr>
              ) : (
                recentOrders.map((o) => {
                  const st = STATUS_DISPLAY[o.orderStatus] || { label: o.orderStatus || 'Unknown', cls: 'status-gray', dot: 'dot-gray' };
                  return (
                    <TableRow
                      key={o.id}
                      id={orderRefOf(o)}
                      name={o.passengerName || '\u2014'}
                      initials={initialsOf(o.passengerName)}
                      rest={o.restaurantName || '\u2014'}
                      status={st.label}
                      statusClass={st.cls}
                      dotClass={st.dot}
                      amount={money(o.totalPrice)}
                      onView={() => setActiveTab && setActiveTab('Orders')}
                    />
                  );
                })
              )}
            </tbody>
          </table>
        </div>
        <div className="table-footer">
          <button className="view-all" onClick={() => setActiveTab && setActiveTab('Orders')}>View All History &rarr;</button>
        </div>
      </div>
    </div>
  );
};

const MetricCard = ({ title, value, trend, trendClass, icon, iconClass }) => (
  <div className="metric-card">
    <div className="metric-header">
      <div className={`metric-icon-wrapper ${iconClass}`}>
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" className="metric-icon">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d={icon} />
        </svg>
      </div>
      <span className={`metric-trend ${trendClass}`}>{trend}</span>
    </div>
    <div className="metric-body">
      <p className="metric-title">{title}</p>
      <h4 className="metric-value">{value}</h4>
    </div>
  </div>
);

const TableRow = ({ id, name, initials, rest, status, statusClass, dotClass, amount, onView }) => (
  <tr>
    <td className="font-medium text-dark">{id}</td>
    <td>
      <div className="customer-cell">
        <div className="customer-initials">{initials}</div>
        <span className="customer-name">{name}</span>
      </div>
    </td>
    <td>{rest}</td>
    <td>
      <span className={`status-badge ${statusClass}`}>
        <span className={`status-dot ${dotClass}`}></span>
        {status}
      </span>
    </td>
    <td className="font-semibold text-dark">{amount}</td>
    <td className="text-right">
      <button className="view-details" onClick={onView}>View Details</button>
    </td>
  </tr>
);

export default Dashboard;