import { useEffect, useMemo, useState } from 'react';
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

type OrderReview = {
  reviewId: string;
  orderId: string;
  tripId: string;
  reviewerId: string;
  rating: number;
  comment: string | null;
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

const ORDER_STATUS_LABEL: Record<OrderDetail['status'], string> = {
  PENDING_PAYMENT: '待支付',
  SEAT_LOCKED: '已支付 · 座位锁定',
  TIMEOUT_CANCELLED: '支付超时已取消',
  USER_CANCELLED: '已取消（本人）',
  DRIVER_CANCELLED: '已取消（司机）',
  OPERATOR_CANCELLED: '已取消（运营）',
  COMPLETED: '已完成'
};

const ORDER_STATUS_TONE: Record<OrderDetail['status'], 'accent' | 'success' | 'danger'> = {
  PENDING_PAYMENT: 'accent',
  SEAT_LOCKED: 'success',
  COMPLETED: 'success',
  TIMEOUT_CANCELLED: 'danger',
  USER_CANCELLED: 'danger',
  DRIVER_CANCELLED: 'danger',
  OPERATOR_CANCELLED: 'danger'
};

const PAYMENT_STATUS_LABEL: Record<PaymentIntentStatus, string> = {
  REQUIRES_PAYMENT: '待支付',
  AUTHORIZED: '已授权',
  SUCCEEDED: '支付成功',
  FAILED: '支付失败',
  CANCELED: '已取消',
  EXPIRED: '已过期'
};

const IDENTITY_STATUS_LABEL: Record<IdentityStatus, string> = {
  PENDING: '认证中',
  APPROVED: '认证通过',
  REJECTED: '认证被驳回',
  TIMEOUT: '认证超时',
  RETRY_REQUIRED: '需要重试'
};

const IDENTITY_STATUS_TONE: Record<IdentityStatus, 'accent' | 'success' | 'danger'> = {
  PENDING: 'accent',
  APPROVED: 'success',
  REJECTED: 'danger',
  TIMEOUT: 'danger',
  RETRY_REQUIRED: 'danger'
};

const LIVENESS_STATUS_LABEL: Record<LivenessStatus, string> = {
  PENDING: '待检测',
  PASSED: '活体通过',
  FAILED: '活体失败',
  TIMEOUT: '活体超时',
  RETRY_REQUIRED: '需要重试'
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
  const [identityApproved, setIdentityApproved] = useState(false);

  const userId = session.user.userId;
  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  const tripsQuery = useQuery({
    queryKey: ['trips', origin, destination, userId],
    queryFn: () => api<TripOffer[]>(`/api/trips?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}`)
  });

  const ordersQuery = useQuery({
    queryKey: ['orders', userId],
    queryFn: () => api<OrderDetail[]>('/api/orders'),
    // Poll so an operator/PSP-driven signed payment callback, or a payment timeout,
    // surfaces here without a manual refresh. The order status is server-authoritative.
    refetchInterval: 5000
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

  const placeOrder = useMutation({
    mutationFn: () => {
      if (!selectedTrip) {
        throw new Error('请选择行程');
      }
      const idempotencyKey = `book-${selectedTrip.tripId}-${Date.now()}`;
      return api<OrderDetail>('/api/orders', {
        method: 'POST',
        body: { tripId: selectedTrip.tripId, riderId: userId, seats, idempotencyKey }
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['trips'] });
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      toast({ title: '已下单锁座，请在下方发起支付', tone: 'success' });
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
                disabled={!selectedTrip || selectedAvailableSeats <= 0 || placeOrder.isPending}
                onClick={() => placeOrder.mutate()}
              >
                {placeOrder.isPending ? '处理中…' : `下单锁座 ¥${bookAmount}`}
              </Button>
            </Stack>
          </Card>

          {latestOrder && <CurrentOrderCard order={latestOrder} />}
        </div>
      )}

      {tab === 'verify' && (
        <div className="tab-panel">
          <IdentityVerifyCard onApprovedChange={setIdentityApproved} />

          <Card padding="var(--space-5)">
            <Stack gap={16}>
              <Stack direction="row" align="center" gap={8}>
                <ShieldCheck size={18} color="var(--accent)" />
                <Text variant="h4" as="span">司机证件审核</Text>
              </Stack>
              {!identityApproved && (
                <Alert tone="info" title="需先完成实名认证">
                  司机证件提交前，必须先通过上方的实名认证（状态为「认证通过」）。未通过时服务端会拒绝提交。
                </Alert>
              )}
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
                disabled={!identityApproved || !drivingLicenseFile || !vehicleLicenseFile || submitVerification.isPending}
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

function OrderReviewSection({ orderId }: { orderId: string }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [rating, setRating] = useState(5);
  const [comment, setComment] = useState('');

  // The rider may not have reviewed yet: a 404 means "no review", not an error.
  const reviewQuery = useQuery({
    queryKey: ['order-review', orderId],
    queryFn: async () => {
      try {
        return await api<OrderReview>(`/api/orders/${orderId}/review`);
      } catch (error) {
        if (error instanceof ApiRequestError && error.status === 404) {
          return null;
        }
        throw error;
      }
    }
  });

  const submit = useMutation({
    mutationFn: () => api<OrderReview>(`/api/orders/${orderId}/review`, {
      method: 'POST',
      body: { rating, comment }
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['order-review', orderId] });
      toast({ title: '评价已提交，谢谢！', tone: 'success' });
    },
    onError: (error) => toast({ title: describeError(error), tone: 'danger' })
  });

  const review = reviewQuery.data;

  return (
    <Stack gap={12}>
      <Alert tone="success" title="行程已完成">给这次行程打个分吧。</Alert>
      {review ? (
        <div className="status-line">
          <Badge tone="success">已评价 {review.rating}★</Badge>
          {review.comment && <span>{review.comment}</span>}
        </div>
      ) : reviewQuery.isLoading ? (
        <Text variant="small" style={{ color: 'var(--text-subtle)' }}>加载评价…</Text>
      ) : (
        <>
          <Stack direction="row" align="center" justify="space-between">
            <Text variant="small" style={{ color: 'var(--text)' }}>评分（1-5）</Text>
            <NumberInput value={rating} min={1} max={5} onChange={(value) => setRating(Number(value))} />
          </Stack>
          <Input label="评价（可选）" value={comment} onChange={(event) => setComment(event.target.value)} />
          <Button full variant="primary" disabled={submit.isPending} onClick={() => submit.mutate()}>
            {submit.isPending ? '提交中…' : '提交评价'}
          </Button>
        </>
      )}
    </Stack>
  );
}

function CurrentOrderCard({ order }: { order: OrderDetail }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [intentId, setIntentId] = useState<string | null>(null);
  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  const createIntent = useMutation({
    mutationFn: () => api<PaymentIntent>('/api/payments/intents', {
      method: 'POST',
      body: { orderId: order.orderId, idempotencyKey: `intent-${order.orderId}` }
    }),
    onSuccess: (intent) => {
      setIntentId(intent.intentId);
      toast({ title: '已发起支付，等待签名回调', tone: 'info' });
    },
    onError: showError
  });

  // Poll the intent so its status (REQUIRES_PAYMENT → SUCCEEDED/…) reflects the callback outcome.
  const intentQuery = useQuery({
    queryKey: ['payment-intent', intentId],
    queryFn: () => api<PaymentIntent>(`/api/payments/intents/${intentId}`),
    enabled: !!intentId,
    refetchInterval: 4000
  });

  const cancelOrder = useMutation({
    mutationFn: () => api<OrderDetail>(`/api/orders/${order.orderId}/cancel`, { method: 'POST' }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      queryClient.invalidateQueries({ queryKey: ['trips'] });
      toast({ title: '订单已取消，座位已释放', tone: 'success' });
    },
    onError: showError
  });

  const intent = intentQuery.data;
  const canPay = order.status === 'PENDING_PAYMENT';
  const canCancel = order.status === 'PENDING_PAYMENT' || order.status === 'SEAT_LOCKED';

  return (
    <Card padding="var(--space-5)">
      <Stack gap={14}>
        <Stack direction="row" align="center" justify="space-between">
          <Text variant="h4" as="span">当前订单</Text>
          <Badge tone={ORDER_STATUS_TONE[order.status]}>{ORDER_STATUS_LABEL[order.status]}</Badge>
        </Stack>
        <Text variant="small" style={{ color: 'var(--text)' }}>
          {order.orderId} · ¥{Number(order.amount.amount).toFixed(2)} · {order.seats} 座
        </Text>

        {canPay && (
          <Button
            full
            variant="primary"
            size="lg"
            iconLeft={<CreditCard size={18} />}
            disabled={createIntent.isPending || !!intentId}
            onClick={() => createIntent.mutate()}
          >
            {intentId ? '支付已发起' : createIntent.isPending ? '发起中…' : '发起支付'}
          </Button>
        )}

        {intentId && (
          <div className="status-line">
            <Badge tone="accent">支付意图</Badge>
            <span>{intentId} · {intent ? PAYMENT_STATUS_LABEL[intent.status] : '查询中…'}</span>
          </div>
        )}

        {canPay && (
          <Alert tone="info" title="支付由已签名回调驱动">
            发起支付后，结果由支付供应商的签名回调触发（演示中由运营在后台控制台触发 succeeded/failed/canceled/expired）。订单状态会在此处自动刷新，前端不会自行改支付状态。
          </Alert>
        )}
        {order.status === 'PENDING_PAYMENT' && (
          <Text variant="small" style={{ color: 'var(--text-subtle)' }}>超时未支付将自动取消并释放座位。</Text>
        )}
        {order.status === 'COMPLETED' && <OrderReviewSection orderId={order.orderId} />}

        {canCancel && (
          <Button full variant="ghost" disabled={cancelOrder.isPending} onClick={() => cancelOrder.mutate()}>
            {cancelOrder.isPending ? '取消中…' : '取消订单'}
          </Button>
        )}
      </Stack>
    </Card>
  );
}

function IdentityVerifyCard({ onApprovedChange }: { onApprovedChange: (approved: boolean) => void }) {
  const toast = useToast();
  const [verificationId, setVerificationId] = useState<string | null>(null);
  const [realName, setRealName] = useState('');
  const [idNumber, setIdNumber] = useState('');
  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  const start = useMutation({
    mutationFn: () => api<IdentityVerification>('/api/identity/verifications', {
      method: 'POST',
      body: { realName, idNumber }
    }),
    onSuccess: (verification) => {
      setVerificationId(verification.verificationId);
      toast({ title: '实名认证已发起，请完成活体检测', tone: 'info' });
    },
    onError: showError
  });

  // Poll the session so the operator/provider-driven outcome (liveness + approval) surfaces here.
  const verificationQuery = useQuery({
    queryKey: ['identity-verification', verificationId],
    queryFn: () => api<IdentityVerification>(`/api/identity/verifications/${verificationId}`),
    enabled: !!verificationId,
    refetchInterval: 4000
  });

  const verification = verificationQuery.data;
  const status = verification?.status;

  useEffect(() => {
    onApprovedChange(status === 'APPROVED');
  }, [status, onApprovedChange]);

  return (
    <Card padding="var(--space-5)">
      <Stack gap={14}>
        <Stack direction="row" align="center" gap={8}>
          <ShieldCheck size={18} color="var(--accent)" />
          <Text variant="h4" as="span">实名认证 + 活体检测</Text>
        </Stack>

        {!verificationId ? (
          <>
            <Input label="真实姓名" value={realName} onChange={(event) => setRealName(event.target.value)} />
            <Input label="证件号" inputMode="numeric" value={idNumber} onChange={(event) => setIdNumber(event.target.value)} />
            <Button
              full
              variant="primary"
              size="lg"
              disabled={!realName || !idNumber || start.isPending}
              onClick={() => start.mutate()}
            >
              {start.isPending ? '发起中…' : '发起实名认证'}
            </Button>
          </>
        ) : (
          <>
            <div className="status-line">
              <Badge tone={verification ? IDENTITY_STATUS_TONE[verification.status] : 'accent'}>
                {verification ? IDENTITY_STATUS_LABEL[verification.status] : '查询中…'}
              </Badge>
              {verification && <Tag accent="neutral">{LIVENESS_STATUS_LABEL[verification.livenessStatus]}</Tag>}
            </div>
            {status === 'APPROVED' ? (
              <Alert tone="success" title="实名认证已通过">现在可以在下方提交司机证件了。</Alert>
            ) : (
              <Alert tone="info" title="等待认证结果">
                认证与活体结果由供应商回调驱动（演示中由运营在后台控制台触发活体 PASS 与会话 APPROVED）。结果会异步投递到收件箱，此处状态自动刷新。
              </Alert>
            )}
          </>
        )}
      </Stack>
    </Card>
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
