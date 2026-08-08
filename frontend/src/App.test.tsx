import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it } from 'vitest';
import { App } from './App';
import { AuthProvider } from './auth';

describe('app routing', () => {
  beforeEach(() => localStorage.clear());
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
});
