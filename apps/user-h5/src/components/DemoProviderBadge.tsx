import { useCitiesQuery } from '../lib/queries';

/**
 * Says plainly that map results are demo data.
 *
 * Demo output must be impossible to mistake for real provider output, so this is deliberately
 * unmissable rather than a subtle grey footnote.
 */
export function DemoProviderBadge() {
  const cities = useCitiesQuery();
  if (!cities.data?.demoProvider) return null;

  return (
    <p className="demo-provider-badge" role="note">
      演示地图数据 · 地点与路线为固定示例，非真实供应商结果
    </p>
  );
}
