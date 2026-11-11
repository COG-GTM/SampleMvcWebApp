import { useCallback, useState } from 'react';

const KEY = 'scheduling.candidateNumber';

/**
 * The signed-in candidate is just a remembered candidate number (no auth in this story).
 * `initial` (e.g. from a deep link) wins over the remembered number and is remembered in its place.
 */
export function useCandidateNumber(initial?: string): [string, (v: string) => void] {
  const [value, setValue] = useState(() => {
    if (initial) localStorage.setItem(KEY, initial);
    return initial || (localStorage.getItem(KEY) ?? '');
  });
  const set = useCallback((v: string) => {
    // persist synchronously: callers often navigate away in the same tick
    if (v) localStorage.setItem(KEY, v);
    else localStorage.removeItem(KEY);
    setValue(v);
  }, []);
  return [value, set];
}
