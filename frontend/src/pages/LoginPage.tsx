import { useState, type FormEvent } from 'react';
import { Navigate, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import { ErrorNotice } from '../components/Feedback';

export function LoginPage() {
  const auth = useAuth();
  const navigate = useNavigate();
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  if (auth.session) return <Navigate to="/" replace />;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setLoading(true);
    setError('');
    const form = new FormData(event.currentTarget);
    try {
      const session = await auth.login(
        String(form.get('username')),
        String(form.get('password')),
      );
      navigate(session.role === 'SELLER' ? '/campaigns' : '/shop', {
        replace: true,
      });
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Login failed');
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="login-shell">
      <section className="login-card">
        <span className="brand-mark">★</span>
        <span className="eyebrow">Demo marketplace</span>
        <h1>Lucky Shop</h1>
        <p className="muted">
          Buy products, earn tickets, and join a lucky draw.
        </p>
        <ErrorNotice message={error} />
        <form onSubmit={submit}>
          <label>
            Username
            <input
              name="username"
              autoComplete="username"
              required
              defaultValue="customer"
            />
          </label>
          <label>
            Password
            <input
              name="password"
              type="password"
              autoComplete="current-password"
              required
              defaultValue="customer123"
            />
          </label>
          <button disabled={loading}>
            {loading ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
        <div className="demo-credentials">
          <span>Customer: customer / customer123</span>
          <span>Seller: seller / seller123</span>
        </div>
      </section>
    </main>
  );
}
