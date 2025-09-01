import React from 'react';

export interface ToolExecution {
  serverName: string;
  toolName: string;
  status: 'in_progress' | 'completed';
  agenticSearchLogs?: AgenticSearchLog[];
}

export interface AgenticSearchLog {
  iteration: number;
  query: string;
  reasoning: string;
  citations: string[];
  remaining_iterations: number;
}

export interface ContentBlock {
  type: 'text' | 'tool_progress' | 'inline_loading';
  content?: string;
  toolExecutions?: ToolExecution[];
}

export interface Message {
  id: string;
  role: 'user' | 'assistant';
  content: string;
  contentBlocks?: ContentBlock[];
  type: 'text' | 'image';
  timestamp: Date;
  hasThinkTags?: boolean;
  isLoading?: boolean;
  isStreaming?: boolean;
}

export interface PromptMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
}

export interface Tool {
  id: string;
  name: string;
  icon: React.ComponentType<{ className?: string }>;
  functionDefinition?: string; // For function tools
  mcpConfig?: any; // For MCP server tools
  fileSearchConfig?: { selectedFiles: string[]; selectedVectorStores: string[]; vectorStoreNames: string[] }; // For file search tools
  agenticFileSearchConfig?: { selectedFiles: string[]; selectedVectorStores: string[]; vectorStoreNames: string[]; iterations: number; maxResults: number }; // For agentic file search tools
  pyFunctionConfig?: any; // For Py function tools
}

export type TextFormat = 'text' | 'json_object' | 'json_schema';

export interface UseResponsesChatConfig {
  model: { provider: string; name: string };
  instructions?: string;
  textFormat: TextFormat;
  jsonSchema?: { name?: string; schema?: object };
  tools?: any[];
  buildToolsPayload?: (ctx: { modelSettings?: any }) => any[];
  store?: boolean; // default: true
  stream?: boolean; // default: true
  headers?: Record<string, string>; // optional override; apiClient provides defaults
  onEvent?: (evt: { type: string; data: any }) => void; // optional streaming hooks
  // Model test mode support
  modelTestMode?: boolean;
  onSaveModelStateChange?: (state: 'success' | 'tool_issue' | 'error' | null) => void;
  onShowSaveModelChange?: (show: boolean) => void;
  // Custom endpoint and request transformation support
  customEndpoint?: string; // e.g., '/agents/agent-builder/chat'
  requestTransformer?: (standardRequest: any, context?: any) => any;
  customContext?: any; // Additional context for request transformation
}

export interface UseResponsesChatApi {
  messages: Message[];
  isLoading: boolean;
  previousResponseId: string | null;
  conversationId: string | null;
  lastRequest: any | null; // PlaygroundRequest type
  sendTextMessage: (text: string) => Promise<void>;
  resetConversation: () => void;
  retry: (messageId?: string) => Promise<void>;
  streamGreetingMessage: (greetingText: string) => void;
  streamUserMessage: (userText: string, onComplete?: () => void) => void;
  addAssistantMessage: (message: Message) => void;
  updateMessage: (messageId: string, updates: Partial<Message>) => void;
}
