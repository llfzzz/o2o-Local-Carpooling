// Structured location types, mirroring the backend LocationRef contract.
//
// A place is only usable once the server has resolved it: free text is an input to resolution,
// never a location. `isResolved` is the guard every search/publish path goes through, so an
// unresolved value cannot reach an order.

/**
 * Which system a coordinate pair is expressed in. The browser Geolocation API returns WGS84;
 * AMap returns and expects GCJ02. Inside China they differ by roughly 500m, so a pair without a
 * datum is meaningless — every coordinate crossing our API carries one.
 */
export type CoordinateDatum = 'WGS84' | 'GCJ02';

export type GeoPoint = {
  latitude: number;
  longitude: number;
  datum: CoordinateDatum;
};

export type LocationSource =
  | 'GEOLOCATION'
  | 'AUTOCOMPLETE'
  | 'POI_SEARCH'
  | 'MAP_PIN'
  | 'MANUAL'
  | 'DEMO_SEED';

export type LocationRef = {
  point: GeoPoint;
  provider: string;
  providerPlaceId: string | null;
  cityCode: string | null;
  adcode: string;
  displayName: string;
  formattedAddress: string;
  source: LocationSource;
  accuracyMeters: number | null;
  capturedAt: string;
};

export type SupportedCity = {
  adcodePrefix: string;
  name: string;
  cityCode: string;
};

export type MapCities = {
  unrestricted: boolean;
  /** True when the demo provider is active, so every place must be badged as demo data. */
  demoProvider: boolean;
  cities: SupportedCity[];
};

/** A location can only enter a search, publish, or order once it looks like this. */
export function isResolved(value: LocationRef | null | undefined): value is LocationRef {
  return Boolean(value && value.adcode && Number.isFinite(value.point?.latitude));
}

/** True when this place came from the demo provider and must be labelled in the UI. */
export function isDemoLocation(location: LocationRef): boolean {
  return location.provider === 'demo' || location.source === 'DEMO_SEED';
}
