import { useEffect, useState } from "react";
import { Navigate } from "react-router-dom";
import { onAuthStateChanged } from "firebase/auth";
import { auth } from "../firebase/config";

// Yeh "darwaza" hai: login ke baghair koi page nahi khulne deta.
// Warna address bar mein /dashboard likh kar koi bhi andar aa jata.
//
// Istemal (App.jsx mein):
//   <Route path="/dashboard" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />

const ProtectedRoute = ({ children }) => {
  // null = abhi check ho raha hai, true = login hai, false = login nahi
  const [authed, setAuthed] = useState(null);

  useEffect(() => {
    // Firebase batata hai ke user login hai ya nahi
    const unsubscribe = onAuthStateChanged(auth, (user) => {
      setAuthed(!!user);
    });
    // Page band hone par sunna band kar do
    return unsubscribe;
  }, []);

  // Firebase ko login yaad karne mein thora waqt lagta hai.
  // Tab tak "Loading..." dikhao, taake page chamak kar gayab na ho.
  if (authed === null) {
    return (
      <div style={{ display: "flex", alignItems: "center", justifyContent: "center", height: "100vh" }}>
        Loading...
      </div>
    );
  }

  // Login nahi hai to wapas login page par bhej do
  if (!authed) {
    return <Navigate to="/" replace />;
  }

  // Login hai to asli page dikha do
  return children;
};

export default ProtectedRoute;
