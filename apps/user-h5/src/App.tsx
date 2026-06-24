import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Button, Card, Form, Input, List, NavBar, Space, Stepper, Tabs, Tag, Toast } from 'antd-mobile';
import { CalendarClock, CarFront, CreditCard, MapPinned, ShieldCheck, UploadCloud } from 'lucide-react';
import { create } from 'zustand';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080';

type AuthToken = {
  accessToken: string;
  user: {
    userId: string;
    phone: string;
    roles: string[];
  };
};

type TripOffer = {
  tripId: string;
  driverId: string;
  originText: string;
  destinationText: string;
  departureAt: string;
  route: {
    routeId: string;
    distanceMeters: number;
    durationSeconds: number;
    providerTrace: string;
  };
  inventory: {
    totalSeats: number;
    lockedSeats: number;
  };
  seatPrice: {
    amount: number;
    currency: string;
  };
  status: 'PUBLISHED' | 'CANCELLED' | 'FINISHED';
};

type OrderDetail = {
  orderId: string;
  tripId: string;
  riderId: string;
  seats: number;
  amount: {
    amount: number;
    currency: string;
  };
  status: 'PENDING_PAYMENT' | 'SEAT_LOCKED' | 'TIMEOUT_CANCELLED' | 'USER_CANCELLED' | 'DRIVER_CANCELLED' | 'COMPLETED';
  createdAt: string;
};

type FileObject = {
  fileObjectId: string;
  ownerId: string;
  bucket: string;
  objectName: string;
  contentType: string;
  privateObject: boolean;
  createdAt: string;
};

type PresignedUpload = {
  fileObject: FileObject;
  uploadUrl: string;
  method: 'PUT';
  requiredHeaders: Record<string, string>;
  expiresAt: string;
};

type VerificationState = 'DRAFT' | 'OCR_REVIEWABLE' | 'APPROVED';

type BookingStore = {
  selectedTripId: string;
  bookedOrderId: string;
  verificationState: VerificationState;
  setSelectedTripId: (tripId: string) => void;
  setBookedOrderId: (orderId: string) => void;
  setVerificationState: (state: VerificationState) => void;
};

const useBookingStore = create<BookingStore>((set) => ({
  selectedTripId: '',
  bookedOrderId: '',
  verificationState: 'DRAFT',
  setSelectedTripId: (tripId) => set({ selectedTripId: tripId }),
  setBookedOrderId: (orderId) => set({ bookedOrderId: orderId }),
  setVerificationState: (state) => set({ verificationState: state })
}));

export default function App() {
  const queryClient = useQueryClient();
  const [origin, setOrigin] = useState('软件园三期');
  const [destination, setDestination] = useState('集美大学');
  const [city, setCity] = useState('厦门');
  const [seats, setSeats] = useState(1);
  const [drivingLicenseFile, setDrivingLicenseFile] = useState<File | null>(null);
  const [vehicleLicenseFile, setVehicleLicenseFile] = useState<File | null>(null);
  const selectedTripId = useBookingStore((state) => state.selectedTripId);
  const setSelectedTripId = useBookingStore((state) => state.setSelectedTripId);
  const setBookedOrderId = useBookingStore((state) => state.setBookedOrderId);
  const bookedOrderId = useBookingStore((state) => state.bookedOrderId);
  const verificationState = useBookingStore((state) => state.verificationState);
  const setVerificationState = useBookingStore((state) => state.setVerificationState);

  const sessionQuery = useQuery({
    queryKey: ['mock-rider-login'],
    queryFn: () => api<AuthToken>('/api/auth/login', {
      method: 'POST',
      body: { phone: '13800000000', code: 'MOCK-123456', roles: ['RIDER', 'DRIVER'] }
    }),
    retry: 1
  });
  const token = sessionQuery.data?.accessToken;

  const tripsQuery = useQuery({
    queryKey: ['trips', origin, destination, token],
    queryFn: () => api<TripOffer[]>(`/api/trips?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}`, { token }),
    enabled: Boolean(token)
  });

  const ordersQuery = useQuery({
    queryKey: ['orders', token],
    queryFn: () => api<OrderDetail[]>('/api/orders', { token }),
    enabled: Boolean(token)
  });

  const trips = tripsQuery.data ?? [];
  const selectedTrip = trips.find((trip) => trip.tripId === selectedTripId) ?? trips[0];
  const selectedAvailableSeats = selectedTrip ? selectedTrip.inventory.totalSeats - selectedTrip.inventory.lockedSeats : 0;
  const routeProviderLabel = !selectedTrip
    ? '等待路线快照'
    : selectedTrip.route.providerTrace === 'amap-v5' ? '高德路线快照' : '本地 Mock 路线快照';

  const publishTrip = useMutation({
    mutationFn: () => api<TripOffer>('/api/trips', {
      method: 'POST',
      token,
      body: {
        driverId: sessionQuery.data?.user.userId,
        originText: origin,
        destinationText: destination,
        city,
        departureAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
        totalSeats: 3
      }
    }),
    onSuccess: (trip) => {
      setSelectedTripId(trip.tripId);
      queryClient.invalidateQueries({ queryKey: ['trips'] });
      Toast.show({ content: '示例行程已发布' });
    },
    onError: showError
  });

  const bookSeat = useMutation({
    mutationFn: async () => {
      if (!selectedTrip || !sessionQuery.data) {
        throw new Error('请选择行程');
      }
      const idempotencyKey = `book-${selectedTrip.tripId}-${Date.now()}`;
      const order = await api<OrderDetail>('/api/orders', {
        method: 'POST',
        token,
        body: {
          tripId: selectedTrip.tripId,
          riderId: sessionQuery.data.user.userId,
          seats,
          idempotencyKey
        }
      });
      await api('/api/payments/simulations', {
        method: 'POST',
        token,
        body: {
          orderId: order.orderId,
          idempotencyKey: `pay-${order.orderId}`
        }
      });
      return order;
    },
    onSuccess: (order) => {
      setBookedOrderId(order.orderId);
      queryClient.invalidateQueries({ queryKey: ['trips'] });
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      Toast.show({ content: '座位已锁定，模拟支付成功' });
    },
    onError: showError
  });

  const submitVerification = useMutation({
    mutationFn: async () => {
      if (!sessionQuery.data) {
        throw new Error('登录未完成');
      }
      if (!drivingLicenseFile || !vehicleLicenseFile) {
        throw new Error('请选择驾驶证和行驶证文件');
      }
      const drivingLicense = await uploadDriverDocument(drivingLicenseFile, token);
      const vehicleLicense = await uploadDriverDocument(vehicleLicenseFile, token);
      return api<{ status: VerificationState }>('/api/drivers/verification-cases', {
        method: 'POST',
        token,
        body: {
          userId: sessionQuery.data.user.userId,
          drivingLicenseFileId: drivingLicense.fileObjectId,
          vehicleLicenseFileId: vehicleLicense.fileObjectId
        }
      });
    },
    onSuccess: (verification) => {
      setVerificationState(verification.status);
      Toast.show({ content: 'OCR Mock 已识别，等待后台复核' });
    },
    onError: showError
  });

  const latestOrder = useMemo(() => ordersQuery.data?.[0], [ordersQuery.data]);

  return (
    <main className="mobile-shell">
      <NavBar backArrow={false} className="topbar">
        同城拼车
      </NavBar>

      <section className="map-band">
        <div className="map-grid">
          <span className="node start" />
          <span className="route-line" />
          <span className="node end" />
        </div>
        <div className="map-copy">
          <MapPinned size={20} />
          <div>
            <strong>{origin} 至 {destination}</strong>
            <span>{sessionQuery.isError ? 'Gateway 未连接' : `${routeProviderLabel} · 服务端计价`}</span>
          </div>
        </div>
      </section>

      <Tabs defaultActiveKey="search" className="task-tabs">
        <Tabs.Tab title="找车" key="search">
          <Card className="panel">
            <Form layout="horizontal" footer={null}>
              <Form.Item label="出发">
                <Input value={origin} onChange={setOrigin} clearable />
              </Form.Item>
              <Form.Item label="到达">
                <Input value={destination} onChange={setDestination} clearable />
              </Form.Item>
              <Form.Item label="城市">
                <Input value={city} onChange={setCity} clearable />
              </Form.Item>
            </Form>
            <Button block color="success" loading={publishTrip.isPending} disabled={!token} onClick={() => publishTrip.mutate()}>
              发布示例行程
            </Button>
          </Card>

          <List className="trip-list">
            {trips.map((trip) => {
              const seatsLeft = trip.inventory.totalSeats - trip.inventory.lockedSeats;
              return (
                <List.Item
                  key={trip.tripId}
                  onClick={() => setSelectedTripId(trip.tripId)}
                  prefix={<CarFront size={22} />}
                  description={`${formatDeparture(trip.departureAt)} · ${(trip.route.distanceMeters / 1000).toFixed(1)}km · 剩余 ${seatsLeft} 座`}
                  extra={<Tag color={selectedTrip?.tripId === trip.tripId ? 'success' : 'primary'}>{trip.status}</Tag>}
                >
                  {trip.driverId} · ¥{Number(trip.seatPrice.amount).toFixed(2)}
                </List.Item>
              );
            })}
            {!tripsQuery.isLoading && trips.length === 0 && <List.Item description="可先发布示例行程">暂无可订行程</List.Item>}
          </List>

          <Card className="panel booking-card">
            <div className="section-title">
              <CalendarClock size={18} />
              <span>订座确认</span>
            </div>
            <p>{selectedTrip ? `${selectedTrip.originText} → ${selectedTrip.destinationText}` : '请选择或发布行程'}</p>
            <Space justify="between" block align="center">
              <span>座位数</span>
              <Stepper min={1} max={Math.max(1, selectedAvailableSeats)} value={seats} onChange={(value) => setSeats(Number(value))} />
            </Space>
            <Button
              block
              color="primary"
              size="large"
              loading={bookSeat.isPending}
              disabled={!selectedTrip || selectedAvailableSeats <= 0}
              onClick={() => bookSeat.mutate()}
            >
              <CreditCard size={18} /> 模拟支付 ¥{selectedTrip ? (Number(selectedTrip.seatPrice.amount) * seats).toFixed(2) : '0.00'}
            </Button>
            {(bookedOrderId || latestOrder) && (
              <div className="status-line">
                最近订单：{bookedOrderId || latestOrder?.orderId} · {latestOrder?.status ?? 'SEAT_LOCKED'}
              </div>
            )}
          </Card>
        </Tabs.Tab>

        <Tabs.Tab title="认证" key="verify">
          <Card className="panel verify-card">
            <div className="section-title">
              <ShieldCheck size={18} />
              <span>司机证件审核</span>
            </div>
            <List>
              <List.Item prefix={<UploadCloud size={20} />} description={drivingLicenseFile?.name ?? '未选择'}>
                <label className="file-picker">
                  驾驶证
                  <input type="file" accept="image/*,.pdf" onChange={(event) => setDrivingLicenseFile(event.target.files?.[0] ?? null)} />
                </label>
              </List.Item>
              <List.Item prefix={<UploadCloud size={20} />} description={vehicleLicenseFile?.name ?? '未选择'}>
                <label className="file-picker">
                  行驶证
                  <input type="file" accept="image/*,.pdf" onChange={(event) => setVehicleLicenseFile(event.target.files?.[0] ?? null)} />
                </label>
              </List.Item>
            </List>
            <div className="ocr-box">
              <strong>OCR 状态</strong>
              <Tag color={verificationState === 'DRAFT' ? 'default' : 'warning'}>{verificationState}</Tag>
            </div>
            <Button block color="success" size="large" loading={submitVerification.isPending} disabled={!token || !drivingLicenseFile || !vehicleLicenseFile} onClick={() => submitVerification.mutate()}>
              提交证件审核
            </Button>
          </Card>
        </Tabs.Tab>
      </Tabs>
    </main>
  );
}

async function uploadDriverDocument(file: File, token?: string) {
  const presigned = await api<PresignedUpload>('/api/files/presign-upload', {
    method: 'POST',
    token,
    body: {
      objectName: file.name,
      contentType: file.type || 'application/octet-stream'
    }
  });
  const uploadResponse = await fetch(presigned.uploadUrl, {
    method: presigned.method,
    headers: presigned.requiredHeaders,
    body: file
  });
  if (!uploadResponse.ok) {
    throw new Error(`文件上传失败：HTTP ${uploadResponse.status}`);
  }
  return api<FileObject>(`/api/files/${presigned.fileObject.fileObjectId}/complete`, {
    method: 'POST',
    token
  });
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
    const message = await errorMessage(response);
    throw new Error(message);
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

function showError(error: Error) {
  Toast.show({ content: error.message });
}

function formatDeparture(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}
