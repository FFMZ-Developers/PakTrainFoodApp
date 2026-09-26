import { Routes, Route } from 'react-router-dom';
import Login from './components/Login';
import Dashboard from './components/Dashboard';
import TrainRoutes from './components/TrainRoutes';
import TrainDetails from './components/TrainDetails';
import ProtectedRoute from './components/ProtectedRoute';

// Yahan app ke saare pages (routes) ki list hai.
// ProtectedRoute mein lipta hua page sirf login ke baad khulta hai.
function App() {
  return (
    <Routes>
      {/* Pehla page: login */}
      <Route path="/" element={<Login />} />

      {/* Neeche wale pages ke liye login zaroori hai */}
      <Route path="/dashboard" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
      <Route path="/train-routes" element={<ProtectedRoute><TrainRoutes /></ProtectedRoute>} />
      {/* :id ki jagah train ki asli id aati hai */}
      <Route path="/train-details/:id" element={<ProtectedRoute><TrainDetails /></ProtectedRoute>} />
    </Routes>
  );
}

export default App;
