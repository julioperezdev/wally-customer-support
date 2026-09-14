import type { AgentFilterOptions, AgentRegistryAgent } from "./api";

export type AgentFilterDimension = "agentId" | "environment" | "channel" | "useCase";
export type AgentFilterSelection = Record<AgentFilterDimension, string>;

export function deriveAgentFilterOptions(agents: AgentRegistryAgent[] | null): AgentFilterOptions {
  const versions = agents?.flatMap((agent) => agent.versions.map(() => agent.agentId)) ?? [];
  const assignments = agents?.flatMap((agent) => agent.activations.map((activation) => ({
    agentId: agent.agentId,
    environment: activation.environment,
    channel: activation.channel,
    useCase: activation.useCase
  }))) ?? [];
  const uniqueAssignments = Array.from(
    new Map(assignments.map((assignment) => [
      `${assignment.agentId}|${assignment.environment}|${assignment.channel}|${assignment.useCase}`,
      assignment
    ])).values()
  );
  return {
    agentIds: uniqueSorted(versions),
    environments: uniqueSorted(uniqueAssignments.map((assignment) => assignment.environment)),
    channels: uniqueSorted(uniqueAssignments.map((assignment) => assignment.channel)),
    useCases: uniqueSorted(uniqueAssignments.map((assignment) => assignment.useCase)),
    assignments: uniqueAssignments
  };
}

export function filterValuesFor(
  options: AgentFilterOptions,
  selection: AgentFilterSelection,
  dimension: AgentFilterDimension
): string[] {
  const assignments = options.assignments.filter((assignment) =>
    (dimension === "agentId" || !selection.agentId || assignment.agentId === selection.agentId)
    && (dimension === "environment" || !selection.environment || assignment.environment === selection.environment)
    && (dimension === "channel" || !selection.channel || assignment.channel === selection.channel)
    && (dimension === "useCase" || !selection.useCase || assignment.useCase === selection.useCase));
  if (dimension === "agentId" && !selection.environment && !selection.channel && !selection.useCase) {
    return options.agentIds;
  }
  if (dimension === "agentId") return uniqueSorted(assignments.map((assignment) => assignment.agentId));
  if (dimension === "environment") return uniqueSorted(assignments.map((assignment) => assignment.environment));
  if (dimension === "channel") return uniqueSorted(assignments.map((assignment) => assignment.channel));
  return uniqueSorted(assignments.map((assignment) => assignment.useCase));
}

function uniqueSorted(values: string[]): string[] {
  return Array.from(new Set(values.filter(Boolean))).sort((left, right) => left.localeCompare(right));
}
