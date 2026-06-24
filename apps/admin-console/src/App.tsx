import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Card, Col, ConfigProvider, Layout, Row, Segmented, Space, Statistic, Table, Tag, Timeline, Typography, message } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CarFront, FileCheck2, Radar, ShieldAlert, Workflow } from 'lucide-react';

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

export default function App() {
  const [view, setView] = useState<'overview' | 'reviews' | 'orders'>('overview');
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

  const reviewColumns = useMemo<ColumnsType<VerificationCase>>(
    () => [
      { title: '司机用户', dataIndex: 'userId', width: 150 },
      {
        title: '资料',
        render: (_, row) => (
          <Space wrap>
            {Object.entries(row.uploadedFileIds).map(([name, value]) => (
              <Button key={name} size="small" icon={<FileCheck2 size={14} />} loading={fileDownloadMutation.isPending} onClick={() => fileDownloadMutation.mutate(value)}>
                {name}
              </Button>
            ))}
          </Space>
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
        render: (value: VerificationCase['status']) => <Tag color={value === 'APPROVED' ? 'green' : value === 'REJECTED' ? 'red' : 'gold'}>{value}</Tag>
      },
      {
        title: '操作',
        width: 160,
        render: (_, row) => (
          <Space>
            <Button size="small" type="primary" disabled={row.status !== 'OCR_REVIEWABLE'} loading={reviewMutation.isPending} onClick={() => reviewMutation.mutate({ caseId: row.caseId, action: 'approve' })}>通过</Button>
            <Button size="small" danger disabled={row.status !== 'OCR_REVIEWABLE'} loading={reviewMutation.isPending} onClick={() => reviewMutation.mutate({ caseId: row.caseId, action: 'reject' })}>驳回</Button>
          </Space>
        )
      }
    ],
    [fileDownloadMutation, reviewMutation, reviewMutation.isPending]
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
        render: (value: OrderRow['status']) => <Tag color={value === 'SEAT_LOCKED' ? 'green' : value === 'TIMEOUT_CANCELLED' ? 'volcano' : 'blue'}>{value}</Tag>
      }
    ],
    []
  );

  return (
    <ConfigProvider
      theme={{
        token: {
          colorPrimary: '#137a63',
          borderRadius: 6,
          fontFamily: '"Avenir Next", "PingFang SC", sans-serif'
        }
      }}
    >
      {contextHolder}
      <Layout className="console-shell">
        <Layout.Sider width={248} className="sider">
          <div className="brand">
            <Radar size={26} />
            <div>
              <strong>Carpool Ops</strong>
              <span>同城拼车运营台</span>
            </div>
          </div>
          <Segmented
            vertical
            block
            value={view}
            onChange={(value) => setView(value as typeof view)}
            options={[
              { label: '运营总览', value: 'overview' },
              { label: '司机审核', value: 'reviews' },
              { label: '订单监控', value: 'orders' }
            ]}
          />
        </Layout.Sider>
        <Layout>
          <Layout.Header className="header">
            <Typography.Title level={3}>企业级 O2O 拼车首期控制面</Typography.Title>
            <Tag color={dashboard.status === 'live-mvp' ? 'green' : 'gold'}>MVP 0.4.0</Tag>
          </Layout.Header>
          <Layout.Content className="content">
            <Alert
              type={sessionQuery.isError ? 'error' : 'info'}
              showIcon
              message={sessionQuery.isError ? 'Gateway 未连接，运营数据无法加载。' : '运营台读取真实 MVP 服务数据；支付与 OCR 仍是 Mock 适配器。'}
              className="notice"
            />

            {view === 'overview' && (
              <>
                <Row gutter={[16, 16]}>
                  <Metric title="待审核司机" value={dashboard.pendingDriverReviews} icon={<FileCheck2 />} />
                  <Metric title="今日订单" value={dashboard.todayOrders} icon={<CarFront />} />
                  <Metric title="超时待处理" value={dashboard.overduePendingPayments} icon={<ShieldAlert />} />
                  <Metric title="服务模块" value={12} icon={<Workflow />} />
                </Row>
                <Card className="table-card" title="审计时间线">
                  <Timeline
                    items={[
                      { color: 'green', children: `待审核司机 ${dashboard.pendingDriverReviews} 个` },
                      { color: 'blue', children: `锁座订单 ${dashboard.lockedOrders} 个` },
                      { color: 'orange', children: `支付超时待取消 ${dashboard.overduePendingPayments} 个` }
                    ]}
                  />
                </Card>
              </>
            )}

            {view === 'reviews' && (
              <Card className="table-card" title="司机证件审核">
                <Table columns={reviewColumns} dataSource={verifications} rowKey="caseId" loading={verificationsQuery.isLoading} pagination={false} />
              </Card>
            )}

            {view === 'orders' && (
              <Card className="table-card" title="订单状态监控">
                <Table columns={orderColumns} dataSource={orders} rowKey="orderId" loading={ordersQuery.isLoading} pagination={false} />
              </Card>
            )}
          </Layout.Content>
        </Layout>
      </Layout>
    </ConfigProvider>
  );
}

function Metric({ title, value, icon }: { title: string; value: number; icon: React.ReactNode }) {
  return (
    <Col xs={24} md={12} xl={6}>
      <Card className="metric-card">
        <div className="metric-icon">{icon}</div>
        <Statistic title={title} value={value} />
      </Card>
    </Col>
  );
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
