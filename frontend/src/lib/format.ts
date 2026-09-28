/** Turns a rubric question key such as `skills_coverage` into `Skills coverage`. */
export function humanise(key: string): string {
  const spaced = key.replace(/_/g, " ");
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

export function formatSalary(salary: {
  currency: string;
  minValue: number;
  maxValue: number;
  unitText: string;
}): string {
  const format = (value: number) => value.toLocaleString();
  return `${salary.currency} ${format(salary.minValue)}–${format(salary.maxValue)} / ${salary.unitText.toLowerCase()}`;
}
