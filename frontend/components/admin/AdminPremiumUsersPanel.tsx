"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Field, Input } from "@/components/ui/Input";
import { ApiError, fetchPremiumUsers, grantPremium, revokePremium, type AdminPremiumUser } from "@/lib/api";

export default function AdminPremiumUsersPanel({
  initialPremiumUsers,
  token,
}: {
  initialPremiumUsers: AdminPremiumUser[];
  token: string;
}) {
  const [premiumUsers, setPremiumUsers] = useState(initialPremiumUsers);
  const [email, setEmail] = useState("");
  const [granting, setGranting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [revokingUserId, setRevokingUserId] = useState<string | null>(null);

  async function refresh() {
    setPremiumUsers(await fetchPremiumUsers(token));
  }

  async function handleGrant() {
    if (!email.trim()) return;
    setGranting(true);
    setError(null);
    try {
      await grantPremium(token, email.trim());
      setEmail("");
      await refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.body.message : "Could not grant premium access.");
    } finally {
      setGranting(false);
    }
  }

  async function handleRevoke(userId: string) {
    setRevokingUserId(userId);
    setError(null);
    try {
      await revokePremium(token, userId);
      await refresh();
    } catch (err) {
      setError(err instanceof ApiError ? err.body.message : "Could not revoke premium access.");
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
              <th className="py-2 pr-4">Granted</th>
              <th className="py-2" />
            </tr>
          </thead>
          <tbody>
            {premiumUsers.map((user) => (
              <tr key={user.userId} className="border-b border-border-subtle/50">
                <td className="py-3 pr-4">{user.email}</td>
                <td className="py-3 pr-4 text-text-secondary">{user.displayName ?? "—"}</td>
                <td className="py-3 pr-4 text-text-secondary">
                  {new Date(user.grantedAt).toLocaleDateString()}
                </td>
                <td className="py-3">
                  <Button
                    type="button"
                    variant="secondary"
                    onClick={() => handleRevoke(user.userId)}
                    disabled={revokingUserId === user.userId}
                    className="text-xs"
                  >
                    {revokingUserId === user.userId ? "Revoking..." : "Revoke"}
                  </Button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex items-end gap-3">
        <Field label="Grant premium access by email" htmlFor="grantPremiumEmail">
          <Input
            id="grantPremiumEmail"
            type="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            placeholder="user@gmail.com"
          />
        </Field>
        <Button type="button" onClick={handleGrant} disabled={granting || !email.trim()}>
          {granting ? "Granting..." : "Grant premium"}
        </Button>
      </div>
      {error && <p className="text-sm text-brand-primary">{error}</p>}
      <p className="text-xs text-text-muted">
        The person must have signed in at least once before you can grant them premium access.
      </p>
    </div>
  );
}
