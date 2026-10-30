import type { ButtonHTMLAttributes } from "react";

type Variant = "primary" | "secondary" | "danger";

type Props = ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant };

const VARIANTS: Record<Variant, string> = {
  primary: "bg-primary text-on-primary hover:bg-primary-hover",
  secondary: "border border-border-strong bg-surface text-text hover:bg-bg",
  danger: "bg-danger text-on-danger hover:opacity-90",
};

/** 48px minimum target at the default 18px root (larger with the text-size switch); visible focus ring comes from the global :focus-visible style. */
export function Button({ variant = "primary", className = "", type = "button", ...rest }: Props) {
  return (
    <button
      type={type}
      className={`inline-flex min-h-12 min-w-12 cursor-pointer items-center justify-center gap-2 rounded-lg px-5 text-lg font-semibold transition-colors duration-200 disabled:cursor-not-allowed disabled:opacity-50 ${VARIANTS[variant]} ${className}`}
      {...rest}
    />
  );
}
