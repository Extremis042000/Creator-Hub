"use client";

import { useState } from "react";
import { Button } from "@/components/ui/Button";
import { Field, Input } from "@/components/ui/Input";
import {
  ApiError,
  createAdminAffiliateProduct,
  updateAdminAffiliateProduct,
  type AdminAffiliateProduct,
  type AdminAffiliateProductInput,
} from "@/lib/api";

const EMPTY_FORM: AdminAffiliateProductInput = {
  name: "",
  brand: "",
  category: "",
  priceInfo: "",
  affiliateUrl: "",
  merchant: "",
  region: "",
  disclosureText: "",
  imageUrl: "",
};

function toInput(product: AdminAffiliateProduct): AdminAffiliateProductInput {
  return {
    name: product.name,
    brand: product.brand ?? "",
    category: product.category ?? "",
    priceInfo: product.priceInfo ?? "",
    affiliateUrl: product.affiliateUrl,
    merchant: product.merchant ?? "",
    region: product.region ?? "",
    disclosureText: product.disclosureText ?? "",
    imageUrl: product.imageUrl ?? "",
  };
}

function ProductFields({
  form,
  onChange,
  idPrefix,
}: {
  form: AdminAffiliateProductInput;
  onChange: (form: AdminAffiliateProductInput) => void;
  idPrefix: string;
}) {
  function set<K extends keyof AdminAffiliateProductInput>(key: K, value: string) {
    onChange({ ...form, [key]: value });
  }

  return (
    <div className="grid gap-3 sm:grid-cols-2">
      <Field label="Name" htmlFor={`${idPrefix}-name`}>
        <Input id={`${idPrefix}-name`} value={form.name} onChange={(e) => set("name", e.target.value)} />
      </Field>
      <Field label="Brand" htmlFor={`${idPrefix}-brand`}>
        <Input id={`${idPrefix}-brand`} value={form.brand ?? ""} onChange={(e) => set("brand", e.target.value)} />
      </Field>
      <Field label="Category" htmlFor={`${idPrefix}-category`}>
        <Input
          id={`${idPrefix}-category`}
          value={form.category ?? ""}
          onChange={(e) => set("category", e.target.value)}
          placeholder="e.g. mouse, keyboard, headset"
        />
      </Field>
      <Field label="Price info" htmlFor={`${idPrefix}-priceInfo`}>
        <Input
          id={`${idPrefix}-priceInfo`}
          value={form.priceInfo ?? ""}
          onChange={(e) => set("priceInfo", e.target.value)}
          placeholder="e.g. ~$49"
        />
      </Field>
      <Field label="Affiliate URL" htmlFor={`${idPrefix}-affiliateUrl`}>
        <Input
          id={`${idPrefix}-affiliateUrl`}
          value={form.affiliateUrl}
          onChange={(e) => set("affiliateUrl", e.target.value)}
          placeholder="https://..."
        />
      </Field>
      <Field label="Merchant" htmlFor={`${idPrefix}-merchant`}>
        <Input
          id={`${idPrefix}-merchant`}
          value={form.merchant ?? ""}
          onChange={(e) => set("merchant", e.target.value)}
          placeholder="e.g. Amazon"
        />
      </Field>
      <Field label="Region" htmlFor={`${idPrefix}-region`}>
        <Input
          id={`${idPrefix}-region`}
          value={form.region ?? ""}
          onChange={(e) => set("region", e.target.value)}
          placeholder="e.g. IN, US, global"
        />
      </Field>
      <Field label="Disclosure text (optional override)" htmlFor={`${idPrefix}-disclosureText`}>
        <Input
          id={`${idPrefix}-disclosureText`}
          value={form.disclosureText ?? ""}
          onChange={(e) => set("disclosureText", e.target.value)}
        />
      </Field>
      <Field label="Image URL (optional)" htmlFor={`${idPrefix}-imageUrl`}>
        <Input
          id={`${idPrefix}-imageUrl`}
          value={form.imageUrl ?? ""}
          onChange={(e) => set("imageUrl", e.target.value)}
          placeholder="https://... (a real product photo, e.g. from the merchant's own listing)"
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
  product: AdminAffiliateProduct;
  token: string;
  onSaved: (updated: AdminAffiliateProduct) => void;
}) {
  const [form, setForm] = useState(toInput(product));
  const [active, setActive] = useState(product.active);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSave() {
    setSaving(true);
    setError(null);
    try {
      const updated = await updateAdminAffiliateProduct(token, product.id, { ...form, active });
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

export default function AdminAffiliateProductsTable({
  initialProducts,
  token,
}: {
  initialProducts: AdminAffiliateProduct[];
  token: string;
}) {
  const [products, setProducts] = useState(initialProducts);
  const [newForm, setNewForm] = useState<AdminAffiliateProductInput>(EMPTY_FORM);
  const [creating, setCreating] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  function handleSaved(updated: AdminAffiliateProduct) {
    setProducts((prev) => prev.map((p) => (p.id === updated.id ? updated : p)));
  }

  async function handleCreate() {
    if (!newForm.name.trim() || !newForm.affiliateUrl.trim()) return;
    setCreating(true);
    setCreateError(null);
    try {
      const created = await createAdminAffiliateProduct(token, newForm);
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
      {products.map((product) => (
        <ProductRow key={product.id} product={product} token={token} onSaved={handleSaved} />
      ))}

      <div className="rounded-md border border-dashed border-border-strong p-4">
        <h3 className="mb-3 text-sm font-semibold text-text-secondary">Add affiliate product</h3>
        <ProductFields form={newForm} onChange={setNewForm} idPrefix="new" />
        <div className="mt-3 flex items-center gap-3">
          <Button
            type="button"
            variant="secondary"
            onClick={handleCreate}
            disabled={creating || !newForm.name.trim() || !newForm.affiliateUrl.trim()}
          >
            {creating ? "Creating..." : "Create product"}
          </Button>
          {createError && <p className="text-xs text-brand-primary">{createError}</p>}
        </div>
      </div>
    </div>
  );
}
