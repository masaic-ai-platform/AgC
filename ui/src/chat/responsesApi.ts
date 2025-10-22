import { apiClient } from '@/lib/api';
import { TextFormat } from './types';

export interface ResponsesRequestBody {
  model: string;
  instructions?: string;
  input: Array<{
    role: 'user' | 'assistant';
    content: Array<{
      type: 'input_text' | 'input_image';
      text?: string;
      source?: {
        type: 'base64';
        media_type: string;
        data: string;
      };
    }>;
  }>;
  text?: {
    format?: {
      type: TextFormat;
      name?: string;
      schema?: object;
    };
  };
  tools?: any[];
  temperature?: number;
  max_output_tokens?: number;
  top_p?: number;
  store?: boolean;
  stream?: boolean;
  previous_response_id?: string | null;
}

export interface ResponsesApiOptions {
  headers?: Record<string, string>;
}

/**
 * POST to /v1/responses endpoint with proper headers and error handling
 */
export async function postResponses(
  requestBody: ResponsesRequestBody,
  options: ResponsesApiOptions = {}
): Promise<Response> {
  try {
    const response = await apiClient.rawRequest('/v1/responses', {
      method: 'POST',
      headers: {
        ...options.headers,
      },
      body: JSON.stringify(requestBody)
    });

    return response;
  } catch (error) {
    // Re-throw with more context if needed
    throw error;
  }
}
