import { API_URL } from '@/config';

class ApiClient {
  private getModelApiKey(): string | null {
    try {
      // Get the selected model provider and name
      const modelProvider = localStorage.getItem('platform_modelProvider');
      const modelName = localStorage.getItem('platform_modelName');
      
      // If no model is selected, return null
      if (!modelProvider || !modelName) {
        console.warn('No model provider or name selected');
        return null;
      }
      
      // Get API keys from localStorage
      const savedApiKeys = localStorage.getItem('platform_apiKeys');
      if (!savedApiKeys) {
        console.warn('No API keys found in localStorage');
        return null;
      }
      
      const apiKeys = JSON.parse(savedApiKeys);
      
      // Find the API key for the selected model provider
      const providerKey = apiKeys.find((key: any) => key.name === modelProvider);
      if (providerKey?.apiKey) {
        console.log(`Using API key for provider: ${modelProvider}, model: ${modelName}`);
        return providerKey.apiKey;
      }
      
      // Fallback: try to use any available API key
      const firstAvailableKey = apiKeys.find((key: any) => key.apiKey);
      if (firstAvailableKey?.apiKey) {
        console.warn(`No API key found for provider: ${modelProvider}, using fallback key from: ${firstAvailableKey.name}`);
        return firstAvailableKey.apiKey;
      }
      
      console.warn(`No API key found for provider: ${modelProvider} and no fallback keys available`);
      return null;
    } catch (error) {
      console.error('Error getting model API key:', error);
      return null;
    }
  }

  private getGoogleToken(): string | null {
    return localStorage.getItem('google_token');
  }

  private async isAuthEnabled(): Promise<boolean> {
    try {
      const response = await fetch(`${API_URL}/v1/dashboard/platform/info`);
      const platformInfo = await response.json();
      return platformInfo.authConfig?.enabled || false;
    } catch {
      return false;
    }
  }

              private async getHeaders(): Promise<HeadersInit> {
                const headers: HeadersInit = {
                  'Content-Type': 'application/json',
                };

                // Add model API key (existing behavior)
                const modelApiKey = this.getModelApiKey();
                if (modelApiKey) {
                  headers['Authorization'] = `Bearer ${modelApiKey}`;
                }

                // Add Google token if auth is enabled
                const authEnabled = await this.isAuthEnabled();
                if (authEnabled) {
                  const googleToken = this.getGoogleToken();
                  if (googleToken) {
                    headers['X-Google-Token'] = googleToken;
                  }
                }

                return headers;
              }

  private handleAuthError() {
    // Clear invalid token
    localStorage.removeItem('google_token');
    // Instead of reloading, dispatch a custom event to trigger login screen
    window.dispatchEvent(new CustomEvent('auth:token-expired'));
  }

  async request(endpoint: string, options: RequestInit = {}): Promise<Response> {
    const headers = { ...await this.getHeaders(), ...options.headers };

    // Debug: Log headers being sent (only in development)
    if (process.env.NODE_ENV === 'development') {
      console.log(`API Request to ${endpoint}:`, {
        headers: headers,
        hasAuth: !!headers['Authorization'],
        hasGoogleToken: !!headers['X-Google-Token']
      });
    }

    try {
      const response = await fetch(`${API_URL}${endpoint}`, {
        ...options,
        headers,
      });

      if (response.status === 401) {
        // Handle authentication error
        this.handleAuthError();
        throw new Error('Authentication required');
      }

      return response;
    } catch (error) {
      // If it's an authentication error, re-throw it
      if (error instanceof Error && error.message === 'Authentication required') {
        throw error;
      }
      
      // For other errors (network, etc.), throw a generic error
      throw new Error('Network error');
    }
  }

  // Helper method for JSON requests
  async jsonRequest<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const response = await this.request(endpoint, options);
    
    if (!response.ok) {
      // Try to get the error response body for better error messages
      let errorMessage = `HTTP error! status: ${response.status}`;
      try {
        const errorBody = await response.text();
        if (errorBody) {
          try {
            const errorData = JSON.parse(errorBody);
            if (errorData.message) {
              errorMessage = errorData.message;
            } else {
              errorMessage = errorBody;
            }
          } catch {
            // If not JSON, use the raw text
            errorMessage = errorBody;
          }
        }
      } catch {
        // If we can't read the response body, use the status-based message
      }
      
      const error = new Error(errorMessage);
      (error as { response?: Response; status?: number }).response = response;
      (error as { response?: Response; status?: number }).status = response.status;
      throw error;
    }
    
    return response.json();
  }

  // Helper method for non-JSON requests (like file uploads)
  async rawRequest(endpoint: string, options: RequestInit = {}): Promise<Response> {
    return this.request(endpoint, options);
  }
}

// Export singleton instance
export const apiClient = new ApiClient(); 