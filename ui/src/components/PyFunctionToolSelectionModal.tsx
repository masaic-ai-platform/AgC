import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Loader2, Search, Code, Check, Plus } from 'lucide-react';
import { API_URL } from '@/config';
import { apiClient } from '@/lib/api';
import { toast } from 'sonner';

interface PyFunction {
  name: string;
  description: string;
  parameters: any;
  code: string;
  isCodeValid: boolean;
  codeProblem?: string;
  testData?: any;
}

interface PyFunctionToolSelectionModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onFunctionSelect: (functionName: string) => void;
  onCreateFunction?: () => void;
}

const PyFunctionToolSelectionModal: React.FC<PyFunctionToolSelectionModalProps> = ({
  open,
  onOpenChange,
  onFunctionSelect,
  onCreateFunction
}) => {
  const [functions, setFunctions] = useState<string[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedFunction, setSelectedFunction] = useState<string | null>(null);

  const fetchFunctions = async () => {
    setLoading(true);
    try {
      const functionsData = await apiClient.jsonRequest<string[]>('/v1/dashboard/functions:names');
      setFunctions(functionsData);
    } catch (error) {
      console.error('Error fetching functions:', error);
      toast.error('Failed to load functions');
      setFunctions([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchFunctionDetails = async (functionName: string) => {
    try {
      const functionData = await apiClient.jsonRequest<{
        suggestedFunctionDetails: {
          name: string;
          description: string;
          parameters: any;
        };
        testData: any;
        isCodeValid: boolean;
        codeProblem?: string;
        code: string;
      }>(`/v1/dashboard/functions/${encodeURIComponent(functionName)}`);
      
      // Save to localStorage as py function tool
      const pyFunctionTool = {
        type: 'py_fun_tool',
        tool_def: {
          name: functionData.suggestedFunctionDetails.name,
          description: functionData.suggestedFunctionDetails.description,
          parameters: functionData.suggestedFunctionDetails.parameters
        },
        code: functionData.code,
        testData: functionData.testData
      };
      
      // Get existing tools from localStorage
      const existingTools = localStorage.getItem('platform_py_fun_tools');
      let tools: any[] = [];
      
      if (existingTools) {
        try {
          const parsed = JSON.parse(existingTools);
          // Ensure tools is always an array
          tools = Array.isArray(parsed) ? parsed : [];
        } catch (error) {
          console.error('Error parsing existing tools:', error);
          tools = [];
        }
      }
      
      // Check if tool already exists
      const existingIndex = tools.findIndex((tool: any) => tool.tool_def?.name === functionName);
      if (existingIndex >= 0) {
        // Update existing tool
        tools[existingIndex] = pyFunctionTool;
      } else {
        // Add new tool
        tools.push(pyFunctionTool);
      }
      
      // Save back to localStorage
      localStorage.setItem('platform_py_fun_tools', JSON.stringify(tools));
      
      return functionData;
    } catch (error) {
      console.error('Error fetching function details:', error);
      toast.error(`Failed to load function: ${functionName}`);
      throw error;
    }
  };

  const handleFunctionSelect = async (functionName: string) => {
    setSelectedFunction(functionName);
    try {
      console.log('Starting function selection for:', functionName);
      
      // Fetch full function details and save to localStorage
      const functionData = await fetchFunctionDetails(functionName);
      console.log('Function data received:', functionData);
      
      // Verify the function was saved
      const storedTools = localStorage.getItem('platform_py_fun_tools');
      console.log('Stored tools after save:', storedTools);
      
      // Verify it's properly parsed
      if (storedTools) {
        try {
          const parsed = JSON.parse(storedTools);
          console.log('Parsed stored tools:', parsed);
          console.log('Is array:', Array.isArray(parsed));
        } catch (parseError) {
          console.error('Error parsing stored tools:', parseError);
        }
      }
      
      // Call the callback to notify parent component
      console.log('Calling onFunctionSelect with:', functionName);
      onFunctionSelect(functionName);
      
      setSelectedFunction(null);
      onOpenChange(false);
      // Don't show toast here, let the parent component handle it
    } catch (error) {
      console.error('Error in handleFunctionSelect:', error);
      setSelectedFunction(null);
      toast.error(`Failed to add function: ${functionName}`);
    }
  };

  useEffect(() => {
    if (open) {
      fetchFunctions();
    }
  }, [open]);

  const filteredFunctions = functions.filter(func =>
    func.toLowerCase().includes(searchTerm.toLowerCase())
  );

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-md w-full">
        <DialogHeader>
          <DialogTitle>Select Python Function</DialogTitle>
          <DialogDescription>
            Choose an existing function or create a new one.
          </DialogDescription>
        </DialogHeader>
        
        <div className="space-y-4">
          {/* Create Function Button */}
          {onCreateFunction && (
            <Button
              variant="outline"
              onClick={() => {
                onCreateFunction();
                onOpenChange(false);
              }}
              className="w-full flex items-center justify-center space-x-2 hover:bg-positive-trend/10 hover:text-positive-trend border-positive-trend/20"
            >
              <Plus className="h-4 w-4" />
              <span>Create New Function</span>
            </Button>
          )}
          
          {/* Search Input */}
          <div className="relative">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search functions..."
              className="pl-9"
            />
          </div>
          
          {/* Functions List */}
          <div className="max-h-[400px] overflow-y-auto border rounded-lg">
            {loading ? (
              <div className="flex items-center justify-center p-8">
                <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              </div>
            ) : filteredFunctions.length === 0 ? (
              <div className="p-8 text-center text-muted-foreground">
                {searchTerm ? 'No functions found matching your search.' : 'No functions available.'}
              </div>
            ) : (
              <div className="p-2 space-y-1">
                {filteredFunctions.map((functionName) => (
                  <div
                    key={functionName}
                    className={`flex items-center justify-between p-3 rounded-lg cursor-pointer transition-colors ${
                      selectedFunction === functionName
                        ? 'bg-positive-trend/10 text-positive-trend border border-positive-trend/20'
                        : 'hover:bg-accent/50'
                    }`}
                    onClick={() => handleFunctionSelect(functionName)}
                  >
                    <div className="flex items-center space-x-3">
                      <Code className="h-4 w-4 text-muted-foreground" />
                      <div>
                        <div className="font-medium text-sm">{functionName}</div>
                      </div>
                    </div>
                    {selectedFunction === functionName ? (
                      <Loader2 className="h-4 w-4 animate-spin text-positive-trend" />
                    ) : (
                      <Check className="h-4 w-4 text-muted-foreground opacity-0 group-hover:opacity-100" />
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default PyFunctionToolSelectionModal;
