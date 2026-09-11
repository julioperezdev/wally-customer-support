import { describe, expect, it, vi } from "vitest";
import { refreshReadOnlyPanels } from "./refresh";

const successfulLoaders = () => ({
  registry: vi.fn().mockResolvedValue(true),
  agentMap: vi.fn().mockResolvedValue(true),
  store: vi.fn().mockResolvedValue(true),
  featureFlags: vi.fn().mockResolvedValue(true)
});

describe("refreshReadOnlyPanels", () => {
  it("does not load panels when access validation fails", async () => {
    const loaders = successfulLoaders();

    const result = await refreshReadOnlyPanels(vi.fn().mockResolvedValue(false), loaders);

    expect(result).toEqual({ validated: false, failedPanels: [] });
    Object.values(loaders).forEach((loader) => expect(loader).not.toHaveBeenCalled());
  });

  it("validates first and refreshes every panel independently", async () => {
    const events: string[] = [];
    const loaders = successfulLoaders();
    const validateAccess = vi.fn().mockImplementation(async () => {
      events.push("validated");
      return true;
    });
    Object.entries(loaders).forEach(([name, loader]) => {
      loader.mockImplementation(async () => {
        events.push(name);
        return name !== "agentMap";
      });
    });

    const result = await refreshReadOnlyPanels(validateAccess, loaders);

    expect(events[0]).toBe("validated");
    expect(result).toEqual({ validated: true, failedPanels: ["agentMap"] });
    Object.values(loaders).forEach((loader) => expect(loader).toHaveBeenCalledOnce());
  });

  it("contains an unexpected panel rejection without cancelling the others", async () => {
    const loaders = successfulLoaders();
    loaders.store.mockRejectedValue(new Error("store unavailable"));

    const result = await refreshReadOnlyPanels(vi.fn().mockResolvedValue(true), loaders);

    expect(result).toEqual({ validated: true, failedPanels: ["store"] });
    expect(loaders.registry).toHaveBeenCalledOnce();
    expect(loaders.agentMap).toHaveBeenCalledOnce();
    expect(loaders.featureFlags).toHaveBeenCalledOnce();
  });
});
