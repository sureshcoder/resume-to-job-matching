import { useQuery } from "@tanstack/react-query";
import { Binary, ListOrdered, ScrollText, Sparkles, Target } from "lucide-react";
import { api } from "@/api/client";
import { ErrorBoundary } from "@/components/ErrorBoundary";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { MatchDetail } from "@/screens/MatchDetail";
import { RankingWorkbench } from "@/screens/RankingWorkbench";
import { RubricViewer } from "@/screens/RubricViewer";
import { SampleExplorer } from "@/screens/SampleExplorer";

const PRIMITIVES = [
  {
    name: "noul",
    description: "Truth of a statement, returned as a probability on 0–1. Used here as a gate.",
  },
  {
    name: "choice",
    description: "Selects one option, with probabilities across all of them and a confidence.",
  },
  {
    name: "score",
    description:
      "Rates against an ordered rubric, returning a probability-weighted index plus confidence.",
  },
];

export default function App() {
  const rubrics = useQuery({ queryKey: ["rubrics"], queryFn: api.rubrics });

  return (
    <div className="min-h-screen bg-background">
      <header className="border-b">
        <div className="mx-auto max-w-7xl px-6 py-6">
          <div className="flex items-center gap-2">
            <Target className="size-5 text-primary" aria-hidden />
            <h1 className="text-lg font-semibold">Resume to job matching</h1>
          </div>
          <p className="mt-1 max-w-3xl text-sm text-muted-foreground">
            Candidates scored against job postings with{" "}
            <span className="font-medium text-foreground">Jev</span>, TypeSafe AI's System One
            model. Jev does not generate text — it answers typed questions and returns values with
            probability distributions. The weights that turn those into a ranking live in
            application code, not in the model.
          </p>
          <div className="mt-4 grid gap-2 sm:grid-cols-3">
            {PRIMITIVES.map((primitive) => (
              <Card key={primitive.name} className="py-0">
                <CardContent className="flex gap-2 p-3">
                  <Binary className="mt-0.5 size-3.5 shrink-0 text-muted-foreground" aria-hidden />
                  <div>
                    <code className="text-xs font-semibold">{primitive.name}</code>
                    <p className="text-xs text-muted-foreground">{primitive.description}</p>
                  </div>
                </CardContent>
              </Card>
            ))}
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-7xl px-6 py-6">
        {rubrics.isPending ? <Skeleton className="h-96 w-full" /> : null}

        {rubrics.isError ? (
          <Card>
            <CardContent className="pt-6 text-sm">
              <p className="font-medium">Could not reach the API</p>
              <p className="mt-1 text-muted-foreground">
                {(rubrics.error as Error).message}. Is the Spring app running on :8080?
              </p>
            </CardContent>
          </Card>
        ) : null}

        {rubrics.data ? (
          <Tabs defaultValue="rank">
            <TabsList>
              <TabsTrigger value="rank">
                <ListOrdered className="size-4" aria-hidden />
                Ranking
              </TabsTrigger>
              <TabsTrigger value="match">
                <Target className="size-4" aria-hidden />
                Single match
              </TabsTrigger>
              <TabsTrigger value="sample">
                <Sparkles className="size-4" aria-hidden />
                Sample data
              </TabsTrigger>
              <TabsTrigger value="rubrics">
                <ScrollText className="size-4" aria-hidden />
                Rubrics
              </TabsTrigger>
            </TabsList>

            <TabsContent value="rank" className="mt-6">
              <ErrorBoundary label="Ranking"><RankingWorkbench rubrics={rubrics.data} /></ErrorBoundary>
            </TabsContent>
            <TabsContent value="match" className="mt-6">
              <ErrorBoundary label="Single match"><MatchDetail rubrics={rubrics.data} /></ErrorBoundary>
            </TabsContent>
            <TabsContent value="sample" className="mt-6">
              <ErrorBoundary label="Sample data"><SampleExplorer /></ErrorBoundary>
            </TabsContent>
            <TabsContent value="rubrics" className="mt-6">
              <ErrorBoundary label="Rubrics"><RubricViewer rubrics={rubrics.data} /></ErrorBoundary>
            </TabsContent>
          </Tabs>
        ) : null}
      </main>
    </div>
  );
}
