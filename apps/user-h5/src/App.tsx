import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Badge, Button, Card, EmptyState, Input, List, NumberInput, Stack, Tabs, Tag, Text, useToast } from '@fj';
import { CalendarClock, CarFront, CreditCard, MapPinned, ShieldCheck } from 'lucide-react';
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

const TRIP_STATUS_LABEL: Record<TripOffer['status'], string> = {
  PUBLISHED: '可订',
  CANCELLED: '已取消',
  FINISHED: '已结束'
};

export default function App() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [tab, setTab] = useState('search');
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

  const showError = (error: Error) => toast({ title: describeError(error), tone: 'danger' });

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
      toast({ title: '示例行程已发布', tone: 'success' });
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
      toast({ title: '座位已锁定，模拟支付成功', tone: 'success' });
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
      toast({ title: 'OCR Mock 已识别，等待后台复核', tone: 'success' });
    },
    onError: showError
  });

  const latestOrder = useMemo(() => ordersQuery.data?.[0], [ordersQuery.data]);
  const bookAmount = selectedTrip ? (Number(selectedTrip.seatPrice.amount) * seats).toFixed(2) : '0.00';

  return (
    <main className="mobile-shell">
      <header className="topbar fj-glass-strong">
        <Stack direction="row" align="center" gap={10}>
          <span className="brand-dot" />
          <Stack gap={1}>
            <Text variant="h4" as="span">同城拼车</Text>
            <Text variant="eyebrow" as="span">FREE JOY · 服务端权威计价</Text>
          </Stack>
        </Stack>
      </header>

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

      <div className="tab-bar">
        <Tabs
          value={tab}
          onChange={setTab}
          items={[
            { id: 'search', label: '找车' },
            { id: 'verify', label: '认证' }
          ]}
        />
      </div>

      {sessionQuery.isError && (
        <div className="panel-pad">
          <Alert tone="danger" title="Gateway 未连接">
            未能加载登录态与行程数据，请确认本地 Gateway/服务已启动。
          </Alert>
        </div>
      )}

      {tab === 'search' && (
        <div className="tab-panel">
          <Card padding="var(--space-5)">
            <Stack gap={16}>
              <Input label="出发" value={origin} onChange={(e) => setOrigin(e.target.value)} />
              <Input label="到达" value={destination} onChange={(e) => setDestination(e.target.value)} />
              <Input label="城市" value={city} onChange={(e) => setCity(e.target.value)} />
              <Button full variant="primary" disabled={!token || publishTrip.isPending} onClick={() => publishTrip.mutate()}>
                {publishTrip.isPending ? '发布中…' : '发布示例行程'}
              </Button>
            </Stack>
          </Card>

          {trips.length > 0 ? (
            <List
              items={trips.map((trip) => {
                const seatsLeft = trip.inventory.totalSeats - trip.inventory.lockedSeats;
                const isSelected = selectedTrip?.tripId === trip.tripId;
                return {
                  id: trip.tripId,
                  icon: 'car-front',
                  title: `${trip.driverId} · ¥${Number(trip.seatPrice.amount).toFixed(2)}`,
                  subtitle: `${formatDeparture(trip.departureAt)} · ${(trip.route.distanceMeters / 1000).toFixed(1)}km · 剩余 ${seatsLeft} 座`,
                  trailing: <Tag accent={isSelected ? 'coral' : 'neutral'}>{TRIP_STATUS_LABEL[trip.status]}</Tag>,
                  onClick: () => setSelectedTripId(trip.tripId)
                };
              })}
            />
          ) : (
            <Card padding="var(--space-3)">
              <EmptyState
                icon="car-front"
                compact
                title={tripsQuery.isLoading ? '正在加载行程' : '暂无可订行程'}
                description={tripsQuery.isLoading ? undefined : '可先发布一条示例行程再来订座。'}
              />
            </Card>
          )}

          <Card padding="var(--space-5)">
            <Stack gap={14}>
              <Stack direction="row" align="center" gap={8}>
                <CalendarClock size={18} color="var(--accent)" />
                <Text variant="h4" as="span">订座确认</Text>
              </Stack>
              <Text variant="small">
                {selectedTrip ? `${selectedTrip.originText} → ${selectedTrip.destinationText}` : '请选择或发布行程'}
              </Text>
              <Stack direction="row" align="center" justify="space-between">
                <Text variant="small" style={{ color: 'var(--text)' }}>座位数</Text>
                <NumberInput
                  value={seats}
                  min={1}
                  max={Math.max(1, selectedAvailableSeats)}
                  onChange={(value) => setSeats(Number(value))}
                />
              </Stack>
              <Button
                full
                variant="primary"
                size="lg"
                iconLeft={<CreditCard size={18} />}
                disabled={!selectedTrip || selectedAvailableSeats <= 0 || bookSeat.isPending}
                onClick={() => bookSeat.mutate()}
              >
                {bookSeat.isPending ? '处理中…' : `模拟支付 ¥${bookAmount}`}
              </Button>
              {(bookedOrderId || latestOrder) && (
                <div className="status-line">
                  <Badge tone="accent">最近订单</Badge>
                  <span>{bookedOrderId || latestOrder?.orderId} · {latestOrder?.status ?? 'SEAT_LOCKED'}</span>
                </div>
              )}
            </Stack>
          </Card>
        </div>
      )}

      {tab === 'verify' && (
        <div className="tab-panel">
          <Card padding="var(--space-5)">
            <Stack gap={16}>
              <Stack direction="row" align="center" gap={8}>
                <ShieldCheck size={18} color="var(--accent)" />
                <Text variant="h4" as="span">司机证件审核</Text>
              </Stack>
              <DocPicker label="驾驶证" file={drivingLicenseFile} onPick={setDrivingLicenseFile} />
              <DocPicker label="行驶证" file={vehicleLicenseFile} onPick={setVehicleLicenseFile} />
              <div className="ocr-box">
                <Text variant="small" style={{ color: 'var(--text)' }}>OCR 状态</Text>
                <Tag accent={verificationState === 'DRAFT' ? 'neutral' : 'coral'}>{verificationState}</Tag>
              </div>
              <Button
                full
                variant="primary"
                size="lg"
                disabled={!token || !drivingLicenseFile || !vehicleLicenseFile || submitVerification.isPending}
                onClick={() => submitVerification.mutate()}
              >
                {submitVerification.isPending ? '提交中…' : '提交证件审核'}
              </Button>
            </Stack>
          </Card>
        </div>
      )}
    </main>
  );
}

function DocPicker({ label, file, onPick }: { label: string; file: File | null; onPick: (file: File | null) => void }) {
  return (
    <label className="doc-picker">
      <Stack gap={2}>
        <Text variant="small" style={{ color: 'var(--text)' }}>{label}</Text>
        <Text variant="small" style={{ color: 'var(--text-subtle)' }}>{file?.name ?? '未选择文件'}</Text>
      </Stack>
      <span className="doc-picker-action">选择文件</span>
      <input
        type="file"
        accept="image/*,.pdf"
        onChange={(event) => onPick(event.target.files?.[0] ?? null)}
      />
    </label>
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

function formatDeparture(value: string) {
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}
