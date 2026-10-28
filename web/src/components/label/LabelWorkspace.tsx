"use client";

import { useCallback, useEffect, useId, useMemo, useReducer, useRef, useState } from "react";
import { Button } from "@/components/ui/Button";
import type { Recording } from "@/lib/record/schema";
import { effectiveFps } from "@/lib/record/session";
import { deriveSpans, initLabelState, labelReducer, tallyReps, type LabelAction } from "@/lib/label/reducer";
import { TRIAL_FAMILY, type LabelEvent, type LabelKey, type Labeller, type RecordingRef } from "@/lib/label/schema";
import { EventList } from "./EventList";
import { ExportPanel } from "./ExportPanel";
import { HelpPanel } from "./HelpPanel";
import { KeyPad } from "./KeyPad";
import { RecordingSummary } from "./RecordingSummary";
import { Tallies } from "./Tallies";
import { Timeline } from "./Timeline";
import { useLabelKeyboard } from "./useLabelKeyboard";
import { usePlayback, type Playback } from "./usePlayback";
import { VideoPlayer } from "./VideoPlayer";

export interface WorkspaceProps {
  recording: Recording;
  recordingRef: RecordingRef;
  videoUrl: string;
  videoName: string;
  labeller: Labeller;
  initialEvents: readonly LabelEvent[];
  initialNotes: string;
  active: boolean;
  onClose: () => void;
}

/** Warn before the tab closes while marks or notes are not exported. */
function useUnsavedGuard(dirty: boolean): void {
  useEffect(() => {
    if (!dirty) return undefined;
    const onBeforeUnload = (event: BeforeUnloadEvent) => event.preventDefault();
    window.addEventListener("beforeunload", onBeforeUnload);
    return () => window.removeEventListener("beforeunload", onBeforeUnload);
  }, [dirty]);
}

/** Reducer state, notes, saved-state tracking and the actions that need the current video frame. */
function useLabelSession(props: WorkspaceProps, frameTs: readonly number[], playback: Playback) {
  const [state, dispatch] = useReducer(labelReducer, props.recordingRef, (ref) => initLabelState(ref, props.initialEvents));
  const [notes, setNotes] = useState(props.initialNotes);
  const [saved, setSaved] = useState({ events: state.events, notes: props.initialNotes });
  const [seq, setSeq] = useState(0);
  const { clearNotice } = playback;
  const act = useCallback(
    (action: LabelAction) => {
      clearNotice();
      dispatch(action);
      setSeq((n) => n + 1);
    },
    [clearNotice],
  );
  const press = (key: LabelKey) => {
    const { videoT, frameT } = playback.position();
    act({ type: "press", key, t: frameT, videoT });
  };
  const nudge = useCallback(
    (event: LabelEvent, direction: -1 | 1) => {
      const i = frameTs.indexOf(event.t);
      const target = i < 0 ? undefined : frameTs[i + direction];
      // No neighbouring frame: NaN makes the reducer refuse with a visible message.
      act({ type: "nudge", id: event.id, deltaS: target === undefined ? Number.NaN : target - event.t });
    },
    [act, frameTs],
  );
  const remove = useCallback((id: string) => act({ type: "delete", id }), [act]);
  const undo = () => act({ type: "undo" });
  const markSaved = useCallback((events: readonly LabelEvent[], savedNotes: string) => setSaved({ events, notes: savedNotes }), []);
  const dirty = state.events !== saved.events || notes !== saved.notes;
  return { state, seq, notes, setNotes, press, nudge, remove, undo, dirty, markSaved };
}

/** Pause when hidden (Compare mode) and move focus to the step heading when the workspace opens. */
function useWorkspaceEffects(active: boolean, playback: Playback) {
  const headingRef = useRef<HTMLHeadingElement>(null);
  const { pause } = playback;
  useEffect(() => {
    headingRef.current?.focus();
  }, []);
  useEffect(() => {
    if (!active) pause();
  }, [active, pause]);
  return headingRef;
}

export function LabelWorkspace(props: WorkspaceProps) {
  const { recording, recordingRef, labeller, active } = props;
  const [video, setVideo] = useState<HTMLVideoElement | null>(null);
  const frameTs = useMemo(() => recording.frames.map((f) => f.t), [recording]);
  const fps = useMemo(() => effectiveFps(recording.frames), [recording]);
  const playback = usePlayback(video, frameTs);
  const session = useLabelSession(props, frameTs, playback);
  const { state } = session;
  const family = TRIAL_FAMILY[recordingRef.trial];
  const spans = useMemo(() => (family === "rep" ? [] : deriveSpans(state.events, family)), [family, state.events]);
  const listId = useId();
  const headingRef = useWorkspaceEffects(active, playback);
  useUnsavedGuard(session.dirty);
  useLabelKeyboard(active, { ...playback, press: session.press, undo: session.undo });
  const close = () => {
    if (!session.dirty || window.confirm("Your marks are not exported yet. Load another recording anyway?")) props.onClose();
  };
  const notice = playback.playError ?? playback.notice?.text ?? null;

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_24rem]">
      <section aria-labelledby="label-heading" className="flex min-w-0 flex-col gap-4">
        <div className="flex flex-wrap items-center justify-between gap-2">
          <h2 id="label-heading" ref={headingRef} tabIndex={-1} className="text-lg font-semibold">2. Label</h2>
          <Button variant="secondary" onClick={close}>Load another recording</Button>
        </div>
        <RecordingSummary recording={recording} recordingRef={recordingRef} fps={fps} video={video} videoName={props.videoName} />
        <VideoPlayer
          video={video}
          onVideoElement={setVideo}
          src={props.videoUrl}
          width={recording.capture.videoWidth}
          height={recording.capture.videoHeight}
          goT={recordingRef.goT}
          onTogglePlay={playback.togglePlay}
          onStepFrame={playback.stepFrame}
          onStepSecond={playback.stepSecond}
          onChangeSpeed={playback.changeSpeed}
        />
        <KeyPad trial={recordingRef.trial} outcome={state.outcome} seq={`${session.seq}-${playback.notice?.seq ?? 0}`} notice={notice} onPress={session.press} onUndo={session.undo} />
        <Timeline video={video} phases={recording.phases} events={state.events} spans={spans} firstT={recordingRef.firstT} lastT={recordingRef.lastT} goT={recordingRef.goT} describedBy={listId} onSeek={playback.seek} />
        <HelpPanel trial={recordingRef.trial} />
      </section>
      <aside aria-label="Marks and export" className="flex min-w-0 flex-col gap-6">
        <Tallies family={family} tally={tallyReps(state.events)} spans={spans} />
        <EventList id={listId} events={state.events} goT={recordingRef.goT} onSeek={playback.seek} onNudge={session.nudge} onDelete={session.remove} />
        <ExportPanel recordingRef={recordingRef} labeller={labeller} events={state.events} spans={spans} notes={session.notes} onNotesChange={session.setNotes} onExported={session.markSaved} />
      </aside>
    </div>
  );
}
