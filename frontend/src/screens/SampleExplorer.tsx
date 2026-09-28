import { useState } from "react";
import { useQuery } from "@tanstack/react-query";
import { RefreshCw } from "lucide-react";
import { api } from "@/api/client";
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
import { Skeleton } from "@/components/ui/skeleton";
import { formatSalary } from "@/lib/format";

/**
 * The synthetic data before any scoring happens.
 *
 * <p>Both fakers draw from per-industry pools, so generating a posting and a candidate for the
 * same industry gives them a shared skills vocabulary — which is what makes same-industry versus
 * cross-industry a usable approximate ground truth for the matcher.
 */
/** A canonical key from /industries — the faker aliases such as "technology" are not listed there. */
const DEFAULT_INDUSTRY = "technology_information_and_media";

export function SampleExplorer() {
  const [industry, setIndustry] = useState(DEFAULT_INDUSTRY);
  const [seed, setSeed] = useState(() => Math.floor(Math.random() * 10_000));

  const industries = useQuery({ queryKey: ["industries"], queryFn: api.industries });
  const sample = useQuery({
    queryKey: ["sample", industry, seed],
    queryFn: () => api.sample(industry, seed),
  });

  return (
    <div className="space-y-6">
      <Card>
        <CardContent className="flex flex-wrap items-end gap-4 pt-6">
          <div className="min-w-56 space-y-1.5">
            <Label htmlFor="sample-industry">Industry</Label>
            <Select value={industry} onValueChange={setIndustry}>
              <SelectTrigger id="sample-industry" className="w-full">
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
          <Button
            variant="outline"
            onClick={() => setSeed(Math.floor(Math.random() * 10_000))}
          >
            <RefreshCw className="size-4" aria-hidden />
            Regenerate
          </Button>
          <span className="pb-2 font-mono text-xs text-muted-foreground">seed {seed}</span>
        </CardContent>
      </Card>

      {sample.isPending ? <Skeleton className="h-96 w-full" /> : null}

      {sample.data ? (
        <div className="grid gap-4 lg:grid-cols-2">
          <Card>
            <CardHeader>
              <CardTitle className="text-base">{sample.data.jobPosting.title}</CardTitle>
              <p className="text-sm text-muted-foreground">
                {sample.data.jobPosting.hiringOrganization} · {sample.data.jobPosting.industry}
              </p>
            </CardHeader>
            <CardContent className="space-y-4 text-sm">
              <p className="text-muted-foreground">{sample.data.jobPosting.description}</p>
              <Field label="Required skills">
                <div className="flex flex-wrap gap-1.5">
                  {sample.data.jobPosting.skills.map((s) => (
                    <Badge key={s} variant="secondary">
                      {s}
                    </Badge>
                  ))}
                </div>
              </Field>
              <Field label="Location">{sample.data.jobPosting.jobLocation}</Field>
              <Field label="Employment type">{sample.data.jobPosting.employmentType}</Field>
              <Field label="Salary">{formatSalary(sample.data.jobPosting.baseSalary)}</Field>
              <Field label="Open">
                {sample.data.jobPosting.datePosted} → {sample.data.jobPosting.validThrough}
              </Field>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle className="text-base">
                {sample.data.candidate.firstName} {sample.data.candidate.lastName}
              </CardTitle>
              <p className="text-sm text-muted-foreground">
                {sample.data.candidate.address.city}, {sample.data.candidate.address.state}
              </p>
            </CardHeader>
            <CardContent className="space-y-4 text-sm">
              <p className="text-muted-foreground">
                {sample.data.candidate.professionalSummary}
              </p>
              <Field label="Skills">
                <div className="flex flex-wrap gap-1.5">
                  {sample.data.candidate.skills.map((s) => (
                    <Badge key={s} variant="outline">
                      {s}
                    </Badge>
                  ))}
                </div>
              </Field>
              <Field label="Experience">
                <ul className="space-y-2">
                  {sample.data.candidate.jobHistory.map((job, i) => (
                    <li key={i}>
                      <span className="font-medium">{job.designation}</span>
                      <span className="text-muted-foreground">
                        {" "}
                        · {job.companyName} · {job.startDate} →{" "}
                        {job.endDate ?? "present"}
                      </span>
                    </li>
                  ))}
                </ul>
              </Field>
              <Field label="Education">
                <ul className="space-y-1">
                  {sample.data.candidate.educationHistory.map((edu, i) => (
                    <li key={i} className="text-muted-foreground">
                      {edu.courseName} — {edu.specialization} ({edu.institutionName})
                    </li>
                  ))}
                </ul>
              </Field>
              {sample.data.candidate.certifications.length > 0 ? (
                <Field label="Certifications">
                  <div className="flex flex-wrap gap-1.5">
                    {sample.data.candidate.certifications.map((c) => (
                      <Badge key={c.name} variant="outline">
                        {c.name}
                      </Badge>
                    ))}
                  </div>
                </Field>
              ) : null}
            </CardContent>
          </Card>
        </div>
      ) : null}

      <p className="text-xs text-muted-foreground">
        Name, email, phone and street address are shown here but never sent to Jev — the state
        builder strips identifying fields before the model sees anything.
      </p>
    </div>
  );
}

function Field({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="space-y-1">
      <span className="text-xs font-medium text-muted-foreground">{label}</span>
      <div>{children}</div>
    </div>
  );
}
