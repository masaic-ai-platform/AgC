import React, { useState } from 'react';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { Button } from '@/components/ui/button';
import { 
  Code, 
  Search, 
  FileSearch,
  Image,
  Brain,
  Puzzle,
  Save,
  Layers
} from 'lucide-react';
import { MCP } from '@lobehub/icons';
import { usePlatformInfo } from '@/contexts/PlatformContext';
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip';
import FunctionModal from './FunctionModal';
import MCPModal from './MCPModal';
import FileSearchModal from './FileSearchModal';
import AgenticFileSearchModal from './AgenticFileSearchModal';
import PyFunctionToolModal from './PyFunctionToolModal';
import PyFunctionToolSelectionModal from './PyFunctionToolSelectionModal';
import LocalToolModal from './LocalToolModal';
import { toast } from 'sonner';

interface Tool {
  id: string;
  name: string;
  icon: React.ComponentType<{ className?: string }>;
  functionDefinition?: string; // For function tools
  mcpConfig?: any; // For MCP server tools
  fileSearchConfig?: { selectedFiles: string[]; selectedVectorStores: string[]; vectorStoreNames: string[] }; // For file search tools
  agenticFileSearchConfig?: { selectedFiles: string[]; selectedVectorStores: string[]; vectorStoreNames: string[]; iterations: number; maxResults: number }; // For agentic file search tools
  pyFunctionConfig?: any; // For Python function tools
  localToolConfig?: any; // For Local tools
}

interface PyFunctionTool {
  type: string;
  tool_def: {
    name: string;
    description: string;
    parameters: string;
  };
  code: string;
}

interface ToolsSelectionModalProps {
  selectedTools: Tool[];
  onToolSelect: (tool: Tool) => void;
  editingFunction?: Tool | null;
  onEditingFunctionChange?: (editingFunction: Tool | null) => void;
  editingMCP?: Tool | null;
  onEditingMCPChange?: (editingMCP: Tool | null) => void;
  editingFileSearch?: Tool | null;
  onEditingFileSearchChange?: (editingFileSearch: Tool | null) => void;
  editingAgenticFileSearch?: Tool | null;
  onEditingAgenticFileSearchChange?: (editingAgenticFileSearch: Tool | null) => void;
  editingPyFunction?: Tool | null;
  onEditingPyFunctionChange?: (editingPyFunction: Tool | null) => void;
  editingLocalTool?: Tool | null;
  onEditingLocalToolChange?: (editingLocalTool: Tool | null) => void;
  onOpenE2BModal?: () => void;
  getMCPToolByLabel?: (label: string) => any;
  children?: React.ReactNode;
}

const availableTools: Tool[] = [
  { id: 'mcp_server', name: 'MCP Server', icon: MCP },
  { id: 'py_fun_tool', name: 'Py Function Tool', icon: Code },
  { id: 'local_tool', name: 'Local Tool', icon: Puzzle },
  { id: 'file_search', name: 'File Search', icon: Search },
  { id: 'agentic_file_search', name: 'Agentic File Search', icon: FileSearch },
  { id: 'function', name: 'Function', icon: Code },
  { id: 'image_generation', name: 'Image Generation', icon: Image },
  { id: 'think', name: 'Think', icon: Brain }
];

const ToolsSelectionModal: React.FC<ToolsSelectionModalProps> = ({
  selectedTools,
  onToolSelect,
  editingFunction,
  onEditingFunctionChange,
  editingMCP,
  onEditingMCPChange,
  editingFileSearch,
  onEditingFileSearchChange,
  editingAgenticFileSearch,
  onEditingAgenticFileSearchChange,
  editingPyFunction,
  onEditingPyFunctionChange,
  editingLocalTool,
  onEditingLocalToolChange,
  onOpenE2BModal,
  getMCPToolByLabel,
  children
}) => {
  const [isOpen, setIsOpen] = useState(false);
  const [functionModalOpen, setFunctionModalOpen] = useState(false);
  const [mcpModalOpen, setMcpModalOpen] = useState(false);
  const [fileSearchModalOpen, setFileSearchModalOpen] = useState(false);
  const [agenticFileSearchModalOpen, setAgenticFileSearchModalOpen] = useState(false);
  const [pyFunctionModalOpen, setPyFunctionModalOpen] = useState(false);
  const [pyFunctionSelectionModalOpen, setPyFunctionSelectionModalOpen] = useState(false);
  const [localToolModalOpen, setLocalToolModalOpen] = useState(false);
  const [functionDefinition, setFunctionDefinition] = useState('');
  const [editingMCPConfig, setEditingMCPConfig] = useState<any>(null);
  
  const { platformInfo } = usePlatformInfo();
  const isVectorStoreEnabled = platformInfo?.vectorStoreInfo?.isEnabled ?? true;

  // Handle editing existing function
  React.useEffect(() => {
    if (editingFunction && editingFunction.id === 'function') {
      setFunctionDefinition(editingFunction.functionDefinition || '');
      setFunctionModalOpen(true);
    }
  }, [editingFunction]);

  // Handle editing existing MCP server
  React.useEffect(() => {
    if (editingMCP && editingMCP.id === 'mcp_server') {
      setMcpModalOpen(true);
    }
  }, [editingMCP]);

  // Handle editing existing file search
  React.useEffect(() => {
    if (editingFileSearch && editingFileSearch.id === 'file_search') {
      setFileSearchModalOpen(true);
    }
  }, [editingFileSearch]);

  // Handle editing existing agentic file search
  React.useEffect(() => {
    if (editingAgenticFileSearch && editingAgenticFileSearch.id === 'agentic_file_search') {
      setAgenticFileSearchModalOpen(true);
    }
  }, [editingAgenticFileSearch]);

  // Handle editing existing PyFunction tool
  React.useEffect(() => {
    if (editingPyFunction && editingPyFunction.id === 'py_fun_tool') {
      setPyFunctionModalOpen(true);
    }
  }, [editingPyFunction]);

  // Handle editing existing Local tool
  React.useEffect(() => {
    if (editingLocalTool && editingLocalTool.id === 'local_tool') {
      setLocalToolModalOpen(true);
    }
  }, [editingLocalTool]);

  const handleToolSelect = (tool: Tool) => {
    if (tool.id === 'function') {
      setIsOpen(false);
      setFunctionModalOpen(true);
    } else if (tool.id === 'mcp_server') {
      setIsOpen(false);
      setMcpModalOpen(true);
    } else if (tool.id === 'file_search') {
      setIsOpen(false);
      setFileSearchModalOpen(true);
    } else if (tool.id === 'agentic_file_search') {
      setIsOpen(false);
      setAgenticFileSearchModalOpen(true);
    } else if (tool.id === 'py_fun_tool') {
      setIsOpen(false);
      setPyFunctionSelectionModalOpen(true);
    } else if (tool.id === 'local_tool') {
      setIsOpen(false);
      setLocalToolModalOpen(true);
    } else {
      onToolSelect(tool);
      setIsOpen(false);
    }
  };

  const handlePyFunctionSelect = (functionName: string) => {
    // Function is already saved to localStorage by the selection modal
    // Add a small delay to ensure localStorage has been updated
    setTimeout(() => {
      try {
        console.log('Attempting to select function:', functionName);
        const storedTools = localStorage.getItem('platform_py_fun_tools');
        console.log('Stored tools:', storedTools);
        
        if (storedTools) {
          const pyFunctionTools = JSON.parse(storedTools);
          console.log('Parsed tools:', pyFunctionTools);
          
          // The data is stored as an array
          const selectedFunction = Array.isArray(pyFunctionTools) 
            ? pyFunctionTools.find((tool: any) => tool.tool_def?.name === functionName)
            : null;
          console.log('Found function:', selectedFunction);
          
          if (selectedFunction) {
            // Create a tool object for the selected function
            const pyFunctionTool: Tool = {
              id: 'py_fun_tool',
              name: `Function: ${selectedFunction.tool_def.name}`,
              icon: Code,
              pyFunctionConfig: selectedFunction
            };
            
            console.log('Created tool object:', pyFunctionTool);
            
            // Add to selected tools
            onToolSelect(pyFunctionTool);
            toast.success(`Function "${functionName}" added to your tools!`);
          } else {
            console.error('Function not found in localStorage:', functionName);
            toast.error(`Function "${functionName}" not found in storage`);
          }
        } else {
          console.error('No stored tools found');
          toast.error('No functions found in storage');
        }
      } catch (error) {
        console.error('Error adding Py function tool:', error);
        toast.error('Failed to add function to tools');
      }
    }, 100); // Small delay to ensure localStorage is updated
    
    setPyFunctionSelectionModalOpen(false);
  };

  const handleCreatePyFunction = () => {
    // Close selection modal and open the existing PyFunctionToolModal for creation
    setPyFunctionSelectionModalOpen(false);
    setPyFunctionModalOpen(true);
  };

  const handleFunctionSave = () => {
    // Get function name from definition for better display
    let functionName = 'Function';
    try {
      const parsed = JSON.parse(functionDefinition);
      functionName = parsed.name || 'Function';
    } catch {
      // Keep default name if parsing fails
    }

    // Create function tool with definition
    const functionTool: Tool = {
      id: 'function',
      name: functionName,
      icon: Code,
      functionDefinition: functionDefinition
    };

    // If editing, remove the old function first
    if (editingFunction && onEditingFunctionChange) {
      // The parent component will handle removing the old function and adding the new one
      onEditingFunctionChange(null);
    }

    onToolSelect(functionTool);
    setFunctionModalOpen(false);
    setFunctionDefinition('');
    
    // Clear editing state
    if (onEditingFunctionChange) {
      onEditingFunctionChange(null);
    }
  };

  const handleMCPConnect = (config: any) => {
    // Create MCP server tool with config
    const mcpTool: Tool = {
      id: 'mcp_server',
      name: config.label || 'MCP Server',
      icon: MCP,
      mcpConfig: config // Store the full config including selectedTools
    };

    // If editing, remove the old MCP server first
    if (editingMCP && onEditingMCPChange) {
      // The parent component will handle removing the old MCP server and adding the new one
      onEditingMCPChange(null);
    }

    onToolSelect(mcpTool);
    setMcpModalOpen(false);
    
    // Clear editing state
    if (onEditingMCPChange) {
      onEditingMCPChange(null);
    }
  };

  const handleFileSearchSave = (config: { selectedFiles: string[]; selectedVectorStores: string[]; vectorStoreNames: string[] }) => {
    // Create file search tool with config
    const displayName = config.vectorStoreNames.length > 0 ? config.vectorStoreNames.join(', ') : 'File Search';
    const fileSearchTool: Tool = {
      id: 'file_search',
      name: displayName,
      icon: Search,
      fileSearchConfig: config
    };

    // If editing, remove the old file search first
    if (editingFileSearch && onEditingFileSearchChange) {
      // The parent component will handle removing the old file search and adding the new one
      onEditingFileSearchChange(null);
    }

    onToolSelect(fileSearchTool);
    setFileSearchModalOpen(false);
    
    // Clear editing state
    if (onEditingFileSearchChange) {
      onEditingFileSearchChange(null);
    }
  };

  const handleAgenticFileSearchSave = (config: { selectedFiles: string[]; selectedVectorStores: string[]; vectorStoreNames: string[]; iterations: number; maxResults: number }) => {
    // Create agentic file search tool with config
    const displayName = config.vectorStoreNames.length > 0 ? config.vectorStoreNames.join(', ') : 'Agentic File Search';
    const agenticFileSearchTool: Tool = {
      id: 'agentic_file_search',
      name: displayName,
      icon: FileSearch,
      agenticFileSearchConfig: config
    };

    // If editing, remove the old agentic file search first
    if (editingAgenticFileSearch && onEditingAgenticFileSearchChange) {
      // The parent component will handle removing the old agentic file search and adding the new one
      onEditingAgenticFileSearchChange(null);
    }

    onToolSelect(agenticFileSearchTool);
    setAgenticFileSearchModalOpen(false);
    
    // Clear editing state
    if (onEditingAgenticFileSearchChange) {
      onEditingAgenticFileSearchChange(null);
    }
  };

  const handlePyFunctionSave = (config: PyFunctionTool) => {
    // Create PyFunction tool with config
    const pyFunctionTool: Tool = {
      id: 'py_fun_tool',
      name: config.tool_def.name || 'Python Function',
      icon: Code,
      pyFunctionConfig: config
    };

    // If editing, remove the old PyFunction tool first
    if (editingPyFunction && onEditingPyFunctionChange) {
      // The parent component will handle removing the old PyFunction tool and adding the new one
      onEditingPyFunctionChange(null);
    }

    onToolSelect(pyFunctionTool);
    setPyFunctionModalOpen(false);
    
    // Clear editing state
    if (onEditingPyFunctionChange) {
      onEditingPyFunctionChange(null);
    }
  };

  const handleLocalToolSave = (config: any) => {
    // Create Local tool with config
    const localTool: Tool = {
      id: 'local_tool',
      name: config.name || 'Local Function',
      icon: Puzzle,
      localToolConfig: config
    };

    // If editing, remove the old Local tool first
    if (editingLocalTool && onEditingLocalToolChange) {
      // The parent component will handle removing the old Local tool and adding the new one
      onEditingLocalToolChange(null);
    }

    onToolSelect(localTool);
    setLocalToolModalOpen(false);
    
    // Clear editing state
    if (onEditingLocalToolChange) {
      onEditingLocalToolChange(null);
    }
  };

  const isToolSelected = (toolId: string) => {
    // Allow multiple functions, MCP servers, PyFunction tools, and Local tools to be added
    if (toolId === 'function' || toolId === 'mcp_server' || toolId === 'py_fun_tool' || toolId === 'local_tool') {
      return false;
    }
    return selectedTools.some(tool => tool.id === toolId);
  };

  const getToolDisabledState = (tool: Tool) => {
    // Disable file search tools if vector store is not enabled
    if ((tool.id === 'file_search' || tool.id === 'agentic_file_search') && !isVectorStoreEnabled) {
      return {
        isDisabled: true,
        tooltipMessage: 'To enable, boot up platform with Qdrant vector store'
      };
    }
    
    // Keep existing disabled state for other tools
    if (['function', 'image_generation', 'think'].includes(tool.id)) {
      return {
        isDisabled: true,
        tooltipMessage: 'Coming Soon'
      };
    }

    // Enable PyFunction tool
    if (tool.id === 'py_fun_tool') {
      return {
        isDisabled: false,
        tooltipMessage: null
      };
    }

    // Enable Local tool
    if (tool.id === 'local_tool') {
      return {
        isDisabled: false,
        tooltipMessage: null
      };
    }

    return {
      isDisabled: false,
      tooltipMessage: null
    };
  };

  const renderToolButton = (tool: Tool) => {
    const IconComponent = tool.icon;
    const { isDisabled, tooltipMessage } = getToolDisabledState(tool);
    
    const toolButton = (
      <Button
        key={tool.id}
        variant="ghost"
        className={`w-full justify-start h-auto p-3 hover:bg-positive-trend/10 hover:text-positive-trend focus:bg-positive-trend/10 focus:text-positive-trend rounded-md ${
          isDisabled ? 'opacity-50 cursor-not-allowed' : ''
        }`}
        onClick={() => !isDisabled && handleToolSelect(tool)}
        disabled={isDisabled && !tooltipMessage} // Only disable if no tooltip message
      >
        <div className="flex items-center space-x-3 w-full">
          <IconComponent className="h-4 w-4 shrink-0" />
          <span className="text-sm font-medium">{tool.name}</span>
        </div>
      </Button>
    );

    // Show tooltip if there's a specific tooltip message, regardless of disabled state
    if (tooltipMessage) {
      return (
        <TooltipProvider key={tool.id}>
          <Tooltip>
            <TooltipTrigger asChild>
              <div 
                className={`${isDisabled ? 'cursor-not-allowed' : ''}`}
                onClick={(e) => {
                  if (isDisabled) {
                    e.preventDefault();
                    e.stopPropagation();
                  } else {
                    handleToolSelect(tool);
                  }
                }}
              >
                <Button
                  variant="ghost"
                  className={`w-full justify-start h-auto p-3 hover:bg-positive-trend/10 hover:text-positive-trend focus:bg-positive-trend/10 focus:text-positive-trend rounded-md ${
                    isDisabled ? 'opacity-50 cursor-not-allowed pointer-events-none' : ''
                  }`}
                  disabled={false} // Never disable to allow hover events
                >
                  <div className="flex items-center space-x-3 w-full">
                    <IconComponent className="h-4 w-4 shrink-0" />
                    <span className="text-sm font-medium">{tool.name}</span>
                  </div>
                </Button>
              </div>
            </TooltipTrigger>
            <TooltipContent>
              <p>{tooltipMessage}</p>
            </TooltipContent>
          </Tooltip>
        </TooltipProvider>
      );
    }

    return toolButton;
  };

  return (
    <>
      <Popover open={isOpen} onOpenChange={setIsOpen}>
        <PopoverTrigger asChild>
          {children || (
            <Button
              variant="ghost"
              size="sm"
              className="h-6 text-xs text-muted-foreground hover:bg-muted/50"
            >
              +
            </Button>
          )}
        </PopoverTrigger>
        <PopoverContent 
          className="w-56 p-2 border border-border/50 shadow-lg"
          style={{
            backgroundColor: 'rgba(0, 0, 0, 0.1)',
            backdropFilter: 'blur(12px)',
            WebkitBackdropFilter: 'blur(12px)',
            border: '1px solid rgba(255, 255, 255, 0.1)'
          }}
          side="right"
          align="start"
        >
          <div className="space-y-1">
            {availableTools.map((tool) => renderToolButton(tool))}
          </div>
        </PopoverContent>
      </Popover>
      
      <FunctionModal
        open={functionModalOpen}
        onOpenChange={(open) => {
          setFunctionModalOpen(open);
          if (!open) {
            // Clear editing state when modal closes
            if (onEditingFunctionChange) {
              onEditingFunctionChange(null);
            }
            setFunctionDefinition('');
          }
        }}
        functionDefinition={functionDefinition}
        onFunctionDefinitionChange={setFunctionDefinition}
        onSave={handleFunctionSave}
      />
    
      <MCPModal
        open={mcpModalOpen}
        onOpenChange={(open) => {
          setMcpModalOpen(open);
          if (!open) {
            // Clear editing state when modal closes
            if (onEditingMCPChange) {
              onEditingMCPChange(null);
            }
          }
        }}
        onConnect={handleMCPConnect}
        initialConfig={editingMCP?.mcpConfig || (editingMCP?.mcpConfig?.label && getMCPToolByLabel ? getMCPToolByLabel(editingMCP.mcpConfig.label) : undefined)}
      />
    
      <FileSearchModal
        open={fileSearchModalOpen}
        onOpenChange={(open) => {
          setFileSearchModalOpen(open);
          if (!open) {
            // Clear editing state when modal closes
            if (onEditingFileSearchChange) {
              onEditingFileSearchChange(null);
            }
          }
        }}
        onSave={handleFileSearchSave}
        initialVectorStores={editingFileSearch?.fileSearchConfig?.selectedVectorStores || []}
        initialSelectedFiles={editingFileSearch?.fileSearchConfig?.selectedFiles}
        initialVectorStoreNames={editingFileSearch?.fileSearchConfig?.vectorStoreNames}
      />
    
      <AgenticFileSearchModal
        open={agenticFileSearchModalOpen}
        onOpenChange={(open) => {
          setAgenticFileSearchModalOpen(open);
          if (!open) {
            // Clear editing state when modal closes
            if (onEditingAgenticFileSearchChange) {
              onEditingAgenticFileSearchChange(null);
            }
          }
        }}
        onSave={handleAgenticFileSearchSave}
        initialVectorStores={editingAgenticFileSearch?.agenticFileSearchConfig?.selectedVectorStores || []}
        initialIterations={editingAgenticFileSearch?.agenticFileSearchConfig?.iterations}
        initialMaxResults={editingAgenticFileSearch?.agenticFileSearchConfig?.maxResults}
        initialSelectedFiles={editingAgenticFileSearch?.agenticFileSearchConfig?.selectedFiles}
        initialVectorStoreNames={editingAgenticFileSearch?.agenticFileSearchConfig?.vectorStoreNames}
      />

      <PyFunctionToolModal
        open={pyFunctionModalOpen}
        onOpenChange={(open) => {
          setPyFunctionModalOpen(open);
          if (!open) {
            // Clear editing state when modal closes
            if (onEditingPyFunctionChange) {
              onEditingPyFunctionChange(null);
            }
          }
        }}
        onSave={handlePyFunctionSave}
        initialTool={editingPyFunction?.pyFunctionConfig || null}
        onOpenE2BModal={onOpenE2BModal}
      />

      <PyFunctionToolSelectionModal
        open={pyFunctionSelectionModalOpen}
        onOpenChange={(open) => {
          setPyFunctionSelectionModalOpen(open);
          if (!open) {
            // Clear editing state when modal closes
            if (onEditingPyFunctionChange) {
              onEditingPyFunctionChange(null);
            }
          }
        }}
        onFunctionSelect={handlePyFunctionSelect}
        onCreateFunction={handleCreatePyFunction}
      />

      <LocalToolModal
        open={localToolModalOpen}
        onOpenChange={(open) => {
          setLocalToolModalOpen(open);
          if (!open) {
            // Clear editing state when modal closes
            if (onEditingLocalToolChange) {
              onEditingLocalToolChange(null);
            }
          }
        }}
        onSave={handleLocalToolSave}
        initialTool={editingLocalTool?.localToolConfig || null}
      />
    </>
  );
};

export default ToolsSelectionModal; 