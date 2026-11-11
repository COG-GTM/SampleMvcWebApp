import { Navigate, Route, Routes } from 'react-router-dom';
import Layout from './components/Layout';
import FindSessions from './pages/FindSessions';
import Book from './pages/Book';
import MyBookings from './pages/MyBookings';
import Reschedule from './pages/Reschedule';
import AdminIndex from './pages/AdminIndex';
import AdminCenter from './pages/AdminCenter';
import AdminSession from './pages/AdminSession';

export default function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<FindSessions />} />
        <Route path="book" element={<Book />} />
        <Route path="my-bookings" element={<MyBookings />} />
        <Route path="reschedule/:bookingId" element={<Reschedule />} />
        <Route path="admin" element={<AdminIndex />} />
        <Route path="admin/centers/:centerCode" element={<AdminCenter />} />
        <Route path="admin/sessions/:sessionId" element={<AdminSession />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Route>
    </Routes>
  );
}
