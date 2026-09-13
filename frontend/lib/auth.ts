const TOKEN_KEY = "extremis_auth_token";

/**
 * Stored in localStorage, not an httpOnly cookie — a deliberate MVP
 * simplicity choice (see backend JwtService), since no authenticated
 * route yet handles payment or highly sensitive data. Revisit if/when
 * Phase 20 (payments) raises the stakes.
 */
export function getToken(): string | null {
  if (typeof window === "undefined") return null;
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export function setToken(token: string) {
  try {
    localStorage.setItem(TOKEN_KEY, token);
  } catch {
    // localStorage can be unavailable (private browsing, blocked
    // site data) — sign-in just won't persist across reloads.
  }
}

export function clearToken() {
  try {
    localStorage.removeItem(TOKEN_KEY);
  } catch {
    // see getToken
  }
}
