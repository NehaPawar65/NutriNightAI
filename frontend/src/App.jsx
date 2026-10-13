import { Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import { EmptyState } from './components/ui';
import { AuthProvider } from './context/AuthContext';
import { FilterProvider } from './context/FilterContext';
import About from './pages/About';
import AiInsights from './pages/AiInsights';
import Dashboard from './pages/Dashboard';
import DataManagement from './pages/DataManagement';
import HealthyAlternatives from './pages/HealthyAlternatives';
import LateNightAnalysis from './pages/LateNightAnalysis';
import NutritionAnalysis from './pages/NutritionAnalysis';
import Reports from './pages/Reports';

export default function App() {
  return (
    <AuthProvider>
      <FilterProvider>
        <Layout>
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/data" element={<DataManagement />} />
            <Route path="/late-night" element={<LateNightAnalysis />} />
            <Route path="/nutrition" element={<NutritionAnalysis />} />
            <Route path="/ai" element={<AiInsights />} />
            <Route path="/alternatives" element={<HealthyAlternatives />} />
            <Route path="/reports" element={<Reports />} />
            <Route path="/about" element={<About />} />
            <Route path="*" element={<EmptyState title="Page not found" message="Use the navigation to choose a page." />} />
          </Routes>
        </Layout>
      </FilterProvider>
    </AuthProvider>
  );
}
