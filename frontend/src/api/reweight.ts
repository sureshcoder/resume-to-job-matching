import type { MatchResult } from "./types";

/**
 * Recomputes a composite score from new weights, in the browser.
 *
 * <p>This is the whole point of the composite-scoring pattern: Jev returns each dimension scored
 * independently and normalised to 0-1, and the weights that turn those into a single number live
 * in our code, not in the model. Re-weighting therefore needs no new Jev call, costs nothing, and
 * is instant — the same model output re-read for a different role shape.
 */
export function reweight(
  result: MatchResult,
  weights: Record<string, number>,
): MatchResult {
  const total = Object.values(weights).reduce((sum, w) => sum + w, 0);
  if (total <= 0) return result;

  const dimensions = result.dimensions.map((dimension) => {
    // Normalising by the total keeps the composite on 0-1 even while sliders are
    // mid-drag and the weights do not yet sum to 1.
    const weight = (weights[dimension.key] ?? dimension.weight) / total;
    return {
      ...dimension,
      weight,
      contribution: dimension.normalisedScore * weight,
    };
  });

  const compositeScore = dimensions.reduce((sum, d) => sum + d.contribution, 0);
  const confidence = dimensions.reduce((sum, d) => sum + d.confidence * d.weight, 0);

  return { ...result, dimensions, compositeScore, confidence };
}

export function isReweighted(
  weights: Record<string, number>,
  original: Record<string, number>,
): boolean {
  return Object.keys(original).some(
    (key) => Math.abs((weights[key] ?? 0) - original[key]) > 0.001,
  );
}
