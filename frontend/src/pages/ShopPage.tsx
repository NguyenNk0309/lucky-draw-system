import { useState } from 'react';
import { Link } from 'react-router-dom';
import { api } from '../api';
import { useAuth } from '../auth';
import { Empty, ErrorNotice, Loading } from '../components/Feedback';
import { useResource } from '../hooks/useResource';
import type { Order, Ticket } from '../types';

const products = [
  {
    name: 'Noise-cancelling Headphones',
    icon: '🎧',
    price: 1_290_000,
    detail: 'Qualifies for 1 draw ticket',
  },
  {
    name: 'Active Smart Watch',
    icon: '⌚',
    price: 1_590_000,
    detail: 'Qualifies for 1 draw ticket',
  },
  {
    name: 'Mechanical Keyboard',
    icon: '⌨️',
    price: 1_150_000,
    detail: 'Qualifies for 1 draw ticket',
  },
  {
    name: 'Fast-charge Cable',
    icon: '🔌',
    price: 290_000,
    detail: 'Does not qualify',
  },
];

export function ShopPage() {
  const token = useAuth().session!.token;
  const [message, setMessage] = useState('');
  const [error, setError] = useState('');
  const [buying, setBuying] = useState('');
  const orders = useResource(() => api<Order[]>('/api/orders', token), [token]);
  const tickets = useResource(
    () => api<Ticket[]>('/api/tickets', token),
    [token],
  );

  async function buy(product: (typeof products)[number]) {
    setBuying(product.name);
    setMessage('');
    setError('');
    try {
      await api('/api/orders', token, {
        method: 'POST',
        body: JSON.stringify({ total: product.price }),
      });
      setMessage(
        product.price > 1_000_000
          ? `${product.name} ordered. Your ticket is being issued.`
          : `${product.name} ordered. This order is below the ticket threshold.`,
      );
      await orders.refresh();
      window.setTimeout(() => void tickets.refresh(), 800);
    } catch (reason) {
      setError(reason instanceof Error ? reason.message : 'Order failed');
    } finally {
      setBuying('');
    }
  }

  const available =
    tickets.data?.filter((ticket) => ticket.status === 'ISSUED') ?? [];
  return (
    <div className="stack">
      <section className="shop-hero">
        <div>
          <span className="eyebrow">Order service</span>
          <h2>Shop, earn a ticket, enter.</h2>
          <p>
            Every completed order above 1,000,000 earns one lucky-draw ticket
            asynchronously.
          </p>
        </div>
        <div className="ticket-badge">
          <strong>{available.length}</strong>
          <span>tickets ready</span>
          <Link to="/lucky-draw">Submit tickets →</Link>
        </div>
      </section>
      <ErrorNotice message={error} />
      {message && <p className="notice success">{message}</p>}
      <section>
        <div className="section-title">
          <h3>Featured products</h3>
          <span>Mock catalog</span>
        </div>
        <div className="product-grid">
          {products.map((product) => (
            <article className="product-card" key={product.name}>
              <div className="product-art" aria-hidden="true">
                {product.icon}
              </div>
              <h3>{product.name}</h3>
              <p>{product.detail}</p>
              <strong className="price">
                ₫{product.price.toLocaleString()}
              </strong>
              <button
                disabled={Boolean(buying)}
                onClick={() => void buy(product)}
              >
                {buying === product.name ? 'Ordering…' : 'Buy now'}
              </button>
            </article>
          ))}
        </div>
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
