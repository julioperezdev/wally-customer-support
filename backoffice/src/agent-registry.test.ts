import { describe, expect, it } from "vitest";
import { assignmentValuesFor, draftFromVersion, versionsForAgent } from "./agent-registry";
import type { AgentFilterOptions } from "./api";

const VERSION = {
  agentId: "catalog-specialist",
  version: 3,
  name: "Catalog specialist",
  purpose: "Search the catalog",
  state: "APPROVED",
  modelProvider: "bedrock",
  modelId: "model-v3",
  temperature: 0.2,
  topP: 0.9,
  systemPromptVersion: "catalog-system-v3",
  systemPromptHash: "a".repeat(64),
  inputSchemaVersion: "catalog-input-v3",
  outputSchemaVersion: "catalog-output-v3",
  allowedTools: ["catalog.search", "catalog.stock"],
  knowledgeSources: ["wcs-catalog-kb"],
  memoryPolicy: "summary-v2",
  responsePolicy: "grounded-v2",
  timeoutMs: 8000,
  maxSteps: 2,
  maxInputTokens: 2000,
  maxOutputTokens: 1000,
  budgetLimitUsd: 0.05,
  fallbackAgentId: "support-safety",
  evaluationSuiteVersion: "catalog-eval-v3",
  createdAt: "2026-09-15T12:00:00Z",
  approvedAt: "2026-09-15T12:10:00Z"
};

const FILTER_OPTIONS: AgentFilterOptions = {
  agentIds: ["catalog-specialist", "knowledge-specialist"],
  environments: ["prod", "test"],
  channels: ["telegram", "whatsapp"],
  useCases: ["CATALOG_SEARCH", "GENERAL_SUPPORT"],
  assignments: [
    { agentId: "catalog-specialist", environment: "prod", channel: "telegram", useCase: "CATALOG_SEARCH" },
    { agentId: "catalog-specialist", environment: "prod", channel: "whatsapp", useCase: "CATALOG_SEARCH" },
    { agentId: "knowledge-specialist", environment: "test", channel: "telegram", useCase: "GENERAL_SUPPORT" }
  ]
};

describe("agent registry authoring helpers", () => {
  it("maps a sanitized version to an editable new-draft definition", () => {
    const definition = draftFromVersion(VERSION);

    expect(definition).toMatchObject({
      version: null,
      name: "Catalog specialist",
      modelId: "model-v3",
      fallbackAgentId: "support-safety"
    });
    expect(definition.allowedTools).toEqual(["catalog.search", "catalog.stock"]);
    expect(definition.knowledgeSources).toEqual(["wcs-catalog-kb"]);
  });

  it("returns versions only for the selected agent", () => {
    expect(versionsForAgent([
      { agentId: "catalog-specialist", versions: [VERSION], activations: [] },
      { agentId: "knowledge-specialist", versions: [], activations: [] }
    ], "catalog-specialist")).toEqual([VERSION]);
  });

  it("derives valid assignment values from the selected dimensions", () => {
    expect(assignmentValuesFor(FILTER_OPTIONS, {
      agentId: "catalog-specialist",
      environment: "prod",
      channel: "",
      useCase: "CATALOG_SEARCH"
    }, "channel")).toEqual(["telegram", "whatsapp"]);

    expect(assignmentValuesFor(FILTER_OPTIONS, {
      agentId: "catalog-specialist",
      environment: "test",
      channel: "",
      useCase: ""
    }, "channel")).toEqual([]);
  });
});
