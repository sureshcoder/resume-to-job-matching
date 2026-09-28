import { Component, type ErrorInfo, type ReactNode } from "react";
import { TriangleAlert } from "lucide-react";

/**
 * Stops one bad render from blanking the whole page.
 *
 * <p>Without this, a single unexpected field shape — an API returning `input_tokens` where the
 * client expected `inputTokens`, say — throws during render and React unmounts the entire tree,
 * leaving a white screen whose only clue is a minified stack in the console. A demo that shows
 * nothing is worse than one that shows what broke.
 */
export class ErrorBoundary extends Component<
  { children: ReactNode; label?: string },
  { error: Error | null }
> {
  state: { error: Error | null } = { error: null };

  static getDerivedStateFromError(error: Error) {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Render failed", error, info.componentStack);
  }

  render() {
    const { error } = this.state;
    if (!error) return this.props.children;

    return (
      <div className="rounded-lg border border-red-500/30 bg-red-500/5 p-6">
        <div className="flex items-start gap-3">
          <TriangleAlert
            className="mt-0.5 size-5 shrink-0 text-red-600 dark:text-red-400"
            aria-hidden
          />
          <div className="min-w-0 space-y-2">
            <h2 className="font-medium">
              {this.props.label ?? "This view"} failed to render
            </h2>
            <p className="text-sm text-muted-foreground">
              The data came back in a shape this screen did not expect. The details below and the
              browser console say which field.
            </p>
            <pre className="overflow-x-auto rounded bg-muted p-3 text-xs">{error.message}</pre>
            <button
              type="button"
              className="text-sm underline underline-offset-4"
              onClick={() => this.setState({ error: null })}
            >
              Try again
            </button>
          </div>
        </div>
      </div>
    );
  }
}
