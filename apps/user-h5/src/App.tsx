import { useMemo, useState } from 'react';
import { Button, Card, Form, Input, List, NavBar, Space, Stepper, Tabs, Tag, Toast } from 'antd-mobile';
import { CalendarClock, CarFront, CreditCard, MapPinned, ShieldCheck, UploadCloud } from 'lucide-react';
import { create } from 'zustand';

type TripStatus = 'PUBLISHED' | 'SEAT_LOCKED' | 'TIMEOUT_CANCELLED';

type Trip = {
  id: string;
  driver: string;
  origin: string;
  destination: string;
  departure: string;
  seatsLeft: number;
  price: number;
  distanceKm: number;
  status: TripStatus;
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
  selectedTripId: 'trip-1024',
  bookedOrderId: '',
  verificationState: 'DRAFT',
  setSelectedTripId: (tripId) => set({ selectedTripId: tripId }),
  setBookedOrderId: (orderId) => set({ bookedOrderId: orderId }),
  setVerificationState: (state) => set({ verificationState: state })
}));

const seedTrips: Trip[] = [
  {
    id: 'trip-1024',
    driver: '林师傅',
    origin: '软件园三期',
    destination: '集美大学',
    departure: '今天 18:30',
    seatsLeft: 3,
    price: 28.2,
    distanceKm: 18.5,
    status: 'PUBLISHED'
  },
  {
    id: 'trip-2048',
    driver: '陈师傅',
    origin: '海沧湾',
    destination: '湖里万达',
    departure: '今天 19:10',
    seatsLeft: 1,
    price: 21.6,
    distanceKm: 13,
    status: 'PUBLISHED'
  }
];

export default function App() {
  const [origin, setOrigin] = useState('软件园三期');
  const [destination, setDestination] = useState('集美大学');
  const [seats, setSeats] = useState(1);
  const [orders, setOrders] = useState<Trip[]>(seedTrips);
  const selectedTripId = useBookingStore((state) => state.selectedTripId);
  const setSelectedTripId = useBookingStore((state) => state.setSelectedTripId);
  const setBookedOrderId = useBookingStore((state) => state.setBookedOrderId);
  const verificationState = useBookingStore((state) => state.verificationState);
  const setVerificationState = useBookingStore((state) => state.setVerificationState);

  const filteredTrips = useMemo(() => {
    return orders.filter((trip) => trip.origin.includes(origin) && trip.destination.includes(destination));
  }, [destination, orders, origin]);

  const selectedTrip = orders.find((trip) => trip.id === selectedTripId) ?? orders[0];

  function bookSeat() {
    setOrders((current) =>
      current.map((trip) =>
        trip.id === selectedTrip.id
          ? { ...trip, seatsLeft: Math.max(0, trip.seatsLeft - seats), status: 'SEAT_LOCKED' }
          : trip
      )
    );
    setBookedOrderId(`order-${Date.now()}`);
    Toast.show({ content: '座位已锁定，模拟支付成功' });
  }

  function submitVerification() {
    setVerificationState('OCR_REVIEWABLE');
    Toast.show({ content: 'OCR Mock 已识别，等待后台复核' });
  }

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
            <span>高德地图 Mock 路线快照 · 服务端计价</span>
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
            </Form>
          </Card>

          <List className="trip-list">
            {filteredTrips.map((trip) => (
              <List.Item
                key={trip.id}
                onClick={() => setSelectedTripId(trip.id)}
                prefix={<CarFront size={22} />}
                description={`${trip.departure} · ${trip.distanceKm}km · 剩余 ${trip.seatsLeft} 座`}
                extra={<Tag color={trip.status === 'SEAT_LOCKED' ? 'success' : 'primary'}>{trip.status}</Tag>}
              >
                {trip.driver} · ¥{trip.price.toFixed(2)}
              </List.Item>
            ))}
          </List>

          <Card className="panel booking-card">
            <div className="section-title">
              <CalendarClock size={18} />
              <span>订座确认</span>
            </div>
            <p>{selectedTrip.origin} → {selectedTrip.destination}</p>
            <Space justify="between" block align="center">
              <span>座位数</span>
              <Stepper min={1} max={Math.max(1, selectedTrip.seatsLeft)} value={seats} onChange={(value) => setSeats(Number(value))} />
            </Space>
            <Button block color="primary" size="large" onClick={bookSeat}>
              <CreditCard size={18} /> 模拟支付 ¥{(selectedTrip.price * seats).toFixed(2)}
            </Button>
          </Card>
        </Tabs.Tab>

        <Tabs.Tab title="认证" key="verify">
          <Card className="panel verify-card">
            <div className="section-title">
              <ShieldCheck size={18} />
              <span>司机证件审核</span>
            </div>
            <List>
              <List.Item prefix={<UploadCloud size={20} />}>驾驶证：file-driving-license-001</List.Item>
              <List.Item prefix={<UploadCloud size={20} />}>行驶证：file-vehicle-license-001</List.Item>
            </List>
            <div className="ocr-box">
              <strong>OCR 状态</strong>
              <Tag color={verificationState === 'DRAFT' ? 'default' : 'warning'}>{verificationState}</Tag>
            </div>
            <Button block color="success" size="large" onClick={submitVerification}>
              提交证件审核
            </Button>
          </Card>
        </Tabs.Tab>
      </Tabs>
    </main>
  );
}
