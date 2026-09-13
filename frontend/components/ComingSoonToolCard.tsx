import { Card } from "./ui/Card";

export default function ComingSoonToolCard({ name }: { name: string }) {
  return (
    <Card className="flex h-full flex-col gap-3 opacity-70">
      <span className="w-fit rounded-full border border-border-strong px-2 py-0.5 text-xs uppercase tracking-wide text-text-secondary">
        Coming soon
      </span>
      <h3 className="text-lg font-semibold text-text-primary">{name}</h3>
      <p className="text-sm text-text-secondary">This tool is in the works — check back soon.</p>
    </Card>
  );
}
