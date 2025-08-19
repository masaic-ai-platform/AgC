import React, { useState, useEffect } from 'react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';

import { Card, CardContent } from '@/components/ui/card';
import { Bot, Loader2 } from 'lucide-react';
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

  const getAgentsHeaders = async (): Promise<HeadersInit> => {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
    };

    // Only add X-Google-Token if auth is enabled (no Authorization header for agents API)
    try {
      const response = await fetch(`${API_URL}/v1/dashboard/platform/info`);
      const platformInfo = await response.json();
      const authEnabled = platformInfo.authConfig?.enabled || false;
      
      if (authEnabled) {
        const googleToken = localStorage.getItem('google_token');
        if (googleToken) {
          headers['X-Google-Token'] = googleToken;
        }
      }
    } catch (error) {
      console.warn('Failed to check auth status:', error);
    }

    return headers;
  };

  const fetchAgents = async () => {
    setLoading(true);
    try {
      const headers = await getAgentsHeaders();
      const response = await fetch(`${API_URL}/v1/agents`, {
        method: 'GET',
        headers,
      });

      if (!response.ok) {
        throw new Error('Failed to fetch agents');
      }

      const agentsData = await response.json();
      setAgents(agentsData);
    } catch (error) {
      console.error('Error fetching agents:', error);
      toast.error('Failed to load agents');
      setAgents([]);
    } finally {
      setLoading(false);
    }
  };

  const fetchAgentDetails = async (agentName: string) => {
    try {
      const headers = await getAgentsHeaders();
      const response = await fetch(`${API_URL}/v1/agents/${agentName}`, {
        method: 'GET',
        headers,
      });

      if (!response.ok) {
        throw new Error(`Failed to fetch agent: ${agentName}`);
      }

      const agentData = await response.json();
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
                    {isSelecting && (
                      <Loader2 className="h-4 w-4 animate-spin text-muted-foreground ml-2 mt-0.5" />
                    )}
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
