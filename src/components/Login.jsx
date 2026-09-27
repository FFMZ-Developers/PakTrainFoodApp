import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import './Login.css';

import { signInWithEmailAndPassword } from 'firebase/auth';
import { doc, getDoc } from 'firebase/firestore';
import { auth, db } from '../firebase/config';

const Login = () => {

  // Form ki values aur error message yaad rakhne ke liye
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');

  // Dusre page par jaane ke liye
  const navigate = useNavigate();

  // "Sign In" dabane par yeh chalta hai
  const handleLogin = async (e) => {

    // Page reload hone se roko
    e.preventDefault();

    // Khali fields ka check
    if (!email || !password) {
      setError('Please fill in all fields.');
      return;
    }

    try {

      // Step 1: Firebase se email/password ka login
      const userCredential = await signInWithEmailAndPassword(
        auth,
        email,
        password
      );

      const user = userCredential.user;

      // Step 2: Check karo ke yeh banda "admins" list mein hai ya nahi
      const adminRef = doc(db, 'admins', user.uid);
      const adminSnap = await getDoc(adminRef);

      if (adminSnap.exists()) {

        const adminData = adminSnap.data();

        // Role save karo. Agar role likha nahi to sab se kam ijazat wala
        // "support" maan lo (safety ke liye, "super-admin" kabhi nahi).
        const resolvedRole = adminData.role || 'support';
        localStorage.setItem("role", resolvedRole);

        console.log('Admin Role:', resolvedRole);

        setError('');

        // Step 3: Dashboard par bhej do
        navigate('/dashboard');

      } else {

        // Login sahi hai lekin admin nahi hai
        setError('Access denied. You are not an admin.');

      }

    } catch (error) {

      // Galat password ya koi aur Firebase error
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

        {/* Error ho to red box mein dikhao */}
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
