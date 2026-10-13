import { createContext, useContext, useMemo, useState } from 'react';
import { apiErrorMessage, basicToken, getStoredAuth, storeAuth } from '../api/client';
import { checkAuth } from '../api/endpoints';

const AuthContext = createContext(null);

/** Admin sign-in for upload/delete. Credentials live only in sessionStorage for this tab. */
export function AuthProvider({ children }) {
  const [user, setUser] = useState(() => (getStoredAuth() ? { username: 'admin' } : null));

  const value = useMemo(() => ({
    user,
    async login(username, password) {
      const token = basicToken(username, password);
      try {
        const me = await checkAuth(token);
        storeAuth(token);
        setUser({ username: me.username });
        return null;
      } catch (err) {
        return err.response?.status === 401 ? 'Incorrect username or password' : apiErrorMessage(err);
      }
    },
    logout() {
      storeAuth(null);
      setUser(null);
    },
  }), [user]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside AuthProvider');
  return ctx;
}
