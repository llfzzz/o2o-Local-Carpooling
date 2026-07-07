# @fj — Free Joy design system (local)

Local copy of the **Free Joy (FJ)** design system, imported from the Claude
Design project `Free Joy Design System`
(`a42e56e8-d278-47e6-9679-38bc1fc507bb`) and re-themed to the carpooling
teal-green identity. Both frontends (`apps/user-h5`, `apps/admin-console`)
consume it via the `@fj` alias.

## Layout

```
styles.css            global entry — @imports tokens/*
tokens/
  colors.css          FJ neutral + joy/sun/bloom + semantic ramps (coral default)
  typography.css      Bricolage Grotesque (display) / Hanken Grotesk (text) / JetBrains Mono
  spacing.css         space / radius / shadow / motion
  fonts.css           Google Fonts @import
  base.css            resets, .fj-glass utilities, focus ring
  brand-carpool.css   ← OUR override: re-points the --joy-* ramp to teal #137A63
components/<group>/    <Name>.jsx (runtime) + <Name>.d.ts (types) per component
index.ts               barrel re-export consumed as `@fj`
```

## Usage

`apps/*/src/main.tsx` imports the styles once, override last:

```ts
import '@fj/styles.css';
import '@fj/tokens/brand-carpool.css';
import './styles.css';
```

Components are plain ES modules — import from the barrel:

```tsx
import { Button, Card, Stack, Tabs, useToast } from '@fj';
```

Wiring per app: `vite.config.ts` aliases `@fj` → this dir + `resolve.dedupe`
for React; `tsconfig.json` maps `@fj/*` paths and `react`/`react-dom` (so these
files, which live outside the apps' `node_modules`, resolve React types).

## Theming

FJ is token-driven. `brand-carpool.css` overrides only the `--joy-*` ramp, which
re-themes `--accent`, `--accent-hover/press`, `--ring`, `--border-focus`, and the
per-instance `accent="coral"` maps in one place — so the whole system reads teal
while keeping FJ neutrals, type, spacing, radius, shadow, and motion. Components
also accept an `accent` prop (`"coral" | "sun" | "bloom" | any CSS color`).

## Scope / provenance notes

- **Curated subset.** Only the components both apps use were imported (core,
  layout, forms, navigation, feedback, plus data `List`/`Stat`/`Timeline`). The
  heavier data-grid family (`DataGrid`, `Table`, `Tree`, `Transfer`, etc.) is
  intentionally **deferred** — `apps/admin-console` keeps the antd `Table` for now
  (isolated in `DataTablePanel`) until FJ `DataGrid` is imported to replace it.
  Pull more components on demand via the same `DesignSync` flow.
- **Fonts self-hosted.** Fonts ship via `@fontsource` (Bricolage Grotesque
  variable, Hanken Grotesk, JetBrains Mono), imported in each app's `main.tsx` —
  no Google Fonts CDN. `tokens/fonts.css` is intentionally empty; the variable
  display family name (`Bricolage Grotesque Variable`) is set in
  `brand-carpool.css`.
- **Icons are local.** The `Icon` / `FileUpload` / `List` / `Stat` / `Timeline`
  components render a curated Lucide-style subset as inline SVG masks, so
  production pages do not depend on an external icon CDN. App-level icons use
  bundled `lucide-react` passed as `iconLeft`/`iconRight` props.
- **Keep in sync, don't fork.** These files mirror the upstream FJ project. Apply
  brand changes in `tokens/brand-carpool.css`, not by editing component sources,
  so a future `DesignSync` re-pull stays clean.
