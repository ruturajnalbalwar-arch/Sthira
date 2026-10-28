import { z } from 'zod';

export const PARAM_CLASSES = [
  'protocol-constant',
  'per-person-calibration',
  'fitted',
  'cited-reference',
] as const;

export const ParamClassSchema = z.enum(PARAM_CLASSES);
export type ParamClass = z.infer<typeof ParamClassSchema>;

export const ParamEntrySchema = z.object({
  value: z.number(),
  class: ParamClassSchema,
  unit: z.string().min(1),
  source: z.string().min(1),
  script: z.string().min(1).optional(),
  date: z.string().min(1),
});
export type ParamEntry = z.infer<typeof ParamEntrySchema>;

export const ParamsFileSchema = z.object({
  version: z.string().min(1),
  generatedAt: z.string().min(1),
  params: z.record(z.string(), ParamEntrySchema),
});
export type ParamsFile = z.infer<typeof ParamsFileSchema>;
