import React, { useState, useRef, useEffect } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { Drawer, DrawerTrigger, DrawerContent } from '@/components/ui/drawer';
import { Button } from '@/components/ui/button';
import { Card } from '@/components/ui/card';
import UnifiedCard from '@/components/ui/unified-card';
import { Sparkles, RotateCcw, Copy, Check, Menu, Code, Brain, Image, Puzzle, Save, Layers, Search } from 'lucide-react';
import { toast } from 'sonner';
import ConfigurationPanel from './ConfigurationPanel';
import PlaygroundSidebar from './PlaygroundSidebar';
import AgentsSelectionModal from './AgentsSelectionModal';
import CodeTabs from '@/playground/CodeTabs';
import { PlaygroundRequest } from '@/playground/PlaygroundRequest';
import { API_URL } from '@/config';
import { apiClient } from '@/lib/api';
import { usePlatformInfo } from '@/contexts/PlatformContext';
import { ResponsesChat, buildToolsPayload, ResponsesChatRef } from '@/chat';

interface ToolExecution {
  serverName: string;
  toolName: string;
  status: 'in_progress' | 'completed';
  agenticSearchLogs?: AgenticSearchLog[];
}

interface AgenticSearchLog {
  iteration: number;
  query: string;
  reasoning: string;
  citations: string[];
  remaining_iterations: number;
}

interface ContentBlock {
  type: 'text' | 'tool_progress' | 'inline_loading';
  content?: string;
  toolExecutions?: ToolExecution[];
}

interface Message {
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

interface PromptMessage {
  id: string;
  role: 'user' | 'assistant';
  content: string;
}

interface Tool {
  id: string;
  name: string;
  icon: React.ComponentType<{ className?: string }>;
  functionDefinition?: string; // For function tools
  mcpConfig?: any; // For MCP server tools
  fileSearchConfig?: { selectedFiles: string[]; selectedVectorStores: string[]; vectorStoreNames: string[] }; // For file search tools
  agenticFileSearchConfig?: { selectedFiles: string[]; selectedVectorStores: string[]; vectorStoreNames: string[]; iterations: number; maxResults: number }; // For agentic file search tools
  pyFunctionConfig?: any; // For Py function tools
}

const getProviderApiKey = (provider: string): string => {
  try {
    const saved = localStorage.getItem('platform_apiKeys');
    if (!saved) return '';
    const savedKeys: { name: string; apiKey: string }[] = JSON.parse(saved);
    return savedKeys.find(item => item.name === provider)?.apiKey || '';
  } catch {
    return '';
  }
};

const isValidUrl = (url: string): boolean => {
  try {
    const urlObj = new URL(url);
    return urlObj.protocol === 'http:' || urlObj.protocol === 'https:';
  } catch {
    return false;
  }
};

const AiPlayground: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  const [apiKey, setApiKey] = useState('');
  const [baseUrl, setBaseUrl] = useState('http://localhost:8080');
  const [modelProvider, setModelProvider] = useState('openai');
  const [modelName, setModelName] = useState('gpt-4o');
  const [imageModelProvider, setImageModelProvider] = useState('gemini');
  const [imageModelName, setImageModelName] = useState('imagen-3.0-generate-002');
  const [imageProviderKey, setImageProviderKey] = useState('');
  const [conversationId, setConversationId] = useState<string | null>(null);
  const [previousResponseId, setPreviousResponseId] = useState<string | null>(null);
  const [selectedVectorStore, setSelectedVectorStore] = useState<string>('');
  const [instructions, setInstructions] = useState('');
  
  // Configuration parameters for AI model
  const [temperature, setTemperature] = useState(1.0);
  const [maxTokens, setMaxTokens] = useState(2048);
  const [topP, setTopP] = useState(1.0);
  const [storeLogs, setStoreLogs] = useState(true);
  const [textFormat, setTextFormat] = useState<'text' | 'json_object' | 'json_schema'>('text');
  const [toolChoice, setToolChoice] = useState<'auto' | 'none'>('auto');
  
  
  // Prompt messages state
  const [promptMessages, setPromptMessages] = useState<PromptMessage[]>([]);
  
  // Tools state
  const [selectedTools, setSelectedTools] = useState<Tool[]>([]);
  
  // Playground state
  const [activeTab, setActiveTab] = useState('responses');
  const [apiKeysModalOpen, setApiKeysModalOpen] = useState(false);
  const [e2bModalOpen, setE2bModalOpen] = useState(false);
  
  const [jsonSchemaContent, setJsonSchemaContent] = useState('');
  const [jsonSchemaName, setJsonSchemaName] = useState<string | null>(null);

  // Masaic Mocky mode state
  const [mockyMode, setMockyMode] = useState(false);
  const [mockyAgentData, setMockyAgentData] = useState<null | { systemPrompt: string; greetingMessage: string; description: string; tools: any[] }>(null);

  // Model Test Agent mode state
  const [modelTestMode, setModelTestMode] = useState(false);

  // OAuth MCP modal state
  const [oauthMcpConfig, setOauthMcpConfig] = useState<{
    serverUrl: string;
    serverLabel: string;
    accessToken?: string;
    errorMessage?: string;
  } | null>(null);

  // Debug OAuth config changes
  useEffect(() => {
    console.log('AiPlayground: oauthMcpConfig changed:', oauthMcpConfig);
  }, [oauthMcpConfig]);
  const [modelTestAgentData, setModelTestAgentData] = useState<null | { systemPrompt: string; greetingMessage: string; userMessage: string; tools: any[] }>(null);
  const [modelTestUrl, setModelTestUrl] = useState('');
  const [modelTestName, setModelTestName] = useState('');
  const [modelTestApiKey, setModelTestApiKey] = useState('');
  const [isTestingModel, setIsTestingModel] = useState(false);
  const [showSaveModel, setShowSaveModel] = useState(false);
  const [saveModelState, setSaveModelState] = useState<'success' | 'tool_issue' | 'error' | null>(null);

  // Agents mode state
  const [agentMode, setAgentMode] = useState(() => {
    try {
      return localStorage.getItem('platform_agentMode') === 'true';
    } catch {
      return false;
    }
  });
  const [agentData, setAgentData] = useState<null | { name: string; description: string; systemPrompt: string; tools: any[]; model?: string; temperature?: number; maxTokenOutput?: number; topP?: number; suggestedQueries?: string[]; }>(() => {
    try {
      const saved = localStorage.getItem('platform_agentData');
      return saved ? JSON.parse(saved) : null;
    } catch {
      return null;
    }
  });


  // Suggested queries state
  const [suggestedQueries, setSuggestedQueries] = useState<string[]>([]);

  // Chat header state
  const [copiedResponseId, setCopiedResponseId] = useState(false);

  // Restore agent state on page load
  useEffect(() => {
    // Generate sessionId if it doesn't exist
    let sessionId = localStorage.getItem('platform_sessionId');
    if (!sessionId) {
      // Generate new random sessionId: 12 alphanumeric chars
      const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
      sessionId = Array.from({length: 12}, () => chars.charAt(Math.floor(Math.random() * chars.length))).join('');
      localStorage.setItem('platform_sessionId', sessionId);
      console.log('Generated new sessionId:', sessionId);
    }

    if (agentMode && agentData) {
      // Restore configuration from persisted agent data
      setInstructions(agentData.systemPrompt || '');
      if (agentData.temperature !== undefined) setTemperature(agentData.temperature);
      if (agentData.maxTokenOutput !== undefined) setMaxTokens(agentData.maxTokenOutput);
      if (agentData.topP !== undefined) setTopP(agentData.topP);

      // Restore suggested queries
      if (agentData.suggestedQueries) {
        setSuggestedQueries(agentData.suggestedQueries);
      }

      // Restore model
      if (agentData.model) {
        validateAndSetAgentModel(agentData.model);
      }
      
      // Transform and restore tools
      if (agentData.tools && agentData.tools.length > 0) {
        const transformedTools = transformAgentToolsToUI(agentData.tools);
        setSelectedTools(transformedTools);
      }
      
      // Set the active tab to responses
      setActiveTab('responses');
    }
  }, []); // Only run on mount
  
  // Code snippet generator state
  const [codeModalOpen, setCodeModalOpen] = useState(false);
  const [lastRequest, setLastRequest] = useState<PlaygroundRequest | null>(null);
  
  // Import API_URL from central config
  const apiUrl = API_URL;

  const newChatRef = useRef<ResponsesChatRef>(null);

  // Handle OAuth MCP return flow
  useEffect(() => {
    const screen = searchParams.get('screen');
    const modal = searchParams.get('modal');
    const serverUrl = searchParams.get('serverUrl');
    const serverLabel = searchParams.get('serverLabel');
    const accessToken = searchParams.get('accessToken');
    const errorMessage = searchParams.get('errorMessage');

    console.log('OAuth MCP URL params:', { screen, modal, serverUrl, serverLabel, accessToken, errorMessage });

    if (screen === 'playground' && modal === 'mcp' && serverUrl && serverLabel) {
      console.log('Setting OAuth MCP config:', { serverUrl, serverLabel, accessToken, errorMessage });
      setOauthMcpConfig({
        serverUrl,
        serverLabel,
        accessToken: accessToken || undefined,
        errorMessage: errorMessage || undefined
      });

      // Don't clear URL parameters immediately - let the modal handle its lifecycle
      // The URL will be cleaned up when the modal closes or connects successfully
    }
  }, [searchParams, setSearchParams]);

  const { platformInfo } = usePlatformInfo();
  const modelSettings = platformInfo?.modelSettings;

  // Helper function to generate new sessionId
  const generateNewSessionId = () => {
    const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
    const newSessionId = Array.from({length: 12}, () => chars.charAt(Math.floor(Math.random() * chars.length))).join('');
    localStorage.setItem('platform_sessionId', newSessionId);
    console.log('Generated new sessionId:', newSessionId);
    return newSessionId;
  };

  useEffect(() => {
    // Load saved settings from localStorage
    const savedBaseUrl = localStorage.getItem('aiPlayground_baseUrl') || 'http://localhost:8080';
    const savedModelProvider = localStorage.getItem('platform_modelProvider') || 'openai';
    const savedModelName = localStorage.getItem('platform_modelName') || 'gpt-4o';
    const savedImageModelProvider = localStorage.getItem('aiPlayground_imageModelProvider') || 'gemini';
    const savedImageModelName = localStorage.getItem('aiPlayground_imageModelName') || 'imagen-3.0-generate-002';
    const savedImageProviderKey = localStorage.getItem('aiPlayground_imageProviderKey') || '';
    const savedSelectedVectorStore = localStorage.getItem('aiPlayground_selectedVectorStore') || '';
    const savedInstructions = localStorage.getItem('platform_instructions') || '';
    const savedTemperature = parseFloat(localStorage.getItem('aiPlayground_temperature') || '1.0');
    const savedMaxTokens = parseInt(localStorage.getItem('aiPlayground_maxTokens') || '2048');
    const savedTopP = parseFloat(localStorage.getItem('aiPlayground_topP') || '1.0');
    const savedStoreLogs = localStorage.getItem('aiPlayground_storeLogs') === 'true';
    const savedTextFormat = (localStorage.getItem('aiPlayground_textFormat') || 'text') as 'text' | 'json_object' | 'json_schema';
    const savedToolChoice = (localStorage.getItem('aiPlayground_toolChoice') || 'auto') as 'auto' | 'none';
    const savedPromptMessages = JSON.parse(localStorage.getItem('aiPlayground_promptMessages') || '[]');
    const savedOtherToolsRaw = JSON.parse(localStorage.getItem('aiPlayground_otherTools') || '[]');

    // Helper to map tool id to its icon component
    const getIconForTool = (id: string) => {
      switch (id) {
        case 'image_generation':
          return Image;
        case 'think':
          return Brain;
        case 'fun_req_gathering_tool':
          return Puzzle;
        case 'fun_def_generation_tool':
          return Code;
        case 'mock_fun_save_tool':
          return Save;
        case 'mock_generation_tool':
          return Layers;
        case 'mock_save_tool':
          return Save;
        default:
          return Code; // fallback
      }
    };

    const savedOtherTools = savedOtherToolsRaw.map((tool: any) => ({
      ...tool,
      icon: getIconForTool(tool.id)
    }));
    const savedMCPTools = loadMCPToolsFromStorage();
    const savedFileSearchTools = loadFileSearchToolsFromStorage();
    const savedAgenticFileSearchTools = loadAgenticFileSearchToolsFromStorage();
    const savedPyFunctionTools = loadPyFunctionToolsFromStorage();
    
    // Don't load apiKey from localStorage - it's managed by getProviderApiKey function
    setBaseUrl(savedBaseUrl);
    setModelProvider(savedModelProvider);
    setModelName(savedModelName);
    setImageModelProvider(savedImageModelProvider);
    setImageModelName(savedImageModelName);
    setImageProviderKey(savedImageProviderKey);
    setSelectedVectorStore(savedSelectedVectorStore);
    setInstructions(savedInstructions);
    setTemperature(savedTemperature);
    setMaxTokens(savedMaxTokens);
    setTopP(savedTopP);
    setStoreLogs(savedStoreLogs);
    setTextFormat(savedTextFormat);
    setToolChoice(savedToolChoice);
    setPromptMessages(savedPromptMessages);
    setSelectedTools([...savedOtherTools, ...savedMCPTools, ...savedFileSearchTools, ...savedAgenticFileSearchTools, ...savedPyFunctionTools]);
  }, []);

  // Save settings to localStorage whenever they change
  useEffect(() => {
    // Don't save platform_apiKeys here - it's managed by ApiKeysModal
    localStorage.setItem('aiPlayground_baseUrl', baseUrl);
    localStorage.setItem('platform_modelProvider', modelProvider);
    localStorage.setItem('platform_modelName', modelName);
    localStorage.setItem('aiPlayground_imageModelProvider', imageModelProvider);
    localStorage.setItem('aiPlayground_imageModelName', imageModelName);
    localStorage.setItem('aiPlayground_imageProviderKey', imageProviderKey);
    localStorage.setItem('aiPlayground_selectedVectorStore', selectedVectorStore);
    localStorage.setItem('platform_instructions', instructions);
    localStorage.setItem('aiPlayground_temperature', temperature.toString());
    localStorage.setItem('aiPlayground_maxTokens', maxTokens.toString());
    localStorage.setItem('aiPlayground_topP', topP.toString());
    localStorage.setItem('aiPlayground_storeLogs', storeLogs.toString());
    localStorage.setItem('aiPlayground_textFormat', textFormat);
    localStorage.setItem('aiPlayground_toolChoice', toolChoice);
    localStorage.setItem('aiPlayground_promptMessages', JSON.stringify(promptMessages));
    
    // Separate tools by type for better management
    const mcpTools = selectedTools.filter(tool => tool.id === 'mcp_server');
    const fileSearchTools = selectedTools.filter(tool => tool.id === 'file_search');
    const agenticFileSearchTools = selectedTools.filter(tool => tool.id === 'agentic_file_search');
    const pyFunctionTools = selectedTools.filter(tool => tool.id === 'py_fun_tool');
    const otherTools = selectedTools.filter(tool => 
      tool.id !== 'mcp_server' && 
      tool.id !== 'file_search' && 
      tool.id !== 'agentic_file_search' &&
      tool.id !== 'py_fun_tool'
    );
    
    saveMCPToolsToStorage(mcpTools);
    saveFileSearchToolsToStorage(fileSearchTools);
    saveAgenticFileSearchToolsToStorage(agenticFileSearchTools);
    savePyFunctionToolsToStorage(pyFunctionTools);
    localStorage.setItem('aiPlayground_otherTools', JSON.stringify(otherTools));
  }, [apiKey, baseUrl, modelProvider, modelName, imageModelProvider, imageModelName, imageProviderKey, selectedVectorStore, instructions, temperature, maxTokens, topP, storeLogs, textFormat, toolChoice, promptMessages, selectedTools]);


  const resetConversation = () => {
    if (newChatRef.current) {
      // Use new chat's reset function
      newChatRef.current.resetConversation();
    }
    
    toast.success('Conversation reset');
  };

  // Function to clear all localStorage data (for testing/debugging)
  const clearAllStorageData = () => {
    const keys = Object.keys(localStorage).filter(key => 
      key.startsWith('aiPlayground_') || key.startsWith('platform_')
    );
    keys.forEach(key => localStorage.removeItem(key));
    toast.success('All storage data cleared');
  };

  const handleVectorStoreSelect = (vectorStoreId: string | null) => {
    setSelectedVectorStore(vectorStoreId || '');
    localStorage.setItem('aiPlayground_selectedVectorStore', vectorStoreId || '');
    
    if (vectorStoreId) {
      toast.success('Vector store selected for file search');
    } else {
      toast.success('Vector store deselected');
    }
  };

  const handleAddPromptMessage = (message: PromptMessage) => {
    setPromptMessages(prev => [...prev, message]);
    toast.success(`${message.role === 'user' ? 'User' : 'Assistant'} message added to prompt`);
  };

  const handleRemovePromptMessage = (id: string) => {
    setPromptMessages(prev => prev.filter(msg => msg.id !== id));
    toast.success('Message removed from prompt');
  };

  const handleCopyResponseId = async () => {
    if (!previousResponseId) return;
    
    try {
      await navigator.clipboard.writeText(previousResponseId);
      setCopiedResponseId(true);
      setTimeout(() => setCopiedResponseId(false), 2000);
      toast.success('Response ID copied to clipboard');
    } catch (err) {
      console.error('Failed to copy response ID:', err);
      toast.error('Failed to copy response ID');
    }
  };

  // Enhanced MCP tools persistence using label as key
  const saveMCPToolsToStorage = (tools: Tool[]) => {
    const mcpTools = tools.filter(tool => tool.id === 'mcp_server' && tool.mcpConfig?.label);
    const mcpToolsMap: Record<string, any> = {};
    
    mcpTools.forEach(tool => {
      const label = tool.mcpConfig.label;
      mcpToolsMap[label] = {
        label: tool.mcpConfig.label,
        url: tool.mcpConfig.url,
        authentication: tool.mcpConfig.authentication,
        accessToken: tool.mcpConfig.accessToken,
        customHeaders: tool.mcpConfig.customHeaders,
        selectedTools: tool.mcpConfig.selectedTools
      };
    });
    
    localStorage.setItem('platform_mcpTools', JSON.stringify(mcpToolsMap));
  };

  const loadMCPToolsFromStorage = (): Tool[] => {
    try {
      const stored = localStorage.getItem('platform_mcpTools');
      if (!stored) return [];
      
      const mcpToolsMap = JSON.parse(stored);
      const mcpTools: Tool[] = [];
      
      Object.values(mcpToolsMap).forEach((config: any) => {
        if (config.label) {
          mcpTools.push({
            id: 'mcp_server',
            name: config.label,
            icon: () => null, // Icon will be set by the component
            mcpConfig: config
          });
        }
      });
      
      return mcpTools;
    } catch (error) {
      console.error('Error loading MCP tools from storage:', error);
      return [];
    }
  };

  const getMCPToolByLabel = (label: string) => {
    try {
      const stored = localStorage.getItem('platform_mcpTools');
      if (!stored) return null;
      
      const mcpToolsMap = JSON.parse(stored);
      return mcpToolsMap[label] || null;
    } catch (error) {
      console.error('Error getting MCP tool by label:', error);
      return null;
    }
  };

  // File search tools persistence
  const loadFileSearchToolsFromStorage = (): Tool[] => {
    try {
      const stored = localStorage.getItem('platform_fileSearchTools');
      if (!stored) return [];
      
      const fileSearchToolsMap = JSON.parse(stored);
      const fileSearchTools: Tool[] = [];
      
      Object.values(fileSearchToolsMap).forEach((config: any) => {
        if (config.selectedVectorStores && config.vectorStoreNames && config.selectedVectorStores.length > 0) {
          const displayName = config.vectorStoreNames.join(', ');
          fileSearchTools.push({
            id: 'file_search',
            name: displayName,
            icon: () => null, // Icon will be set by the component
            fileSearchConfig: {
              selectedFiles: config.selectedFiles,
              selectedVectorStores: config.selectedVectorStores,
              vectorStoreNames: config.vectorStoreNames
            }
          });
        }
      });
      
      return fileSearchTools;
    } catch (error) {
      console.error('Error loading file search tools from storage:', error);
      return [];
    }
  };

  // Agentic file search tools persistence
  const loadAgenticFileSearchToolsFromStorage = (): Tool[] => {
    try {
      const stored = localStorage.getItem('platform_agenticFileSearchTools');
      if (!stored) return [];
      
      const agenticFileSearchToolsMap = JSON.parse(stored);
      const agenticFileSearchTools: Tool[] = [];
      
      Object.values(agenticFileSearchToolsMap).forEach((config: any) => {
        if (config.selectedVectorStores && config.vectorStoreNames && config.selectedVectorStores.length > 0) {
          const displayName = config.vectorStoreNames.join(', ');
          agenticFileSearchTools.push({
            id: 'agentic_file_search',
            name: displayName,
            icon: () => null, // Icon will be set by the component
            agenticFileSearchConfig: {
              selectedFiles: config.selectedFiles,
              selectedVectorStores: config.selectedVectorStores,
              vectorStoreNames: config.vectorStoreNames,
              iterations: config.iterations,
              maxResults: config.maxResults || 4 // Default to 4 if not set
            }
          });
        }
      });
      
      return agenticFileSearchTools;
    } catch (error) {
      console.error('Error loading agentic file search tools from storage:', error);
      return [];
    }
  };

  const saveFileSearchToolsToStorage = (tools: Tool[]) => {
    const fileSearchTools = tools.filter(tool => tool.id === 'file_search' && tool.fileSearchConfig?.selectedVectorStores && tool.fileSearchConfig.selectedVectorStores.length > 0);
    const fileSearchToolsMap: Record<string, any> = {};
    
    fileSearchTools.forEach(tool => {
      const combinedKey = tool.fileSearchConfig!.selectedVectorStores.sort().join('|');
      fileSearchToolsMap[combinedKey] = {
        selectedFiles: tool.fileSearchConfig!.selectedFiles,
        selectedVectorStores: tool.fileSearchConfig!.selectedVectorStores,
        vectorStoreNames: tool.fileSearchConfig!.vectorStoreNames,
        lastUpdated: new Date().toISOString()
      };
    });
    
    localStorage.setItem('platform_fileSearchTools', JSON.stringify(fileSearchToolsMap));
  };

  const saveAgenticFileSearchToolsToStorage = (tools: Tool[]) => {
    const agenticFileSearchTools = tools.filter(tool => tool.id === 'agentic_file_search' && tool.agenticFileSearchConfig?.selectedVectorStores && tool.agenticFileSearchConfig.selectedVectorStores.length > 0);
    const agenticFileSearchToolsMap: Record<string, any> = {};
    
    agenticFileSearchTools.forEach(tool => {
      const combinedKey = tool.agenticFileSearchConfig!.selectedVectorStores.sort().join('|');
      agenticFileSearchToolsMap[combinedKey] = {
        selectedFiles: tool.agenticFileSearchConfig!.selectedFiles,
        selectedVectorStores: tool.agenticFileSearchConfig!.selectedVectorStores,
        vectorStoreNames: tool.agenticFileSearchConfig!.vectorStoreNames,
        iterations: tool.agenticFileSearchConfig!.iterations,
        maxResults: tool.agenticFileSearchConfig!.maxResults,
        lastUpdated: new Date().toISOString()
      };
    });
    
    localStorage.setItem('platform_agenticFileSearchTools', JSON.stringify(agenticFileSearchToolsMap));
  };

  // Py function tools persistence
  const loadPyFunctionToolsFromStorage = (): Tool[] => {
    try {
      const stored = localStorage.getItem('platform_py_fun_tools');
      if (!stored) return [];
      
      const pyFunctionToolsMap = JSON.parse(stored);
      const pyFunctionTools: Tool[] = [];
      
      // Handle both array and object formats for backward compatibility
      if (Array.isArray(pyFunctionToolsMap)) {
        // Old array format - convert to new format
        pyFunctionToolsMap.forEach((tool: any) => {
          pyFunctionTools.push({
            id: 'py_fun_tool',
            name: tool.tool_def.name,
            icon: Code,
            pyFunctionConfig: tool
          });
        });
      } else {
        // New object format
        Object.values(pyFunctionToolsMap).forEach((tool: any) => {
          pyFunctionTools.push({
            id: 'py_fun_tool',
            name: tool.tool_def.name,
            icon: Code,
            pyFunctionConfig: tool
          });
        });
      }
      
      return pyFunctionTools;
    } catch (error) {
      console.error('Error loading Py function tools from storage:', error);
      return [];
    }
  };

  const savePyFunctionToolsToStorage = (tools: Tool[]) => {
    const pyFunctionTools = tools.filter(tool => tool.id === 'py_fun_tool' && tool.pyFunctionConfig);
    const pyFunctionToolsMap: Record<string, any> = {};
    
    pyFunctionTools.forEach(tool => {
      const functionName = tool.pyFunctionConfig!.tool_def.name;
      pyFunctionToolsMap[functionName] = tool.pyFunctionConfig;
    });
    
    localStorage.setItem('platform_py_fun_tools', JSON.stringify(pyFunctionToolsMap));
  };

  // REMOVED: Old chat generateResponse function (was ~900 lines of legacy code)

  // Retry logic for error messages
  // REMOVED: handleRetry - part of old chat implementation

  // REMOVED: handleSubmit - part of old chat implementation

  // REMOVED: handleKeyPress - part of old chat implementation

  // REMOVED: handleSuggestedQuerySelect - part of old chat implementation

  // REMOVED: handleTextareaChange - part of old chat implementation

  // Helper function to transform agent tools to UI tool format
  const transformAgentToolsToUI = (agentTools: any[]): Tool[] => {
    return agentTools.map(tool => {
      if (tool.type === 'mcp') {
        return {
          id: 'mcp_server',
          name: `MCP: ${tool.server_label}`,
          icon: Brain,
          mcpConfig: {
            label: tool.server_label,
            url: tool.server_url,
            selectedTools: tool.allowed_tools || [],
            authentication: tool.headers && Object.keys(tool.headers).length > 0 ? 'access_token' : 'none',
            accessToken: tool.headers?.Authorization?.replace('Bearer ', '') || '',
            customHeaders: []
          }
        };
      } else if (tool.type === 'file_search') {
        return {
          id: 'file_search',
          name: 'File Search',
          icon: Search,
          fileSearchConfig: {
            selectedFiles: [],
            selectedVectorStores: tool.vector_store_ids || [],
            vectorStoreNames: []
          }
        };
      } else if (tool.type === 'agentic_search') {
        return {
          id: 'agentic_file_search',
          name: 'Agentic File Search',
          icon: Brain,
          agenticFileSearchConfig: {
            selectedFiles: [],
            selectedVectorStores: tool.vector_store_ids || [],
            vectorStoreNames: [],
            iterations: tool.max_iterations || 3,
            maxResults: tool.max_num_results || 5
          }
        };
      } else if (tool.type === 'py_fun_tool') {
        return {
          id: 'py_fun_tool',
          name: `Function: ${tool.tool_def?.name || 'Python Function'}`,
          icon: Code,
          pyFunctionConfig: tool
        };
      }
      // Handle other tool types with their default configurations
      return {
        id: tool.type,
        name: tool.type.replace(/_/g, ' ').replace(/\b\w/g, l => l.toUpperCase()),
        icon: Puzzle
      };
    }).filter(tool => tool !== null);
  };

  // Helper function to validate and set model from agent data using the same logic as ConfigurationPanel
  const validateAndSetAgentModel = async (agentModelName?: string) => {
    console.log(`validateAndSetAgentModel called with:`, agentModelName);

    if (!agentModelName) {
      console.log('No agent model name provided, clearing selection');
      // Clear model selection if no model specified
      setModelProvider('');
      setModelName('');
      return;
    }

    try {
      // Use the same API call as ConfigurationPanel to get available models
      const providers = await apiClient.jsonRequest<any[]>('/v1/dashboard/models');
      
      // Build allModels array using the same logic as ConfigurationPanel
      const apiModels = providers.flatMap(provider =>
        (provider.supportedModels || []) // Handle providers without supportedModels
          .filter((model: any) => !model.isEmbeddingModel) // Filter out embedding models
          .map((model: any) => ({
            ...model,
            providerName: provider.name,
            providerDescription: provider.description
          }))
      );

      // Get models from localStorage (own models) - same as ConfigurationPanel
      let ownModels: any[] = [];
      try {
        const savedOwnModels = localStorage.getItem('platform_own_model');
        if (savedOwnModels) {
          const ownModelsData = JSON.parse(savedOwnModels);
          ownModels = ownModelsData.supportedModels.map((model: any) => ({
            name: model.name,
            modelSyntax: model.modelSyntax,
            providerName: ownModelsData.name,
            providerDescription: ownModelsData.description,
            isEmbeddingModel: false
          }));
        }
      } catch (error) {
        console.error('Error loading own models from localStorage:', error);
      }

      // Combine own models and API models (same as ConfigurationPanel)
      const allModels = [...ownModels, ...apiModels];

      // Find the agent's model in the available models
      const foundModel = allModels.find((model: any) => {
        const modelSyntaxMatch = model.modelSyntax === agentModelName;
        const nameMatch = model.name === agentModelName;
        const trimmedModelSyntaxMatch = model.modelSyntax?.trim() === agentModelName?.trim();
        const trimmedNameMatch = model.name?.trim() === agentModelName?.trim();

        return modelSyntaxMatch || nameMatch || trimmedModelSyntaxMatch || trimmedNameMatch;
      });

      console.log(`Model search result:`, { agentModelName, found: !!foundModel, model: foundModel });

      if (foundModel) {
        // Extract provider and model name from modelSyntax
        if (foundModel.modelSyntax && foundModel.modelSyntax.includes('@')) {
          const [providerName, modelNamePart] = foundModel.modelSyntax.split('@');
          setModelProvider(providerName);
          setModelName(modelNamePart);
        } else {
          setModelProvider(foundModel.providerName || '');
          setModelName(foundModel.name || agentModelName);
        }

      } else {
        console.warn(`Agent model "${agentModelName}" not found in available models. Trying fallback options.`, {
          agentModelName,
          apiModelsCount: apiModels.length,
          firstFewModels: apiModels.slice(0, 3).map(m => ({ name: m.name, modelSyntax: m.modelSyntax }))
        });

        // Step 2: Try to use model from localStorage
        const savedProvider = localStorage.getItem('platform_modelProvider');
        const savedModelName = localStorage.getItem('platform_modelName');

        if (savedProvider && savedModelName) {
          console.log(`Using saved model from localStorage: ${savedProvider}@${savedModelName}`);
          setModelProvider(savedProvider);
          setModelName(savedModelName);
          return;
        }

        // Step 3: Select first model from API and save to localStorage
        if (apiModels.length > 0) {
          const firstApiModel = apiModels[0];
          let selectedProvider = '';
          let selectedModelName = '';

          if (firstApiModel.modelSyntax && firstApiModel.modelSyntax.includes('@')) {
            const [providerName, modelNamePart] = firstApiModel.modelSyntax.split('@');
            selectedProvider = providerName;
            selectedModelName = modelNamePart;
          } else {
            selectedProvider = firstApiModel.providerName || '';
            selectedModelName = firstApiModel.name || '';
          }

          console.log(`Using first API model: ${selectedProvider}@${selectedModelName}`);
          setModelProvider(selectedProvider);
          setModelName(selectedModelName);

          // Save to localStorage for future use
          try {
            localStorage.setItem('platform_modelProvider', selectedProvider);
            localStorage.setItem('platform_modelName', selectedModelName);
          } catch (error) {
            console.error('Failed to save model to localStorage:', error);
          }
        } else {
          // If no API models available, clear selection
          console.warn('No API models available, clearing model selection');
          setModelProvider('');
          setModelName('');
        }
      }
    } catch (error) {
      console.error('Error validating agent model:', error);
      // If API fails, show "Select a model..." by clearing the selection
      setModelProvider('');
      setModelName('');
    }
  };

  const handleAgentSaved = async (agentName: string, agentDescription: string) => {
    try {
      // Fetch the saved agent details from the API
      const agentDetails = await apiClient.agentJsonRequest(`/v1/agents/${agentName}`);
      
      // Use the existing handleAgentSelect logic to switch to agent context
      await handleAgentSelect(agentDetails);
      
    } catch (error) {
      console.error('Error switching to saved agent context:', error);
    }
  };

  const handleAgentSelect = async (agent: any) => {
    try {
      // Reset all other modes
      const resetAllModes = () => {
        setMockyMode(false);
        setMockyAgentData(null);
        setModelTestMode(false);
        setModelTestAgentData(null);
        setModelTestUrl('');
        setModelTestName('');
        setModelTestApiKey('');
        setIsTestingModel(false);
        setShowSaveModel(false);
        setSaveModelState(null);
        setSuggestedQueries([]);
      };

      resetAllModes();

      // Set agent mode and data
      const agentDataValue = {
        name: agent.name,
        description: agent.description,
        systemPrompt: agent.systemPrompt,
        tools: agent.tools || [],
        model: agent.model,
        temperature: agent.temperature,
        maxTokenOutput: agent.maxTokenOutput,
        topP: agent.topP,
        suggestedQueries: agent.suggestedQueries || []
      };
      
      setAgentMode(true);
      setAgentData(agentDataValue);
      setSuggestedQueries(agent.suggestedQueries || []);

      // Persist to localStorage
      try {
        localStorage.setItem('platform_agentMode', 'true');
        localStorage.setItem('platform_agentData', JSON.stringify(agentDataValue));
      } catch (error) {
        console.error('Failed to persist agent data to localStorage:', error);
      }

      // Set instructions from agent system prompt
      setInstructions(agent.systemPrompt || '');

      // Set model parameters from agent data
      setTemperature(agent.temperature || 1.0);
      setMaxTokens(agent.maxTokenOutput || 2048);
      setTopP(agent.topP || 1.0);

      // Validate and set model (only if agent has a model specified)
      if (agent.model) {
        await validateAndSetAgentModel(agent.model);
      }

      // Transform and set tools
      const transformedTools = transformAgentToolsToUI(agent.tools || []);
      setSelectedTools(transformedTools);

      // Reset conversation state
      if (newChatRef.current) {
        newChatRef.current.resetConversation();
      }
      
      setConversationId(null);
      setPreviousResponseId(null);
      
      // Generate new sessionId for new agent conversation
      generateNewSessionId();

      // Switch to responses tab
      setActiveTab('responses');

      toast.success(`Agent "${agent.name}" loaded successfully`);
    } catch (error) {
      console.error('Error setting up agent:', error);
      toast.error('Failed to setup agent');
    }
  };


  const handleNewAgentBuilder = () => {
    navigate('/agent-builder');
  };


  const handleTabChange = (tab: string) => {
    // First, always reset any active special modes
    const resetMockyMode = () => {
      if (mockyMode) {
        setMockyMode(false);
        setMockyAgentData(null);
        
        // Reset conversation
        if (newChatRef.current) {
          newChatRef.current.resetConversation();
        }
        
        setConversationId(null);
        setPreviousResponseId(null);
        generateNewSessionId(); // Generate new sessionId for new conversation
      }
    };

    const resetModelTestMode = () => {
      if (modelTestMode) {
        setModelTestMode(false);
        setModelTestAgentData(null);
        setModelTestUrl('');
        setModelTestName('');
        setModelTestApiKey('');
        setIsTestingModel(false);
        setShowSaveModel(false);
        setSaveModelState(null);
        
        // Reset conversation
        if (newChatRef.current) {
          newChatRef.current.resetConversation();
        }
        
        setConversationId(null);
        setPreviousResponseId(null);
        generateNewSessionId(); // Generate new sessionId for new conversation
      }
    };

    const resetAgentMode = () => {
      if (agentMode) {
        setAgentMode(false);
        setAgentData(null);
        
        // Reset conversation
        if (newChatRef.current) {
          newChatRef.current.resetConversation();
        }
        
        setConversationId(null);
        setPreviousResponseId(null);
        generateNewSessionId(); // Generate new sessionId for new conversation
        
        // Clear from localStorage
        try {
          localStorage.removeItem('platform_agentMode');
          localStorage.removeItem('platform_agentData');
        } catch (error) {
          console.error('Failed to clear agent data from localStorage', error);
        }
      }
    };


    // Special handling for Masaic Mocky option
    if (tab === 'masaic-mocky') {
      // Reset other modes first if active
      resetModelTestMode();
      resetAgentMode();
      
      setActiveTab(tab);
      
      // Fetch agent definition with proper async/await
      (async () => {
        try {
          const data = await apiClient.agentJsonRequest<any>('/v1/agents/Masaic-Mocky');
          
          if (data) {
            setMockyMode(true);
            setMockyAgentData({
              systemPrompt: data.systemPrompt || '',
              greetingMessage: data.greetingMessage || '',
              description: data.description || '',
              tools: data.tools || []
            });

            // reset conversation tracking ids
            setConversationId(null);
            setPreviousResponseId(null);

            // Reset previous conversation handled by new chat
            
            // Generate new sessionId for new Masaic Mocky conversation
            generateNewSessionId();

            // Handle greeting message
            if (newChatRef.current) {
              // Use new chat's streaming greeting
              newChatRef.current.streamGreetingMessage(data.greetingMessage || '');
            }
          } else {
            toast.error('Failed to load Masaic Mocky agent data');
          }
        } catch (err) {
          console.error(err);
          toast.error('Failed to load Masaic Mocky agent data');
        }
      })();
      return;
    }


    // Special handling for Add Model option
    if (tab === 'add-model') {
      // Reset other modes first if active
      resetMockyMode();
      resetAgentMode();
      
      setActiveTab(tab);
      
      // Fetch ModelTestAgent definition with proper async/await
      (async () => {
        try {
          const data = await apiClient.agentJsonRequest<any>('/v1/agents/ModelTestAgent');
          
          if (data) {
            setModelTestMode(true);
            setModelTestAgentData({
              systemPrompt: data.systemPrompt || '',
              greetingMessage: data.greetingMessage || '',
              userMessage: data.userMessage || '',
              tools: data.tools || []
            });

            // Reset form fields
            setModelTestUrl('');
            setModelTestName('');
            setModelTestApiKey('');
            setIsTestingModel(false);
            setShowSaveModel(false);

            // reset conversation tracking ids
            setConversationId(null);
            setPreviousResponseId(null);

            // Reset previous conversation
            if (newChatRef.current) {
              newChatRef.current.resetConversation();
            }
            
            // Generate new sessionId for new Model Test conversation
            generateNewSessionId();
          } else {
            toast.error('Failed to load Model Test Agent data');
          }
        } catch (err) {
          console.error(err);
          toast.error('Failed to load Model Test Agent data');
        }
      })();
      return;
    }

    // Special handling for E2B Server option
    if (tab === 'e2b-server') {
      // Reset all modes first if active
      resetMockyMode();
      resetModelTestMode();
      resetAgentMode();
      
      setActiveTab('responses');
      setE2bModalOpen(true);
      return;
    }

    // For any other tab, reset all modes
    resetMockyMode();
    resetModelTestMode();
    resetAgentMode();

    setActiveTab(tab);
    // Handle API Keys tab by opening the API keys modal
    if (tab === 'api-keys') {
      setApiKeysModalOpen(true);
      // Reset to previous tab since API Keys is a modal action, not a tab
      setActiveTab('responses');
    }
  };

  const handleTestModelConnectivity = async () => {
    // Validation
    if (!modelTestUrl.trim()) {
      toast.error('Please enter a model URL');
      return;
    }
    
    if (!isValidUrl(modelTestUrl.trim())) {
      toast.error('Please enter a valid URL starting with http:// or https://');
      return;
    }
    
    if (!modelTestName.trim()) {
      toast.error('Please enter a model name');
      return;
    }
    
    if (!modelTestApiKey.trim()) {
      toast.error('Please enter an API key');
      return;
    }

    if (!modelTestAgentData) {
      toast.error('Model Test Agent data not loaded');
      return;
    }

    setIsTestingModel(true);
    setShowSaveModel(false);
    setSaveModelState(null);

    // Reset conversation state like Reset Chat CTA
    if (newChatRef.current) {
      // Use new chat's reset function
      newChatRef.current.resetConversation();
    }
    
      setConversationId(null);
      setPreviousResponseId(null);
    
    // Generate new sessionId for new model test conversation
    generateNewSessionId();

    // Handle greeting and user message streaming
      const greetingText: string = modelTestAgentData.greetingMessage || '';
      const userText: string = modelTestAgentData.userMessage || '';
      
      if (newChatRef.current) {
        // Stream greeting first
        newChatRef.current.streamGreetingMessage(greetingText);
        
        // After greeting completes, send user message directly which will trigger API call
        setTimeout(() => {
          if (newChatRef.current) {
            // Use sendTextMessage directly - this will add the user message and make the API call
            newChatRef.current.sendTextMessage(userText).catch((error) => {
              console.error('Error in new chat model test call:', error);
              setIsTestingModel(false);
              toast.error('Failed to test model connectivity');
            });
          }
        }, (greetingText.length * 25) + 1000);
      }
  };

  // REMOVED: updateMessagesForModelTest - not needed with new chat implementation

  const makeModelTestApiCall = async () => {
    console.log('makeModelTestApiCall called', { modelTestAgentData });
    if (!modelTestAgentData || !newChatRef.current) return;

    // Check if model test fields are filled
    if (!modelTestName || modelTestName.trim() === '') {
      toast.error('Please enter a model name for testing.');
      setIsTestingModel(false);
      return;
    }

    if (!modelTestUrl || modelTestUrl.trim() === '') {
      toast.error('Please enter a model URL for testing.');
      setIsTestingModel(false);
      return;
    }

    if (!modelTestApiKey || modelTestApiKey.trim() === '') {
      toast.error('Please enter an API key for testing.');
      setIsTestingModel(false);
      return;
    }

    // Use the new chat implementation which is already configured with model test settings
    const userText = modelTestAgentData.userMessage || '';
    
    try {
      await newChatRef.current.sendTextMessage(userText);
    } catch (error) {
      console.error('Error in model test API call:', error);
        setIsTestingModel(false);
      toast.error('Failed to test model connectivity');
    }

  };

  // LIFT AND SHIFT: Complete new chat implementation for model test mode
  const makeNewChatModelTestCall = async () => {
    if (!modelTestAgentData || !newChatRef.current) return;

    // Check if model test fields are filled (EXACT COPY from old)
    if (!modelTestName || modelTestName.trim() === '') {
      toast.error('Please enter a model name for testing.');
      setIsTestingModel(false);
      return;
    }

    if (!modelTestUrl || modelTestUrl.trim() === '') {
      toast.error('Please enter a model URL for testing.');
      setIsTestingModel(false);
      return;
    }

    if (!modelTestApiKey || modelTestApiKey.trim() === '') {
      toast.error('Please enter an API key for testing.');
      setIsTestingModel(false);
      return;
    }

    // Now that the new chat is properly configured with model test settings,
    // we can simply use sendTextMessage and it will work correctly!
    const userText = modelTestAgentData.userMessage || '';
    
    try {
      await newChatRef.current.sendTextMessage(userText);
    } catch (error) {
      console.error('Error in new chat model test call:', error);
      setIsTestingModel(false);
      toast.error('Failed to test model connectivity');
    }
  };

  const handleSaveModel = () => {
    if (!modelTestUrl.trim() || !modelTestName.trim() || !modelTestApiKey.trim()) {
      toast.error('Missing model information');
      return;
    }

    try {
      // 1. Update platform_own_model in localStorage
      const existingOwnModels = localStorage.getItem('platform_own_model');
      let ownModelsData;
      
      if (existingOwnModels) {
        ownModelsData = JSON.parse(existingOwnModels);
      } else {
        ownModelsData = {
          name: "own model",
          description: "My own models",
          supportedModels: []
        };
      }

      const newModelSyntax = `${modelTestUrl.trim()}@${modelTestName.trim()}`;
      
      // Check if model already exists by modelSyntax (to prevent duplicates)
      const existingModelIndex = ownModelsData.supportedModels.findIndex(
        (model: any) => model.modelSyntax === newModelSyntax
      );

      const newModel = {
        name: modelTestName.trim(),
        modelSyntax: newModelSyntax
      };

      if (existingModelIndex >= 0) {
        // Update existing model
        ownModelsData.supportedModels[existingModelIndex] = newModel;
      } else {
        // Add new model
        ownModelsData.supportedModels.push(newModel);
      }

      localStorage.setItem('platform_own_model', JSON.stringify(ownModelsData));

      // 2. Update platform_apiKeys in localStorage
      const existingApiKeys = localStorage.getItem('platform_apiKeys');
      let apiKeysData = [];
      
      if (existingApiKeys) {
        apiKeysData = JSON.parse(existingApiKeys);
      }

      // Check if API key for this model already exists
      const existingApiKeyIndex = apiKeysData.findIndex(
        (apiKey: any) => apiKey.name === modelTestUrl.trim()
      );

      const newApiKey = {
        name: modelTestUrl.trim(),
        apiKey: modelTestApiKey.trim()
      };

      if (existingApiKeyIndex >= 0) {
        // Update existing API key
        apiKeysData[existingApiKeyIndex] = newApiKey;
      } else {
        // Add new API key
        apiKeysData.push(newApiKey);
      }

      localStorage.setItem('platform_apiKeys', JSON.stringify(apiKeysData));

      toast.success(`Model "${modelTestName.trim()}" saved successfully!`);
      
      // Trigger a refresh of the models list by dispatching a storage event
      window.dispatchEvent(new Event('storage'));
      
      // Reset the form
      setModelTestUrl('');
      setModelTestName('');
      setModelTestApiKey('');
      setShowSaveModel(false);
      setSaveModelState(null);
      
    } catch (error) {
      console.error('Error saving model:', error);
      toast.error('Failed to save model');
    }
  };

  return (
    <>
    <Drawer>
      {/* Mobile Hamburger */}
      <DrawerTrigger asChild>
        <button className="md:hidden fixed top-3 left-3 z-50 p-2 rounded-md bg-background/80 border border-border shadow-sm" aria-label="Open menu">
          <Menu className="h-5 w-5 text-foreground" />
        </button>
      </DrawerTrigger>

      {/* Drawer Content */}
      <DrawerContent className="h-[85vh] overflow-y-auto p-4 space-y-4">
        {/* Sidebar */}
        <PlaygroundSidebar 
          activeTab={activeTab}
          onTabChange={handleTabChange}
          onNewAgentBuilder={handleNewAgentBuilder}
          className="flex flex-col w-full"
        />
        {/* Configuration Panel */}
        <ConfigurationPanel
          modelProvider={modelProvider}
          setModelProvider={setModelProvider}
          modelName={modelName}
          setModelName={setModelName}
          imageModelProvider={imageModelProvider}
          setImageModelProvider={setImageModelProvider}
          imageModelName={imageModelName}
          setImageModelName={setImageModelName}
          imageProviderKey={imageProviderKey}
          setImageProviderKey={setImageProviderKey}
          apiKey={apiKey}
          setApiKey={setApiKey}
          baseUrl={baseUrl}
          setBaseUrl={setBaseUrl}
          temperature={temperature}
          setTemperature={setTemperature}
          maxTokens={maxTokens}
          setMaxTokens={setMaxTokens}
          topP={topP}
          setTopP={setTopP}
          storeLogs={storeLogs}
          setStoreLogs={setStoreLogs}
          textFormat={textFormat}
          setTextFormat={setTextFormat}
          toolChoice={toolChoice}
          setToolChoice={setToolChoice}
          instructions={instructions}
          setInstructions={setInstructions}
          promptMessages={promptMessages}
          onAddPromptMessage={handleAddPromptMessage}
          onRemovePromptMessage={handleRemovePromptMessage}
          selectedTools={selectedTools}
          onSelectedToolsChange={setSelectedTools}
          getMCPToolByLabel={getMCPToolByLabel}
          selectedVectorStore={selectedVectorStore}
          onVectorStoreSelect={handleVectorStoreSelect}
          onResetConversation={resetConversation}
          openApiKeysModal={apiKeysModalOpen}
          onApiKeysModalChange={setApiKeysModalOpen}
          openE2bModal={e2bModalOpen}
          onE2bModalChange={setE2bModalOpen}
          jsonSchemaContent={jsonSchemaContent}
          setJsonSchemaContent={setJsonSchemaContent}
          jsonSchemaName={jsonSchemaName}
          setJsonSchemaName={setJsonSchemaName}
          className="w-full"
          mockyMode={mockyMode}
          modelTestMode={modelTestMode}
          modelTestUrl={modelTestUrl}
          setModelTestUrl={setModelTestUrl}
          modelTestName={modelTestName}
          setModelTestName={setModelTestName}
          modelTestApiKey={modelTestApiKey}
          setModelTestApiKey={setModelTestApiKey}
          onTestModelConnectivity={handleTestModelConnectivity}
          isTestingModel={isTestingModel}
          agentMode={agentMode}
          agentData={agentData}
          onAgentSaved={handleAgentSaved}
          onAgentSelect={handleAgentSelect}
          oauthMcpConfig={oauthMcpConfig}
          onOauthMcpConfigHandled={() => {
            console.log('AiPlayground: onOauthMcpConfigHandled called - clearing config');
            setOauthMcpConfig(null);
          }}
        />
      </DrawerContent>
    </Drawer>

    <div className="flex h-full bg-background">
      {/* Left Sidebar - 10% (desktop only) */}
      <PlaygroundSidebar 
        activeTab={activeTab}
        onTabChange={handleTabChange}
        onAgentSelect={handleAgentSelect}
        onNewAgentBuilder={handleNewAgentBuilder}
        className="hidden md:flex md:flex-col md:w-[10%] md:min-w-[160px]"
      />

      {/* Configuration Panel - 30% (desktop only) */}
      <ConfigurationPanel
        modelProvider={modelProvider}
        setModelProvider={setModelProvider}
        modelName={modelName}
        setModelName={setModelName}
        imageModelProvider={imageModelProvider}
        setImageModelProvider={setImageModelProvider}
        imageModelName={imageModelName}
        setImageModelName={setImageModelName}
        imageProviderKey={imageProviderKey}
        setImageProviderKey={setImageProviderKey}
        apiKey={apiKey}
        setApiKey={setApiKey}
        baseUrl={baseUrl}
        setBaseUrl={setBaseUrl}
        temperature={temperature}
        setTemperature={setTemperature}
        maxTokens={maxTokens}
        setMaxTokens={setMaxTokens}
        topP={topP}
        setTopP={setTopP}
        storeLogs={storeLogs}
        setStoreLogs={setStoreLogs}
        textFormat={textFormat}
        setTextFormat={setTextFormat}
        toolChoice={toolChoice}
        setToolChoice={setToolChoice}
        instructions={instructions}
        setInstructions={setInstructions}
        promptMessages={promptMessages}
        onAddPromptMessage={handleAddPromptMessage}
        onRemovePromptMessage={handleRemovePromptMessage}
        selectedTools={selectedTools}
        onSelectedToolsChange={setSelectedTools}
        getMCPToolByLabel={getMCPToolByLabel}
        selectedVectorStore={selectedVectorStore}
        onVectorStoreSelect={handleVectorStoreSelect}
        onResetConversation={resetConversation}
        openApiKeysModal={apiKeysModalOpen}
        onApiKeysModalChange={setApiKeysModalOpen}
        openE2bModal={e2bModalOpen}
        onE2bModalChange={setE2bModalOpen}
        jsonSchemaContent={jsonSchemaContent}
        setJsonSchemaContent={setJsonSchemaContent}
        jsonSchemaName={jsonSchemaName}
        setJsonSchemaName={setJsonSchemaName}
        className="hidden md:block md:w-[30%]"
        mockyMode={mockyMode}
        modelTestMode={modelTestMode}
        modelTestUrl={modelTestUrl}
        setModelTestUrl={setModelTestUrl}
        modelTestName={modelTestName}
        setModelTestName={setModelTestName}
        modelTestApiKey={modelTestApiKey}
        agentMode={agentMode}
        agentData={agentData}
        onAgentSaved={handleAgentSaved}
        onAgentSelect={handleAgentSelect}
        setModelTestApiKey={setModelTestApiKey}
        onTestModelConnectivity={handleTestModelConnectivity}
        isTestingModel={isTestingModel}
        oauthMcpConfig={oauthMcpConfig}
        onOauthMcpConfigHandled={() => {
          console.log('AiPlayground: onOauthMcpConfigHandled called - clearing config');
          setOauthMcpConfig(null);
        }}
      />

      {/* Chat Area */}
      <div className="flex-1 md:w-[60%] flex flex-col">
        {/* Sticky Header */}
        <div className="sticky top-0 z-10 bg-background/95 backdrop-blur-sm border-b border-border px-6 py-3">
          <div className="flex items-center justify-between w-full">
            {/* Action Buttons - Moved to extreme left */}
            <div className="flex items-center space-x-2">
              {agentMode && agentData && (
                <span className="text-sm text-foreground font-medium px-2 py-1 bg-accent/50 rounded-md">
                  {agentData.name}
                </span>
              )}
              <Button
                variant="ghost"
                size="sm"
                onClick={resetConversation}
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
                disabled={!lastRequest}
                className="flex items-center space-x-2 text-muted-foreground hover:text-foreground hover:bg-muted/50 disabled:opacity-50 disabled:cursor-not-allowed"
                title={lastRequest ? "View code snippets for last request" : "Send a message to generate code snippets"}
              >
                <Code className="w-4 h-4" />
                <span className="text-sm">View Code</span>
              </Button>
            </div>

            {mockyMode && mockyAgentData?.description && (
              <span className="absolute left-1/2 transform -translate-x-1/2 text-xs text-muted-foreground truncate max-w-[60%] text-center">
                {mockyAgentData.description}
              </span>
            )}

            {modelTestMode && (
              <span className="absolute left-1/2 transform -translate-x-1/2 text-xs text-muted-foreground truncate max-w-[60%] text-center">
                Test model connectivity and validate API integration
              </span>
            )}



            {/* Response ID Display - Moved to right */}
            {previousResponseId && (
              <div className="flex items-center space-x-2">
                <span className="text-xs text-muted-foreground">Response ID:</span>
                <div className="flex items-center space-x-1 bg-muted/50 rounded px-2 py-1">
                  <code className="text-xs font-mono text-foreground">
                    {previousResponseId.length > 20 
                      ? `${previousResponseId.substring(0, 20)}...` 
                      : previousResponseId
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

          <ResponsesChat
            ref={newChatRef}
            hookConfig={{
              model: modelTestMode && modelTestUrl && modelTestName ? 
                { provider: modelTestUrl, name: modelTestName } :
                { provider: modelProvider, name: modelName },
              instructions: mockyMode && mockyAgentData ? mockyAgentData.systemPrompt 
                : modelTestMode && modelTestAgentData ? modelTestAgentData.systemPrompt
                : instructions,
              textFormat,
              jsonSchema: textFormat === 'json_schema' ? { 
                name: jsonSchemaName ?? undefined, 
                schema: (() => {
                  try {
                    return jsonSchemaContent ? JSON.parse(jsonSchemaContent).schema : undefined;
                  } catch {
                    return undefined;
                  }
                })()
              } : undefined,
              tools: mockyMode && mockyAgentData && Array.isArray(mockyAgentData.tools) ? mockyAgentData.tools
                : modelTestMode && modelTestAgentData && Array.isArray(modelTestAgentData.tools) ? modelTestAgentData.tools
                : selectedTools.length > 0 ? buildToolsPayload(selectedTools, modelSettings ? { settingsType: modelSettings.settingsType as 'RUNTIME' | 'PLATFORM' } : undefined) : undefined,
              store: storeLogs,
              stream: true,
              headers: modelTestMode && modelTestApiKey ? {
                'Authorization': `Bearer ${modelTestApiKey}`
              } : undefined,
              onEvent: (evt) => {
                console.log('Chat event:', evt);
              },
              // Model test mode callbacks
              modelTestMode,
              onSaveModelStateChange: setSaveModelState,
              onShowSaveModelChange: setShowSaveModel
            }}
            placeholder="Chat with your prompt..."
            apiKey={apiKey}
            baseUrl={baseUrl}
            imageModelProvider={imageModelProvider}
            imageModelName={imageModelName}
            imageProviderKey={imageProviderKey}
            selectedVectorStore={selectedVectorStore}
            suggestedQueries={suggestedQueries}
            agentMode={agentMode}
            previousResponseId={previousResponseId}
            // Pass callbacks to sync state with parent
            onPreviousResponseIdChange={setPreviousResponseId}
            onLastRequestChange={setLastRequest}
          // Model test mode support
            modelTestMode={modelTestMode}
            showSaveModel={showSaveModel}
            saveModelState={saveModelState}
            onSaveModel={handleSaveModel}
          />
      </div>
    </div>
    
    {/* Code Snippets Modal */}
    <CodeTabs
      open={codeModalOpen}
      onOpenChange={setCodeModalOpen}
      lastRequest={lastRequest}
      baseUrl={apiUrl}
    />


    </>
  );
};

export default AiPlayground; 