"use client";

import { useEffect, useRef } from "react";

/** Display refresh (~10 Hz). Display only: results come from the exact start/stop times. */
const TICK_MS = 100;

export const nowSeconds = (): number => performance.now() / 1000;

/** Calls onTick(nowSeconds()) about ten times a second while `active`. */
export function useTicker(active: boolean, onTick: (t: number) => void): void {
  const onTickRef = useRef(onTick);
  useEffect(() => {
    onTickRef.current = onTick;
  }, [onTick]);
  useEffect(() => {
    if (!active) return;
    const id = setInterval(() => onTickRef.current(nowSeconds()), TICK_MS);
    return () => clearInterval(id);
  }, [active]);
}
