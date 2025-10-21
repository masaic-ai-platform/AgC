import React from 'react';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import { AlertTriangle, Eye, EyeOff, Copy, Check } from 'lucide-react';
import { Input } from '@/components/ui/input';
import { Button } from '@/components/ui/button';
import { apiClient } from '@/lib/api';

interface ClientSideToolsPrerequisiteDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  agentName: string;
}

const ClientSideToolsPrerequisiteDialog: React.FC<ClientSideToolsPrerequisiteDialogProps> = ({
  open,
  onOpenChange,
  agentName,
}) => {
  const [credentials, setCredentials] = React.useState('');
  const [showCredentials, setShowCredentials] = React.useState(false);
  const [isLoading, setIsLoading] = React.useState(false);
  const [error, setError] = React.useState<string | null>(null);
  const [isCopied, setIsCopied] = React.useState(false);

  const fetchCredentials = async () => {
    setIsLoading(true);
    setError(null);
    try {
      const response = await apiClient.agentJsonRequest(`/v1/agents/${agentName}/credentials/client_side`) as { creds: string };
      setCredentials(response.creds || '');
    } catch (err) {
      console.error('Error fetching credentials:', err);
      setError('Failed to load credentials. Please try again.');
    } finally {
      setIsLoading(false);
    }
  };

  React.useEffect(() => {
    if (open && agentName) {
      fetchCredentials();
    }
  }, [open, agentName]);

  const handleCopyCredentials = async () => {
    try {
      await navigator.clipboard.writeText(credentials);
      setIsCopied(true);
      // Reset the copied state after 2 seconds
      setTimeout(() => setIsCopied(false), 2000);
    } catch (err) {
      console.error('Failed to copy credentials:', err);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-2xl">
        <DialogHeader>
          <div className="flex items-center gap-2">
            <AlertTriangle className="h-5 w-5 text-amber-500" />
            <DialogTitle>Prerequisite Required</DialogTitle>
          </div>
          <DialogDescription className="text-left">
            The agent "{agentName}" contains client-side tools that need to execute on client machine.{" "}
            <a 
              href="#" 
              className="text-blue-600 hover:text-blue-800 underline"
              onClick={(e) => {
                e.preventDefault();
                // You can add setup instructions or open a help page here
                console.log('Setup link clicked');
              }}
            >
              Setup Instructions
            </a>
          </DialogDescription>
        </DialogHeader>
        
        <div className="space-y-4">
          {/* Credentials Section */}
          <div className="space-y-2">
            <div className="relative">
              <Input
                id="credentials"
                type={showCredentials ? 'text' : 'password'}
                value={credentials}
                readOnly
                className="pl-16 pr-20 bg-white cursor-default"
                disabled={isLoading}
              />
              <div className="absolute left-3 top-1/2 transform -translate-y-1/2 text-sm font-medium text-gray-500 pointer-events-none">
                Creds
              </div>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="absolute right-8 top-0 h-full px-3 py-2 hover:bg-transparent"
                onClick={() => setShowCredentials(!showCredentials)}
                disabled={isLoading || !credentials}
              >
                {showCredentials ? (
                  <EyeOff className="h-4 w-4 text-gray-400" />
                ) : (
                  <Eye className="h-4 w-4 text-gray-400" />
                )}
              </Button>
              <Button
                type="button"
                variant="ghost"
                size="sm"
                className="absolute right-0 top-0 h-full px-3 py-2 hover:bg-transparent"
                onClick={handleCopyCredentials}
                disabled={isLoading || !credentials}
              >
                {isCopied ? (
                  <Check className="h-4 w-4 text-green-500" />
                ) : (
                  <Copy className="h-4 w-4 text-gray-400" />
                )}
              </Button>
            </div>
            
            {/* Circular Progress */}
            {isLoading && (
              <div className="flex flex-col items-center space-y-2">
                <div className="relative w-8 h-8">
                  <div className="absolute inset-0 rounded-full border-2 border-gray-200"></div>
                  <div className="absolute inset-0 rounded-full border-2 border-green-500 border-t-transparent animate-spin"></div>
                </div>
                <p className="text-xs text-gray-500 text-center">Loading credentials...</p>
              </div>
            )}
            
            {/* Error Message */}
            {error && (
              <div className="text-sm text-red-600 bg-red-50 border border-red-200 rounded p-2">
                {error}
              </div>
            )}
          </div>
          
          <div className="bg-amber-50 border border-amber-200 rounded-lg p-4">
            <h4 className="font-medium text-amber-800 mb-3">What are client-side tools?</h4>
            <div className="space-y-2 text-sm text-amber-700">
              <p>
                Client-side tools execute directly on your <strong>local machine</strong>, <strong>client server</strong>, 
                or <strong>private cloud environment</strong>.
              </p>
              <p>
                This ensures <strong>complete data protection</strong> and <strong>zero PII leakage</strong> to external 
                services or LLMs. Your sensitive data never leaves your controlled environment.
              </p>
            </div>
          </div>
          
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div className="flex flex-col items-center text-center space-y-2">
              <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center">
                <span className="text-sm font-medium text-blue-600">1</span>
              </div>
              <div>
                <p className="text-sm font-medium">Setup Instruction</p>
                <p className="text-xs text-muted-foreground">
                  Follow the setup guide to configure your environment
                </p>
              </div>
            </div>
            
            <div className="flex flex-col items-center text-center space-y-2">
              <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center">
                <span className="text-sm font-medium text-blue-600">2</span>
              </div>
              <div>
                <p className="text-sm font-medium">Deploy Locally</p>
                <p className="text-xs text-muted-foreground">
                  Install on your machine, server, or private cloud
                </p>
              </div>
            </div>
            
            <div className="flex flex-col items-center text-center space-y-2">
              <div className="w-8 h-8 bg-blue-100 rounded-full flex items-center justify-center">
                <span className="text-sm font-medium text-blue-600">3</span>
              </div>
              <div>
                <p className="text-sm font-medium">Use Securely</p>
                <p className="text-xs text-muted-foreground">
                  Process data with zero external exposure
                </p>
              </div>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default ClientSideToolsPrerequisiteDialog;
