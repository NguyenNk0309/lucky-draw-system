import {
  createContext,
  useContext,
  useMemo,
  useState,
  type PropsWithChildren,
} from 'react';
import type { DemoIdentity } from './api';
import type { Role } from './types';

interface AuthValue extends DemoIdentity {
  setRole: (role: Role) => void;
}

const AuthContext = createContext<AuthValue | null>(null);

export function AuthProvider({ children }: PropsWithChildren) {
  const [role, setRole] = useState<Role>('CUSTOMER');
  const value = useMemo(
    () => ({
      role,
      userId: role === 'CUSTOMER' ? 'customer-1' : 'seller-1',
      setRole,
    }),
    [role],
  );
  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);
  if (!value) throw new Error('AuthProvider is missing');
  return value;
}
