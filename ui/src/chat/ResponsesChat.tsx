import React, { useEffect, useImperativeHandle, forwardRef } from 'react';
import { UseResponsesChatConfig, Message } from './types';
import { useResponsesChat } from './useResponsesChat';
import ChatThread from './ChatThread';
import ChatComposer from './ChatComposer';

interface ResponsesChatProps {
  hookConfig: UseResponsesChatConfig;
  className?: string;
  placeholder?: string;
  // ChatMessage props
  apiKey?: string;
  baseUrl?: string;
  imageModelProvider?: string;
  imageModelName?: string;
  imageProviderKey?: string;
  selectedVectorStore?: string;
  // Suggested queries support
  suggestedQueries?: string[];
  agentMode?: boolean;
  previousResponseId?: string | null;
  // State sync callbacks
  onPreviousResponseIdChange?: (id: string | null) => void;
  onLastRequestChange?: (request: any | null) => void;
  // Model test mode support - EXACT COPY from old implementation
  modelTestMode?: boolean;
  showSaveModel?: boolean;
  saveModelState?: 'success' | 'tool_issue' | 'error' | null;
  onSaveModel?: () => void;
}

export interface ResponsesChatRef {
  resetConversation: () => void;
  streamGreetingMessage: (greetingText: string) => void;
  streamUserMessage: (userText: string, onComplete?: () => void) => void;
  addAssistantMessage: (message: Message) => void;
  updateMessage: (messageId: string, updates: Partial<Message>) => void;
  sendTextMessage: (text: string) => Promise<void>;
}

const ResponsesChat = forwardRef<ResponsesChatRef, ResponsesChatProps>(({
  hookConfig,
  className = '',
  placeholder = 'Chat with your prompt...',
  apiKey = '',
  baseUrl = '',
  imageModelProvider = '',
  imageModelName = '',
  imageProviderKey = '',
  selectedVectorStore = '',
  suggestedQueries = [],
  agentMode = false,
  previousResponseId = null,
  onPreviousResponseIdChange,
  onLastRequestChange,
  // Model test mode props
  modelTestMode = false,
  showSaveModel = false,
  saveModelState = null,
  onSaveModel
}, ref) => {
  const {
    messages,
    isLoading,
    previousResponseId: hookPreviousResponseId,
    lastRequest,
    sendTextMessage,
    resetConversation,
    retry,
    streamGreetingMessage,
    streamUserMessage,
    addAssistantMessage,
    updateMessage
  } = useResponsesChat(hookConfig);

  // Sync state with parent component
  useEffect(() => {
    if (onPreviousResponseIdChange && hookPreviousResponseId !== previousResponseId) {
      onPreviousResponseIdChange(hookPreviousResponseId);
    }
  }, [hookPreviousResponseId, onPreviousResponseIdChange, previousResponseId]);

  useEffect(() => {
    if (onLastRequestChange) {
      onLastRequestChange(lastRequest);
    }
  }, [lastRequest, onLastRequestChange]);

  // Expose functions to parent via ref
  useImperativeHandle(ref, () => ({
    resetConversation,
    streamGreetingMessage,
    streamUserMessage,
    addAssistantMessage,
    updateMessage,
    sendTextMessage
  }), [resetConversation, streamGreetingMessage, streamUserMessage, addAssistantMessage, updateMessage, sendTextMessage]);

  // EXACT COPY: Suggested query handling from old implementation
  const handleSuggestedQuerySelect = (query: string) => {
    sendTextMessage(query);
  };

  return (
    // EXACT COPY: Fragment structure like old implementation - NO wrapper div
    <>
      {/* EXACT COPY: Messages Area */}
      <ChatThread
        messages={messages}
        onRetry={retry}
        textFormat={hookConfig.textFormat}
        apiKey={apiKey}
        baseUrl={baseUrl}
        modelProvider={hookConfig.model.provider}
        modelName={hookConfig.model.name}
        imageModelProvider={imageModelProvider}
        imageModelName={imageModelName}
        imageProviderKey={imageProviderKey}
        selectedVectorStore={selectedVectorStore}
        instructions={hookConfig.instructions}
      />
      
      {/* EXACT COPY: Input Area */}
      <ChatComposer
        onSendMessage={sendTextMessage}
        isLoading={isLoading}
        placeholder={placeholder}
        suggestedQueries={suggestedQueries}
        onSuggestedQuerySelect={handleSuggestedQuerySelect}
        showSuggested={suggestedQueries.length > 0 && agentMode && !previousResponseId}
        // Model test mode support
        modelTestMode={modelTestMode}
        showSaveModel={showSaveModel}
        saveModelState={saveModelState}
        onSaveModel={onSaveModel}
      />
    </>
  );
});

ResponsesChat.displayName = 'ResponsesChat';

export default ResponsesChat;
