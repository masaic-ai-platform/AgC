import { useState, useRef, useCallback } from 'react';
import { UseResponsesChatConfig, UseResponsesChatApi, Message, ContentBlock, ToolExecution } from './types';
import { apiClient } from '@/lib/api';
import { PlaygroundRequest } from '@/playground/PlaygroundRequest';

const generateSessionId = (): string => {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
  return Array.from({length: 12}, () => chars.charAt(Math.floor(Math.random() * chars.length))).join('');
};

const getOrCreateSessionId = (): string => {
  let sessionId = localStorage.getItem('platform_sessionId');
  if (!sessionId) {
    sessionId = generateSessionId();
    localStorage.setItem('platform_sessionId', sessionId);
    console.log('Generated new sessionId:', sessionId);
  }
  return sessionId;
};

export function useResponsesChat(config: UseResponsesChatConfig): UseResponsesChatApi {
  const [messages, setMessages] = useState<Message[]>([]);
  const [isLoading, setIsLoading] = useState(false);
  const [previousResponseId, setPreviousResponseId] = useState<string | null>(null);
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [lastRequest, setLastRequest] = useState<PlaygroundRequest | null>(null);
  
  // Keep track of current streaming operation
  const abortControllerRef = useRef<AbortController | null>(null);

  const validateConfig = useCallback(() => {
    if (!config.model?.provider || !config.model?.name) {
      throw new Error('Model provider and name are required');
    }
    
    if (config.textFormat === 'json_schema') {
      if (!config.jsonSchema?.name || !config.jsonSchema?.schema) {
        throw new Error('JSON Schema format requires both name and schema');
      }
    }
  }, [config]);

    // EXACT COPY OF generateResponse from AiPlayground - just adapted for hook
  const sendTextMessage = useCallback(async (text: string): Promise<void> => {
    if (isLoading) {
      console.warn('Already loading, ignoring send request');
      return;
    }

    if (!text.trim()) {
      console.warn('Empty message, ignoring send request');
      return;
    }

    // Provider and model validation from old implementation
    const provider = config.model.provider;
    const modelName = config.model.name;
    
    if (!provider || provider.trim() === '') {
      console.error('Please select a model provider before sending a message.');
      return;
    }
    
    if (!modelName || modelName.trim() === '') {
      console.error('Please select a model before sending a message.');
      return;
    }

    setIsLoading(true);

    // Add user message - EXACT COPY
    const userMessage: Message = {
      id: Date.now().toString() + '_user',
      role: 'user',
      content: text,
      type: 'text',
      timestamp: new Date()
    };
    setMessages(prev => [...prev, userMessage]);

    // Add assistant message placeholder - EXACT COPY
    const assistantMessageId = Date.now().toString() + '_assistant';
    const assistantMessage: Message = {
      id: assistantMessageId,
      role: 'assistant',
      content: '',
      type: 'text',
      timestamp: new Date(),
      isLoading: true
    };
    setMessages(prev => [...prev, assistantMessage]);

    // Build API request - EXACT COPY from old implementation
    const input = [
      {
        role: 'user',
        content: [
          {
            type: 'input_text',
            text: text
          }
        ]
      }
    ];

    let textFormatBlock: any = { type: config.textFormat };
    if (config.textFormat === 'json_schema') {
      let schema = null;
      let schemaName = config.jsonSchema?.name;
      try {
        if (config.jsonSchema?.schema) {
          schema = config.jsonSchema.schema;
          schemaName = config.jsonSchema.name || schemaName;
        }
      } catch {}
      if (!schema || !schemaName) {
        const errorMsg = 'JSON schema is missing or invalid. Please define a valid schema in settings.';
        setMessages(prev => prev.map(msg =>
          msg.id === assistantMessageId
            ? {
                ...msg,
                content: errorMsg,
                type: 'text',
                hasThinkTags: false,
                isLoading: false
              }
            : msg
        ));
        setIsLoading(false);
        return;
      }
      textFormatBlock = {
        type: 'json_schema',
        name: schemaName,
        schema
      };
    }

    const requestBody: any = {
      model: `${provider}@${modelName}`,
      instructions: config.instructions,
      input,
      text: {
        format: textFormatBlock
      },
      temperature: 1.0,
      max_output_tokens: 2048,
      top_p: 1.0,
      store: true,
      stream: true
    };
    
    // Tools handling - simplified for now
    if (config.tools && config.tools.length > 0) {
      requestBody.tools = config.tools;
    }
    
    if (previousResponseId) {
      requestBody.previous_response_id = previousResponseId;
    }

    // Determine endpoint and transform request if needed
    const endpoint = config.customEndpoint || '/v1/responses';
    const finalRequestBody = config.requestTransformer 
      ? config.requestTransformer(requestBody, config.customContext)
      : requestBody;

    // EXACT COPY: Capture request for code snippet generation from old implementation
    // Note: For custom endpoints, we store a compatible request object
    const playgroundRequest: any = {
      method: 'POST',
      url: endpoint,
      headers: {
        'Content-Type': 'application/json',
        ...(config.headers || {})
      },
      body: finalRequestBody
    };
    setLastRequest(playgroundRequest);

    try {
      // Make API call - using existing apiClient with configurable endpoint
      const response = await apiClient.rawRequest(endpoint, {
        method: 'POST',
        headers: config.headers || {},
        body: JSON.stringify(finalRequestBody)
      });

      if (!response.ok) {
        const errorText = await response.text();
        setMessages(prev => prev.map(msg =>
          msg.id === assistantMessageId
            ? {
                ...msg,
                content: `Error: ${errorText}`,
                type: 'text',
                hasThinkTags: false,
                isLoading: false
              }
            : msg
        ));
        setIsLoading(false);
        return;
      }

      // EXACT COPY of streaming logic from old implementation
      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      let buffer = '';
      let streamingContent = '';
      let contentBlocks: ContentBlock[] = [];
      let responseId: string | null = null;
      let activeToolExecutions = new Map<string, ToolExecution>();
      let isStreaming = false;
      let currentTextBlock: ContentBlock | null = null;
      let encounteredStreamError = false;
      let lastEvent: string | null = null;
      
      // EXACT COPY: Model test mode tracking from old implementation
      let responseCompleted = false;
      let toolCompleted = false;

      const updateMessage = (blocks: ContentBlock[], fullContent: string, streaming: boolean = false) => {
        setMessages(prev => prev.map(msg =>
          msg.id === assistantMessageId
            ? {
                ...msg,
                content: fullContent,
                contentBlocks: [...blocks],
                type: 'text',
                hasThinkTags: false,
                isLoading: false,
                isStreaming: streaming
              }
            : msg
        ));
      };

      const addInlineLoading = (blocks: ContentBlock[]) => {
        const blocksWithoutLoading = blocks.filter(block => block.type !== 'inline_loading');
        return [...blocksWithoutLoading, { type: 'inline_loading' as const }];
      };

      const removeInlineLoading = (blocks: ContentBlock[]) => {
        return blocks.filter(block => block.type !== 'inline_loading');
      };

      if (reader) {
        try {
          while (true) {
            const { done, value } = await reader.read();
            if (done) break;

            buffer += decoder.decode(value, { stream: true });
            const lines = buffer.split('\n');
            buffer = lines.pop() || '';

            for (const line of lines) {
              // Capture event name if present
              if (line.startsWith('event: ')) {
                lastEvent = line.slice(7).trim();
                continue;
              }
              if (line.startsWith('data: ')) {
                try {
                  const data = JSON.parse(line.slice(6));

                  // Emit event if handler provided (like in streaming.ts)
                  if (config.onEvent) {
                    config.onEvent({ type: lastEvent || data.type || 'data', data });
                  }

                  // Handle explicit error events from SSE stream  
                  if (lastEvent === 'error' || (data.type === 'error') || (data.code && data.message)) {
                    console.log('🚨 Error detected in stream!', { lastEvent, dataType: data.type, dataCode: data.code, dataMessage: data.message });
                    const errorCode = data.code || 'error';
                    const errorMsg = data.message || 'Unknown error';
                    const errorContent = `Error: [${errorCode}] ${errorMsg}`;

                    activeToolExecutions.clear();
                    encounteredStreamError = true;
                    
                    const errorContentBlocks = contentBlocks.filter(block => 
                      block.type !== 'inline_loading' && block.type !== 'tool_progress'
                    );
                    
                    errorContentBlocks.push({
                      type: 'text',
                      content: `[${errorCode}] ${errorMsg}`
                    });

                    setMessages(prev => prev.map(msg =>
                      msg.id === assistantMessageId
                        ? {
                            ...msg,
                            content: errorContent,
                            contentBlocks: errorContentBlocks,
                            type: 'text',
                            hasThinkTags: false,
                            isLoading: false,
                            isStreaming: false
                          }
                        : msg
                    ));

                    setIsLoading(false);
                    reader.cancel().catch(() => {});
                    break;
                  }

                  // Handle different event types - EXACT COPY
                  if (data.type === 'response.completed') {
                    if (data.response?.id) {
                      responseId = data.response.id;
                      setPreviousResponseId(responseId);
                    }
                    
                    // EXACT COPY: Model test mode response completion tracking
                    responseCompleted = true;
                    
                    activeToolExecutions.forEach((execution, key) => {
                      if (execution.status === 'in_progress') {
                        execution.status = 'completed';
                      }
                    });
                    
                    contentBlocks.forEach(block => {
                      if (block.type === 'tool_progress' && block.toolExecutions) {
                        block.toolExecutions = Array.from(activeToolExecutions.values());
                      }
                    });
                    
                    const blocksWithoutLoading = removeInlineLoading(contentBlocks);
                    let finalBlocks = blocksWithoutLoading;
                    if (finalBlocks.length === 0) {
                      finalBlocks = [{ type: 'text' as const, content: 'Response completed' }];
                    }
                    
                    const finalContent = streamingContent || 'Response completed';
                    updateMessage(finalBlocks, finalContent, false);
                    
                    // EXACT COPY: Model test mode save model state logic from old implementation
                    if (config.modelTestMode) {
                      if (toolCompleted) {
                        // Both response and tool completed successfully
                        config.onSaveModelStateChange?.('success');
                      } else {
                        // Response completed but no tool completion - tool calling issue
                        config.onSaveModelStateChange?.('tool_issue');
                      }
                      
                      // Response completed - stop testing and show save button
                      config.onShowSaveModelChange?.(true);
                    }

                  } else if (data.type === 'response.output_text.delta') {
                    // Start or continue streaming
                    if (!isStreaming) {
                      isStreaming = true;
                    }
                    
                    if (data.delta) {
                      streamingContent += data.delta;
                      
                      // Create or update current text block
                      if (!currentTextBlock) {
                        currentTextBlock = { type: 'text', content: data.delta };
                        contentBlocks.push(currentTextBlock);
                      } else {
                        currentTextBlock.content = (currentTextBlock.content || '') + data.delta;
                      }
                      
                      // Check if we have a complete JSON object for real-time formatting
                      let displayContent = streamingContent;
                      if (config.textFormat === 'json_object' || config.textFormat === 'json_schema') {
                        try {
                          JSON.parse(streamingContent);
                          displayContent = streamingContent;
                        } catch {
                          displayContent = streamingContent;
                        }
                      }
                      
                                          // Remove any inline loading when text streaming starts
                    const blocksWithoutLoading = removeInlineLoading(contentBlocks);
                    updateMessage(blocksWithoutLoading, displayContent, true);
                    }

                  } else if (data.type === 'response.output_text.done') {
                    // Streaming completed for this output
                    isStreaming = false;
                    currentTextBlock = null;
                    if (data.text) {
                      streamingContent = data.text;
                      // Update the last text block with complete content
                      for (let i = contentBlocks.length - 1; i >= 0; i--) {
                        if (contentBlocks[i].type === 'text') {
                          contentBlocks[i].content = data.text;
                          break;
                        }
                      }
                      updateMessage(contentBlocks, streamingContent, false);
                    }

                  } else if (data.type && data.type.startsWith('response.mcp_call.')) {
                    // Handle MCP tool events - EXACT COPY
                    const typeParts = data.type.split('.');
                    if (typeParts.length >= 4) {
                      const toolIdentifier = typeParts[2];
                      const status = typeParts[3];
                      
                      const identifierParts = toolIdentifier.split('_');
                      const serverName = identifierParts[0];
                      const toolName = identifierParts.slice(1).join('_');
                      
                      if (status === 'in_progress') {
                        const toolExecution: ToolExecution = {
                          serverName,
                          toolName,
                          status: 'in_progress'
                        };
                        activeToolExecutions.set(toolIdentifier, toolExecution);
                        
                        let toolProgressBlock = contentBlocks.find(block => block.type === 'tool_progress');
                        if (!toolProgressBlock) {
                          toolProgressBlock = {
                            type: 'tool_progress',
                            toolExecutions: Array.from(activeToolExecutions.values())
                          };
                          contentBlocks.push(toolProgressBlock);
                        } else {
                          toolProgressBlock.toolExecutions = Array.from(activeToolExecutions.values());
                        }
                        currentTextBlock = null;
                        
                        updateMessage(contentBlocks, streamingContent, false);
                      } else if (status === 'completed') {
                        const toolExecution = activeToolExecutions.get(toolIdentifier);
                        if (toolExecution) {
                          toolExecution.status = 'completed';
                          
                          for (let i = contentBlocks.length - 1; i >= 0; i--) {
                            if (contentBlocks[i].type === 'tool_progress') {
                              contentBlocks[i].toolExecutions = Array.from(activeToolExecutions.values());
                              break;
                            }
                          }
                          
                          const blocksWithLoading = addInlineLoading(contentBlocks);
                          contentBlocks = blocksWithLoading;
                          updateMessage(blocksWithLoading, streamingContent, false);
                        }
                      }
                    }

                  } else if (data.type && data.type.startsWith('response.agc.')) {
                    // EXACT COPY: Handle agc.* tool events (Py function tools) from old implementation
                    const typeParts = data.type.split('.');
                    if (typeParts.length >= 4) {
                      const toolName = typeParts[2];
                      const status = typeParts[3];
                      
                      if (status === 'executing' || status === 'in_progress') {
                        // Add new agc tool execution
                        const toolExecution: ToolExecution = {
                          serverName: 'agc',
                          toolName,
                          status: 'in_progress'
                        };
                        activeToolExecutions.set(`agc_${toolName}`, toolExecution);
                        
                        // Find existing tool progress block or create new one
                        let toolProgressBlock = contentBlocks.find(block => block.type === 'tool_progress');
                        if (!toolProgressBlock) {
                          toolProgressBlock = {
                            type: 'tool_progress',
                            toolExecutions: Array.from(activeToolExecutions.values())
                          };
                          contentBlocks.push(toolProgressBlock);
                        } else {
                          // Update existing tool progress block
                          toolProgressBlock.toolExecutions = Array.from(activeToolExecutions.values());
                        }
                        currentTextBlock = null; // Reset text block for potential next text
                        
                        updateMessage(contentBlocks, streamingContent, false);
                      } else if (status === 'completed') {
                        // Update agc tool execution status
                        const toolExecution = activeToolExecutions.get(`agc_${toolName}`);
                        if (toolExecution) {
                          toolExecution.status = 'completed';
                          
                          // Update the last tool progress block
                          for (let i = contentBlocks.length - 1; i >= 0; i--) {
                            if (contentBlocks[i].type === 'tool_progress') {
                              contentBlocks[i].toolExecutions = Array.from(activeToolExecutions.values());
                              break;
                            }
                          }
                          
                          // Add inline loading when tools complete, indicating we're waiting for next text stream
                          const blocksWithLoading = addInlineLoading(contentBlocks);
                          contentBlocks = blocksWithLoading;
                          updateMessage(blocksWithLoading, streamingContent, false);
                        }
                      }
                    }

                  } else if (data.type === 'response.file_search.in_progress') {
                    // Handle file search tool start event - EXACT COPY
                    const toolExecution: ToolExecution = {
                      serverName: 'file_search',
                      toolName: 'search',
                      status: 'in_progress'
                    };
                    activeToolExecutions.set('file_search', toolExecution);
                    
                    let toolProgressBlock = contentBlocks.find(block => block.type === 'tool_progress');
                    if (!toolProgressBlock) {
                      toolProgressBlock = {
                        type: 'tool_progress',
                        toolExecutions: Array.from(activeToolExecutions.values())
                      };
                      contentBlocks.push(toolProgressBlock);
                    } else {
                      toolProgressBlock.toolExecutions = Array.from(activeToolExecutions.values());
                    }
                    currentTextBlock = null;
                    
                    updateMessage(contentBlocks, streamingContent);

                  } else if (data.type === 'response.file_search.completed') {
                    // Handle file search tool completion event - EXACT COPY
                    const toolExecution = activeToolExecutions.get('file_search');
                    if (toolExecution) {
                      toolExecution.status = 'completed';
                      
                      for (let i = contentBlocks.length - 1; i >= 0; i--) {
                        if (contentBlocks[i].type === 'tool_progress') {
                          contentBlocks[i].toolExecutions = Array.from(activeToolExecutions.values());
                          break;
                        }
                      }
                      
                      const blocksWithLoading = addInlineLoading(contentBlocks);
                      contentBlocks = blocksWithLoading;
                      updateMessage(blocksWithLoading, streamingContent);
                    }

                  } else if (data.type === 'response.get_weather_by_city.in_progress') {
                    // EXACT COPY: Handle get_weather_by_city tool in_progress from old implementation
                    const toolExecution: ToolExecution = {
                      serverName: 'get_weather_by_city',
                      toolName: 'get_weather_by_city',
                      status: 'in_progress'
                    };
                    activeToolExecutions.set('get_weather_by_city', toolExecution);
                    
                    // Find or create tool progress block
                    let toolProgressBlock = contentBlocks.find(block => block.type === 'tool_progress');
                    if (!toolProgressBlock) {
                      toolProgressBlock = { type: 'tool_progress', toolExecutions: Array.from(activeToolExecutions.values()) };
                      contentBlocks.push(toolProgressBlock);
                    } else {
                      toolProgressBlock.toolExecutions = Array.from(activeToolExecutions.values());
                    }
                    currentTextBlock = null;
                    updateMessage(contentBlocks, streamingContent);

                  } else if (data.type === 'response.get_weather_by_city.completed') {
                    // EXACT COPY: Handle get_weather_by_city tool completed from old implementation
                    const toolExecution = activeToolExecutions.get('get_weather_by_city');
                    if (toolExecution) {
                      toolExecution.status = 'completed';
                      toolCompleted = true;
                      
                      // If response already completed, update save model state to success
                      if (responseCompleted && config.modelTestMode) {
                        config.onSaveModelStateChange?.('success');
                      }
                      
                      // Update the tool progress block
                      for (let i = contentBlocks.length - 1; i >= 0; i--) {
                        if (contentBlocks[i].type === 'tool_progress') {
                          contentBlocks[i].toolExecutions = Array.from(activeToolExecutions.values());
                          break;
                        }
                      }
                      
                      const blocksWithLoading = addInlineLoading(contentBlocks);
                      contentBlocks = blocksWithLoading;
                      updateMessage(blocksWithLoading, streamingContent);
                    }

                  } else if (data.type === 'response.agentic_search.in_progress') {
                    // EXACT COPY: Handle agentic search tool start event from old implementation
                    const toolExecution: ToolExecution = {
                      serverName: 'agentic_search',
                      toolName: 'search',
                      status: 'in_progress'
                    };
                    activeToolExecutions.set('agentic_search', toolExecution);
                    
                    let toolProgressBlock = contentBlocks.find(block => block.type === 'tool_progress');
                    if (!toolProgressBlock) {
                      toolProgressBlock = {
                        type: 'tool_progress',
                        toolExecutions: Array.from(activeToolExecutions.values())
                      };
                      contentBlocks.push(toolProgressBlock);
                    } else {
                      toolProgressBlock.toolExecutions = Array.from(activeToolExecutions.values());
                    }
                    currentTextBlock = null;
                    updateMessage(contentBlocks, streamingContent);

                  } else if (data.type === 'response.agentic_search.completed') {
                    // EXACT COPY: Handle agentic search tool completion event from old implementation
                    const toolExecution = activeToolExecutions.get('agentic_search');
                    if (toolExecution) {
                      toolExecution.status = 'completed';
                      
                      for (let i = contentBlocks.length - 1; i >= 0; i--) {
                        if (contentBlocks[i].type === 'tool_progress') {
                          contentBlocks[i].toolExecutions = Array.from(activeToolExecutions.values());
                          break;
                        }
                      }
                      
                      const blocksWithLoading = addInlineLoading(contentBlocks);
                      contentBlocks = blocksWithLoading;
                      updateMessage(blocksWithLoading, streamingContent);
                    }

                  } else if (data.type === 'response.fun_req_gathering_tool.in_progress') {
                    // EXACT COPY: Handle fun req assembler start from old implementation
                    const toolExecution: ToolExecution = {
                      serverName: 'fun_req_gathering_tool',
                      toolName: 'assemble',
                      status: 'in_progress'
                    };
                    activeToolExecutions.set('fun_req_gathering_tool', toolExecution);
                    
                    let toolProgressBlock = contentBlocks.find(block => block.type === 'tool_progress');
                    if (!toolProgressBlock) {
                      toolProgressBlock = { type: 'tool_progress', toolExecutions: Array.from(activeToolExecutions.values()) };
                      contentBlocks.push(toolProgressBlock);
                    } else {
                      toolProgressBlock.toolExecutions = Array.from(activeToolExecutions.values());
                    }
                    currentTextBlock = null;
                    updateMessage(contentBlocks, streamingContent);

                  } else if (data.type === 'response.fun_req_gathering_tool.completed') {
                    // EXACT COPY: Handle fun req assembler completion from old implementation
                    const toolExecution = activeToolExecutions.get('fun_req_gathering_tool');
                    if (toolExecution) {
                      toolExecution.status = 'completed';
                      for (let i = contentBlocks.length - 1; i >= 0; i--) {
                        if (contentBlocks[i].type === 'tool_progress') {
                          contentBlocks[i].toolExecutions = Array.from(activeToolExecutions.values());
                          break;
                        }
                      }
                      const blocksWithLoading = addInlineLoading(contentBlocks);
                      contentBlocks = blocksWithLoading;
                      updateMessage(blocksWithLoading, streamingContent);
                    }

                  } else if (data.type === 'response.fun_def_generation_tool.in_progress') {
                    // EXACT COPY: Handle fun def generator start from old implementation
                    const toolExecution: ToolExecution = {
                      serverName: 'fun_def_generation_tool',
                      toolName: 'generate',
                      status: 'in_progress'
                    };
                    activeToolExecutions.set('fun_def_generation_tool', toolExecution);
                    
                    let toolProgressBlock = contentBlocks.find(block => block.type === 'tool_progress');
                    if (!toolProgressBlock) {
                      toolProgressBlock = { type: 'tool_progress', toolExecutions: Array.from(activeToolExecutions.values()) };
                      contentBlocks.push(toolProgressBlock);
                    } else {
                      toolProgressBlock.toolExecutions = Array.from(activeToolExecutions.values());
                    }
                    currentTextBlock = null;
                    updateMessage(contentBlocks, streamingContent);

                  } else if (data.type === 'response.fun_def_generation_tool.completed') {
                    // EXACT COPY: Handle fun def generator completion from old implementation
                    const toolExecution = activeToolExecutions.get('fun_def_generation_tool');
                    if (toolExecution) {
                      toolExecution.status = 'completed';
                      for (let i = contentBlocks.length - 1; i >= 0; i--) {
                        if (contentBlocks[i].type === 'tool_progress') {
                          contentBlocks[i].toolExecutions = Array.from(activeToolExecutions.values());
                          break;
                        }
                      }
                      const blocksWithLoading = addInlineLoading(contentBlocks);
                      updateMessage(blocksWithLoading, streamingContent);
                    }

                  } else if (data.type === 'response.mock_fun_save_tool.in_progress') {
                    // EXACT COPY: Handle mock fun save tool start from old implementation
                    const toolExecution: ToolExecution = {
                      serverName: 'mock_fun_save_tool',
                      toolName: 'save_function',
                      status: 'in_progress'
                    };
                    activeToolExecutions.set('mock_fun_save_tool', toolExecution);
                    
                    let toolProgressBlock = contentBlocks.find(block => block.type === 'tool_progress');
                    if (!toolProgressBlock) {
                      toolProgressBlock = { type: 'tool_progress', toolExecutions: Array.from(activeToolExecutions.values()) };
                      contentBlocks.push(toolProgressBlock);
                    } else {
                      toolProgressBlock.toolExecutions = Array.from(activeToolExecutions.values());
                    }
                    currentTextBlock = null;
                    updateMessage(contentBlocks, streamingContent);

                  } else if (data.type === 'response.mock_fun_save_tool.completed') {
                    // EXACT COPY: Handle mock fun save tool completion from old implementation
                    const toolExecution = activeToolExecutions.get('mock_fun_save_tool');
                    if (toolExecution) {
                      toolExecution.status = 'completed';
                      for (let i = contentBlocks.length - 1; i >= 0; i--) {
                        if (contentBlocks[i].type === 'tool_progress') {
                          contentBlocks[i].toolExecutions = Array.from(activeToolExecutions.values());
                          break;
                        }
                      }
                      const blocksWithLoading = addInlineLoading(contentBlocks);
                      contentBlocks = blocksWithLoading;
                      updateMessage(blocksWithLoading, streamingContent);
                    }

                  } else if (data.type === 'response.mock_generation_tool.in_progress') {
                    // EXACT COPY: Handle mock generation tool start from old implementation
                    const toolExecution: ToolExecution = {
                      serverName: 'mock_generation_tool',
                      toolName: 'generate',
                      status: 'in_progress'
                    };
                    activeToolExecutions.set('mock_generation_tool', toolExecution);
                    
                    let toolProgressBlock = contentBlocks.find(block => block.type === 'tool_progress');
                    if (!toolProgressBlock) {
                      toolProgressBlock = { type: 'tool_progress', toolExecutions: Array.from(activeToolExecutions.values()) };
                      contentBlocks.push(toolProgressBlock);
                    } else {
                      toolProgressBlock.toolExecutions = Array.from(activeToolExecutions.values());
                    }
                    currentTextBlock = null;
                    updateMessage(contentBlocks, streamingContent);

                  } else if (data.type === 'response.mock_generation_tool.completed') {
                    // EXACT COPY: Handle mock generation tool completion from old implementation
                    const toolExecution = activeToolExecutions.get('mock_generation_tool');
                    if (toolExecution) {
                      toolExecution.status = 'completed';
                      for (let i = contentBlocks.length - 1; i >= 0; i--) {
                        if (contentBlocks[i].type === 'tool_progress') {
                          contentBlocks[i].toolExecutions = Array.from(activeToolExecutions.values());
                          break;
                        }
                      }
                      const blocksWithLoading = addInlineLoading(contentBlocks);
                      contentBlocks = blocksWithLoading;
                      updateMessage(blocksWithLoading, streamingContent);
                    }

                  } else if (data.type === 'response.mock_save_tool.in_progress') {
                    // EXACT COPY: Handle mock save tool start from old implementation
                    const toolExecution: ToolExecution = {
                      serverName: 'mock_save_tool',
                      toolName: 'save',
                      status: 'in_progress'
                    };
                    activeToolExecutions.set('mock_save_tool', toolExecution);
                    
                    let toolProgressBlock = contentBlocks.find(block => block.type === 'tool_progress');
                    if (!toolProgressBlock) {
                      toolProgressBlock = { type: 'tool_progress', toolExecutions: Array.from(activeToolExecutions.values()) };
                      contentBlocks.push(toolProgressBlock);
                    } else {
                      toolProgressBlock.toolExecutions = Array.from(activeToolExecutions.values());
                    }
                    currentTextBlock = null;
                    updateMessage(contentBlocks, streamingContent);

                  } else if (data.type === 'response.mock_save_tool.completed') {
                    // EXACT COPY: Handle mock save tool completion from old implementation
                    const toolExecution = activeToolExecutions.get('mock_save_tool');
                    if (toolExecution) {
                      toolExecution.status = 'completed';
                      for (let i = contentBlocks.length - 1; i >= 0; i--) {
                        if (contentBlocks[i].type === 'tool_progress') {
                          contentBlocks[i].toolExecutions = Array.from(activeToolExecutions.values());
                          break;
                        }
                      }
                      const blocksWithLoading = addInlineLoading(contentBlocks);
                      contentBlocks = blocksWithLoading;
                      updateMessage(blocksWithLoading, streamingContent);
                    }
                  }

                } catch (error) {
                  console.error('Failed to parse SSE data:', error);
                  continue;
                }
              }
            }
          }
        } catch (error) {
          console.error('Streaming error:', error);
        } finally {
          try {
            reader.cancel();
          } catch {}
        }
      }

      // Update previousResponseId if we got one
      if (responseId) {
        setPreviousResponseId(responseId);
      }

      setIsLoading(false);

    } catch (error) {
      console.error('Error sending message:', error);
      
      setMessages(prev => prev.map(msg => {
        if (msg.role === 'assistant' && msg.isLoading) {
          return {
            ...msg,
            content: `Error: ${error instanceof Error ? error.message : 'Unknown error occurred'}`,
            isLoading: false
          };
        }
        return msg;
      }));

      setIsLoading(false);
    }
  }, [config, isLoading, previousResponseId]);

  const resetConversation = useCallback(() => {
    // Abort any ongoing streaming
    if (abortControllerRef.current) {
      abortControllerRef.current.abort();
      abortControllerRef.current = null;
    }

    setMessages([]);
    setConversationId(null);
    setPreviousResponseId(null);
    setIsLoading(false);
    
    // Generate new session ID
    const newSessionId = generateSessionId();
    localStorage.setItem('platform_sessionId', newSessionId);
    console.log('Generated new sessionId:', newSessionId);
  }, []);

  const retry = useCallback(async (messageId?: string): Promise<void> => {
    // Find the error message to retry
    const errorMessageIndex = messageId 
      ? messages.findIndex(msg => msg.id === messageId)
      : messages.findIndex(msg => msg.role === 'assistant' && msg.content.startsWith('Error:'));
    
    if (errorMessageIndex === -1) {
      console.warn('No error message found to retry');
      return;
    }

    // Find the corresponding user message (should be just before the error message)
    const userMessageIndex = errorMessageIndex - 1;
    if (userMessageIndex < 0 || messages[userMessageIndex].role !== 'user') {
      console.warn('No user message found to retry');
      return;
    }

    const userMessage = messages[userMessageIndex];
    
    // Remove the error message and all messages after it
    setMessages(prev => prev.slice(0, errorMessageIndex));
    
    // Re-send the user message
    await sendTextMessage(userMessage.content);
  }, [messages, sendTextMessage]);

  // EXACT COPY: Artificial streaming of greeting text from old implementation
  const streamGreetingMessage = useCallback((greetingText: string) => {
    // Reset conversation first
    setMessages([]);
    setConversationId(null);
    setPreviousResponseId(null);
    setLastRequest(null);
    setIsLoading(false);

    if (!greetingText) return;

    const greetingId = Date.now().toString() + '_assistant';
    const greetingMessage: Message = {
      id: greetingId,
      role: 'assistant',
      content: '',
      type: 'text',
      timestamp: new Date(),
      isLoading: true
    };
    setMessages([greetingMessage]);

    // Artificial streaming of greeting text - EXACT COPY from old implementation
    let idx = 0;
    const interval = setInterval(() => {
      idx += 1;
      const partial = greetingText.slice(0, idx);
      setMessages(prev => prev.map(msg => msg.id === greetingId ? { ...msg, content: partial } : msg));
      if (idx >= greetingText.length) {
        clearInterval(interval);
        setMessages(prev => prev.map(msg => msg.id === greetingId ? { ...msg, isLoading: false } : msg));
      }
    }, 25);
  }, []);

  // EXACT COPY: Stream user message function from old implementation
  const streamUserMessage = useCallback((userText: string, onComplete?: () => void) => {
    const userMessageId = Date.now().toString() + '_user';
    const userMessage: Message = {
      id: userMessageId,
      role: 'user',
      content: '',
      type: 'text',
      timestamp: new Date(),
      isLoading: true
    };
    
    setMessages(prev => [...prev, userMessage]);
    
    // Stream user message (EXACT COPY from old implementation)
    let userIdx = 0;
    const userInterval = setInterval(() => {
      userIdx += 1;
      const userPartial = userText.slice(0, userIdx);
      setMessages(prev => prev.map(msg => msg.id === userMessageId ? { ...msg, content: userPartial } : msg));
      if (userIdx >= userText.length) {
        clearInterval(userInterval);
        setMessages(prev => prev.map(msg => msg.id === userMessageId ? { ...msg, isLoading: false } : msg));
        
        // Call onComplete callback if provided
        if (onComplete) {
          setTimeout(() => {
            onComplete();
          }, 500);
        }
      }
    }, 25);
  }, []);

  // Simple function to add an assistant message to the new chat
  const addAssistantMessage = useCallback((message: Message) => {
    setMessages(prev => [...prev, message]);
  }, []);

  // Function to update a specific message by ID
  const updateMessage = useCallback((messageId: string, updates: Partial<Message>) => {
    setMessages(prev => prev.map(msg => 
      msg.id === messageId ? { ...msg, ...updates } : msg
    ));
  }, []);

  // Initialize session ID on first use
  useState(() => {
    getOrCreateSessionId();
  });

  return {
    messages,
    isLoading,
    previousResponseId,
    conversationId,
    lastRequest,
    sendTextMessage,
    resetConversation,
    retry,
    streamGreetingMessage,
    streamUserMessage,
    addAssistantMessage,
    updateMessage
  };
}
