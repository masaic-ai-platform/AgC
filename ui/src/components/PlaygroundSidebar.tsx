import React, { useState, useEffect } from 'react';
import { cn } from '@/lib/utils';
import { Button } from '@/components/ui/button';
import { useAuth } from '@/contexts/AuthContext';
import AgentsSelectionModal from './AgentsSelectionModal';
import { 
  MessageSquare, 
  Key,
  Github,
  MessageCircle,
  Database,
  BarChart3,
  Shield,
  Sparkles,
  Plus,
  Server,
  LogOut,
  Bot,
  Activity,
  Brain,
  Zap
} from 'lucide-react';
import { apiClient } from '@/lib/api';

interface PlaygroundSidebarProps {
  activeTab: string;
  onTabChange: (tab: string) => void;
  className?: string;
  onAgentSelect?: (agent: any) => void;
  onNewAgentBuilder?: () => void;
}

interface Partner {
  code: string;
  name: string;
  category: 'VECTOR_DB' | 'EVALS' | 'OBSERVABILITY' | 'COMPUTE';
  enabled: boolean;
  deploymentLink: string;
}

interface PlatformInfo {
  partners: {
    details: Partner[];
  };
}

const PlaygroundSidebar: React.FC<PlaygroundSidebarProps> = ({ 
  activeTab, 
  onTabChange,
  className = '',
  onAgentSelect,
  onNewAgentBuilder
}) => {
  const { logout, authEnabled, isAuthenticated } = useAuth();
  const [agentsModalOpen, setAgentsModalOpen] = useState(false);
  const [platformInfo, setPlatformInfo] = useState<PlatformInfo | null>(null);
  const [isLoadingPartners, setIsLoadingPartners] = useState(true);

  const mainOptions = [
    { id: 'agents', label: 'Agents', icon: Bot, clickable: true },
    { id: 'responses', label: 'AgC API', icon: MessageSquare },
  ];

  // Get icon for partner category
  const getPartnerIcon = (category: string) => {
    switch (category) {
      case 'VECTOR_DB':
        return Database;
      case 'EVALS':
        return Brain;
      case 'OBSERVABILITY':
        return BarChart3;
      case 'COMPUTE':
        return Zap;
      default:
        return Activity; // fallback icon
    }
  };

  // Fetch platform info to get partner details
  useEffect(() => {
    const fetchPlatformInfo = async () => {
      try {
        setIsLoadingPartners(true);
        const data = await apiClient.jsonRequest<any>('/v1/dashboard/platform/features');
        setPlatformInfo(data);
        
        // Handle userId based on auth config
        if (data.authConfig?.enabled === false) {
          // Auth is disabled, ensure we have a userId
          let userId = localStorage.getItem('platform_userId');
          if (!userId) {
            // Generate new random userId: user_ + 9 alphanumeric chars
            const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789';
            const randomId = 'user_' + Array.from({length: 9}, () => chars.charAt(Math.floor(Math.random() * chars.length))).join('');
            localStorage.setItem('platform_userId', randomId);
            console.log('Generated new userId:', randomId);
          }
        } else if (data.authConfig?.enabled === true) {
          // Auth is enabled, clear userId if it exists
          const existingUserId = localStorage.getItem('platform_userId');
          if (existingUserId) {
            localStorage.removeItem('platform_userId');
            console.log('Auth enabled, cleared userId');
          }
        }
      } catch (error) {
        console.error('Failed to fetch platform info:', error);
      } finally {
        setIsLoadingPartners(false);
      }
    };

    fetchPlatformInfo();
  }, []);

  // Generate partner links dynamically
  const partnerLinks = platformInfo?.partners?.details
    .filter(partner => partner.enabled)
    .map(partner => ({
      id: `partner-${partner.code}`,
      label: partner.name,
      icon: getPartnerIcon(partner.category),
      link: partner.deploymentLink,
      category: partner.category,
      code: partner.code,
      // Special handling for E2B: if it's E2B and has no link, make it clickable internally
      clickable: partner.code === 'e2b' && !partner.deploymentLink
    })) || [];

  // Static options that appear under Completions
  const staticCompletionOptions = [
    // Partner links will be dynamically inserted here
    ...partnerLinks,
    // New clickable option directly below partner links
    { id: 'masaic-mocky', label: 'Mocky', icon: Sparkles, clickable: true },
    { id: 'add-model', label: 'Add Model', icon: Plus, clickable: true },
    { id: 'compliance', label: 'Compliance', icon: Shield },
  ];

  const bottomOptions = [
    { id: 'api-keys', label: 'API Keys', icon: Key },
  ];

  // Add Sign Out option if authentication is enabled and user is authenticated
  const signOutOption = authEnabled && isAuthenticated ? 
    { id: 'sign-out', label: 'Sign Out', icon: LogOut, action: 'signout' } : null;

  return (
    <div className={cn("bg-background border-r border-border h-full flex flex-col", className)}>
      {/* Main Options */}
      <div className="flex-1 p-2 space-y-1">
        {mainOptions.map((option) => {
          const Icon = option.icon;
          const isClickable = option.clickable;
          
          // Special handling for agents option
          if (option.id === 'agents') {
            return (
              <AgentsSelectionModal
                key={option.id}
                open={agentsModalOpen}
                onOpenChange={setAgentsModalOpen}
                onAgentSelect={(agent) => {
                  if (onAgentSelect) {
                    onAgentSelect(agent);
                  }
                  setAgentsModalOpen(false);
                }}
                onNewAgentBuilder={onNewAgentBuilder}
                triggerButton={
                  <Button
                    variant={activeTab === option.id ? "secondary" : "ghost"}
                    className={`w-full justify-start text-xs h-8 ${
                      activeTab === option.id 
                        ? 'bg-accent text-accent-foreground' 
                        : 'text-muted-foreground hover:text-foreground hover:bg-accent/50'
                    }`}
                    onClick={() => {
                      setAgentsModalOpen(true);
                      onTabChange(option.id);
                    }}
                  >
                    <Icon className="h-3 w-3 mr-2" />
                    {option.label}
                  </Button>
                }
              />
            );
          }
          
          return (
            <Button
              key={option.id}
              variant={activeTab === option.id ? "secondary" : "ghost"}
              className={`w-full justify-start text-xs h-8 ${
                activeTab === option.id 
                  ? 'bg-accent text-accent-foreground' 
                  : 'text-muted-foreground hover:text-foreground hover:bg-accent/50'
              }`}
              onClick={() => onTabChange(option.id)}
            >
              <Icon className="h-3 w-3 mr-2" />
              {option.label}
            </Button>
          );
        })}

        {/* Partner Links and Static Completion Options */}
        <div className="space-y-1 mt-1">
          {isLoadingPartners ? (
            <div className="flex items-center justify-center p-2">
              <div className="animate-spin rounded-full h-4 w-4 border-b-2 border-primary"></div>
            </div>
          ) : (
            staticCompletionOptions.map((option) => {
              const Icon = option.icon;

              // External link behaviour for partner links (only if they have a link)
              if ('link' in option && option.link) {
                return (
                  <Button
                    key={option.id}
                    variant="ghost"
                    className="w-full justify-start text-xs h-8 text-muted-foreground hover:text-foreground hover:bg-accent/50"
                    onClick={() => window.open(option.link, '_blank')}
                  >
                    <Icon className="h-3 w-3 mr-2" />
                    {option.label}
                  </Button>
                );
              }

              // Clickable internal option (e.g., Masaic Mocky, E2B)
              if ('clickable' in option && option.clickable) {
                // Special handling for E2B partner - open E2B modal
                const tabId = 'code' in option && option.code === 'e2b' ? 'e2b-server' : option.id;
                
                return (
                  <Button
                    key={option.id}
                    variant={activeTab === tabId ? 'secondary' : 'ghost'}
                    className={`w-full justify-start text-xs h-8 ${
                      activeTab === tabId
                        ? 'bg-accent text-accent-foreground'
                        : 'text-muted-foreground hover:text-foreground hover:bg-accent/50'
                    }`}
                    onClick={() => onTabChange(tabId)}
                  >
                    <Icon className="h-3 w-3 mr-2" />
                    {option.label}
                  </Button>
                );
              }

              // Default disabled static option
              return (
                <Button
                  key={option.id}
                  variant="ghost"
                  className="w-full justify-start text-xs h-8 text-muted-foreground opacity-50 cursor-default"
                  disabled
                >
                  <Icon className="h-3 w-3 mr-2" />
                  {option.label}
                </Button>
              );
            })
          )}
        </div>
      </div>

      {/* Bottom Options */}
      <div className="p-2 border-t border-border space-y-1">
        {bottomOptions.map((option) => {
          const Icon = option.icon;
          
          return (
            <Button
              key={option.id}
              variant="ghost"
              className="w-full justify-start text-xs h-8 text-muted-foreground hover:text-foreground hover:bg-accent/50"
              onClick={() => onTabChange(option.id)}
            >
              <Icon className="h-3 w-3 mr-2" />
              {option.label}
            </Button>
          );
        })}
        
        {/* Sign Out Button - Only shown when authenticated */}
        {signOutOption && (
          <Button
            key={signOutOption.id}
            variant="ghost"
            className="w-full justify-start text-xs h-8 text-muted-foreground hover:text-red-600 hover:bg-red-50 dark:hover:bg-red-950/20"
            onClick={() => {
              logout();
            }}
          >
            <signOutOption.icon className="h-3 w-3 mr-2" />
            {signOutOption.label}
          </Button>
        )}
      </div>
    </div>
  );
};

export default PlaygroundSidebar; 
