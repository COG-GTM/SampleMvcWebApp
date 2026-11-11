import { NavLink, Navigate, Route, Routes } from 'react-router-dom';
import FindSessions from './pages/FindSessions';
import Book from './pages/Book';
import MyBookings from './pages/MyBookings';
import Reschedule from './pages/Reschedule';
import AdminHome from './pages/AdminHome';
import AdminCenter from './pages/AdminCenter';
import AdminSession from './pages/AdminSession';

const link = ({ isActive }: { isActive: boolean }) =>
  `rounded-lg px-3 py-2 text-sm font-medium ${isActive ? 'bg-indigo-50 text-indigo-700' : 'text-slate-600 hover:bg-slate-100'}`;

export default function App() {
  return (
    <div className="min-h-screen">
      <header className="sticky top-0 z-30 border-b border-slate-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-6xl flex-wrap items-center justify-between gap-2 px-4 py-3">
          <NavLink to="/" className="flex items-center gap-2 font-bold text-slate-900">
            <span className="grid h-8 w-8 place-items-center rounded-lg bg-indigo-600 text-white">N</span>
            <span>Northwind Scheduling</span>
          </NavLink>
          <nav className="flex flex-wrap gap-1">
            <NavLink to="/" end className={link}>Find sessions</NavLink>
            <NavLink to="/my-bookings" className={link}>My bookings</NavLink>
            <NavLink to="/admin" className={link}>Admin</NavLink>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-6xl px-4 py-8">
        <Routes>
          <Route path="/" element={<FindSessions />} />
          <Route path="/book" element={<Book />} />
          <Route path="/my-bookings" element={<MyBookings />} />
          <Route path="/reschedule/:bookingId" element={<Reschedule />} />
          <Route path="/admin" element={<AdminHome />} />
          <Route path="/admin/centers/:centerCode" element={<AdminCenter />} />
          <Route path="/admin/sessions/:sessionId" element={<AdminSession />} />
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
    </div>
  );
}
