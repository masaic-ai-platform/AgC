import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Eye, EyeOff, Loader2, Check } from 'lucide-react';
import { API_URL } from '@/config';
import { apiClient } from '@/lib/api';
import { toast } from 'sonner';

interface E2BModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

interface E2BConfig {
  server_label: string;
  url: string;
  apiKey: string;
}

const E2BModal: React.FC<E2BModalProps> = ({
  open,
  onOpenChange,
}) => {
  const [url, setUrl] = useState('');
  const [label, setLabel] = useState('');
  const [apiKey, setApiKey] = useState('');
  const [showApiKey, setShowApiKey] = useState(false);
  const [isConnecting, setIsConnecting] = useState(false);
  const [isEditing, setIsEditing] = useState(false);

  // Load existing configuration from localStorage
  useEffect(() => {
    if (open) {
      const savedConfig = localStorage.getItem('platform_e2b_mcp');
      if (savedConfig) {
        try {
          const config: E2BConfig = JSON.parse(savedConfig);
          setUrl(config.url || '');
          setLabel(config.server_label || '');
          setApiKey(config.apiKey || '');
          setIsEditing(true);
        } catch (error) {
          console.error('Failed to parse saved E2B config:', error);
        }
      } else {
        // Reset form for new configuration
        setUrl('');
        setLabel('');
        setApiKey('');
        setIsEditing(false);
      }
    }
  }, [open]);

  const handleConnect = async () => {
    setIsConnecting(true);
    
    try {
      // Prepare request body
      const requestBody = {
        serverLabel: label.trim(),
        serverUrl: url.trim(),
        headers: {
          "Content-Type": "application/json",
          "Authorization": `Bearer ${apiKey.trim()}`
        },
        testMcpTool: [
          {
            name: "run_code",
            arguments: {
              "code": "print(\"hello world\")"
            }
          }
        ]
      };

      // Make API call
      await apiClient.jsonRequest<unknown[]>('/v1/dashboard/mcp/list_actions', {
        method: 'POST',
        body: JSON.stringify(requestBody)
      });
      
      // Save to localStorage
      const config: E2BConfig = {
        server_label: label.trim(),
        url: url.trim(),
        apiKey: apiKey.trim()
      };
      localStorage.setItem('platform_e2b_mcp', JSON.stringify(config));
      
      toast.success('Successfully connected to E2B MCP server!');
      onOpenChange(false);
      
    } catch (error: unknown) {
      console.error('Failed to connect to E2B MCP server:', error);
      
      // Extract error message from the error object
      let errorMessage = 'Failed to connect to E2B MCP server';
      
      if (error && typeof error === 'object' && 'message' in error) {
        errorMessage = (error as Error).message;
      }
      
      toast.error(errorMessage);
    } finally {
      setIsConnecting(false);
    }
  };

  const resetForm = () => {
    setUrl('');
    setLabel('');
    setApiKey('');
    setShowApiKey(false);
    setIsConnecting(false);
    setIsEditing(false);
  };

  // Reset form when modal closes
  useEffect(() => {
    if (!open) {
      resetForm();
    }
  }, [open]);

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-full max-w-md">
        <DialogHeader className="text-center">
          <DialogTitle className="text-xl font-semibold">
            Connect to AgC Macro
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          {/* URL Field */}
          <div className="space-y-2">
            <Label htmlFor="url" className="text-sm font-medium">
              URL
            </Label>
            <Input
              id="url"
              value={url}
              onChange={(e) => setUrl(e.target.value)}
              placeholder="https://e2b.example.com"
              disabled={isConnecting}
              className="bg-muted/50 border border-border focus:border-positive-trend/60 focus:ring-0 focus:ring-offset-0 focus:shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-positive-trend/60 transition-all duration-200"
              style={{ 
                boxShadow: 'none !important',
                outline: 'none !important'
              }}
            />
          </div>

          {/* Label Field */}
          <div className="space-y-2">
            <Label htmlFor="label" className="text-sm font-medium">
              Label
            </Label>
            <Input
              id="label"
              value={label}
              onChange={(e) => {
                // Replace spaces and underscores with hyphens
                const formatted = e.target.value.replace(/[_\s]+/g, '-');
                setLabel(formatted);
              }}
              placeholder="my-e2b-server"
              disabled={isConnecting}
              className="bg-muted/50 border border-border focus:border-positive-trend/60 focus:ring-0 focus:ring-offset-0 focus:shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-positive-trend/60 transition-all duration-200"
              style={{ 
                boxShadow: 'none !important',
                outline: 'none !important'
              }}
            />
          </div>

          {/* API Key Field */}
          <div className="space-y-2">
            <Label htmlFor="apiKey" className="text-sm font-medium">
              API Key
            </Label>
            <div className="relative">
              <Input
                id="apiKey"
                type={showApiKey ? 'text' : 'password'}
                value={apiKey}
                onChange={(e) => setApiKey(e.target.value)}
                placeholder="Enter your API key"
                autoComplete="off"
                disabled={isConnecting}
                className="bg-muted/50 border border-border focus:border-positive-trend/60 focus:ring-0 focus:ring-offset-0 focus:shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-positive-trend/60 transition-all duration-200 pr-10"
                style={{ 
                  boxShadow: 'none !important',
                  outline: 'none !important'
                }}
              />
              <Button
                variant="ghost"
                size="sm"
                className="absolute right-2 top-1/2 transform -translate-y-1/2 h-6 w-6 p-0 hover:bg-muted/50"
                onClick={() => setShowApiKey(!showApiKey)}
                disabled={isConnecting}
              >
                {showApiKey ? (
                  <EyeOff className="h-4 w-4 text-muted-foreground" />
                ) : (
                  <Eye className="h-4 w-4 text-muted-foreground" />
                )}
              </Button>
            </div>
          </div>
        </div>

        {/* Footer */}
        <div className="flex items-center justify-end space-x-2 pt-4">
          <Button
            variant="ghost"
            onClick={() => onOpenChange(false)}
            disabled={isConnecting}
            className="hover:bg-muted/50"
          >
            Cancel
          </Button>
          <Button
            onClick={handleConnect}
            disabled={
              isConnecting ||
              !url.trim() || 
              !label.trim() || 
              !apiKey.trim()
            }
            className="bg-positive-trend hover:bg-positive-trend/90 text-white disabled:opacity-50"
          >
            {isConnecting ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin mr-2" />
                Connecting...
              </>
            ) : (
              <>
                {isEditing ? 'Update' : 'Connect'}
              </>
            )}
          </Button>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default E2BModal;
