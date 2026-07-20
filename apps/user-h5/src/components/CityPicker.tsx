import { MapPin } from 'lucide-react';
import { useCitiesQuery } from '../lib/queries';
import type { SupportedCity } from '../lib/location';

type Props = {
  city: SupportedCity | null;
  onChange: (city: SupportedCity) => void;
};

/**
 * City selector, seeded from the rider's last choice.
 *
 * This is the fallback whenever location is denied or unavailable — the reason a denied prompt
 * is never a dead end. When the backend runs without a city allowlist there is nothing to pick
 * from, so the control stays out of the way rather than showing an empty menu.
 */
export function CityPicker({ city, onChange }: Props) {
  const cities = useCitiesQuery();
  const options = cities.data?.cities ?? [];

  if (cities.data?.unrestricted || options.length === 0) {
    return (
      <span className="city-chip city-chip-static">
        <MapPin size={16} color="var(--accent)" />
        <span>{city?.name ?? '全国'}</span>
      </span>
    );
  }

  return (
    <label className="city-chip">
      <MapPin size={16} color="var(--accent)" />
      <select
        className="city-select"
        aria-label="城市"
        value={city?.adcodePrefix ?? ''}
        onChange={(event) => {
          const picked = options.find((option) => option.adcodePrefix === event.target.value);
          if (picked) onChange(picked);
        }}
      >
        <option value="" disabled>选择城市</option>
        {options.map((option) => (
          <option key={option.adcodePrefix} value={option.adcodePrefix}>{option.name}</option>
        ))}
      </select>
    </label>
  );
}
