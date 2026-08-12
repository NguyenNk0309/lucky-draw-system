import {
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
