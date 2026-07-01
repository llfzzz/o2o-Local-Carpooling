import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Badge, Button, Card, EmptyState, Input, List, NumberInput, Stack, Tabs, Tag, Text, useToast } from '@fj';
import { CalendarClock, CreditCard, Inbox, MapPinned, ShieldCheck, Smartphone } from 'lucide-react';
import { create } from 'zustand';

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? 'http://127.0.0.1:8080';
const SESSION_KEY = 'carpool.session';

type SessionUser = { userId: string; phone: string; roles: string[] };
type Session = { accessToken: string; refreshToken: string; user: SessionUser };

type AuthToken = {
  accessToken: string;
  refreshToken: string;
  expiresAt: string;
  refreshExpiresAt: string;
  user: SessionUser;
};

type RefreshResponse = {
  accessToken: string;
  refreshToken: string;
  expiresAt: string;
  refreshExpiresAt: string;
};

type SmsCodeResponse = { phoneMasked: string; expiresAt: string; message: string };
type DemoInboxPeek = { phoneMasked: string; maskedPreview: string | null; code: string | null; expiresAt: string | null; message: string };

type DeliveryRecord = {
  deliveryId: string;
  channel: 'SMS' | 'PUSH' | 'IN_APP';
  category: string;
  title: string;
  maskedPreview: string;
  status: 'QUEUED' | 'DELIVERED' | 'FAILED' | 'RETRYING' | 'READ';
  createdAt: string;
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

type OrderDetail = {
  orderId: string;
  tripId: string;
  riderId: string;
  seats: number;
  amount: { amount: number; currency: string };
  status: 'PENDING_PAYMENT' | 'SEAT_LOCKED' | 'TIMEOUT_CANCELLED' | 'USER_CANCELLED' | 'DRIVER_CANCELLED' | 'OPERATOR_CANCELLED' | 'COMPLETED';
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

type SessionStore = {
  session: Session | null;
  setSession: (session: Session | null) => void;
};

function loadSession(): Session | null {
  try {
    const raw = localStorage.getItem(SESSION_KEY);
    return raw ? (JSON.parse(raw) as Session) : null;
  } catch {
    return null;
  }
}

const useSession = create<SessionStore>((set) => ({
  session: loadSession(),
  setSession: (session) => {
    if (session) {
      localStorage.setItem(SESSION_KEY, JSON.stringify(session));
    } else {
      localStorage.removeItem(SESSION_KEY);
    }
    set({ session });
  }
}));

const TRIP_STATUS_LABEL: Record<TripOffer['status'], string> = {
  PUBLISHED: '可订',
  CANCELLED: '已取消',
  FINISHED: '已结束'
};

export default function App() {
  const session = useSession((state) => state.session);
  if (!session) {
    return <LoginScreen />;
  }
  return <MainApp session={session} />;
}

function LoginScreen() {
  const toast = useToast();
  const setSession = useSession((state) => state.setSession);
  const [phone, setPhone] = useState('13800000000');
  const [code, setCode] = useState('');
  const [codeSent, setCodeSent] = useState(false);
  const [demoCode, setDemoCode] = useState<string | null>(null);

  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  const sendCode = useMutation({
    mutationFn: () => rawApi<SmsCodeResponse>('/api/auth/sms-code', { method: 'POST', body: { phone } }),
    onSuccess: (response) => {
      setCodeSent(true);
      setDemoCode(null);
      toast({ title: response.message, tone: 'success' });
    },
    onError: showError
  });

  const peekDemoInbox = useMutation({
    mutationFn: () => rawApi<DemoInboxPeek>(`/api/auth/sms-code/demo-inbox?phone=${encodeURIComponent(phone)}`),
    onSuccess: (response) => {
      setDemoCode(response.code);
      toast({ title: response.code ? '已从演示收件箱取出验证码' : response.message, tone: response.code ? 'success' : 'info' });
    },
    onError: showError
  });

  const login = useMutation({
    mutationFn: () => rawApi<AuthToken>('/api/auth/login', { method: 'POST', body: { phone, code } }),
    onSuccess: (token) => {
      setSession({ accessToken: token.accessToken, refreshToken: token.refreshToken, user: token.user });
      toast({ title: '登录成功', tone: 'success' });
    },
    onError: showError
  });

  return (
    <main className="mobile-shell">
      <header className="topbar fj-glass-strong">
        <Stack direction="row" align="center" gap={10}>
          <span className="brand-dot" />
          <Stack gap={1}>
            <Text variant="h4" as="span">同城拼车</Text>
            <Text variant="eyebrow" as="span">FREE JOY · 验证码登录</Text>
          </Stack>
        </Stack>
      </header>

      <div className="tab-panel">
        <Card padding="var(--space-5)">
          <Stack gap={16}>
            <Stack direction="row" align="center" gap={8}>
              <Smartphone size={18} color="var(--accent)" />
              <Text variant="h4" as="span">手机号登录</Text>
            </Stack>
            <Input label="手机号" inputMode="numeric" value={phone} onChange={(event) => setPhone(event.target.value)} />
            <Button full variant="secondary" disabled={sendCode.isPending} onClick={() => sendCode.mutate()}>
              {sendCode.isPending ? '发送中…' : '发送验证码'}
            </Button>

            {codeSent && (
              <Alert tone="info" title="演示模式：验证码不会通过短信发送">
                验证码已写入演示收件箱。点击下方按钮取出后填入。
              </Alert>
            )}
            {codeSent && (
              <Button full variant="ghost" disabled={peekDemoInbox.isPending} onClick={() => peekDemoInbox.mutate()}>
                {peekDemoInbox.isPending ? '读取中…' : '查看演示验证码'}
              </Button>
            )}
            {demoCode && (
              <div className="status-line">
                <Badge tone="accent">演示验证码</Badge>
                <span>{demoCode}</span>
              </div>
            )}

            <Input label="验证码" inputMode="numeric" value={code} onChange={(event) => setCode(event.target.value)} />
            <Button
              full
              variant="primary"
              size="lg"
              disabled={!codeSent || !code || login.isPending}
              onClick={() => login.mutate()}
            >
              {login.isPending ? '登录中…' : '登录'}
            </Button>
          </Stack>
        </Card>
      </div>
    </main>
  );
}

function MainApp({ session }: { session: Session }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const setSession = useSession((state) => state.setSession);
  const [tab, setTab] = useState('search');
  const [origin, setOrigin] = useState('软件园三期');
  const [destination, setDestination] = useState('集美大学');
  const [city, setCity] = useState('厦门');
  const [seats, setSeats] = useState(1);
  const [selectedTripId, setSelectedTripId] = useState('');
  const [drivingLicenseFile, setDrivingLicenseFile] = useState<File | null>(null);
  const [vehicleLicenseFile, setVehicleLicenseFile] = useState<File | null>(null);
  const [verificationState, setVerificationState] = useState<VerificationState>('DRAFT');

  const userId = session.user.userId;
  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  const tripsQuery = useQuery({
    queryKey: ['trips', origin, destination, userId],
    queryFn: () => api<TripOffer[]>(`/api/trips?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}`)
  });

  const ordersQuery = useQuery({
    queryKey: ['orders', userId],
    queryFn: () => api<OrderDetail[]>('/api/orders')
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
      body: {
        driverId: userId,
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
      if (!selectedTrip) {
        throw new Error('请选择行程');
      }
      const idempotencyKey = `book-${selectedTrip.tripId}-${Date.now()}`;
      const order = await api<OrderDetail>('/api/orders', {
        method: 'POST',
        body: { tripId: selectedTrip.tripId, riderId: userId, seats, idempotencyKey }
      });
      await api('/api/payments/simulations', {
        method: 'POST',
        body: { orderId: order.orderId, idempotencyKey: `pay-${order.orderId}` }
      });
      return order;
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['trips'] });
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      toast({ title: '座位已锁定，模拟支付成功', tone: 'success' });
    },
    onError: showError
  });

  const submitVerification = useMutation({
    mutationFn: async () => {
      if (!drivingLicenseFile || !vehicleLicenseFile) {
        throw new Error('请选择驾驶证和行驶证文件');
      }
      const drivingLicense = await uploadDriverDocument(drivingLicenseFile);
      const vehicleLicense = await uploadDriverDocument(vehicleLicenseFile);
      return api<{ status: VerificationState }>('/api/drivers/verification-cases', {
        method: 'POST',
        body: { userId, drivingLicenseFileId: drivingLicense.fileObjectId, vehicleLicenseFileId: vehicleLicense.fileObjectId }
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
        <Stack direction="row" align="center" justify="space-between">
          <Stack direction="row" align="center" gap={10}>
            <span className="brand-dot" />
            <Stack gap={1}>
              <Text variant="h4" as="span">同城拼车</Text>
              <Text variant="eyebrow" as="span">{session.user.phone} · 已登录</Text>
            </Stack>
          </Stack>
          <Button variant="ghost" size="sm" onClick={() => logout(session, setSession)}>退出</Button>
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
            <span>{tripsQuery.isError ? 'Gateway 未连接' : `${routeProviderLabel} · 服务端计价`}</span>
          </div>
        </div>
      </section>

      <div className="tab-bar">
        <Tabs
          value={tab}
          onChange={setTab}
          items={[
            { id: 'search', label: '找车' },
            { id: 'verify', label: '认证' },
            { id: 'inbox', label: '收件箱' }
          ]}
        />
      </div>

      {tab === 'search' && (
        <div className="tab-panel">
          <Card padding="var(--space-5)">
            <Stack gap={16}>
              <Input label="出发" value={origin} onChange={(event) => setOrigin(event.target.value)} />
              <Input label="到达" value={destination} onChange={(event) => setDestination(event.target.value)} />
              <Input label="城市" value={city} onChange={(event) => setCity(event.target.value)} />
              <Button full variant="primary" disabled={publishTrip.isPending} onClick={() => publishTrip.mutate()}>
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
              {latestOrder && (
                <div className="status-line">
                  <Badge tone="accent">最近订单</Badge>
                  <span>{latestOrder.orderId} · {latestOrder.status}</span>
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
                disabled={!drivingLicenseFile || !vehicleLicenseFile || submitVerification.isPending}
                onClick={() => submitVerification.mutate()}
              >
                {submitVerification.isPending ? '提交中…' : '提交证件审核'}
              </Button>
            </Stack>
          </Card>
        </div>
      )}

      {tab === 'inbox' && <InboxPanel />}
    </main>
  );
}

function InboxPanel() {
  const toast = useToast();
  const queryClient = useQueryClient();
  const inboxQuery = useQuery({
    queryKey: ['demo-inbox'],
    queryFn: () => api<DeliveryRecord[]>('/api/demo/inbox'),
    refetchInterval: 5000
  });

  const reveal = useMutation({
    mutationFn: (deliveryId: string) => api<{ deliveryId: string; value: string }>(`/api/demo/inbox/${deliveryId}/reveal`, { method: 'POST' }),
    onSuccess: (response) => toast({ title: `内容：${response.value}`, tone: 'success' }),
    onError: (error) => toast({ title: describeError(error), tone: 'danger' })
  });

  const markRead = useMutation({
    mutationFn: (deliveryId: string) => api(`/api/demo/inbox/${deliveryId}/read`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['demo-inbox'] })
  });

  const items = inboxQuery.data ?? [];

  return (
    <div className="tab-panel">
      <Card padding="var(--space-5)">
        <Stack gap={12}>
          <Stack direction="row" align="center" gap={8}>
            <Inbox size={18} color="var(--accent)" />
            <Text variant="h4" as="span">演示收件箱</Text>
          </Stack>
          <Text variant="small" style={{ color: 'var(--text-subtle)' }}>
            验证码、支付与通知事件都会出现在这里。敏感内容默认遮蔽，点击「查看」显式取出。
          </Text>
        </Stack>
      </Card>

      {items.length > 0 ? (
        <List
          items={items.map((record) => ({
            id: record.deliveryId,
            icon: 'inbox',
            title: `${record.title} · ${record.status}`,
            subtitle: `${record.category} · ${record.maskedPreview}`,
            trailing: (
              <Button size="sm" variant="ghost" onClick={() => { reveal.mutate(record.deliveryId); markRead.mutate(record.deliveryId); }}>
                查看
              </Button>
            )
          }))}
        />
      ) : (
        <Card padding="var(--space-3)">
          <EmptyState icon="inbox" compact title={inboxQuery.isLoading ? '加载中' : '暂无消息'} />
        </Card>
      )}
    </div>
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
      <input type="file" accept="image/*,.pdf" onChange={(event) => onPick(event.target.files?.[0] ?? null)} />
    </label>
  );
}

async function uploadDriverDocument(file: File) {
  const presigned = await api<PresignedUpload>('/api/files/presign-upload', {
    method: 'POST',
    body: { objectName: file.name, contentType: file.type || 'application/octet-stream' }
  });
  const uploadResponse = await fetch(presigned.uploadUrl, {
    method: presigned.method,
    headers: presigned.requiredHeaders,
    body: file
  });
  if (!uploadResponse.ok) {
    throw new Error(`文件上传失败：HTTP ${uploadResponse.status}`);
  }
  return api<FileObject>(`/api/files/${presigned.fileObject.fileObjectId}/complete`, { method: 'POST' });
}

function logout(session: Session, setSession: (session: Session | null) => void) {
  void rawApi('/api/auth/logout', { method: 'POST', body: { refreshToken: session.refreshToken } }).catch(() => undefined);
  setSession(null);
}

type ApiErrorBody = { errorCode?: string; message?: string; traceId?: string };

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

async function rawApi<T>(path: string, options: { method?: string; token?: string; body?: unknown } = {}): Promise<T> {
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
  if (response.status === 204) {
    return undefined as T;
  }
  return response.json() as Promise<T>;
}

// Authenticated request: attaches the access token and transparently refreshes once on 401.
async function api<T>(path: string, options: { method?: string; body?: unknown } = {}): Promise<T> {
  const { session, setSession } = useSession.getState();
  if (!session) {
    throw new ApiRequestError(401, { errorCode: 'NO_SESSION', message: '未登录' });
  }
  try {
    return await rawApi<T>(path, { ...options, token: session.accessToken });
  } catch (error) {
    if (error instanceof ApiRequestError && error.status === 401) {
      try {
        const refreshed = await rawApi<RefreshResponse>('/api/auth/refresh', {
          method: 'POST',
          body: { refreshToken: session.refreshToken }
        });
        setSession({ accessToken: refreshed.accessToken, refreshToken: refreshed.refreshToken, user: session.user });
        return await rawApi<T>(path, { ...options, token: refreshed.accessToken });
      } catch {
        setSession(null);
      }
    }
    throw error;
  }
}

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
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}
