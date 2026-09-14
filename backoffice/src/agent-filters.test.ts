import { describe, expect, it } from "vitest";
import { deriveAgentFilterOptions, filterValuesFor } from "./agent-filters";

describe("agent filters", () => {
  const options = deriveAgentFilterOptions([
    {
      agentId: "catalog-specialist",
      versions: [{ agentId: "catalog-specialist" } as never],
      activations: [{ agentId: "catalog-specialist", agentVersion: 1, environment: "prod", channel: "telegram", useCase: "catalog-search" } as never]
    },
    {
      agentId: "support",
      versions: [{ agentId: "support" } as never],
      activations: [{ agentId: "support", agentVersion: 1, environment: "prod", channel: "whatsapp", useCase: "general-support" } as never]
    }
  ]);

  it("derives unique values from agent assignments", () => {
    expect(options.agentIds).toEqual(["catalog-specialist", "support"]);
    expect(options.channels).toEqual(["telegram", "whatsapp"]);
    expect(options.useCases).toEqual(["catalog-search", "general-support"]);
  });

  it("restricts dependent options to valid assignments", () => {
    expect(filterValuesFor(options, {
      agentId: "catalog-specialist",
      environment: "prod",
      channel: "",
      useCase: ""
    }, "channel")).toEqual(["telegram"]);
  });
});
