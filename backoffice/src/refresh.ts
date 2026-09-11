export type ReadOnlyRefreshPanel = "registry" | "agentMap" | "store" | "featureFlags";

export type ReadOnlyRefreshResult = {
  validated: boolean;
  failedPanels: ReadOnlyRefreshPanel[];
};

type Loader = () => Promise<boolean>;

/**
 * Validates access before refreshing the remaining read-only panels.
 * Each panel is isolated so one failure does not cancel the others.
 */
export async function refreshReadOnlyPanels(
  validateAccess: Loader,
  loaders: Record<ReadOnlyRefreshPanel, Loader>
): Promise<ReadOnlyRefreshResult> {
  let validated = false;
  try {
    validated = await validateAccess();
  } catch {
    validated = false;
  }

  if (!validated) return { validated: false, failedPanels: [] };

  const results = await Promise.all(
    (Object.entries(loaders) as Array<[ReadOnlyRefreshPanel, Loader]>).map(async ([panel, loader]) => {
      try {
        return [panel, await loader()] as const;
      } catch {
        return [panel, false] as const;
      }
    })
  );

  return {
    validated: true,
    failedPanels: results.filter(([, succeeded]) => !succeeded).map(([panel]) => panel)
  };
}
