/** Mirrors the Java records served by the Spring API. */

export type ReviewDecision =
  | "AUTO_ADVANCE"
  | "FLAG_FOR_REVIEW"
  | "HUMAN_REVIEW"
  | "GATE_FAILED";

export interface BaseSalary {
  currency: string;
  minValue: number;
  maxValue: number;
  unitText: string;
}

export interface JobPosting {
  industry: string;
  title: string;
  description: string;
  skills: string[];
  datePosted: string;
  validThrough: string;
  employmentType: string;
  hiringOrganization: string;
  jobLocation: string;
  baseSalary: BaseSalary;
}

export interface Address {
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  country: string;
  zipCode: string;
}

export interface EducationHistory {
  institutionName: string;
  degreeLevel: string;
  courseName: string;
  specialization: string;
  startDate: string;
  endDate: string;
  scoreType: string;
  scoreValue: number;
}

export interface JobHistory {
  companyName: string;
  role: string;
  designation: string;
  startDate: string;
  endDate: string | null;
  responsibilities: string[];
  skills: string[];
  current: boolean;
}

export interface Certification {
  name: string;
  issuingOrganization: string;
  issuedDate: string;
}

export interface Candidate {
  firstName: string;
  lastName: string;
  email: string;
  mobileNumber: string;
  professionalSummary: string;
  address: Address;
  educationHistory: EducationHistory[];
  jobHistory: JobHistory[];
  skills: string[];
  certifications: Certification[];
}

export interface DimensionScore {
  key: string;
  /** Jev's raw probability-weighted index into the criteria levels. */
  rawScore: number;
  /** rawScore rescaled to 0-1, which is what re-weighting multiplies. */
  normalisedScore: number;
  weight: number;
  contribution: number;
  confidence: number;
  level: string | null;
}

export interface GateResult {
  key: string;
  probability: number;
  minimum: number;
  passed: boolean;
}

export interface Usage {
  inputTokens: number;
  outputTokens: number;
}

export interface MatchResult {
  candidateName: string;
  candidateHeadline: string;
  jobTitle: string;
  industry: string;
  rubricId: string;
  rubricFallback: boolean;
  compositeScore: number;
  confidence: number;
  decision: ReviewDecision;
  dimensions: DimensionScore[];
  gates: GateResult[];
  usage: Usage;
}

export interface RankResponse {
  jobPosting: JobPosting;
  results: MatchResult[];
}

export interface MatchRequest {
  jobPosting: JobPosting;
  candidate: Candidate;
  rubricId?: string | null;
}

export interface RubricQuestionView {
  key: string;
  type: "score" | "noul" | "choice";
  instructions: string;
  levels: string[] | null;
  options: Record<string, string> | null;
  weight: number;
  minimum: number;
}

export interface RubricView {
  id: string;
  displayName: string;
  isDefault: boolean;
  questions: RubricQuestionView[];
}
