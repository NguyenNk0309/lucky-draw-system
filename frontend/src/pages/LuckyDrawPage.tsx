import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api';
import { useAuth } from '../auth';
import { Empty, ErrorNotice, Loading } from '../components/Feedback';
import { LuckyWheel } from '../components/LuckyWheel';
import { useResource } from '../hooks/useResource';
import type {
  Campaign,
  DrawResult,
  MyResult,
  Notification,
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
  return session!.role === 'SELLER' ? <SellerWheel /> : <CustomerWheel />;
}

function CustomerWheel() {
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
      (item) => !['DRAFT', 'CANCELLED'].includes(item.status),
    ) ?? [];
  const [selection, setSelection] = useState('');
  const selected = selection || visible[0]?.id || '';
  const [spinning, setSpinning] = useState(false);
  const [wheelMessage, setWheelMessage] = useState('');
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

  async function spin(ticketId: string) {
    setError('');
    setWheelMessage('');
    setSpinning(true);
    const animation = new Promise((resolve) =>
      window.setTimeout(resolve, 2500),
    );
    try {
      await Promise.all([
        api(`/api/campaigns/${selected}/entries`, token, {
          method: 'POST',
          body: JSON.stringify({ ticketId }),
        }),
        animation,
      ]);
      setWheelMessage(
        'Entry accepted! Winner will be announced when the campaign ends.',
      );
      await tickets.refresh();
      window.setTimeout(() => void mine.refresh(), 700);
    } catch (reason) {
      await animation;
      const failure = reason as ApiError;
      setError(
        failure.code ? `${failure.message} (${failure.code})` : failure.message,
      );
    } finally {
      setSpinning(false);
    }
  }

  const result =
    campaign?.status === 'DRAWN'
      ? mine.data?.won
        ? '🎉 You won!'
        : 'Draw completed'
      : wheelMessage;
  return (
    <div className="stack">
      <section className="hero">
        <span className="eyebrow">Customer app · submit entry</span>
        <h2>Use your purchase ticket to spin.</h2>
        <p>
          The spin submits an entry. The secure final winner is chosen later
          from the frozen snapshot.
        </p>
      </section>
      <ErrorNotice message={error || campaigns.error || mine.error} />
      <div className="grid wheel-layout">
        <section className="card">
          <label>
            Campaign
            <select
              value={selected}
              onChange={(event) => {
                setSelection(event.target.value);
                setWheelMessage('');
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
          <LuckyWheel spinning={spinning} result={result || undefined} />
        </section>
        <section className="card">
          <h3>{campaign?.name ?? 'Choose a campaign'}</h3>
          <Loading active={campaigns.loading || tickets.loading} />
          {!tickets.loading && !available.length && (
            <p className="muted">
              No ticket ready. <Link to="/shop">Create a qualifying order</Link>{' '}
              to get one, then return here.
            </p>
          )}
          <div className="ticket-actions">
            {campaign?.status === 'ACTIVE' &&
              available.map((ticket) => (
                <button
                  disabled={spinning}
                  key={ticket.id}
                  onClick={() => void spin(ticket.id)}
                >
                  Spin with ticket {ticket.id.slice(0, 8)}
                </button>
              ))}
          </div>
          <div className="metrics">
            <div>
              <strong>{mine.data?.entryIds.length ?? 0}</strong>
              <span>My entries</span>
            </div>
            <div>
              <strong>{mine.data?.remainingQuota ?? '—'}</strong>
              <span>Quota left</span>
            </div>
            <div>
              <strong>
                {mine.data?.won
                  ? 'Winner'
                  : campaign?.status === 'DRAWN'
                    ? 'Not won'
                    : 'Pending'}
              </strong>
              <span>Result</span>
            </div>
          </div>
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
          <h3>Winner notifications</h3>
          <Empty show={!notifications.data?.length}>No notifications.</Empty>
          <ul className="items">
            {notifications.data?.map((item) => (
              <li key={item.id}>{item.message}</li>
            ))}
          </ul>
        </section>
        <section className="card">
          <h3>Rewards</h3>
          <Empty show={!rewards.data?.length}>No rewards.</Empty>
          <ul className="items">
            {rewards.data?.map((item) => (
              <li key={item.id}>
                <span>{item.reference}</span>
                <span>{item.deliveryReference ?? 'Processing'}</span>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}

function SellerWheel() {
  const { session } = useAuth();
  const token = session!.token;
  const campaigns = useResource(
    () => api<Campaign[]>('/api/campaigns', token),
    [token],
  );
  const owned =
    campaigns.data?.filter(
      (item) =>
        item.sellerId === session!.userId &&
        !['DRAFT', 'CANCELLED'].includes(item.status),
    ) ?? [];
  const [selection, setSelection] = useState('');
  const selected = selection || owned[0]?.id || '';
  const [spinning, setSpinning] = useState(false);
  const [result, setResult] = useState<DrawResult>();
  const [error, setError] = useState('');
  const stats = useResource(
    () =>
      selected
        ? api<Stats>(`/api/analytics/campaigns/${selected}/stats`, token)
        : Promise.resolve(emptyStats),
    [selected, token],
  );
  const campaign = owned.find((item) => item.id === selected);

  async function end() {
    setError('');
    try {
      await api(`/api/campaigns/${selected}/end`, token, { method: 'POST' });
      await campaigns.refresh();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Request failed');
    }
  }
  async function draw() {
    setError('');
    setResult(undefined);
    setSpinning(true);
    const animation = new Promise((resolve) =>
      window.setTimeout(resolve, 2500),
    );
    try {
      const [winner] = await Promise.all([
        api<DrawResult>(`/api/campaigns/${selected}/draw`, token, {
          method: 'POST',
        }),
        animation,
      ]);
      setResult(winner);
      await Promise.all([campaigns.refresh(), stats.refresh()]);
    } catch (reason) {
      await animation;
      setError(reason instanceof Error ? reason.message : 'Draw failed');
    } finally {
      setSpinning(false);
    }
  }

  return (
    <div className="stack">
      <section className="hero">
        <span className="eyebrow">Seller portal · final draw</span>
        <h2>Freeze entries and draw the winner.</h2>
        <p>
          The wheel presents the server result selected with secure randomness
          from the MySQL snapshot.
        </p>
      </section>
      <ErrorNotice message={error || campaigns.error || stats.error} />
      <div className="grid wheel-layout">
        <section className="card">
          <label>
            Campaign
            <select
              value={selected}
              onChange={(event) => {
                setSelection(event.target.value);
                setResult(undefined);
              }}
            >
              <option value="">Select</option>
              {owned.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name} · {item.status}
                </option>
              ))}
            </select>
          </label>
          <LuckyWheel
            spinning={spinning}
            result={
              result
                ? `Winner: ${result.winner.userId}`
                : campaign?.status === 'DRAWN'
                  ? `Winner: ${stats.data?.winnerUserId ?? 'loading…'}`
                  : undefined
            }
          />
        </section>
        <section className="card">
          <h3>Draw controls</h3>
          <div className="metrics">
            <div>
              <strong>{stats.data?.totalEntries ?? 0}</strong>
              <span>Entries</span>
            </div>
            <div>
              <strong>{stats.data?.distinctParticipants ?? 0}</strong>
              <span>Participants</span>
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
            {campaign?.status === 'DRAWN' && (
              <button disabled={spinning} onClick={() => void draw()}>
                Replay recorded result
              </button>
            )}
          </div>
          {result && (
            <div className="draw-audit">
              <p>Selected entry: #{result.selectedIndex}</p>
              <p>
                Snapshot: <code>{result.snapshotHash}</code>
              </p>
            </div>
          )}
        </section>
      </div>
    </div>
  );
}
