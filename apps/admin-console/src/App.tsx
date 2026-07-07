import { useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ConfigProvider, Modal, Popconfirm, Table, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import type { MessageInstance } from 'antd/es/message/interface';
import { Alert, Badge, Button, Card, Input, NumberInput, SegmentedControl, Stack, Stat, Text, Timeline } from '@fj';
import type { BadgeProps } from '@fj';
import { CreditCard, FileCheck2, Inbox, Radar, RotateCw, ScanText, ShieldCheck } from 'lucide-react';

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
  status: 'PENDING_PAYMENT' | 'SEAT_LOCKED' | 'TIMEOUT_CANCELLED' | 'USER_CANCELLED' | 'DRIVER_CANCELLED' | 'OPERATOR_CANCELLED' | 'COMPLETED';
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

type TripOffer = {
  tripId: string;
  driverId: string;
  originText: string;
  destinationText: string;
  departureAt: string;
  route: { routeId: string; distanceMeters: number; durationSeconds: number; providerTrace: string };
  inventory: { totalSeats: number; lockedSeats: number };
  seatPrice: { amount: number; currency: string };
  status: 'PUBLISHED' | 'CANCELLED' | 'FINISHED';
};

type UserSummary = {
  userId: string;
  phoneMasked: string;
  roles: string[];
  createdAt: string;
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

type PaymentIntentStatus = 'REQUIRES_PAYMENT' | 'AUTHORIZED' | 'SUCCEEDED' | 'FAILED' | 'CANCELED' | 'EXPIRED';

type PaymentIntent = {
  intentId: string;
  orderId: string;
  riderId: string;
  amount: { amount: number; currency: string };
  status: PaymentIntentStatus;
  provider: string;
  providerRef: string;
  createdAt: string;
  updatedAt: string;
};

type CallbackEmission = {
  eventId: string;
  outcome: PaymentIntentStatus;
  accepted: boolean;
  resultStatus: PaymentIntentStatus | null;
  rejectionCode: string | null;
};

type SimulationResponse = {
  intentId: string;
  finalStatus: PaymentIntentStatus;
  emissions: CallbackEmission[];
};

type IdentityStatus = 'PENDING' | 'APPROVED' | 'REJECTED' | 'TIMEOUT' | 'RETRY_REQUIRED';
type LivenessStatus = 'PENDING' | 'PASSED' | 'FAILED' | 'TIMEOUT' | 'RETRY_REQUIRED';

type IdentityVerification = {
  verificationId: string;
  userId: string;
  status: IdentityStatus;
  livenessStatus: LivenessStatus;
  provider: string;
  providerRef: string;
  createdAt: string;
  updatedAt: string;
};

type DeliveryRecord = {
  deliveryId: string;
  userId: string;
  channel: 'SMS' | 'PUSH' | 'IN_APP';
  category: string;
  title: string;
  maskedPreview: string;
  status: 'QUEUED' | 'DELIVERED' | 'FAILED' | 'RETRYING' | 'READ';
  correlationId: string | null;
  retryCount: number;
  createdAt: string;
  updatedAt: string;
  readAt: string | null;
};

type OcrTask = {
  taskId: string;
  fileObjectId: string;
  status: 'SUBMITTED' | 'PROCESSING' | 'COMPLETED' | 'FAILED';
  providerRef: string | null;
  result: { provider: string; confidence: number; fields: Record<string, string> } | null;
  submittedAt: string;
  completedAt: string | null;
};

type ConsoleView =
  | 'overview' | 'reviews' | 'orders' | 'trips' | 'users' | 'audits' | 'ocr'
  | 'payments' | 'identity' | 'notifications';

// Production ops views (real APIs) vs. demo-simulation modules (/api/demo/control/**,
// demo-profile only) — kept as separate sidebar groups so the distinction stays visible.
const NAV_MAIN: { value: ConsoleView; label: string }[] = [
  { value: 'overview', label: '运营总览' },
  { value: 'reviews', label: '司机审核' },
  { value: 'orders', label: '订单监控' },
  { value: 'trips', label: '行程总览' },
  { value: 'users', label: '用户管理' },
  { value: 'audits', label: '审计检索' },
  { value: 'ocr', label: 'OCR 任务' }
];

const NAV_DEMO: { value: ConsoleView; label: string }[] = [
  { value: 'payments', label: '支付回调' },
  { value: 'identity', label: '实名认证' },
  { value: 'notifications', label: '通知投递' }
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
  OPERATOR_CANCELLED: 'danger',
  PENDING_PAYMENT: 'accent'
};

const PAYMENT_STATUS_LABEL: Record<PaymentIntentStatus, string> = {
  REQUIRES_PAYMENT: '待支付',
  AUTHORIZED: '已授权',
  SUCCEEDED: '支付成功',
  FAILED: '支付失败',
  CANCELED: '已取消',
  EXPIRED: '已过期'
};

const PAYMENT_STATUS_TONE: Record<PaymentIntentStatus, 'accent' | 'success' | 'danger' | 'warn'> = {
  REQUIRES_PAYMENT: 'accent',
  AUTHORIZED: 'warn',
  SUCCEEDED: 'success',
  FAILED: 'danger',
  CANCELED: 'danger',
  EXPIRED: 'danger'
};

const IDENTITY_STATUS_LABEL: Record<IdentityStatus, string> = {
  PENDING: '认证中',
  APPROVED: '已通过',
  REJECTED: '已驳回',
  TIMEOUT: '已超时',
  RETRY_REQUIRED: '需重试'
};

const IDENTITY_STATUS_TONE: Record<IdentityStatus, 'accent' | 'success' | 'danger' | 'warn'> = {
  PENDING: 'accent',
  APPROVED: 'success',
  REJECTED: 'danger',
  TIMEOUT: 'danger',
  RETRY_REQUIRED: 'warn'
};

const LIVENESS_STATUS_LABEL: Record<LivenessStatus, string> = {
  PENDING: '待检测',
  PASSED: '已通过',
  FAILED: '已失败',
  TIMEOUT: '已超时',
  RETRY_REQUIRED: '需重试'
};

const LIVENESS_STATUS_TONE: Record<LivenessStatus, 'accent' | 'success' | 'danger' | 'warn'> = {
  PENDING: 'accent',
  PASSED: 'success',
  FAILED: 'danger',
  TIMEOUT: 'danger',
  RETRY_REQUIRED: 'warn'
};

const DELIVERY_STATUS_LABEL: Record<DeliveryRecord['status'], string> = {
  QUEUED: '排队中',
  DELIVERED: '已投递',
  FAILED: '投递失败',
  RETRYING: '重试中',
  READ: '已读'
};

const DELIVERY_STATUS_TONE: Record<DeliveryRecord['status'], 'accent' | 'success' | 'danger' | 'warn' | 'neutral'> = {
  QUEUED: 'accent',
  DELIVERED: 'success',
  FAILED: 'danger',
  RETRYING: 'warn',
  READ: 'neutral'
};

const OCR_STATUS_LABEL: Record<OcrTask['status'], string> = {
  SUBMITTED: '已提交',
  PROCESSING: '识别中',
  COMPLETED: '已完成',
  FAILED: '已失败'
};

const OCR_STATUS_TONE: Record<OcrTask['status'], 'accent' | 'success' | 'danger' | 'warn'> = {
  SUBMITTED: 'accent',
  PROCESSING: 'warn',
  COMPLETED: 'success',
  FAILED: 'danger'
};

export default function App() {
  const [view, setView] = useState<ConsoleView>('overview');
  const [auditDraft, setAuditDraft] = useState(EMPTY_AUDIT_FILTER);
  const [auditApplied, setAuditApplied] = useState(EMPTY_AUDIT_FILTER);
  const [auditPage, setAuditPage] = useState(0);
  const [tripDraft, setTripDraft] = useState({ origin: '', destination: '' });
  const [tripApplied, setTripApplied] = useState({ origin: '', destination: '' });
  const queryClient = useQueryClient();
  const [messageApi, contextHolder] = message.useMessage();

  const sessionQuery = useQuery({
    queryKey: ['demo-operator-session'],
    // Demo-only: mint an operator (OPERATOR + ADMIN) session in one call. Server-gated by the demo
    // seed flag (S26); replaces the old client-supplied-roles mock login removed by the S8 auth fix.
    queryFn: () => api<AuthToken>('/api/auth/demo/operator-session', { method: 'POST', body: {} }),
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
    enabled: Boolean(token),
    // Poll only while the orders monitor itself is on screen; the demo modules
    // refresh manually (and invalidate this query after a payment simulation).
    refetchInterval: view === 'orders' ? 5000 : false
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

  const tripsQuery = useQuery({
    queryKey: ['admin-trips', token, tripApplied],
    queryFn: () => {
      const params = new URLSearchParams();
      if (tripApplied.origin) params.set('origin', tripApplied.origin);
      if (tripApplied.destination) params.set('destination', tripApplied.destination);
      const qs = params.toString();
      return api<TripOffer[]>(`/api/trips${qs ? `?${qs}` : ''}`, { token });
    },
    enabled: Boolean(token) && view === 'trips'
  });

  const usersQuery = useQuery({
    queryKey: ['admin-users', token],
    queryFn: () => api<UserSummary[]>('/api/users', { token }),
    enabled: Boolean(token) && view === 'users'
  });

  const reviewMutation = useMutation({
    mutationFn: ({ caseId, action }: { caseId: string; action: 'approve' | 'reject' }) =>
      api<VerificationCase>(`/api/drivers/verification-cases/${caseId}/${action}`, { method: 'POST', token }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['driver-verifications'] });
      queryClient.invalidateQueries({ queryKey: ['admin-dashboard'] });
      messageApi.success('审核状态已更新');
    },
    onError: (error: Error) => messageApi.error(describeError(error))
  });

  const fileDownloadMutation = useMutation({
    mutationFn: (fileId: string) => api<PresignedDownload>(`/api/files/${fileId}/presign-download`, { token }),
    onSuccess: (download) => {
      window.open(download.downloadUrl, '_blank', 'noopener,noreferrer');
      messageApi.success('已生成短时下载链接');
    },
    onError: (error: Error) => messageApi.error(describeError(error))
  });

  // Production APIs (not demo-control): operator completes / cancels a real order; audited server-side.
  const [cancelTarget, setCancelTarget] = useState<OrderRow | null>(null);
  const [cancelReason, setCancelReason] = useState('');

  const completeOrderMutation = useMutation({
    mutationFn: (orderId: string) => api<OrderRow>(`/api/orders/${orderId}/complete`, { method: 'POST', token }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-orders'] });
      queryClient.invalidateQueries({ queryKey: ['admin-dashboard'] });
      messageApi.success('订单已完成，评价邀请已投递乘客收件箱');
    },
    onError: (error: Error) => messageApi.error(describeError(error))
  });

  const cancelOrderMutation = useMutation({
    mutationFn: ({ orderId, reason }: { orderId: string; reason: string }) =>
      api<OrderRow>(`/api/orders/${orderId}/cancel`, {
        method: 'POST',
        token,
        body: reason.trim() ? { reason: reason.trim() } : {}
      }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin-orders'] });
      queryClient.invalidateQueries({ queryKey: ['admin-dashboard'] });
      setCancelTarget(null);
      setCancelReason('');
      messageApi.success('订单已取消，座位已释放；取消原因已写入审计');
    },
    onError: (error: Error) => messageApi.error(describeError(error))
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
  const trips = tripsQuery.data ?? [];
  const users = usersQuery.data ?? [];

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
      },
      {
        title: '操作',
        width: 190,
        render: (_, row) => {
          const canComplete = row.status === 'SEAT_LOCKED';
          const canCancel = row.status === 'PENDING_PAYMENT' || row.status === 'SEAT_LOCKED';
          if (!canComplete && !canCancel) {
            return <Text variant="small" as="span" style={{ color: 'var(--text-subtle)' }}>—</Text>;
          }
          return (
            <div className="cell-actions">
              {canComplete && (
                <Popconfirm
                  title="确认完成该订单？"
                  description="行程视为已消费，乘客将收到评价邀请。"
                  okText="完成"
                  cancelText="再想想"
                  onConfirm={() => completeOrderMutation.mutate(row.orderId)}
                >
                  <Button variant="primary" size="sm" disabled={completeOrderMutation.isPending}>完成订单</Button>
                </Popconfirm>
              )}
              {canCancel && (
                <Button
                  variant="danger"
                  size="sm"
                  disabled={cancelOrderMutation.isPending}
                  onClick={() => { setCancelTarget(row); setCancelReason(''); }}
                >
                  取消订单
                </Button>
              )}
            </div>
          );
        }
      }
    ],
    [completeOrderMutation, cancelOrderMutation]
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

  const tripColumns = useMemo<ColumnsType<TripOffer>>(
    () => [
      { title: '行程', dataIndex: 'tripId', width: 180 },
      { title: '司机', dataIndex: 'driverId', width: 150 },
      { title: '路线', width: 220, render: (_, row) => `${row.originText} → ${row.destinationText}` },
      { title: '出发', dataIndex: 'departureAt', width: 150, render: (value: string) => formatTime(value) },
      { title: '距离', width: 90, render: (_, row) => `${(row.route.distanceMeters / 1000).toFixed(1)}km` },
      { title: '座位(锁/总)', width: 110, render: (_, row) => `${row.inventory.lockedSeats}/${row.inventory.totalSeats}` },
      { title: '单价', width: 100, render: (_, row) => `¥${Number(row.seatPrice.amount).toFixed(2)}` },
      {
        title: '路线源',
        dataIndex: ['route', 'providerTrace'],
        width: 120,
        render: (value: string) => <Badge tone={value === 'amap-v5' ? 'success' : 'sun'}>{value}</Badge>
      },
      {
        title: '状态',
        dataIndex: 'status',
        width: 120,
        render: (value: TripOffer['status']) => <Badge tone={value === 'PUBLISHED' ? 'accent' : value === 'CANCELLED' ? 'danger' : 'neutral'}>{value}</Badge>
      }
    ],
    []
  );

  const userColumns = useMemo<ColumnsType<UserSummary>>(
    () => [
      { title: '用户', dataIndex: 'userId', width: 200 },
      { title: '手机（脱敏）', dataIndex: 'phoneMasked', width: 160 },
      {
        title: '角色',
        dataIndex: 'roles',
        render: (roles: string[]) => (
          <div className="cell-actions">
            {roles.map((role) => (
              <Badge key={role} tone={role === 'ADMIN' ? 'danger' : role === 'OPERATOR' ? 'accent' : role === 'DRIVER' ? 'success' : 'neutral'}>{role}</Badge>
            ))}
          </div>
        )
      },
      { title: '注册时间', dataIndex: 'createdAt', width: 175, render: (value: string) => formatTime(value) }
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
            {NAV_MAIN.map((item) => (
              <button
                key={item.value}
                className={`nav-item${view === item.value ? ' active' : ''}`}
                aria-current={view === item.value ? 'page' : undefined}
                onClick={() => setView(item.value)}
              >
                {item.label}
              </button>
            ))}
            <div className="nav-group-label">
              <Text variant="eyebrow" as="span">演示模拟</Text>
            </div>
            {NAV_DEMO.map((item) => (
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
              <>
                <Alert tone="info" title="生产 API：完成 / 取消为真实运营动作">
                  「完成订单」「取消订单」调用真实订单接口（服务端鉴权 + 状态机 + 审计留痕），非演示模拟；取消原因可在「审计检索」中按 ORDER 查看。
                </Alert>
                <DataTablePanel title="订单状态监控">
                  <Table columns={orderColumns} dataSource={orders} rowKey="orderId" loading={ordersQuery.isLoading} pagination={false} scroll={{ x: 1020 }} />
                </DataTablePanel>
              </>
            )}

            {view === 'payments' && <PaymentCallbacksView token={token} messageApi={messageApi} />}

            {view === 'identity' && <IdentityControlView token={token} messageApi={messageApi} />}

            {view === 'notifications' && <NotificationDeliveriesView token={token} messageApi={messageApi} />}

            {view === 'ocr' && <OcrTasksView token={token} messageApi={messageApi} />}

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

            {view === 'trips' && (
              <>
                <Card padding="var(--space-5)">
                  <Stack direction="row" gap={12} wrap align="flex-end">
                    <Input label="起点" size="sm" placeholder="origin" value={tripDraft.origin} onChange={(e) => setTripDraft((d) => ({ ...d, origin: e.target.value }))} style={{ minWidth: 180 }} />
                    <Input label="终点" size="sm" placeholder="destination" value={tripDraft.destination} onChange={(e) => setTripDraft((d) => ({ ...d, destination: e.target.value }))} style={{ minWidth: 180 }} />
                    <Button variant="primary" size="sm" onClick={() => setTripApplied(tripDraft)}>查询</Button>
                    <Button variant="secondary" size="sm" onClick={() => { setTripDraft({ origin: '', destination: '' }); setTripApplied({ origin: '', destination: '' }); }}>重置</Button>
                  </Stack>
                </Card>
                <DataTablePanel title="行程总览（已发布）">
                  <Table columns={tripColumns} dataSource={trips} rowKey="tripId" loading={tripsQuery.isLoading} pagination={false} scroll={{ x: 1120 }} />
                </DataTablePanel>
              </>
            )}

            {view === 'users' && (
              <DataTablePanel title="用户管理（手机号脱敏展示）">
                <Table columns={userColumns} dataSource={users} rowKey="userId" loading={usersQuery.isLoading} pagination={false} scroll={{ x: 760 }} />
              </DataTablePanel>
            )}
          </div>
        </main>

        <Modal
          title="取消订单（运营）"
          open={cancelTarget !== null}
          okText={cancelOrderMutation.isPending ? '取消中…' : '确认取消'}
          cancelText="返回"
          okButtonProps={{ danger: true, disabled: cancelOrderMutation.isPending || !cancelReason.trim() }}
          onOk={() => { if (cancelTarget) cancelOrderMutation.mutate({ orderId: cancelTarget.orderId, reason: cancelReason }); }}
          onCancel={() => { setCancelTarget(null); setCancelReason(''); }}
          destroyOnHidden
        >
          <Stack gap={12} style={{ paddingTop: 8 }}>
            <Text variant="small" as="p">
              订单 {cancelTarget?.orderId}（当前 {cancelTarget?.status ?? ''}）将迁移为 OPERATOR_CANCELLED，座位立即释放。该动作走真实订单状态机并写入审计。
            </Text>
            <Input
              label="取消原因（必填，≤200 字）"
              placeholder="如：乘客投诉司机爽约 / 风控拦截"
              value={cancelReason}
              onChange={(event) => setCancelReason(event.target.value)}
            />
          </Stack>
        </Modal>
      </div>
    </ConfigProvider>
  );
}

/**
 * Shared pieces for the operational modules. The three demo-simulation modules (支付回调 /
 * 实名认证 / 通知投递) call /api/demo/control/** — double-gated server-side, nonexistent outside
 * the demo profile — and flow through the same signed-webhook pipeline / authoritative state
 * machines a real provider would drive; the frontend never mutates business state itself.
 * OCR 任务 is a production API. None of these lists auto-poll: data changes only on the manual
 * 刷新 button or right after a user-triggered action completes.
 */

/** Manual refresh for a list query; the modules deliberately do not auto-poll. */
function RefreshButton({ query }: { query: { refetch: () => unknown; isFetching: boolean } }) {
  return (
    <Button
      variant="secondary"
      size="sm"
      iconLeft={<RotateCw size={14} />}
      disabled={query.isFetching}
      onClick={() => query.refetch()}
    >
      {query.isFetching ? '刷新中…' : '刷新'}
    </Button>
  );
}

/** Module title row: icon + name + demo/production badge + right-aligned actions (refresh 等). */
function ModuleHeader({ icon, title, demo = false, actions }: {
  icon: ReactNode;
  title: string;
  demo?: boolean;
  actions?: ReactNode;
}) {
  return (
    <Stack direction="row" align="center" justify="space-between" gap={12} wrap>
      <Stack direction="row" align="center" gap={8}>
        {icon}
        <Text variant="h4" as="span">{title}</Text>
        <Badge tone={demo ? 'warn' : 'accent'}>{demo ? '演示模拟' : '生产 API'}</Badge>
      </Stack>
      {actions}
    </Stack>
  );
}

/** Uniform status pill driven by the per-domain label/tone maps. */
function StatusBadge<T extends string>({ value, labels, tones }: {
  value: T;
  labels: Record<T, string>;
  tones: Record<T, NonNullable<BadgeProps['tone']>>;
}) {
  return <Badge tone={tones[value]}>{labels[value]}</Badge>;
}

/** 支付回调管理：选择 intent → 选择结局/投递模式 → 经签名管道投递，展示每次回调被接受/拒绝。 */
function PaymentCallbacksView({ token, messageApi }: { token?: string; messageApi: MessageInstance }) {
  const queryClient = useQueryClient();
  const [selectedIntentId, setSelectedIntentId] = useState<string | null>(null);
  const [outcome, setOutcome] = useState<PaymentIntentStatus>('SUCCEEDED');
  const [mode, setMode] = useState('NORMAL');
  const [delaySeconds, setDelaySeconds] = useState(0);
  const [lastResult, setLastResult] = useState<SimulationResponse | null>(null);

  const intentsQuery = useQuery({
    queryKey: ['demo-intents', token],
    queryFn: () => api<PaymentIntent[]>('/api/demo/control/payment/intents?limit=20', { token }),
    enabled: Boolean(token),
    // Manual refresh only (刷新 button); no polling, no focus refetch.
    refetchOnWindowFocus: false
  });
  const intents = intentsQuery.data ?? [];
  const selected = intents.find((intent) => intent.intentId === selectedIntentId) ?? null;

  const simulate = useMutation({
    mutationFn: () =>
      api<SimulationResponse>(`/api/demo/control/payment/${selectedIntentId}/callbacks`, {
        method: 'POST',
        token,
        body: { outcome, mode, delaySeconds }
      }),
    onSuccess: (result) => {
      setLastResult(result);
      queryClient.invalidateQueries({ queryKey: ['demo-intents'] });
      queryClient.invalidateQueries({ queryKey: ['admin-orders'] });
      const accepted = result.emissions.filter((emission) => emission.accepted).length;
      messageApi.success(`已投递 ${result.emissions.length} 条签名回调（${accepted} 条被接受）· 最终 ${PAYMENT_STATUS_LABEL[result.finalStatus]}`);
    },
    onError: (error: Error) => messageApi.error(describeError(error))
  });

  const columns = useMemo<ColumnsType<PaymentIntent>>(
    () => [
      { title: '支付意图', dataIndex: 'intentId', width: 200 },
      { title: '订单', dataIndex: 'orderId', width: 190 },
      { title: '金额', dataIndex: ['amount', 'amount'], width: 100, render: (value: number) => `¥${Number(value).toFixed(2)}` },
      {
        title: '状态',
        dataIndex: 'status',
        width: 110,
        render: (value: PaymentIntentStatus) => <StatusBadge value={value} labels={PAYMENT_STATUS_LABEL} tones={PAYMENT_STATUS_TONE} />
      },
      { title: '创建时间', dataIndex: 'createdAt', width: 150, render: (value: string) => formatTime(value) },
      { title: '更新时间', dataIndex: 'updatedAt', width: 150, render: (value: string) => formatTime(value) }
    ],
    []
  );

  return (
    <>
      <Alert tone="info" title="演示模拟（Demo-only）：模拟支付供应商的签名回调">
        本模块调用 /api/demo/control/payment/**，仅 demo 环境存在（staging/production 下为 404）。结局经 HMAC 签名 Webhook 管道摄取（重放/幂等/终态保护全程可观测），订单状态只由验证通过的回调驱动；接入真实供应商时仅替换 Provider，流程不变。
      </Alert>
      <Card padding="var(--space-5)">
      <Stack gap={16}>
        <ModuleHeader
          demo
          icon={<CreditCard size={18} color="var(--accent)" />}
          title="支付回调管理"
          actions={<RefreshButton query={intentsQuery} />}
        />
        <Text variant="small" as="p" style={{ color: 'var(--text-subtle)' }}>
          「待支付」即 pending：不投递回调订单就停在 PENDING_PAYMENT（超时后由 RabbitMQ 延迟队列自动取消）。选择一个 intent 后可主动触发成功 / 失败 / 取消 / 过期，或演练重复、乱序、超窗迟到回调。列表不自动刷新，点右上「刷新」拉取最新。
        </Text>
        <Table
          size="small"
          columns={columns}
          dataSource={intents}
          rowKey="intentId"
          loading={intentsQuery.isLoading}
          pagination={false}
          scroll={{ x: 910, y: 260 }}
          rowSelection={{
            type: 'radio',
            selectedRowKeys: selectedIntentId ? [selectedIntentId] : [],
            onChange: (keys) => setSelectedIntentId(keys.length ? String(keys[0]) : null)
          }}
          locale={{ emptyText: intentsQuery.isError ? '加载失败，请确认已登录运营会话' : '暂无支付意图 — 请先在 H5 下单并「发起支付」，再点「刷新」' }}
        />
        {selected ? (
          <Stack gap={14}>
            <div className="cell-actions">
              <StatusBadge value={selected.status} labels={PAYMENT_STATUS_LABEL} tones={PAYMENT_STATUS_TONE} />
              <Text variant="small" as="span">{selected.intentId} · 订单 {selected.orderId}</Text>
            </div>
            <Stack direction="row" gap={16} wrap align="flex-end">
              <Stack gap={4}>
                <Text variant="small" as="span" style={{ color: 'var(--text)' }}>回调结局</Text>
                <SegmentedControl
                  size="sm"
                  value={outcome}
                  onChange={(value) => setOutcome(value as PaymentIntentStatus)}
                  options={[
                    { value: 'SUCCEEDED', label: '成功' },
                    { value: 'FAILED', label: '失败' },
                    { value: 'CANCELED', label: '取消' },
                    { value: 'EXPIRED', label: '过期' }
                  ]}
                />
              </Stack>
              <Stack gap={4}>
                <Text variant="small" as="span" style={{ color: 'var(--text)' }}>投递模式</Text>
                <SegmentedControl
                  size="sm"
                  value={mode}
                  onChange={setMode}
                  options={[
                    { value: 'NORMAL', label: '正常' },
                    { value: 'DUPLICATE', label: '重复投递' },
                    { value: 'OUT_OF_ORDER', label: '乱序投递' }
                  ]}
                />
              </Stack>
              <NumberInput
                size="sm"
                label="回调时间回拨（秒）"
                hint="超过 300 秒会因超出签名时间窗被拒绝（重放保护演示）"
                value={delaySeconds}
                min={0}
                max={3600}
                step={60}
                onChange={(value) => setDelaySeconds(Number(value))}
              />
              <Button variant="primary" disabled={simulate.isPending} onClick={() => simulate.mutate()}>
                {simulate.isPending ? '投递中…' : '投递签名回调'}
              </Button>
            </Stack>
          </Stack>
        ) : (
          <Text variant="small" as="p" style={{ color: 'var(--text-subtle)' }}>先在上表选择一个支付意图。</Text>
        )}
        {lastResult && (
          <Stack gap={10}>
            <div className="cell-actions">
              <Text variant="small" as="span" style={{ color: 'var(--text)' }}>最近一次投递结果：</Text>
              <Badge tone={PAYMENT_STATUS_TONE[lastResult.finalStatus]}>最终 {PAYMENT_STATUS_LABEL[lastResult.finalStatus]}</Badge>
            </div>
            <Timeline
              items={lastResult.emissions.map((emission) => ({
                title: `${PAYMENT_STATUS_LABEL[emission.outcome]} · ${
                  emission.accepted
                    ? `已接受 → ${emission.resultStatus ? PAYMENT_STATUS_LABEL[emission.resultStatus] : ''}`
                    : `被管道拒绝 · ${emission.rejectionCode ?? ''}`
                }（${emission.eventId.slice(0, 16)}…）`,
                accent: emission.accepted ? 'bloom' : 'coral',
                icon: emission.accepted ? 'file-check-2' : 'shield-alert'
              }))}
            />
          </Stack>
        )}
      </Stack>
      </Card>
    </>
  );
}

/** 实名认证 / 活体检测管理：为选中的认证会话驱动活体结果与会话结论（结果异步投递该用户收件箱）。 */
function IdentityControlView({ token, messageApi }: { token?: string; messageApi: MessageInstance }) {
  const queryClient = useQueryClient();
  const [selectedId, setSelectedId] = useState<string | null>(null);

  const verificationsQuery = useQuery({
    queryKey: ['demo-verifications', token],
    queryFn: () => api<IdentityVerification[]>('/api/demo/control/identity/verifications?limit=20', { token }),
    enabled: Boolean(token),
    // Manual refresh only (刷新 button); no polling, no focus refetch.
    refetchOnWindowFocus: false
  });
  const verifications = verificationsQuery.data ?? [];
  const selected = verifications.find((verification) => verification.verificationId === selectedId) ?? null;

  const drive = useMutation({
    mutationFn: ({ kind, outcome }: { kind: 'liveness' | 'session'; outcome: string }) =>
      api<IdentityVerification>(`/api/demo/control/identity/${selectedId}/${kind}`, { method: 'POST', token, body: { outcome } }),
    onSuccess: (verification) => {
      queryClient.invalidateQueries({ queryKey: ['demo-verifications'] });
      messageApi.success(`已更新：会话 ${IDENTITY_STATUS_LABEL[verification.status]} · 活体 ${LIVENESS_STATUS_LABEL[verification.livenessStatus]}`);
    },
    onError: (error: Error) => messageApi.error(describeError(error))
  });

  const columns = useMemo<ColumnsType<IdentityVerification>>(
    () => [
      { title: '认证会话', dataIndex: 'verificationId', width: 200 },
      { title: '用户', dataIndex: 'userId', width: 170 },
      {
        title: '会话状态',
        dataIndex: 'status',
        width: 110,
        render: (value: IdentityStatus) => <StatusBadge value={value} labels={IDENTITY_STATUS_LABEL} tones={IDENTITY_STATUS_TONE} />
      },
      {
        title: '活体状态',
        dataIndex: 'livenessStatus',
        width: 110,
        render: (value: LivenessStatus) => <StatusBadge value={value} labels={LIVENESS_STATUS_LABEL} tones={LIVENESS_STATUS_TONE} />
      },
      { title: '发起时间', dataIndex: 'createdAt', width: 150, render: (value: string) => formatTime(value) },
      { title: '更新时间', dataIndex: 'updatedAt', width: 150, render: (value: string) => formatTime(value) }
    ],
    []
  );

  const actionDisabled = !selected || drive.isPending;

  return (
    <>
      <Alert tone="info" title="演示模拟（Demo-only）：模拟实名认证供应商的异步结论">
        本模块调用 /api/demo/control/identity/**，仅 demo 环境存在（staging/production 下为 404）。结论经两层权威状态机流转（终态不可覆盖）并异步投递到该用户的演示收件箱，从不内联返回；接入真实供应商时仅替换 Provider。
      </Alert>
      <Card padding="var(--space-5)">
      <Stack gap={16}>
        <ModuleHeader
          demo
          icon={<ShieldCheck size={18} color="var(--accent)" />}
          title="实名认证 / 活体检测管理"
          actions={<RefreshButton query={verificationsQuery} />}
        />
        <Text variant="small" as="p" style={{ color: 'var(--text-subtle)' }}>
          会话「通过」要求活体已通过（服务端状态机强制，非法迁移返回 409）。列表不自动刷新，点右上「刷新」拉取最新。
        </Text>
        <Table
          size="small"
          columns={columns}
          dataSource={verifications}
          rowKey="verificationId"
          loading={verificationsQuery.isLoading}
          pagination={false}
          scroll={{ x: 760, y: 260 }}
          rowSelection={{
            type: 'radio',
            selectedRowKeys: selectedId ? [selectedId] : [],
            onChange: (keys) => setSelectedId(keys.length ? String(keys[0]) : null)
          }}
          locale={{ emptyText: '暂无认证会话 — 请先在 H5「认证」页发起实名认证，再点「刷新」' }}
        />
        {selected ? (
          <Stack gap={12}>
            <Stack direction="row" gap={12} wrap align="center">
              <Text variant="small" as="span" style={{ color: 'var(--text)', minWidth: 72 }}>活体结果</Text>
              <div className="cell-actions">
                <Button variant="primary" size="sm" disabled={actionDisabled} onClick={() => drive.mutate({ kind: 'liveness', outcome: 'PASSED' })}>通过</Button>
                <Button variant="danger" size="sm" disabled={actionDisabled} onClick={() => drive.mutate({ kind: 'liveness', outcome: 'FAILED' })}>失败</Button>
                <Button variant="secondary" size="sm" disabled={actionDisabled} onClick={() => drive.mutate({ kind: 'liveness', outcome: 'TIMEOUT' })}>超时</Button>
                <Button variant="secondary" size="sm" disabled={actionDisabled} onClick={() => drive.mutate({ kind: 'liveness', outcome: 'RETRY_REQUIRED' })}>需重试</Button>
              </div>
            </Stack>
            <Stack direction="row" gap={12} wrap align="center">
              <Text variant="small" as="span" style={{ color: 'var(--text)', minWidth: 72 }}>会话结论</Text>
              <div className="cell-actions">
                <Button variant="primary" size="sm" disabled={actionDisabled} onClick={() => drive.mutate({ kind: 'session', outcome: 'APPROVED' })}>通过</Button>
                <Button variant="danger" size="sm" disabled={actionDisabled} onClick={() => drive.mutate({ kind: 'session', outcome: 'REJECTED' })}>驳回</Button>
                <Button variant="secondary" size="sm" disabled={actionDisabled} onClick={() => drive.mutate({ kind: 'session', outcome: 'TIMEOUT' })}>超时</Button>
                <Button variant="secondary" size="sm" disabled={actionDisabled} onClick={() => drive.mutate({ kind: 'session', outcome: 'RETRY_REQUIRED' })}>需重试</Button>
              </div>
            </Stack>
          </Stack>
        ) : (
          <Text variant="small" as="p" style={{ color: 'var(--text-subtle)' }}>先在上表选择一个认证会话。</Text>
        )}
      </Stack>
      </Card>
    </>
  );
}

/** 通知投递管理：驱动收件箱投递记录的状态（投递/失败/重试/已读），预览始终脱敏。 */
function NotificationDeliveriesView({ token, messageApi }: { token?: string; messageApi: MessageInstance }) {
  const queryClient = useQueryClient();

  const deliveriesQuery = useQuery({
    queryKey: ['demo-deliveries', token],
    queryFn: () => api<DeliveryRecord[]>('/api/demo/control/notification/deliveries?limit=20', { token }),
    enabled: Boolean(token),
    // Manual refresh only (刷新 button); no polling, no focus refetch.
    refetchOnWindowFocus: false
  });
  const deliveries = deliveriesQuery.data ?? [];

  const simulate = useMutation({
    mutationFn: ({ deliveryId, status }: { deliveryId: string; status: DeliveryRecord['status'] }) =>
      api(`/api/demo/control/notification/${deliveryId}/status`, { method: 'POST', token, body: { status } }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['demo-deliveries'] });
      messageApi.success('投递状态已更新');
    },
    onError: (error: Error) => messageApi.error(describeError(error))
  });

  const columns = useMemo<ColumnsType<DeliveryRecord>>(
    () => [
      { title: '用户', dataIndex: 'userId', width: 150 },
      { title: '渠道', dataIndex: 'channel', width: 80 },
      { title: '类别', dataIndex: 'category', width: 170 },
      { title: '预览（脱敏）', dataIndex: 'maskedPreview', ellipsis: true },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (value: DeliveryRecord['status']) => <StatusBadge value={value} labels={DELIVERY_STATUS_LABEL} tones={DELIVERY_STATUS_TONE} />
      },
      { title: '重试', dataIndex: 'retryCount', width: 70 },
      { title: '更新时间', dataIndex: 'updatedAt', width: 150, render: (value: string) => formatTime(value) },
      {
        title: '操作',
        width: 250,
        render: (_, row) => (
          <div className="cell-actions">
            <Button variant="secondary" size="sm" disabled={simulate.isPending} onClick={() => simulate.mutate({ deliveryId: row.deliveryId, status: 'DELIVERED' })}>投递</Button>
            <Button variant="danger" size="sm" disabled={simulate.isPending} onClick={() => simulate.mutate({ deliveryId: row.deliveryId, status: 'FAILED' })}>失败</Button>
            <Button variant="secondary" size="sm" disabled={simulate.isPending} onClick={() => simulate.mutate({ deliveryId: row.deliveryId, status: 'RETRYING' })}>重试</Button>
            <Button variant="ghost" size="sm" disabled={simulate.isPending} onClick={() => simulate.mutate({ deliveryId: row.deliveryId, status: 'READ' })}>已读</Button>
          </div>
        )
      }
    ],
    [simulate]
  );

  return (
    <>
      <Alert tone="info" title="演示模拟（Demo-only）：模拟通知渠道侧的投递结果">
        本模块调用 /api/demo/control/notification/**，仅 demo 环境存在（staging/production 下为 404）。跨用户列表内容永远脱敏，敏感值只能由收件人本人在 H5 收件箱内显式「查看」取出。
      </Alert>
      <Card padding="var(--space-5)">
      <Stack gap={16}>
        <ModuleHeader
          demo
          icon={<Inbox size={18} color="var(--accent)" />}
          title="通知投递管理"
          actions={<RefreshButton query={deliveriesQuery} />}
        />
        <Text variant="small" as="p" style={{ color: 'var(--text-subtle)' }}>
          可将任意投递记录驱动为 投递 / 失败 / 重试（计数 +1）/ 已读。列表不自动刷新，点右上「刷新」拉取最新。
        </Text>
        <Table
          size="small"
          columns={columns}
          dataSource={deliveries}
          rowKey="deliveryId"
          loading={deliveriesQuery.isLoading}
          pagination={false}
          scroll={{ x: 1150, y: 300 }}
          locale={{ emptyText: '暂无投递记录 — 登录验证码 / 支付结果 / 认证结论都会产生投递，点「刷新」拉取' }}
        />
      </Stack>
      </Card>
    </>
  );
}

/** OCR 任务管理：真实 ai-service 异步任务接口（提交 → 轮询 → 完成，结果字段已脱敏）。 */
function OcrTasksView({ token, messageApi }: { token?: string; messageApi: MessageInstance }) {
  const queryClient = useQueryClient();
  const [fileObjectId, setFileObjectId] = useState('');

  const tasksQuery = useQuery({
    queryKey: ['ocr-tasks', token],
    queryFn: () => api<OcrTask[]>('/api/ai/ocr/tasks?limit=20', { token }),
    enabled: Boolean(token),
    // Manual refresh only (刷新 button); no polling, no focus refetch.
    refetchOnWindowFocus: false
  });
  const tasks = tasksQuery.data ?? [];

  const submit = useMutation({
    mutationFn: () => api<OcrTask>('/api/ai/ocr/tasks', { method: 'POST', token, body: { fileObjectId: fileObjectId.trim() } }),
    onSuccess: (task) => {
      queryClient.invalidateQueries({ queryKey: ['ocr-tasks'] });
      setFileObjectId('');
      messageApi.success(`OCR 任务已提交（${OCR_STATUS_LABEL[task.status]}），点击「查询进度」推进`);
    },
    onError: (error: Error) => messageApi.error(describeError(error))
  });

  const poll = useMutation({
    mutationFn: (taskId: string) => api<OcrTask>(`/api/ai/ocr/tasks/${taskId}`, { token }),
    onSuccess: (task) => {
      queryClient.invalidateQueries({ queryKey: ['ocr-tasks'] });
      messageApi.success(`任务 ${OCR_STATUS_LABEL[task.status]}${task.result ? ` · 置信度 ${Math.round(task.result.confidence * 100)}%` : ''}`);
    },
    onError: (error: Error) => messageApi.error(describeError(error))
  });

  const columns = useMemo<ColumnsType<OcrTask>>(
    () => [
      { title: '任务', dataIndex: 'taskId', width: 210 },
      { title: '文件对象', dataIndex: 'fileObjectId', width: 190, ellipsis: true },
      {
        title: '状态',
        dataIndex: 'status',
        width: 100,
        render: (value: OcrTask['status']) => <StatusBadge value={value} labels={OCR_STATUS_LABEL} tones={OCR_STATUS_TONE} />
      },
      {
        title: '置信度',
        width: 90,
        render: (_, row) => (row.result ? `${Math.round(row.result.confidence * 100)}%` : '—')
      },
      {
        title: '识别字段（已脱敏）',
        render: (_, row) => {
          const entries = Object.entries(row.result?.fields ?? {});
          return entries.length
            ? <span className="audit-meta">{entries.map(([key, value]) => `${key}=${value}`).join(' · ')}</span>
            : '—';
        }
      },
      { title: '提交时间', dataIndex: 'submittedAt', width: 150, render: (value: string) => formatTime(value) },
      {
        title: '操作',
        width: 120,
        render: (_, row) => (
          <Button
            variant="secondary"
            size="sm"
            disabled={poll.isPending || row.status === 'COMPLETED' || row.status === 'FAILED'}
            onClick={() => poll.mutate(row.taskId)}
          >
            查询进度
          </Button>
        )
      }
    ],
    [poll]
  );

  return (
    <>
      <Alert tone="info" title="生产 API：独立 OCR 任务（提交 → 轮询 → 完成）">
        这是 ai-service 的真实异步任务接口，Provider 由 providers.ocr.type 选型（当前 demo）。接入真实 OCR 供应商时流程与本页交互不变；识别出的证件号等敏感字段在服务端脱敏后才入库/返回。
      </Alert>
      <Card padding="var(--space-5)">
        <Stack gap={14}>
          <ModuleHeader
            icon={<ScanText size={18} color="var(--accent)" />}
            title="创建 OCR 任务"
          />
          <Stack direction="row" gap={12} wrap align="flex-end">
            <Input
              label="文件对象 ID"
              placeholder="如「司机审核」中证件的 fileObjectId；演示模式任意 ID 亦可"
              value={fileObjectId}
              onChange={(event) => setFileObjectId(event.target.value)}
              style={{ minWidth: 340 }}
            />
            <Button variant="primary" disabled={!fileObjectId.trim() || submit.isPending} onClick={() => submit.mutate()}>
              {submit.isPending ? '提交中…' : '提交任务'}
            </Button>
          </Stack>
        </Stack>
      </Card>
      <DataTablePanel title="OCR 任务列表" extra={<RefreshButton query={tasksQuery} />}>
        <Table
          size="small"
          columns={columns}
          dataSource={tasks}
          rowKey="taskId"
          loading={tasksQuery.isLoading}
          pagination={false}
          scroll={{ x: 1100 }}
          locale={{ emptyText: '暂无任务 — 在上方提交一个，或在「司机审核」上传证件后自动生成；列表不自动刷新' }}
        />
      </DataTablePanel>
    </>
  );
}

/**
 * Boundary around the retained antd Table so the future FJ DataGrid swap is
 * localized to one component. Restyled with FJ tokens (hairline card + header
 * with an optional right-aligned action slot, e.g. a manual refresh button).
 */
function DataTablePanel({ title, extra, children }: { title: string; extra?: ReactNode; children: ReactNode }) {
  return (
    <Card padding="0" style={{ overflow: 'hidden' }}>
      <div className="table-panel-head">
        <Text variant="h4" as="h2">{title}</Text>
        {extra}
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

type ApiErrorBody = {
  errorCode?: string;
  message?: string;
  traceId?: string;
};

class ApiRequestError extends Error {
  readonly status: number;
  readonly errorCode?: string;
  readonly traceId?: string;
  constructor(status: number, body: ApiErrorBody) {
    super(body.message ?? `HTTP ${status}`);
    this.name = 'ApiRequestError';
    this.status = status;
    this.errorCode = body.errorCode;
    this.traceId = body.traceId;
  }
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
    let body: ApiErrorBody = {};
    try {
      body = (await response.json()) as ApiErrorBody;
    } catch {
      // non-JSON error body — keep an empty shape
    }
    throw new ApiRequestError(response.status, body);
  }
  return response.json() as Promise<T>;
}

// Surface the backend ApiError (message + errorCode + short traceId) for ops.
function describeError(error: unknown): string {
  if (error instanceof ApiRequestError) {
    const parts = [error.message];
    if (error.errorCode) parts.push(error.errorCode);
    if (error.traceId) parts.push(`trace ${error.traceId.slice(0, 8)}`);
    return parts.join(' · ');
  }
  return error instanceof Error ? error.message : String(error);
}
