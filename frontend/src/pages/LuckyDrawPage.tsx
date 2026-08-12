import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api';
import { useAuth } from '../auth';
import { Empty, ErrorNotice, Loading } from '../components/Feedback';
import { LuckyWheel } from '../components/LuckyWheel';
import { useResource } from '../hooks/useResource';
import type {
  Campaign,
  CustomerDetails,
  DrawResult,
  LuckyEntry,
  MyResult,
  Notification,
  RealtimeUpdate,
  RewardClaim,
  Stats,
  Ticket,
} from '../types';

const emptyMine: MyResult = {
  campaignId: '',
  entryIds: [],
  remainingQuota: 0,
  won: false,
};
const emptyStats: Stats = {
  campaignId: '',
  totalEntries: 0,
  distinctParticipants: 0,
};

export function LuckyDrawPage() {
  const { session } = useAuth();
  return session!.role === 'SELLER' ? <SellerDraw /> : <CustomerEntry />;
}

function CustomerEntry() {
  const token = useAuth().session!.token;
  const campaigns = useResource(
    () => api<Campaign[]>('/api/campaigns', token),
    [token],
  );
  const tickets = useResource(
    () => api<Ticket[]>('/api/tickets', token),
    [token],
  );
  const notifications = useResource(
    () => api<Notification[]>('/api/notifications', token),
    [token],
  );
  const rewards = useResource(
    () => api<RewardClaim[]>('/api/rewards', token),
    [token],
  );
  const visible =
    campaigns.data?.filter(
      (campaign) => !['DRAFT', 'CANCELLED'].includes(campaign.status),
    ) ?? [];
  const [selection, setSelection] = useState('');
  const selected = selection || visible[0]?.id || '';
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const mine = useResource(
    () =>
      selected
        ? api<MyResult>(`/api/analytics/campaigns/${selected}/me`, token)
        : Promise.resolve(emptyMine),
    [selected, token],
  );
  const campaign = visible.find((item) => item.id === selected);
  const available =
    tickets.data?.filter((ticket) => ticket.status === 'ISSUED') ?? [];
  const refreshMine = mine.refresh;
  const refreshNotifications = notifications.refresh;
  const refreshRewards = rewards.refresh;

  useEffect(() => {
    if (typeof WebSocket === 'undefined') return;
    const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
    const socket = new WebSocket(
      `${protocol}://${window.location.host}/ws/realtime?access_token=${encodeURIComponent(token)}`,
    );
    socket.onmessage = (event) => {
      const update = JSON.parse(event.data) as RealtimeUpdate;
      if (update.type === 'NOTIFICATION') {
        void refreshNotifications();
        void refreshMine();
      }
      if (update.type === 'REWARD') void refreshRewards();
    };
    return () => socket.close();
  }, [token, refreshMine, refreshNotifications, refreshRewards]);

  async function submit(ticketId: string) {
    setError('');
    setMessage('');
    setSubmitting(true);
    try {
      const entry = await api<LuckyEntry>(
        `/api/campaigns/${selected}/entries`,
        token,
        { method: 'POST', body: JSON.stringify({ ticketId }) },
      );
      setMessage(`Ticket submitted as entry #${entry.sequence}.`);
      await tickets.refresh();
      window.setTimeout(() => void mine.refresh(), 500);
    } catch (reason) {
      const failure = reason as ApiError;
      setError(
        failure.code ? `${failure.message} (${failure.code})` : failure.message,
      );
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="stack">
      <section className="hero">
        <span className="eyebrow">Customer app · ticket submission</span>
        <h2>Submit a ticket to a campaign.</h2>
        <p>
          Each ticket creates one draw slot. The seller picks one winner after
          the campaign closes.
        </p>
      </section>
      <ErrorNotice message={error || campaigns.error || mine.error} />
      {message && <p className="notice success">{message}</p>}
      <div className="grid two">
        <section className="card">
          <label>
            Campaign
            <select
              value={selected}
              disabled={submitting}
              onChange={(event) => {
                setSelection(event.target.value);
                setMessage('');
              }}
            >
              <option value="">Select campaign</option>
              {visible.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name} · {item.status}
                </option>
              ))}
            </select>
          </label>
          <Loading active={campaigns.loading || tickets.loading} />
          {!tickets.loading && !available.length && (
            <p className="muted">
              No ticket ready. <Link to="/shop">Buy a qualifying product</Link>,
              then return here.
            </p>
          )}
          <div className="ticket-actions">
            {campaign?.status === 'ACTIVE' &&
              available.map((ticket) => (
                <button
                  disabled={submitting}
                  key={ticket.id}
                  onClick={() => void submit(ticket.id)}
                >
                  Submit ticket {ticket.id.slice(0, 8)}
                </button>
              ))}
          </div>
        </section>
        <section className="card">
          <div className="row">
            <h3>{campaign?.name ?? 'Choose a campaign'}</h3>
            <button
              className="secondary"
              onClick={() =>
                void Promise.all([
                  campaigns.refresh(),
                  mine.refresh(),
                  notifications.refresh(),
                  rewards.refresh(),
                ])
              }
            >
              Refresh
            </button>
          </div>
          <div className="metrics">
            <div>
              <strong>{mine.data?.entryIds.length ?? 0}</strong>
              <span>My slots</span>
            </div>
            <div>
              <strong>{mine.data?.remainingQuota ?? '—'}</strong>
              <span>Quota left</span>
            </div>
            <div>
              <strong>
                {campaign?.status === 'DRAWN'
                  ? mine.data?.won
                    ? 'Winner'
                    : 'Not selected'
                  : 'Pending'}
              </strong>
              <span>Result</span>
            </div>
          </div>
          {mine.data?.won && (
            <p className="notice success">
              You won {mine.data.reward?.type} {mine.data.reward?.reference}.
            </p>
          )}
          <p className="muted">
            Last projected:{' '}
            {mine.data?.lastUpdatedAt
              ? new Date(mine.data.lastUpdatedAt).toLocaleString()
              : 'not yet'}
          </p>
        </section>
      </div>
      <div className="grid two">
        <section className="card">
          <h3>Notifications · live</h3>
          <Empty show={!notifications.data?.length}>No notifications.</Empty>
          <ul className="items">
            {notifications.data?.map((item) => (
              <li key={item.id}>{item.message}</li>
            ))}
          </ul>
        </section>
        <section className="card">
          <h3>Reward delivery · live</h3>
          <Empty show={!rewards.data?.length}>No rewards.</Empty>
          <ul className="items">
            {rewards.data?.map((item) => (
              <li key={item.id}>
                <span>
                  {item.rewardType}: {item.reference}
                </span>
                <span>{item.deliveryReference ?? 'Preparing delivery'}</span>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}

function SellerDraw() {
  const { session } = useAuth();
  const token = session!.token;
  const campaigns = useResource(
    () => api<Campaign[]>('/api/campaigns', token),
    [token],
  );
  const owned =
    campaigns.data?.filter(
      (campaign) =>
        campaign.sellerId === session!.userId &&
        !['DRAFT', 'CANCELLED'].includes(campaign.status),
    ) ?? [];
  const [selection, setSelection] = useState('');
  const selected = selection || owned[0]?.id || '';
  const [spinning, setSpinning] = useState(false);
  const [results, setResults] = useState<Record<string, DrawResult>>({});
  const [customers, setCustomers] = useState<Record<string, CustomerDetails>>(
    {},
  );
  const [resultErrors, setResultErrors] = useState<Record<string, string>>({});
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [error, setError] = useState('');
  const stats = useResource(
    () =>
      selected
        ? api<Stats>(`/api/analytics/campaigns/${selected}/stats`, token)
        : Promise.resolve(emptyStats),
    [selected, token],
  );
  const campaign = owned.find((item) => item.id === selected);
  const result = results[selected];
  const resultError = resultErrors[selected] ?? '';
  const customer = customers[selected];
  const rewardLabel = campaign
    ? `${campaign.reward.type}: ${campaign.reward.reference}`
    : undefined;

  useEffect(() => {
    if (!selected || campaign?.status !== 'DRAWN' || result) return;
    let active = true;
    void api<DrawResult>(`/api/campaigns/${selected}/draw`, token, {
      method: 'POST',
    })
      .then((winner) => {
        if (active) {
          setResults((current) => ({ ...current, [selected]: winner }));
          setResultErrors((current) => ({ ...current, [selected]: '' }));
        }
      })
      .catch((reason: unknown) => {
        if (active) {
          setResultErrors((current) => ({
            ...current,
            [selected]:
              reason instanceof Error
                ? reason.message
                : 'Could not load winner',
          }));
        }
      });
    return () => {
      active = false;
    };
  }, [campaign?.status, loadAttempt, result, selected, token]);

  async function end() {
    setError('');
    try {
      await api(`/api/campaigns/${selected}/end`, token, { method: 'POST' });
      await Promise.all([campaigns.refresh(), stats.refresh()]);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Request failed');
    }
  }

  async function draw() {
    const campaignId = selected;
    setError('');
    setSpinning(true);
    try {
      const [winner] = await Promise.all([
        api<DrawResult>(`/api/campaigns/${campaignId}/draw`, token, {
          method: 'POST',
        }),
        new Promise((resolve) => window.setTimeout(resolve, 2500)),
      ]);
      setResults((current) => ({ ...current, [campaignId]: winner }));
      await Promise.all([campaigns.refresh(), stats.refresh()]);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Draw failed');
    } finally {
      setSpinning(false);
    }
  }

  async function viewCustomer() {
    if (!result) return;
    const campaignId = selected;
    setError('');
    try {
      const details = await api<CustomerDetails>(
        `/api/customers/${result.winner.userId}`,
        token,
      );
      setCustomers((current) => ({ ...current, [campaignId]: details }));
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Request failed');
    }
  }

  return (
    <div className="stack">
      <section className="hero">
        <span className="eyebrow">Seller portal · final draw</span>
        <h2>Close the campaign, then pick one winner.</h2>
        <p>
          Closing freezes and hashes every submitted ticket. The wheel uses the
          server-selected winner from that immutable snapshot.
        </p>
      </section>
      <ErrorNotice
        message={error || resultError || campaigns.error || stats.error}
      />
      <div className="grid wheel-layout">
        <section className="card">
          <label>
            Campaign
            <select
              value={selected}
              disabled={spinning}
              onChange={(event) => {
                setSelection(event.target.value);
              }}
            >
              <option value="">Select campaign</option>
              {owned.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name} · {item.status}
                </option>
              ))}
            </select>
          </label>
          <LuckyWheel
            spinning={spinning}
            reward={rewardLabel}
            segment={result ? (result.selectedIndex - 1) % 8 : undefined}
            result={
              result
                ? `Winner: ${result.winner.userId}`
                : campaign?.status === 'ENDED'
                  ? 'Ready to pick a winner'
                  : campaign?.status === 'DRAWN'
                    ? resultError
                      ? 'Winner is recorded'
                      : 'Loading recorded winner...'
                    : undefined
            }
          />
        </section>
        <section className="card">
          <h3>Draw controls</h3>
          <div className="metrics">
            <div>
              <strong>{stats.data?.totalEntries ?? 0}</strong>
              <span>Submitted tickets</span>
            </div>
            <div>
              <strong>{stats.data?.distinctParticipants ?? 0}</strong>
              <span>Customers</span>
            </div>
            <div>
              <strong>{campaign?.status ?? '—'}</strong>
              <span>Status</span>
            </div>
          </div>
          <div className="actions">
            {campaign?.status === 'ACTIVE' && (
              <button onClick={() => void end()}>End & freeze snapshot</button>
            )}
            {campaign?.status === 'ENDED' && (
              <button disabled={spinning} onClick={() => void draw()}>
                Spin final draw
              </button>
            )}
            {campaign?.status === 'DRAWN' && resultError && (
              <button
                onClick={() => {
                  setResultErrors((current) => ({
                    ...current,
                    [selected]: '',
                  }));
                  setLoadAttempt((attempt) => attempt + 1);
                }}
              >
                Retry loading winner
              </button>
            )}
          </div>
          {result && (
            <div className="draw-audit">
              <p>
                Winner: <strong>{result.winner.userId}</strong>
              </p>
              <p>
                Entry #{result.winner.sequence} · ticket{' '}
                <code>{result.winner.ticketId}</code>
              </p>
              <p>
                Snapshot: <code>{result.snapshotHash}</code>
              </p>
              <button className="secondary" onClick={() => void viewCustomer()}>
                View winner details
              </button>
            </div>
          )}
          {customer && (
            <div className="draw-audit">
              <h3>Customer details</h3>
              <p>User: {customer.userId}</p>
              <p>Orders: {customer.totalOrders}</p>
              <p>
                Total spent: ₫{Number(customer.totalSpent).toLocaleString()}
              </p>
              <ul className="items">
                {customer.recentOrders.map((order) => (
                  <li key={order.id}>
                    <code>{order.id.slice(0, 8)}</code>
                    <span>₫{Number(order.total).toLocaleString()}</span>
                  </li>
                ))}
              </ul>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
