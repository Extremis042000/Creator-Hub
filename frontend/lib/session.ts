/**
 * A random per-browser id, persisted in localStorage, used only to
 * correlate anonymous affiliate clicks server-side (AffiliateClick.sessionRef)
 * -- not an auth mechanism, and never sent anywhere but the redirect endpoint.
 */
export function getOrCreateSessionRef(): string {
  if (typeof window === "undefined") return "";
  try {
    const key = "sessionRef";
    let ref = localStorage.getItem(key);
    if (!ref) {
      ref = crypto.randomUUID();
      localStorage.setItem(key, ref);
    }
    return ref;
  } catch {
    return "";
  }
}
