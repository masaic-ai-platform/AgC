import React, { useState, useRef, useEffect, useMemo } from 'react';
import { ResponsesChat, buildToolsPayload, ResponsesChatRef } from '@/chat';
import { UseResponsesChatConfig } from '@/chat/types';
import ModelSelector from '@/components/ModelSelector';
import ApiKeysModal from '@/components/ApiKeysModal';
import AgentsSelectionModal from '@/components/AgentsSelectionModal';
import { apiClient } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Bot, Sparkles, RotateCcw } from 'lucide-react';



const AgentBuilder: React.FC = () => {
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
  
  // Agent builder data
  const [agentBuilderData, setAgentBuilderData] = useState<any>(null);
  const [loadingAgentBuilder, setLoadingAgentBuilder] = useState(false);

  // Agents selection modal state
  const [agentsModalOpen, setAgentsModalOpen] = useState(false);
  
  // Agent modification context
  const [modifyAgent, setModifyAgent] = useState(false);
  const [modifiedAgentName, setModifiedAgentName] = useState('');

  // Load agent builder data and start chat on component mount
  useEffect(() => {
    fetchAgentBuilderData();
    
    // Load saved model from localStorage
    const savedProvider = localStorage.getItem('platform_ab_modelProvider');
    const savedName = localStorage.getItem('platform_ab_modelName');
    if (savedProvider) setModelProvider(savedProvider);
    if (savedName) setModelName(savedName);
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
        
        // Set default model if not selected
        if (!modelProvider && !modelName) {
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
    console.log('Setting modifyAgent to true, agent name:', agent.name);
    setModifyAgent(true);
    setModifiedAgentName(agent.name);
    
    // Reset chat initialization to prevent greeting message
    setChatInitialized(true);
    
    setAgentsModalOpen(false);
  };

  // Handler for "New" button - reset chat and clear modify context
  const handleNewAgent = () => {
    console.log('New agent creation requested');
    
    // Clear all chat messages
    if (chatRef.current) {
      chatRef.current.resetConversation();
    }
    
    // Reset modify context - all subsequent requests will have modifyAgent=false
    console.log('Setting modifyAgent to false, clearing agent name');
    setModifyAgent(false);
    setModifiedAgentName('');
    
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
    // Debug logging
    console.log('Request transformer - context:', context);
    console.log('context.modifyAgent type:', typeof context?.modifyAgent, 'value:', context?.modifyAgent);
    console.log('context.modifiedAgentName:', context?.modifiedAgentName);
    
    // Always ensure correct values based on context
    const isModifying = context?.modifyAgent === true && !!context?.modifiedAgentName;
    
    const result = {
      modifyAgent: isModifying,  // Always boolean true/false
      modifiedAgentName: isModifying ? context.modifiedAgentName : '',  // Agent name or empty string
      responsesRequest: standardRequest
    };
    
    console.log('Request transformer - result:', result);
    return result;
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
      }
    };
  }, [modelProvider, modelName, agentBuilderData, modifyAgent, modifiedAgentName]);



  const renderRightPanel = () => {
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
        
        return (
          <div className="flex items-center justify-center h-full">
            <div className="text-center">
              <Bot className="h-16 w-16 text-muted-foreground mx-auto mb-4" />
              <h3 className="text-lg font-medium text-foreground mb-2">Ready to Build</h3>
              <p className="text-sm text-muted-foreground">The agent builder is ready. Start describing your agent requirements!</p>
            </div>
          </div>
        );
      
      case 'building':
        return (
          <div className="flex items-center justify-center h-full">
            <div className="text-center max-w-md">
              <Sparkles className="h-16 w-16 text-primary mx-auto mb-4 animate-pulse" />
              <h3 className="text-lg font-medium text-foreground mb-2">Agent Building in Progress</h3>
              <p className="text-sm text-muted-foreground mb-4">
                Your conversation is happening in the left panel. The AI is analyzing your requirements and will create a custom agent based on your needs.
              </p>
              <div className="bg-muted/30 rounded-lg p-4 text-left">
                <h4 className="text-sm font-medium mb-2">What's happening:</h4>
                <ul className="text-xs text-muted-foreground space-y-1">
                  <li>• Understanding your requirements</li>
                  <li>• Selecting appropriate tools</li>
                  <li>• Configuring agent capabilities</li>
                  <li>• Preparing for deployment</li>
                </ul>
              </div>
            </div>
          </div>
        );
      
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
        <div className="w-1/3 min-w-0 border-r border-border flex flex-col">
          {/* Header Section */}
          <div className="p-6 space-y-4 bg-background">
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
              <Button
                variant="ghost"
                size="sm"
                className="h-7 px-2 text-xs text-muted-foreground hover:text-green-600 hover:bg-green-50 dark:hover:bg-green-900/20 dark:hover:text-green-400 transition-colors"
                onClick={handleNewAgent}
              >
                New
              </Button>
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
    </div>
  );
};

export default AgentBuilder;
