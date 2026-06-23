import { useMemo, useState } from 'react';
import { Alert, Button, Card, Col, ConfigProvider, Layout, Row, Segmented, Space, Statistic, Table, Tag, Timeline, Typography } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import { CarFront, FileCheck2, Radar, ShieldAlert, Workflow } from 'lucide-react';

type VerificationRow = {
  key: string;
  driver: string;
  phone: string;
  status: 'OCR_REVIEWABLE' | 'APPROVED' | 'REJECTED';
  confidence: number;
  files: string;
};

type OrderRow = {
  key: string;
  route: string;
  rider: string;
  status: 'PENDING_PAYMENT' | 'SEAT_LOCKED' | 'TIMEOUT_CANCELLED';
  amount: number;
  seats: number;
};

const initialVerifications: VerificationRow[] = [
  { key: 'verify-001', driver: '林师傅', phone: '13800000001', status: 'OCR_REVIEWABLE', confidence: 0.91, files: '驾驶证 / 行驶证' },
  { key: 'verify-002', driver: '陈师傅', phone: '13800000002', status: 'APPROVED', confidence: 0.96, files: '驾驶证 / 行驶证' }
];

const orderRows: OrderRow[] = [
  { key: 'order-001', route: '软件园三期 → 集美大学', rider: '乘客 A', status: 'SEAT_LOCKED', amount: 28.2, seats: 1 },
  { key: 'order-002', route: '海沧湾 → 湖里万达', rider: '乘客 B', status: 'PENDING_PAYMENT', amount: 21.6, seats: 1 },
  { key: 'order-003', route: '厦门北站 → 软件园二期', rider: '乘客 C', status: 'TIMEOUT_CANCELLED', amount: 35.4, seats: 2 }
];

export default function App() {
  const [view, setView] = useState<'overview' | 'reviews' | 'orders'>('overview');
  const [verifications, setVerifications] = useState(initialVerifications);

  const pendingCount = verifications.filter((item) => item.status === 'OCR_REVIEWABLE').length;
  const lockedOrders = orderRows.filter((item) => item.status === 'SEAT_LOCKED').length;

  const reviewColumns = useMemo<ColumnsType<VerificationRow>>(
    () => [
      { title: '司机', dataIndex: 'driver', width: 120 },
      { title: '手机号', dataIndex: 'phone', width: 150 },
      { title: '资料', dataIndex: 'files' },
      {
        title: 'OCR 置信度',
        dataIndex: 'confidence',
        render: (value: number) => `${Math.round(value * 100)}%`
      },
      {
        title: '状态',
        dataIndex: 'status',
        render: (value: VerificationRow['status']) => <Tag color={value === 'APPROVED' ? 'green' : value === 'REJECTED' ? 'red' : 'gold'}>{value}</Tag>
      },
      {
        title: '操作',
        render: (_, row) => (
          <Space>
            <Button size="small" type="primary" onClick={() => approve(row.key)}>通过</Button>
            <Button size="small" danger onClick={() => reject(row.key)}>驳回</Button>
          </Space>
        )
      }
    ],
    []
  );

  const orderColumns = useMemo<ColumnsType<OrderRow>>(
    () => [
      { title: '路线', dataIndex: 'route' },
      { title: '乘客', dataIndex: 'rider', width: 120 },
      { title: '座位', dataIndex: 'seats', width: 90 },
      {
        title: '金额',
        dataIndex: 'amount',
        width: 110,
        render: (value: number) => `¥${value.toFixed(2)}`
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

  function approve(key: string) {
    setVerifications((current) => current.map((item) => item.key === key ? { ...item, status: 'APPROVED' } : item));
  }

  function reject(key: string) {
    setVerifications((current) => current.map((item) => item.key === key ? { ...item, status: 'REJECTED' } : item));
  }

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
            <Tag color="green">MVP 0.2.0</Tag>
          </Layout.Header>
          <Layout.Content className="content">
            <Alert
              type="info"
              showIcon
              message="当前为 Mock 支付与 OCR 适配器，真实资金和实名能力保留替换边界。"
              className="notice"
            />

            {view === 'overview' && (
              <>
                <Row gutter={[16, 16]}>
                  <Metric title="待审核司机" value={pendingCount} icon={<FileCheck2 />} />
                  <Metric title="锁座订单" value={lockedOrders} icon={<CarFront />} />
                  <Metric title="风控事件" value={0} icon={<ShieldAlert />} />
                  <Metric title="服务模块" value={12} icon={<Workflow />} />
                </Row>
                <Card className="table-card" title="审计时间线">
                  <Timeline
                    items={[
                      { color: 'green', children: '司机林师傅提交证件，OCR 置信度 91%' },
                      { color: 'blue', children: '订单 order-001 完成模拟支付并锁座' },
                      { color: 'orange', children: '订单 order-003 超时取消并释放座位' }
                    ]}
                  />
                </Card>
              </>
            )}

            {view === 'reviews' && (
              <Card className="table-card" title="司机证件审核">
                <Table columns={reviewColumns} dataSource={verifications} pagination={false} />
              </Card>
            )}

            {view === 'orders' && (
              <Card className="table-card" title="订单状态监控">
                <Table columns={orderColumns} dataSource={orderRows} pagination={false} />
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
