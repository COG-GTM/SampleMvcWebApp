import { Link } from 'react-router-dom';
import { api } from '../api';
import { ErrorBox, PageHeader, Spinner } from '../components/ui';
import { useLoad } from '../hooks';

export default function AdminIndex() {
  const centers = useLoad(() => api.centers(), []);
  return (
    <div>
      <PageHeader title="Test-center administration" subtitle="Choose a center to manage session capacity and waitlists." />
      {centers.loading && !centers.data && <Spinner />}
      {centers.error && <ErrorBox error={centers.error} />}
      <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-4">
        {centers.data?.map((c) => (
          <Link key={c.centerCode} to={`/admin/centers/${c.centerCode}`} className="card p-4 transition hover:border-brand-500 hover:shadow-md" data-testid="admin-center-link">
            <p className="text-xs font-semibold text-brand-600">{c.centerCode}</p>
            <p className="font-semibold">{c.name}</p>
            <p className="text-sm text-slate-500">{c.city}, {c.region}</p>
          </Link>
        ))}
      </div>
    </div>
  );
}
