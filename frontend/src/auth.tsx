import {
  createContext,
  useContext,
  useState,
  type PropsWithChildren,
} from 'react';
import { api } from './api';
import type { Role } from './types';

export interface Session {
  token: string;
  userId: string;
  role: Role;
  expiresAt: number;
}
interface AuthValue {
  session: Session | null;
  login: (username: string, password: string) => Promise<Session>;
  logout: () => void;
}
const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [session, setSession] = useState<Session | null>(() => {
    try {
      const saved = JSON.parse(
        localStorage.getItem('lucky-draw-session') ?? 'null',
      ) as Session | null;
      return saved && saved.expiresAt > Date.now() / 1000 ? saved : null;
    } catch {
      return null;
    }
  });

  async function login(username: string, password: string) {
    const next = await api<Session>('/auth/login', undefined, {
      method: 'POST',
      body: JSON.stringify({ username, password }),
    });
    localStorage.setItem('lucky-draw-session', JSON.stringify(next));
    setSession(next);
    return next;
  }
  function logout() {
    localStorage.removeItem('lucky-draw-session');
    setSession(null);
  }
  return (
    <AuthContext.Provider value={{ session, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('AuthProvider is missing');
  return value;
}
