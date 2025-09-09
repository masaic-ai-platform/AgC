// Main chat components
export { default as ResponsesChat } from './ResponsesChat';
export { default as ChatThread } from './ChatThread';
export { default as ChatComposer } from './ChatComposer';
export type { ResponsesChatRef } from './ResponsesChat';

// Hook
export { useResponsesChat } from './useResponsesChat';

// Utilities
export { postResponses } from './responsesApi';
export { buildTextFormat, buildToolsPayload, buildInputFromText, buildRequestBody } from './buildRequest';
export { parseResponsesStream, createInitialStreamingState } from './streaming';

// Types
export type {
  Message,
  ContentBlock,
  ToolExecution,
  AgenticSearchLog,
  Tool,
  PromptMessage,
  TextFormat,
  UseResponsesChatConfig,
  UseResponsesChatApi
} from './types';

export type { ResponsesRequestBody, ResponsesApiOptions } from './responsesApi';
export type { ModelSettings, TextFormatConfig } from './buildRequest';
export type { StreamingEvent, StreamingState } from './streaming';
