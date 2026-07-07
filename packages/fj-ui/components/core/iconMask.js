const ICONS = {
  "alarm-clock": '<circle cx="12" cy="13" r="7"/><path d="M12 10v4l2 2M5 3 2 6M19 3l3 3M7 21l-1 1M17 21l1 1"/>',
  "alert-circle": '<circle cx="12" cy="12" r="10"/><path d="M12 8v5M12 17h.01"/>',
  "alert-triangle": '<path d="M10.3 3.9 1.8 18a2 2 0 0 0 1.7 3h17a2 2 0 0 0 1.7-3L13.7 3.9a2 2 0 0 0-3.4 0Z"/><path d="M12 9v4M12 17h.01"/>',
  "car-front": '<path d="M6 14h.01M18 14h.01M5 18h14M5 18v2M19 18v2M4 14l1.5-5A3 3 0 0 1 8.4 7h7.2a3 3 0 0 1 2.9 2L20 14v4H4v-4Z"/>',
  "check-circle": '<circle cx="12" cy="12" r="10"/><path d="m9 12 2 2 4-5"/>',
  file: '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6"/>',
  "file-check-2": '<path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z"/><path d="M14 2v6h6M9 15l2 2 4-4"/>',
  inbox: '<path d="M22 12h-6l-2 3h-4l-2-3H2"/><path d="M5.5 5h13L22 12v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2v-6Z"/>',
  info: '<circle cx="12" cy="12" r="10"/><path d="M12 16v-4M12 8h.01"/>',
  lock: '<rect x="4" y="10" width="16" height="10" rx="2"/><path d="M8 10V7a4 4 0 0 1 8 0v3"/>',
  minus: '<path d="M5 12h14"/>',
  "shield-alert": '<path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"/><path d="M12 8v4M12 16h.01"/>',
  "trending-down": '<path d="m22 17-8.5-8.5-5 5L2 7"/><path d="M16 17h6v-6"/>',
  "trending-up": '<path d="m22 7-8.5 8.5-5-5L2 17"/><path d="M16 7h6v6"/>',
  "upload-cloud": '<path d="M16 16l-4-4-4 4M12 12v9"/><path d="M20.4 16.5A5 5 0 0 0 18 7h-1.3A7 7 0 1 0 5 14.5"/>',
  workflow: '<rect x="3" y="3" width="6" height="6" rx="1"/><rect x="15" y="15" width="6" height="6" rx="1"/><path d="M9 6h4a3 3 0 0 1 3 3v6M12 18H9a3 3 0 0 1-3-3v-3"/>'
};

const CACHE = new Map();

export function iconMask(name = "info") {
  const key = ICONS[name] ? name : "info";
  if (!CACHE.has(key)) {
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="black" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">${ICONS[key]}</svg>`;
    CACHE.set(key, `url("data:image/svg+xml,${encodeURIComponent(svg)}")`);
  }
  return CACHE.get(key);
}
