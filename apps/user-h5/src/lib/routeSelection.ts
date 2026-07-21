// Shared route-selection state: the origin, destination and (confirmed) route preview that both
// shells' home screens AND the expanded interactive map read and write. Lifting this out of
// per-screen useState is what keeps the home thumbnail and the expanded map showing the same
// selected route — editing a point in either place updates both.
//
// The preview is server-authoritative (POST /api/trips/route-preview) and is cleared whenever an
// endpoint changes, so a stale distance/price can never linger after the route changed.
import { create } from 'zustand';
import type { LocationRef } from './location';
import type { RoutePreview } from './types';

type RouteSelectionState = {
  origin: LocationRef | null;
  destination: LocationRef | null;
  /** Confirmed route + price for the current origin/destination, or null until confirmed. */
  preview: RoutePreview | null;
  setOrigin: (location: LocationRef | null) => void;
  setDestination: (location: LocationRef | null) => void;
  swap: () => void;
  clearOrigin: () => void;
  clearDestination: () => void;
  setPreview: (preview: RoutePreview | null) => void;
  reset: () => void;
};

export const useRouteSelection = create<RouteSelectionState>((set) => ({
  origin: null,
  destination: null,
  preview: null,
  // Any endpoint change invalidates a previously confirmed preview.
  setOrigin: (location) => set({ origin: location, preview: null }),
  setDestination: (location) => set({ destination: location, preview: null }),
  swap: () => set((state) => ({ origin: state.destination, destination: state.origin, preview: null })),
  clearOrigin: () => set({ origin: null, preview: null }),
  clearDestination: () => set({ destination: null, preview: null }),
  setPreview: (preview) => set({ preview }),
  reset: () => set({ origin: null, destination: null, preview: null })
}));
