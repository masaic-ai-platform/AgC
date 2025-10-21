import React, { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

import { Card, CardContent } from '@/components/ui/card';
import { Bot, Loader2, RefreshCcw, Trash2 } from 'lucide-react';
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



interface AgentListProps {
  className?: string;
  onAgentSelect?: (agent: Agent) => void;
}

const AgentList: React.FC<AgentListProps> = ({ className = '', onAgentSelect }) => {
  const [agents, setAgents] = useState<Agent[]>([]);
  const [loading, setLoading] = useState(false);
  const [selectedAgent, setSelectedAgent] = useState<string | null>(null);
  const [deletingAgent, setDeletingAgent] = useState<string | null>(null);

  const fetchAgents = async () => {
    setLoading(true);
    try {
      const agentsData = await apiClient.agentJsonRequest('/v1/agents') as Agent[];
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
      const response = await apiClient.agentRequest(`/v1/agents/${agentName}`, { method: 'DELETE' });
      
      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || `Failed to delete agent. Status: ${response.status}`);
      }
      
      setAgents(prev => prev.filter(a => a.name !== agentName));
      toast.success(`Deleted agent: ${agentName}`);
    } catch (error) {
      console.error('Error deleting agent:', error);
      const errorMessage = error instanceof Error ? error.message : `Failed to delete agent: ${agentName}`;
      toast.error(errorMessage);
    } finally {
      setDeletingAgent(null);
    }
  };

  const fetchAgentDetails = async (agentName: string) => {
    try {
      const agentData = await apiClient.agentJsonRequest(`/v1/agents/${agentName}`) as Agent;
      return agentData;
    } catch (error) {
      console.error('Error fetching agent details:', error);
      toast.error(`Failed to load agent: ${agentName}`);
      throw error;
    }
  };

  const handleAgentClick = async (agent: Agent) => {
    if (!onAgentSelect) return;
    
    setSelectedAgent(agent.name);
    try {
      // Fetch full agent details
      const fullAgentData = await fetchAgentDetails(agent.name);
      onAgentSelect(fullAgentData);
      setSelectedAgent(null);
    } catch (error) {
      setSelectedAgent(null);
    }
  };

  useEffect(() => {
    fetchAgents();
  }, []);



  if (loading) {
    return (
      <div className={`flex items-center justify-center py-8 ${className}`}>
        <Loader2 className="h-6 w-6 animate-spin text-muted-foreground" />
        <span className="ml-2 text-sm text-muted-foreground">Loading agents...</span>
      </div>
    );
  }

  return (
    <div className={`space-y-4 ${className}`}>
      <div className="flex items-center space-x-2 mb-4">
        <Bot className="h-5 w-5 text-muted-foreground" />
        <h3 className="text-lg font-medium">Available Agents</h3>
        <Badge variant="secondary" className="text-xs">
          {agents.length}
        </Badge>
        <button
          type="button"
          onClick={fetchAgents}
          className="ml-auto h-8 w-8 p-0 rounded-md text-muted-foreground hover:text-foreground hover:bg-muted/50 flex items-center justify-center"
          title="Refresh"
        >
          <RefreshCcw className="h-4 w-4" />
        </button>
      </div>

      {agents.length === 0 ? (
        <Card>
          <CardContent className="py-8 text-center">
            <Bot className="h-12 w-12 mx-auto text-muted-foreground mb-4" />
            <p className="text-muted-foreground">No agents available</p>
          </CardContent>
        </Card>
      ) : (
        <div className="space-y-3">
          {agents.map((agent) => {
            const isSelecting = selectedAgent === agent.name;
            
            return (
              <Card 
                key={agent.name} 
                className="overflow-hidden cursor-pointer hover:bg-muted/50 transition-colors"
                onClick={() => handleAgentClick(agent)}
              >
                <CardContent className="p-4">
                  {/* Agent Name and Description */}
                  <div className="flex items-start justify-between">
                    <p className="text-sm leading-relaxed whitespace-normal break-words flex-1">
                      <span className="font-medium">{agent.name}</span>
                      <span className="text-muted-foreground">: {agent.description}</span>
                    </p>
                    <div className="flex items-center space-x-2 ml-2">
                      {isSelecting && (
                        <Loader2 className="h-4 w-4 animate-spin text-muted-foreground mt-0.5" />
                      )}
                      <button
                        type="button"
                        className="h-8 w-8 p-0 rounded-md text-red-500 hover:bg-red-500/10 flex items-center justify-center"
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
                  </div>
                </CardContent>
              </Card>
            );
          })}
        </div>
      )}
    </div>
  );
};

export default AgentList;
