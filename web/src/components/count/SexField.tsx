import { NORM_SEX_LABELS, type NormSex } from "@/lib/count/norms";

type Props = {
  value: NormSex | "";
  error: string | undefined;
  onChange: (value: NormSex | "") => void;
};

const INPUT =
  "min-h-11 w-full rounded-lg border border-border-strong bg-surface px-3 text-base text-text aria-[invalid=true]:border-2 aria-[invalid=true]:border-danger";

const isSex = (value: string): value is NormSex => Object.hasOwn(NORM_SEX_LABELS, value);

/** Picks the CDC reference row only; it is not written into the recording file. */
export function SexField({ value, error, onChange }: Props) {
  const describedBy = error === undefined ? "normSex-help" : "normSex-help normSex-error";
  return (
    <div className="flex flex-col gap-1 sm:max-w-sm">
      <label htmlFor="normSex" className="font-semibold text-text">
        Sex, for the CDC reference table
      </label>
      <select
        id="normSex"
        className={INPUT}
        value={value}
        aria-describedby={describedBy}
        aria-invalid={error !== undefined}
        onChange={(event) => onChange(isSex(event.target.value) ? event.target.value : "")}
      >
        <option value="">Choose...</option>
        {Object.entries(NORM_SEX_LABELS).map(([optionValue, text]) => (
          <option key={optionValue} value={optionValue}>
            {text}
          </option>
        ))}
      </select>
      <p id="normSex-help" className="text-sm text-muted">
        CDC lists separate values for women and men. Used on this screen only; not saved in the recording.
      </p>
      {error !== undefined && (
        <p id="normSex-error" className="text-sm font-semibold text-danger">
          {error}
        </p>
      )}
    </div>
  );
}
