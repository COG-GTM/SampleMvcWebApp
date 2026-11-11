import { createContext, useCallback, useContext, useState, type ReactNode } from 'react';

type Kind = 'success' | 'error' | 'info';
interface Toast { id: number; kind: Kind; title: string; detail?: string }
const Ctx = createContext<(kind: Kind, title: string, detail?: string) => void>(() => {});

export function ToastProvider({ children }: { children: ReactNode }) {
  const [toasts, setToasts] = useState<Toast[]>([]);
  const push = useCallback((kind: Kind, title: string, detail?: string) => {
    const id = Date.now() + Math.random();
    setToasts((t) => [...t, { id, kind, title, detail }]);
    setTimeout(() => setToasts((t) => t.filter((x) => x.id !== id)), 6000);
  }, []);
  const colors: Record<Kind, string> = {
    success: 'border-emerald-200 bg-emerald-50 text-emerald-900',
    error: 'border-rose-200 bg-rose-50 text-rose-900',
    info: 'border-sky-200 bg-sky-50 text-sky-900',
  };
  return (
    <Ctx.Provider value={push}>
      {children}
      <div className="pointer-events-none fixed inset-x-0 top-4 z-50 flex flex-col items-center gap-2 px-4 sm:items-end">
        {toasts.map((t) => (
          <div key={t.id} role="status" data-testid={`toast-${t.kind}`}
            className={`pointer-events-auto w-full max-w-sm rounded-lg border px-4 py-3 shadow-lg ${colors[t.kind]}`}>
            <div className="flex items-start justify-between gap-3">
              <p className="text-sm font-semibold">{t.title}</p>
              <button className="text-xs opacity-60 hover:opacity-100" aria-label="Dismiss"
                onClick={() => setToasts((all) => all.filter((x) => x.id !== t.id))}>✕</button>
            </div>
            {t.detail && <p className="mt-1 text-sm opacity-80">{t.detail}</p>}
          </div>
        ))}
      </div>
    </Ctx.Provider>
  );
}

export const useToast = () => useContext(Ctx);
