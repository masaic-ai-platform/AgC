import React, { useState, useEffect, useMemo } from 'react';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import { Badge } from '@/components/ui/badge';
import { Search, Check, ChevronDown, Trash2, RefreshCw } from 'lucide-react';
import { apiClient } from '@/lib/api';
import { toast } from 'sonner';

interface Model {
  name: string;
  modelSyntax: string;
  providerName: string;
  providerDescription: string;
  isEmbeddingModel?: boolean;
}

interface Provider {
  name: string;
  description: string;
  supportedModels: Model[];
}

interface ModelSelectorProps {
  modelProvider: string;
  setModelProvider: (provider: string) => void;
  modelName: string;
  setModelName: (name: string) => void;
  className?: string;
  disabled?: boolean;
  placeholder?: string;
  /** Optional callback when model selection requires API key */
  onApiKeyRequired?: (provider: string) => void;
  /** Optional callback for custom model validation */
  onModelValidate?: (provider: string, modelName: string) => boolean;
}

const ModelSelector: React.FC<ModelSelectorProps> = ({
  modelProvider,
  setModelProvider,
  modelName,
  setModelName,
  className = "",
  disabled = false,
  placeholder = "Select a model...",
  onApiKeyRequired,
  onModelValidate
}) => {
  const [providers, setProviders] = useState<Provider[]>([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [refreshTrigger, setRefreshTrigger] = useState(0);
  const [isOpen, setIsOpen] = useState(false);
  const [searchQuery, setSearchQuery] = useState('');

  // Fetch models from API
  useEffect(() => {
    const fetchModels = async () => {
      setLoading(true);
      setError(null);
      
      try {
        const data = await apiClient.jsonRequest<any[]>('/v1/dashboard/models');
        setProviders(data);
      } catch (err) {
        console.error('Error fetching models:', err);
        
        // Check if it's an authentication error
        if (err instanceof Error && err.message === 'Authentication required') {
          // This will trigger the login screen via ApiClient's handleAuthError
          return;
        }
        
        // For other errors, show appropriate message
        const errorMessage = err instanceof Error && err.message.includes('Network error')
          ? 'Cannot connect to API server.'
          : 'Failed to load models.';
        setError(errorMessage);
        setProviders([]);
      } finally {
        setLoading(false);
      }
    };

    fetchModels();
  }, [refreshTrigger]);

  // Get all available models from providers - memoized to prevent unnecessary recalculations
  const allModels = useMemo(() => {
    // Get models from API providers (defensive against undefined server response)
    const safeProviders = Array.isArray(providers) ? providers : [];
    const apiModels = safeProviders.flatMap((provider: any) =>
      (provider?.supportedModels ?? [])
        .filter((model: any) => !model?.isEmbeddingModel)
        .map((model: any) => ({
          ...model,
          providerName: provider?.name ?? 'unknown',
          providerDescription: provider?.description ?? ''
        }))
    );

    // Get models from localStorage (own models)
    let ownModels: any[] = [];
    try {
      const savedOwnModels = localStorage.getItem('platform_own_model');
      if (savedOwnModels) {
        const ownModelsData = JSON.parse(savedOwnModels);
        const supported = Array.isArray(ownModelsData?.supportedModels) ? ownModelsData.supportedModels : [];
        ownModels = supported.map((model: any) => ({
          name: model.name,
          modelSyntax: model.modelSyntax,
          providerName: ownModelsData?.name ?? 'own model',
          providerDescription: ownModelsData?.description ?? '',
          isEmbeddingModel: false
        }));
      }
    } catch (error) {
      console.error('Error loading own models from localStorage:', error);
    }

    // Return own models first, then API models
    return [...ownModels, ...apiModels];
  }, [providers, refreshTrigger]);

  // Filter models based on search query
  const filteredModels = useMemo(() => {
    if (!searchQuery.trim()) {
      return allModels;
    }
    return allModels.filter(model => 
      model.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      model.providerName.toLowerCase().includes(searchQuery.toLowerCase())
    );
  }, [searchQuery, allModels]);

  // Listen for storage events to auto-refresh when models are saved
  useEffect(() => {
    const handleStorageChange = () => {
      setRefreshTrigger(prev => prev + 1);
    };

    window.addEventListener('storage', handleStorageChange);
    return () => window.removeEventListener('storage', handleStorageChange);
  }, []);

  // Check if API key exists for provider
  const checkApiKey = (provider: string): boolean => {
    try {
      const saved = localStorage.getItem('platform_apiKeys');
      if (!saved) return false;
      
      const savedKeys: { name: string; apiKey: string }[] = JSON.parse(saved);
      return savedKeys.some(item => item.name === provider && item.apiKey.trim());
    } catch (error) {
      console.error('Error checking API key:', error);
      return false;
    }
  };

  const handleModelSelect = (modelSyntax: string) => {
    const [provider, name] = modelSyntax.split('@');
    
    // Custom model validation if provided
    if (onModelValidate && !onModelValidate(provider, name)) {
      return;
    }
    
    // Check if API key exists for this provider
    if (!checkApiKey(provider)) {
      if (onApiKeyRequired) {
        onApiKeyRequired(provider);
        return; // Don't set the model until API key is provided
      }
    }
    
    // API key exists or not required, proceed with model selection
    setModelProvider(provider);
    setModelName(name);
    setIsOpen(false);
    setSearchQuery('');
  };

  const handleDeleteModel = (modelSyntax: string) => {
    try {
      const existingOwnModels = localStorage.getItem('platform_own_model');
      if (existingOwnModels) {
        const ownModelsData = JSON.parse(existingOwnModels);
        
        // Remove the model from supportedModels array
        ownModelsData.supportedModels = ownModelsData.supportedModels.filter(
          (model: any) => model.modelSyntax !== modelSyntax
        );
        
        localStorage.setItem('platform_own_model', JSON.stringify(ownModelsData));
        
        // Trigger refresh to update the dropdown
        setRefreshTrigger(prev => prev + 1);
        
        // Also dispatch storage event for other components
        window.dispatchEvent(new Event('storage'));

        toast.success('Model deleted successfully');
      }
    } catch (error) {
      console.error('Error deleting model:', error);
      toast.error('Failed to delete model');
    }
  };

  const getSelectedModelInfo = () => {
    const model = allModels.find(m => m.modelSyntax === `${modelProvider}@${modelName}`);
    return model || { name: placeholder, providerName: '' };
  };

  const refreshModels = () => {
    setRefreshTrigger(prev => prev + 1);
  };

  // Reset search when modal opens/closes to prevent empty states
  useEffect(() => {
    if (!isOpen) {
      setSearchQuery('');
    }
  }, [isOpen]);

  const selectedModelInfo = getSelectedModelInfo();

  // Group models by provider
  const groupedModels = filteredModels.reduce((acc, model) => {
    if (!acc[model.providerName]) {
      acc[model.providerName] = [];
    }
    acc[model.providerName].push(model);
    return acc;
  }, {} as Record<string, Model[]>);

  return (
    <div className={`flex items-center space-x-2 ${className}`}>
      <Popover open={isOpen} onOpenChange={setIsOpen}>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            className="w-full max-w-[420px] justify-between h-9 px-3 text-sm focus:border-positive-trend/60 focus:ring-0 focus:ring-offset-0 focus:shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-positive-trend/60 transition-all duration-200"
            disabled={loading || disabled}
          >
            <div className="flex items-center space-x-2 flex-1 min-w-0">
              {selectedModelInfo.providerName && (
                <Badge variant="outline" className="text-xs shrink-0">
                  {selectedModelInfo.providerName}
                </Badge>
              )}
              <span className="truncate">
                {loading ? "Loading models..." : selectedModelInfo.name}
              </span>
            </div>
            <ChevronDown className="h-4 w-4 opacity-50 shrink-0" />
          </Button>
        </PopoverTrigger>
        
        <PopoverContent 
          className="w-[400px] p-0 border border-border/50 shadow-lg" 
          style={{
            backgroundColor: 'rgba(0, 0, 0, 0.1)',
            backdropFilter: 'blur(12px)',
            WebkitBackdropFilter: 'blur(12px)',
            border: '1px solid rgba(255, 255, 255, 0.1)'
          }}
          align="start"
        >
          <div className="p-4 border-b">
            <div className="flex items-center justify-between mb-3">
              <h4 className="text-sm font-medium">Select a model</h4>
              <Button
                variant="ghost"
                size="sm"
                onClick={refreshModels}
                disabled={loading}
                className="h-6 w-6 p-0"
                title="Refresh models"
              >
                <RefreshCw className={`h-4 w-4 ${loading ? 'animate-spin' : ''}`} />
              </Button>
            </div>
            
            {/* Search Input */}
            <div className="relative">
              <Search className="absolute left-3 top-2.5 h-4 w-4 text-muted-foreground" />
              <Input
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                placeholder="Search models..."
                className="pl-9 focus:border-positive-trend/60 focus:ring-0 focus:ring-offset-0 focus:shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-positive-trend/60 transition-all duration-200"
              />
            </div>
          </div>
          
          {/* Models List */}
          <div className="max-h-[300px] overflow-y-auto">
            {loading ? (
              <div className="flex items-center justify-center py-8">
                <span className="text-sm text-muted-foreground">Loading models...</span>
              </div>
            ) : error ? (
              <div className="flex flex-col items-center justify-center py-8 space-y-2">
                <span className="text-sm text-muted-foreground">{error}</span>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={refreshModels}
                  className="text-xs"
                >
                  Retry
                </Button>
              </div>
            ) : filteredModels.length === 0 ? (
              <div className="flex items-center justify-center py-8">
                <span className="text-sm text-muted-foreground">No models found</span>
              </div>
            ) : (
              Object.entries(groupedModels).map(([provider, providerModels]: [string, Model[]]) => (
                <div key={provider}>
                  {Object.keys(groupedModels).length > 1 && (
                    <div className="text-xs font-medium text-muted-foreground uppercase tracking-wider py-2 px-4 bg-muted/30">
                      {provider}
                    </div>
                  )}
                  <div>
                    {providerModels.map((model) => (
                      <div key={model.modelSyntax} className="flex items-center group">
                        <Button
                          variant="ghost"
                          className="flex-1 justify-start h-auto p-4 hover:bg-positive-trend/10 hover:text-positive-trend focus:bg-positive-trend/10 focus:text-positive-trend rounded-none"
                          onClick={() => handleModelSelect(model.modelSyntax)}
                        >
                          <div className="flex items-center space-x-3 w-full">
                            <div className="flex items-center justify-center w-5 h-5">
                              {`${modelProvider}@${modelName}` === model.modelSyntax && (
                                <Check className="h-4 w-4 text-positive-trend" />
                              )}
                            </div>
                            <div className="flex items-center space-x-2 flex-1 min-w-0">
                              <span className="font-medium truncate">{model.name}</span>
                            </div>
                          </div>
                        </Button>
                        {provider === 'own model' && (
                          <Button
                            variant="ghost"
                            size="sm"
                            className="h-8 w-8 p-0 opacity-0 group-hover:opacity-100 transition-opacity hover:bg-red-500/10 hover:text-red-500 mr-2"
                            onClick={(e) => {
                              e.stopPropagation();
                              handleDeleteModel(model.modelSyntax);
                            }}
                            title="Delete model"
                          >
                            <Trash2 className="h-4 w-4" />
                          </Button>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              ))
            )}
          </div>
        </PopoverContent>
      </Popover>
    </div>
  );
};

export default ModelSelector;
