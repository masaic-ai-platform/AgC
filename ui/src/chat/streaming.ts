import { ContentBlock, ToolExecution, AgenticSearchLog, TextFormat } from './types';

export interface StreamingEvent {
  type: string;
  data: any;
}

export interface StreamingState {
  streamingContent: string;
  contentBlocks: ContentBlock[];
  activeToolExecutions: Map<string, ToolExecution>;
  isStreaming: boolean;
  currentTextBlock: ContentBlock | null;
  responseId: string | null;
  encounteredStreamError: boolean;
}

export function createInitialStreamingState(): StreamingState {
  return {
    streamingContent: '',
    contentBlocks: [],
    activeToolExecutions: new Map(),
    isStreaming: false,
    currentTextBlock: null,
    responseId: null,
    encounteredStreamError: false,
  };
}

export function addInlineLoading(contentBlocks: ContentBlock[]): ContentBlock[] {
  // Remove any existing inline loading blocks first
  const blocksWithoutLoading = contentBlocks.filter(block => block.type !== 'inline_loading');
  // Add new inline loading block
  return [...blocksWithoutLoading, { type: 'inline_loading' }];
}

export function removeInlineLoading(contentBlocks: ContentBlock[]): ContentBlock[] {
  return contentBlocks.filter(block => block.type !== 'inline_loading');
}

export async function* parseResponsesStream(
  response: Response,
  textFormat: TextFormat,
  onEvent?: (evt: StreamingEvent) => void
): AsyncGenerator<StreamingState, StreamingState, unknown> {
  if (!response.body) {
    throw new Error('Response body is null');
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  let lastEvent = '';

  const state = createInitialStreamingState();

  const updateMessage = (blocks: ContentBlock[], content: string) => {
    state.contentBlocks = blocks;
    state.streamingContent = content;
  };

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

            // Emit event if handler provided
            if (onEvent) {
              onEvent({ type: lastEvent || data.type || 'data', data });
            }

            // Handle explicit error events from SSE stream  
            if (lastEvent === 'error' || (data.type === 'error') || (data.code && data.message) || data.error) {
              console.log('🚨 Error detected in stream!', { lastEvent, dataType: data.type, dataCode: data.code, dataMessage: data.message, dataError: data.error });
              const errorCode = data.code || 'error';
              const errorMsg = data.message || data.error || 'Unknown error';
              const errorContent = `Error: [${errorCode}] ${errorMsg}`;

              // Clear all active tool executions to stop loading spinners
              state.activeToolExecutions.clear();
              state.encounteredStreamError = true;
              
              // Remove any inline loading blocks and tool progress blocks
              const errorContentBlocks = state.contentBlocks.filter(block => 
                block.type !== 'inline_loading' && block.type !== 'tool_progress'
              );
              
              // Add the error content block
              errorContentBlocks.push({
                type: 'text',
                content: `[${errorCode}] ${errorMsg}`
              });

              state.contentBlocks = errorContentBlocks;
              state.streamingContent = errorContent;
              state.isStreaming = false;

              yield state;
              reader.cancel().catch(() => {});
              break;
            }

            // Handle different event types
            if (data.type === 'response.completed') {
              if (data.response?.id) {
                state.responseId = data.response.id;
              }
              // Mark all in-progress tool executions as completed when response completes
              state.activeToolExecutions.forEach((execution, key) => {
                if (execution.status === 'in_progress') {
                  execution.status = 'completed';
                }
              });
              
              // Update tool progress blocks with completed status
              state.contentBlocks.forEach(block => {
                if (block.type === 'tool_progress' && block.toolExecutions) {
                  block.toolExecutions = Array.from(state.activeToolExecutions.values());
                }
              });
              
              // Remove inline loading when response is completed
              const blocksWithoutLoading = removeInlineLoading(state.contentBlocks);
              
              // If no content blocks exist, create a default text block
              let finalBlocks = blocksWithoutLoading;
              if (finalBlocks.length === 0) {
                finalBlocks = [{ type: 'text', content: 'Response completed' }];
              }
              
              // If no streaming content was received, use a default message or empty string
              const finalContent = state.streamingContent || 'Response completed';
              updateMessage(finalBlocks, finalContent);

            } else if (data.type === 'response.output_text.delta') {
              // Start or continue streaming
              if (!state.isStreaming) {
                state.isStreaming = true;
              }
              
              if (data.delta) {
                state.streamingContent += data.delta;
                
                // Create or update current text block
                if (!state.currentTextBlock) {
                  state.currentTextBlock = { type: 'text', content: data.delta };
                  state.contentBlocks.push(state.currentTextBlock);
                } else {
                  state.currentTextBlock.content = (state.currentTextBlock.content || '') + data.delta;
                }
                
                // Check if we have a complete JSON object for real-time formatting
                let displayContent = state.streamingContent;
                if (textFormat === 'json_object' || textFormat === 'json_schema') {
                  try {
                    JSON.parse(state.streamingContent);
                    displayContent = state.streamingContent;
                  } catch {
                    displayContent = state.streamingContent;
                  }
                }
                
                // Remove any inline loading when text streaming starts
                const blocksWithoutLoading = removeInlineLoading(state.contentBlocks);
                updateMessage(blocksWithoutLoading, displayContent);
              }

            } else if (data.type === 'response.output_text.done') {
              // Streaming completed for this output
              state.isStreaming = false;
              state.currentTextBlock = null; // Reset for potential next text stream
              if (data.text) {
                state.streamingContent = data.text;
                // Update the last text block with complete content
                for (let i = state.contentBlocks.length - 1; i >= 0; i--) {
                  if (state.contentBlocks[i].type === 'text') {
                    state.contentBlocks[i].content = data.text;
                    break;
                  }
                }
                updateMessage(state.contentBlocks, state.streamingContent);
              }

            } else if (data.type && data.type.startsWith('response.mcp_call.')) {
              handleMcpToolEvent(data, state);

            } else if (data.type && data.type.startsWith('response.agc.')) {
              handlePyFunctionToolEvent(data, state);

            } else if (data.type === 'response.file_search.in_progress') {
              handleFileSearchStart(state);

            } else if (data.type === 'response.file_search.completed') {
              handleFileSearchComplete(state);

            } else if (data.type === 'response.agentic_search.in_progress') {
              handleAgenticSearchStart(state);

            } else if (data.type === 'response.agentic_search.query_phase.iteration') {
              handleAgenticSearchIteration(data, state);

            } else if (data.type === 'response.agentic_search.completed') {
              handleAgenticSearchComplete(state);

            } else if (data.type === 'response.fun_req_gathering_tool.in_progress') {
              handleFunReqGatheringStart(state);

            } else if (data.type === 'response.fun_req_gathering_tool.completed') {
              handleFunReqGatheringComplete(state);

            } else if (data.type === 'response.fun_def_generation_tool.in_progress') {
              handleFunDefGenerationStart(state);

            } else if (data.type === 'response.fun_def_generation_tool.completed') {
              handleFunDefGenerationComplete(state);

            } else if (data.type === 'response.mock_fun_save_tool.in_progress') {
              handleMockFunSaveStart(state);

            } else if (data.type === 'response.mock_fun_save_tool.completed') {
              handleMockFunSaveComplete(state);
            }

            // Yield state after every event to enable real-time updates
            yield state;

          } catch (error) {
            console.error('Failed to parse SSE data:', error);
            continue;
          }
        }
      }
    }
  } catch (error) {
    console.error('Stream reading error:', error);
    state.encounteredStreamError = true;
  } finally {
    try {
      reader.cancel();
    } catch {}
  }

  return state;
}

function handleMcpToolEvent(data: any, state: StreamingState) {
  const typeParts = data.type.split('.');
  if (typeParts.length >= 4) {
    const toolIdentifier = typeParts[2];
    const status = typeParts[3];
    
    // Parse tool identifier: myshopify_search_shop_catalog
    const identifierParts = toolIdentifier.split('_');
    const serverName = identifierParts[0];
    const toolName = identifierParts.slice(1).join('_');
    
    if (status === 'in_progress') {
      // Add new tool execution
      const toolExecution: ToolExecution = {
        serverName,
        toolName,
        status: 'in_progress'
      };
      state.activeToolExecutions.set(toolIdentifier, toolExecution);
      
      // Find existing tool progress block or create new one
      let toolProgressBlock = state.contentBlocks.find(block => block.type === 'tool_progress');
      if (!toolProgressBlock) {
        toolProgressBlock = {
          type: 'tool_progress',
          toolExecutions: Array.from(state.activeToolExecutions.values())
        };
        state.contentBlocks.push(toolProgressBlock);
      } else {
        // Update existing tool progress block
        toolProgressBlock.toolExecutions = Array.from(state.activeToolExecutions.values());
      }
      state.currentTextBlock = null; // Reset text block for potential next text
      
    } else if (status === 'completed') {
      // Update tool execution status
      const toolExecution = state.activeToolExecutions.get(toolIdentifier);
      if (toolExecution) {
        toolExecution.status = 'completed';
        
        // Update the last tool progress block
        for (let i = state.contentBlocks.length - 1; i >= 0; i--) {
          if (state.contentBlocks[i].type === 'tool_progress') {
            state.contentBlocks[i].toolExecutions = Array.from(state.activeToolExecutions.values());
            break;
          }
        }
        
        // Add inline loading when tools complete, indicating we're waiting for next text stream
        const blocksWithLoading = addInlineLoading(state.contentBlocks);
        state.contentBlocks = blocksWithLoading;
      }
    }
  }
}

function handlePyFunctionToolEvent(data: any, state: StreamingState) {
  const typeParts = data.type.split('.');
  if (typeParts.length >= 4) {
    const toolName = typeParts[2];
    const status = typeParts[3];
    
    if (status === 'executing' || status === 'in_progress') {
      // Add new Py function tool execution
      const toolExecution: ToolExecution = {
        serverName: 'agc',
        toolName,
        status: 'in_progress'
      };
      state.activeToolExecutions.set(`agc_${toolName}`, toolExecution);
      
      // Find existing tool progress block or create new one
      let toolProgressBlock = state.contentBlocks.find(block => block.type === 'tool_progress');
      if (!toolProgressBlock) {
        toolProgressBlock = {
          type: 'tool_progress',
          toolExecutions: Array.from(state.activeToolExecutions.values())
        };
        state.contentBlocks.push(toolProgressBlock);
      } else {
        // Update existing tool progress block
        toolProgressBlock.toolExecutions = Array.from(state.activeToolExecutions.values());
      }
      state.currentTextBlock = null; // Reset text block for potential next text
      
    } else if (status === 'completed') {
      // Update Py function tool execution status
      const toolExecution = state.activeToolExecutions.get(`agc_${toolName}`);
      if (toolExecution) {
        toolExecution.status = 'completed';
        
        // Update the last tool progress block
        for (let i = state.contentBlocks.length - 1; i >= 0; i--) {
          if (state.contentBlocks[i].type === 'tool_progress') {
            state.contentBlocks[i].toolExecutions = Array.from(state.activeToolExecutions.values());
            break;
          }
        }
        
        // Add inline loading when tools complete, indicating we're waiting for next text stream
        const blocksWithLoading = addInlineLoading(state.contentBlocks);
        state.contentBlocks = blocksWithLoading;
      }
    }
  }
}

function handleFileSearchStart(state: StreamingState) {
  const toolExecution: ToolExecution = {
    serverName: 'file_search',
    toolName: 'search',
    status: 'in_progress'
  };
  state.activeToolExecutions.set('file_search', toolExecution);
  
  // Find existing tool progress block or create new one
  let toolProgressBlock = state.contentBlocks.find(block => block.type === 'tool_progress');
  if (!toolProgressBlock) {
    toolProgressBlock = {
      type: 'tool_progress',
      toolExecutions: Array.from(state.activeToolExecutions.values())
    };
    state.contentBlocks.push(toolProgressBlock);
  } else {
    // Update existing tool progress block
    toolProgressBlock.toolExecutions = Array.from(state.activeToolExecutions.values());
  }
  state.currentTextBlock = null; // Reset text block for potential next text
}

function handleFileSearchComplete(state: StreamingState) {
  const toolExecution = state.activeToolExecutions.get('file_search');
  if (toolExecution) {
    toolExecution.status = 'completed';
    
    // Update the last tool progress block
    for (let i = state.contentBlocks.length - 1; i >= 0; i--) {
      if (state.contentBlocks[i].type === 'tool_progress') {
        state.contentBlocks[i].toolExecutions = Array.from(state.activeToolExecutions.values());
        break;
      }
    }
    
    // Add inline loading when tools complete, indicating we're waiting for next text stream
    const blocksWithLoading = addInlineLoading(state.contentBlocks);
    state.contentBlocks = blocksWithLoading;
  }
}

function handleAgenticSearchStart(state: StreamingState) {
  const toolExecution: ToolExecution = {
    serverName: 'agentic_search',
    toolName: 'search',
    status: 'in_progress',
    agenticSearchLogs: []
  };
  state.activeToolExecutions.set('agentic_search', toolExecution);
  
  // Find existing tool progress block or create new one
  let toolProgressBlock = state.contentBlocks.find(block => block.type === 'tool_progress');
  if (!toolProgressBlock) {
    toolProgressBlock = {
      type: 'tool_progress',
      toolExecutions: Array.from(state.activeToolExecutions.values())
    };
    state.contentBlocks.push(toolProgressBlock);
  } else {
    // Update existing tool progress block
    toolProgressBlock.toolExecutions = Array.from(state.activeToolExecutions.values());
  }
  state.currentTextBlock = null; // Reset text block for potential next text
}

function handleAgenticSearchIteration(data: any, state: StreamingState) {
  const toolExecution = state.activeToolExecutions.get('agentic_search');
  if (toolExecution && data.iteration && data.query) {
    const logEntry: AgenticSearchLog = {
      iteration: data.iteration,
      query: data.query,
      reasoning: data.reasoning || '',
      citations: data.citations || [],
      remaining_iterations: data.remaining_iterations || 0
    };
    
    if (!toolExecution.agenticSearchLogs) {
      toolExecution.agenticSearchLogs = [];
    }
    toolExecution.agenticSearchLogs.push(logEntry);
    
    // Update the tool progress block
    for (let i = state.contentBlocks.length - 1; i >= 0; i--) {
      if (state.contentBlocks[i].type === 'tool_progress') {
        state.contentBlocks[i].toolExecutions = Array.from(state.activeToolExecutions.values());
        break;
      }
    }
  }
}

function handleAgenticSearchComplete(state: StreamingState) {
  const toolExecution = state.activeToolExecutions.get('agentic_search');
  if (toolExecution) {
    toolExecution.status = 'completed';
    
    // Update the last tool progress block
    for (let i = state.contentBlocks.length - 1; i >= 0; i--) {
      if (state.contentBlocks[i].type === 'tool_progress') {
        state.contentBlocks[i].toolExecutions = Array.from(state.activeToolExecutions.values());
        break;
      }
    }
    
    // Add inline loading when tools complete, indicating we're waiting for next text stream
    const blocksWithLoading = addInlineLoading(state.contentBlocks);
    state.contentBlocks = blocksWithLoading;
  }
}

function handleFunReqGatheringStart(state: StreamingState) {
  const toolExecution: ToolExecution = {
    serverName: 'fun_req_gathering_tool',
    toolName: 'assemble',
    status: 'in_progress'
  };
  state.activeToolExecutions.set('fun_req_gathering_tool', toolExecution);

  let toolProgressBlock = state.contentBlocks.find(block => block.type === 'tool_progress');
  if (!toolProgressBlock) {
    toolProgressBlock = {
      type: 'tool_progress',
      toolExecutions: Array.from(state.activeToolExecutions.values())
    };
    state.contentBlocks.push(toolProgressBlock);
  } else {
    toolProgressBlock.toolExecutions = Array.from(state.activeToolExecutions.values());
  }
  state.currentTextBlock = null;
}

function handleFunReqGatheringComplete(state: StreamingState) {
  const toolExecution = state.activeToolExecutions.get('fun_req_gathering_tool');
  if (toolExecution) {
    toolExecution.status = 'completed';
    for (let i = state.contentBlocks.length - 1; i >= 0; i--) {
      if (state.contentBlocks[i].type === 'tool_progress') {
        state.contentBlocks[i].toolExecutions = Array.from(state.activeToolExecutions.values());
        break;
      }
    }
    const blocksWithLoading = addInlineLoading(state.contentBlocks);
    state.contentBlocks = blocksWithLoading;
  }
}

function handleFunDefGenerationStart(state: StreamingState) {
  const toolExecution: ToolExecution = {
    serverName: 'fun_def_generation_tool',
    toolName: 'generate',
    status: 'in_progress'
  };
  state.activeToolExecutions.set('fun_def_generation_tool', toolExecution);

  let toolProgressBlock = state.contentBlocks.find(block => block.type === 'tool_progress');
  if (!toolProgressBlock) {
    toolProgressBlock = {
      type: 'tool_progress',
      toolExecutions: Array.from(state.activeToolExecutions.values())
    };
    state.contentBlocks.push(toolProgressBlock);
  } else {
    toolProgressBlock.toolExecutions = Array.from(state.activeToolExecutions.values());
  }
  state.currentTextBlock = null;
}

function handleFunDefGenerationComplete(state: StreamingState) {
  const toolExecution = state.activeToolExecutions.get('fun_def_generation_tool');
  if (toolExecution) {
    toolExecution.status = 'completed';
    for (let i = state.contentBlocks.length - 1; i >= 0; i--) {
      if (state.contentBlocks[i].type === 'tool_progress') {
        state.contentBlocks[i].toolExecutions = Array.from(state.activeToolExecutions.values());
        break;
      }
    }
    const blocksWithLoading = addInlineLoading(state.contentBlocks);
    state.contentBlocks = blocksWithLoading;
  }
}

function handleMockFunSaveStart(state: StreamingState) {
  const toolExecution: ToolExecution = {
    serverName: 'mock_fun_save_tool',
    toolName: 'save_function',
    status: 'in_progress'
  };
  state.activeToolExecutions.set('mock_fun_save_tool', toolExecution);

  let toolProgressBlock = state.contentBlocks.find(block => block.type === 'tool_progress');
  if (!toolProgressBlock) {
    toolProgressBlock = { type: 'tool_progress', toolExecutions: Array.from(state.activeToolExecutions.values()) };
    state.contentBlocks.push(toolProgressBlock);
  } else {
    toolProgressBlock.toolExecutions = Array.from(state.activeToolExecutions.values());
  }
  state.currentTextBlock = null;
}

function handleMockFunSaveComplete(state: StreamingState) {
  const toolExecution = state.activeToolExecutions.get('mock_fun_save_tool');
  if (toolExecution) {
    toolExecution.status = 'completed';
    for (let i = state.contentBlocks.length - 1; i >= 0; i--) {
      if (state.contentBlocks[i].type === 'tool_progress') {
        state.contentBlocks[i].toolExecutions = Array.from(state.activeToolExecutions.values());
        break;
      }
    }
    const blocksWithLoading = addInlineLoading(state.contentBlocks);
    state.contentBlocks = blocksWithLoading;
  }
}
