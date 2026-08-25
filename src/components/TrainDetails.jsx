import { useEffect, useState } from 'react';
import { useParams, useNavigate, useLocation } from 'react-router-dom';
import {
  collection, doc, getDoc, getDocs, setDoc
} from 'firebase/firestore';
import { db } from '../firebase/config';
import './TrainDetails.css';

// Same Firestore paths as TrainRoutes.jsx / the Android app - see
// FirebaseSeeder.java for the exact shape written by the app:
//   RailwaySystem/main/Routes/{routeId} -> { stations: [{ name, index }, ...] }
// The Android side only ever reads the ARRAY ORDER of "stations" (not the
// "index" field's value) to work out which station comes before/after
// which - so what matters here is the order of the array, and "index" is
// just kept in sync alongside it for consistency with the seeder's format.

const railwayRoot = () => doc(db, 'RailwaySystem', 'main');
const trainsCollection = () => collection(railwayRoot(), 'Trains');
const stationsCollection = () => collection(railwayRoot(), 'Stations');
const routesCollection = () => collection(railwayRoot(), 'Routes');

const TrainDetails = () => {
  const { id } = useParams();
  const navigate = useNavigate();
  const location = useLocation();

  const [train, setTrain] = useState(location.state?.train || null);
  const [routeStations, setRouteStations] = useState([]);
  const [allStations, setAllStations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');

  const [selectedStationToAdd, setSelectedStationToAdd] = useState('');
  const [insertPosition, setInsertPosition] = useState('end');

  useEffect(() => {
    loadAll();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id]);

  const loadAll = async () => {
    setLoading(true);
    setError('');

    try {
      let trainData = location.state?.train || null;

      if (!trainData) {
        const trainSnap = await getDoc(doc(trainsCollection(), id));
        if (trainSnap.exists()) {
          trainData = { id: trainSnap.id, ...trainSnap.data() };
        }
      }

      setTrain(trainData);

      const stationsSnap = await getDocs(stationsCollection());
      setAllStations(stationsSnap.docs.map((d) => ({ name: d.id, ...d.data() })));

      if (trainData?.routeId) {
        const routeSnap = await getDoc(doc(routesCollection(), trainData.routeId));

        if (routeSnap.exists()) {
          const data = routeSnap.data();
          setRouteStations(Array.isArray(data.stations) ? data.stations : []);
        } else {
          setRouteStations([]);
        }
      } else {
        setRouteStations([]);
      }
    } catch (err) {
      console.error('Load route error:', err);
      setError('Could not load route data: ' + err.message);
    } finally {
      setLoading(false);
    }
  };

  const saveRoute = async (newStations) => {
    if (!train?.routeId) return;

    setSaving(true);
    setError('');

    // Re-sequence "index" to match the new array order on every save, so
    // the two never drift apart even though the app itself only reads
    // array order.
    const reindexed = newStations.map((s, i) => ({ name: s.name, index: i }));

    try {
      await setDoc(
        doc(routesCollection(), train.routeId),
        { stations: reindexed, isActive: true },
        { merge: true }
      );

      setRouteStations(reindexed);
    } catch (err) {
      console.error('Save route error:', err);
      setError('Could not save route: ' + err.message);
    } finally {
      setSaving(false);
    }
  };

  const handleAddStation = () => {
    if (!selectedStationToAdd) return;

    const newEntry = { name: selectedStationToAdd };
    let updated;

    if (insertPosition === 'start') {
      updated = [newEntry, ...routeStations];
    } else if (insertPosition === 'end') {
      updated = [...routeStations, newEntry];
    } else {
      // insertPosition holds the array index to insert AFTER
      const afterIndex = parseInt(insertPosition, 10);
      updated = [
        ...routeStations.slice(0, afterIndex + 1),
        newEntry,
        ...routeStations.slice(afterIndex + 1),
      ];
    }

    saveRoute(updated);
    setSelectedStationToAdd('');
    setInsertPosition('end');
  };

  const handleRemoveStation = (indexToRemove) => {
    if (!window.confirm('Remove this station from the route?')) return;

    const updated = routeStations.filter((_, i) => i !== indexToRemove);
    saveRoute(updated);
  };

  const handleMove = (index, direction) => {
    const newIndex = index + direction;
    if (newIndex < 0 || newIndex >= routeStations.length) return;

    const updated = [...routeStations];
    [updated[index], updated[newIndex]] = [updated[newIndex], updated[index]];
    saveRoute(updated);
  };

  if (loading) {
    return <div className="loading">Loading route…</div>;
  }

  if (!train) {
    return (
      <div className="train-details-wrapper">
        <button onClick={() => navigate('/train-routes')} className="back-btn">
          &larr; Back to Trains
        </button>
        <p>Train not found.</p>
      </div>
    );
  }

  // Stations already on the route shouldn't be offered again in the
  // "add station" dropdown.
  const usedNames = new Set(routeStations.map((s) => s.name));
  const availableStations = allStations.filter((s) => !usedNames.has(s.name));

  return (
    <div className="train-details-wrapper">
      <div className="train-details-header">
        <button onClick={() => navigate('/train-routes')} className="back-btn">
          &larr; Back to Trains
        </button>

        <div className="header-info">
          <div>
            <h1>
              {train.name} {train.number ? <span>({train.number})</span> : null}
            </h1>
            <p className="route-subtitle">Route ID: {train.routeId || 'N/A'}</p>
          </div>
        </div>
      </div>

      {error && <div className="error-banner">{error}</div>}

      <div className="timeline-card">
        <h2>Route Stations ({routeStations.length})</h2>
        <p>
          This is the exact order the train visits these stations - the app uses this
          order to work out which station is ahead of, or behind, the passenger.
        </p>

        {routeStations.length === 0 ? (
          <div className="no-stations">No stations yet - add the first one below.</div>
        ) : (
          <div className="stations-timeline">
            {routeStations.map((s, i) => (
              <div className="timeline-item" key={`${s.name}-${i}`}>
                <div className="timeline-marker">
                  <div className="marker-dot" />
                  {i < routeStations.length - 1 && <div className="marker-line" />}
                </div>

                <div className="timeline-content">
                  <div className="station-info">
                    <h3>{s.name}</h3>
                    <span className="time-badge dep">Stop {i + 1} of {routeStations.length}</span>
                  </div>

                  <div className="station-item-actions">
                    <button
                      className="btn-move"
                      disabled={i === 0 || saving}
                      onClick={() => handleMove(i, -1)}
                      title="Move earlier on the route"
                    >
                      ↑
                    </button>
                    <button
                      className="btn-move"
                      disabled={i === routeStations.length - 1 || saving}
                      onClick={() => handleMove(i, 1)}
                      title="Move later on the route"
                    >
                      ↓
                    </button>
                    <button
                      className="btn-icon-delete"
                      onClick={() => handleRemoveStation(i)}
                      disabled={saving}
                      title="Remove from route"
                    >
                      ✕
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <div className="timeline-card">
        <h2>Add a Station to This Route</h2>
        <p>Pick any existing station and where it should go - at the start, the end, or right after a specific stop.</p>

        <div className="form-row">
          <div className="form-group half">
            <label>Station</label>
            <select
              value={selectedStationToAdd}
              onChange={(e) => setSelectedStationToAdd(e.target.value)}
            >
              <option value="">Select a station…</option>
              {availableStations.map((s) => (
                <option key={s.name} value={s.name}>{s.name}</option>
              ))}
            </select>
          </div>

          <div className="form-group half">
            <label>Position</label>
            <select
              value={insertPosition}
              onChange={(e) => setInsertPosition(e.target.value)}
            >
              <option value="start">At the start</option>
              {routeStations.map((s, i) => (
                <option key={i} value={String(i)}>After {s.name}</option>
              ))}
              <option value="end">At the end</option>
            </select>
          </div>
        </div>

        {allStations.length === 0 && (
          <div className="no-stations">
            No stations exist yet - add some from the Stations tab on the previous screen first.
          </div>
        )}

        <button
          className="btn-primary"
          disabled={!selectedStationToAdd || saving}
          onClick={handleAddStation}
        >
          {saving ? 'Saving…' : '+ Add to Route'}
        </button>
      </div>
    </div>
  );
};

export default TrainDetails;
