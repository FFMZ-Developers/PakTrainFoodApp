import { Routes, Route } from 'react-router-dom';
import Login from './components/Login';
import Dashboard from './components/Dashboard';
import TrainRoutes from './components/TrainRoutes';
import TrainDetails from './components/TrainDetails';
import ProtectedRoute from './components/ProtectedRoute';

function App() {
  return (
    <Routes>
      <Route path="/" element={<Login />} />
      <Route path="/dashboard" element={<ProtectedRoute><Dashboard /></ProtectedRoute>} />
      <Route path="/train-routes" element={<ProtectedRoute><TrainRoutes /></ProtectedRoute>} />
      <Route path="/train-details/:id" element={<ProtectedRoute><TrainDetails /></ProtectedRoute>} />
    </Routes>
  );
}

export default App;
