/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL?: string;
  readonly VITE_PROXY_TARGET?: string;
  readonly VITE_BASE_PATH?: string;
  /**
   * AMap JS API key. Render-only — every place lookup goes through map-service, so this key
   * cannot be used to query POI data. Restrict it by domain whitelist in the AMap console.
   */
  readonly VITE_AMAP_JS_KEY?: string;
}

interface ImportMeta {
  readonly env: ImportMetaEnv;
}
