import { AlertTriangle, CheckCircle2, ShieldX, UserSearch } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import type { ReviewDecision } from "@/api/types";

/**
 * TypeSafe's three-tier confidence guidance, plus the gate override. Colour carries the same
 * meaning everywhere in the app: green acts automatically, amber wants a human to confirm,
 * slate needs a human to read it, red failed a hard requirement.
 */
const DECISIONS: Record<
  ReviewDecision,
  { label: string; className: string; icon: typeof CheckCircle2; hint: string }
> = {
  AUTO_ADVANCE: {
    label: "Auto-advance",
    className:
      "bg-emerald-500/15 text-emerald-700 dark:text-emerald-300 border-emerald-500/30",
    icon: CheckCircle2,
    hint: "Confidence above 0.9 — safe to act on without review",
  },
  FLAG_FOR_REVIEW: {
    label: "Flag for review",
    className:
      "bg-amber-500/15 text-amber-700 dark:text-amber-300 border-amber-500/30",
    icon: AlertTriangle,
    hint: "Confidence 0.5–0.9 — usable, but confirm before it changes a status",
  },
  HUMAN_REVIEW: {
    label: "Human review",
    className:
      "bg-slate-500/15 text-slate-700 dark:text-slate-300 border-slate-500/30",
    icon: UserSearch,
    hint: "Confidence below 0.5 — the model was not decisive; read the resume",
  },
  GATE_FAILED: {
    label: "Gate failed",
    className: "bg-red-500/15 text-red-700 dark:text-red-300 border-red-500/30",
    icon: ShieldX,
    hint: "A mandatory requirement was not met, whatever the composite score says",
  },
};

export function DecisionBadge({ decision }: { decision: ReviewDecision }) {
  const { label, className, icon: Icon, hint } = DECISIONS[decision];
  return (
    <Badge variant="outline" className={className} title={hint}>
      <Icon className="size-3.5" aria-hidden />
      {label}
    </Badge>
  );
}

export function decisionHint(decision: ReviewDecision) {
  return DECISIONS[decision].hint;
}
