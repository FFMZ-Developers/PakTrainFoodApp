import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './Login.css';

import { signInWithEmailAndPassword } from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import { auth, db } from '../firebase/config';

const Login = () => {

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  const navigate = useNavigate();

  const handleLogin = async (e) => {

    e.preventDefault();

    if (!email || !password) {
      setError('Please fill in all fields.');
      return;
    }

    try {

      // Firebase Login
      const userCredential = await signInWithEmailAndPassword(
        auth,
        email,
        password
      );

      const user = userCredential.user;

      // Check Admin in Firestore
      const adminRef = doc(db, 'admins', user.uid);
      const adminSnap = await getDoc(adminRef);

      if (adminSnap.exists()) {

        const adminData = adminSnap.data(); // ✅ FIXED

        // 👉 STORE ROLE (IMPORTANT)
        // Fail-safe: agar Firestore doc mein role field set nahi hai
        // to "support" (sab se limited role) treat karo, "super-admin" nahi.
        const resolvedRole = adminData.role || 'support';
        localStorage.setItem("role", resolvedRole);

        console.log('Admin Role:', resolvedRole);

        setError('');

        // 👉 GO TO DASHBOARD
        navigate('/dashboard');

      } else {

        setError('Access denied. You are not an admin.');

      }

    } catch (error) {

      console.log("Firebase Error:", error.code);
      console.log("Firebase Message:", error.message);

      setError(error.message);

    }

  };

  return (
    <div className="login-container">

      <div className="login-card">

        <div className="login-header">
          <h2>Admin Panel</h2>
          <p>Please sign in to continue</p>
        </div>

        {error && <div className="login-error">{error}</div>}

        <form onSubmit={handleLogin} className="login-form">

          <div className="form-group">

            <label htmlFor="email">Email Address</label>

            <input
              type="email"
              id="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              placeholder="admin@gmail.com"
            />

          </div>

          <div className="form-group">

            <label htmlFor="password">Password</label>

            <input
              type="password"
              id="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              placeholder="••••••••"
            />

          </div>

          <button type="submit" className="login-button">
            Sign In
          </button>

        </form>

      </div>

    </div>
  );
};

export default Login;