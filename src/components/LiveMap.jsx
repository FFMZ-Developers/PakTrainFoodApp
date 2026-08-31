import { useEffect, useRef, useState, useCallback } from "react";
import { collection, onSnapshot, query, where } from "firebase/firestore";
import { ref, onValue } from "firebase/database";
import { db, rtdb } from "../firebase/config";
import "./LiveMap.css";

// Same key the Android app uses for Maps SDK - needs "Maps JavaScript API"
// enabled too (that's a separate toggle in Google Cloud Console from
// "Maps SDK for Android", even though it's the same key).
const MAPS_API_KEY = "AIzaSyCmRd6o2p8AOTeoKJHb5DqHm5ih8fzWKRg";

const ACTIVE_STATUSES = [
  "Active", "Accepted", "ready_for_delivery",
  "accepted_by_rider", "arrive_rider_at_resturent", "dropped", "pick_up"
];

let mapsLoadPromise = null;

/** Loads the Google Maps JS SDK once, however many times this is called. */
function loadGoogleMaps() {
  if (window.google && window.google.maps) return Promise.resolve();
  if (mapsLoadPromise) return mapsLoadPromise;

  mapsLoadPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = `https://maps.googleapis.com/maps/api/js?key=${MAPS_API_KEY}`;
    script.async = true;
    script.onload = resolve;
    script.onerror = () => reject(new Error("Failed to load Google Maps"));
    document.head.appendChild(script);
  });

  return mapsLoadPromise;
}

const MARKER_COLOURS = {
  restaurant: "#E64A19",
  rider: "#1565C0",
  passenger: "#2E7D32",
};

/**
 * Module: live operations map.
 *
 * Shows every currently-relevant position at once:
 *   - every online rider (whether or not they're on an order right now)
 *   - every restaurant that has a registered location
 *   - every passenger with an order currently in a trackable stage
 *
 * For any order that has BOTH a rider assigned AND is still active, the
 * restaurant / rider / passenger for that one order are joined with a
 * line, so it's visually obvious which three dots belong together -
 * exactly the "who's serving who" view that's otherwise impossible to
 * get from a flat list of orders.
 *
 * Role and city filters apply to what's DRAWN, not what's fetched - all
 * the underlying listeners stay live regardless, so toggling a filter is
 * instant with no extra reads.
 */
const LiveMap = () => {
  const mapDivRef = useRef(null);
  const mapRef = useRef(null);
  const markersRef = useRef([]);
  const linesRef = useRef([]);

  const [mapsReady, setMapsReady] = useState(false);
  const [mapsError, setMapsError] = useState(null);

  const [restaurants, setRestaurants] = useState({});   // uid -> {name, lat, lng, city}
  const [riderProfiles, setRiderProfiles] = useState({}); // uid -> {name, city}
  const [riderPositions, setRiderPositions] = useState({}); // uid -> {lat, lng, online}
  const [activeOrders, setActiveOrders] = useState([]);   // [{id, restaurantId, acceptedBy, ...}]
  const [passengerPositions, setPassengerPositions] = useState({}); // orderId -> {lat, lng}

  const [roleFilter, setRoleFilter] = useState({ restaurant: true, rider: true, passenger: true });
  const [cityFilter, setCityFilter] = useState("All");
  const [expanded, setExpanded] = useState(false);

  // ---- Load the SDK once ----
  useEffect(() => {
    loadGoogleMaps()
      .then(() => setMapsReady(true))
      .catch((e) => setMapsError(e.message));
  }, []);

  // ---- Create the map instance once the SDK + div are both ready ----
  useEffect(() => {
    if (!mapsReady || !mapDivRef.current || mapRef.current) return;

    mapRef.current = new window.google.maps.Map(mapDivRef.current, {
      center: { lat: 30.3753, lng: 69.3451 }, // Pakistan, roughly centred
      zoom: 6,
      streetViewControl: false,
      mapTypeControl: false,
    });
  }, [mapsReady]);

  // ---- Restaurants (Firestore, live) ----
  useEffect(() => {
    const unsub = onSnapshot(collection(db, "Users", "Restaurant", "VerifiedRegister"), (snap) => {
      const map = {};
      snap.forEach((d) => {
        const r = d.data();
        if (typeof r.restaurantLat === "number" && typeof r.restaurantLng === "number") {
          map[d.id] = {
            name: r.restaurantName || "Restaurant",
            lat: r.restaurantLat,
            lng: r.restaurantLng,
            city: r.cityNormalized || "",
          };
        }
      });
      setRestaurants(map);
    });
    return () => unsub();
  }, []);

  // ---- Rider profiles (Firestore, live) - name + registered city ----
  useEffect(() => {
    const unsub = onSnapshot(collection(db, "Users", "Delivery", "VerifiedRegister"), (snap) => {
      const map = {};
      snap.forEach((d) => {
        const r = d.data();
        map[d.id] = { name: r.name || "Rider", city: r.cityNormalized || "" };
      });
      setRiderProfiles(map);
    });
    return () => unsub();
  }, []);

  // ---- Rider live positions (Realtime Database, live) ----
  useEffect(() => {
    const unsub = onValue(ref(rtdb, "DeliveryRiders"), (snap) => {
      const map = {};
      snap.forEach((child) => {
        const r = child.val();
        if (r && r.online && typeof r.lat === "number" && typeof r.lng === "number") {
          map[child.key] = { lat: r.lat, lng: r.lng };
        }
      });
      setRiderPositions(map);
    });
    return () => unsub();
  }, []);

  // ---- Active orders (Firestore, live) ----
  useEffect(() => {
    const q = query(collection(db, "Orders"), where("orderStatus", "in", ACTIVE_STATUSES));
    const unsub = onSnapshot(q, (snap) => {
      setActiveOrders(snap.docs.map((d) => ({ id: d.id, ...d.data() })));
    }, (err) => {
      // "in" queries need an index on orderStatus for large datasets, but
      // this is a single-field filter so it should always work without
      // one - logged in case that assumption is ever wrong.
      console.error("LiveMap active-orders listener failed:", err);
    });
    return () => unsub();
  }, []);

  // ---- Passenger positions - one listener per active order that has a
  //      rider assigned (matches when the Android app is actually
  //      writing to this path) ----
  const trackableOrderIds = activeOrders
    .filter((o) => o.acceptedBy && ["accepted_by_rider", "arrive_rider_at_resturent", "dropped", "pick_up"].includes(o.orderStatus))
    .map((o) => o.id);

  useEffect(() => {
    // Note: entries for orders that have dropped out of the trackable set
    // are simply never read again (the marker-drawing effect below only
    // ever looks up passengerPositions[o.id] for orders still present in
    // activeOrders) - so there's no need to eagerly prune them here, which
    // would mean calling setState directly in an effect body.
    const unsubs = trackableOrderIds.map((orderId) =>
      onValue(ref(rtdb, `OrderLocations/${orderId}/latest`), (snap) => {
        const v = snap.val();
        setPassengerPositions((prev) => {
          const next = { ...prev };
          if (v && typeof v.lat === "number" && typeof v.lng === "number") {
            next[orderId] = { lat: v.lat, lng: v.lng };
          } else {
            delete next[orderId];
          }
          return next;
        });
      })
    );

    return () => unsubs.forEach((u) => u());
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [JSON.stringify(trackableOrderIds)]);

  const cities = useCallback(() => {
    const set = new Set();
    Object.values(restaurants).forEach((r) => r.city && set.add(r.city));
    Object.values(riderProfiles).forEach((r) => r.city && set.add(r.city));
    return Array.from(set).sort();
  }, [restaurants, riderProfiles]);

  // ---- Redraw markers + connecting lines whenever anything relevant changes ----
  useEffect(() => {
    if (!mapRef.current || !window.google) return;

    markersRef.current.forEach((m) => m.setMap(null));
    linesRef.current.forEach((l) => l.setMap(null));
    markersRef.current = [];
    linesRef.current = [];

    const bounds = new window.google.maps.LatLngBounds();
    let any = false;

    const addMarker = (pos, colour, title) => {
      const marker = new window.google.maps.Marker({
        position: pos,
        map: mapRef.current,
        title,
        icon: {
          path: window.google.maps.SymbolPath.CIRCLE,
          fillColor: colour,
          fillOpacity: 1,
          strokeColor: "#fff",
          strokeWeight: 2,
          scale: 8,
        },
      });
      markersRef.current.push(marker);
      bounds.extend(pos);
      any = true;
    };

    if (roleFilter.restaurant) {
      Object.entries(restaurants).forEach(([, r]) => {
        if (cityFilter !== "All" && r.city !== cityFilter) return;
        addMarker({ lat: r.lat, lng: r.lng }, MARKER_COLOURS.restaurant, r.name);
      });
    }

    if (roleFilter.rider) {
      Object.entries(riderPositions).forEach(([riderUid, pos]) => {
        const profile = riderProfiles[riderUid];
        if (cityFilter !== "All" && profile && profile.city !== cityFilter) return;
        addMarker(pos, MARKER_COLOURS.rider, profile ? profile.name : "Rider");
      });
    }

    // Passengers + the connecting line per active, rider-assigned order.
    activeOrders.forEach((o) => {
      const passengerPos = passengerPositions[o.id];
      const restaurant = restaurants[o.restaurantId];
      const riderPos = o.acceptedBy ? riderPositions[o.acceptedBy] : null;

      if (cityFilter !== "All" && restaurant && restaurant.city !== cityFilter) return;

      if (roleFilter.passenger && passengerPos) {
        addMarker(passengerPos, MARKER_COLOURS.passenger, o.passengerName || "Passenger");
      }

      // Draw the line whenever at least 2 of the 3 points for this order
      // are known and visible under the current filters - visually ties
      // together who's serving who.
      const points = [];
      if (roleFilter.restaurant && restaurant) points.push({ lat: restaurant.lat, lng: restaurant.lng });
      if (roleFilter.rider && riderPos) points.push(riderPos);
      if (roleFilter.passenger && passengerPos) points.push(passengerPos);

      if (points.length >= 2) {
        const line = new window.google.maps.Polyline({
          path: points,
          map: mapRef.current,
          strokeColor: "#616161",
          strokeOpacity: 0.7,
          strokeWeight: 2,
        });
        linesRef.current.push(line);
      }
    });

    if (any) {
      mapRef.current.fitBounds(bounds);
    }
  }, [restaurants, riderPositions, riderProfiles, activeOrders, passengerPositions, roleFilter, cityFilter]);

  const toggleRole = (role) => setRoleFilter((prev) => ({ ...prev, [role]: !prev[role] }));

  return (
    <div className={`live-map-card ${expanded ? "live-map-expanded" : ""}`}>
      <div className="live-map-header">
        <div>
          <h3 className="chart-title">Live Operations Map</h3>
          <p className="chart-subtitle">
            {Object.keys(riderPositions).length} online rider(s) &middot; {activeOrders.length} active order(s)
          </p>
        </div>
        <button className="live-map-expand-btn" onClick={() => setExpanded((e) => !e)}>
          {expanded ? "Collapse" : "Expand"}
        </button>
      </div>

      <div className="live-map-filters">
        <label className={`live-map-chip ${roleFilter.restaurant ? "active" : ""}`}>
          <input type="checkbox" checked={roleFilter.restaurant} onChange={() => toggleRole("restaurant")} />
          <span className="dot" style={{ background: MARKER_COLOURS.restaurant }}></span> Restaurants
        </label>
        <label className={`live-map-chip ${roleFilter.rider ? "active" : ""}`}>
          <input type="checkbox" checked={roleFilter.rider} onChange={() => toggleRole("rider")} />
          <span className="dot" style={{ background: MARKER_COLOURS.rider }}></span> Riders
        </label>
        <label className={`live-map-chip ${roleFilter.passenger ? "active" : ""}`}>
          <input type="checkbox" checked={roleFilter.passenger} onChange={() => toggleRole("passenger")} />
          <span className="dot" style={{ background: MARKER_COLOURS.passenger }}></span> Passengers
        </label>

        <select value={cityFilter} onChange={(e) => setCityFilter(e.target.value)} className="live-map-city-select">
          <option value="All">All Cities</option>
          {cities().map((c) => (
            <option key={c} value={c}>{c}</option>
          ))}
        </select>
      </div>

      {mapsError ? (
        <div className="live-map-error">Couldn't load the map: {mapsError}</div>
      ) : (
        <div ref={mapDivRef} className="live-map-canvas" />
      )}
    </div>
  );
};

export default LiveMap;
