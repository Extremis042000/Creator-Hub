"use client";

import Link from "next/link";
import { useEffect, useState, type ReactNode } from "react";
import { fetchCurrentUser } from "@/lib/api";
import { getToken } from "@/lib/auth";

/**
 * Wraps a premium tool's form. The API still enforces access server-side
 * on submit -- this just avoids showing a fully interactive form to a
 * non-entitled visitor who navigated straight to the tool's URL.
 */
export default function PremiumToolGate({
  premiumOnly,
  children,
}: {
  premiumOnly: boolean;
  children: ReactNode;
}) {
  const [checked, setChecked] = useState(!premiumOnly);
  const [entitled, setEntitled] = useState(!premiumOnly);

  useEffect(() => {
    if (!premiumOnly) return;
    const token = getToken();
    if (!token) {
      setChecked(true);
      return;
    }
    fetchCurrentUser(token).then((user) => {
      setEntitled(Boolean(user?.hasPremiumAccess));
      setChecked(true);
    });
  }, [premiumOnly]);

  if (!premiumOnly || entitled) {
    return <>{children}</>;
  }

  if (!checked) {
    return <div className="h-40" />;
  }

  return (
    <div className="rounded-lg border border-status-medium/40 bg-status-medium/10 p-8 text-center">
      <div className="text-3xl">🔒</div>
      <p className="mt-2 text-lg font-semibold text-text-primary">Premium Tool</p>
      <p className="mt-1 text-sm text-text-secondary">
        This tool requires premium access. Kindly contact Admin support, or{" "}
        <Link href="/login" className="text-brand-accent hover:underline">
          sign in
        </Link>{" "}
        if you already have access.
      </p>
    </div>
  );
}
