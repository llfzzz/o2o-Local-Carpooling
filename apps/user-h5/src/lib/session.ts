import { create } from 'zustand';
import type { Session } from './types';

const SESSION_KEY = 'carpool.session';

type SessionStore = {
  session: Session | null;
  setSession: (session: Session | null) => void;
};

function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

export const useSession = create<SessionStore>((set) => ({
  session: loadSession(),
  setSession: (session) => {
    if (session) {
      localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    } else {
      localStorage.removeItem(SESSION_KEY);
    }
    set({ session });
  }
}));
