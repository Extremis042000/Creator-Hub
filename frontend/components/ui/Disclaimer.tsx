/**
 * Renders the API's `disclaimer` field verbatim — never paraphrased
 * or dropped. See docs/06-prd.md §5.3.
 */
export function Disclaimer({ text }: { text: string }) {
  return (
    <p className="rounded-md border border-border-subtle bg-bg-surface-alt px-3 py-2 text-xs text-text-secondary">
      {text}
    </p>
  );
}
