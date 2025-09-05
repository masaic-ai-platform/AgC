import React, { useState, useRef, useEffect, useMemo, useCallback } from 'react';
import { useNavigate } from 'react-router-dom';
import { ResponsesChat, buildToolsPayload, ResponsesChatRef } from '@/chat';
import { UseResponsesChatConfig } from '@/chat/types';
import ModelSelector from '@/components/ModelSelector';
import ApiKeysModal from '@/components/ApiKeysModal';
import AgentsSelectionModal from '@/components/AgentsSelectionModal';
import AgentBuilderRollingMessages from '@/components/AgentBuilderRollingMessages';
import CodeTabs from '@/playground/CodeTabs';
import { apiClient } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Bot, Sparkles, RotateCcw, Code, Home, Copy, Check } from 'lucide-react';
import { toast } from 'sonner';



const AgentBuilder: React.FC = () => {
  const navigate = useNavigate();
  
  // Model state
  const [modelProvider, setModelProvider] = useState('');
  const [modelName, setModelName] = useState('');

  // API Key modal state
  const [apiKeysModalOpen, setApiKeysModalOpen] = useState(false);
  const [requiredProvider, setRequiredProvider] = useState<string | undefined>(undefined);
  const [pendingModelSelection, setPendingModelSelection] = useState<string | null>(null);

  // Chat ref for controlling the chat
  const chatRef = useRef<ResponsesChatRef>(null);

  // Agent creation state
  const [agentCreationStep, setAgentCreationStep] = useState<'idle' | 'building' | 'ready'>('idle');
  const [createdAgentName, setCreatedAgentName] = useState<string>('');
  
  // Rolling messages state
  const [isRollingMessages, setIsRollingMessages] = useState(false);
  
  // Agent builder data
  const [agentBuilderData, setAgentBuilderData] = useState<any>(null);
  const [loadingAgentBuilder, setLoadingAgentBuilder] = useState(false);

  // Agents selection modal state
  const [agentsModalOpen, setAgentsModalOpen] = useState(false);
  
  // Agent modification context
  const [modifyAgent, setModifyAgent] = useState(false);
  const [modifiedAgentName, setModifiedAgentName] = useState('');
  
  // Agent modification state
  const [selectedAgentData, setSelectedAgentData] = useState<any>(null);
  const [loadingSelectedAgent, setLoadingSelectedAgent] = useState(false);
  
  // Copy response ID state
  const [copiedResponseId, setCopiedResponseId] = useState(false);
  
  // Chat state for agent modification mode
  const agentChatRef = useRef<ResponsesChatRef>(null);
  const [agentChatPreviousResponseId, setAgentChatPreviousResponseId] = useState<string | null>(null);
  const [agentChatLastRequest, setAgentChatLastRequest] = useState<any>(null);
  const [codeModalOpen, setCodeModalOpen] = useState(false);

  // Load agent builder data and start chat on component mount
  useEffect(() => {
    // Load saved model from localStorage first
    const savedProvider = localStorage.getItem('platform_ab_modelProvider');
    const savedName = localStorage.getItem('platform_ab_modelName');
    
    // Set saved values if they exist
    if (savedProvider) setModelProvider(savedProvider);
    if (savedName) setModelName(savedName);
    
    // Then fetch agent builder data
    fetchAgentBuilderData();
  }, []);

  const fetchAgentBuilderData = async () => {
    try {
      setLoadingAgentBuilder(true);
      const data = await apiClient.agentJsonRequest<any>('/v1/agents/agent-builder');
      
      if (data) {
        setAgentBuilderData({
          systemPrompt: data.systemPrompt || '',
          greetingMessage: data.greetingMessage || '',
          description: data.description || '',
          tools: data.tools || []
        });
        
        // Set default model if not selected (check localStorage directly to avoid race condition)
        const savedProvider = localStorage.getItem('platform_ab_modelProvider');
        const savedName = localStorage.getItem('platform_ab_modelName');
        
        if (!savedProvider && !savedName) {
          setModelProvider('openai');
          setModelName('gpt-4o');
        }
        
        // Start the agent builder chat immediately
        setAgentCreationStep('building');
      }
    } catch (error) {
      console.error('Failed to load agent builder data:', error);
    } finally {
      setLoadingAgentBuilder(false);
    }
  };

  // Save model selection to localStorage whenever it changes
  useEffect(() => {
    if (modelProvider && modelName) {
      try {
        localStorage.setItem('platform_ab_modelProvider', modelProvider);
        localStorage.setItem('platform_ab_modelName', modelName);
      } catch (error) {
        console.error('Failed to save model to localStorage:', error);
      }
    }
  }, [modelProvider, modelName]);

  // Get API key for the current provider from AgentBuilder's scoped storage
  const getProviderApiKey = (): string => {
    try {
      // Read the provider from AgentBuilder's specific localStorage key
      const currentProvider = localStorage.getItem('platform_ab_modelProvider') || modelProvider;
      
      const saved = localStorage.getItem('platform_apiKeys');
      if (!saved) return '';
      const savedKeys: { name: string; apiKey: string }[] = JSON.parse(saved);
      return savedKeys.find(item => item.name === currentProvider)?.apiKey || '';
    } catch {
      return '';
    }
  };

  // Handle API key requirement for model selection
  const handleApiKeyRequired = (provider: string) => {
    setPendingModelSelection(`${provider}@${modelName || 'gpt-4o'}`);
    setRequiredProvider(provider);
    setApiKeysModalOpen(true);
  };

  // Handler for agent selection - clear messages and set modify context
  const handleAgentSelect = async (agent: any) => {
    console.log('Agent selected:', agent);
    
    // Clear all chat messages
    if (chatRef.current) {
      chatRef.current.resetConversation();
    }
    
    // Set modify context - all subsequent requests will have modifyAgent=true
    setModifyAgent(true);
    setModifiedAgentName(agent.name);
    
    // Load the selected agent data
    await loadSelectedAgentData(agent.name);
    
    // Reset chat initialization to prevent greeting message
    setChatInitialized(true);
    
    setAgentsModalOpen(false);
  };

  // Load selected agent data for modification mode
  const loadSelectedAgentData = async (agentName: string) => {
    setLoadingSelectedAgent(true);
    try {
      const data = await apiClient.agentJsonRequest(`/v1/agents/${agentName}`);
      console.log('Loaded agent data:', data);
      setSelectedAgentData(data);
      
      // Reset chat state for agent modification mode
      if (agentChatRef.current) {
        agentChatRef.current.resetConversation();
      }
      setAgentChatPreviousResponseId(null);
      setAgentChatLastRequest(null);
      
    } catch (error) {
      console.error('Error loading agent data:', error);
      toast.error('Failed to load agent data');
    } finally {
      setLoadingSelectedAgent(false);
    }
  };

  // Handler for "New" button - reset chat and clear modify context
  const handleNewAgent = () => {
    console.log('New agent creation requested');
    
    // Clear all chat messages
    if (chatRef.current) {
      chatRef.current.resetConversation();
    }
    
    // Reset modify context - all subsequent requests will have modifyAgent=false
    setModifyAgent(false);
    setModifiedAgentName('');
    setSelectedAgentData(null);
    
    // Reset agent chat state
    if (agentChatRef.current) {
      agentChatRef.current.resetConversation();
    }
    setAgentChatPreviousResponseId(null);
    setAgentChatLastRequest(null);
    
    // Reset rolling messages state
    setIsRollingMessages(false);
    setAgentCreationStep('idle');
    setCreatedAgentName('');
    
    // Reset chat initialization to allow greeting message
    setChatInitialized(false);
    
    // Small delay then stream greeting message again
    setTimeout(() => {
      if (chatRef.current && agentBuilderData && !chatInitialized) {
        const greetingMessage = agentBuilderData.greetingMessage || "Hi! I'm your Agent Builder assistant. I'll help you create a custom agent by understanding your requirements and automatically configuring the right tools and capabilities. What kind of agent would you like to build today?";
        chatRef.current.streamGreetingMessage(greetingMessage);
        setChatInitialized(true);
      }
    }, 100);
  };

  // Reset agent chat conversation
  const resetAgentChat = () => {
    if (agentChatRef.current) {
      agentChatRef.current.resetConversation();
    }
    setAgentChatPreviousResponseId(null);
    setAgentChatLastRequest(null);
  };

  // Handle copying response ID to clipboard
  const handleCopyResponseId = async () => {
    if (!agentChatPreviousResponseId) return;
    
    try {
      await navigator.clipboard.writeText(agentChatPreviousResponseId);
      setCopiedResponseId(true);
      setTimeout(() => setCopiedResponseId(false), 2000);
      toast.success('Response ID copied to clipboard');
    } catch (err) {
      console.error('Failed to copy response ID:', err);
      toast.error('Failed to copy response ID');
    }
  };

  // Handle agent updated event - refresh agent data (for agent modification mode)
  const handleAgentUpdated = useCallback(async () => {
    if (!modifiedAgentName) {
      return;
    }

    try {
      // Reload the agent data from the API
      await loadSelectedAgentData(modifiedAgentName);
      
      // Show a subtle notification that the agent was updated
      toast.success(`Agent "${modifiedAgentName}" has been updated`, {
        duration: 3000,
      });
      
    } catch (error) {
      console.error('Failed to refresh agent data:', error);
      toast.error('Failed to refresh agent data');
    }
  }, [modifiedAgentName, loadSelectedAgentData]);

  // Central event handler for all streaming events from AgentBuilder
  const handleStreamingEvent = useCallback((evt: { type: string; data: any }) => {
    // Handle agent updated event - check both event type and data.type
    if (evt.data?.type === 'response.agent.updated') {
      // Only refresh if we're in agent modification mode
      if (modifyAgent && modifiedAgentName) {
        handleAgentUpdated();
      } else {
        // In new agent creation mode, load the created agent
        if (evt.data?.agentName) {
          setCreatedAgentName(evt.data.agentName);
          setIsRollingMessages(false);
          
          // Set up to show the created agent in the right panel
          setModifyAgent(true);
          setModifiedAgentName(evt.data.agentName);
          
          // Load the created agent data
          loadSelectedAgentData(evt.data.agentName);
        }
      }
    }
    // Handle agent creation in progress event
    else if (evt.data?.type === 'response.agent.creation.in_progress') {
      console.log('Agent creation in progress');
      setIsRollingMessages(true);
    }
    // Handle agent creation paused event
    else if (evt.data?.type === 'response.agent.creation.paused') {
      console.log('Agent creation paused');
      setIsRollingMessages(false);
    }
  }, [modifyAgent, modifiedAgentName, handleAgentUpdated]);

  // Transform tools to add code_interpreter for py_fun_tool (similar to AI Playground)
  const transformToolsForAgentChat = (tools: any[]): any[] => {
    if (!Array.isArray(tools)) return [];
    
    return tools.map(tool => {
      if (tool.type === 'py_fun_tool') {
        // Get E2B server configuration from localStorage
        const e2bConfig = localStorage.getItem('platform_e2b_mcp');
        let codeInterpreter = {};
        if (e2bConfig) {
          try {
            const e2bData = JSON.parse(e2bConfig);
            codeInterpreter = {
              server_label: e2bData.server_label || '',
              url: e2bData.url || '',
              apiKey: e2bData.apiKey || ''
            };
          } catch (error) {
            console.error('Error parsing E2B configuration:', error);
          }
        }
        
        return {
          ...tool,
          code_interpreter: codeInterpreter
        };
      }
      return tool;
    });
  };

  // Initialize chat only once when agent builder data loads (not on model changes)
  const [chatInitialized, setChatInitialized] = useState(false);
  
  useEffect(() => {
    if (!loadingAgentBuilder && modelProvider && modelName && agentBuilderData && chatRef.current && !chatInitialized) {
      // Stream the greeting message from agent builder data or use default
      const greetingMessage = agentBuilderData.greetingMessage || "Hi! I'm your Agent Builder assistant. I'll help you create a custom agent by understanding your requirements and automatically configuring the right tools and capabilities. What kind of agent would you like to build today?";
      
      // Small delay to ensure chat component is fully mounted
      setTimeout(() => {
        if (chatRef.current) {
          chatRef.current.streamGreetingMessage(greetingMessage);
          setChatInitialized(true);
        }
      }, 100);
    }
  }, [loadingAgentBuilder, modelProvider, modelName, agentBuilderData, chatInitialized]);

  // Agent Builder request transformer for POST /v1/agents/agent-builder/chat API
  const agentBuilderRequestTransformer = (standardRequest: any, context: any) => {
    // Always ensure correct values based on context
    const isModifying = context?.modifyAgent === true && !!context?.modifiedAgentName;
    
    return {
      modifyAgent: isModifying,  // Always boolean true/false
      modifiedAgentName: isModifying ? context.modifiedAgentName : '',  // Agent name or empty string
      responsesRequest: standardRequest
    };
  };

  // Chat configuration for agent builder - memoized to prevent unnecessary re-renders
  const chatConfig: UseResponsesChatConfig = useMemo(() => {
    // Get the API key for the current provider
    const currentProvider = localStorage.getItem('platform_ab_modelProvider') || modelProvider;
    const apiKey = getProviderApiKey();
    
    return {
      model: {
        provider: modelProvider || 'openai',
        name: modelName || 'gpt-4o'
      },
      instructions: agentBuilderData?.systemPrompt || '',
      textFormat: 'text' as const,
      tools: agentBuilderData?.tools || [],
      store: true,
      stream: true,
      headers: apiKey ? {
        'Authorization': `Bearer ${apiKey}`
      } : {},
      // Custom endpoint and request transformation for agent builder
      customEndpoint: '/v1/agents/agent-builder/chat',
      requestTransformer: agentBuilderRequestTransformer,
      customContext: {
        modifyAgent,
        modifiedAgentName
      },
      // Add event handler to catch response.agent.updated from left pane stream
      onEvent: handleStreamingEvent
    };
  }, [modelProvider, modelName, agentBuilderData, modifyAgent, modifiedAgentName, handleStreamingEvent]);



  const renderRightPanel = () => {
    // Agent modification mode - show chat interface
    if (modifyAgent && modifiedAgentName) {
      if (loadingSelectedAgent) {
        return (
          <div className="flex items-center justify-center h-full">
            <div className="text-center">
              <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
              <h3 className="text-lg font-medium text-foreground mb-2">Loading Agent</h3>
              <p className="text-sm text-muted-foreground">Loading {modifiedAgentName} for modification...</p>
            </div>
          </div>
        );
      }

      if (!selectedAgentData) {
        return (
          <div className="flex items-center justify-center h-full">
            <div className="text-center">
              <Bot className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-medium text-foreground mb-2">Agent Not Found</h3>
              <p className="text-sm text-muted-foreground">Could not load agent data for {modifiedAgentName}</p>
            </div>
          </div>
        );
      }

      return (
        <div className="flex flex-col h-full">
          {/* Chat Header */}
          <div className="sticky top-0 z-10 bg-background/95 backdrop-blur-sm border-b border-border px-6 py-3">
            <div className="flex items-center justify-between w-full">
              {/* Left side - Agent name and action buttons */}
              <div className="flex items-center space-x-2">
                <span className="text-sm text-foreground font-medium px-2 py-1 bg-accent/50 rounded-md">
                  {modifiedAgentName}
                </span>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={resetAgentChat}
                  className="flex items-center space-x-2 text-muted-foreground hover:text-foreground hover:bg-muted/50"
                  title="Reset conversation"
                >
                  <RotateCcw className="w-4 h-4" />
                  <span className="text-sm">Reset Chat</span>
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  onClick={() => setCodeModalOpen(true)}
                  disabled={!agentChatLastRequest}
                  className="flex items-center space-x-2 text-muted-foreground hover:text-foreground hover:bg-muted/50"
                  title={agentChatLastRequest ? "View code snippets for last request" : "Send a message to generate code snippets"}
                >
                  <Code className="w-4 h-4" />
                  <span className="text-sm">View Code</span>
                </Button>
              </div>
              
              {/* Right side - Response ID */}
              {agentChatPreviousResponseId && (
                <div className="flex items-center space-x-2">
                  <span className="text-xs text-muted-foreground">Response ID:</span>
                  <div className="flex items-center space-x-1 bg-muted/50 rounded px-2 py-1">
                    <code className="text-xs font-mono text-foreground">
                      {agentChatPreviousResponseId.length > 20 
                        ? `${agentChatPreviousResponseId.substring(0, 20)}...` 
                        : agentChatPreviousResponseId
                      }
                    </code>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={handleCopyResponseId}
                      className="h-6 w-6 p-0 text-muted-foreground hover:text-foreground"
                      title="Copy response ID"
                    >
                      {copiedResponseId ? (
                        <Check className="w-3 h-3 text-positive-trend" />
                      ) : (
                        <Copy className="w-3 h-3" />
                      )}
                    </Button>
                  </div>
                </div>
              )}
            </div>
          </div>

          {/* Chat Area - Wrapper needed since ResponsesChat returns fragment */}
          <div className="flex-1 flex flex-col min-h-0 overflow-hidden">
            <ResponsesChat
              ref={agentChatRef}
              hookConfig={{
                model: { provider: modelProvider, name: modelName },
                instructions: selectedAgentData.systemPrompt || '',
                textFormat: 'text',
                tools: Array.isArray(selectedAgentData.tools) ? transformToolsForAgentChat(selectedAgentData.tools) : undefined,
                store: true,
                stream: true,
                // Pass the correct API key for AgentBuilder's model provider
                headers: getProviderApiKey() ? {
                  'Authorization': `Bearer ${getProviderApiKey()}`
                } : undefined,
                // Use the central event handler to catch agent updated events
                onEvent: handleStreamingEvent
              }}
              placeholder={`Chat with ${modifiedAgentName}...`}
              onPreviousResponseIdChange={setAgentChatPreviousResponseId}
              onLastRequestChange={setAgentChatLastRequest}
            />
          </div>
        </div>
      );
    }

    switch (agentCreationStep) {
      case 'idle':
        if (loadingAgentBuilder) {
          return (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
                <h3 className="text-lg font-medium text-foreground mb-2">Loading Agent Builder</h3>
                <p className="text-sm text-muted-foreground">Setting up the agent building environment...</p>
              </div>
            </div>
          );
        }
        
        // Show rolling messages component for new agent creation mode
        return <AgentBuilderRollingMessages isRolling={isRollingMessages} />;
      
      case 'building':
        // Show rolling messages component for building state as well
        return <AgentBuilderRollingMessages isRolling={isRollingMessages} />;
      
      case 'ready':
        return (
          <div className="flex flex-col h-full">
            <div className="flex items-center space-x-2 p-4 border-b">
              <Bot className="h-5 w-5 text-green-600" />
              <h3 className="text-lg font-medium">Agent Ready!</h3>
            </div>
            <div className="flex-1 flex flex-col">
              <div className="p-4 text-center">
                <div className="bg-green-50 dark:bg-green-900/20 p-6 rounded-lg mb-4">
                  <h4 className="text-lg font-medium text-green-800 dark:text-green-400 mb-2">
                    🎉 Agent "{createdAgentName}" created successfully!
                  </h4>
                  <p className="text-sm text-green-600 dark:text-green-300">
                    Your agent is now ready to use. You can start chatting with it or share it with your technical teams for integration.
                  </p>
                </div>
                <div className="space-y-3">
                  <Button className="w-full" variant="default">
                    Start Chatting with Agent
                  </Button>
                  <Button className="w-full" variant="outline">
                    Share Agent Details
                  </Button>
                  <Button className="w-full" variant="ghost" onClick={() => {
                    setAgentCreationStep('idle');
                    setCreatedAgentName('');
                    chatRef.current?.resetConversation();
                  }}>
                    Build Another Agent
                  </Button>
                </div>
              </div>
            </div>
          </div>
        );
      
      default:
        return null;
    }
  };

  return (
    <div className="h-full bg-background flex">
        {/* Left Panel - Agent Builder Configuration (1/3) */}
        <div className="w-1/3 min-w-0 border-r border-border flex flex-col bg-muted/40">
          {/* Header Section */}
          <div className="p-6 space-y-4">
            {/* Header CTAs */}
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-2">
                <AgentsSelectionModal
                  open={agentsModalOpen}
                  onOpenChange={setAgentsModalOpen}
                  onAgentSelect={handleAgentSelect}
                  triggerButton={
                    <Button
                      variant="ghost"
                      size="sm"
                      className="h-7 px-2 text-xs text-muted-foreground hover:text-green-600 hover:bg-green-50 dark:hover:bg-green-900/20 dark:hover:text-green-400 transition-colors"
                      onClick={() => setAgentsModalOpen(true)}
                    >
                      <Bot className="h-3 w-3 mr-2" />
                      Agents
                    </Button>
                  }
                />
                {/* Show modification context label when modifying an agent */}
                {modifyAgent && modifiedAgentName && (
                  <span className="text-xs text-green-600 dark:text-green-400 font-medium">
                    Modifying {modifiedAgentName}
                  </span>
                )}
              </div>
              <div className="flex items-center space-x-2">
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-7 px-2 text-xs text-muted-foreground hover:text-blue-600 hover:bg-blue-50 dark:hover:bg-blue-900/20 dark:hover:text-blue-400 transition-colors"
                  onClick={() => navigate('/')}
                  title="Go to AI Playground"
                >
                  <Home className="h-3 w-3" />
                </Button>
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-7 px-2 text-xs text-muted-foreground hover:text-green-600 hover:bg-green-50 dark:hover:bg-green-900/20 dark:hover:text-green-400 transition-colors"
                  onClick={handleNewAgent}
                >
                  New
                </Button>
              </div>
            </div>
            
            {/* Model Selection */}
            <div className="flex items-center space-x-3 min-w-0">
              <label className="text-sm font-medium text-foreground shrink-0">Model</label>
              <div className="flex-1 min-w-0">
                <ModelSelector
                  modelProvider={modelProvider}
                  setModelProvider={setModelProvider}
                  modelName={modelName}
                  setModelName={setModelName}
                  onApiKeyRequired={handleApiKeyRequired}
                />
              </div>
            </div>
          </div>

        {/* Agent Builder Chat - Same as AiPlayground */}
        <div className="flex-1 flex flex-col min-h-0">
          {agentBuilderData && (modelProvider && modelName) ? (
            <ResponsesChat
              ref={chatRef}
              hookConfig={chatConfig}
              placeholder="Describe the agent you want to build..."
              apiKey={getProviderApiKey()}
              baseUrl="http://localhost:8080"
              imageModelProvider=""
              imageModelName=""
              imageProviderKey=""
              selectedVectorStore=""
              suggestedQueries={[]}
              agentMode={true}
              previousResponseId={null}
              onPreviousResponseIdChange={() => {}}
              onLastRequestChange={() => {}}
              modelTestMode={false}
              showSaveModel={false}
              saveModelState={null}
              onSaveModel={() => {}}

            />
          ) : (
            <div className="flex items-center justify-center h-full">
              <div className="text-center">
                <div className="w-8 h-8 border-2 border-primary border-t-transparent rounded-full animate-spin mx-auto mb-4"></div>
                <p className="text-sm text-muted-foreground">
                  {loadingAgentBuilder ? 'Loading agent builder...' : 'Initializing chat...'}
                </p>
              </div>
            </div>
          )}
        </div>
        </div>

        {/* Right Panel - Agent Creation Progress (2/3) */}
        <div className="flex-1 flex flex-col">
          {renderRightPanel()}
        </div>

      {/* API Keys Modal */}
      <ApiKeysModal
        open={apiKeysModalOpen}
        onOpenChange={(open) => {
          setApiKeysModalOpen(open);
          if (!open) {
            // Clear state when modal closes (regardless of reason)
            setRequiredProvider(undefined);
            setPendingModelSelection(null);
          }
        }}
        onSaveSuccess={() => {
          // Only complete model selection when keys are successfully saved
          if (pendingModelSelection && requiredProvider) {
            // Add a small delay to ensure localStorage is updated
            setTimeout(() => {
              const [provider, name] = pendingModelSelection.split('@');
              setModelProvider(provider);
              setModelName(name);
            }, 100);
          }
        }}
        requiredProvider={requiredProvider}
      />

      {/* Code Snippets Modal for Agent Chat */}
      <CodeTabs
        open={codeModalOpen}
        onOpenChange={setCodeModalOpen}
        lastRequest={agentChatLastRequest}
        baseUrl="http://localhost:8080"
      />
    </div>
  );
};

export default AgentBuilder;
