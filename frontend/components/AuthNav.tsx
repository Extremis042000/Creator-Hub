"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import AdminNavLink from "./AdminNavLink";
import { fetchCurrentUser, type CurrentUser } from "@/lib/api";
import { clearToken, getToken } from "@/lib/auth";

export default function AuthNav() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [checked, setChecked] = useState(false);
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

  function handleSignOut() {
    clearToken();
    setUser(null);
    router.push("/");
    router.refresh();
  }

  // Avoid a flash of "Sign in" before the token check resolves.
  if (!checked) return <div className="h-9 w-20" />;

  if (user) {
    return (
      <div className="flex items-center gap-3">
        <AdminNavLink isAdmin={user.isAdmin} />
        <Link href="/dashboard" className="text-sm text-text-secondary hover:text-text-primary">
          {user.displayName ?? user.email}
        </Link>
        <button
          type="button"
          onClick={handleSignOut}
          className="rounded-md border border-border-strong px-3 py-1.5 text-sm text-text-secondary hover:text-text-primary"
        >
          Sign out
        </button>
      </div>
    );
  }

  return (
    <div className="flex items-center gap-3">
      <AdminNavLink isAdmin={false} />
      <Link
        href="/login"
        className="rounded-md border border-border-strong px-3 py-1.5 text-sm text-text-secondary hover:text-text-primary"
      >
        Sign in
      </Link>
    </div>
  );
}
