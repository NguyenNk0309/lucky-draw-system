import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { AuthProvider } from './auth';
import { LuckyWheel } from './components/LuckyWheel';

describe('app routing', () => {
  beforeEach(() => {
    localStorage.clear();
    vi.restoreAllMocks();
  });
  it('redirects anonymous customers to login', async () => {
    render(
      <MemoryRouter initialEntries={['/shop']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );
    expect(
      await screen.findByRole('button', { name: 'Sign in' }),
    ).toBeInTheDocument();
  });

  it('shows the frontend-only mock product catalog', async () => {
    localStorage.setItem(
      'lucky-draw-session',
      JSON.stringify({
        token: 'token',
        userId: 'customer-1',
        role: 'CUSTOMER',
        expiresAt: Date.now() / 1000 + 3600,
      }),
    );
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(
          new Response(JSON.stringify([]), {
            headers: { 'Content-Type': 'application/json' },
          }),
        ),
      ),
    );

    render(
      <MemoryRouter initialEntries={['/shop']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(
      await screen.findByText('Noise-cancelling Headphones'),
    ).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Buy now' })).toHaveLength(4);
  });

  it('shows reward release controls instead of a seller wheel', async () => {
    localStorage.setItem(
      'lucky-draw-session',
      JSON.stringify({
        token: 'token',
        userId: 'seller-1',
        role: 'SELLER',
        expiresAt: Date.now() / 1000 + 3600,
      }),
    );
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(
          new Response(JSON.stringify([]), {
            headers: { 'Content-Type': 'application/json' },
          }),
        ),
      ),
    );

    render(
      <MemoryRouter initialEntries={['/lucky-draw']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(await screen.findByText('Reward release')).toBeInTheDocument();
    expect(screen.queryByLabelText(/Lucky draw wheel/)).not.toBeInTheDocument();
  });

  it('creates a campaign from selected dates', async () => {
    localStorage.setItem(
      'lucky-draw-session',
      JSON.stringify({
        token: 'token',
        userId: 'seller-1',
        role: 'SELLER',
        expiresAt: Date.now() / 1000 + 3600,
      }),
    );
    const request = vi.fn(() =>
      Promise.resolve(
        new Response(JSON.stringify([]), {
          headers: { 'Content-Type': 'application/json' },
        }),
      ),
    );
    vi.stubGlobal('fetch', request);

    render(
      <MemoryRouter initialEntries={['/campaigns']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );

    fireEvent.change(await screen.findByLabelText('Name'), {
      target: { value: 'August draw' },
    });
    fireEvent.change(screen.getByLabelText('From date'), {
      target: { value: '2026-08-12T09:00' },
    });
    fireEvent.change(screen.getByLabelText('To date'), {
      target: { value: '2026-08-19T18:00' },
    });
    fireEvent.change(screen.getByLabelText('Entries per user'), {
      target: { value: '2' },
    });
    fireEvent.change(screen.getByLabelText('Reward reference'), {
      target: { value: 'SAVE-50' },
    });
    fireEvent.click(screen.getByRole('button', { name: 'Create draft' }));

    await waitFor(() =>
      expect(request).toHaveBeenCalledWith(
        '/api/campaigns',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({
            name: 'August draw',
            startAt: new Date('2026-08-12T09:00').toISOString(),
            endAt: new Date('2026-08-19T18:00').toISOString(),
            maxEntriesPerUser: 2,
            rewardType: 'COUPON',
            rewardReference: 'SAVE-50',
          }),
        }),
      ),
    );
  });

  it('lets a seller select and cancel several pending rewards', async () => {
    localStorage.setItem(
      'lucky-draw-session',
      JSON.stringify({
        token: 'token',
        userId: 'seller-1',
        role: 'SELLER',
        expiresAt: Date.now() / 1000 + 3600,
      }),
    );
    const request = vi.fn((input: RequestInfo | URL) => {
      const path = String(input);
      const data = path.endsWith('/api/campaigns')
        ? [
            {
              id: 'campaign-1',
              sellerId: 'seller-1',
              name: 'Demo',
              status: 'ACTIVE',
              maxEntriesPerUser: 3,
              startAt: '2026-08-09T00:00:00Z',
              endAt: '2026-08-10T00:00:00Z',
              reward: { type: 'COUPON', reference: 'SAVE-50' },
            },
          ]
        : path.endsWith('/rewards/pending')
          ? [
              {
                entryId: 'entry-1',
                userId: 'customer-1',
                sequence: 1,
                wonAt: '2026-08-09T01:00:00Z',
              },
              {
                entryId: 'entry-2',
                userId: 'customer-1',
                sequence: 2,
                wonAt: '2026-08-09T01:01:00Z',
              },
            ]
          : path.endsWith('/rewards/cancel')
            ? { canceledEntryIds: ['entry-1', 'entry-2'] }
            : {
                campaignId: 'campaign-1',
                totalEntries: 2,
                distinctParticipants: 1,
                rewardWinners: 0,
                canceledRewards: 0,
              };
      return Promise.resolve(
        new Response(JSON.stringify(data), {
          headers: { 'Content-Type': 'application/json' },
        }),
      );
    });
    vi.stubGlobal('fetch', request);

    render(
      <MemoryRouter initialEntries={['/lucky-draw']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );

    const rewards = await screen.findAllByRole('checkbox');
    fireEvent.click(rewards[0]);
    fireEvent.click(rewards[1]);
    fireEvent.click(
      screen.getByRole('button', { name: 'Cancel selected (2)' }),
    );

    await waitFor(() =>
      expect(request).toHaveBeenCalledWith(
        '/api/campaigns/campaign-1/rewards/cancel',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ entryIds: ['entry-1', 'entry-2'] }),
        }),
      ),
    );
  });
});

describe('lucky wheel', () => {
  it('writes the configured reward on prize segments', () => {
    render(
      <LuckyWheel spinning={false} reward="COUPON: SAVE-50" result="Ready" />,
    );

    expect(screen.getAllByText('COUPON: SAVE-50')).toHaveLength(2);
    expect(
      screen.getByLabelText('Lucky draw wheel with reward COUPON: SAVE-50'),
    ).toBeInTheDocument();
  });

  it('keeps the wheel on the server-selected segment', () => {
    render(
      <LuckyWheel spinning={false} segment={1} reward="PRODUCT: HEADPHONES" />,
    );

    expect(
      screen.getByLabelText('Lucky draw wheel with reward PRODUCT: HEADPHONES'),
    ).toHaveClass('settled');
  });
});
