import { useState } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { Empty, ErrorNotice, Loading } from '../components/Feedback';
import { useResource } from '../hooks/useResource';
import type { Campaign, Stats } from '../types';

const empty: Stats = {
  campaignId: '',
  totalEntries: 0,
  distinctParticipants: 0,
  rewardWinners: 0,
};
export function AnalyticsPage() {
  const { session } = useAuth();
  const token = session!.token;
  const campaigns = useResource(
    () => api<Campaign[]>('/api/campaigns', token),
    [token],
  );
  const owned =
    campaigns.data?.filter((item) => item.sellerId === session!.userId) ?? [];
  const [selection, setSelection] = useState('');
  const selected = selection || owned[0]?.id || '';
  const stats = useResource(
    () =>
      selected
        ? api<Stats>(`/api/analytics/campaigns/${selected}/stats`, token)
        : Promise.resolve(empty),
    [selected, token],
  );
  return (
    <div className="stack">
      <section className="hero">
        <span className="eyebrow">Analytics read service</span>
        <h2>Campaign performance from Redis.</h2>
        <p>
          The read API and idempotent projector are combined in Analytics
          Service.
        </p>
      </section>
      <section className="card">
        <div className="row">
          <label>
            Campaign
            <select
              value={selected}
              onChange={(event) => setSelection(event.target.value)}
            >
              <option value="">Select</option>
              {owned.map((item) => (
                <option key={item.id} value={item.id}>
                  {item.name}
                </option>
              ))}
            </select>
          </label>
          <button className="secondary" onClick={() => void stats.refresh()}>
            Refresh
          </button>
        </div>
        <Loading active={campaigns.loading || stats.loading} />
        <ErrorNotice message={campaigns.error || stats.error} />
        <Empty show={!owned.length}>No campaigns.</Empty>
        {selected && (
          <>
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
                <strong>{stats.data?.rewardWinners ?? 0}</strong>
                <span>Reward winners</span>
              </div>
            </div>
            <p className="muted">
              Last projected:{' '}
              {stats.data?.lastUpdatedAt
                ? new Date(stats.data.lastUpdatedAt).toLocaleString()
                : 'not yet'}
            </p>
          </>
        )}
      </section>
    </div>
  );
}
