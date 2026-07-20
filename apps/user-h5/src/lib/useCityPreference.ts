import { useCallback, useState } from 'react';
import type { SupportedCity } from './location';

// Remembers the city the rider last chose.
//
// This is the fallback when location permission is denied or unavailable. We deliberately do NOT
// infer a city from IP: guessing someone's location straight after they declined to share it is
// the wrong instinct, and carrier IP geolocation is frequently wrong anyway. A remembered choice
// is both more accurate and more honest.

const STORAGE_KEY = 'carpool.city';

function read(): SupportedCity | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as SupportedCity;
    return parsed && parsed.adcodePrefix ? parsed : null;
  } catch {
    // Corrupt or unavailable storage must never break the screen.
    return null;
  }
}

export function useCityPreference() {
  const [city, setCityState] = useState<SupportedCity | null>(() => read());

  const setCity = useCallback((next: SupportedCity | null) => {
    setCityState(next);
    try {
      if (next) {
        window.localStorage.setItem(STORAGE_KEY, JSON.stringify(next));
      } else {
        window.localStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // Non-fatal: the choice still applies for this session.
    }
  }, []);

  return { city, setCity };
}
