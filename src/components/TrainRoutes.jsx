import { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import {
  collection, doc, getDocs, addDoc, deleteDoc, setDoc
} from 'firebase/firestore';
import { db } from '../firebase/config';
import './TrainRoutes.css';

// Everything here reads/writes the exact same Firestore paths the Android
// app itself reads from (see HomeFragment.java's loadAllData()):
//   RailwaySystem/main/Stations/{stationName}   -> { lat, lng }
//   RailwaySystem/main/Trains/{autoId}          -> { name, number, routeId }
//   RailwaySystem/main/Routes/{routeId}         -> { stations: [{ name }, ...] }
// This admin screen was previously backed entirely by localStorage and
// never touched Firebase at all, which is why nothing showed up here.

const railwayRoot = () => doc(db, 'RailwaySystem', 'main');
const trainsCollection = () => collection(railwayRoot(), 'Trains');
const stationsCollection = () => collection(railwayRoot(), 'Stations');
const routesCollection = () => collection(railwayRoot(), 'Routes');

const TrainRoutes = () => {
  const navigate = useNavigate();

  const [activeTab, setActiveTab] = useState('Trains');

  const [trains, setTrains] = useState([]);
  const [stations, setStations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const [showTrainModal, setShowTrainModal] = useState(false);
  const [newTrain, setNewTrain] = useState({ name: '', number: '' });

  const [showStationModal, setShowStationModal] = useState(false);
  const [editingStation, setEditingStation] = useState(null);
  const [stationForm, setStationForm] = useState({ name: '', lat: '', lng: '' });

  useEffect(() => {
    loadAll();
  }, []);

  const loadAll = async () => {
    setLoading(true);
    setError('');
    try {
      const [trainsSnap, stationsSnap] = await Promise.all([
        getDocs(trainsCollection()),
        getDocs(stationsCollection()),
      ]);

      setTrains(trainsSnap.docs.map((d) => ({ id: d.id, ...d.data() })));
      setStations(stationsSnap.docs.map((d) => ({ id: d.id, name: d.id, ...d.data() })));
    } catch (err) {
      console.error('Load railway data error:', err);
      setError('Could not load train/station data: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  // =========================================================
  // TRAINS
  // =========================================================

  const handleAddTrain = async (e) => {
    e.preventDefault();

    if (!newTrain.name.trim()) return;

    try {
      // A train and its route are 1:1 here - creating a train also creates
      // an empty route document for it, whose stations are then managed
      // from the "Manage Route" screen (TrainDetails).
      const routeRef = await addDoc(routesCollection(), { stations: [] });

      await addDoc(trainsCollection(), {
        name: newTrain.name.trim(),
        number: newTrain.number.trim(),
        routeId: routeRef.id,
      });

      setNewTrain({ name: '', number: '' });
      setShowTrainModal(false);
      loadAll();
    } catch (err) {
      console.error('Add train error:', err);
      setError('Could not add train: ' + err.message);
    }
  };

  const handleDeleteTrain = async (train) => {
    if (!window.confirm(`Delete "${train.name}"? This also removes its route.`)) return;

    try {
      await deleteDoc(doc(trainsCollection(), train.id));

      if (train.routeId) {
        await deleteDoc(doc(routesCollection(), train.routeId));
      }

      loadAll();
    } catch (err) {
      console.error('Delete train error:', err);
      setError('Could not delete train: ' + err.message);
    }
  };

  // =========================================================
  // STATIONS
  // =========================================================

  const openAddStation = () => {
    setEditingStation(null);
    setStationForm({ name: '', lat: '', lng: '' });
    setShowStationModal(true);
  };

  const openEditStation = (station) => {
    setEditingStation(station.name);
    setStationForm({ name: station.name, lat: String(station.lat ?? ''), lng: String(station.lng ?? '') });
    setShowStationModal(true);
  };

  const handleSaveStation = async (e) => {
    e.preventDefault();

    const name = stationForm.name.trim();
    const lat = parseFloat(stationForm.lat);
    const lng = parseFloat(stationForm.lng);

    if (!name || Number.isNaN(lat) || Number.isNaN(lng)) {
      setError('Please enter a station name and valid latitude/longitude.');
      return;
    }

    try {
      // The station name IS the Firestore document ID (the Android app
      // reads doc.getId() as the station name), so renaming means
      // deleting the old doc and creating a new one.
      if (editingStation && editingStation !== name) {
        await deleteDoc(doc(stationsCollection(), editingStation));
      }

      await setDoc(doc(stationsCollection(), name), { lat, lng });

      setShowStationModal(false);
      loadAll();
    } catch (err) {
      console.error('Save station error:', err);
      setError('Could not save station: ' + err.message);
    }
  };

  const handleDeleteStation = async (station) => {
    if (!window.confirm(`Delete station "${station.name}"? Any route currently using it should be updated too.`)) return;

    try {
      await deleteDoc(doc(stationsCollection(), station.name));
      loadAll();
    } catch (err) {
      console.error('Delete station error:', err);
      setError('Could not delete station: ' + err.message);
    }
  };

  if (loading) {
    return <div className="loading">Loading train and station data…</div>;
  }

  return (
    <div className="train-routes-wrapper">
      <header className="train-routes-header">
        <button onClick={() => navigate('/dashboard')} className="back-btn">
          &larr; Back to Dashboard
        </button>
        <div className="header-actions">
          <h1>Trains &amp; Stations</h1>
          {activeTab === 'Trains' ? (
            <button className="btn-primary" onClick={() => setShowTrainModal(true)}>
              + Add New Train
            </button>
          ) : (
            <button className="btn-primary" onClick={openAddStation}>
              + Add New Station
            </button>
          )}
        </div>
      </header>

      <div className="tab-buttons-group" style={{ margin: '0 2rem' }}>
        <button
          className={`btn-tab ${activeTab === 'Trains' ? 'btn-tab-active' : ''}`}
          onClick={() => setActiveTab('Trains')}
        >
          🚆 Trains <span className="tab-badge-count">{trains.length}</span>
        </button>
        <button
          className={`btn-tab ${activeTab === 'Stations' ? 'btn-tab-active' : ''}`}
          onClick={() => setActiveTab('Stations')}
        >
          📍 Stations <span className="tab-badge-count">{stations.length}</span>
        </button>
      </div>

      {error && <div className="error-banner" style={{ margin: '1rem 2rem' }}>{error}</div>}

      <main className="train-routes-content">
        {activeTab === 'Trains' ? (
          <div className="card">
            <h2>Registered Trains</h2>
            <p>Trains read directly from Firestore (RailwaySystem/main/Trains) - the same data the app reads.</p>
            <div className="table-responsive">
              <table className="routes-table">
                <thead>
                  <tr>
                    <th>Train Name</th>
                    <th>Train Number</th>
                    <th>Route ID</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {trains.map((train) => (
                    <tr key={train.id}>
                      <td className="font-medium text-dark">{train.name}</td>
                      <td>{train.number || 'N/A'}</td>
                      <td>{train.routeId || 'N/A'}</td>
                      <td>
                        <div style={{ display: 'flex', gap: '0.5rem' }}>
                          <button
                            className="btn-secondary"
                            style={{ padding: '0.4rem 0.8rem', fontSize: '0.8rem' }}
                            onClick={() => navigate(`/train-details/${train.id}`, { state: { train } })}
                          >
                            Manage Route
                          </button>
                          <button
                            className="btn-icon-delete"
                            onClick={() => handleDeleteTrain(train)}
                            style={{ border: 'none', background: 'transparent', color: '#ff4d4d', cursor: 'pointer' }}
                            title="Delete Train"
                          >
                            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" width="18" height="18">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {trains.length === 0 && (
                    <tr>
                      <td colSpan="4" style={{ textAlign: 'center', padding: '2rem' }}>No trains added yet.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        ) : (
          <div className="card">
            <h2>Stations</h2>
            <p>Stations read directly from Firestore (RailwaySystem/main/Stations) - used for the app's nearest-station detection.</p>
            <div className="table-responsive">
              <table className="routes-table">
                <thead>
                  <tr>
                    <th>Station Name</th>
                    <th>Latitude</th>
                    <th>Longitude</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {stations.map((station) => (
                    <tr key={station.id}>
                      <td className="font-medium text-dark">{station.name}</td>
                      <td>{station.lat}</td>
                      <td>{station.lng}</td>
                      <td>
                        <div style={{ display: 'flex', gap: '0.5rem' }}>
                          <button
                            className="btn-secondary"
                            style={{ padding: '0.4rem 0.8rem', fontSize: '0.8rem' }}
                            onClick={() => openEditStation(station)}
                          >
                            Edit
                          </button>
                          <button
                            className="btn-icon-delete"
                            onClick={() => handleDeleteStation(station)}
                            style={{ border: 'none', background: 'transparent', color: '#ff4d4d', cursor: 'pointer' }}
                            title="Delete Station"
                          >
                            <svg fill="none" viewBox="0 0 24 24" stroke="currentColor" width="18" height="18">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                  {stations.length === 0 && (
                    <tr>
                      <td colSpan="4" style={{ textAlign: 'center', padding: '2rem' }}>No stations added yet.</td>
                    </tr>
                  )}
                </tbody>
              </table>
            </div>
          </div>
        )}
      </main>

      {/* Add Train Modal */}
      {showTrainModal && (
        <div className="modal-overlay">
          <div className="modal-content">
            <h2>Add New Train</h2>
            <form onSubmit={handleAddTrain}>
              <div className="form-group">
                <label>Train Name</label>
                <input
                  type="text"
                  placeholder="e.g., Green Line Express"
                  value={newTrain.name}
                  onChange={(e) => setNewTrain({ ...newTrain, name: e.target.value })}
                  required
                />
              </div>
              <div className="form-group">
                <label>Train Number</label>
                <input
                  type="text"
                  placeholder="e.g., 39UP"
                  value={newTrain.number}
                  onChange={(e) => setNewTrain({ ...newTrain, number: e.target.value })}
                />
              </div>
              <p style={{ fontSize: '0.8rem', color: '#6b7280' }}>
                An empty route is created automatically - add its stations from "Manage Route" after saving.
              </p>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowTrainModal(false)}>Cancel</button>
                <button type="submit" className="btn-primary">Save Train</button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Add/Edit Station Modal */}
      {showStationModal && (
        <div className="modal-overlay">
          <div className="modal-content">
            <h2>{editingStation ? 'Edit Station' : 'Add New Station'}</h2>
            <form onSubmit={handleSaveStation}>
              <div className="form-group">
                <label>Station Name</label>
                <input
                  type="text"
                  placeholder="e.g., Lahore Junction"
                  value={stationForm.name}
                  onChange={(e) => setStationForm({ ...stationForm, name: e.target.value })}
                  required
                />
              </div>
              <div className="form-row" style={{ display: 'flex', gap: '1rem' }}>
                <div className="form-group half">
                  <label>Latitude</label>
                  <input
                    type="number"
                    step="any"
                    placeholder="e.g., 31.5497"
                    value={stationForm.lat}
                    onChange={(e) => setStationForm({ ...stationForm, lat: e.target.value })}
                    required
                  />
                </div>
                <div className="form-group half">
                  <label>Longitude</label>
                  <input
                    type="number"
                    step="any"
                    placeholder="e.g., 74.3436"
                    value={stationForm.lng}
                    onChange={(e) => setStationForm({ ...stationForm, lng: e.target.value })}
                    required
                  />
                </div>
              </div>
              <div className="modal-actions">
                <button type="button" className="btn-secondary" onClick={() => setShowStationModal(false)}>Cancel</button>
                <button type="submit" className="btn-primary">Save Station</button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};

export default TrainRoutes;
