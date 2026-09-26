/**
 * One item inside a HomeShowcase slide (components/HomeShowcase.tsx) --
 * a single small presentational shape shared by all 6 section types
 * (tools, premium tools, coming soon, deals, gear, store) so the
 * carousel reads as one consistent design language while it rotates,
 * rather than 6 mismatched card styles bleeding in from each section's
 * own full-page component. Not a link on its own -- callers wrap it
 * (see HomeShowcase's slide builders) so the anchor can be external,
 * internal, or (Coming Soon) omitted entirely.
 */
export function ShowcaseTile({
  title,
  badge,
  meta,
  cta,
}: {
  title: string;
  /** Small chip text, e.g. a category, "Premium", "Coming soon", or a "% OFF" pill. */
  badge?: string;
  /** Secondary line -- price, brand, or a short note. */
  meta?: string;
  /** Trailing action text, e.g. "Try it ->", "Get Deal ->". Omit for a non-interactive tile. */
  cta?: string;
}) {
  return (
    <div className="flex h-full flex-col gap-1 rounded-md border border-border-subtle bg-bg-surface-alt p-2.5 transition-colors group-hover:border-border-strong">
      {badge && (
        <span className="w-fit rounded-full border border-border-strong px-1.5 py-0.5 text-[10px] uppercase tracking-wide text-text-secondary">
          {badge}
        </span>
      )}
      <h4 className="line-clamp-1 text-sm font-semibold text-text-primary">{title}</h4>
      {meta && <p className="line-clamp-1 text-xs text-text-secondary">{meta}</p>}
      {cta && <span className="mt-auto text-xs font-medium text-brand-accent">{cta}</span>}
    </div>
  );
}
