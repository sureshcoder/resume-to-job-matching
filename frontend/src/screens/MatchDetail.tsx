import { useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { Loader2, Play, RefreshCw, TriangleAlert } from "lucide-react";
import { api } from "@/api/client";
import type { RubricView } from "@/api/types";
import { DecisionBadge, decisionHint } from "@/components/DecisionBadge";
import { DimensionBreakdown } from "@/components/DimensionBreakdown";
import { ScoreMeter } from "@/components/ScoreMeter";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Separator } from "@/components/ui/separator";
import { formatSalary } from "@/lib/format";

const ANY = "__any__";
/** A canonical key from /industries — the faker aliases such as "technology" are not listed there. */
const DEFAULT_INDUSTRY = "technology_information_and_media";

/** One pair, scored in full: every dimension, every gate, and what it cost. */
export function MatchDetail({ rubrics }: { rubrics: RubricView[] }) {
  const [industry, setIndustry] = useState(DEFAULT_INDUSTRY);
  const [rubricId, setRubricId] = useState(ANY);
  const [seed, setSeed] = useState(() => Math.floor(Math.random() * 10_000));

  const industries = useQuery({ queryKey: ["industries"], queryFn: api.industries });
  const sample = useQuery({
    queryKey: ["sample", industry, seed],
    queryFn: () => api.sample(industry, seed),
  });

  const match = useMutation({
    mutationFn: () => {
      if (!sample.data) throw new Error("no sample loaded");
      return api.match({
        ...sample.data,
        rubricId: rubricId === ANY ? null : rubricId,
      });
    },
  });

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader className="pb-3">
          <CardTitle className="text-base">Score a single pair</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 sm:grid-cols-3">
            <div className="space-y-1.5">
              <Label htmlFor="detail-industry">Industry</Label>
              <Select value={industry} onValueChange={setIndustry}>
                <SelectTrigger id="detail-industry" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {(industries.data ?? []).map((value) => (
                    <SelectItem key={value} value={value}>
                      {value}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="detail-rubric">Rubric</Label>
              <Select value={rubricId} onValueChange={setRubricId}>
                <SelectTrigger id="detail-rubric" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ANY}>Resolve from industry</SelectItem>
                  {rubrics.map((rubric) => (
                    <SelectItem key={rubric.id} value={rubric.id}>
                      {rubric.displayName}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="flex items-end gap-2">
              <Button
                variant="outline"
                onClick={() => setSeed(Math.floor(Math.random() * 10_000))}
              >
                <RefreshCw className="size-4" aria-hidden />
                New pair
              </Button>
              <Button onClick={() => match.mutate()} disabled={!sample.data || match.isPending}>
                {match.isPending ? (
                  <Loader2 className="size-4 animate-spin" aria-hidden />
                ) : (
                  <Play className="size-4" aria-hidden />
                )}
                Score
              </Button>
            </div>
          </div>

          {match.isError ? (
            <div className="mt-4 flex items-start gap-2 rounded-md border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-700 dark:text-red-300">
              <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
              <span className="text-xs">{(match.error as Error).message}</span>
            </div>
          ) : null}
        </CardContent>
      </Card>

      {sample.data ? (
        <div className="grid gap-4 lg:grid-cols-2">
          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">{sample.data.jobPosting.title}</CardTitle>
              <p className="text-xs text-muted-foreground">
                {sample.data.jobPosting.hiringOrganization} ·{" "}
                {sample.data.jobPosting.jobLocation} ·{" "}
                {formatSalary(sample.data.jobPosting.baseSalary)}
              </p>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="text-sm text-muted-foreground">
                {sample.data.jobPosting.description}
              </p>
              <div className="flex flex-wrap gap-1.5">
                {sample.data.jobPosting.skills.map((skill) => (
                  <Badge key={skill} variant="secondary">
                    {skill}
                  </Badge>
                ))}
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader className="pb-2">
              <CardTitle className="text-sm">
                {sample.data.candidate.firstName} {sample.data.candidate.lastName}
              </CardTitle>
              <p className="text-xs text-muted-foreground">
                {sample.data.candidate.address.city}, {sample.data.candidate.address.state}
              </p>
            </CardHeader>
            <CardContent className="space-y-3">
              <p className="text-sm text-muted-foreground">
                {sample.data.candidate.professionalSummary}
              </p>
              <div className="flex flex-wrap gap-1.5">
                {sample.data.candidate.skills.map((skill) => (
                  <Badge key={skill} variant="outline">
                    {skill}
                  </Badge>
                ))}
              </div>
            </CardContent>
          </Card>
        </div>
      ) : null}

      {match.data ? (
        <Card>
          <CardHeader>
            <div className="flex flex-wrap items-center justify-between gap-4">
              <div>
                <CardTitle className="text-base">{match.data.candidateName}</CardTitle>
                <p className="text-sm text-muted-foreground">
                  {match.data.candidateHeadline} · scored with{" "}
                  <span className="font-mono text-xs">{match.data.rubricId}</span>
                </p>
              </div>
              <DecisionBadge decision={match.data.decision} />
            </div>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="grid gap-4 sm:grid-cols-2">
              <div className="space-y-1">
                <span className="text-xs text-muted-foreground">Composite score</span>
                <ScoreMeter value={match.data.compositeScore} />
              </div>
              <div className="space-y-1">
                <span className="text-xs text-muted-foreground">Confidence</span>
                <ScoreMeter value={match.data.confidence} tone="confidence" />
              </div>
            </div>
            <p className="text-xs text-muted-foreground">{decisionHint(match.data.decision)}</p>

            <Separator />
            <DimensionBreakdown dimensions={match.data.dimensions} gates={match.data.gates} />

            <Separator />
            <p className="font-mono text-xs text-muted-foreground">
              {(match.data.usage?.inputTokens ?? 0).toLocaleString()} input tokens ·{" "}
              {(match.data.usage?.outputTokens ?? 0).toLocaleString()} output tokens (billed on
              input only)
            </p>
          </CardContent>
        </Card>
      ) : null}
    </div>
  );
}
