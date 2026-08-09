import { useState, type FormEvent } from 'react';
import { api } from '../api';
import { useAuth } from '../auth';
import { Empty, ErrorNotice, Loading } from '../components/Feedback';
import { useResource } from '../hooks/useResource';
import type { Campaign } from '../types';

export function CampaignsPage() {
  const { session } = useAuth();
  const token = session!.token;
  const [error, setError] = useState('');
  const [message, setMessage] = useState('');
  const campaigns = useResource(
    () => api<Campaign[]>('/api/campaigns', token),
    [token],
  );
  const owned =
    campaigns.data?.filter((item) => item.sellerId === session!.userId) ?? [];

  async function mutate(path: string, body?: object) {
    setError('');
    setMessage('');
    try {
      await api(path, token, {
        method: 'POST',
        body: body ? JSON.stringify(body) : undefined,
      });
      setMessage('Campaign updated.');
      await campaigns.refresh();
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Request failed');
    }
  }
  function create(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    void mutate('/api/campaigns', {
      name: form.get('name'),
      startAt: new Date().toISOString(),
      endAt: new Date(
        Date.now() + Number(form.get('minutes')) * 60_000,
      ).toISOString(),
      maxEntriesPerUser: Number(form.get('limit')),
      rewardType: form.get('rewardType'),
      rewardReference: form.get('rewardReference'),
    });
  }

  return (
    <div className="stack">
      <section className="hero">
        <span className="eyebrow">Campaign service</span>
        <h2>Configure campaigns and rewards.</h2>
        <p>
          This page calls the separate Campaign Service shown in the
          architecture.
        </p>
      </section>
      <ErrorNotice message={error} />
      {message && <p className="notice success">{message}</p>}
      <div className="grid two">
        <section className="card">
          <h3>Create campaign</h3>
          <form onSubmit={create}>
            <label>
              Name
              <input name="name" required placeholder="Campaign name" />
            </label>
            <div className="grid two">
              <label>
                Duration (minutes)
                <input name="minutes" type="number" min="1" required />
              </label>
              <label>
                Entries per user
                <input name="limit" type="number" min="1" required />
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
                placeholder="Coupon code or product reference"
              />
            </label>
            <button>Create draft</button>
          </form>
        </section>
        <section className="card">
          <div className="row">
            <h3>My campaigns</h3>
            <button
              className="secondary"
              onClick={() => void campaigns.refresh()}
            >
              Refresh
            </button>
          </div>
          <Loading active={campaigns.loading} />
          <ErrorNotice message={campaigns.error} />
          <Empty show={!owned.length}>No campaigns.</Empty>
          <ul className="campaign-list">
            {owned.map((campaign) => (
              <li key={campaign.id}>
                <div className="campaign-row">
                  <span>
                    <strong>{campaign.name}</strong>
                    <small>{new Date(campaign.endAt).toLocaleString()}</small>
                  </span>
                  <span className={`pill ${campaign.status.toLowerCase()}`}>
                    {campaign.status}
                  </span>
                </div>
                <div className="actions">
                  {campaign.status === 'DRAFT' && (
                    <button
                      onClick={() =>
                        void mutate(`/api/campaigns/${campaign.id}/activate`)
                      }
                    >
                      Publish
                    </button>
                  )}
                  {campaign.status === 'ACTIVE' && (
                    <button
                      onClick={() =>
                        void mutate(`/api/campaigns/${campaign.id}/end`)
                      }
                    >
                      End & release rewards
                    </button>
                  )}
                  {['DRAFT', 'ACTIVE'].includes(campaign.status) && (
                    <button
                      className="danger"
                      onClick={() =>
                        void mutate(`/api/campaigns/${campaign.id}/cancel`)
                      }
                    >
                      Cancel
                    </button>
                  )}
                </div>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}
