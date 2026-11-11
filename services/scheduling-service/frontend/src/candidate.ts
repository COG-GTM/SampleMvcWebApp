import { useCallback, useState } from 'react';

const KEY = 'scheduling.candidateNumber';

/** The signed-in candidate is just a remembered candidate number (no auth in this story). */
export function useCandidateNumber(): [string, (v: string) => void] {
  const [value, setValue] = useState(() => localStorage.getItem(KEY) ?? '');
  const set = useCallback((v: string) => {
    // persist synchronously: callers often navigate away in the same tick
    if (v) localStorage.setItem(KEY, v);
    else localStorage.removeItem(KEY);
    setValue(v);
  }, []);
  return [value, set];
}
