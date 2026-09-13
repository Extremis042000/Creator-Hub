import type { InputHTMLAttributes, SelectHTMLAttributes } from "react";

const FIELD_CLASSES =
  "w-full rounded-md border border-border-subtle bg-bg-surface-alt px-3 py-2 text-sm text-text-primary placeholder:text-text-muted focus:border-brand-accent focus:outline-none";
const ERROR_CLASSES = "border-brand-primary";

type FieldWrapperProps = {
  label: string;
  htmlFor: string;
  error?: string;
  children: React.ReactNode;
};

export function Field({ label, htmlFor, error, children }: FieldWrapperProps) {
  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={htmlFor} className="text-sm font-medium text-text-secondary">
        {label}
      </label>
      {children}
      {error && (
        <p id={`${htmlFor}-error`} className="text-xs text-brand-primary">
          {error}
        </p>
      )}
    </div>
  );
}

type InputProps = InputHTMLAttributes<HTMLInputElement> & { error?: string };

export function Input({ error, className = "", id, ...props }: InputProps) {
  return (
    <input
      id={id}
      className={`${FIELD_CLASSES} ${error ? ERROR_CLASSES : ""} ${className}`}
      aria-invalid={Boolean(error)}
      aria-describedby={error ? `${id}-error` : undefined}
      {...props}
    />
  );
}

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & { error?: string };

export function Select({ error, className = "", id, children, ...props }: SelectProps) {
  return (
    <select
      id={id}
      className={`${FIELD_CLASSES} ${error ? ERROR_CLASSES : ""} ${className}`}
      aria-invalid={Boolean(error)}
      aria-describedby={error ? `${id}-error` : undefined}
      {...props}
    >
      {children}
    </select>
  );
}
