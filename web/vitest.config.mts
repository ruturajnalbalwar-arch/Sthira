import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    environment: 'node',
    include: ['src/**/*.test.ts'],
    coverage: {
      provider: 'v8',
      include: ['src/lib/**/*.ts'],
      exclude: [
        'src/**/*.test.ts',
        'src/lib/sthira/**/__tests__/**',
        'src/lib/sthira/norms/**',
        'src/lib/sthira/types.ts',
        // Browser-only glue (camera model loader, file save dialog): checked in the browser, not in node.
        'src/lib/pose/createPoseLandmarker.ts',
        'src/lib/record/saveFile.ts',
      ],
      thresholds: { statements: 80, branches: 80, functions: 80, lines: 80 },
    },
  },
});
