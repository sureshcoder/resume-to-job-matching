import { useState } from "react";
import { ShieldAlert } from "lucide-react";
import type { RubricView } from "@/api/types";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { humanise } from "@/lib/format";

/**
 * The rubrics themselves: the criteria levels are the interesting part, because a Jev score is an
 * index into them. Seeing "3.2" next to the level text is what makes the number legible.
 */
export function RubricViewer({ rubrics }: { rubrics: RubricView[] }) {
  const [selected, setSelected] = useState(rubrics[0]?.id ?? "");
  const rubric = rubrics.find((r) => r.id === selected) ?? rubrics[0];
  if (!rubric) return null;

  const scored = rubric.questions.filter((q) => q.type === "score");
  const gates = rubric.questions.filter((q) => q.type === "noul");
  const totalWeight = scored.reduce((sum, q) => sum + q.weight, 0);

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap gap-2">
        {rubrics.map((r) => (
          <Button
            key={r.id}
            variant={r.id === selected ? "default" : "outline"}
            size="sm"
            onClick={() => setSelected(r.id)}
          >
            {r.displayName}
            {r.isDefault ? (
              <Badge variant="secondary" className="ml-1.5">
                default
              </Badge>
            ) : null}
          </Button>
        ))}
      </div>

      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">{rubric.displayName}</CardTitle>
          <p className="text-sm text-muted-foreground">
            <span className="font-mono text-xs">{rubric.id}</span> · {scored.length} scored
            dimension{scored.length === 1 ? "" : "s"} · weights sum to{" "}
            {totalWeight.toFixed(2)}
          </p>
        </CardHeader>
        <CardContent className="space-y-6">
          {scored.map((question) => (
            <div key={question.key} className="space-y-2">
              <div className="flex flex-wrap items-baseline justify-between gap-2">
                <h3 className="font-medium">{humanise(question.key)}</h3>
                <Badge variant="secondary">weight {question.weight.toFixed(2)}</Badge>
              </div>
              <p className="text-sm text-muted-foreground">{question.instructions}</p>
              <ol className="space-y-1">
                {(question.levels ?? []).map((level, index) => (
                  <li key={index} className="flex gap-3 text-sm">
                    <span className="w-5 shrink-0 text-right font-mono text-xs text-muted-foreground">
                      {index}
                    </span>
                    <span>{level}</span>
                  </li>
                ))}
              </ol>
              <p className="text-xs text-muted-foreground">
                Jev returns a probability-weighted index across these {question.levels?.length}{" "}
                levels, so a score of 0–{(question.levels?.length ?? 1) - 1} normalises to 0–1.
              </p>
            </div>
          ))}

          {gates.length > 0 ? (
            <div className="space-y-3 rounded-lg border border-amber-500/30 bg-amber-500/5 p-4">
              <div className="flex items-center gap-2">
                <ShieldAlert className="size-4 text-amber-600 dark:text-amber-400" aria-hidden />
                <h3 className="font-medium">Gates</h3>
              </div>
              <p className="text-xs text-muted-foreground">
                Gates are <code>noul</code> questions — a truth value on 0–1. They are never
                averaged into the composite; falling below the minimum forces a gate failure
                outright, so a strong score cannot paper over a missing hard requirement.
              </p>
              {gates.map((gate) => (
                <div key={gate.key} className="space-y-1">
                  <div className="flex items-baseline justify-between gap-2">
                    <span className="text-sm font-medium">{humanise(gate.key)}</span>
                    <Badge variant="outline">minimum {gate.minimum}</Badge>
                  </div>
                  <p className="text-sm text-muted-foreground">{gate.instructions}</p>
                </div>
              ))}
            </div>
          ) : null}
        </CardContent>
      </Card>
    </div>
  );
}
