import React, { useState } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogTrigger,
} from '@/components/ui/dialog';
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
}

const SaveAgentModal: React.FC<SaveAgentModalProps> = ({
  open,
  onOpenChange,
  systemPrompt,
  tools,
  modelName
}) => {
  const [agentName, setAgentName] = useState('');
  const [description, setDescription] = useState('');
  const [isLoading, setIsLoading] = useState(false);

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

    setIsLoading(true);
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
        // Removed: kind field as requested
      };

      // Use fetch directly since authorization header is not needed for this request
      const response = await fetch(`${API_URL}/v1/agents`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify(agentData),
      });

      if (!response.ok) {
        let errorMessage = 'Failed to save agent';
        try {
          const errorData = await response.json();
          // Handle the specific API error format with type, message, etc.
          if (errorData.message) {
            errorMessage = errorData.message;
          } else if (errorData.error) {
            errorMessage = errorData.error;
          } else {
            errorMessage = `HTTP ${response.status}: ${response.statusText}`;
          }
        } catch (parseError) {
          // If response body is not JSON, use status text
          errorMessage = `HTTP ${response.status}: ${response.statusText}`;
        }
        throw new Error(errorMessage);
      }

      toast.success(`Agent "${agentName.trim()}" saved successfully!`);
      onOpenChange(false);
      setAgentName('');
      setDescription('');
    } catch (error) {
      console.error('Error saving agent:', error);
      if (error instanceof Error) {
        toast.error(error.message);
      } else {
        toast.error('Failed to save agent');
      }
    } finally {
      setIsLoading(false);
    }
  };

  const handleNameChange = (e: React.ChangeEvent<HTMLInputElement>) => {
    const value = e.target.value;
    // Remove spaces as user types
    setAgentName(value.replace(/\s+/g, ''));
  };

  return (
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
  );
};

export default SaveAgentModal;
