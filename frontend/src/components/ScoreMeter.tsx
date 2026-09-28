import { cn } from "@/lib/utils";

/** A 0-1 value as a labelled bar. Used for composite scores and confidence alike. */
export function ScoreMeter({
  value,
  label,
  tone = "score",
  className,
}: {
  value: number;
  label?: string;
  tone?: "score" | "confidence";
  className?: string;
}) {
  const percent = Math.round(Math.min(Math.max(value, 0), 1) * 100);
  return (
    <div className={cn("flex items-center gap-2", className)}>
      <div className="relative h-2 w-full overflow-hidden rounded-full bg-muted">
        <div
          className={cn(
            "h-full rounded-full transition-[width] duration-300",
            tone === "score" ? "bg-primary" : "bg-sky-500",
          )}
          style={{ width: `${percent}%` }}
        />
      </div>
      <span className="w-12 shrink-0 text-right font-mono text-xs tabular-nums text-muted-foreground">
        {value.toFixed(3)}
      </span>
      {label ? (
        <span className="shrink-0 text-xs text-muted-foreground">{label}</span>
      ) : null}
    </div>
  );
}
