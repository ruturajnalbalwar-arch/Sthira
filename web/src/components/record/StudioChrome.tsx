import { Button } from "@/components/ui/Button";
import { CLOTHING_LABELS, TRIAL_LABELS, VIEW_ANGLE_LABELS } from "@/lib/record/labels";
import type { SessionMeta } from "@/lib/record/schema";

export function PrivacyBanner() {
  return (
    <p className="flex items-start gap-3 rounded-xl border border-success bg-success-bg p-3 font-semibold text-success">
      <svg aria-hidden="true" viewBox="0 0 24 24" className="mt-0.5 size-5 shrink-0" fill="none" stroke="currentColor" strokeWidth="2">
        <rect x="4" y="11" width="16" height="10" rx="2" />
        <path d="M8 11V7a4 4 0 0 1 8 0v4" />
      </svg>
      Nothing leaves this device. Pose tracking runs in this browser; files are saved only where you choose.
    </p>
  );
}

type SessionLineProps = { meta: SessionMeta; canEdit: boolean; onEdit: () => void };

export function SessionLine({ meta, canEdit, onEdit }: SessionLineProps) {
  const parts = [meta.participantCode, TRIAL_LABELS[meta.trial], CLOTHING_LABELS[meta.clothing], VIEW_ANGLE_LABELS[meta.viewAngle]];
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border bg-surface p-3">
      <p className="font-semibold">
        <span className="sr-only">Session: </span>
        {parts.join(" · ")}
      </p>
      <Button variant="secondary" onClick={onEdit} disabled={!canEdit}>
        Edit session
      </Button>
    </div>
  );
}
