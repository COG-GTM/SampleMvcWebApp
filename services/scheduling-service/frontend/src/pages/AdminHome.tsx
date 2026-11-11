import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, type Center } from '../api';
import { PageHeader, Spinner } from '../components/ui';

export default function AdminHome() {
  const [centers, setCenters] = useState<Center[] | null>(null);
  useEffect(() => { api.centers().then(setCenters).catch(() => setCenters([])); }, []);
  return (
    <>
      <PageHeader title="Test-centre administration" subtitle="Pick a centre to manage session capacity and waitlists." />
      {!centers ? <Spinner /> : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {centers.map((c) => (
            <Link key={c.centerCode} to={`/admin/centers/${c.centerCode}`} className="card p-4 transition hover:border-indigo-300 hover:shadow" data-testid={`admin-center-${c.centerCode}`}>
              <p className="text-xs font-semibold text-indigo-600">{c.centerCode} · {c.region}</p>
              <p className="font-semibold">{c.name}</p>
              <p className="text-sm text-slate-500">{c.city} · {c.timeZone}</p>
            </Link>
          ))}
        </div>
      )}
    </>
  );
}
