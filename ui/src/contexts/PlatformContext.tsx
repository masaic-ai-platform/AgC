import React, { createContext, useContext, useState, useEffect } from 'react';
import { API_URL } from '@/config';
import { apiClient } from '@/lib/api';
import { useAuth } from './AuthContext';

interface ModelSettings {
  settingsType: string;
  apiKey: string;
  model: string;
  bearerToken: string;
  qualifiedModelName: string;
}

interface VectorStoreInfo {
  isEnabled: boolean;
}

interface PyInterpreterSettings {
  systemSettingsType: string;
  pyInterpreterServer: any;
  isEnabled: boolean;
}

interface PlatformInfo {
  version: string;
  modelSettings: ModelSettings;
  vectorStoreInfo: VectorStoreInfo;
  pyInterpreterSettings?: PyInterpreterSettings;
}

interface PlatformContextType {
  platformInfo: PlatformInfo | null;
  isLoading: boolean;
  error: string | null;
  refetchPlatformInfo: () => Promise<void>;
}

const defaultPlatformInfo: PlatformInfo = {
  version: 'UNKNOWN',
  modelSettings: {
    settingsType: 'RUNTIME',
    apiKey: '',
    model: '',
    bearerToken: 'Bearer ',
    qualifiedModelName: ''
  },
  vectorStoreInfo: {
    isEnabled: true
  },
  pyInterpreterSettings: {
    systemSettingsType: 'RUNTIME',
    pyInterpreterServer: null,
    isEnabled: true
  }
};

const PlatformContext = createContext<PlatformContextType>({
  platformInfo: defaultPlatformInfo,
  isLoading: false,
  error: null,
  refetchPlatformInfo: async () => {}
});

export const usePlatformInfo = () => {
  const context = useContext(PlatformContext);
  if (!context) {
    throw new Error('usePlatformInfo must be used within a PlatformProvider');
  }
  return context;
};

interface PlatformProviderProps {
  children: React.ReactNode;
}

export const PlatformProvider: React.FC<PlatformProviderProps> = ({ children }) => {
  const [platformInfo, setPlatformInfo] = useState<PlatformInfo | null>(defaultPlatformInfo);
  const [isLoading, setIsLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { isAuthenticated, authEnabled, isLoading: authLoading } = useAuth();

  const fetchPlatformInfo = async () => {
    // Only fetch if auth is disabled OR user is authenticated
    const shouldFetch = !authEnabled || isAuthenticated;
    
    if (!shouldFetch) {
      console.log('Skipping platform info fetch - user not authenticated');
      return;
    }

    setIsLoading(true);
    setError(null);
    
    try {
      const response = await apiClient.rawRequest('/v1/dashboard/platform/features');
      
      if (!response.ok) {
        throw new Error(`HTTP error! status: ${response.status}`);
      }
      
      const data = await response.json();
      setPlatformInfo(data);
    } catch (err) {
      console.error('Error fetching platform info:', err);
      setError(err instanceof Error ? err.message : 'Failed to fetch platform info');
      // Keep default values on error
      setPlatformInfo(defaultPlatformInfo);
    } finally {
      setIsLoading(false);
    }
  };

  // Fetch platform info when authentication state changes
  useEffect(() => {
    // Wait for auth to finish loading
    if (authLoading) {
      return;
    }

    fetchPlatformInfo();

    // Listen for page visibility changes (to detect page refresh/focus)
    const handleVisibilityChange = () => {
      if (document.visibilityState === 'visible') {
        fetchPlatformInfo();
      }
    };

    // Listen for focus events (when user returns to the tab)
    const handleFocus = () => {
      fetchPlatformInfo();
    };

    document.addEventListener('visibilitychange', handleVisibilityChange);
    window.addEventListener('focus', handleFocus);

    return () => {
      document.removeEventListener('visibilitychange', handleVisibilityChange);
      window.removeEventListener('focus', handleFocus);
    };
  }, [isAuthenticated, authEnabled, authLoading]);

  const refetchPlatformInfo = async () => {
    await fetchPlatformInfo();
  };

  return (
    <PlatformContext.Provider
      value={{
        platformInfo,
        isLoading,
        error,
        refetchPlatformInfo
      }}
    >
      {children}
    </PlatformContext.Provider>
  );
};

export default PlatformContext; 