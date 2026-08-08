import { useMemo, useState, type FormEvent } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { Empty, ErrorNotice, Loading } from '../components/Feedback';
import { useResource } from '../hooks/useResource';
import type { Campaign, Stats } from '../types';

export function AdminPage() {
  const auth = useAuth();
  const identity = useMemo(
    () => ({ userId: auth.userId, role: auth.role }),
    [auth.userId, auth.role],
  );
  const [selected, setSelected] = useState('demo-campaign');
  const [error, setError] = useState('');
  const [result, setResult] = useState('');
  const campaigns = useResource(
    () => api<Campaign[]>('/api/write/campaigns', identity),
    [identity],
  );
  const stats = useResource(
    () => api<Stats>(`/api/analytics/campaigns/${selected}/stats`, identity),
    [identity, selected],
  );

  async function mutate(path: string, body?: object) {
    setError('');
    setResult('');
    try {
      const response = await api<{
        snapshotHash?: string;
        winner?: { userId: string };
      }>(path, identity, {
        method: 'POST',
        body: body ? JSON.stringify(body) : undefined,
      });
      setResult(
        response?.snapshotHash
          ? `Winner ${response.winner?.userId}; snapshot ${response.snapshotHash}`
          : 'Campaign updated.',
      );
      await Promise.all([campaigns.refresh(), stats.refresh()]);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Request failed');
    }
  }

  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    const startAt = new Date().toISOString();
    const endAt = new Date(
      Date.now() + Number(form.get('minutes')) * 60_000,
    ).toISOString();
    void mutate('/api/write/campaigns', {
      name: form.get('name'),
      startAt,
      endAt,
      maxEntriesPerUser: Number(form.get('limit')),
      rewardType: form.get('rewardType'),
      rewardReference: form.get('rewardReference'),
    });
  }

  const owned =
    campaigns.data?.filter((campaign) => campaign.sellerId === auth.userId) ??
    [];
  const campaign = owned.find((item) => item.id === selected);

  return (
    <div className="stack">
      <section className="hero">
        <span className="eyebrow">Seller workspace</span>
        <h2>Configure, monitor, close, and draw.</h2>
        <p>
          Dashboard values come from the eventually consistent Redis projection.
        </p>
      </section>
      <ErrorNotice message={error} />
      {result && <p className="notice success">{result}</p>}
      <div className="grid two">
        <section className="card">
          <h3>Create campaign</h3>
          <form onSubmit={create}>
            <label>
              Name
              <input name="name" required defaultValue="Weekend Prize Draw" />
            </label>
            <div className="grid two">
              <label>
                Duration (minutes)
                <input name="minutes" type="number" min="1" defaultValue="30" />
              </label>
              <label>
                Entries per user
                <input name="limit" type="number" min="1" defaultValue="2" />
              </label>
            </div>
            <label>
              Reward type
              <select name="rewardType">
                <option>COUPON</option>
                <option>PRODUCT</option>
              </select>
            </label>
            <label>
              Reward reference
              <input
                name="rewardReference"
                required
                defaultValue="WELCOME-50"
              />
            </label>
            <button>Create draft</button>
          </form>
        </section>
        <section className="card">
          <h3>My campaigns</h3>
          <Loading active={campaigns.loading} />
          <ErrorNotice message={campaigns.error} />
          <Empty show={!owned.length}>No campaigns.</Empty>
          <ul className="campaign-list">
            {owned.map((item) => (
              <li key={item.id}>
                <button
                  className={selected === item.id ? 'selected' : 'secondary'}
                  onClick={() => setSelected(item.id)}
                >
                  <span>{item.name}</span>
                  <span className={`pill ${item.status.toLowerCase()}`}>
                    {item.status}
                  </span>
                </button>
              </li>
            ))}
          </ul>
        </section>
      </div>
      {campaign && (
        <section className="card">
          <div className="row">
            <div>
              <span className="eyebrow">{campaign.status}</span>
              <h3>{campaign.name}</h3>
            </div>
            <button className="secondary" onClick={() => void stats.refresh()}>
              Refresh projection
            </button>
          </div>
          <div className="metrics">
            <div>
              <strong>{stats.data?.totalEntries ?? 0}</strong>
              <span>Total entries</span>
            </div>
            <div>
              <strong>{stats.data?.distinctParticipants ?? 0}</strong>
              <span>Participants</span>
            </div>
            <div>
              <strong>{campaign.maxEntriesPerUser}</strong>
              <span>User cap</span>
            </div>
          </div>
          <p className="muted">
            Projection updated:{' '}
            {stats.data?.lastUpdatedAt
              ? new Date(stats.data.lastUpdatedAt).toLocaleString()
              : 'not yet projected'}
          </p>
          <div className="actions">
            {campaign.status === 'DRAFT' && (
              <button
                onClick={() =>
                  void mutate(`/api/write/campaigns/${campaign.id}/activate`)
                }
              >
                Publish
              </button>
            )}
            {campaign.status === 'ACTIVE' && (
              <button
                onClick={() =>
                  void mutate(`/api/write/campaigns/${campaign.id}/end`)
                }
              >
                End and freeze snapshot
              </button>
            )}
            {campaign.status === 'ENDED' && (
              <button
                onClick={() =>
                  void mutate(`/api/write/campaigns/${campaign.id}/draw`)
                }
              >
                Draw winner
              </button>
            )}
            {['DRAFT', 'ACTIVE'].includes(campaign.status) && (
              <button
                className="danger"
                onClick={() =>
                  void mutate(`/api/write/campaigns/${campaign.id}/cancel`)
                }
              >
                Cancel
              </button>
            )}
          </div>
          {campaign.snapshotHash && (
            <p>
              <strong>Snapshot hash:</strong>{' '}
              <code>{campaign.snapshotHash}</code>
            </p>
          )}
        </section>
      )}
    </div>
  );
}
