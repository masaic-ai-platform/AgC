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

    // Add session ID header (always present)
    const sessionId = localStorage.getItem('platform_sessionId');
    if (sessionId) {
      headers['x-session-id'] = sessionId;
      console.log('Adding x-session-id header:', sessionId);
    } else {
      console.warn('No sessionId found in localStorage');
    }

    // Add user ID header only when auth is disabled
    const authEnabled = await this.isAuthEnabled();
    if (!authEnabled) {
      const userId = localStorage.getItem('platform_userId');
      if (userId) {
        headers['x-user-id'] = userId;
        console.log('Adding x-user-id header (auth disabled):', userId);
      } else {
        console.warn('No userId found in localStorage (auth disabled)');
      }
    } else {
      console.log('Auth enabled, not sending x-user-id header');
    }

    // Add API key if available
    const apiKey = this.getModelApiKey();
    if (apiKey) {
      headers['Authorization'] = `Bearer ${apiKey}`;
    }

    // Add Google token if available
    const googleToken = this.getGoogleToken();
    if (googleToken) {
      headers['x-google-token'] = googleToken;
    }

    console.log('Final headers:', headers);
    return headers;
  }

  private handleAuthError() {
    // Clear invalid token
    localStorage.removeItem('google_token');
    // Instead of reloading, dispatch a custom event to trigger login screen
    window.dispatchEvent(new CustomEvent('auth:token-expired'));
  }

  async request(endpoint: string, options: RequestInit = {}): Promise<Response> {
    const baseHeaders = await this.getHeaders();
    
    // If body is FormData, don't include Content-Type header (browser will set it with boundary)
    const headers = options.body instanceof FormData 
      ? { ...baseHeaders, ...options.headers }
      : { ...baseHeaders, ...options.headers };
    
    // Remove Content-Type if it's FormData to let browser set it with boundary
    if (options.body instanceof FormData && headers['Content-Type']) {
      delete headers['Content-Type'];
    }

    // Debug: Log headers being sent (only in development)
    if (process.env.NODE_ENV === 'development') {
      console.log(`API Request to ${endpoint}:`, {
        headers: headers,
        hasAuth: !!headers['Authorization'],
        hasGoogleToken: !!headers['X-Google-Token'],
        isFormData: options.body instanceof FormData
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

  // Helper method for agents API calls (excludes Authorization header, only includes X-Google-Token if applicable)
  private async getAgentsHeaders(): Promise<HeadersInit> {
    const headers: HeadersInit = {
      'Content-Type': 'application/json',
    };

    // Add session ID header (always present)
    const sessionId = localStorage.getItem('platform_sessionId');
    if (sessionId) {
      headers['x-session-id'] = sessionId;
      console.log('Adding x-session-id header (agents):', sessionId);
    } else {
      console.warn('No sessionId found in localStorage (agents)');
    }

    // Add user ID header only when auth is disabled
    const authEnabled = await this.isAuthEnabled();
    if (!authEnabled) {
      const userId = localStorage.getItem('platform_userId');
      if (userId) {
        headers['x-user-id'] = userId;
        console.log('Adding x-user-id header (agents, auth disabled):', userId);
      } else {
        console.warn('No userId found in localStorage (agents, auth disabled)');
      }
    } else {
      console.log('Auth enabled, not sending x-user-id header (agents)');
    }

    // Only add X-Google-Token if auth is enabled (no Authorization header for agents API)
    if (authEnabled) {
      const googleToken = this.getGoogleToken();
      if (googleToken) {
        headers['X-Google-Token'] = googleToken;
      }
    }

    return headers;
  }

  // Agent-specific request method (no Authorization header)
  async agentRequest(endpoint: string, options: RequestInit = {}): Promise<Response> {
    const baseHeaders = await this.getAgentsHeaders();
    const headers = { ...baseHeaders, ...options.headers };
    
    // Remove Content-Type if it's FormData to let browser set it with boundary
    if (options.body instanceof FormData && headers['Content-Type']) {
      delete headers['Content-Type'];
    }

    // Debug: Log headers being sent (only in development)
    if (process.env.NODE_ENV === 'development') {
      console.log(`Agent API Request to ${endpoint}:`, {
        headers: headers,
        hasAuth: !!headers['Authorization'],
        hasGoogleToken: !!headers['X-Google-Token'],
        isFormData: options.body instanceof FormData
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

  // Helper method for JSON requests to agents endpoints
  async agentJsonRequest<T>(endpoint: string, options: RequestInit = {}): Promise<T> {
    const response = await this.agentRequest(endpoint, options);
    
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
    
    // Check if response has content before trying to parse JSON
    const contentType = response.headers.get('content-type');
    const contentLength = response.headers.get('content-length');
    
    // If no content or content-length is 0, return empty object/null
    if (contentLength === '0' || response.status === 204) {
      return {} as T;
    }
    
    // Check if response is actually JSON
    if (!contentType || !contentType.includes('application/json')) {
      // For non-JSON responses (like plain text), try to get the text
      const text = await response.text();
      if (!text) {
        return {} as T;
      }
      // If it's not empty text, try to parse as JSON anyway
      try {
        return JSON.parse(text);
      } catch {
        // If parsing fails, return the text wrapped in an object
        return { message: text } as T;
      }
    }
    
    // Try to parse JSON, handle empty responses
    try {
      const text = await response.text();
      if (!text || text.trim() === '') {
        return {} as T;
      }
      return JSON.parse(text);
    } catch (error) {
      console.warn('Failed to parse JSON response:', error);
      return {} as T;
    }
  }
}

// Export singleton instance
export const apiClient = new ApiClient(); 