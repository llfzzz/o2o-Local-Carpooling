import { useState } from 'react';
import type { ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { Button } from '@fj';
import { CarFront, Compass, MessageCircle, RotateCw, Route as RouteIcon, ShieldCheck, UserRound } from 'lucide-react';
import { avatarInitial } from '../lib/format';
import { useOrdersQuery, useUnreadCountQuery } from '../lib/queries';
import { useChatUnreadQuery } from '../lib/chat';
import type { Session } from '../lib/types';
import { DesktopDriver } from './views/DesktopDriver';
import { DesktopHome } from './views/DesktopHome';
import { DesktopInbox } from './views/DesktopInbox';
import { DesktopProfile } from './views/DesktopProfile';
import { DesktopTrips } from './views/DesktopTrips';

type DesktopView = 'home' | 'trips' | 'inbox' | 'driver' | 'profile';

const VIEW_LABEL: Record<DesktopView, string> = {
  home: '找车',
  trips: '我的行程',
  inbox: '消息',
  driver: '成为车主',
  profile: '个人中心'
};

const NAV_TRAVEL: { value: DesktopView; label: string; icon: ReactNode }[] = [
  { value: 'home', label: '找车', icon: <Compass size={16} /> },
  { value: 'trips', label: '我的行程', icon: <RouteIcon size={16} /> },
  { value: 'inbox', label: '消息', icon: <MessageCircle size={16} /> }
];

const NAV_ACCOUNT: { value: DesktopView; label: string; icon: ReactNode }[] = [
  { value: 'driver', label: '成为车主', icon: <ShieldCheck size={16} /> },
  { value: 'profile', label: '个人中心', icon: <UserRound size={16} /> }
];

const VIEW_REFRESH_KEYS: Record<DesktopView, string[]> = {
  home: ['trips'],
  trips: ['orders', 'trip', 'payment-intent'],
  inbox: ['inbox', 'inbox-unread', 'conversations', 'chat-unread'],
  driver: ['identity-verification'],
  profile: ['orders', 'inbox-unread']
};

/** Desktop rider console — the admin Dispatch shell frame with rider-friendly content. */
export function DesktopApp({ session }: { session: Session }) {
  const [view, setView] = useState<DesktopView>('home');
  const queryClient = useQueryClient();

  // Shell-level unread polls feed the sider chip (notifications + chat) and the online
  // indicator; the message lists are fetched inside the inbox view.
  const unreadQuery = useUnreadCountQuery();
  const chatUnreadQuery = useChatUnreadQuery();
  const unreadCount = (unreadQuery.data?.unread ?? 0) + (chatUnreadQuery.data?.unread ?? 0);

  // Prefetched at shell level so the 我的行程 nav chip stays live from the shared 5s poll.
  const ordersQuery = useOrdersQuery();
  const ongoingCount = (ordersQuery.data ?? []).filter(
    (order) => order.status === 'PENDING_PAYMENT' || order.status === 'SEAT_LOCKED'
  ).length;

  const navChip = (value: DesktopView) => {
    const count = value === 'inbox' ? unreadCount : value === 'trips' ? ongoingCount : 0;
    if (count <= 0) return null;
    return <span className={`dsk-nav-count${view === value ? ' solid' : ''}`}>{count}</span>;
  };

  return (
    <div className="dsk-shell">
      <aside className="dsk-sider">
        <div className="dsk-sider-brand">
          <span className="dsk-sider-brand-mark"><CarFront size={17} /></span>
          <div className="dsk-sider-brand-copy">
            <span className="dsk-sider-brand-name">同城拼车</span>
            <span className="dsk-sider-brand-sub">RIDER · TRIP FLOW</span>
          </div>
        </div>

        <span className="dsk-nav-group-label">出行 · TRAVEL</span>
        <nav className="dsk-nav">
          {NAV_TRAVEL.map((item) => (
            <button
              key={item.value}
              className={`dsk-nav-item${view === item.value ? ' active' : ''}`}
              aria-current={view === item.value ? 'page' : undefined}
              onClick={() => setView(item.value)}
            >
              {item.icon}
              {item.label}
              {navChip(item.value)}
            </button>
          ))}
        </nav>

        <span className="dsk-nav-group-label account">账户 · ACCOUNT</span>
        <nav className="dsk-nav">
          {NAV_ACCOUNT.map((item) => (
            <button
              key={item.value}
              className={`dsk-nav-item${view === item.value ? ' active' : ''}`}
              aria-current={view === item.value ? 'page' : undefined}
              onClick={() => setView(item.value)}
            >
              {item.icon}
              {item.label}
            </button>
          ))}
        </nav>

        <div className="dsk-sider-foot">
          <span className="dsk-sider-avatar">{avatarInitial(session.user.phone)}</span>
          <div className="dsk-sider-foot-copy">
            <span>{session.user.phone}</span>
            <span className="dsk-sider-foot-sub">{session.user.roles.join(' · ') || 'RIDER'}</span>
          </div>
        </div>
      </aside>

      <main className="dsk-main">
        <header className="dsk-crumb-bar">
          <div className="dsk-crumbs">
            <span>拼车</span>
            <span>/</span>
            <strong>{VIEW_LABEL[view]}</strong>
          </div>
          <div className="dsk-crumb-actions">
            <span className={`dsk-online-chip${unreadQuery.isError ? ' offline' : ''}`}>
              <span className="dsk-online-dot" />
              {unreadQuery.isError ? '离线' : '在线'}
            </span>
            <Button
              variant="secondary"
              size="sm"
              iconLeft={<RotateCw size={14} />}
              onClick={() => VIEW_REFRESH_KEYS[view].forEach((key) => queryClient.invalidateQueries({ queryKey: [key] }))}
            >
              刷新
            </Button>
          </div>
        </header>

        <div className="dsk-view-body">
          {view === 'home' && <DesktopHome session={session} onBooked={() => setView('trips')} />}
          {view === 'trips' && <DesktopTrips />}
          {view === 'inbox' && <DesktopInbox onOpenLink={() => setView('trips')} />}
          {view === 'driver' && <DesktopDriver />}
          {view === 'profile' && <DesktopProfile session={session} onGoDriver={() => setView('driver')} />}
        </div>
      </main>
    </div>
  );
}
