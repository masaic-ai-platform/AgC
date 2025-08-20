import React, { useState, useEffect } from 'react';
import {
  Popover,
  PopoverContent,
  PopoverTrigger,
} from '@/components/ui/popover';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import { Loader2, Search, Bot, Check, Trash2 } from 'lucide-react';
import { API_URL } from '@/config';
import { apiClient } from '@/lib/api';
import { toast } from 'sonner';

interface Agent {
  name: string;
  description: string;
  systemPrompt: string;
  tools: any[];
  model?: string;
  formatType?: string;
  temperature?: number;
  maxTokenOutput?: number;
  topP?: number;
  store?: boolean;
  stream?: boolean;
  createdAt?: string;
  updatedAt?: string;
}

interface AgentsSelectionModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onAgentSelect: (agent: Agent) => void;
  onCreateAgent?: () => void;
  triggerButton?: React.ReactNode;
}

const AgentsSelectionModal: React.FC<AgentsSelectionModalProps> = ({
  open,
  onOpenChange,
  onAgentSelect,
  onCreateAgent,
  triggerButton
}) => {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(false);
  const [searchTerm, setSearchTerm] = useState('');
  const [selectedAgent, setSelectedAgent] = useState<string | null>(null);
  const [deletingAgent, setDeletingAgent] = useState<string | null>(null);

  const fetchAgents = async () => {
    setLoading(true);
    try {
      const agentsData = await apiClient.agentJsonRequest('/v1/agents');
      setAgents(agentsData);
    } catch (error) {
      console.error('Error fetching agents:', error);
      toast.error('Failed to load agents');
      setAgents([]);
    } finally {
      setLoading(false);
    }
  };
  const handleDeleteAgent = async (agentName: string) => {
    try {
      setDeletingAgent(agentName);
      await apiClient.agentRequest(`/v1/agents/${agentName}`, { method: 'DELETE' });
      setAgents(prev => prev.filter(a => a.name !== agentName));
      toast.success(`Deleted agent: ${agentName}`);
    } catch (error) {
      console.error('Error deleting agent:', error);
      toast.error(`Failed to delete agent: ${agentName}`);
    } finally {
      setDeletingAgent(null);
    }
  };


  const fetchAgentDetails = async (agentName: string) => {
    try {
      const agentData = await apiClient.agentJsonRequest(`/v1/agents/${agentName}`);
      return agentData;
    } catch (error) {
      console.error('Error fetching agent details:', error);
      toast.error(`Failed to load agent: ${agentName}`);
      throw error;
    }
  };

  const handleAgentSelect = async (agent: Agent) => {
    setSelectedAgent(agent.name);
    try {
      // Fetch full agent details
      const fullAgentData = await fetchAgentDetails(agent.name);
      onAgentSelect(fullAgentData);
      onOpenChange(false);
      setSelectedAgent(null);
    } catch (error) {
      setSelectedAgent(null);
    }
  };

  useEffect(() => {
    if (open) {
      fetchAgents();
    }
  }, [open]);

  const filteredAgents = agents.filter(agent =>
    agent.name.toLowerCase().includes(searchTerm.toLowerCase()) ||
    agent.description.toLowerCase().includes(searchTerm.toLowerCase())
  );

  const defaultTrigger = (
    <Button
      variant="outline"
      className="justify-start text-left font-normal"
    >
      <Bot className="mr-2 h-4 w-4" />
      Select Agent
    </Button>
  );

  return (
    <Popover open={open} onOpenChange={onOpenChange}>
      <PopoverTrigger asChild>
        {triggerButton || defaultTrigger}
      </PopoverTrigger>
      <PopoverContent 
        className="w-[400px] p-0 border border-border/50 shadow-lg" 
        style={{
          backgroundColor: 'rgba(0, 0, 0, 0.1)',
          backdropFilter: 'blur(12px)',
          WebkitBackdropFilter: 'blur(12px)',
          border: '1px solid rgba(255, 255, 255, 0.1)'
        }}
        side="right"
        align="start"
      >
        <div className="p-4 border-b">
          <div className="flex items-center justify-between mb-3">
            <h4 className="text-sm font-medium">Select an agent</h4>
            {onCreateAgent && (
              <Button
                variant="ghost"
                size="sm"
                onClick={() => {
                  onCreateAgent();
                  onOpenChange(false);
                }}
                className="h-7 px-2 text-xs text-muted-foreground hover:text-green-600 hover:bg-green-50 dark:hover:bg-green-900/20 dark:hover:text-green-400 transition-colors"
              >
                Create Agent
              </Button>
            )}
          </div>
          
          {/* Search Input */}
          <div className="relative">
            <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
            <Input
              value={searchTerm}
              onChange={(e) => setSearchTerm(e.target.value)}
              placeholder="Search agents..."
              className="pl-9 focus:border-positive-trend/60 focus:ring-0 focus:ring-offset-0 focus:shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-positive-trend/60 transition-all duration-200"
            />
          </div>
        </div>
        
        {/* Agents List */}
        <div className="max-h-[300px] overflow-y-auto">
          {loading ? (
            <div className="flex items-center justify-center py-6">
              <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
              <span className="ml-2 text-sm text-muted-foreground">Loading agents...</span>
            </div>
          ) : filteredAgents.length === 0 ? (
            <div className="py-6 text-center">
              <Bot className="h-8 w-8 mx-auto text-muted-foreground mb-2" />
              <p className="text-sm text-muted-foreground">
                {searchTerm ? 'No agents found' : 'No agents available'}
              </p>
            </div>
          ) : (
            <div className="p-2 space-y-1">
              {filteredAgents.map((agent) => (
                <div key={agent.name} className="flex items-center group">
                  <Button
                    variant="ghost"
                    className="flex-1 justify-start h-auto p-3 text-left hover:bg-accent/50 rounded-none"
                    onClick={() => handleAgentSelect(agent)}
                    disabled={selectedAgent === agent.name}
                  >
                    <div className="flex items-start space-x-3 w-full">
                      <div className="flex-shrink-0 mt-0.5">
                        {selectedAgent === agent.name ? (
                          <Loader2 className="h-4 w-4 animate-spin text-primary" />
                        ) : (
                          <Bot className="h-4 w-4 text-muted-foreground" />
                        )}
                      </div>
                      <div className="flex-1 min-w-0">
                        <p className="text-sm leading-relaxed whitespace-normal break-words">
                          <span className="font-medium">{agent.name}</span>
                          <span className="text-muted-foreground">: {agent.description}</span>
                        </p>
                      </div>
                    </div>
                  </Button>
                  <button
                    type="button"
                    className="h-8 w-8 p-0 opacity-0 group-hover:opacity-100 transition-opacity rounded-md text-red-500 hover:bg-red-500/10 flex items-center justify-center mr-1"
                    title="Delete agent"
                    onClick={(e) => {
                      e.stopPropagation();
                      handleDeleteAgent(agent.name);
                    }}
                    disabled={deletingAgent === agent.name}
                  >
                    {deletingAgent === agent.name ? (
                      <Loader2 className="h-4 w-4 animate-spin" />
                    ) : (
                      <Trash2 className="h-4 w-4" />
                    )}
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </PopoverContent>
    </Popover>
  );
};

export default AgentsSelectionModal;
