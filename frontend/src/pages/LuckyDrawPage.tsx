import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api, ApiError } from '../api';
import { useAuth } from '../auth';
import { Empty, ErrorNotice, Loading } from '../components/Feedback';
import { LuckyWheel } from '../components/LuckyWheel';
import { useResource } from '../hooks/useResource';
import type {
  Campaign,
  LuckyEntry,
  MyResult,
  Notification,
  PendingReward,
  RewardClaim,
  Stats,
  Ticket,
} from '../types';

const emptyMine: MyResult = {
  campaignId: '',
  entryIds: [],
  remainingQuota: 0,
  won: false,
  pendingRewards: 0,
  releasedRewards: 0,
  canceledRewards: 0,
};
const emptyStats: Stats = {
  campaignId: '',
  totalEntries: 0,
  distinctParticipants: 0,
  rewardWinners: 0,
  canceledRewards: 0,
};

export function LuckyDrawPage() {
  const { session } = useAuth();
  return session!.role === 'SELLER' ? (
    <SellerCampaignStatus />
  ) : (
    <CustomerWheel />
  );
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
  const [submitting, setSubmitting] = useState(false);
  const [spinning, setSpinning] = useState(false);
  const [wheelSegment, setWheelSegment] = useState<number>();
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
  const rewardLabel = campaign
    ? `${campaign.reward.type}: ${campaign.reward.reference}`
    : undefined;
  const available =
    tickets.data?.filter((ticket) => ticket.status === 'ISSUED') ?? [];

  async function spin(ticketId: string) {
    setError('');
    setWheelMessage('');
    setSubmitting(true);
    setSpinning(false);
    try {
      const entry = await api<LuckyEntry>(
        `/api/campaigns/${selected}/entries`,
        token,
        {
          method: 'POST',
          body: JSON.stringify({ ticketId }),
        },
      );
      setWheelSegment(entry.wheelSegment);
      setSpinning(true);
      await new Promise((resolve) => window.setTimeout(resolve, 2500));
      setWheelMessage(
        entry.rewardPending
          ? `🎉 You won ${rewardLabel}! Status: pending until the campaign ends.`
          : 'No prize this spin. Your entry was recorded.',
      );
      await tickets.refresh();
      window.setTimeout(() => void mine.refresh(), 700);
    } catch (reason) {
      const failure = reason as ApiError;
      setError(
        failure.code ? `${failure.message} (${failure.code})` : failure.message,
      );
    } finally {
      setSpinning(false);
      setSubmitting(false);
    }
  }

  const result =
    wheelMessage ||
    (mine.data?.rewardStatus === 'PENDING'
      ? `🎉 ${mine.data.reward?.reference} won · pending`
      : mine.data?.rewardStatus === 'DELIVERING'
        ? `${mine.data.reward?.reference} is being delivered`
        : mine.data?.rewardStatus === 'CANCELED'
          ? `${mine.data.reward?.reference} · CANCELED`
          : campaign?.status === 'DRAWN'
            ? 'Campaign completed · no reward'
            : undefined);
  return (
    <div className="stack">
      <section className="hero">
        <span className="eyebrow">Customer app · instant lucky wheel</span>
        <h2>Spin your ticket for a reward.</h2>
        <p>
          The server selects the wheel segment. A winning reward stays pending
          until the campaign ends, then delivery starts automatically.
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
                setWheelSegment(undefined);
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
          <LuckyWheel
            spinning={spinning}
            result={result || undefined}
            reward={rewardLabel}
            segment={wheelSegment}
          />
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
              Refresh result
            </button>
          </div>
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
                  disabled={submitting || spinning}
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
                  ? mine.data.rewardStatus === 'PENDING'
                    ? 'Reward pending'
                    : mine.data.rewardStatus === 'CANCELED'
                      ? 'Canceled'
                      : 'Being delivered'
                  : campaign?.status === 'DRAWN'
                    ? 'No reward'
                    : 'Ready'}
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
                <span>
                  {item.rewardType}: {item.reference}
                </span>
                <span>
                  {item.deliveryReference
                    ? `Being delivered · ${item.deliveryReference}`
                    : 'Preparing delivery'}
                </span>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}

function SellerCampaignStatus() {
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
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [canceling, setCanceling] = useState(false);
  const [checked, setChecked] = useState<string[]>([]);
  const stats = useResource(
    () =>
      selected
        ? api<Stats>(`/api/analytics/campaigns/${selected}/stats`, token)
        : Promise.resolve(emptyStats),
    [selected, token],
  );
  const campaign = owned.find((item) => item.id === selected);
  const pending = useResource(
    () =>
      selected
        ? api<PendingReward[]>(
            `/api/campaigns/${selected}/rewards/pending`,
            token,
          )
        : Promise.resolve([]),
    [selected, token],
  );

  async function end() {
    setError('');
    setNotice('');
    try {
      await api(`/api/campaigns/${selected}/end`, token, { method: 'POST' });
      await Promise.all([
        campaigns.refresh(),
        stats.refresh(),
        pending.refresh(),
      ]);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Request failed');
    }
  }

  async function cancelSelected() {
    setError('');
    setNotice('');
    setCanceling(true);
    try {
      await api(`/api/campaigns/${selected}/rewards/cancel`, token, {
        method: 'POST',
        body: JSON.stringify({ entryIds: checked }),
      });
      setNotice(
        `${checked.length} pending reward${checked.length === 1 ? '' : 's'} canceled.`,
      );
      setChecked([]);
      await Promise.all([pending.refresh(), stats.refresh()]);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Request failed');
    } finally {
      setCanceling(false);
    }
  }

  return (
    <div className="stack">
      <section className="hero">
        <span className="eyebrow">Seller portal · reward release</span>
        <h2>End the campaign and release pending rewards.</h2>
        <p>
          Customers spin their own wheel. When time expires—or you end the
          campaign—notifications and reward delivery start automatically.
        </p>
      </section>
      <ErrorNotice
        message={error || campaigns.error || stats.error || pending.error}
      />
      <section className="card">
        <div className="row">
          <label>
            Campaign
            <select
              value={selected}
              onChange={(event) => {
                setSelection(event.target.value);
                setChecked([]);
                setNotice('');
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
          <button
            className="secondary"
            onClick={() =>
              void Promise.all([
                campaigns.refresh(),
                stats.refresh(),
                pending.refresh(),
              ])
            }
          >
            Refresh
          </button>
        </div>
        <div className="metrics">
          <div>
            <strong>{stats.data?.totalEntries ?? 0}</strong>
            <span>Entries</span>
          </div>
          <div>
            <strong>{stats.data?.rewardWinners ?? 0}</strong>
            <span>Released rewards</span>
          </div>
          <div>
            <strong>{stats.data?.canceledRewards ?? 0}</strong>
            <span>Canceled rewards</span>
          </div>
          <div>
            <strong>{campaign?.status ?? '—'}</strong>
            <span>Status</span>
          </div>
        </div>
        {campaign?.status === 'ACTIVE' && (
          <div className="pending-rewards">
            <div className="row">
              <h3>Pending customer rewards</h3>
              <span className="pill active">
                {pending.data?.length ?? 0} pending
              </span>
            </div>
            <Empty show={!pending.loading && !pending.data?.length}>
              No pending rewards.
            </Empty>
            <div className="reward-options">
              {pending.data?.map((item) => (
                <label className="reward-option" key={item.entryId}>
                  <input
                    type="checkbox"
                    checked={checked.includes(item.entryId)}
                    onChange={(event) =>
                      setChecked((current) =>
                        event.target.checked
                          ? [...current, item.entryId]
                          : current.filter((id) => id !== item.entryId),
                      )
                    }
                  />
                  <span>
                    <strong>{campaign.reward.reference}</strong> for{' '}
                    {item.userId}
                    <small>
                      Entry #{item.sequence} ·{' '}
                      {new Date(item.wonAt).toLocaleString()}
                    </small>
                  </span>
                </label>
              ))}
            </div>
            {!!checked.length && (
              <button
                className="danger"
                disabled={canceling}
                onClick={() => void cancelSelected()}
              >
                Cancel selected ({checked.length})
              </button>
            )}
          </div>
        )}
        {notice && <p className="notice success">{notice}</p>}
        <div className="actions">
          {campaign && ['ACTIVE', 'ENDED'].includes(campaign.status) && (
            <button onClick={() => void end()}>
              {campaign.status === 'ENDED'
                ? 'Release pending rewards'
                : 'End campaign & release rewards'}
            </button>
          )}
        </div>
        {campaign?.status === 'DRAWN' && (
          <p className="notice success">
            Campaign completed. Winning customers are being notified and their
            rewards are being delivered.
          </p>
        )}
      </section>
    </div>
  );
}
