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
import { Textarea } from '@/components/ui/textarea';
import { Loader2, Code, Sparkles, Play, AlertCircle, Copy, Wand2, AlertTriangle, ExternalLink } from 'lucide-react';
import { toast } from 'sonner';

import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { atomDark } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { apiClient } from '@/lib/api';

interface PyFunctionToolModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSave: (tool: PyFunctionTool) => void;
  initialTool?: PyFunctionTool | null;
  onOpenE2BModal?: () => void; // Callback to open E2B modal
}

interface PyFunctionTool {
  type: string;
  tool_def: {
    name: string;
    description: string;
    parameters: any; // JSON object, not string
  };
  code: string; // Base64 encoded
  testData?: any; // Test input data for the function
}

const PyFunctionToolModal: React.FC<PyFunctionToolModalProps> = ({
  open,
  onOpenChange,
  onSave,
  initialTool,
  onOpenE2BModal
}) => {
  const [code, setCode] = useState('');
  const [name, setName] = useState('');
  const [description, setDescription] = useState('');
  const [parameters, setParameters] = useState('');
  const [testInput, setTestInput] = useState('');
  const [isConnecting, setIsConnecting] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  const [isE2BConnected, setIsE2BConnected] = useState(false);
  const [isValidating, setIsValidating] = useState(false); // State for code validation
  const [generatedCode, setGeneratedCode] = useState(''); // New state for generated code
  const [validationError, setValidationError] = useState<string | null>(null); // New state for validation error
  const [testError, setTestError] = useState<string | null>(null); // State for test execution errors
  const [validationCompleted, setValidationCompleted] = useState(false); // Track if validation has been completed
  const [testResult, setTestResult] = useState<{ 
    success: boolean; 
    output?: any; 
    error?: {
      code: string;
      message: string;
      trace?: any;
      type?: string;
      timestamp?: number;
      param?: any;
    }
  } | null>(null); // Test result state

  // Check E2B connection status
  useEffect(() => {
    if (open) {
      const e2bConfig = localStorage.getItem('platform_e2b_mcp');
      setIsE2BConnected(!!e2bConfig);
    }
  }, [open]);

  // Add interval to check E2B connection status when modal is open
  useEffect(() => {
    if (!open) return;

    // Check immediately
    const checkE2BStatus = () => {
      const e2bConfig = localStorage.getItem('platform_e2b_mcp');
      setIsE2BConnected(!!e2bConfig);
    };

    // Check every 500ms while modal is open
    const interval = setInterval(checkE2BStatus, 500);

    return () => clearInterval(interval);
  }, [open]);

  // Load existing tool configuration when editing
  useEffect(() => {
    if (open) {
      if (initialTool && initialTool.tool_def) {
        try {
          // Decode base64 code back to plain text
          if (initialTool.code) {
            try {
              const decodedCode = atob(initialTool.code);
              setCode(decodedCode);
            } catch (error) {
              console.error('Failed to decode base64 code:', error);
              setCode(''); // Clear code if decode fails
            }
          } else {
            setCode('');
          }
          
          setName(initialTool.tool_def.name || '');
          setDescription(initialTool.tool_def.description || '');
          
          // Convert parameters object back to JSON string for editing
          if (initialTool.tool_def.parameters && typeof initialTool.tool_def.parameters === 'object') {
            setParameters(JSON.stringify(initialTool.tool_def.parameters, null, 2));
          } else if (initialTool.tool_def.parameters) {
            setParameters(String(initialTool.tool_def.parameters));
          } else {
            setParameters('');
          }
          
          // Load test data if available
          if (initialTool.testData) {
            if (typeof initialTool.testData === 'object') {
              const testDataString = JSON.stringify(initialTool.testData, null, 2);
              setTestInput(testDataString);
            } else {
              setTestInput(String(initialTool.testData));
            }
          } else {
            setTestInput('');
          }
          
          setIsEditing(true);
        } catch (error) {
          console.error('Error loading initial tool:', error);
          // Fallback to empty form if loading fails
          resetForm();
        }
      } else {
        // Reset form for new tool
        setCode('');
        setName('');
        setDescription('');
        setParameters('');
        setTestInput('');
        setIsEditing(false);
      }
    }
  }, [open, initialTool]);

  // Auto-suggest fields when code changes (placeholder for BE API integration)
  useEffect(() => {
    if (code.trim() && !isEditing) {
      // This is where the BE API call will be made later
      // For now, we'll just show a placeholder

    }
  }, [code, isEditing]);

  const handleSave = () => {
    // Validation
    if (!code.trim()) {
      toast.error('Please enter Python code');
      return;
    }
    if (!name.trim()) {
      toast.error('Please enter a function name');
      return;
    }
    if (!description.trim()) {
      toast.error('Please enter a description');
      return;
    }

    // Validate JSON schema if provided
    let parsedParameters = {};
    if (parameters.trim()) {
      try {
        parsedParameters = JSON.parse(parameters);
      } catch (error) {
        toast.error('Parameters must be valid JSON');
        return;
      }
    }

    // Create tool object with base64 encoded code
    const tool: PyFunctionTool = {
      type: 'py_fun_tool',
      tool_def: {
        name: name.trim(),
        description: description.trim(),
        parameters: parsedParameters // Store as JSON object, not string
      },
      code: btoa(code.trim()), // Base64 encode the code
      testData: testInput.trim() ? (() => {
        try {
          return JSON.parse(testInput.trim());
        } catch (error) {
          console.error('Failed to parse test input:', error);
          return testInput.trim(); // Fallback to string if JSON parsing fails
        }
      })() : undefined
    };

    // NEW: Save on server first
    (async () => {
      try {
        await apiClient.jsonRequest('/v1/registry/functions', {
          method: 'POST',
          body: JSON.stringify({
            name: tool.tool_def.name,
            description: tool.tool_def.description,
            code: tool.code,
            inputSchema: tool.tool_def.parameters
          })
        });
      } catch (err: any) {
        toast.error(err?.message || 'Failed to save function on server');
        return; // do not continue if server save fails
      }

      // Save to localStorage using object structure with function names as keys
      const existingTools = localStorage.getItem('platform_py_fun_tools');
      let toolsMap: { [key: string]: PyFunctionTool } = {};
      
      if (existingTools) {
        try {
          const existingArray = JSON.parse(existingTools);
          // Convert existing array to object format
          if (Array.isArray(existingArray)) {
            existingArray.forEach((tool: PyFunctionTool) => {
              toolsMap[tool.tool_def.name] = tool;
            });
          } else {
            // Already in object format
            toolsMap = existingArray;
          }
        } catch (error) {
          console.error('Failed to parse existing tools:', error);
        }
      }

      if (isEditing && initialTool) {
        // Update existing tool - remove old name if it changed
        const oldName = initialTool.tool_def.name;
        if (oldName !== tool.tool_def.name) {
          delete toolsMap[oldName];
        }
      }
      
      // Add/update tool with new name as key
      toolsMap[tool.tool_def.name] = tool;

      localStorage.setItem('platform_py_fun_tools', JSON.stringify(toolsMap));
      
      toast.success(`Python function tool "${name.trim()}" saved successfully!`);
      onSave(tool);
      onOpenChange(false);
    })();
  };

  const resetForm = () => {
    setCode('');
    setName('');
    setDescription('');
    setParameters('');
    setTestInput('');
    setIsConnecting(false);
    setIsEditing(false);
    setIsValidating(false); // Reset validation state
    setGeneratedCode(''); // Clear generated code
    setValidationError(null); // Clear validation error
    setTestError(null); // Clear test error
    setTestResult(null); // Clear test results
    setValidationCompleted(false); // Reset validation completed state
  };

  // Reset form when modal closes
  useEffect(() => {
    if (!open) {
      // Only reset if we're not editing (i.e., no initialTool)
      if (!initialTool) {
        resetForm();
      }
    }
  }, [open, initialTool]);

  // Generate default function name from code
  const generateDefaultFunctionName = (code: string): string => {
    // Look for function definitions in the code
    const functionMatch = code.match(/def\s+([a-zA-Z_][a-zA-Z0-9_]*)\s*\(/);
    if (functionMatch) {
      return functionMatch[1];
    }
    
    // Look for class definitions
    const classMatch = code.match(/class\s+([a-zA-Z_][a-zA-Z0-9_]*)/);
    if (classMatch) {
      return classMatch[1];
    }
    
    // Generate a generic name based on code content
    const words = code.toLowerCase()
      .replace(/[^a-zA-Z\s]/g, ' ')
      .split(/\s+/)
      .filter(word => word.length > 2)
      .slice(0, 3);
    
    if (words.length > 0) {
      return words.join('_');
    }
    
    // Fallback to a generic name
    return 'python_function';
  };

  // Beautify JSON function
  const beautifyJson = (fieldName: 'parameters' | 'testInput') => {
    const value = fieldName === 'parameters' ? parameters : testInput;
    const setter = fieldName === 'parameters' ? setParameters : setTestInput;
    
    try {
      const parsed = JSON.parse(value);
      const beautified = JSON.stringify(parsed, null, 2);
      setter(beautified);
      toast.success('JSON formatted successfully!');
    } catch (error) {
      toast.error('Invalid JSON - cannot format');
    }
  };

  // Real-time JSON validation that doesn't interrupt typing
  const validateJsonInRealTime = (value: string): boolean => {
    if (!value.trim()) return true; // Empty is considered valid
    try {
      JSON.parse(value.trim());
      return true;
    } catch {
      return false;
    }
  };

  // Copy to clipboard function
  const copyToClipboard = async (text: string, fieldName: string) => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success(`${fieldName} copied to clipboard!`);
    } catch (error) {
      toast.error('Failed to copy to clipboard');
    }
  };

  // Validate Python code and generate suggestions from API
  const validateCode = async () => {
    if (!code.trim()) {
      toast.error('Please enter Python code first');
      return;
    }

    // Store the current code to ensure it's never lost
    const currentCode = code.trim();
    
    setIsValidating(true);
    setValidationError(null);
    setTestError(null); // Clear any previous test errors
    setTestResult(null); // Clear any previous test results/success states

    try {
      // Get current model settings from localStorage
      const savedModelProvider = localStorage.getItem('platform_modelProvider') || 'openai';
      const savedModelName = localStorage.getItem('platform_modelName') || 'gpt-4o';
      


      // Prepare request payload
      const payload = {
        encodedCode: btoa(currentCode), // Use stored code
        functionDetails: {
          ...(name.trim() && { name: name.trim() }),
          ...(description.trim() && { description: description.trim() }),
          ...(parameters.trim() && { 
            parameters: (() => {
              try {
                return JSON.parse(parameters.trim());
              } catch {
                return parameters.trim(); // Fallback to string if parsing fails
              }
            })()
          })
        },
        testData: testInput.trim() ? (() => {
          try {
            return JSON.parse(testInput.trim());
          } catch {
            return testInput.trim(); // Fallback to string if parsing fails
          }
        })() : undefined,
        modelInfo: {
          model: `${savedModelProvider}@${savedModelName}`
        }
      };

      // Make API call using apiClient
      const data = await apiClient.jsonRequest<{
        suggestedFunctionDetails?: {
          name?: string;
          description?: string;
          parameters?: any;
        };
        testData?: any;
        isCodeValid?: boolean;
        codeProblem?: string;
      }>('/v1/dashboard/agc/functions:suggest', {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      
      // Handle code validation - NEVER clear the code field
      if (!data.isCodeValid) {
        setValidationError(data.codeProblem || 'Code validation failed');
        toast.error('Code has validation issues. Please review the errors above and fix them.');
        
        // Ensure code is preserved even on validation errors
        if (code !== currentCode) {
          console.warn('Code was modified during validation error, restoring original code');
          setCode(currentCode);
        }
      } else {
        setValidationError(null);
        setValidationCompleted(true); // Mark validation as completed
        
        // Ensure name field is always populated after successful validation
        if (!name.trim()) {
          const defaultName = generateDefaultFunctionName(code);
          setName(defaultName);
        }
        
        toast.success('Code validated successfully! Suggestions generated.');
      }

      // Populate fields with suggestions (allow regeneration even if fields have content)
      if (data.suggestedFunctionDetails) {
        const suggestions = data.suggestedFunctionDetails;
        
        // Always populate name and description with new suggestions
        if (suggestions.name) {
          setName(suggestions.name);
        } else {
          // If no name is provided by API, generate a default name based on the code
          const defaultName = generateDefaultFunctionName(code);
          setName(defaultName);
        }
        
        if (suggestions.description) {
          setDescription(suggestions.description);
        }
        
        // Always populate parameters with new suggestions
        if (suggestions.parameters) {
          // Handle parameters as object or string
          if (typeof suggestions.parameters === 'object') {
            setParameters(JSON.stringify(suggestions.parameters, null, 2));
          } else {
            setParameters(suggestions.parameters);
          }
        }
      }

      // Handle testData at root level (always populate with new suggestions)
      if (data.testData) {
        // Handle testData as object or string
        if (typeof data.testData === 'object') {
          setTestInput(JSON.stringify(data.testData, null, 2));
        } else {
          setTestInput(data.testData);
        }
      }

      // Final safety check - ensure code is never lost
      if (code !== currentCode) {
        console.warn('Code was unexpectedly modified, restoring original code');
        setCode(currentCode);
      }

    } catch (error) {
      console.error('Failed to validate code:', error);
      toast.error('Failed to validate code. Please try again.');
      
      // Ensure code is preserved even on API errors
      if (code !== currentCode) {
        console.warn('Code was modified during API error, restoring original code');
        setCode(currentCode);
      }
    } finally {
      setIsValidating(false);
      
      // Final safety check in finally block
      if (code !== currentCode) {
        console.warn('Code was modified during code validation, restoring original code');
        setCode(currentCode);
      }
    }
  };

  // Test function execution
  const testFunction = async () => {
    if (!code.trim() || !name.trim() || !description.trim()) {
      toast.error('Please fill all required fields before testing');
      return;
    }

    // Validate test input JSON if provided
    if (testInput.trim() && !validateJsonInRealTime(testInput)) {
      toast.error('Please provide valid JSON for test input');
      return;
    }

    // Clear any previous errors when starting test
    setValidationError(null);
    setTestError(null);
    setTestResult(null);

    setIsConnecting(true);

    try {

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

      // Prepare request payload
      const payload = {
        encodedCode: btoa(code.trim()), // Base64 encode the code
        encodedJsonParams: testInput.trim() ? btoa(testInput.trim()) : undefined, // Base64 encode test params if provided
        code_interpreter: codeInterpreter
      };

      // Make API call using apiClient
      const data = await apiClient.jsonRequest<{
        function_output?: any;
        error?: {
          code: string;
          message: string;
          trace?: any;
        };
      }>(`/v1/dashboard/functions/${encodeURIComponent(name.trim())}:execute`, {
        method: 'POST',
        body: JSON.stringify(payload)
      });
      
      // Handle response based on success/failure
      if (data.error) {
        // Function execution failed - 2XX response with data.error (CodeExecError)
        const error = data.error;
        let errorMessage = `Test failed: ${error.message}`;
        
        // Build detailed error message
        let errorDetails = [];
        if (error.code) errorDetails.push(`Code: ${error.code}`);
        if (error.message) errorDetails.push(`Message: ${error.message}`);
        if (error.trace?.traceback) errorDetails.push(`Traceback: ${error.trace.traceback}`);
        
        setTestError(errorDetails.join('\n'));
        toast.error(`Function execution failed: ${error.message}`);
        console.error('Function execution error:', error);
      } else {
        // Function execution successful
        setTestResult({
          success: true,
          output: data.function_output || {}
        });
        setTestError(null); // Clear any test errors
        setValidationError(null); // Clear any validation errors
        toast.success('Function executed successfully!');

      }

    } catch (error) {
      console.error('Failed to test function:', error);
      
      // Try to parse structured error from the error message
      let parsedError = null;
      try {
        if (error.message && error.message.startsWith('{')) {
          parsedError = JSON.parse(error.message);
        }
      } catch (parseError) {
        // If parsing fails, use the original error
        parsedError = null;
      }
      
      if (parsedError) {
        // Handle structured error (from 4XX/5XX responses or parsed data.error)
        setTestError(`Test failed: ${parsedError.message}`);
        
        // Show appropriate error message
        if (parsedError.type === 'invalid_request') {
          toast.error(`Configuration Error: ${parsedError.message}`);
        } else {
          toast.error(`Function execution failed: ${parsedError.message}`);
        }
      } else {
        // Handle generic errors
        setTestError(`Test failed: ${error.message || 'Failed to test function'}`);
        toast.error('Failed to test function. Please try again.');
      }
    } finally {
      setIsConnecting(false);
    }
  };

  // Check if validate CTA should be shown - keep it visible for code validation
  const shouldShowValidateCTA = code.trim() && isE2BConnected;
  
  // Check if test function CTA should be shown - after successful validation
  // Test data is optional - CTA shows if validation succeeded and basic fields are filled
  const shouldShowTestFunctionCTA = code.trim() && name.trim() && description.trim() && !validationError && !testError && validationCompleted && !isValidating;
  
  // Check if add/save CTA should be shown - after successful test
  const shouldShowAddCTA = testResult?.success && code.trim() && name.trim() && description.trim();

  // JSON highlighting functions (copy from JsonSchemaModal)
  const highlightJson = (jsonString: string) => {
    if (!jsonString.trim()) return null;
    
    try {
      const parsed = JSON.parse(jsonString);
      const formatted = JSON.stringify(parsed, null, 2);
      const lines = formatted.split('\n');
      return (
        <pre className="font-mono text-sm leading-relaxed whitespace-pre-wrap">
          {lines.map((line, index) => (
            <div key={index}>{highlightLine(line)}</div>
          ))}
        </pre>
      );
    } catch {
      // If parsing fails, it's partial JSON - still highlight as JSON
      const lines = jsonString.split('\n');
      return (
        <pre className="font-mono text-sm leading-relaxed whitespace-pre-wrap">
          {lines.map((line, index) => (
            <div key={index}>{highlightLine(line)}</div>
          ))}
        </pre>
      );
    }
  };

  // Highlight individual line
  const highlightLine = (line: string) => {
    if (line.includes(':') && line.includes('"')) {
      // This is likely a key-value pair
      const keyMatch = line.match(/"([^"]+)"(\s*:)/);
      if (keyMatch) {
        const beforeKey = line.substring(0, line.indexOf(keyMatch[0]));
        const key = keyMatch[1];
        const afterKey = line.substring(line.indexOf(keyMatch[0]) + keyMatch[0].length);
        
        return (
          <span className="break-words max-w-full inline-block">
            <span className="text-muted-foreground">{beforeKey}</span>
            <span className="text-positive-trend">"{key}"</span>
            <span className="text-muted-foreground">:</span>
            <span className="text-foreground">{afterKey}</span>
          </span>
        );
      }
    } else if (line.includes('"') && !line.includes(':')) {
      // This is likely a string value
      return <span className="text-positive-trend break-words max-w-full inline-block">{line}</span>;
    } else if (line.match(/\b(true|false|null)\b/)) {
      // This contains literals
      return <span className="text-positive-trend break-words max-w-full inline-block">{line}</span>;
    } else if (line.match(/\b\d+\b/)) {
      // This contains numbers
      return <span className="text-foreground break-words max-w-full inline-block">{line}</span>;
    }
    
    return <span className="text-muted-foreground break-words max-w-full inline-block">{line}</span>;
  };

  // Custom syntax highlighting theme matching app colors
  const customSyntaxStyle = {
    'comment': { color: '#6b7280' },
    'string': { color: '#10b981' }, // positive-trend green
    'number': { color: '#ffffff' },
    'boolean': { color: '#10b981' },
    'keyword': { color: '#10b981' },
    'function': { color: '#ffffff' },
    'operator': { color: '#ffffff' },
    'punctuation': { color: '#d1d5db' },
    'property': { color: '#ffffff' },
    'builtin': { color: '#10b981' },
    'class-name': { color: '#ffffff' },
    'constant': { color: '#10b981' },
    'symbol': { color: '#10b981' },
    'deleted': { color: '#ef4444' },
    'inserted': { color: '#10b981' },
    'entity': { color: '#ffffff' },
    'url': { color: '#10b981' },
    'variable': { color: '#ffffff' },
    'atrule': { color: '#10b981' },
    'attr-value': { color: '#10b981' },
    'attr-name': { color: '#ffffff' },
    'tag': { color: '#10b981' },
    'prolog': { color: '#6b7280' },
    'doctype': { color: '#6b7280' },
    'cdata': { color: '#6b7280' },
    'namespace': { color: '#ffffff' },
    'selector': { color: '#10b981' },
    'important': { color: '#ef4444' },
    'bold': { fontWeight: 'bold' },
    'italic': { fontStyle: 'italic' }
  };

  const customStyle = {
    background: 'transparent',
    padding: '16px',
    fontSize: '13px',
    fontFamily: '"Geist Mono", Menlo, Consolas, monospace',
    lineHeight: '1.5',
    margin: 0,
    minHeight: '100%'
  };

  // JSON validation functions
  const validateJson = (jsonString: string): boolean => {
    if (!jsonString.trim()) return true; // Empty is valid
    try {
      JSON.parse(jsonString);
      return true;
    } catch {
      return false;
    }
  };

  const getJsonValidationError = (jsonString: string): string | null => {
    if (!jsonString.trim()) return null; // Empty is valid
    try {
      JSON.parse(jsonString);
      return null;
    } catch (error) {
      return 'Invalid JSON format';
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-full max-w-4xl max-h-[90vh] overflow-y-auto">
        <DialogHeader className="text-center pb-2">
          <div className="flex items-center justify-center space-x-3">
            <Code className="h-6 w-6 text-foreground" />
            <DialogTitle className="text-xl font-semibold">
              Py Function Tool
            </DialogTitle>
          </div>
        </DialogHeader>

        {/* E2B Connection Warning */}
        {!isE2BConnected && (
          <div className="p-4 bg-amber-500/10 border border-amber-500/20 rounded-lg">
            <div className="flex items-center space-x-2 text-amber-600">
              <AlertTriangle className="h-4 w-4" />
              <span className="text-sm font-medium">E2B Server Required</span>
            </div>
            <p className="text-sm text-amber-600 mt-2">
              You need to connect to an E2B MCP server to use this feature. 
              <button 
                onClick={onOpenE2BModal}
                className="text-amber-500 hover:text-amber-400 underline ml-1 inline-flex items-center"
              >
                Connect E2B Server <ExternalLink className="h-3 w-3 ml-1" />
              </button>
            </p>
          </div>
        )}

        {/* Python Code Section */}
        <div className="space-y-2">
          <Label htmlFor="code" className="text-sm font-medium">
            Python Code
          </Label>
          <div className="relative">
            <Textarea
              id="code"
              value={code}
              onChange={(e) => setCode(e.target.value)}
              placeholder="def my_function(param1, param2):\n    # Your Python code here\n    result = param1 + param2\n    return result"
              className={`min-h-[200px] font-mono text-sm bg-muted/50 border focus:ring-0 focus:ring-offset-0 focus:shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-positive-trend/60 transition-all duration-200 ${
                validationError 
                  ? 'border-red-500 focus:border-red-500' 
                  : 'border-border focus:border-positive-trend/60'
              }`}
              style={{ 
                boxShadow: 'none !important',
                outline: 'none !important'
              }}
              disabled={!isE2BConnected}
            />
            <div className="absolute top-2 right-2 flex items-center space-x-2">
              {/* Copy button */}
              <Button
                variant="ghost"
                size="sm"
                className="h-6 w-6 p-0 hover:bg-muted/50 bg-muted/30"
                title="Copy code to clipboard"
                onClick={() => copyToClipboard(code, 'Python code')}
                disabled={!code.trim()}
              >
                <Copy className="h-3 w-3 text-muted-foreground" />
              </Button>
              {/* Auto-suggest button */}
              <Button
                variant="ghost"
                size="sm"
                className="h-6 w-6 p-0 hover:bg-muted/50 bg-muted/30"
                title="Auto-suggest fields from code"
                disabled={!code.trim() || !isE2BConnected}
              >
                <Sparkles className="h-3 w-3 text-muted-foreground" />
              </Button>
            </div>
            {validationError && (
              <div className="text-sm text-red-500 bg-red-500/10 p-3 rounded-md border border-red-500/20 mt-2">
                <div className="flex items-start space-x-2">
                  <AlertTriangle className="h-4 w-4 text-red-500 mt-0.5 flex-shrink-0" />
                  <div className="flex-1">
                    <p className="font-medium mb-1">Code Validation Error</p>
                    <p className="text-red-600/80">{validationError}</p>
                    <p className="text-xs text-red-500/70 mt-2">
                      Please fix the issues in your Python code above and try validating again.
                    </p>
                  </div>
                </div>
              </div>
            )}
            {testError && (
              <div className="text-sm text-red-500 bg-red-500/10 p-3 rounded-md border border-red-500/20 mt-2">
                <div className="flex items-start space-x-2">
                  <AlertTriangle className="h-4 w-4 text-red-500 mt-0.5 flex-shrink-0" />
                  <div className="flex-1">
                    <p className="font-medium mb-1">Function Testing Error</p>
                    <p className="text-red-600/80">{testError}</p>
                    <p className="text-xs text-red-500/70 mt-2">
                      {testError.includes('connection settings') || testError.includes('Configuration Error') 
                        ? 'Please check your E2B server configuration and try testing again.'
                        : 'Please fix the issues in your Python code above and try testing again.'
                      }
                    </p>
                  </div>
                </div>
              </div>
            )}
            {testResult?.success && (
              <div className="text-sm bg-green-500/10 p-3 rounded-md border border-green-500/20 mt-2">
                <div className="flex items-start space-x-2">
                  <div className="h-4 w-4 bg-green-500 rounded-full flex items-center justify-center mt-0.5 flex-shrink-0">
                    <div className="h-2 w-2 bg-white rounded-full"></div>
                  </div>
                  <div className="flex-1">
                    <p className="font-medium text-green-600 mb-1">Function Test Successful!</p>
                    <p className="text-green-600/80">Your function executed successfully.</p>
                    {testResult.output && Object.keys(testResult.output).length > 0 && (
                      <div className="mt-3">
                        <p className="text-xs font-medium text-green-600 mb-2">Output:</p>
                        <div className="bg-gray-900 rounded-md p-3 border overflow-auto max-h-32">
                          <pre className="text-xs text-green-400 whitespace-pre-wrap">
                            {JSON.stringify(testResult.output, null, 2)}
                          </pre>
                        </div>
                      </div>
                    )}
                  </div>
                </div>
              </div>
            )}
          </div>
        </div>

        {/* Main Content */}
        <div className="space-y-6">
          {/* Name and Description Side by Side */}
          <div className="space-y-2">
            {/* Labels Row with Generate CTA */}
            <div className="flex items-center justify-between">
              <div className="grid grid-cols-3 gap-4 flex-1">
                <Label htmlFor="name" className="text-sm font-medium">
                  Function Name
                </Label>
                <Label htmlFor="description" className="text-sm font-medium col-span-2">
                  Description
                </Label>
              </div>
              {/* Validate CTA - always visible when code exists and E2B is connected */}
              {shouldShowValidateCTA && (
                <Button
                  size="sm"
                  className="bg-positive-trend hover:bg-positive-trend/90 text-white h-7 px-3 text-xs shadow-sm ml-4"
                  disabled={!isE2BConnected || isValidating}
                  onClick={validateCode}
                >
                  {isValidating ? (
                    <>
                      <Loader2 className="h-3 w-3 animate-spin mr-1" />
                      Validating...
                    </>
                  ) : (
                    'Validate'
                  )}
                </Button>
              )}
            </div>
            
            {/* Input Fields Row */}
            <div className="grid grid-cols-3 gap-4">
              {/* Name Field - 1/3 width */}
              <div className="space-y-2">
                <Input
                  id="name"
                  value={name}
                  placeholder="Function name will be generated by validation"
                  className="bg-white border border-gray-300 cursor-not-allowed focus:border-gray-300 focus:ring-0 focus:ring-offset-0 focus:shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-gray-300 transition-all duration-200 text-gray-900"
                  style={{ 
                    boxShadow: 'none !important',
                    outline: 'none !important'
                  }}
                  disabled={true}
                  readOnly
                />
                <p className="text-xs text-muted-foreground">
                  Name is automatically generated based on your code.
                </p>
              </div>

              {/* Description Field - 2/3 width */}
              <div className="col-span-2 space-y-2">
                <Textarea
                  id="description"
                  value={description}
                  onChange={(e) => setDescription(e.target.value)}
                  placeholder="Describe what this function does..."
                  className="min-h-[80px] bg-muted/50 border border-border focus:border-positive-trend/60 focus:ring-0 focus:ring-offset-0 focus:shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-positive-trend/60 transition-all duration-200"
                  style={{ 
                    boxShadow: 'none !important',
                    outline: 'none !important'
                  }}
                  disabled={!isE2BConnected}
                />
              </div>
            </div>
          </div>

          {/* Parameters Field (JSON Schema) - Optional */}
          <div className="space-y-2">
            <Label htmlFor="parameters" className="text-sm font-medium">
              Parameters (JSON Schema) (Optional)
            </Label>
            <div className="relative">
              <Textarea
                id="parameters"
                value={parameters}
                onChange={(e) => setParameters(e.target.value)}
                placeholder={`{
  "type": "object",
  "properties": {
    "param1": {
      "type": "string",
      "description": "First parameter"
    },
    "param2": {
      "type": "number",
      "description": "Second parameter"
    }
  },
  "required": ["param1", "param2"]
}`}
                className={`min-h-[120px] font-mono text-sm bg-muted/50 border focus:ring-0 focus:ring-offset-0 focus:shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-positive-trend/60 transition-all duration-200 ${
                  parameters.trim() && !validateJsonInRealTime(parameters)
                    ? 'border-red-500 focus:border-red-500' 
                    : 'border-border focus:border-positive-trend/60'
                }`}
                style={{ 
                  boxShadow: 'none !important',
                  outline: 'none !important'
                }}
                disabled={!isE2BConnected}
              />
              <div className="absolute top-2 right-2 flex items-center space-x-2">
                {/* Beautify button */}
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-6 w-6 p-0 hover:bg-muted/50 bg-muted/30"
                  title="Beautify JSON"
                  onClick={() => beautifyJson('parameters')}
                  disabled={!parameters.trim()}
                >
                  <Wand2 className="h-3 w-3 text-muted-foreground" />
                </Button>
                {/* Copy button */}
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-6 w-6 p-0 hover:bg-muted/50 bg-muted/30"
                  title="Copy parameters to clipboard"
                  onClick={() => copyToClipboard(parameters, 'Parameters')}
                  disabled={!parameters.trim()}
                >
                  <Copy className="h-3 w-3 text-muted-foreground" />
                </Button>
              </div>
              {parameters.trim() && !validateJsonInRealTime(parameters) && (
                <div className="text-sm text-red-500 bg-red-500/10 p-3 rounded-md border border-red-500/20 mt-2">
                  Invalid JSON format
                </div>
              )}
            </div>
          </div>

          {/* Test Input Field */}
          <div className="space-y-2">
            <Label htmlFor="testInput" className="text-sm font-medium">
              Test Input (Optional)
            </Label>
            <div className="relative">
              <Textarea
                id="testInput"
                value={testInput}
                onChange={(e) => setTestInput(e.target.value)}
                placeholder={`{
  "param1": "test value",
  "param2": 42,
  "param3": true
}`}
                className={`min-h-[120px] font-mono text-sm bg-muted/50 border focus:ring-0 focus:ring-offset-0 focus:shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-positive-trend/60 transition-all duration-200 ${
                  testInput.trim() && !validateJsonInRealTime(testInput)
                    ? 'border-red-500 focus:border-red-500' 
                    : 'border-border focus:border-positive-trend/60'
                }`}
                style={{ 
                  boxShadow: 'none !important',
                  outline: 'none !important'
                }}
                disabled={!isE2BConnected}
              />
              <div className="absolute top-2 right-2 flex items-center space-x-2">
                {/* Beautify button */}
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-6 w-6 p-0 hover:bg-muted/50 bg-muted/30"
                  title="Beautify JSON"
                  onClick={() => beautifyJson('testInput')}
                  disabled={!testInput.trim()}
                >
                  <Wand2 className="h-3 w-3 text-muted-foreground" />
                </Button>
                {/* Copy button */}
                <Button
                  variant="ghost"
                  size="sm"
                  className="h-6 w-6 p-0 hover:bg-muted/50 bg-muted/30"
                  title="Copy test input to clipboard"
                  onClick={() => copyToClipboard(testInput, 'Test Input')}
                  disabled={!testInput.trim()}
                >
                  <Copy className="h-3 w-3 text-muted-foreground" />
                </Button>
              </div>
              {testInput.trim() && !validateJsonInRealTime(testInput) && (
                <div className="text-sm text-red-500 bg-red-500/10 p-3 rounded-md border border-red-500/20 mt-2">
                  Invalid JSON format
                </div>
              )}
            </div>
          </div>

          {/* Test Function CTA - appears after successful validation */}
          {shouldShowTestFunctionCTA && (
            <div className="p-4 bg-gradient-to-r from-green-500/10 to-emerald-500/10 border border-green-500/20 rounded-lg">
              <div className="flex items-center justify-between">
                <div className="flex items-center space-x-3">
                  <Play className="h-5 w-5 text-green-500" />
                  <div>
                    <h4 className="text-sm font-medium text-green-600">
                      🚀 Ready to Test Your Function
                    </h4>
                    <p className="text-xs text-green-600/80">
                      Code validation passed! {testInput.trim() ? 'Test your function with the provided input.' : 'Test your function (no test data required).'}
                    </p>
                  </div>
                </div>
                <div className="flex items-center space-x-2">
                  <Button
                    size="sm"
                    className="bg-gradient-to-r from-green-500 to-emerald-500 hover:from-green-600 hover:to-emerald-600 text-white"
                    disabled={!isE2BConnected || isConnecting}
                    onClick={testFunction}
                  >
                    {isConnecting ? (
                      <>
                        <Loader2 className="h-3 w-3 animate-spin mr-1" />
                        Testing...
                      </>
                    ) : (
                      <Play className="h-4 w-4 mr-2" />
                    )}
                    Test Function
                  </Button>
                  {/* Save CTA - appears after successful test */}
                  {testResult?.success && (
                    <Button
                      size="sm"
                      className="bg-gradient-to-r from-positive-trend to-green-600 hover:from-positive-trend/90 hover:to-green-700 text-white"
                      onClick={handleSave}
                      disabled={!isE2BConnected}
                    >
                      <Code className="h-4 w-4 mr-2" />
                      Save Function
                    </Button>
                  )}
                </div>
              </div>
            </div>
          )}


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
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default PyFunctionToolModal;
