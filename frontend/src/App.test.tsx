import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { App } from './App';
import { AuthProvider } from './auth';

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
});
