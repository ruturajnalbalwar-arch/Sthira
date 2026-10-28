"use client";

import { useCallback, useRef, useState } from "react";
import type { CounterStatus } from "@/lib/count/counterStatus";
import { INITIAL_LIVE_COUNT, liveCountReducer, type LiveCountEvent, type LiveCountState } from "@/lib/count/liveCount";

export interface LiveCount {
  readonly state: LiveCountState;
  /** Applies an event to the latest state. Frames update the ref only; phase changes also re-render. */
  dispatch(event: LiveCountEvent): void;
  /** Publishes the latest state to React (called by the display timer). */
  refresh(): void;
}

/**
 * Wraps the pure live-count reducer. Frames arrive at camera rate, so the latest state lives in a
 * ref and React only re-renders on phase changes and on the display timer.
 */
export function useLiveCount(counter: CounterStatus): LiveCount {
  const [state, setState] = useState<LiveCountState>(INITIAL_LIVE_COUNT);
  const stateRef = useRef<LiveCountState>(INITIAL_LIVE_COUNT);

  const dispatch = useCallback(
    (event: LiveCountEvent) => {
      stateRef.current = liveCountReducer(stateRef.current, event, counter);
      if (event.type === "phase") setState(stateRef.current);
    },
    [counter],
  );

  const refresh = useCallback(() => setState(stateRef.current), []);

  return { state, dispatch, refresh };
}
