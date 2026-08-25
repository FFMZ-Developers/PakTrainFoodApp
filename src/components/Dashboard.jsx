import { useState, useEffect } from 'react'; 
import { useNavigate } from 'react-router-dom';
import './Dashboard.css';
import Restaurant from './Restaurant';
import Riders from './Riders';
import Payments from './Payments';
import OrderReports from './OrderReports';
import Settings from './Settings';

// Firebase Firestore ke imports
import { db } from '../firebase/config'; 
import { collection, onSnapshot } from 'firebase/firestore';

const Dashboard = () => {
  const navigate = useNavigate();
  const [activeTab, setActiveTab] = useState('Dashboard');

  const menuItems = [
    { name: 'Dashboard', icon: 'M4 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2V6zM14 6a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2V6zM4 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2H6a2 2 0 01-2-2v-2zM14 16a2 2 0 012-2h2a2 2 0 012 2v2a2 2 0 01-2 2h-2a2 2 0 01-2-2v-2z' },
    { name: 'Restaurants', icon: 'M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6' },
    { name: 'Delivery Riders', icon: 'M12 19l9 2-9-18-9 18 9-2zm0 0v-8' },
    { name: 'Payments', icon: 'M3 10h18M7 15h1m4 0h1m-7 4h12a3 3 0 003-3V8a3 3 0 00-3-3H6a3 3 0 00-3 3v8a3 3 0 003 3z' },
    { name: 'Order Reports', icon: 'M9 17v-2m3 2v-4m3 4v-6m2 10H7a2 2 0 01-2-2V5a2 2 0 012-2h5.586a1 1 0 01.707.293l5.414 5.414a1 1 0 01.293.707V19a2 2 0 01-2 2z' },
    { name: 'Settings', icon: 'M10.325 4.317c.426-1.756 2.924-1.756 3.35 0a1.724 1.724 0 002.573 1.066c1.543-.94 3.31.826 2.37 2.37a1.724 1.724 0 001.065 2.572c1.756.426 1.756 2.924 0 3.35a1.724 1.724 0 00-1.066 2.573c.94 1.543-.826 3.31-2.37 2.37a1.724 1.724 0 00-2.572 1.065c-.426 1.756-2.924 1.756-3.35 0a1.724 1.724 0 00-2.573-1.066c-1.543.94-3.31-.826-2.37-2.37a1.724 1.724 0 00-1.065-2.572c-1.756-.426-1.756-2.924 0-3.35a1.724 1.724 0 001.066-2.573c-.94-1.543.826-3.31 2.37-2.37.996.608 2.296.07 2.572-1.065z M15 12a3 3 0 11-6 0 3 3 0 016 0z' },
  ];

  const handleLogout = () => {
    navigate('/');
  };

  const renderContent = () => {
    switch (activeTab) {
      case 'Dashboard':
        return <DashboardOverview />;
      case 'Restaurants':
        return <Restaurant />;
      case 'Delivery Riders':
        return <Riders />;
      case 'Payments':
        return <Payments />;
      case 'Order Reports':
        return <OrderReports />;
      case 'Settings':
        return <Settings />;
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
      {/* Sidebar */}
      <aside className="sidebar">
        <div className="sidebar-header">
          <h1>PakTrain</h1>
          <p>Logistics Admin</p>
        </div>

        <nav className="sidebar-nav">
          {menuItems.map((item) => (
            <button
              key={item.name}
              onClick={() => setActiveTab(item.name)}
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
            onClick={() => navigate('/train-routes')}
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
                <p className="profile-role">SUPER ADMIN</p>
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

const DashboardOverview = () => {
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

  useEffect(() => {
    // 1. Orders Real-time Listener (Main Root Collection)
    const orderCollectionRef = collection(db, 'Orders');
    const unsubscribeOrders = onSnapshot(orderCollectionRef, (snapshot) => {
      setOrderCount(snapshot.size);
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

  return (
    <div className="overview-container">
      {/* Header */}
      <div className="overview-header">
        <div className="header-text">
          <h2>Logistics Overview</h2>
          <p>Real-time performance metrics for the PakTrain network.</p>
        </div>
        <div className="header-actions">
          <button className="btn-secondary">Download Report</button>
          <button className="btn-primary">
            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 4v16m8-8H4" />
            </svg>
            New Distribution
          </button>
        </div>
      </div>

      {/* Metric Cards */}
      <div className="metrics-grid">
        <MetricCard 
          title="TOTAL ORDERS" 
          // Real-time main orders collection count
          value={loadingOrders ? "Loading..." : orderCount.toLocaleString()} 
          trend="+12.5%" 
          trendClass="trend-positive" 
          icon="M16 11V7a4 4 0 00-8 0v4M5 9h14l1 12H4L5 9z" 
          iconClass="icon-blue" 
        />
        <MetricCard 
          title="ACTIVE RESTAURANTS" 
          value={loadingRestaurants ? "Loading..." : restaurantCount.toLocaleString()} 
          trend="Stable" 
          trendClass="trend-neutral" 
          icon="M3 12l2-2m0 0l7-7 7 7M5 10v10a1 1 0 001 1h3m10-11l2 2m-2-2v10a1 1 0 01-1 1h-3m-6 0a1 1 0 001-1v-4a1 1 0 011-1h2a1 1 0 011 1v4a1 1 0 001 1m-6 0h6" 
          iconClass="icon-orange" 
        />
        <MetricCard 
          title="TOTAL PASSENGERS" 
          value={loadingPassengers ? "Loading..." : passengerCount.toLocaleString()} 
          trend="+8.4%" 
          trendClass="trend-positive" 
          icon="M16 7a4 4 0 11-8 0 4 4 0 018 0zm-8 8a6 6 0 00-6 6v1h12v-1a6 6 0 00-6-6z" 
          iconClass="icon-purple" 
        />
        <MetricCard 
          title="DELIVERY BOYS" 
          value={loadingRiders ? "Loading..." : riderCount.toLocaleString()} 
          trend="+48 New" 
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
              <h3>Weekly Order Trends</h3>
              <p>Fluctuations in delivery volume across all hubs</p>
            </div>
            <div className="chart-toggle">
              <button className="active">Week</button>
              <button>Month</button>
            </div>
          </div>
          <div className="bar-chart">
            {[40, 60, 100, 70, 90, 50, 60].map((h, i) => (
              <div key={i} className="bar-container group">
                <div 
                  className={`bar ${i === 2 ? 'active' : ''}`} 
                  style={{ height: `${h}%` }}
                ></div>
                <span className="bar-label">
                  {['MON','TUE','WED','THU','FRI','SAT','SUN'][i]}
                </span>
              </div>
            ))}
          </div>
        </div>

        <div className="chart-card map-card">
          <h3 className="chart-title">Live Distribution Map</h3>
          <p className="chart-subtitle">Current rider density in Lahore Hub</p>
          <div className="map-container">
            <div className="map-bg"></div>
            <div className="map-dot dot-1"></div>
            <div className="map-dot dot-2"></div>
            <div className="map-overlay">
              <div className="overlay-avatars">
                <div className="avatar-green">120</div>
                <div className="avatar-orange">14</div>
              </div>
              <span className="overlay-text">Active Riders & Hubs</span>
            </div>
          </div>
        </div>
      </div>

      {/* Recent Orders Table */}
      <div className="table-card">
        <div className="table-header">
          <div>
            <h3>Recent Orders</h3>
            <p>Latest transactions across the platform</p>
          </div>
          <button className="icon-button">
            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 5v.01M12 12v.01M12 19v.01M12 6a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2zm0 7a1 1 0 110-2 1 1 0 010 2z" />
            </svg>
          </button>
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
              <TableRow id="#ORD-9021" name="Ahmed Hassan" initials="AH" rest="Spice Village" status="In Transit" statusClass="status-blue" dotClass="dot-blue" amount="$45.50" />
              <TableRow id="#ORD-9022" name="Sara Khan" initials="SK" rest="The Burger Co." status="Delayed" statusClass="status-orange" dotClass="dot-orange" amount="$28.00" />
              <TableRow id="#ORD-9023" name="M. Zaid" initials="MZ" rest="Daily Deli Co." status="Delivered" statusClass="status-green" dotClass="dot-green" amount="$62.10" />
              <TableRow id="#ORD-9024" name="Fatima Ali" initials="FA" rest="Pasta Grill" status="Pending" statusClass="status-gray" dotClass="dot-gray" amount="$112.00" />
            </tbody>
          </table>
        </div>
        <div className="table-footer">
          <button className="view-all">View All History &rarr;</button>
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

const TableRow = ({ id, name, initials, rest, status, statusClass, dotClass, amount }) => (
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
      <button className="view-details">View Details</button>
    </td>
  </tr>
);

export default Dashboard;