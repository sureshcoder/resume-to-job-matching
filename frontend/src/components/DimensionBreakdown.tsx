import { Bar, BarChart, Cell, ResponsiveContainer, XAxis, YAxis } from "recharts";
import { ShieldCheck, ShieldX } from "lucide-react";
import type { DimensionScore, GateResult } from "@/api/types";
import { Badge } from "@/components/ui/badge";
import { ScoreMeter } from "./ScoreMeter";
import { humanise } from "@/lib/format";

/**
 * Why a candidate scored what they did.
 *
 * <p>Contribution is what actually drives the ranking (normalised score x weight), so that is what
 * the chart plots. The raw score and the criteria level it landed on sit alongside, because a
 * number like 3.2 only means something next to the level text it indexes into.
 */
export function DimensionBreakdown({
  dimensions,
  gates,
}: {
  dimensions: DimensionScore[];
  gates: GateResult[];
}) {
  const chartData = dimensions.map((d) => ({
    name: humanise(d.key),
    contribution: Number(d.contribution.toFixed(3)),
    confidence: d.confidence,
  }));

  return (
    <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_minmax(0,1.1fr)]">
      <div>
        <h4 className="mb-3 text-sm font-medium">Contribution to composite</h4>
        <ResponsiveContainer width="100%" height={Math.max(140, dimensions.length * 38)}>
          <BarChart data={chartData} layout="vertical" margin={{ left: 8, right: 16 }}>
            <XAxis type="number" domain={[0, "dataMax"]} hide />
            <YAxis
              type="category"
              dataKey="name"
              width={130}
              tickLine={false}
              axisLine={false}
              tick={{ fontSize: 12 }}
            />
            <Bar dataKey="contribution" radius={[0, 4, 4, 0]} barSize={18}>
              {chartData.map((entry) => (
                // Low-confidence dimensions are drawn muted, so an unreliable
                // contribution does not read as solid evidence.
                <Cell
                  key={entry.name}
                  fill={entry.confidence < 0.5 ? "var(--color-muted-foreground)" : "var(--color-primary)"}
                />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      </div>

      <div className="space-y-3">
        {dimensions.map((dimension) => (
          <div key={dimension.key} className="space-y-1.5">
            <div className="flex items-baseline justify-between gap-3">
              <span className="text-sm font-medium">{humanise(dimension.key)}</span>
              <span className="font-mono text-xs text-muted-foreground">
                raw {dimension.rawScore.toFixed(2)} · weight {dimension.weight.toFixed(2)}
              </span>
            </div>
            {dimension.level ? (
              <p className="text-xs text-muted-foreground">“{dimension.level}”</p>
            ) : null}
            <ScoreMeter value={dimension.normalisedScore} />
            <ScoreMeter value={dimension.confidence} tone="confidence" label="confidence" />
          </div>
        ))}

        {gates.length > 0 ? (
          <div className="pt-2">
            <h4 className="mb-2 text-sm font-medium">Gates</h4>
            <div className="flex flex-wrap gap-2">
              {gates.map((gate) => (
                <Badge
                  key={gate.key}
                  variant="outline"
                  className={
                    gate.passed
                      ? "border-emerald-500/30 bg-emerald-500/10 text-emerald-700 dark:text-emerald-300"
                      : "border-red-500/30 bg-red-500/10 text-red-700 dark:text-red-300"
                  }
                  title={`noul ${gate.probability.toFixed(2)} against a minimum of ${gate.minimum}`}
                >
                  {gate.passed ? (
                    <ShieldCheck className="size-3.5" aria-hidden />
                  ) : (
                    <ShieldX className="size-3.5" aria-hidden />
                  )}
                  {humanise(gate.key)} · {gate.probability.toFixed(2)}
                </Badge>
              ))}
            </div>
          </div>
        ) : null}
      </div>
    </div>
  );
}
