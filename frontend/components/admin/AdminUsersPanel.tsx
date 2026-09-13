"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Field, Input } from "@/components/ui/Input";
import { ApiError, fetchAdmins, grantAdmin, revokeAdmin, type AdminUserSummary } from "@/lib/api";

export default function AdminUsersPanel({
  initialAdmins,
  token,
}: {
  initialAdmins: AdminUserSummary[];
  token: string;
}) {
  const [admins, setAdmins] = useState(initialAdmins);
  const [email, setEmail] = useState("");
  const [granting, setGranting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [revokingUserId, setRevokingUserId] = useState<string | null>(null);

  async function refresh() {
    setAdmins(await fetchAdmins(token));
  }

  async function handleGrant() {
    if (!email.trim()) return;
    setGranting(true);
    setError(null);
    try {
      await grantAdmin(token, email.trim());
      setEmail("");
      await refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.body.message : "Could not grant admin access.");
    } finally {
      setGranting(false);
    }
  }

  async function handleRevoke(userId: string) {
    setRevokingUserId(userId);
    setError(null);
    try {
      await revokeAdmin(token, userId);
      await refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.body.message : "Could not revoke admin access.");
    } finally {
      setRevokingUserId(null);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-border-subtle text-text-secondary">
              <th className="py-2 pr-4">Email</th>
              <th className="py-2 pr-4">Name</th>
              <th className="py-2 pr-4">Source</th>
              <th className="py-2" />
            </tr>
          </thead>
          <tbody>
            {admins.map((admin) => (
              <tr key={admin.email} className="border-b border-border-subtle/50">
                <td className="py-3 pr-4">{admin.email}</td>
                <td className="py-3 pr-4 text-text-secondary">{admin.displayName ?? "—"}</td>
                <td className="py-3 pr-4">
                  <span className="rounded-full border border-border-strong px-2 py-0.5 text-xs text-text-secondary">
                    {admin.source === "ENV" ? "Founder allowlist" : "Granted"}
                  </span>
                </td>
                <td className="py-3">
                  {admin.source === "GRANTED" && admin.userId && (
                    <Button
                      type="button"
                      variant="secondary"
                      onClick={() => handleRevoke(admin.userId!)}
                      disabled={revokingUserId === admin.userId}
                      className="text-xs"
                    >
                      {revokingUserId === admin.userId ? "Revoking..." : "Revoke"}
                    </Button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex items-end gap-3">
        <Field label="Grant admin access by email" htmlFor="grantEmail">
          <Input
            id="grantEmail"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="teammate@gmail.com"
          />
        </Field>
        <Button type="button" onClick={handleGrant} disabled={granting || !email.trim()}>
          {granting ? "Granting..." : "Grant admin"}
        </Button>
      </div>
      {error && <p className="text-sm text-brand-primary">{error}</p>}
      <p className="text-xs text-text-muted">
        The person must have signed in at least once before you can grant them admin access.
      </p>
    </div>
  );
}
