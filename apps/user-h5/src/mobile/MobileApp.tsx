import { useState } from 'react';
import { Compass, MessageCircle, Route as RouteIcon, UserRound } from 'lucide-react';
import { useInboxQuery } from '../lib/queries';
import type { Session, TripOffer } from '../lib/types';
import { BookingScreen } from './BookingScreen';
import { HomeScreen } from './HomeScreen';
import { InboxScreen } from './InboxScreen';
import { ProfileScreen } from './ProfileScreen';
import { TripsScreen } from './TripsScreen';

type MainTab = 'home' | 'trips' | 'inbox' | 'profile';

export function MobileApp({ session }: { session: Session }) {
  const [tab, setTab] = useState<MainTab>('home');
  const [bookingTrip, setBookingTrip] = useState<TripOffer | null>(null);

  // Unread dot on the 消息 tab; the list itself is fetched by the inbox screen.
  const inboxQuery = useInboxQuery();
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
