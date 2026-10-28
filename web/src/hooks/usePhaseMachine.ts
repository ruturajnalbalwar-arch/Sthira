"use client";

import { useCallback, useRef, useState } from "react";
import { INITIAL_PHASE_STATE, phaseReducer, type MachineConfig, type PhaseEvent, type PhaseState } from "@/lib/record/phaseMachine";

export interface PhaseTransition {
  readonly previous: PhaseState;
  readonly next: PhaseState;
}

export interface PhaseMachine {
  readonly state: PhaseState;
  /** Applies an event now and returns the transition, so callers can react to it in the same handler. */
  send(event: PhaseEvent): PhaseTransition;
  /** Latest state, for event handlers that run between renders. */
  current(): PhaseState;
}

/**
 * Wraps the pure phase reducer. The latest state lives in a ref so the per-frame callback can read it
 * synchronously; React state is only set when the reducer returns a new object.
 */
export function usePhaseMachine(config: MachineConfig): PhaseMachine {
  const [state, setState] = useState<PhaseState>(INITIAL_PHASE_STATE);
  const stateRef = useRef<PhaseState>(INITIAL_PHASE_STATE);

  const send = useCallback((event: PhaseEvent): PhaseTransition => {
    const previous = stateRef.current;
    const next = phaseReducer(previous, event, config);
    if (next !== previous) {
      stateRef.current = next;
      setState(next);
    }
    return { previous, next };
  }, [config]);

  const current = useCallback(() => stateRef.current, []);

  return { state, send, current };
}
