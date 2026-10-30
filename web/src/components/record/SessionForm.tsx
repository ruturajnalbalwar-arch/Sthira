"use client";

import { useState, type FormEvent, type InputHTMLAttributes, type ReactNode } from "react";
import { Button } from "@/components/ui/Button";
import { AGE_BAND_LABELS, CLOTHING_LABELS, TRIAL_LABELS, VIEW_ANGLE_LABELS } from "@/lib/record/labels";
import type { SessionMeta } from "@/lib/record/schema";
import { parseSessionForm, type SessionField, type SessionFieldErrors, type SessionFormValues } from "@/lib/record/sessionForm";

type Props = {
  initialValues: SessionFormValues;
  onSubmit: (meta: SessionMeta) => void;
  /** Limits the trial list (e.g. the count screen runs only the 30 s chair stand). Defaults to every trial. */
  trialOptions?: Readonly<Record<string, string>>;
  /** Extra fields shown before the consent box; they are not part of the recording metadata. */
  extra?: ReactNode;
};

const INPUT =
  "min-h-11 w-full rounded-lg border border-border-strong bg-surface px-3 text-base text-text aria-[invalid=true]:border-2 aria-[invalid=true]:border-danger";

const FIELD_ORDER: readonly SessionField[] = [
  "participantCode", "trial", "clothing", "ageBand", "viewAngle", "room", "cameraHeightCm", "cameraDistanceCm", "notes", "consentConfirmed",
];

/** Shared accessibility wiring: helper and error text are linked to the control with aria-describedby. */
function a11y(id: SessionField, helper: string | undefined, error: string | undefined) {
  const ids = [helper === undefined ? null : `${id}-help`, error === undefined ? null : `${id}-error`].filter((x) => x !== null);
  return { id, name: id, "aria-describedby": ids.length > 0 ? ids.join(" ") : undefined, "aria-invalid": error !== undefined };
}

type FieldProps = { id: SessionField; label: string; helper?: string; error?: string; children: ReactNode };

function Field({ id, label, helper, error, children }: FieldProps) {
  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="font-semibold text-text">
        {label}
      </label>
      {children}
      {helper !== undefined && (
        <p id={`${id}-help`} className="text-sm text-muted">
          {helper}
        </p>
      )}
      {error !== undefined && (
        <p id={`${id}-error`} className="text-sm font-semibold text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

type ControlProps = Omit<FieldProps, "children"> & { value: string; onChange: (value: string) => void };

function TextField({ id, label, helper, error, value, onChange, ...input }: ControlProps & Omit<InputHTMLAttributes<HTMLInputElement>, "id" | "value" | "onChange">) {
  return (
    <Field id={id} label={label} helper={helper} error={error}>
      <input className={INPUT} value={value} onChange={(event) => onChange(event.target.value)} {...input} {...a11y(id, helper, error)} />
    </Field>
  );
}

function SelectField({ id, label, helper, error, value, onChange, options }: ControlProps & { options: Readonly<Record<string, string>> }) {
  return (
    <Field id={id} label={label} helper={helper} error={error}>
      <select className={INPUT} value={value} onChange={(event) => onChange(event.target.value)} {...a11y(id, helper, error)}>
        <option value="">Choose...</option>
        {Object.entries(options).map(([optionValue, text]) => (
          <option key={optionValue} value={optionValue}>
            {text}
          </option>
        ))}
      </select>
    </Field>
  );
}

function ConsentField({ checked, error, onChange }: { checked: boolean; error?: string; onChange: (checked: boolean) => void }) {
  return (
    <div className="flex flex-col gap-1">
      {/* The whole label row is the click target, so the target is at least 44px tall. */}
      <label htmlFor="consentConfirmed" className="flex min-h-11 cursor-pointer items-start gap-3 py-2 font-semibold">
        <input
          type="checkbox"
          className="mt-0.5 size-6 shrink-0 accent-primary"
          checked={checked}
          onChange={(event) => onChange(event.target.checked)}
          {...a11y("consentConfirmed", undefined, error)}
        />
        The participant has signed the written consent form for this session.
      </label>
      {error !== undefined && (
        <p id="consentConfirmed-error" className="text-sm font-semibold text-danger">
          {error}
        </p>
      )}
    </div>
  );
}

const MEASURE = { type: "number", inputMode: "decimal", min: 1, step: "any" } as const;

export function SessionForm({ initialValues, onSubmit, trialOptions = TRIAL_LABELS, extra }: Props) {
  const [values, setValues] = useState<SessionFormValues>(initialValues);
  const [errors, setErrors] = useState<SessionFieldErrors>({});

  const set = (field: SessionField) => (value: string | boolean) => setValues((prev) => ({ ...prev, [field]: value }));
  const bind = (field: Exclude<SessionField, "consentConfirmed">) => ({ id: field, value: values[field], error: errors[field], onChange: set(field) });

  const handleSubmit = (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    const result = parseSessionForm(values);
    if (result.ok) {
      setErrors({});
      onSubmit(result.meta);
      return;
    }
    setErrors(result.errors);
    const first = FIELD_ORDER.find((field) => result.errors[field] !== undefined);
    if (first !== undefined) document.getElementById(first)?.focus();
  };

  return (
    <form noValidate onSubmit={handleSubmit} aria-labelledby="session-heading" className="flex flex-col gap-5 rounded-xl border border-border bg-surface p-4 sm:p-6">
      <h2 id="session-heading" className="text-xl font-bold">
        1. Session details
      </h2>
      <div className="grid gap-5 sm:grid-cols-2">
        <TextField {...bind("participantCode")} label="Participant code" helper="Any code you choose, like A11 (letters, digits, - and _). Never a name." autoComplete="off" autoCapitalize="characters" spellCheck={false} />
        <SelectField {...bind("trial")} label="Trial" options={trialOptions} />
        <SelectField {...bind("clothing")} label="Clothing" options={CLOTHING_LABELS} />
        <SelectField {...bind("ageBand")} label="Age band" options={AGE_BAND_LABELS} />
        <SelectField {...bind("viewAngle")} label="Camera angle" options={VIEW_ANGLE_LABELS} />
        <TextField {...bind("room")} label="Room" helper="e.g. hostel common room" autoComplete="off" maxLength={80} />
        <TextField {...bind("cameraHeightCm")} label="Camera height (cm)" helper="Floor to lens. Measure with a tape." {...MEASURE} />
        <TextField {...bind("cameraDistanceCm")} label="Camera distance (cm)" helper="Lens to chair front. Measure with a tape." {...MEASURE} />
      </div>
      <TextField {...bind("notes")} label="Notes (optional)" helper="Redo, problems, 'no armchair'. No names." autoComplete="off" maxLength={500} />
      {extra}
      <ConsentField checked={values.consentConfirmed} error={errors.consentConfirmed} onChange={set("consentConfirmed")} />
      <Button type="submit" className="self-start">
        Continue to camera
      </Button>
    </form>
  );
}
