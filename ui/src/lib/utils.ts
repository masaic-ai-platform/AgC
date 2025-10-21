import { clsx, type ClassValue } from "clsx"
import { twMerge } from "tailwind-merge"

export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs))
}

// Utility function to check if an agent has client-side tools
export function hasClientSideTools(agent: any): boolean {
  if (!agent?.tools || !Array.isArray(agent.tools)) {
    return false;
  }
  
  return agent.tools.some((tool: any) => {
    // Check if tool has type=function and execution_specs with client_side
    // This matches the structure used in transformAgentToolsToUI
    return tool.type === 'function' && 
           tool.parameters?.properties?.execution_specs?.properties?.type?.enum?.[0] === 'client_side';
  });
}
