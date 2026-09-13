"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Field, Input } from "@/components/ui/Input";
import { upsertAdminFeatureFlag, type AdminFeatureFlag } from "@/lib/api";

function FlagRow({
  flag,
  token,
  onSaved,
}: {
  flag: AdminFeatureFlag;
  token: string;
  onSaved: (updated: AdminFeatureFlag) => void;
}) {
  const [enabled, setEnabled] = useState(flag.enabled);
  const [rolloutPercent, setRolloutPercent] = useState(String(flag.rolloutPercent));
  const [saving, setSaving] = useState(false);

  async function handleSave() {
    setSaving(true);
    try {
      const updated = await upsertAdminFeatureFlag(token, flag.key, {
        enabled,
        rolloutPercent: Number(rolloutPercent),
      });
      onSaved(updated);
    } finally {
      setSaving(false);
    }
  }

  return (
    <tr className="border-b border-border-subtle/50">
      <td className="py-3 pr-4 font-mono text-xs">{flag.key}</td>
      <td className="py-3 pr-4">
        <input type="checkbox" checked={enabled} onChange={(e) => setEnabled(e.target.checked)} className="h-4 w-4" />
      </td>
      <td className="py-3 pr-4">
        <Input
          type="number"
          min={0}
          max={100}
          value={rolloutPercent}
          onChange={(e) => setRolloutPercent(e.target.value)}
          className="w-20"
        />
      </td>
      <td className="py-3">
        <Button type="button" onClick={handleSave} disabled={saving} className="text-xs">
          {saving ? "Saving..." : "Save"}
        </Button>
      </td>
    </tr>
  );
}

export default function AdminFeatureFlags({
  initialFlags,
  token,
}: {
  initialFlags: AdminFeatureFlag[];
  token: string;
}) {
  const [flags, setFlags] = useState(initialFlags);
  const [newKey, setNewKey] = useState("");
  const [creating, setCreating] = useState(false);

  function handleSaved(updated: AdminFeatureFlag) {
    setFlags((prev) => {
      const exists = prev.some((f) => f.key === updated.key);
      return exists ? prev.map((f) => (f.key === updated.key ? updated : f)) : [...prev, updated];
    });
  }

  async function handleCreate() {
    if (!newKey.trim()) return;
    setCreating(true);
    try {
      const created = await upsertAdminFeatureFlag(token, newKey.trim(), {
        enabled: false,
        rolloutPercent: 0,
      });
      handleSaved(created);
      setNewKey("");
    } finally {
      setCreating(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <div className="overflow-x-auto">
        <table className="w-full text-left text-sm">
          <thead>
            <tr className="border-b border-border-subtle text-text-secondary">
              <th className="py-2 pr-4">Key</th>
              <th className="py-2 pr-4">Enabled</th>
              <th className="py-2 pr-4">Rollout %</th>
              <th className="py-2" />
            </tr>
          </thead>
          <tbody>
            {flags.map((flag) => (
              <FlagRow key={flag.key} flag={flag} token={token} onSaved={handleSaved} />
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex items-end gap-3">
        <Field label="New flag key" htmlFor="newFlagKey">
          <Input
            id="newFlagKey"
            value={newKey}
            onChange={(e) => setNewKey(e.target.value)}
            placeholder="e.g. premium-tools"
          />
        </Field>
        <Button type="button" variant="secondary" onClick={handleCreate} disabled={creating || !newKey.trim()}>
          {creating ? "Creating..." : "Create flag"}
        </Button>
      </div>
    </div>
  );
}
