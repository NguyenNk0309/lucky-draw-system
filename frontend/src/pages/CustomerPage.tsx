import { useMemo, useState, type FormEvent } from 'react';
import { api, ApiError } from '../api';
import { useAuth } from '../auth';
import { Empty, ErrorNotice, Loading } from '../components/Feedback';
import { useResource } from '../hooks/useResource';
import type { Campaign, MyResult, Ticket } from '../types';

interface Notification {
  id: string;
  message: string;
  sentAt: string;
}
interface RewardClaim {
  id: string;
  reference: string;
  deliveryReference?: string;
  deliveredAt?: string;
}

export function CustomerPage() {
  const auth = useAuth();
  const [amount, setAmount] = useState('1200000');
  const [selected, setSelected] = useState('demo-campaign');
  const [message, setMessage] = useState('');
  const [actionError, setActionError] = useState('');
  const identity = useMemo(
    () => ({ userId: auth.userId, role: auth.role }),
    [auth.userId, auth.role],
  );
  const campaigns = useResource(
    () => api<Campaign[]>('/api/write/campaigns', identity),
    [identity],
  );
  const tickets = useResource(
    () => api<Ticket[]>('/api/write/tickets', identity),
    [identity],
  );
  const mine = useResource(
    () => api<MyResult>(`/api/analytics/campaigns/${selected}/me`, identity),
    [identity, selected],
  );
  const notifications = useResource(
    () => api<Notification[]>('/api/notifications', identity),
    [identity],
  );
  const rewards = useResource(
    () => api<RewardClaim[]>('/api/rewards', identity),
    [identity],
  );

  async function run(action: () => Promise<unknown>, success: string) {
    setActionError('');
    setMessage('');
    try {
      await action();
      setMessage(success);
      await Promise.all([
        tickets.refresh(),
        mine.refresh(),
        notifications.refresh(),
        rewards.refresh(),
      ]);
    } catch (reason) {
      const error = reason as ApiError;
      setActionError(
        error.code ? `${error.message} (${error.code})` : error.message,
      );
    }
  }

  function createOrder(event: FormEvent) {
    event.preventDefault();
    void run(
      () =>
        api('/api/orders', identity, {
          method: 'POST',
          body: JSON.stringify({ total: Number(amount) }),
        }),
      'Order created. A qualifying ticket will appear asynchronously.',
    );
  }

  const activeCampaigns =
    campaigns.data?.filter((campaign) => campaign.status === 'ACTIVE') ?? [];
  const availableTickets =
    tickets.data?.filter((ticket) => ticket.status === 'ISSUED') ?? [];

  return (
    <div className="stack">
      <section className="hero">
        <span className="eyebrow">Customer workspace</span>
        <h2>Earn a ticket. Enter a campaign. Follow the result.</h2>
        <p>
          Orders above 1,000,000 issue one ticket through the event pipeline.
        </p>
      </section>
      <ErrorNotice message={actionError} />
      {message && <p className="notice success">{message}</p>}

      <div className="grid two">
        <section className="card">
          <h3>Create demo order</h3>
          <form onSubmit={createOrder}>
            <label>
              Order total
              <input
                type="number"
                min="1"
                value={amount}
                onChange={(e) => setAmount(e.target.value)}
              />
            </label>
            <button>Create order</button>
          </form>
        </section>
        <section className="card">
          <div className="row">
            <h3>My tickets</h3>
            <button
              className="secondary"
              onClick={() => void tickets.refresh()}
            >
              Refresh
            </button>
          </div>
          <Loading active={tickets.loading} />
          <ErrorNotice message={tickets.error} />
          <Empty show={!tickets.loading && !tickets.data?.length}>
            No tickets yet.
          </Empty>
          <ul className="items">
            {tickets.data?.map((ticket) => (
              <li key={ticket.id}>
                <code>{ticket.id}</code>
                <span className={`pill ${ticket.status.toLowerCase()}`}>
                  {ticket.status}
                </span>
              </li>
            ))}
          </ul>
        </section>
      </div>

      <section className="card">
        <h3>Campaign entry</h3>
        <Loading active={campaigns.loading} />
        <ErrorNotice message={campaigns.error} />
        <label>
          Campaign
          <select
            value={selected}
            onChange={(e) => setSelected(e.target.value)}
          >
            {activeCampaigns.map((campaign) => (
              <option key={campaign.id} value={campaign.id}>
                {campaign.name}
              </option>
            ))}
          </select>
        </label>
        <div className="ticket-actions">
          {availableTickets.map((ticket) => (
            <button
              key={ticket.id}
              onClick={() =>
                void run(
                  () =>
                    api(`/api/write/campaigns/${selected}/entries`, identity, {
                      method: 'POST',
                      body: JSON.stringify({ ticketId: ticket.id }),
                    }),
                  'Entry submitted. The read model will catch up shortly.',
                )
              }
            >
              Use ticket {ticket.id.slice(0, 8)}
            </button>
          ))}
        </div>
        <Empty show={!availableTickets.length}>No available tickets.</Empty>
        <div className="metrics">
          <div>
            <strong>{mine.data?.entryIds.length ?? 0}</strong>
            <span>My entries</span>
          </div>
          <div>
            <strong>{mine.data?.remainingQuota ?? '—'}</strong>
            <span>Quota remaining</span>
          </div>
          <div>
            <strong>{mine.data?.won ? 'Winner' : 'Pending / not won'}</strong>
            <span>Result</span>
          </div>
        </div>
        <p className="muted">
          Projection updated:{' '}
          {mine.data?.lastUpdatedAt
            ? new Date(mine.data.lastUpdatedAt).toLocaleString()
            : 'not yet projected'}
        </p>
      </section>

      <div className="grid two">
        <section className="card">
          <h3>Notifications</h3>
          <Empty show={!notifications.data?.length}>No notifications.</Empty>
          <ul className="items">
            {notifications.data?.map((item) => (
              <li key={item.id}>{item.message}</li>
            ))}
          </ul>
        </section>
        <section className="card">
          <h3>Rewards</h3>
          <Empty show={!rewards.data?.length}>No reward claims.</Empty>
          <ul className="items">
            {rewards.data?.map((item) => (
              <li key={item.id}>
                {item.reference}
                <span>{item.deliveryReference ?? 'Processing'}</span>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}
