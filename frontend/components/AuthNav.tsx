"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState } from "react";
import type { MouseEvent } from "react";
import { fetchCurrentUser, type CurrentUser } from "@/lib/api";
import { clearToken, getToken } from "@/lib/auth";

export default function AuthNav() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [checked, setChecked] = useState(false);
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);
  const router = useRouter();

  useEffect(() => {
    const token = getToken();
    if (!token) {
      setChecked(true);
      return;
    }
    fetchCurrentUser(token).then((currentUser) => {
      if (!currentUser) clearToken(); // stale/expired token
      setUser(currentUser);
      setChecked(true);
    });
  }, []);

  useEffect(() => {
    if (!menuOpen) return;
    function handleClickOutside(e: globalThis.MouseEvent) {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, [menuOpen]);

  function handleSignOut() {
    clearToken();
    setUser(null);
    setMenuOpen(false);
    router.push("/");
    router.refresh();
  }

  function handleAdminClick(e: MouseEvent<HTMLAnchorElement>) {
    if (!user?.isAdmin) {
      e.preventDefault();
      window.alert("You are not an Admin");
    } else {
      setMenuOpen(false);
    }
  }

  // Avoid a flash of "Sign in" before the token check resolves.
  if (!checked) return <div className="h-9 w-9" />;

  if (user) {
    const initial = (user.displayName ?? user.email).charAt(0).toUpperCase();

    return (
      <div ref={menuRef} className="relative">
        <button
          type="button"
          onClick={() => setMenuOpen((open) => !open)}
          aria-label="Account menu"
          aria-expanded={menuOpen}
          className={`flex h-9 w-9 items-center justify-center overflow-hidden rounded-full border-2 text-sm font-semibold text-text-primary transition-all duration-200 ${
            menuOpen ? "border-brand-accent" : "border-border-strong hover:border-brand-accent"
          }`}
        >
          {user.avatarUrl ? (
            // eslint-disable-next-line @next/next/no-img-element
            <img src={user.avatarUrl} alt="" className="h-full w-full object-cover" />
          ) : (
            <span className="bg-bg-surface-alt flex h-full w-full items-center justify-center">{initial}</span>
          )}
        </button>

        <div
          className={`absolute right-0 top-full z-20 mt-2 w-56 origin-top-right rounded-lg border border-border-subtle bg-bg-surface shadow-xl transition-all duration-150 ease-out ${
            menuOpen ? "scale-100 opacity-100" : "pointer-events-none scale-95 opacity-0"
          }`}
        >
          <div className="border-b border-border-subtle px-4 py-3">
            <p className="truncate text-sm font-medium text-text-primary">
              {user.displayName ?? user.email}
            </p>
            <p className="truncate text-xs text-text-muted">{user.email}</p>
          </div>
          <div className="flex flex-col p-1.5">
            <Link
              href="/dashboard"
              onClick={() => setMenuOpen(false)}
              className="rounded-md px-3 py-2 text-left text-sm text-text-secondary hover:bg-bg-surface-alt hover:text-text-primary"
            >
              Dashboard
            </Link>
            <Link
              href="/admin"
              onClick={handleAdminClick}
              className="flex items-center gap-1.5 rounded-md px-3 py-2 text-left text-sm text-text-secondary hover:bg-bg-surface-alt hover:text-text-primary"
            >
              Admin
              {!user.isAdmin && <span aria-hidden="true">🔒</span>}
            </Link>
            <button
              type="button"
              onClick={handleSignOut}
              className="rounded-md px-3 py-2 text-left text-sm text-text-secondary hover:bg-bg-surface-alt hover:text-text-primary"
            >
              Sign out
            </button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <Link
      href="/login"
      className="rounded-md border border-border-strong px-3 py-1.5 text-sm text-text-secondary hover:text-text-primary"
    >
      Sign in
    </Link>
  );
}
