import type { ProtocolResult } from "../sthira/params/protocol";
import type { TimerNormsResult } from "../timers/norms";

export type TestCard = {
  readonly href: string;
  readonly title: string;
  /** What the person being tested does, in everyday words. */
  readonly task: string;
  /** How long it takes, from params.json / norms.json; null when the test has no fixed length or the file did not load. */
  readonly time: string | null;
  readonly primary: boolean;
};

/** The home screen's three tests. Every number comes from the data files; a failed read shows none rather than a guess. */
export function testCards(protocol: ProtocolResult, timers: TimerNormsResult): readonly TestCard[] {
  const balance = timers.ok ? timers.norms.balance : null;
  const tug = timers.ok ? timers.norms.tug : null;
  return [
    {
      href: "/count",
      title: "Chair stand",
      task: "Stand up and sit down as many times as you can, arms crossed on the chest.",
      time: protocol.ok ? `${protocol.constants.chairStandDurationS} seconds` : null,
      primary: true,
    },
    {
      href: "/balance",
      title: "Balance",
      task: "Stand still in four foot positions, each a little harder.",
      time: balance === null ? null : `${balance.stages.length} positions, ${balance.stageDurationS} seconds each`,
      primary: false,
    },
    {
      href: "/tug",
      title: "Timed Up and Go",
      task:
        tug === null
          ? "Stand up, walk a short way at a normal pace, turn, walk back and sit down."
          : `Stand up, walk ${tug.walkDistanceMeters} metres at a normal pace, turn, walk back and sit down.`,
      time: null,
      primary: false,
    },
  ];
}
