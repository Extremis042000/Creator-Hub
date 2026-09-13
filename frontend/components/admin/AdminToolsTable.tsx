"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Input } from "@/components/ui/Input";
import { ApiError, updateAdminTool, type AdminTool } from "@/lib/api";

function ToolRow({
  tool,
  token,
  onSaved,
}: {
  tool: AdminTool;
  token: string;
  onSaved: (updated: AdminTool) => void;
}) {
  const [name, setName] = useState(tool.name);
  const [category, setCategory] = useState(tool.category ?? "");
  const [premiumOnly, setPremiumOnly] = useState(tool.premiumOnly);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const dirty = name !== tool.name || category !== (tool.category ?? "") || premiumOnly !== tool.premiumOnly;

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      const updated = await updateAdminTool(token, tool.id, {
        name,
        category: category || null,
        premiumOnly,
      });
      onSaved(updated);
    } catch (err) {
      // Surface the real cause instead of a generic "Save failed" --
      // a swallowed error is exactly what made bug #1 (CORS blocking
      // PATCH) harder to diagnose than it needed to be.
      setError(err instanceof ApiError ? err.body.message : "Save failed (network or CORS error).");
    } finally {
      setSaving(false);
    }
  }

  return (
    <tr className="border-b border-border-subtle/50 align-top">
      <td className="py-3 pr-4 font-mono text-xs text-text-muted">{tool.slug}</td>
      <td className="py-3 pr-4">
        <Input value={name} onChange={(e) => setName(e.target.value)} />
      </td>
      <td className="py-3 pr-4">
        <Input value={category} onChange={(e) => setCategory(e.target.value)} />
      </td>
      <td className="py-3 pr-4">
        <input
          type="checkbox"
          checked={premiumOnly}
          onChange={(e) => setPremiumOnly(e.target.checked)}
          className="h-4 w-4"
        />
      </td>
      <td className="py-3">
        <Button type="button" onClick={handleSave} disabled={!dirty || saving} className="text-xs">
          {saving ? "Saving..." : "Save"}
        </Button>
        {error && <p className="mt-1 text-xs text-brand-primary">{error}</p>}
      </td>
    </tr>
  );
}

export default function AdminToolsTable({
  initialTools,
  token,
}: {
  initialTools: AdminTool[];
  token: string;
}) {
  const [tools, setTools] = useState(initialTools);

  function handleSaved(updated: AdminTool) {
    setTools((prev) => prev.map((t) => (t.id === updated.id ? updated : t)));
  }

  return (
    <div className="overflow-x-auto">
      <table className="w-full text-left text-sm">
        <thead>
          <tr className="border-b border-border-subtle text-text-secondary">
            <th className="py-2 pr-4">Slug</th>
            <th className="py-2 pr-4">Name</th>
            <th className="py-2 pr-4">Category</th>
            <th className="py-2 pr-4">Premium only</th>
            <th className="py-2" />
          </tr>
        </thead>
        <tbody>
          {tools.map((tool) => (
            <ToolRow key={tool.id} tool={tool} token={token} onSaved={handleSaved} />
          ))}
        </tbody>
      </table>
    </div>
  );
}
