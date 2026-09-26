"use client";

import { useEffect, useRef, useState, type ReactNode } from "react";

const INTERVAL_MS = 3000;
/** Matches .animate-showcase-exit's duration in globals.css -- the outgoing slide is unmounted once its exit animation finishes. */
const EXIT_MS = 320;

export type ShowcaseSlide = { key: string; label: string; node: ReactNode };

/**
 * A rotating showcase replacing the old always-on "Hot Game Deals"
 * panel in the homepage hero -- cycles through every non-empty section
 * (All Tools, Premium Tools, Coming Soon, Hot Game Deals, Recommended
 * Gear, Our Store) every 3s in a continuous loop. Slides are pre-built
 * server-side (see app/page.tsx) and handed in as plain nodes; this
 * component only owns the rotation, the crossfade, and the pause
 * conditions -- it never fetches anything itself.
 *
 * Pauses auto-advance (but leaves manual dot navigation available)
 * on hover, on keyboard focus inside the panel, when the tab isn't
 * visible, and under prefers-reduced-motion -- a marketing carousel
 * that keeps spinning while a screen reader user is trying to read it,
 * or while the tab is backgrounded, is a real accessibility/attention
 * cost for zero benefit to anyone.
 */
export default function HomeShowcase({ slides }: { slides: ShowcaseSlide[] }) {
  const [current, setCurrent] = useState(0);
  const [previous, setPrevious] = useState<number | null>(null);
  const [animKey, setAnimKey] = useState(0);
  const [hovering, setHovering] = useState(false);
  const [tabHidden, setTabHidden] = useState(false);
  const [reducedMotion, setReducedMotion] = useState(false);
  const exitTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // Always holds the latest `current` for the interval callback below,
  // so the interval never needs to be torn down and recreated just to
  // see a fresh value -- one stable timer for the whole auto-advance
  // window instead of one per tick.
  const currentRef = useRef(current);
  currentRef.current = current;

  useEffect(() => {
    const mq = window.matchMedia("(prefers-reduced-motion: reduce)");
    setReducedMotion(mq.matches);
    const onChange = (e: MediaQueryListEvent) => setReducedMotion(e.matches);
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);

  useEffect(() => {
    setTabHidden(document.hidden); // capture the real state at mount -- a tab opened in the background must start paused, not wait for the first visibilitychange
    const onVisibility = () => setTabHidden(document.hidden);
    document.addEventListener("visibilitychange", onVisibility);
    return () => document.removeEventListener("visibilitychange", onVisibility);
  }, []);

  useEffect(() => () => {
    if (exitTimeoutRef.current) clearTimeout(exitTimeoutRef.current);
  }, []);

  const autoAdvance = slides.length > 1 && !hovering && !tabHidden && !reducedMotion;

  function goTo(next: number) {
    setCurrent((prevCurrent) => {
      if (next === prevCurrent) return prevCurrent;
      setPrevious(prevCurrent);
      if (exitTimeoutRef.current) clearTimeout(exitTimeoutRef.current);
      exitTimeoutRef.current = setTimeout(() => setPrevious(null), EXIT_MS);
      return next;
    });
    setAnimKey((k) => k + 1);
  }

  useEffect(() => {
    if (!autoAdvance) return;
    const timer = setInterval(() => {
      goTo((currentRef.current + 1) % slides.length);
    }, INTERVAL_MS);
    return () => clearInterval(timer);
    // goTo is stable across renders (defined with no closed-over state other than refs/setters); only these two actually need to restart the timer.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [autoAdvance, slides.length]);

  if (slides.length === 0) return null;

  return (
    <div
      className="relative"
      onMouseEnter={() => setHovering(true)}
      onMouseLeave={() => setHovering(false)}
      onFocus={() => setHovering(true)}
      onBlur={() => setHovering(false)}
    >
      {/* The rotating glow: the OUTER box stays fixed and clips (overflow-hidden)
          so its rounded-rect shape never itself rotates (a rotated rectangle's
          corners would swing outside the card); only the oversized inner square
          -- big enough to fully cover the clip area at any angle -- spins. */}
      <div aria-hidden="true" className="absolute -inset-0.5 overflow-hidden rounded-xl opacity-70">
        <div
          className="absolute left-1/2 top-1/2 h-[220%] w-[220%] -translate-x-1/2 -translate-y-1/2 animate-showcase-ring"
          style={{
            background:
              "conic-gradient(from 0deg, var(--color-brand-primary), var(--color-brand-accent), var(--color-brand-primary))",
          }}
        />
      </div>
      <div className="relative rounded-xl bg-bg-canvas p-[3px]">
        <div className="relative overflow-hidden rounded-[10px] border border-border-subtle bg-bg-surface p-4">
          {slides.length > 1 && (
            <div className="absolute inset-x-0 top-0 h-0.5 overflow-hidden bg-border-subtle">
              <div
                key={animKey}
                className="h-full w-full origin-left bg-brand-accent"
                style={
                  autoAdvance
                    ? { animation: `showcase-progress-fill ${INTERVAL_MS}ms linear forwards` }
                    : { transform: "scaleX(0)" }
                }
              />
            </div>
          )}

          <div className="relative min-h-[260px]">
            {previous !== null && (
              <div className="absolute inset-0 animate-showcase-exit" aria-hidden="true">
                {slides[previous].node}
              </div>
            )}
            <div key={animKey} className="relative animate-showcase-enter">
              {slides[current].node}
            </div>
          </div>

          {slides.length > 1 && (
            <div className="mt-3 flex justify-center gap-1.5" role="tablist" aria-label="Showcase sections">
              {slides.map((s, i) => (
                <button
                  key={s.key}
                  type="button"
                  role="tab"
                  aria-selected={i === current}
                  aria-label={`Show ${s.label}`}
                  onClick={() => goTo(i)}
                  className={`h-1.5 rounded-full transition-all duration-300 ${
                    i === current ? "w-5 bg-brand-accent" : "w-1.5 bg-border-strong hover:bg-text-muted"
                  }`}
                />
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
