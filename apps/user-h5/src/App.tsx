import { useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useIsFetching, useIsMutating, useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Badge, Button, EmptyState, Input, NumberInput, SegmentedControl, Tag, Text, useToast } from '@fj';
import {
  Bell,
  Camera,
  Check,
  ChevronLeft,
  Compass,
  CreditCard,
  MapPin,
  MessageCircle,
  MessageSquare,
  Pencil,
  Route as RouteIcon,
  ShieldCheck,
  Sparkles,
  UserRound
} from 'lucide-react';
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

type MainTab = 'home' | 'trips' | 'inbox' | 'profile';

export default function App() {
  const session = useSession((state) => state.session);
  return (
    <>
      <GlobalActivityBar />
      {session ? <MainApp session={session} /> : <LoginScreen />}
    </>
  );
}

/**
 * Thin top progress bar while a user action or first load is in flight, so slow requests never
 * look frozen. Background poll refetches are excluded to avoid a permanently blinking bar.
 */
function GlobalActivityBar() {
  const loading = useIsFetching({ predicate: (query) => query.state.status === 'pending' });
  const busy = loading + useIsMutating() > 0;
  return <div className={`global-activity${busy ? ' on' : ''}`} aria-hidden />;
}

/** A1 · 登录 — hero copy + phone/code, code fetched from the demo inbox by explicit action. */
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
    <main className="mobile-shell login-screen">
      <div className="login-body">
        <div className="brand-row">
          <span className="brand-dot" />
          <span className="brand-name">同城拼车</span>
        </div>

        <div className="login-hero">
          <Text variant="eyebrow" as="div">FREE JOY · 验证码登录</Text>
          <h1 className="login-headline">顺路的人，<br />一起走。</h1>
          <p className="login-sub">同城通勤拼车。输入手机号，用验证码快速登录。</p>
        </div>

        <div className="login-spacer" />

        <Input label="手机号" inputMode="numeric" value={phone} onChange={(event) => setPhone(event.target.value)} />
        <div className="code-row">
          <div className="code-row-input">
            <Input label="验证码" inputMode="numeric" placeholder="6 位验证码" value={code} onChange={(event) => setCode(event.target.value)} />
          </div>
          <Button variant="secondary" disabled={sendCode.isPending} onClick={() => sendCode.mutate()}>
            {sendCode.isPending ? '发送中…' : '获取验证码'}
          </Button>
        </div>

        <Button
          full
          variant="primary"
          size="lg"
          disabled={!codeSent || !code || login.isPending}
          onClick={() => login.mutate()}
        >
          {login.isPending ? '登录中…' : '登录'}
        </Button>

        <div className="login-demo-row">
          <Tag accent="bloom">演示</Tag>
          <span>验证码会写入演示收件箱</span>
          {codeSent && (
            <Button variant="ghost" size="sm" disabled={peekDemoInbox.isPending} onClick={() => peekDemoInbox.mutate()}>
              {peekDemoInbox.isPending ? '读取中…' : '查看演示验证码'}
            </Button>
          )}
        </div>
        {demoCode && (
          <div className="status-line" style={{ justifyContent: 'center' }}>
            <Badge tone="accent">演示验证码</Badge>
            <span className="mono">{demoCode}</span>
          </div>
        )}

        <p className="login-terms">登录即代表同意《服务协议》与《隐私政策》</p>
      </div>
    </main>
  );
}

function MainApp({ session }: { session: Session }) {
  const [tab, setTab] = useState<MainTab>('home');
  const [bookingTrip, setBookingTrip] = useState<TripOffer | null>(null);

  // Unread dot on the 消息 tab; the list itself is fetched by the inbox screen.
  const inboxQuery = useQuery({
    queryKey: ['demo-inbox'],
    queryFn: () => api<DeliveryRecord[]>('/api/demo/inbox'),
    refetchInterval: 5000
  });
  const unread = (inboxQuery.data ?? []).some((record) => record.status !== 'READ');

  if (bookingTrip) {
    return (
      <main className="mobile-shell">
        <BookingScreen
          trip={bookingTrip}
          onBack={() => setBookingTrip(null)}
          onBooked={() => {
            setBookingTrip(null);
            setTab('trips');
          }}
        />
      </main>
    );
  }

  return (
    <main className="mobile-shell">
      {tab === 'home' && <HomeScreen session={session} onBook={setBookingTrip} />}
      {tab === 'trips' && <TripsScreen />}
      {tab === 'inbox' && <InboxScreen records={inboxQuery.data ?? []} loading={inboxQuery.isLoading} />}
      {tab === 'profile' && <ProfileScreen session={session} />}

      <nav className="bottom-nav fj-glass-strong">
        <button className={`bottom-nav-item${tab === 'home' ? ' active' : ''}`} onClick={() => setTab('home')}>
          <Compass size={21} />
          <span>首页</span>
        </button>
        <button className={`bottom-nav-item${tab === 'trips' ? ' active' : ''}`} onClick={() => setTab('trips')}>
          <RouteIcon size={21} />
          <span>行程</span>
        </button>
        <button className={`bottom-nav-item${tab === 'inbox' ? ' active' : ''}`} onClick={() => setTab('inbox')}>
          {unread && <span className="nav-dot" />}
          <MessageCircle size={21} />
          <span>消息</span>
        </button>
        <button className={`bottom-nav-item${tab === 'profile' ? ' active' : ''}`} onClick={() => setTab('profile')}>
          <UserRound size={21} />
          <span>我的</span>
        </button>
      </nav>
    </main>
  );
}

/** A2 · 首页 / 找车 — map hero + route rail card + 顺路车主 list. Tapping a trip opens booking. */
function HomeScreen({ session, onBook }: { session: Session; onBook: (trip: TripOffer) => void }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [origin, setOrigin] = useState('软件园三期');
  const [destination, setDestination] = useState('集美大学');
  const [city, setCity] = useState('厦门');

  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  const tripsQuery = useQuery({
    queryKey: ['trips', origin, destination, session.user.userId],
    queryFn: () => api<TripOffer[]>(`/api/trips?origin=${encodeURIComponent(origin)}&destination=${encodeURIComponent(destination)}`)
  });
  const trips = tripsQuery.data ?? [];

  const publishTrip = useMutation({
    mutationFn: () => api<TripOffer>('/api/trips', {
      method: 'POST',
      body: {
        driverId: session.user.userId,
        originText: origin,
        destinationText: destination,
        city,
        departureAt: new Date(Date.now() + 60 * 60 * 1000).toISOString(),
        totalSeats: 3
      }
    }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['trips'] });
      toast({ title: '示例行程已发布', tone: 'success' });
    },
    onError: showError
  });

  return (
    <div className="screen">
      <header className="home-header">
        <div className="city-chip">
          <MapPin size={16} color="var(--accent)" />
          <input
            className="city-input"
            value={city}
            onChange={(event) => setCity(event.target.value)}
            aria-label="城市"
          />
        </div>
        <span className="avatar avatar-sm" title={session.user.phone}>{avatarInitial(session.user.phone)}</span>
      </header>

      <div className="screen-body">
        <section className="hero-band">
          <span className="hero-node hero-node-start" />
          <span className="hero-node hero-node-end" />
          <span className="hero-route" />
          <div className="hero-pill fj-glass-strong">
            <span className="live-dot" />
            <span>
              {tripsQuery.isError
                ? 'Gateway 未连接'
                : `${origin} · ${trips.length} 位车主顺路`}
            </span>
          </div>
        </section>

        <section className="route-card">
          <div className="route-rail">
            <span className="rail-dot rail-dot-start" />
            <span className="rail-line" />
            <span className="rail-dot rail-dot-end" />
          </div>
          <div className="route-fields">
            <label className="route-field">
              <span>出发</span>
              <input value={origin} onChange={(event) => setOrigin(event.target.value)} />
            </label>
            <div className="route-divider" />
            <label className="route-field">
              <span>到达</span>
              <input value={destination} onChange={(event) => setDestination(event.target.value)} />
            </label>
          </div>
        </section>

        <div className="list-head">
          <span className="list-title">顺路车主</span>
          <Button variant="ghost" size="sm" disabled={publishTrip.isPending} onClick={() => publishTrip.mutate()}>
            {publishTrip.isPending ? '发布中…' : '+ 发布示例行程'}
          </Button>
        </div>

        {tripsQuery.isLoading ? (
          <div className="skeleton-stack">
            <div className="skeleton-card" />
            <div className="skeleton-card" />
            <div className="skeleton-card" />
          </div>
        ) : trips.length > 0 ? (
          <div className="trip-list">
            {trips.map((trip) => {
              const seatsLeft = trip.inventory.totalSeats - trip.inventory.lockedSeats;
              const bookable = trip.status === 'PUBLISHED' && seatsLeft > 0;
              return (
                <button
                  key={trip.tripId}
                  className="trip-card"
                  disabled={!bookable}
                  onClick={() => onBook(trip)}
                >
                  <div className="trip-card-top">
                    <div className="trip-driver">
                      <span className="avatar avatar-sm">{avatarInitial(trip.driverId)}</span>
                      <div className="trip-driver-meta">
                        <strong>{shortId(trip.driverId)}</strong>
                        <span>{routeProviderLabel(trip)} · {TRIP_STATUS_LABEL[trip.status]}</span>
                      </div>
                    </div>
                    <div className="trip-price">
                      <span className={`trip-price-num${bookable ? ' accent' : ''}`}>¥{Number(trip.seatPrice.amount).toFixed(0)}</span>
                      <span className="trip-price-unit">/座</span>
                    </div>
                  </div>
                  <div className="trip-card-foot">
                    <strong>{formatDeparture(trip.departureAt)} 出发</strong>
                    <span>·</span>
                    <span>{(trip.route.distanceMeters / 1000).toFixed(1)}km</span>
                    <span>·</span>
                    <span>剩 {seatsLeft} 座</span>
                  </div>
                </button>
              );
            })}
          </div>
        ) : (
          <div className="empty-card">
            <EmptyState icon="car-front" compact title="暂无可订行程" description="可先发布一条示例行程再来订座。" />
          </div>
        )}
      </div>
    </div>
  );
}

/** A3 · 订座 + 支付 — route/driver summary, seat stepper, price breakdown, sticky pay bar. */
function BookingScreen({ trip, onBack, onBooked }: { trip: TripOffer; onBack: () => void; onBooked: () => void }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [seats, setSeats] = useState(1);
  const seatsLeft = trip.inventory.totalSeats - trip.inventory.lockedSeats;
  const total = (Number(trip.seatPrice.amount) * seats).toFixed(2);
  const arrivalAt = new Date(new Date(trip.departureAt).getTime() + trip.route.durationSeconds * 1000);

  // Places the order: the server locks seats and the order enters PENDING_PAYMENT. Payment is
  // initiated from the order card (行程页) and stays callback-driven end to end.
  const confirm = useMutation({
    mutationFn: () => {
      const idempotencyKey = `book-${trip.tripId}-${Date.now()}`;
      return api<OrderDetail>('/api/orders', {
        method: 'POST',
        body: { tripId: trip.tripId, riderId: useSession.getState().session?.user.userId, seats, idempotencyKey }
      });
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['trips'] });
      queryClient.invalidateQueries({ queryKey: ['orders'] });
      toast({ title: '已下单锁座，请在行程页发起支付', tone: 'success' });
      onBooked();
    },
    onError: (error) => toast({ title: describeError(error), tone: 'danger' })
  });

  return (
    <div className="screen booking-screen">
      <header className="page-header">
        <button className="back-btn" onClick={onBack} aria-label="返回">
          <ChevronLeft size={22} />
        </button>
        <span className="page-title">确认订座</span>
      </header>

      <div className="screen-body">
        <section className="panel">
          <div className="route-summary">
            <div className="route-rail">
              <span className="rail-dot rail-dot-start" />
              <span className="rail-line" />
              <span className="rail-dot rail-dot-end" />
            </div>
            <div className="route-summary-stops">
              <div className="route-stop">
                <div>
                  <strong>{trip.originText}</strong>
                  <span>集合点</span>
                </div>
                <span className="stop-time accent">{formatClock(trip.departureAt)}</span>
              </div>
              <div className="route-stop">
                <div>
                  <strong>{trip.destinationText}</strong>
                  <span>{(trip.route.distanceMeters / 1000).toFixed(1)}km</span>
                </div>
                <span className="stop-time">{formatClock(arrivalAt.toISOString())}</span>
              </div>
            </div>
          </div>
          <div className="panel-divider" />
          <div className="driver-row">
            <span className="avatar">{avatarInitial(trip.driverId)}</span>
            <div className="driver-meta">
              <strong>{shortId(trip.driverId)}</strong>
              <span>{routeProviderLabel(trip)}</span>
            </div>
            <Tag accent="coral" dot>{TRIP_STATUS_LABEL[trip.status]}</Tag>
          </div>
        </section>

        <section className="panel price-panel">
          <div className="price-row">
            <span>座位数</span>
            <div className="seat-stepper">
              <button onClick={() => setSeats((n) => Math.max(1, n - 1))} disabled={seats <= 1} aria-label="减少座位">−</button>
              <strong>{seats}</strong>
              <button className="plus" onClick={() => setSeats((n) => Math.min(Math.max(1, seatsLeft), n + 1))} disabled={seats >= seatsLeft} aria-label="增加座位">+</button>
            </div>
          </div>
          <div className="price-row muted">
            <span>座位单价 × {seats}</span>
            <span className="strong">¥{total}</span>
          </div>
          <div className="price-row muted">
            <span>平台服务费</span>
            <span className="strong">¥0.00</span>
          </div>
          <div className="panel-divider" />
          <div className="price-row total">
            <span>合计</span>
            <span className="price-total">¥{total}</span>
          </div>
        </section>

        <div className="safety-note">
          <ShieldCheck size={15} />
          <span>支付由已签名回调驱动，超时未支付自动取消并释放座位。</span>
        </div>
      </div>

      <div className="sticky-bar">
        <Button
          full
          variant="primary"
          size="lg"
          iconLeft={<CreditCard size={18} />}
          disabled={seatsLeft <= 0 || confirm.isPending}
          onClick={() => confirm.mutate()}
        >
          {confirm.isPending ? '处理中…' : `下单锁座 ¥${total}`}
        </Button>
      </div>
    </div>
  );
}

/** A5 · 我的行程 — segmented 进行中/历史, per-order status timeline, payment & cancel actions. */
function TripsScreen() {
  const [segment, setSegment] = useState('ongoing');

  const ordersQuery = useQuery({
    queryKey: ['orders'],
    queryFn: () => api<OrderDetail[]>('/api/orders'),
    // Poll so an operator/PSP-driven signed payment callback, or a payment timeout,
    // surfaces here without a manual refresh. The order status is server-authoritative.
    refetchInterval: 5000
  });

  const orders = ordersQuery.data ?? [];
  const ongoing = orders.filter((order) => order.status === 'PENDING_PAYMENT' || order.status === 'SEAT_LOCKED');
  const history = orders.filter((order) => order.status !== 'PENDING_PAYMENT' && order.status !== 'SEAT_LOCKED');
  const visible = segment === 'ongoing' ? ongoing : history;

  return (
    <div className="screen">
      <header className="screen-header">
        <span className="page-title">我的行程</span>
        <SegmentedControl
          full
          value={segment}
          onChange={setSegment}
          options={[
            { value: 'ongoing', label: `进行中${ongoing.length ? ` · ${ongoing.length}` : ''}` },
            { value: 'history', label: '历史' }
          ]}
        />
      </header>

      <div className="screen-body">
        {ordersQuery.isLoading ? (
          <div className="skeleton-stack">
            <div className="skeleton-card tall" />
          </div>
        ) : visible.length > 0 ? (
          visible.map((order) => <OrderCard key={order.orderId} order={order} />)
        ) : (
          <div className="empty-card">
            <EmptyState
              icon="route"
              compact
              title={segment === 'ongoing' ? '暂无进行中的行程' : '暂无历史行程'}
              description={segment === 'ongoing' ? '在首页选择一位顺路车主开始订座。' : undefined}
            />
          </div>
        )}
      </div>
    </div>
  );
}

/** One order: route rail + status timeline + callback-driven payment actions. */
function OrderCard({ order }: { order: OrderDetail }) {
  const toast = useToast();
  const queryClient = useQueryClient();
  const [intentId, setIntentId] = useState<string | null>(null);
  const showError = (error: unknown) => toast({ title: describeError(error), tone: 'danger' });

  // The trip snapshot gives the route text for the rail; orders only carry tripId.
  const tripQuery = useQuery({
    queryKey: ['trip', order.tripId],
    queryFn: () => api<TripOffer>(`/api/trips/${order.tripId}`),
    staleTime: 5 * 60 * 1000,
    retry: 1
  });
  const trip = tripQuery.data;

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
  const cancelled = order.status === 'TIMEOUT_CANCELLED' || order.status === 'USER_CANCELLED'
    || order.status === 'DRIVER_CANCELLED' || order.status === 'OPERATOR_CANCELLED';

  return (
    <section className="panel order-card">
      <div className="order-card-head">
        <span className="mono order-id" title={order.orderId}>ORD·{shortId(order.orderId)}</span>
        <Badge tone={ORDER_STATUS_TONE[order.status]}>{ORDER_STATUS_LABEL[order.status]}</Badge>
      </div>

      <div className="order-rail">
        <strong>{trip ? trip.originText : `行程 ${shortId(order.tripId)}`}</strong>
        <span className="order-rail-line" />
        <strong>{trip ? trip.destinationText : ''}</strong>
      </div>

      <div className="otl">
        <TimelineStep state="done" title="已下单" meta={`${formatClock(order.createdAt)} · ${order.seats} 座 · ¥${Number(order.amount.amount).toFixed(2)}`} />
        {cancelled ? (
          <TimelineStep state="danger" title={ORDER_STATUS_LABEL[order.status]} meta="座位已释放" last />
        ) : (
          <>
            <TimelineStep
              state={order.status === 'PENDING_PAYMENT' ? 'current' : 'done'}
              title={order.status === 'PENDING_PAYMENT' ? '等待支付回调' : '支付成功 · 座位锁定'}
              meta={order.status === 'PENDING_PAYMENT'
                ? (intent ? `支付意图 ${PAYMENT_STATUS_LABEL[intent.status]}` : '发起支付后由签名回调驱动')
                : undefined}
            />
            <TimelineStep
              state={order.status === 'SEAT_LOCKED' ? 'current' : order.status === 'COMPLETED' ? 'done' : 'pending'}
              title={trip ? `待出发 · ${formatClock(trip.departureAt)}` : '待出发'}
              meta={order.status === 'SEAT_LOCKED' ? '等待行程完成（由运营确认）' : undefined}
            />
            <TimelineStep state={order.status === 'COMPLETED' ? 'done' : 'pending'} title="已完成" last />
          </>
        )}
      </div>

      {canPay && (
        <>
          <Button
            full
            variant="primary"
            iconLeft={<CreditCard size={16} />}
            disabled={createIntent.isPending || !!intentId}
            onClick={() => createIntent.mutate()}
          >
            {intentId ? '支付已发起 · 等待回调' : createIntent.isPending ? '发起中…' : '发起支付'}
          </Button>
          <p className="order-note">超时未支付将自动取消并释放座位。支付结果由供应商签名回调驱动，此处状态自动刷新。</p>
        </>
      )}

      {order.status === 'COMPLETED' && <OrderReviewSection orderId={order.orderId} />}

      {canCancel && (
        <Button full variant="ghost" disabled={cancelOrder.isPending} onClick={() => cancelOrder.mutate()}>
          {cancelOrder.isPending ? '取消中…' : '取消订单'}
        </Button>
      )}
    </section>
  );
}

function TimelineStep({ state, title, meta, last = false }: {
  state: 'done' | 'current' | 'pending' | 'danger';
  title: string;
  meta?: string;
  last?: boolean;
}) {
  return (
    <div className={`otl-step ${state}`}>
      <div className="otl-rail">
        <span className="otl-node">{state === 'done' && <Check size={12} />}</span>
        {!last && <span className="otl-line" />}
      </div>
      <div className="otl-body">
        <div className="otl-title">{title}</div>
        {meta && <div className="otl-meta">{meta}</div>}
      </div>
    </div>
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
    <div className="review-block">
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
          <div className="price-row">
            <span>评分（1-5）</span>
            <NumberInput value={rating} min={1} max={5} onChange={(value) => setRating(Number(value))} />
          </div>
          <Input label="评价（可选）" value={comment} onChange={(event) => setComment(event.target.value)} />
          <Button full variant="primary" disabled={submit.isPending} onClick={() => submit.mutate()}>
            {submit.isPending ? '提交中…' : '提交评价'}
          </Button>
        </>
      )}
    </div>
  );
}

const CATEGORY_ICON: { match: RegExp; icon: ReactNode; className: string }[] = [
  { match: /PAYMENT/i, icon: <CreditCard size={19} />, className: 'tint-success' },
  { match: /IDENTITY|LIVENESS/i, icon: <ShieldCheck size={19} />, className: 'tint-accent' },
  { match: /SMS|CODE/i, icon: <MessageSquare size={19} />, className: 'tint-neutral' },
  { match: /REVIEW/i, icon: <Sparkles size={19} />, className: 'tint-accent' }
];

function categoryIcon(category: string) {
  const hit = CATEGORY_ICON.find((entry) => entry.match.test(category));
  return hit ?? { icon: <Bell size={19} />, className: 'tint-neutral' };
}

/** A6 · 消息 — demo inbox; sensitive values stay masked until an explicit 查看. */
function InboxScreen({ records, loading }: { records: DeliveryRecord[]; loading: boolean }) {
  const toast = useToast();
  const queryClient = useQueryClient();

  const reveal = useMutation({
    mutationFn: (deliveryId: string) => api<{ deliveryId: string; value: string }>(`/api/demo/inbox/${deliveryId}/reveal`, { method: 'POST' }),
    onSuccess: (response) => toast({ title: `内容：${response.value}`, tone: 'success' }),
    onError: (error) => toast({ title: describeError(error), tone: 'danger' })
  });

  const markRead = useMutation({
    mutationFn: (deliveryId: string) => api(`/api/demo/inbox/${deliveryId}/read`, { method: 'POST' }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['demo-inbox'] })
  });

  const unread = records.filter((record) => record.status !== 'READ');
  const markAllRead = useMutation({
    mutationFn: async () => {
      for (const record of unread) {
        await api(`/api/demo/inbox/${record.deliveryId}/read`, { method: 'POST' });
      }
    },
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['demo-inbox'] })
  });

  return (
    <div className="screen">
      <header className="screen-header row">
        <span className="page-title">消息</span>
        <button
          className="link-btn"
          disabled={unread.length === 0 || markAllRead.isPending}
          onClick={() => markAllRead.mutate()}
        >
          {markAllRead.isPending ? '处理中…' : '全部已读'}
        </button>
      </header>

      <div className="screen-body">
        <span className="section-eyebrow">演示收件箱 · 敏感内容默认遮蔽，点「查看」显式取出</span>
        {records.length > 0 ? (
          records.map((record) => {
            const { icon, className } = categoryIcon(record.category);
            return (
              <div key={record.deliveryId} className={`inbox-item${record.status !== 'READ' ? ' unread' : ''}`}>
                <span className={`inbox-icon ${className}`}>{icon}</span>
                <div className="inbox-item-body">
                  <div className="inbox-item-head">
                    <strong>{record.title}</strong>
                    <span className="mono">{formatClock(record.createdAt)}</span>
                  </div>
                  <div className="inbox-item-preview">{record.category} · {record.maskedPreview}</div>
                </div>
                <Button
                  size="sm"
                  variant="ghost"
                  onClick={() => {
                    reveal.mutate(record.deliveryId);
                    markRead.mutate(record.deliveryId);
                  }}
                >
                  查看
                </Button>
              </div>
            );
          })
        ) : (
          <div className="empty-card">
            <EmptyState icon="inbox" compact title={loading ? '加载中' : '暂无消息'} />
          </div>
        )}
      </div>
    </div>
  );
}

/** 我的 — profile header + 成为车主 stepper (A4) + logout. */
function ProfileScreen({ session }: { session: Session }) {
  const setSession = useSession((state) => state.setSession);

  return (
    <div className="screen">
      <header className="screen-header">
        <span className="page-title">我的</span>
      </header>

      <div className="screen-body">
        <section className="panel profile-head">
          <span className="avatar avatar-lg">{avatarInitial(session.user.phone)}</span>
          <div className="profile-meta">
            <strong>{session.user.phone}</strong>
            <div className="profile-roles">
              {session.user.roles.map((role) => (
                <Badge key={role} tone={role === 'DRIVER' ? 'success' : 'neutral'}>{role}</Badge>
              ))}
            </div>
          </div>
        </section>

        <DriverOnboardingCard />

        <Button full variant="ghost" onClick={() => logout(session, setSession)}>退出登录</Button>
      </div>
    </div>
  );
}

/** A4 · 成为车主 — 实名 → 活体/审批 → 证件 → 审核 stepper over the real identity + driver-case APIs. */
function DriverOnboardingCard() {
  const toast = useToast();
  const [verificationId, setVerificationId] = useState<string | null>(null);
  const [realName, setRealName] = useState('');
  const [idNumber, setIdNumber] = useState('');
  const [drivingLicenseFile, setDrivingLicenseFile] = useState<File | null>(null);
  const [vehicleLicenseFile, setVehicleLicenseFile] = useState<File | null>(null);
  const [verificationState, setVerificationState] = useState<VerificationState>('DRAFT');
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
  const identityApproved = verification?.status === 'APPROVED';

  const submitVerification = useMutation({
    mutationFn: async () => {
      if (!drivingLicenseFile || !vehicleLicenseFile) {
        throw new Error('请选择驾驶证和行驶证文件');
      }
      const drivingLicense = await uploadDriverDocument(drivingLicenseFile);
      const vehicleLicense = await uploadDriverDocument(vehicleLicenseFile);
      return api<{ status: VerificationState }>('/api/drivers/verification-cases', {
        method: 'POST',
        body: {
          userId: useSession.getState().session?.user.userId,
          drivingLicenseFileId: drivingLicense.fileObjectId,
          vehicleLicenseFileId: vehicleLicense.fileObjectId
        }
      });
    },
    onSuccess: (result) => {
      setVerificationState(result.status);
      toast({ title: 'OCR Mock 已识别，等待后台复核', tone: 'success' });
    },
    onError: showError
  });

  const docsSubmitted = verificationState !== 'DRAFT';
  const step = !verificationId ? 1 : !identityApproved ? 2 : !docsSubmitted ? 3 : 4;

  return (
    <section className="panel onboarding">
      <div className="onboarding-head">
        <ShieldCheck size={18} color="var(--accent)" />
        <span className="panel-title">成为车主</span>
      </div>

      <div className="step-rail">
        <StepDot index={1} label="实名" state={step > 1 ? 'done' : 'current'} />
        <span className={`step-link${step > 1 ? ' done' : ''}`} />
        <StepDot index={2} label="活体" state={step > 2 ? 'done' : step === 2 ? 'current' : 'pending'} />
        <span className={`step-link${step > 2 ? ' done' : ''}`} />
        <StepDot index={3} label="证件" state={step > 3 ? 'done' : step === 3 ? 'current' : 'pending'} />
        <span className={`step-link${step > 3 ? ' done' : ''}`} />
        <StepDot index={4} label="审核" state={verificationState === 'APPROVED' ? 'done' : step === 4 ? 'current' : 'pending'} />
      </div>

      <div className="step-eyebrow">STEP {step} / 4</div>

      {step === 1 && (
        <>
          <h3 className="step-title">实名信息</h3>
          <Input label="真实姓名" value={realName} onChange={(event) => setRealName(event.target.value)} />
          <Input label="证件号" inputMode="numeric" value={idNumber} onChange={(event) => setIdNumber(event.target.value)} />
          <Button
            full
            variant="primary"
            disabled={!realName || !idNumber || start.isPending}
            onClick={() => start.mutate()}
          >
            {start.isPending ? '发起中…' : '发起实名认证'}
          </Button>
        </>
      )}

      {step === 2 && (
        <>
          <h3 className="step-title">等待认证结果</h3>
          <div className="status-line">
            <Badge tone={verification ? IDENTITY_STATUS_TONE[verification.status] : 'accent'}>
              {verification ? IDENTITY_STATUS_LABEL[verification.status] : '查询中…'}
            </Badge>
            {verification && <Tag accent="neutral">{LIVENESS_STATUS_LABEL[verification.livenessStatus]}</Tag>}
          </div>
          <Alert tone="info" title="结果由供应商回调驱动">
            认证与活体结果异步投递到收件箱（演示中由运营在后台驱动活体 PASS 与会话 APPROVED），此处状态自动刷新。
          </Alert>
        </>
      )}

      {step === 3 && (
        <>
          <h3 className="step-title">上传证件</h3>
          <p className="step-sub">OCR 自动识别后转人工复核。照片需清晰、四角完整。</p>
          <UploadCard label="驾驶证" file={drivingLicenseFile} onPick={setDrivingLicenseFile} />
          <UploadCard label="行驶证" file={vehicleLicenseFile} onPick={setVehicleLicenseFile} />
          <div className="ocr-line">
            <span>OCR 状态</span>
            <Tag accent={docsSubmitted ? 'coral' : 'neutral'}>{verificationState}</Tag>
          </div>
          <Button
            full
            variant="primary"
            size="lg"
            disabled={!drivingLicenseFile || !vehicleLicenseFile || submitVerification.isPending}
            onClick={() => submitVerification.mutate()}
          >
            {submitVerification.isPending ? '提交中…' : '提交审核'}
          </Button>
        </>
      )}

      {step === 4 && (
        <>
          <h3 className="step-title">平台审核</h3>
          {verificationState === 'APPROVED' ? (
            <Alert tone="success" title="审核已通过">您已获得车主能力，可以发布行程了。</Alert>
          ) : (
            <Alert tone="info" title="等待运营复核">
              证件已提交（{verificationState}），OCR 识别完成后由运营人工复核，结果会投递到收件箱。
            </Alert>
          )}
        </>
      )}
    </section>
  );
}

function StepDot({ index, label, state }: { index: number; label: string; state: 'done' | 'current' | 'pending' }) {
  return (
    <div className={`step-dot ${state}`}>
      <span className="step-dot-circle">{state === 'done' ? <Check size={15} /> : index}</span>
      <span className="step-dot-label">{label}</span>
    </div>
  );
}

/** Design-style upload slot: dashed 待上传 → solid success once a file is picked. */
function UploadCard({ label, file, onPick }: { label: string; file: File | null; onPick: (file: File | null) => void }) {
  return (
    <label className={`upload-card${file ? ' done' : ''}`}>
      <span className="upload-icon">{file ? <Check size={20} /> : <Camera size={20} />}</span>
      <span className="upload-meta">
        <strong>{label}</strong>
        <span>{file ? `${file.name} · 待提交` : '点击拍摄或从相册选择'}</span>
      </span>
      {file ? <Pencil size={17} className="upload-edit" /> : <span className="upload-action">上传</span>}
      <input type="file" accept="image/*,.pdf" onChange={(event) => onPick(event.target.files?.[0] ?? null)} />
    </label>
  );
}

function avatarInitial(value: string) {
  if (!value) return '客';
  // Phone numbers read as noise; show a friendly 我-style glyph for pure digits.
  return /^\d+$/.test(value) ? '我' : value[0].toUpperCase();
}

function shortId(value: string) {
  // Backend ids carry a type prefix ("order-…", "trip-…", "user-…"): drop it so the
  // short display code shows the distinctive part, e.g. ORD·3F2A91.
  return value.replace(/^[a-z]+-/i, '').replace(/-/g, '').slice(0, 6).toUpperCase();
}

function routeProviderLabel(trip: TripOffer) {
  return trip.route.providerTrace === 'amap-v5' ? '高德路线快照' : '本地 Mock 路线快照';
}

async function uploadDriverDocument(file: File) {
  const presigned = await api<PresignedUpload>('/api/files/presign-upload', {
    method: 'POST',
    body: { objectName: file.name, contentType: file.type || 'application/octet-stream', contentLength: file.size }
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

function formatClock(value: string) {
  return new Intl.DateTimeFormat('zh-CN', { hour: '2-digit', minute: '2-digit' }).format(new Date(value));
}
