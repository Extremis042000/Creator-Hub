"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Field, Input } from "@/components/ui/Input";
import {
  ApiError,
  createAdminProduct,
  updateAdminProduct,
  type AdminProduct,
  type AdminProductInput,
} from "@/lib/api";

const EMPTY_FORM: AdminProductInput = {
  categorySlug: "",
  name: "",
  priceCents: 0,
  currency: "USD",
  fileRef: "",
};

function toInput(product: AdminProduct): AdminProductInput {
  return {
    categorySlug: product.categorySlug,
    name: product.name,
    priceCents: product.priceCents,
    currency: product.currency,
    fileRef: product.fileRef,
  };
}

function ProductFields({
  form,
  onChange,
  idPrefix,
}: {
  form: AdminProductInput;
  onChange: (form: AdminProductInput) => void;
  idPrefix: string;
}) {
  function set<K extends keyof AdminProductInput>(key: K, value: AdminProductInput[K]) {
    onChange({ ...form, [key]: value });
  }

  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <Field label="Name" htmlFor={`${idPrefix}-name`}>
        <Input id={`${idPrefix}-name`} value={form.name} onChange={(e) => set("name", e.target.value)} />
      </Field>
      <Field label="Category" htmlFor={`${idPrefix}-category`}>
        <Input
          id={`${idPrefix}-category`}
          value={form.categorySlug}
          onChange={(e) => set("categorySlug", e.target.value)}
          placeholder="e.g. overlay-packs"
        />
      </Field>
      <Field label="Price (cents)" htmlFor={`${idPrefix}-price`}>
        <Input
          id={`${idPrefix}-price`}
          type="number"
          min={0}
          value={form.priceCents}
          onChange={(e) => set("priceCents", Number(e.target.value))}
        />
      </Field>
      <Field label="Currency" htmlFor={`${idPrefix}-currency`}>
        <Input
          id={`${idPrefix}-currency`}
          value={form.currency}
          onChange={(e) => set("currency", e.target.value)}
        />
      </Field>
      <Field label="File reference" htmlFor={`${idPrefix}-fileRef`}>
        <Input
          id={`${idPrefix}-fileRef`}
          value={form.fileRef}
          onChange={(e) => set("fileRef", e.target.value)}
          placeholder="filename inside backend/secure-files/"
        />
      </Field>
    </div>
  );
}

function ProductRow({
  product,
  token,
  onSaved,
}: {
  product: AdminProduct;
  token: string;
  onSaved: (updated: AdminProduct) => void;
}) {
  const [form, setForm] = useState(toInput(product));
  const [active, setActive] = useState(product.active);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      const updated = await updateAdminProduct(token, product.id, { ...form, active });
      onSaved(updated);
    } catch (err) {
      setError(err instanceof ApiError ? err.body.message : "Save failed (network or CORS error).");
    } finally {
      setSaving(false);
    }
  }

  return (
    <div className="rounded-md border border-border-subtle p-4">
      <ProductFields form={form} onChange={setForm} idPrefix={product.id} />
      <div className="mt-3 flex items-center gap-3">
        <label className="flex items-center gap-1.5 text-sm text-text-secondary">
          <input type="checkbox" checked={active} onChange={(e) => setActive(e.target.checked)} className="h-4 w-4" />
          Active
        </label>
        <Button type="button" onClick={handleSave} disabled={saving} className="text-xs">
          {saving ? "Saving..." : "Save"}
        </Button>
        {error && <p className="text-xs text-brand-primary">{error}</p>}
      </div>
    </div>
  );
}

export default function AdminProductsTable({
  initialProducts,
  token,
}: {
  initialProducts: AdminProduct[];
  token: string;
}) {
  const [products, setProducts] = useState(initialProducts);
  const [newForm, setNewForm] = useState<AdminProductInput>(EMPTY_FORM);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  function handleSaved(updated: AdminProduct) {
    setProducts((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
  }

  async function handleCreate() {
    if (!newForm.name.trim() || !newForm.categorySlug.trim() || !newForm.fileRef.trim()) return;
    setCreating(true);
    setCreateError(null);
    try {
      const created = await createAdminProduct(token, newForm);
      setProducts((prev) => [...prev, created]);
      setNewForm(EMPTY_FORM);
    } catch (err) {
      setCreateError(err instanceof ApiError ? err.body.message : "Create failed (network or CORS error).");
    } finally {
      setCreating(false);
    }
  }

  return (
    <div className="flex flex-col gap-4">
      <p className="text-xs text-text-muted">
        File reference must match a filename already placed in <code>backend/secure-files/</code> on the
        server (no upload UI yet).
      </p>

      {products.map((product) => (
        <ProductRow key={product.id} product={product} token={token} onSaved={handleSaved} />
      ))}

      <div className="rounded-md border border-dashed border-border-strong p-4">
        <h3 className="mb-3 text-sm font-semibold text-text-secondary">Add product</h3>
        <ProductFields form={newForm} onChange={setNewForm} idPrefix="new" />
        <div className="mt-3 flex items-center gap-3">
          <Button
            type="button"
            variant="secondary"
            onClick={handleCreate}
            disabled={creating || !newForm.name.trim() || !newForm.categorySlug.trim() || !newForm.fileRef.trim()}
          >
            {creating ? "Creating..." : "Create product"}
          </Button>
          {createError && <p className="text-xs text-brand-primary">{createError}</p>}
        </div>
      </div>
    </div>
  );
}
