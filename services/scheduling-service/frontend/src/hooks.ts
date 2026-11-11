import { useCallback, useEffect, useState } from 'react';
import { toast } from 'sonner';
import { ApiError } from './api';

/** Loads data on mount / when deps change; exposes reload for after-mutation refreshes. */
export function useLoad<T>(loader: () => Promise<T>, deps: unknown[]) {
  const [data, setData] = useState<T | null>(null);
  const [error, setError] = useState<ApiError | Error | null>(null);
  const [loading, setLoading] = useState(true);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  const run = useCallback(loader, deps);
  const reload = useCallback(async () => {
    setLoading(true);
    try {
      setData(await run());
      setError(null);
    } catch (e) {
      setError(e as Error);
    } finally {
      setLoading(false);
    }
  }, [run]);
  useEffect(() => {
    void reload();
  }, [reload]);
  return { data, error, loading, reload, setData };
}

export function toastError(e: unknown, fallback = 'Something went wrong') {
  if (e instanceof ApiError) {
    toast.error(e.message, { description: e.legacyCode ? `${e.code} (legacy ${e.legacyCode})` : e.code });
  } else {
    toast.error(fallback);
  }
}

const KEY = 'nw.candidateNumber';
export const rememberedCandidate = () => localStorage.getItem(KEY) ?? '';
export const rememberCandidate = (n: string) => localStorage.setItem(KEY, n);

export const todayIso = () => {
  const d = new Date();
  return new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 10);
};

export const plusDays = (iso: string, days: number) => {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + days);
  return d.toISOString().slice(0, 10);
};
