import type {
  AgentFilterDimension,
  AgentFilterSelection
} from "./agent-filters";
import type {
  AgentFilterAssignment,
  AgentFilterOptions,
  AgentRegistryAgent,
  AgentRegistryVersion,
  AgentVersionDraftInput
} from "./api";

export function versionsForAgent(
  agents: AgentRegistryAgent[] | null,
  agentId: string
): AgentRegistryVersion[] {
  return agents?.find((agent) => agent.agentId === agentId)?.versions ?? [];
}

export function draftFromVersion(
  version: AgentRegistryVersion,
  versions: AgentRegistryVersion[] = [version]
): AgentVersionDraftInput {
  const latestSemanticVersion = versions.reduce((latest, candidate) =>
    compareSemanticVersions(candidate.semanticVersion, latest) > 0 ? candidate.semanticVersion : latest,
  version.semanticVersion);
  return {
    version: null,
    semanticVersion: nextPatch(latestSemanticVersion),
    name: version.name,
    purpose: version.purpose,
    modelProvider: version.modelProvider,
    modelId: version.modelId,
    temperature: version.temperature,
    topP: version.topP,
    systemPromptVersion: version.systemPromptVersion,
    systemPromptHash: version.systemPromptHash,
    inputSchemaVersion: version.inputSchemaVersion,
    outputSchemaVersion: version.outputSchemaVersion,
    allowedTools: [...version.allowedTools],
    knowledgeSources: [...version.knowledgeSources],
    memoryPolicy: version.memoryPolicy,
    responsePolicy: version.responsePolicy,
    timeoutMs: version.timeoutMs,
    maxSteps: version.maxSteps,
    maxInputTokens: version.maxInputTokens,
    maxOutputTokens: version.maxOutputTokens,
    budgetLimitUsd: version.budgetLimitUsd,
    fallbackAgentId: version.fallbackAgentId,
    evaluationSuiteVersion: version.evaluationSuiteVersion,
    invocationConfiguration: structuredClone(version.invocationConfiguration)
  };
}

function compareSemanticVersions(left: string, right: string): number {
  const leftParts = left.split(".").map(Number);
  const rightParts = right.split(".").map(Number);
  for (let index = 0; index < 3; index += 1) {
    const difference = (leftParts[index] ?? 0) - (rightParts[index] ?? 0);
    if (difference !== 0) return difference;
  }
  return 0;
}

function nextPatch(version: string): string {
  const match = /^(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)$/.exec(version);
  if (!match) return "1.0.0";
  return `${match[1]}.${match[2]}.${Number(match[3]) + 1}`;
}

export function assignmentValuesFor(
  options: AgentFilterOptions,
  selection: AgentFilterSelection,
  dimension: AgentFilterDimension
): string[] {
  const assignments = options.assignments.filter((assignment) =>
    (dimension === "agentId" || !selection.agentId || assignment.agentId === selection.agentId)
    && (dimension === "environment" || !selection.environment || assignment.environment === selection.environment)
    && (dimension === "channel" || !selection.channel || assignment.channel === selection.channel)
    && (dimension === "useCase" || !selection.useCase || assignment.useCase === selection.useCase));
  const values = assignments.map((assignment) => assignmentValue(assignment, dimension));
  return Array.from(new Set(values.filter(Boolean))).sort((left, right) => left.localeCompare(right));
}

function assignmentValue(assignment: AgentFilterAssignment, dimension: AgentFilterDimension): string {
  return dimension === "agentId"
    ? assignment.agentId
    : dimension === "environment"
      ? assignment.environment
      : dimension === "channel"
        ? assignment.channel
        : assignment.useCase;
}
