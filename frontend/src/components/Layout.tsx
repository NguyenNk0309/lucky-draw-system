import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth';

export function Layout() {
  const auth = useAuth();
  const session = auth.session!;
  return (
    <>
      <header>
        <div>
          <span className="eyebrow">Demo marketplace</span>
          <h1>Lucky Shop</h1>
        </div>
        <nav aria-label="Primary">
          {session.role === 'CUSTOMER' ? (
            <NavLink to="/shop">Shop & orders</NavLink>
          ) : (
            <>
              <NavLink to="/campaigns">Campaigns</NavLink>
              <NavLink to="/analytics">Analytics</NavLink>
            </>
          )}
          <NavLink to="/lucky-draw">
            {session.role === 'CUSTOMER' ? 'Lucky wheel' : 'Reward release'}
          </NavLink>
        </nav>
        <div className="identity">
          <span>{session.userId}</span>
          <button className="secondary" onClick={auth.logout}>
            Sign out
          </button>
        </div>
      </header>
      <main>
        <Outlet />
      </main>
      <footer>
        Campaign → order → ticket → wheel outcome → notification → reward
      </footer>
    </>
  );
}
