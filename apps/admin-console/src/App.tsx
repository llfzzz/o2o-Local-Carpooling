import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ConfigProvider, Table, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { Alert, Badge, Button, Card, Input, Stack, Stat, Text, Timeline } from '@fj';
import { FileCheck2, Radar } from 'lucide-react';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080';

type AuthToken = {
  accessToken: string;
};

type DashboardSummary = {
  pendingDriverReviews: number;
  todayOrders: number;
  lockedOrders: number;
  overduePendingPayments: number;
  riskAlerts: number;
  status: string;
};

type VerificationCase = {
  caseId: string;
  userId: string;
  status: 'OCR_REVIEWABLE' | 'APPROVED' | 'REJECTED';
  uploadedFileIds: Record<string, string>;
  ocrResult: {
    confidence: number;
  };
  submittedAt: string;
};

type OrderRow = {
  orderId: string;
  tripId: string;
  riderId: string;
  status: 'PENDING_PAYMENT' | 'SEAT_LOCKED' | 'TIMEOUT_CANCELLED' | 'USER_CANCELLED' | 'DRIVER_CANCELLED' | 'COMPLETED';
  amount: {
    amount: number;
    currency: string;
  };
  seats: number;
  createdAt: string;
};

type PresignedDownload = {
  fileObject: {
    fileObjectId: string;
    objectName: string;
  };
  downloadUrl: string;
  expiresAt: string;
};

type AuditLog = {
  auditId: string;
  actorId: string;
  action: string;
  targetType: string;
  targetId: string;
  metadata: Record<string, string>;
  traceId: string | null;
  occurredAt: string;
};

type AuditLogPage = {
  items: AuditLog[];
  page: number;
  size: number;
  total: number;
};

type ConsoleView = 'overview' | 'reviews' | 'orders' | 'audits';

const NAV: { value: ConsoleView; label: string }[] = [
  { value: 'overview', label: '运营总览' },
  { value: 'reviews', label: '司机审核' },
  { value: 'orders', label: '订单监控' },
  { value: 'audits', label: '审计检索' }
];

const EMPTY_AUDIT_FILTER = { targetType: '', action: '', actorId: '' };

// Free Joy tokens projected onto antd, so the retained antd Table / data-grid
// visually matches the FJ chrome (teal accent, hairline borders, type, radius).
const FJ_ANTD_THEME = {
  token: {
    colorPrimary: '#137A63',
    colorLink: '#137A63',
    colorInfo: '#137A63',
    borderRadius: 12,
    fontFamily: '"Hanken Grotesk", ui-sans-serif, system-ui, sans-serif',
    colorText: '#1C1C1A',
    colorTextHeading: '#1C1C1A',
    colorTextSecondary: '#565651',
    colorBorder: '#D0D0C8',
    colorBorderSecondary: '#E2E2DC',
    colorBgContainer: '#FFFFFF',
    colorBgLayout: '#F6F6F4'
  }
};

const REVIEW_STATUS_TONE: Record<VerificationCase['status'], 'success' | 'danger' | 'warn'> = {
  APPROVED: 'success',
  REJECTED: 'danger',
  OCR_REVIEWABLE: 'warn'
};

const ORDER_STATUS_TONE: Record<OrderRow['status'], 'success' | 'danger' | 'accent'> = {
  SEAT_LOCKED: 'success',
  COMPLETED: 'success',
  TIMEOUT_CANCELLED: 'danger',
  USER_CANCELLED: 'danger',
  DRIVER_CANCELLED: 'danger',
  PENDING_PAYMENT: 'accent'
};

export default function App() {
  const [view, setView] = useState<ConsoleView>('overview');
  const [auditDraft, setAuditDraft] = useState(EMPTY_AUDIT_FILTER);
  const [auditApplied, setAuditApplied] = useState(EMPTY_AUDIT_FILTER);
  const [auditPage, setAuditPage] = useState(0);
  const queryClient = useQueryClient();
  const [messageApi, contextHolder] = message.useMessage();

  const sessionQuery = useQuery({
    queryKey: ['mock-operator-login'],
    queryFn: () => api<AuthToken>('/api/auth/login', {
      method: 'POST',
      body: { phone: '13900000000', code: 'MOCK-123456', roles: ['OPERATOR'] }
    }),
    retry: 1
  });
  const token = sessionQuery.data?.accessToken;

  const dashboardQuery = useQuery({
    queryKey: ['admin-dashboard', token],
    queryFn: () => api<DashboardSummary>('/api/admin/dashboard', { token }),
    enabled: Boolean(token)
  });

  const verificationsQuery = useQuery({
    queryKey: ['driver-verifications', token],
    queryFn: () => api<VerificationCase[]>('/api/drivers/verification-cases', { token }),
    enabled: Boolean(token)
  });

  const ordersQuery = useQuery({
    queryKey: ['admin-orders', token],
    queryFn: () => api<OrderRow[]>('/api/orders/admin', { token }),
    enabled: Boolean(token)
  });

  const auditsQuery = useQuery({
    queryKey: ['admin-audits', token, auditApplied, auditPage],
    queryFn: () => {
      const params = new URLSearchParams();
      if (auditApplied.targetType) params.set('targetType', auditApplied.targetType);
      if (auditApplied.action) params.set('action', auditApplied.action);
      if (auditApplied.actorId) params.set('actorId', auditApplied.actorId);
      params.set('page', String(auditPage));
      params.set('size', '20');
      return api<AuditLogPage>(`/api/audits?${params.toString()}`, { token });
    },
    enabled: Boolean(token) && view === 'audits'
  });

  const reviewMutation = useMutation({
    mutationFn: ({ caseId, action }: { caseId: string; action: 'approve' | 'reject' }) =>
      api<VerificationCase>(`/api/drivers/verification-cases/${caseId}/${action}`, { method: 'POST', token }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['driver-verifications'] });
      queryClient.invalidateQueries({ queryKey: ['admin-dashboard'] });
      messageApi.success('审核状态已更新');
    },
    onError: (error: Error) => messageApi.error(error.message)
  });

  const fileDownloadMutation = useMutation({
    mutationFn: (fileId: string) => api<PresignedDownload>(`/api/files/${fileId}/presign-download`, { token }),
    onSuccess: (download) => {
      window.open(download.downloadUrl, '_blank', 'noopener,noreferrer');
      messageApi.success('已生成短时下载链接');
    },
    onError: (error: Error) => messageApi.error(error.message)
  });

  const dashboard = dashboardQuery.data ?? {
    pendingDriverReviews: 0,
    todayOrders: 0,
    lockedOrders: 0,
    overduePendingPayments: 0,
    riskAlerts: 0,
    status: 'loading'
  };
  const verifications = verificationsQuery.data ?? [];
  const orders = ordersQuery.data ?? [];
  const audits = auditsQuery.data ?? { items: [], page: 0, size: 20, total: 0 };

  const reviewColumns = useMemo<ColumnsType<VerificationCase>>(
    () => [
      { title: '司机用户', dataIndex: 'userId', width: 150 },
      {
        title: '资料',
        render: (_, row) => (
          <div className="cell-actions">
            {Object.entries(row.uploadedFileIds).map(([name, value]) => (
              <Button key={name} variant="secondary" size="sm" iconLeft={<FileCheck2 size={14} />} disabled={fileDownloadMutation.isPending} onClick={() => fileDownloadMutation.mutate(value)}>
                {name}
              </Button>
            ))}
          </div>
        )
      },
      {
        title: 'OCR 置信度',
        dataIndex: ['ocrResult', 'confidence'],
        width: 120,
        render: (value: number) => `${Math.round(value * 100)}%`
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 150,
        render: (value: VerificationCase['status']) => <Badge tone={REVIEW_STATUS_TONE[value]}>{value}</Badge>
      },
      {
        title: '操作',
        width: 170,
        render: (_, row) => (
          <div className="cell-actions">
            <Button variant="primary" size="sm" disabled={row.status !== 'OCR_REVIEWABLE' || reviewMutation.isPending} onClick={() => reviewMutation.mutate({ caseId: row.caseId, action: 'approve' })}>通过</Button>
            <Button variant="danger" size="sm" disabled={row.status !== 'OCR_REVIEWABLE' || reviewMutation.isPending} onClick={() => reviewMutation.mutate({ caseId: row.caseId, action: 'reject' })}>驳回</Button>
          </div>
        )
      }
    ],
    [fileDownloadMutation, reviewMutation]
  );

  const orderColumns = useMemo<ColumnsType<OrderRow>>(
    () => [
      { title: '订单', dataIndex: 'orderId', width: 190 },
      { title: '行程', dataIndex: 'tripId' },
      { title: '乘客', dataIndex: 'riderId', width: 180 },
      { title: '座位', dataIndex: 'seats', width: 80 },
      {
        title: '金额',
        dataIndex: ['amount', 'amount'],
        width: 110,
        render: (value: number) => `¥${Number(value).toFixed(2)}`
      },
      {
        title: '订单状态',
        dataIndex: 'status',
        width: 160,
        render: (value: OrderRow['status']) => <Badge tone={ORDER_STATUS_TONE[value]}>{value}</Badge>
      }
    ],
    []
  );

  const auditColumns = useMemo<ColumnsType<AuditLog>>(
    () => [
      { title: '时间', dataIndex: 'occurredAt', width: 175, render: (value: string) => formatTime(value) },
      { title: '操作', dataIndex: 'action', width: 150, render: (value: string) => <Badge tone="accent">{value}</Badge> },
      { title: '对象类型', dataIndex: 'targetType', width: 150 },
      { title: '对象 ID', dataIndex: 'targetId', width: 190 },
      { title: '操作者', dataIndex: 'actorId', width: 160 },
      { title: 'Trace', dataIndex: 'traceId', width: 150, render: (value: string | null) => value ?? '—' },
      {
        title: '元数据',
        dataIndex: 'metadata',
        render: (meta: Record<string, string>) => {
          const entries = Object.entries(meta ?? {});
          return entries.length ? <span className="audit-meta">{entries.map(([k, v]) => `${k}=${v}`).join(' · ')}</span> : '—';
        }
      }
    ],
    []
  );

  return (
    <ConfigProvider theme={FJ_ANTD_THEME}>
      {contextHolder}
      <div className="console-shell">
        <aside className="sider">
          <div className="brand">
            <span className="brand-mark"><Radar size={20} /></span>
            <Stack gap={1}>
              <Text variant="h4" as="span">Carpool Ops</Text>
              <Text variant="eyebrow" as="span">同城拼车运营台</Text>
            </Stack>
          </div>
          <nav className="nav">
            {NAV.map((item) => (
              <button
                key={item.value}
                className={`nav-item${view === item.value ? ' active' : ''}`}
                aria-current={view === item.value ? 'page' : undefined}
                onClick={() => setView(item.value)}
              >
                {item.label}
              </button>
            ))}
          </nav>
          <div className="sider-foot">
            <Text variant="eyebrow" as="span">Free Joy · Carpool</Text>
          </div>
        </aside>

        <main className="console-main">
          <header className="console-header">
            <Text variant="h3" as="h1">企业级 O2O 拼车首期控制面</Text>
            <Badge tone={dashboard.status === 'live-mvp' ? 'success' : 'sun'} solid>MVP 0.5.0</Badge>
          </header>

          <div className="console-content">
            <Alert
              tone={sessionQuery.isError ? 'danger' : 'info'}
              title={sessionQuery.isError ? 'Gateway 未连接' : '运营台读取真实 MVP 服务数据'}
            >
              {sessionQuery.isError
                ? '运营数据无法加载，请确认本地 Gateway/服务已启动。'
                : '支付与 OCR 仍是 Mock 适配器；价格、库存、审核结论均以服务端为准。'}
            </Alert>

            {view === 'overview' && (
              <>
                <div className="metric-grid">
                  <Stat label="待审核司机" value={dashboard.pendingDriverReviews} icon="file-check-2" />
                  <Stat label="今日订单" value={dashboard.todayOrders} icon="car-front" />
                  <Stat label="超时待处理" value={dashboard.overduePendingPayments} icon="shield-alert" />
                  <Stat label="服务模块" value={12} icon="workflow" />
                </div>
                <Card padding="var(--space-5)">
                  <Text variant="h4" as="h2" style={{ marginBottom: 18 }}>审计时间线</Text>
                  <Timeline
                    items={[
                      { title: `待审核司机 ${dashboard.pendingDriverReviews} 个`, accent: 'coral', icon: 'file-check-2' },
                      { title: `锁座订单 ${dashboard.lockedOrders} 个`, accent: 'bloom', icon: 'lock' },
                      { title: `支付超时待取消 ${dashboard.overduePendingPayments} 个`, accent: 'sun', icon: 'alarm-clock' }
                    ]}
                  />
                </Card>
              </>
            )}

            {view === 'reviews' && (
              <DataTablePanel title="司机证件审核">
                <Table columns={reviewColumns} dataSource={verifications} rowKey="caseId" loading={verificationsQuery.isLoading} pagination={false} scroll={{ x: 760 }} />
              </DataTablePanel>
            )}

            {view === 'orders' && (
              <DataTablePanel title="订单状态监控">
                <Table columns={orderColumns} dataSource={orders} rowKey="orderId" loading={ordersQuery.isLoading} pagination={false} scroll={{ x: 820 }} />
              </DataTablePanel>
            )}

            {view === 'audits' && (
              <>
                <Card padding="var(--space-5)">
                  <Stack direction="row" gap={12} wrap align="flex-end">
                    <Input label="对象类型" size="sm" placeholder="如 DRIVER_VERIFICATION" value={auditDraft.targetType} onChange={(e) => setAuditDraft((d) => ({ ...d, targetType: e.target.value }))} style={{ minWidth: 180 }} />
                    <Input label="操作" size="sm" placeholder="如 APPROVE" value={auditDraft.action} onChange={(e) => setAuditDraft((d) => ({ ...d, action: e.target.value }))} style={{ minWidth: 150 }} />
                    <Input label="操作者" size="sm" placeholder="userId" value={auditDraft.actorId} onChange={(e) => setAuditDraft((d) => ({ ...d, actorId: e.target.value }))} style={{ minWidth: 150 }} />
                    <Button variant="primary" size="sm" onClick={() => { setAuditPage(0); setAuditApplied(auditDraft); }}>查询</Button>
                    <Button variant="secondary" size="sm" onClick={() => { setAuditDraft(EMPTY_AUDIT_FILTER); setAuditApplied(EMPTY_AUDIT_FILTER); setAuditPage(0); }}>重置</Button>
                  </Stack>
                </Card>
                <DataTablePanel title="审计检索">
                  <Table
                    columns={auditColumns}
                    dataSource={audits.items}
                    rowKey="auditId"
                    loading={auditsQuery.isLoading}
                    pagination={{ current: audits.page + 1, pageSize: audits.size, total: audits.total, showSizeChanger: false, onChange: (p) => setAuditPage(p - 1) }}
                    scroll={{ x: 1040 }}
                  />
                </DataTablePanel>
              </>
            )}
          </div>
        </main>
      </div>
    </ConfigProvider>
  );
}

/**
 * Boundary around the retained antd Table so the future FJ DataGrid swap is
 * localized to one component. Restyled with FJ tokens (hairline card + header).
 */
function DataTablePanel({ title, children }: { title: string; children: ReactNode }) {
  return (
    <Card padding="0" style={{ overflow: 'hidden' }}>
      <div className="table-panel-head">
        <Text variant="h4" as="h2">{title}</Text>
      </div>
      <div className="table-panel-body">{children}</div>
    </Card>
  );
}

function formatTime(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    second: '2-digit'
  }).format(new Date(value));
}

async function api<T>(path: string, options: { method?: string; token?: string; body?: unknown } = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    method: options.method ?? 'GET',
    headers: {
      ...(options.body ? { 'Content-Type': 'application/json' } : {}),
      ...(options.token ? { Authorization: `Bearer ${options.token}` } : {})
    },
    body: options.body ? JSON.stringify(options.body) : undefined
  });
  if (!response.ok) {
    const messageText = await errorMessage(response);
    throw new Error(messageText);
  }
  return response.json() as Promise<T>;
}

async function errorMessage(response: Response) {
  try {
    const payload = await response.json();
    return payload.message ?? `HTTP ${response.status}`;
  } catch {
    return `HTTP ${response.status}`;
  }
}
