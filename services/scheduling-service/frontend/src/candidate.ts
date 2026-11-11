import { useEffect, useState } from 'react';

const KEY = 'scheduling.candidateNumber';

/** The signed-in candidate is just a remembered candidate number (no auth in this story). */
export function useCandidateNumber(): [string, (v: string) => void] {
  const [value, setValue] = useState(() => localStorage.getItem(KEY) ?? '');
  useEffect(() => {
    if (value) localStorage.setItem(KEY, value);
    else localStorage.removeItem(KEY);
  }, [value]);
  return [value, setValue];
}
