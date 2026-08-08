import { NavLink, Outlet, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth';
import type { Role } from '../types';

export function Layout() {
  const auth = useAuth();
  const navigate = useNavigate();

  function changeRole(role: Role) {
    auth.setRole(role);
    navigate(role === 'CUSTOMER' ? '/customer' : '/admin');
  }

  return (
    <>
      <header>
        <div>
          <span className="eyebrow">Marketplace loyalty</span>
          <h1>Lucky Draw</h1>
        </div>
        <nav aria-label="Primary">
          <NavLink to="/customer">Customer</NavLink>
          <NavLink to="/admin">Seller</NavLink>
        </nav>
        <label className="identity">
          Demo role
          <select
            value={auth.role}
            onChange={(event) => changeRole(event.target.value as Role)}
          >
            <option value="CUSTOMER">Customer · customer-1</option>
            <option value="SELLER">Seller · seller-1</option>
          </select>
        </label>
      </header>
      <main>
        <Outlet />
      </main>
      <footer>
        Event-driven demo · analytics may update shortly after a command.
      </footer>
    </>
  );
}
