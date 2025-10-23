import { TextFormat, Tool } from './types';
import { ResponsesRequestBody } from './responsesApi';

export interface ModelSettings {
  settingsType: 'RUNTIME' | 'PLATFORM';
}

export interface TextFormatConfig {
  type: TextFormat;
  name?: string;
  schema?: object;
}

export function buildTextFormat(
  textFormat: TextFormat,
  jsonSchemaName?: string,
  jsonSchemaContent?: string
): TextFormatConfig {
  if (textFormat === 'json_schema') {
    let schema = null;
    let schemaName = jsonSchemaName;
    
    try {
      if (jsonSchemaContent) {
        const parsed = JSON.parse(jsonSchemaContent);
        schema = parsed.schema;
        schemaName = parsed.name || schemaName;
      }
    } catch (error) {
      console.error('Failed to parse JSON schema:', error);
    }
    
    if (!schema || !schemaName) {
      throw new Error('JSON Schema format requires both name and schema');
    }
    
    return {
      type: 'json_schema',
      name: schemaName,
      schema
    };
  }
  
  return { type: textFormat };
}

export function buildToolsPayload(
  selectedTools: Tool[],
  modelSettings?: ModelSettings
): any[] {
  return selectedTools.map(tool => {
    if (tool.id === 'mcp_server' && tool.mcpConfig) {
      return {
        type: 'mcp',
        server_label: tool.mcpConfig.label,
        server_url: tool.mcpConfig.url,
        allowed_tools: tool.mcpConfig.selectedTools,
        headers: tool.mcpConfig.authentication === 'access_token' && tool.mcpConfig.accessToken
          ? { 'Authorization': `Bearer ${tool.mcpConfig.accessToken}` }
          : tool.mcpConfig.authentication === 'custom_headers' && tool.mcpConfig.customHeaders
          ? tool.mcpConfig.customHeaders.reduce((acc: any, header: any) => {
              if (header.key && header.value) {
                acc[header.key] = header.value;
              }
              return acc;
            }, {})
          : {}
      };
    } else if (tool.id === 'file_search' && tool.fileSearchConfig) {
      const toolBody: any = {
        type: 'file_search',
        vector_store_ids: tool.fileSearchConfig.selectedVectorStores
      };
      
      if (modelSettings?.settingsType === 'RUNTIME') {
        const provider = localStorage.getItem('platform_embedding_modelProvider') || '';
        const modelName = localStorage.getItem('platform_embedding_modelName') || '';
        const apiKeysRaw = localStorage.getItem('platform_apiKeys');
        let bearerToken = '';
        if (apiKeysRaw) {
          try {
            const apiKeys = JSON.parse(apiKeysRaw);
            const found = apiKeys.find((item: any) => item.name === provider);
            if (found) bearerToken = found.apiKey;
          } catch {}
        }
        toolBody.modelInfo = {
          bearerToken,
          model: provider && modelName ? `${provider}@${modelName}` : ''
        };
      }
      return toolBody;
    } else if (tool.id === 'agentic_file_search' && tool.agenticFileSearchConfig) {
      const toolBody: any = {
        type: 'agentic_search',
        vector_store_ids: tool.agenticFileSearchConfig.selectedVectorStores,
        max_iterations: tool.agenticFileSearchConfig.iterations,
        max_num_results: tool.agenticFileSearchConfig.maxResults
      };
      
      if (modelSettings?.settingsType === 'RUNTIME') {
        const provider = localStorage.getItem('platform_embedding_modelProvider') || '';
        const modelName = localStorage.getItem('platform_embedding_modelName') || '';
        const apiKeysRaw = localStorage.getItem('platform_apiKeys');
        let bearerToken = '';
        if (apiKeysRaw) {
          try {
            const apiKeys = JSON.parse(apiKeysRaw);
            const found = apiKeys.find((item: any) => item.name === provider);
            if (found) bearerToken = found.apiKey;
          } catch {}
        }
        toolBody.modelInfo = {
          bearerToken,
          model: provider && modelName ? `${provider}@${modelName}` : ''
        };
      }
      return toolBody;
    } else if (tool.id === 'fun_req_gathering_tool') {
      return {
        type: 'fun_req_gathering_tool'
      };
    } else if (tool.id === 'fun_def_generation_tool') {
      return {
        type: 'fun_def_generation_tool'
      };
    } else if (tool.id === 'mock_fun_save_tool') {
      return { type: 'mock_fun_save_tool' };
    } else if (tool.id === 'mock_generation_tool') {
      return { type: 'mock_generation_tool' };
    } else if (tool.id === 'mock_save_tool') {
      return { type: 'mock_save_tool' };
    } else if (tool.id === 'py_fun_tool' && tool.pyFunctionConfig) {
      // Get E2B server configuration from localStorage
      const e2bConfig = localStorage.getItem('platform_e2b_mcp');
      let codeInterpreter = {};
      if (e2bConfig) {
        try {
          const e2bData = JSON.parse(e2bConfig);
          codeInterpreter = {
            server_label: e2bData.server_label || '',
            url: e2bData.url || '',
            apiKey: e2bData.apiKey || ''
          };
        } catch (error) {
          console.error('Error parsing E2B configuration:', error);
        }
      }
      
      return {
        type: 'py_fun_tool',
        tool_def: {
          name: tool.pyFunctionConfig.tool_def.name,
          description: tool.pyFunctionConfig.tool_def.description,
          parameters: tool.pyFunctionConfig.tool_def.parameters
        },
        code: tool.pyFunctionConfig.code,
        code_interpreter: codeInterpreter
      };
    } else if (tool.id === 'client_side_tool' && tool.clientSideToolConfig) {
      return {
        type: tool.clientSideToolConfig.type || 'function',
        name: tool.clientSideToolConfig.name,
        description: tool.clientSideToolConfig.description,
        parameters: tool.clientSideToolConfig.parameters,
        strict: tool.clientSideToolConfig.strict
      };
    }
    
    // Return null for unknown tool types - they will be filtered out
    return null;
  }).filter(Boolean);
}

export function buildInputFromText(text: string): ResponsesRequestBody['input'] {
  return [
    {
      role: 'user',
      content: [
        {
          type: 'input_text',
          text
        }
      ]
    }
  ];
}

export function buildRequestBody({
  model,
  instructions,
  input,
  textFormat,
  tools,
  temperature = 1.0,
  maxOutputTokens = 2048,
  topP = 1.0,
  store = true,
  stream = true,
  previousResponseId
}: {
  model: string;
  instructions?: string;
  input: ResponsesRequestBody['input'];
  textFormat: TextFormatConfig;
  tools?: any[];
  temperature?: number;
  maxOutputTokens?: number;
  topP?: number;
  store?: boolean;
  stream?: boolean;
  previousResponseId?: string | null;
}): ResponsesRequestBody {
  const requestBody: ResponsesRequestBody = {
    model,
    input,
    text: {
      format: textFormat
    },
    temperature,
    max_output_tokens: maxOutputTokens,
    top_p: topP,
    store,
    stream
  };

  if (instructions) {
    requestBody.instructions = instructions;
  }

  if (tools && tools.length > 0) {
    requestBody.tools = tools;
  }

  if (previousResponseId) {
    requestBody.previous_response_id = previousResponseId;
  }

  return requestBody;
}
