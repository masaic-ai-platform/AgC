import React, { useState, useRef, useEffect, useMemo } from 'react';
import { ResponsesChat, buildToolsPayload, ResponsesChatRef } from '@/chat';
import { UseResponsesChatConfig } from '@/chat/types';
import ModelSelector from '@/components/ModelSelector';
import ApiKeysModal from '@/components/ApiKeysModal';
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

  // Load agent builder data and start chat on component mount
  useEffect(() => {
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

  // Handle API key requirement for model selection
  const handleApiKeyRequired = (provider: string) => {
    setPendingModelSelection(`${provider}@${modelName || 'gpt-4o'}`);
    setRequiredProvider(provider);
    setApiKeysModalOpen(true);
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

  // Chat configuration for agent builder - memoized to prevent unnecessary re-renders
  const chatConfig: UseResponsesChatConfig = useMemo(() => ({
    model: {
      provider: modelProvider || 'openai',
      name: modelName || 'gpt-4o'
    },
    instructions: agentBuilderData?.systemPrompt || '',
    textFormat: 'text' as const,
    tools: agentBuilderData?.tools || [],
    store: true,
    stream: true
  }), [modelProvider, modelName, agentBuilderData]);



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
            <h2 className="text-xl font-semibold text-foreground">New Agent Builder</h2>
            
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
              apiKey=""
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
