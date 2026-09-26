import Link from "next/link";
import type { ReactNode } from "react";

/**
 * The consistent header (icon + title + optional "See all" link) every
 * HomeShowcase slide shares, so rotating between 6 different sections
 * reads as one design cycling its content, not 6 different layouts.
 */
export function ShowcaseSlideShell({
  icon,
  title,
  href,
  linkLabel = "See all →",
  children,
}: {
  icon: string;
  title: string;
  href?: string;
  linkLabel?: string;
  children: ReactNode;
}) {
  return (
    <div className="flex h-full flex-col">
      <div className="flex items-center justify-between animate-showcase-header">
        <h3 className="flex items-center gap-1.5 text-base font-semibold text-text-secondary">
          <span aria-hidden="true">{icon}</span> {title}
        </h3>
        {href && (
          <Link href={href} className="text-xs font-medium text-brand-accent hover:underline">
            {linkLabel}
          </Link>
        )}
      </div>
      <div className="mt-3 grid grid-cols-2 gap-2">{children}</div>
    </div>
  );
}
