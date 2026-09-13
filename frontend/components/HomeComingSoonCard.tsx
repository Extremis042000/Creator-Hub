export default function HomeComingSoonCard({ name }: { name: string }) {
  return (
    <div className="relative overflow-hidden rounded-lg border border-status-medium/40 bg-gradient-to-br from-bg-surface to-bg-surface-alt p-5 [animation:glow-pulse_3s_ease-in-out_infinite]">
      <span
        aria-hidden="true"
        className="pointer-events-none absolute inset-y-0 left-0 w-1/3 -skew-x-12 bg-gradient-to-r from-transparent via-white/10 to-transparent [animation:shimmer-sweep_2.8s_ease-in-out_infinite]"
      />

      <span className="relative inline-flex w-fit items-center gap-1 rounded-full border border-status-medium/40 bg-status-medium/15 px-2 py-0.5 text-xs font-semibold uppercase tracking-wide text-status-medium">
        Coming soon
      </span>

      <h3 className="relative mt-3 text-lg font-semibold text-text-primary">{name}</h3>

      <div className="relative mt-3 inline-flex items-center gap-1.5 rounded-full bg-brand-primary/15 px-3 py-1 text-xs font-semibold text-brand-primary [animation:float-badge_2.4s_ease-in-out_infinite]">
        <span aria-hidden="true">🎁</span>
        1 free trial per user once it launches
      </div>
    </div>
  );
}
