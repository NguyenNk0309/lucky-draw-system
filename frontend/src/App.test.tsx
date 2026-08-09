import { render, screen } from '@testing-library/react';
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
