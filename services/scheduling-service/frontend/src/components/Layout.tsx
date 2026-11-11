import { NavLink, Outlet } from 'react-router-dom';

const link = ({ isActive }: { isActive: boolean }) =>
  `rounded-lg px-3 py-2 text-sm font-medium transition ${isActive ? 'bg-white/15 text-white' : 'text-brand-100 hover:bg-white/10 hover:text-white'}`;

export default function Layout() {
  return (
    <div className="min-h-screen">
      <header className="bg-gradient-to-r from-brand-700 via-brand-600 to-indigo-600 shadow">
        <div className="mx-auto flex max-w-7xl flex-wrap items-center justify-between gap-3 px-4 py-3 sm:px-6">
          <NavLink to="/" className="flex items-center gap-2 text-white">
            <span className="grid h-9 w-9 place-items-center rounded-xl bg-white/15 text-lg font-bold">N</span>
            <span className="leading-tight">
              <span className="block text-sm font-semibold">Northwind Assessment Network</span>
              <span className="block text-xs text-brand-100">Test-center scheduling</span>
            </span>
          </NavLink>
          <nav className="flex flex-wrap gap-1">
            <NavLink to="/" end className={link}>Find a session</NavLink>
            <NavLink to="/my-bookings" className={link}>My bookings</NavLink>
            <NavLink to="/admin" className={link}>Center admin</NavLink>
          </nav>
        </div>
      </header>
      <main className="mx-auto max-w-7xl px-4 py-6 sm:px-6 sm:py-8">
        <Outlet />
      </main>
    </div>
  );
}
