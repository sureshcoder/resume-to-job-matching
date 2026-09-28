import { useMemo, useState } from "react";
import { useMutation, useQuery } from "@tanstack/react-query";
import { ChevronDown, Loader2, Play, TriangleAlert } from "lucide-react";
import { api } from "@/api/client";
import { isReweighted, reweight } from "@/api/reweight";
import type { RankResponse, RubricView } from "@/api/types";
import { DecisionBadge } from "@/components/DecisionBadge";
import { DimensionBreakdown } from "@/components/DimensionBreakdown";
import { ScoreMeter } from "@/components/ScoreMeter";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { formatSalary } from "@/lib/format";
import { WeightPanel } from "./WeightPanel";

const ANY = "__any__";
/** A canonical key from /industries — the faker aliases such as "technology" are not listed there. */
const DEFAULT_INDUSTRY = "technology_information_and_media";

export function RankingWorkbench({ rubrics }: { rubrics: RubricView[] }) {
  const [industry, setIndustry] = useState(DEFAULT_INDUSTRY);
  const [candidateIndustry, setCandidateIndustry] = useState(ANY);
  const [count, setCount] = useState(5);
  const [seed, setSeed] = useState<string>("42");
  const [rubricId, setRubricId] = useState(ANY);
  const [weights, setWeights] = useState<Record<string, number>>({});
  const [expanded, setExpanded] = useState<string | null>(null);

  const industries = useQuery({ queryKey: ["industries"], queryFn: api.industries });

  const ranking = useMutation({
    mutationFn: () =>
      api.rank({
        industry,
        candidateIndustry: candidateIndustry === ANY ? undefined : candidateIndustry,
        candidates: count,
        seed: seed === "" ? undefined : Number(seed),
        rubricId: rubricId === ANY ? undefined : rubricId,
      }),
    onSuccess: (data: RankResponse) => {
      // Seed the sliders from whatever rubric the backend actually resolved.
      const first = data.results[0];
      if (first) {
        setWeights(
          Object.fromEntries(first.dimensions.map((d) => [d.key, d.weight])),
        );
      }
      setExpanded(null);
    },
  });

  const original = useMemo(() => {
    const first = ranking.data?.results[0];
    return first
      ? Object.fromEntries(first.dimensions.map((d) => [d.key, d.weight]))
      : {};
  }, [ranking.data]);

  const dirty = isReweighted(weights, original);

  // Re-weighting re-sorts locally; the server's ordering is only the starting point.
  const results = useMemo(() => {
    if (!ranking.data) return [];
    const rows = dirty
      ? ranking.data.results.map((r) => reweight(r, weights))
      : ranking.data.results;
    return [...rows].sort((a, b) => b.compositeScore - a.compositeScore);
  }, [ranking.data, weights, dirty]);

  const totalTokens = ranking.data?.results.reduce(
    (sum, r) => sum + (r.usage?.inputTokens ?? 0),
    0,
  );

  return (
    <div className="space-y-6">
      <Card>
        <CardHeader>
          <CardTitle className="text-base">Generate and rank a candidate pool</CardTitle>
        </CardHeader>
        <CardContent>
          <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
            <div className="space-y-1.5">
              <Label htmlFor="industry">Posting industry</Label>
              <Select value={industry} onValueChange={setIndustry}>
                <SelectTrigger id="industry" className="w-full">
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
              <Label htmlFor="candidate-industry">Candidate industry</Label>
              <Select value={candidateIndustry} onValueChange={setCandidateIndustry}>
                <SelectTrigger id="candidate-industry" className="w-full">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value={ANY}>Same as posting</SelectItem>
                  {(industries.data ?? []).map((value) => (
                    <SelectItem key={value} value={value}>
                      {value}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <p className="text-xs text-muted-foreground">
                Set a different industry to see mismatches score low.
              </p>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="count">Candidates</Label>
              <Input
                id="count"
                type="number"
                min={1}
                max={25}
                value={count}
                onChange={(e) => setCount(Number(e.target.value))}
              />
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="seed">Seed</Label>
              <Input
                id="seed"
                value={seed}
                placeholder="random"
                onChange={(e) => setSeed(e.target.value)}
              />
              <p className="text-xs text-muted-foreground">Reproducible data.</p>
            </div>

            <div className="space-y-1.5">
              <Label htmlFor="rubric">Rubric</Label>
              <Select value={rubricId} onValueChange={setRubricId}>
                <SelectTrigger id="rubric" className="w-full">
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
          </div>

          <div className="mt-4 flex items-center gap-3">
            <Button onClick={() => ranking.mutate()} disabled={ranking.isPending}>
              {ranking.isPending ? (
                <Loader2 className="size-4 animate-spin" aria-hidden />
              ) : (
                <Play className="size-4" aria-hidden />
              )}
              {ranking.isPending ? `Scoring ${count} candidates…` : "Rank candidates"}
            </Button>
            {ranking.isPending ? (
              <span className="text-xs text-muted-foreground">
                One Jev call per candidate, fanned out concurrently.
              </span>
            ) : null}
          </div>

          {ranking.isError ? (
            <div className="mt-4 flex items-start gap-2 rounded-md border border-red-500/30 bg-red-500/10 p-3 text-sm text-red-700 dark:text-red-300">
              <TriangleAlert className="mt-0.5 size-4 shrink-0" aria-hidden />
              <div>
                <p className="font-medium">Ranking failed</p>
                <p className="text-xs opacity-90">{(ranking.error as Error).message}</p>
              </div>
            </div>
          ) : null}
        </CardContent>
      </Card>

      {ranking.isPending ? (
        <div className="space-y-2">
          {Array.from({ length: count }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full" />
          ))}
        </div>
      ) : null}

      {ranking.data ? (
        <div className="grid gap-6 lg:grid-cols-[minmax(0,1fr)_320px]">
          <div className="space-y-4">
            <Card>
              <CardHeader className="pb-3">
                <CardTitle className="text-base">
                  {ranking.data.jobPosting.title}
                </CardTitle>
                <p className="text-sm text-muted-foreground">
                  {ranking.data.jobPosting.hiringOrganization} ·{" "}
                  {ranking.data.jobPosting.jobLocation} ·{" "}
                  {formatSalary(ranking.data.jobPosting.baseSalary)}
                </p>
              </CardHeader>
              <CardContent className="pt-0">
                <div className="flex flex-wrap gap-1.5">
                  {ranking.data.jobPosting.skills.map((skill) => (
                    <Badge key={skill} variant="secondary">
                      {skill}
                    </Badge>
                  ))}
                </div>
              </CardContent>
            </Card>

            <div className="space-y-2">
              {results.map((result, index) => {
                const key = `${result.candidateName}-${index}`;
                const isOpen = expanded === key;
                return (
                  <Card key={key} className="overflow-hidden py-0">
                    <button
                      type="button"
                      onClick={() => setExpanded(isOpen ? null : key)}
                      aria-expanded={isOpen}
                      className="flex w-full items-center gap-4 p-4 text-left transition-colors hover:bg-muted/50"
                    >
                      <span className="w-6 shrink-0 font-mono text-sm text-muted-foreground">
                        {index + 1}
                      </span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-medium">
                          {result.candidateName}
                        </span>
                        <span className="block truncate text-sm text-muted-foreground">
                          {result.candidateHeadline}
                        </span>
                      </span>
                      <span className="hidden w-48 shrink-0 sm:block">
                        <ScoreMeter value={result.compositeScore} />
                      </span>
                      <DecisionBadge decision={result.decision} />
                      <ChevronDown
                        className={`size-4 shrink-0 text-muted-foreground transition-transform ${
                          isOpen ? "rotate-180" : ""
                        }`}
                        aria-hidden
                      />
                    </button>
                    {isOpen ? (
                      <div className="border-t bg-muted/20 p-4">
                        <DimensionBreakdown
                          dimensions={result.dimensions}
                          gates={result.gates}
                        />
                      </div>
                    ) : null}
                  </Card>
                );
              })}
            </div>
          </div>

          <div className="space-y-4">
            {Object.keys(original).length > 0 ? (
              <WeightPanel
                weights={weights}
                original={original}
                dirty={dirty}
                onChange={(key, value) =>
                  setWeights((prev) => ({ ...prev, [key]: value }))
                }
                onReset={() => setWeights(original)}
              />
            ) : null}

            <Card>
              <CardHeader className="pb-2">
                <CardTitle className="text-sm">This run</CardTitle>
              </CardHeader>
              <CardContent className="space-y-1.5 text-sm">
                <Row label="Rubric" value={ranking.data.results[0]?.rubricId ?? "—"} />
                <Row label="Jev calls" value={String(ranking.data.results.length)} />
                <Row label="Input tokens" value={(totalTokens ?? 0).toLocaleString()} />
                <Row
                  label="Output tokens"
                  value={`${ranking.data.results
                    .reduce((s, r) => s + (r.usage?.outputTokens ?? 0), 0)
                    .toLocaleString()} (free)`}
                />
                {ranking.data.results[0]?.rubricFallback ? (
                  <p className="pt-1 text-xs text-amber-600 dark:text-amber-400">
                    No rubric for this industry — fell back to generic.
                  </p>
                ) : null}
              </CardContent>
            </Card>
          </div>
        </div>
      ) : null}
    </div>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex justify-between gap-3">
      <span className="text-muted-foreground">{label}</span>
      <span className="font-mono text-xs tabular-nums">{value}</span>
    </div>
  );
}
