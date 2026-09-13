/**
 * Marks a SHELL screen/control as not-yet-available — see the
 * UI-shell rule in docs/09-phase3-ui-ux.md. Pair with
 * aria-disabled="true" on the parent control, not just visual
 * dimming, so assistive tech announces it correctly.
 */
export function ComingSoonBadge() {
  return (
    <span className="inline-flex items-center rounded-full border border-border-strong px-2 py-0.5 text-xs font-medium text-text-muted">
      Coming soon
    </span>
  );
}
