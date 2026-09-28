import type {
  MatchRequest,
  MatchResult,
  RankResponse,
  RubricView,
} from "./types";

/** RFC 7807 problem detail, which is what the Spring error handler returns. */
interface ProblemDetail {
  title?: string;
  detail?: string;
  status?: number;
  upstreamStatus?: number;
}

export class ApiError extends Error {
  // Declared as fields rather than constructor parameter properties, which the
  // project's `erasableSyntaxOnly` setting disallows.
  readonly status: number;
  readonly upstreamStatus?: number;

  constructor(message: string, status: number, upstreamStatus?: number) {
    super(message);
    this.status = status;
    this.upstreamStatus = upstreamStatus;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    headers: { "Content-Type": "application/json", ...init?.headers },
  });

  if (!response.ok) {
    // A Jev failure arrives as a problem detail naming the upstream status,
    // which is far more useful to show than "request failed".
    let problem: ProblemDetail = {};
    try {
      problem = (await response.json()) as ProblemDetail;
    } catch {
      // Non-JSON error body; fall through to the generic message.
    }
    throw new ApiError(
      problem.detail ?? problem.title ?? `Request failed (${response.status})`,
      response.status,
      problem.upstreamStatus,
    );
  }
  return (await response.json()) as T;
}

export const api = {
  rubrics: () => request<RubricView[]>("/api/v1/rubrics"),

  industries: () => request<string[]>("/api/v1/industries"),

  sample: (industry?: string, seed?: number) => {
    const params = new URLSearchParams();
    if (industry) params.set("industry", industry);
    if (seed !== undefined) params.set("seed", String(seed));
    return request<MatchRequest>(`/api/v1/demo/sample?${params}`);
  },

  match: (body: MatchRequest) =>
    request<MatchResult>("/api/v1/match", {
      method: "POST",
      body: JSON.stringify(body),
    }),

  rank: (options: {
    industry?: string;
    candidateIndustry?: string;
    candidates: number;
    seed?: number;
    rubricId?: string;
  }) => {
    const params = new URLSearchParams({ candidates: String(options.candidates) });
    if (options.industry) params.set("industry", options.industry);
    if (options.candidateIndustry)
      params.set("candidateIndustry", options.candidateIndustry);
    if (options.seed !== undefined) params.set("seed", String(options.seed));
    if (options.rubricId) params.set("rubricId", options.rubricId);
    return request<RankResponse>(`/api/v1/demo/rank?${params}`, { method: "POST" });
  },
};
