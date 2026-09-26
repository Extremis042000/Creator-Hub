"use client";

import { usePathname } from "next/navigation";
import { useEffect, useRef, useState } from "react";

const LOADER_TEXT = "CREATOR HUB";
/** Guarantees the animation is actually perceptible even on a near-instant navigation, instead of flashing imperceptibly. */
const MIN_VISIBLE_MS = 550;
/** Safety net: if a click doesn't lead to a real navigation (e.g. something intercepts it), never leave this stuck on screen. */
const MAX_VISIBLE_MS = 4000;

/**
 * A branded transition splash between page navigations -- transparent
 * (blurred, not opaque) so the outgoing page stays faintly visible
 * underneath, matching how the founder described it. There's no single
 * built-in App Router hook for "a client-side navigation just started"
 * across Next.js versions, so this listens for clicks on internal links
 * directly and clears itself once the pathname actually changes (with a
 * minimum hold time so it's never too brief to see).
 */
export default function RouteLoadingOverlay() {
  const pathname = usePathname();
  const pathnameRef = useRef(pathname);
  const [visible, setVisible] = useState(false);
  const shownAtRef = useRef<number | null>(null);
  const maxTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // The route finished changing -- hide, but never before MIN_VISIBLE_MS
  // has elapsed since it was shown, so fast navigations still animate.
  useEffect(() => {
    pathnameRef.current = pathname;
    if (shownAtRef.current === null) return;
    const elapsed = Date.now() - shownAtRef.current;
    const remaining = MIN_VISIBLE_MS - elapsed;
    if (maxTimerRef.current) clearTimeout(maxTimerRef.current);

    if (remaining <= 0) {
      setVisible(false);
      shownAtRef.current = null;
      return;
    }
    const timer = setTimeout(() => {
      setVisible(false);
      shownAtRef.current = null;
    }, remaining);
    return () => clearTimeout(timer);
  }, [pathname]);

  // Subscribed once, not re-bound per navigation -- reads the latest
  // pathname via the ref above rather than depending on it directly.
  useEffect(() => {
    function handleClick(event: MouseEvent) {
      // Note: NOT checking event.defaultPrevented -- next/link always calls
      // preventDefault() on the native click (that's how it intercepts the
      // browser's own navigation to do client-side routing instead), so
      // that check would incorrectly skip every real Link click.
      if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) {
        return;
      }
      const anchor = (event.target as HTMLElement)?.closest?.("a");
      if (!anchor) return;

      const href = anchor.getAttribute("href");
      if (!href || href.startsWith("#") || href.startsWith("mailto:") || href.startsWith("tel:")) return;
      if (anchor.target && anchor.target !== "_self") return;

      let destinationPath: string;
      try {
        destinationPath = new URL(href, window.location.origin).pathname;
      } catch {
        return;
      }
      // External links and same-page anchors never navigate to a new route here.
      if (anchor.origin !== window.location.origin || destinationPath === pathnameRef.current) return;

      shownAtRef.current = Date.now();
      setVisible(true);
      if (maxTimerRef.current) clearTimeout(maxTimerRef.current);
      maxTimerRef.current = setTimeout(() => {
        setVisible(false);
        shownAtRef.current = null;
      }, MAX_VISIBLE_MS);
    }

    document.addEventListener("click", handleClick);
    return () => document.removeEventListener("click", handleClick);
  }, []);

  if (!visible) return null;

  return (
    <div
      aria-hidden="true"
      className="fixed inset-0 z-50 flex items-center justify-center bg-bg-canvas/70 backdrop-blur-md"
    >
      <div className="flex gap-[0.1em] text-2xl font-bold tracking-[0.15em] sm:text-4xl">
        {LOADER_TEXT.split("").map((char, index) =>
          char === " " ? (
            <span key={index} className="inline-block w-2 sm:w-4" />
          ) : (
            <span
              key={index}
              className="inline-block animate-loader-letter"
              style={{ animationDelay: `${index * 0.06}s` }}
            >
              {char}
            </span>
          ),
        )}
      </div>
    </div>
  );
}
