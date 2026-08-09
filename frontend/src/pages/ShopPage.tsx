import { useState, type FormEvent } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { Empty, ErrorNotice, Loading } from '../components/Feedback';
import { useResource } from '../hooks/useResource';
import type { Order, Ticket } from '../types';

export function ShopPage() {
  const token = useAuth().session!.token;
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [creating, setCreating] = useState(false);
  const orders = useResource(() => api<Order[]>('/api/orders', token), [token]);
  const tickets = useResource(
    () => api<Ticket[]>('/api/tickets', token),
    [token],
  );

  async function createOrder(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = event.currentTarget;
    const total = Number(new FormData(form).get('total'));
    setCreating(true);
    setMessage('');
    setError('');
    try {
      await api('/api/orders', token, {
        method: 'POST',
        body: JSON.stringify({ total }),
      });
      setMessage(
        total > 1_000_000
          ? 'Order created. Your ticket is being issued.'
          : 'Order created. This order is below the ticket threshold.',
      );
      form.reset();
      await orders.refresh();
      window.setTimeout(() => void tickets.refresh(), 800);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Order failed');
    } finally {
      setCreating(false);
    }
  }

  const available =
    tickets.data?.filter((ticket) => ticket.status === 'ISSUED') ?? [];
  return (
    <div className="stack">
      <section className="shop-hero">
        <div>
          <span className="eyebrow">Order service</span>
          <h2>Create an order, earn a ticket, spin.</h2>
          <p>
            Every completed order above 1,000,000 earns one lucky-draw ticket
            asynchronously.
          </p>
        </div>
        <div className="ticket-badge">
          <strong>{available.length}</strong>
          <span>tickets ready</span>
          <Link to="/lucky-draw">Open wheel →</Link>
        </div>
      </section>
      <ErrorNotice message={error} />
      {message && <p className="notice success">{message}</p>}
      <section className="card">
        <h3>Create order</h3>
        <form onSubmit={createOrder}>
          <label>
            Order total (VND)
            <input
              name="total"
              type="number"
              min="1"
              step="1000"
              placeholder="Enter more than 1,000,000 for a ticket"
              required
            />
          </label>
          <button disabled={creating}>
            {creating ? 'Creating…' : 'Create order'}
          </button>
        </form>
      </section>
      <div className="grid two">
        <section className="card">
          <div className="row">
            <h3>My tickets</h3>
            <button
              className="secondary"
              onClick={() => void tickets.refresh()}
            >
              Refresh
            </button>
          </div>
          <Loading active={tickets.loading} />
          <Empty show={!tickets.loading && !tickets.data?.length}>
            No tickets yet.
          </Empty>
          <ul className="items">
            {tickets.data?.map((ticket) => (
              <li key={ticket.id}>
                <code>{ticket.id.slice(0, 12)}</code>
                <span className={`pill ${ticket.status.toLowerCase()}`}>
                  {ticket.status}
                </span>
              </li>
            ))}
          </ul>
        </section>
        <section className="card">
          <h3>Recent orders</h3>
          <Loading active={orders.loading} />
          <ErrorNotice message={orders.error} />
          <Empty show={!orders.loading && !orders.data?.length}>
            No orders.
          </Empty>
          <ul className="items">
            {orders.data?.slice(0, 5).map((order) => (
              <li key={order.id}>
                <span>
                  <strong>₫{Number(order.total).toLocaleString()}</strong>
                  <small>{new Date(order.createdAt).toLocaleString()}</small>
                </span>
                <code>{order.id.slice(0, 8)}</code>
              </li>
            ))}
          </ul>
        </section>
      </div>
    </div>
  );
}
