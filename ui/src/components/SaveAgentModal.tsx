import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Save } from 'lucide-react';
import { toast } from 'sonner';
import { API_URL } from '@/config';

interface SaveAgentModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  systemPrompt: string;
  tools: any[];
  modelName?: string;
  // Agent context for updates
  isAgentContext?: boolean;
  existingAgentName?: string;
  existingAgentDescription?: string;
  // Callback to switch to agent context after saving
  onAgentSaved?: (agentName: string, agentDescription: string) => void;
}

const SaveAgentModal: React.FC<SaveAgentModalProps> = ({
  open,
  onOpenChange,
  systemPrompt,
  tools,
  modelName,
  isAgentContext = false,
  existingAgentName = '',
  existingAgentDescription = '',
  onAgentSaved
}) => {
  const [agentName, setAgentName] = useState('');
  const [description, setDescription] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const [showUpdateConfirmation, setShowUpdateConfirmation] = useState(false);

  // Pre-populate fields when in agent context
  useEffect(() => {
    if (isAgentContext && open) {
      setAgentName(existingAgentName);
      setDescription(existingAgentDescription);
    } else if (!isAgentContext && open) {
      // Clear fields for new agent
      setAgentName('');
      setDescription('');
    }
  }, [open, isAgentContext, existingAgentName, existingAgentDescription]);

  const transformToolsToApiFormat = (selectedTools: any[]) => {
    return selectedTools.map(tool => {
      if (tool.id === 'mcp_server' && tool.mcpConfig) {
        return {
          type: 'mcp',
          server_label: tool.mcpConfig.label,
          server_url: tool.mcpConfig.url,
          allowed_tools: tool.mcpConfig.selectedTools
          // Never send headers/secrets to backend for persistence
        };
      } else if (tool.id === 'file_search' && tool.fileSearchConfig) {
        const toolBody: any = {
          type: 'file_search',
          vector_store_ids: tool.fileSearchConfig.selectedVectorStores
        };
        // Get embedding model info
        const provider = localStorage.getItem('platform_embedding_modelProvider') || '';
        const modelName = localStorage.getItem('platform_embedding_modelName') || '';
        if (provider && modelName) {
          toolBody.modelInfo = {
            model: `${provider}@${modelName}`
          };
        }
        return toolBody;
      } else if (tool.id === 'agentic_file_search' && tool.agenticFileSearchConfig) {
        const toolBody: any = {
          type: 'agentic_search',
          vector_store_ids: tool.agenticFileSearchConfig.selectedVectorStores,
          max_iterations: tool.agenticFileSearchConfig.iterations,
          max_num_results: tool.agenticFileSearchConfig.maxResults
        };
        // Get embedding model info
        const provider = localStorage.getItem('platform_embedding_modelProvider') || '';
        const modelName = localStorage.getItem('platform_embedding_modelName') || '';
        if (provider && modelName) {
          toolBody.modelInfo = {
            model: `${provider}@${modelName}`
          };
        }
        return toolBody;
      } else if (tool.id === 'py_fun_tool' && tool.pyFunctionConfig) {
        return {
          type: 'py_fun_tool',
          tool_def: {
            name: tool.pyFunctionConfig.tool_def.name,
            description: tool.pyFunctionConfig.tool_def.description,
            parameters: tool.pyFunctionConfig.tool_def.parameters
          },
          code: tool.pyFunctionConfig.code
        };
      } else if (tool.id === 'fun_req_gathering_tool') {
        return { type: 'fun_req_gathering_tool' };
      } else if (tool.id === 'fun_def_generation_tool') {
        return { type: 'fun_def_generation_tool' };
      } else if (tool.id === 'mock_fun_save_tool') {
        return { type: 'mock_fun_save_tool' };
      } else if (tool.id === 'mock_generation_tool') {
        return { type: 'mock_generation_tool' };
      } else if (tool.id === 'mock_save_tool') {
        return { type: 'mock_save_tool' };
      }
      return null;
    }).filter(tool => tool !== null);
  };

  const handleSave = async () => {
    if (!agentName.trim() || !description.trim()) {
      toast.error('Please fill in all required fields');
      return;
    }

    // Check if name contains spaces
    if (agentName.includes(' ')) {
      toast.error('Agent name cannot contain spaces');
      return;
    }

    // Determine if this is an update or create operation
    const isUpdate = isAgentContext;
    const isNameChanged = isUpdate && agentName.trim() !== existingAgentName;
    
    // If updating and name changed, show confirmation
    if (isUpdate && isNameChanged && !showUpdateConfirmation) {
      setShowUpdateConfirmation(true);
      return;
    }

    // If updating existing agent with same name, show confirmation
    if (isUpdate && !isNameChanged && !showUpdateConfirmation) {
      setShowUpdateConfirmation(true);
      return;
    }

    await performSave(isUpdate, isNameChanged);
  };

  const performSave = async (isUpdate: boolean, isNameChanged: boolean) => {
    setIsLoading(true);
    setShowUpdateConfirmation(false);
    
    try {
      // Transform tools to API format
      const transformedTools = transformToolsToApiFormat(tools);
      
      const agentData = {
        name: agentName.trim(),
        description: description.trim(),
        systemPrompt: systemPrompt,
        tools: transformedTools,
        model: modelName,
        formatType: 'text',
        temperature: 1.0,
        maxTokenOutput: 2048,
        topP: 1.0,
        store: true,
        stream: true
      };

      let url: string;
      let method: string;
      
      if (isUpdate && !isNameChanged) {
        // Update existing agent with same name - use PUT
        url = `${API_URL}/v1/agents/${existingAgentName}`;
        method = 'PUT';
      } else {
        // Create new agent or create with new name - use POST
        url = `${API_URL}/v1/agents`;
        method = 'POST';
      }

      const response = await fetch(url, {
        method,
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(agentData),
      });

      if (!response.ok) {
        let errorMessage = `Failed to ${isUpdate ? 'update' : 'save'} agent`;
        try {
          const errorData = await response.json();
          if (errorData.message) {
            errorMessage = errorData.message;
          } else if (errorData.error) {
            errorMessage = errorData.error;
          } else {
            errorMessage = `HTTP ${response.status}: ${response.statusText}`;
          }
        } catch (parseError) {
          errorMessage = `HTTP ${response.status}: ${response.statusText}`;
        }
        throw new Error(errorMessage);
      }

      const successMessage = isUpdate 
        ? (isNameChanged ? 'Agent saved as new agent successfully!' : 'Agent updated successfully!')
        : 'Agent saved successfully!';
      
      toast.success(successMessage);
      
      // Call the callback to switch to agent context (only for non-updates or new agents)
      if (onAgentSaved && (!isUpdate || isNameChanged)) {
        onAgentSaved(agentName.trim(), description.trim());
      }
      
      // Reset form only if not in agent context
      if (!isAgentContext) {
        setAgentName('');
        setDescription('');
      }
      onOpenChange(false);
    } catch (error) {
      console.error('Error saving agent:', error);
      toast.error(error instanceof Error ? error.message : `Failed to ${isUpdate ? 'update' : 'save'} agent`);
    } finally {
      setIsLoading(false);
    }
  };

  const handleConfirmUpdate = () => {
    const isNameChanged = agentName.trim() !== existingAgentName;
    performSave(true, isNameChanged);
  };

  const handleCancelUpdate = () => {
    setShowUpdateConfirmation(false);
  };

  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    // Remove spaces as user types
    setAgentName(value.replace(/\s+/g, ''));
  };

  return (
    <>
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center space-x-2">
            <Save className="h-5 w-5" />
            <span>Save Agent</span>
          </DialogTitle>
        </DialogHeader>
        
        <div className="space-y-4">
          <div className="space-y-2">
            <Label htmlFor="agent-name" className="text-sm font-medium">
              Agent Name *
            </Label>
            <Input
              id="agent-name"
              value={agentName}
              onChange={handleNameChange}
              placeholder="Enter agent name"
              className="w-full"
              disabled={isLoading}
            />
            <p className="text-xs text-muted-foreground">
              Spaces are not allowed
            </p>
          </div>
          
          <div className="space-y-2">
            <Label htmlFor="agent-description" className="text-sm font-medium">
              Description *
            </Label>
            <Textarea
              id="agent-description"
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Describe what this agent does"
              className="w-full h-20 resize-none"
              disabled={isLoading}
            />
          </div>

          <div className="flex items-center justify-end space-x-2 pt-2">
            <Button
              variant="outline"
              onClick={() => onOpenChange(false)}
              disabled={isLoading}
            >
              Cancel
            </Button>
            <Button
              onClick={handleSave}
              disabled={!agentName.trim() || !description.trim() || isLoading}
              className="bg-positive-trend hover:bg-positive-trend/90 text-white"
            >
              {isLoading ? (
                <div className="flex items-center space-x-2">
                  <div className="w-4 h-4 border border-white border-t-transparent rounded-full animate-spin"></div>
                  <span>Saving...</span>
                </div>
              ) : (
                'Save Agent'
              )}
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>

    {/* Confirmation Dialog for Updates */}
    <AlertDialog open={showUpdateConfirmation} onOpenChange={setShowUpdateConfirmation}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>
            {agentName.trim() !== existingAgentName ? 'Save as New Agent?' : 'Update Existing Agent?'}
          </AlertDialogTitle>
          <AlertDialogDescription>
            {agentName.trim() !== existingAgentName 
              ? `You've changed the agent name from "${existingAgentName}" to "${agentName.trim()}". This will create a new agent instead of updating the existing one.`
              : `You're about to update the existing agent "${existingAgentName}". This will overwrite the current configuration.`
            }
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          <AlertDialogCancel onClick={handleCancelUpdate}>Cancel</AlertDialogCancel>
          <AlertDialogAction onClick={handleConfirmUpdate}>
            {agentName.trim() !== existingAgentName ? 'Save as New Agent' : 'Update Agent'}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
    </>
  );
};

export default SaveAgentModal;
