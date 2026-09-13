export function Card({
  className = "",
  children,
}: {
  className?: string;
  children: React.ReactNode;
}) {
  return (
    <div
      className={`rounded-lg border border-border-subtle bg-bg-surface p-5 ${className}`}
    >
      {children}
    </div>
  );
}
