import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { onAuthStateChanged } from "firebase/auth";
import { auth } from "../firebase/config";

// Wraps any route that must not be reachable without logging in first.
// Before this, typing /dashboard (or /train-routes, /train-details/:id)
// straight into the address bar opened the page directly - the sidebar
// hid restricted tabs, but the underlying route itself had no login
// check at all.
//
// Usage in App.jsx:
//   <Route path="/dashboard" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />

const ProtectedRoute = ({ children }) => {
  // null = "still checking", true/false = the actual answer.
  const [authed, setAuthed] = useState(null);

  useEffect(() => {
    const unsubscribe = onAuthStateChanged(auth, (user) => {
      setAuthed(!!user);
    });
    return unsubscribe;
  }, []);

  if (authed === null) {
    // Firebase takes a moment to rehydrate the session on page load -
    // show nothing (or a spinner) rather than flashing the page and
    // then yanking it away.
    return (
      <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100vh" }}>
        Loading...
      </div>
    );
  }

  if (!authed) {
    return <Navigate to="/" replace />;
  }

  return children;
};

export default ProtectedRoute;
