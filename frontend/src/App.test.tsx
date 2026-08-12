import {
  act,
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { AuthProvider } from './auth';
import { LuckyWheel } from './components/LuckyWheel';
import { useResource } from './hooks/useResource';

describe('app routing', () => {
  beforeEach(() => {
    cleanup();
    localStorage.clear();
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
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

  it('shows the final wheel only to the seller', async () => {
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

    expect(
      await screen.findByText('Close the campaign, then pick one winner.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Lucky draw wheel')).toBeInTheDocument();
  });

  it('restores a drawn campaign winner without spinning again', async () => {
    localStorage.setItem(
      'lucky-draw-session',
      JSON.stringify({
        token: 'token',
        userId: 'seller-1',
        role: 'SELLER',
        expiresAt: Date.now() / 1000 + 3600,
      }),
    );
    const campaigns = [
      {
        id: 'drawn-campaign',
        sellerId: 'seller-1',
        name: 'Finished draw',
        status: 'DRAWN',
        maxEntriesPerUser: 2,
        startAt: '2026-08-01T00:00:00Z',
        endAt: '2026-08-02T00:00:00Z',
        reward: { type: 'COUPON', reference: 'SAVE-50' },
      },
      {
        id: 'ended-campaign',
        sellerId: 'seller-1',
        name: 'Ready draw',
        status: 'ENDED',
        maxEntriesPerUser: 2,
        startAt: '2026-08-03T00:00:00Z',
        endAt: '2026-08-04T00:00:00Z',
        reward: { type: 'PRODUCT', reference: 'HEADPHONES' },
      },
    ];
    const request = vi.fn((input: RequestInfo | URL) => {
      const path = String(input);
      const data = path.endsWith('/api/campaigns')
        ? campaigns
        : path.endsWith('/draw')
          ? {
              winner: {
                id: 'entry-1',
                userId: 'customer-1',
                ticketId: 'ticket-1',
                sequence: 1,
                submittedAt: '2026-08-01T01:00:00Z',
              },
              snapshotHash: 'hash',
              selectedIndex: 1,
            }
          : {
              campaignId: path.includes('drawn-campaign')
                ? 'drawn-campaign'
                : 'ended-campaign',
              totalEntries: 1,
              distinctParticipants: 1,
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

    expect(await screen.findByText('Winner: customer-1')).toBeInTheDocument();
    expect(screen.queryByText('Show recorded winner')).not.toBeInTheDocument();
    const campaignSelect = screen.getByLabelText('Campaign');
    fireEvent.change(campaignSelect, { target: { value: 'ended-campaign' } });
    expect(
      await screen.findByText('Ready to pick a winner'),
    ).toBeInTheDocument();
    fireEvent.change(campaignSelect, { target: { value: 'drawn-campaign' } });
    expect(screen.getByText('Winner: customer-1')).toBeInTheDocument();
    expect(
      request.mock.calls.filter(([input]) => String(input).endsWith('/draw')),
    ).toHaveLength(1);
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

  it('lets a customer submit one ticket without a wheel', async () => {
    localStorage.setItem(
      'lucky-draw-session',
      JSON.stringify({
        token: 'token',
        userId: 'customer-1',
        role: 'CUSTOMER',
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
        : path.endsWith('/api/tickets')
          ? [{ id: 'ticket-1234', orderId: 'order-1', status: 'ISSUED' }]
          : path.endsWith('/api/notifications')
            ? [
                {
                  id: 'notification-1',
                  campaignId: 'campaign-1',
                  entryId: 'entry-1',
                  message:
                    'Ticket submitted. Entry #48 is in campaign 5fd5a258-078f-4494-930e-3dc98801d82f.',
                  sentAt: '2026-08-09T01:00:00Z',
                },
                {
                  id: 'notification-2',
                  campaignId: 'campaign-1',
                  entryId: 'entry-1',
                  message:
                    'You won COUPON SAVE-50 in campaign 5fd5a258-078f-4494-930e-3dc98801d82f.',
                  sentAt: '2026-08-09T02:00:00Z',
                },
              ]
            : path.endsWith('/api/rewards')
              ? [
                  {
                    id: 'reward-1',
                    campaignId: 'campaign-1',
                    winnerEntryId: 'entry-1',
                    rewardType: 'COUPON',
                    reference: 'SAVE-50',
                    deliveryReference: 'SAVE-50-FC7B59C4',
                    deliveredAt: '2026-08-09T02:00:00Z',
                  },
                ]
              : path.endsWith('/entries')
                ? {
                    id: 'entry-1',
                    userId: 'customer-1',
                    ticketId: 'ticket-1234',
                    sequence: 1,
                    submittedAt: '2026-08-09T01:00:00Z',
                  }
                : path.includes('/analytics/')
                  ? {
                      campaignId: 'campaign-1',
                      entryIds: [],
                      remainingQuota: 3,
                      won: false,
                    }
                  : [];
      return Promise.resolve(
        new Response(JSON.stringify(data), {
          headers: { 'Content-Type': 'application/json' },
        }),
      );
    });
    vi.stubGlobal('fetch', request);
    const socketUrls: string[] = [];
    vi.stubGlobal(
      'WebSocket',
      class {
        onmessage: ((event: MessageEvent) => void) | null = null;
        constructor(url: string) {
          socketUrls.push(url);
        }
        close() {}
      },
    );

    render(
      <MemoryRouter initialEntries={['/lucky-draw']}>
        <AuthProvider>
          <App />
        </AuthProvider>
      </MemoryRouter>,
    );

    expect(screen.queryByLabelText(/Lucky draw wheel/)).not.toBeInTheDocument();
    expect(socketUrls).toContain(
      'ws://localhost:3000/ws/realtime?access_token=token',
    );
    expect(
      await screen.findByText('Ticket submitted to Demo.'),
    ).toBeInTheDocument();
    expect(
      screen.getByText('You won Coupon: SAVE-50 in Demo.'),
    ).toBeInTheDocument();
    expect(screen.getByText('Coupon: SAVE-50 - Delivered')).toBeInTheDocument();
    expect(
      screen.queryByText(/Entry #48|5fd5a258|FC7B59C4/),
    ).not.toBeInTheDocument();
    fireEvent.click(
      await screen.findByRole('button', { name: 'Submit ticket ticket-1' }),
    );

    await waitFor(() =>
      expect(request).toHaveBeenCalledWith(
        '/api/campaigns/campaign-1/entries',
        expect.objectContaining({
          method: 'POST',
          body: JSON.stringify({ ticketId: 'ticket-1234' }),
        }),
      ),
    );
  });
});

describe('resource loading', () => {
  it('clears old data and ignores stale responses', async () => {
    const resolvers: Record<string, (value: string) => void> = {};
    const load = (id: string) =>
      new Promise<string>((resolve) => {
        resolvers[id] = resolve;
      });
    function Probe({ id }: { id: string }) {
      const resource = useResource(() => load(id), [id]);
      return <span>{resource.data ?? 'Loading'}</span>;
    }

    const view = render(<Probe id="first" />);
    await act(async () => resolvers.first('First'));
    expect(screen.getByText('First')).toBeInTheDocument();
    view.rerender(<Probe id="second" />);
    expect(screen.getByText('Loading')).toBeInTheDocument();
    view.rerender(<Probe id="third" />);
    await act(async () => resolvers.third('Third'));
    expect(screen.getByText('Third')).toBeInTheDocument();
    await act(async () => resolvers.second('Second'));
    expect(screen.getByText('Third')).toBeInTheDocument();
    expect(screen.queryByText('Second')).not.toBeInTheDocument();
  });
});

describe('lucky wheel', () => {
  it('shows equal entry slots for the seller draw', () => {
    render(
      <LuckyWheel spinning={false} reward="COUPON: SAVE-50" result="Ready" />,
    );

    expect(screen.getAllByText('ENTRY')).toHaveLength(8);
    expect(
      screen.getByLabelText('Winner draw wheel for COUPON: SAVE-50'),
    ).toBeInTheDocument();
  });

  it('keeps the wheel on the server-selected segment', () => {
    render(
      <LuckyWheel spinning={false} segment={1} reward="PRODUCT: HEADPHONES" />,
    );

    expect(
      screen.getByLabelText('Winner draw wheel for PRODUCT: HEADPHONES'),
    ).toHaveClass('settled');
  });
});
