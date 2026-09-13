"use client";

import Link from "next/link";
import type { MouseEvent } from "react";

/**
 * Shown to everyone, admin or not (per founder request) -- non-admins
 * see a lock icon and get a "You are not an Admin" popup instead of
 * navigating; admins go straight to /admin. This is a UX affordance,
 * not the access control -- /admin itself re-checks admin status
 * against the backend regardless of what this link does.
 */
export default function AdminNavLink({ isAdmin }: { isAdmin: boolean }) {
  function handleClick(e: MouseEvent<HTMLAnchorElement>) {
    if (!isAdmin) {
      e.preventDefault();
      window.alert("You are not an Admin");
    }
  }

  return (
    <Link
      href="/admin"
      onClick={handleClick}
      className="flex items-center gap-1 text-sm text-text-secondary hover:text-text-primary"
    >
      Admin
      {!isAdmin && <span aria-hidden="true">🔒</span>}
    </Link>
  );
}
