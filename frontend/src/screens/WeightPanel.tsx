import { RotateCcw, Zap } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Slider } from "@/components/ui/slider";
import { humanise } from "@/lib/format";

/**
 * Live re-weighting.
 *
 * <p>Dragging these re-ranks instantly and never calls Jev. That is not an optimisation — it is
 * what the composite-scoring pattern buys you: the model scores each dimension independently, and
 * the judgement about what matters for a given role stays in application code. The same model
 * output can be read as an IC opening or a management opening without asking the model again.
 */
export function WeightPanel({
  weights,
  original,
  onChange,
  onReset,
  dirty,
}: {
  weights: Record<string, number>;
  original: Record<string, number>;
  onChange: (key: string, value: number) => void;
  onReset: () => void;
  dirty: boolean;
}) {
  const total = Object.values(weights).reduce((sum, w) => sum + w, 0);
  const keys = Object.keys(original);

  return (
    <div className="rounded-lg border bg-card p-4">
      <div className="mb-3 flex items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <Zap className="size-4 text-amber-500" aria-hidden />
          <h3 className="text-sm font-medium">Re-weight dimensions</h3>
        </div>
        <Button
          variant="ghost"
          size="sm"
          onClick={onReset}
          disabled={!dirty}
          aria-label="Reset weights to the rubric defaults"
        >
          <RotateCcw className="size-3.5" aria-hidden />
          Reset
        </Button>
      </div>

      <p className="mb-4 text-xs text-muted-foreground">
        Recomputed in the browser from the scores Jev already returned — no new model call, no
        cost. Weights are normalised by their total, so the composite stays on 0–1 while you drag.
      </p>

      <div className="space-y-4">
        {keys.map((key) => (
          <div key={key}>
            <div className="mb-1.5 flex items-baseline justify-between">
              <label htmlFor={`weight-${key}`} className="text-sm">
                {humanise(key)}
              </label>
              <span className="font-mono text-xs tabular-nums text-muted-foreground">
                {(weights[key] / (total || 1)).toFixed(2)}
                {Math.abs(weights[key] - original[key]) > 0.001 ? (
                  <span className="ml-1 text-amber-600 dark:text-amber-400">
                    (was {original[key].toFixed(2)})
                  </span>
                ) : null}
              </span>
            </div>
            <Slider
              id={`weight-${key}`}
              value={[weights[key]]}
              min={0}
              max={1}
              step={0.01}
              onValueChange={([value]) => onChange(key, value)}
            />
          </div>
        ))}
      </div>
    </div>
  );
}
