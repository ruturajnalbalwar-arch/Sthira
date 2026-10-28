import type { CounterResult } from "../sthira/counter";
import type { ChairStandParams } from "../sthira/params/required";

/**
 * The serialisable part of `createCounter`'s result, so a Server Component can hand it to the
 * client. A ready counter carries its validated params; the client rebuilds the context from them.
 */
export type CounterStatus =
  | { readonly status: "ready"; readonly params: ChairStandParams }
  | { readonly status: "untuned"; readonly missing: readonly string[] }
  | { readonly status: "invalid"; readonly errors: readonly string[] };

export function toCounterStatus(result: CounterResult): CounterStatus {
  switch (result.status) {
    case "ready":
      return { status: "ready", params: result.params };
    case "untuned":
      return { status: "untuned", missing: [...result.missing] };
    case "invalid":
      return { status: "invalid", errors: [...result.errors] };
  }
}
