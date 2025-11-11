import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Switch } from '@/components/ui/switch';
import { Loader2, Plus, Trash2, Copy, Wand2, Pencil } from 'lucide-react';
import { toast } from 'sonner';
import { apiClient } from '@/lib/api';
import ObjectPropertiesModal from './ObjectPropertiesModal';
import ArrayItemsModal from './ArrayItemsModal';
import EnumValuesModal from './EnumValuesModal';
import { Tabs, TabsList, TabsTrigger, TabsContent } from '@/components/ui/tabs';
// Removed radio group, using switch to match form styling

interface LocalToolModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSave: (tool: LocalTool) => void;
  initialTool?: LocalTool | null;
  selectedTools?: any[]; // Add selected tools prop
}

interface LocalTool {
  type: string;
  name: string;
  description: string;
  parameters: {
    type: string;
    properties: Record<string, PropertyDefinition>;
    required: string[];
    additionalProperties: boolean;
  };
  strict: boolean;
  executionSpecs?: ExecutionSpecs;
}

interface PropertyDefinition {
  type: string;
  description?: string;
  default?: any;
  enum?: any[];
  properties?: Record<string, PropertyDefinition>;
  items?: PropertyDefinition;
}

interface ExecutionSpecs {
  type: string;
  maxRetryAttempts: number;
  waitTimeInMillis: number;
}

const LocalToolModal: React.FC<LocalToolModalProps> = ({
  open,
  onOpenChange,
  onSave,
  initialTool,
  selectedTools = [],
}) => {
  // Nudge links (customize as needed)
  const CLIENT_TOOL_DOWNLOAD_URL = '/client_side_tool.zip';
  const [howToUseOpen, setHowToUseOpen] = useState(false);
  const [lastCreatedToolName, setLastCreatedToolName] = useState('');
  const [downloadModalOpen, setDownloadModalOpen] = useState(false);
  const [showDownloadButton, setShowDownloadButton] = useState(false);
  const [name, setName] = useState('');
  const [nameError, setNameError] = useState('');
  const [description, setDescription] = useState('');
  const [parameters, setParameters] = useState<Record<string, PropertyDefinition>>({});
  const [requiredFields, setRequiredFields] = useState<string[]>([]);
  const [strict, setStrict] = useState(true);
  const [additionalProperties, setAdditionalProperties] = useState(false);
  const [isEditing, setIsEditing] = useState(false);
  
  // Execution specs state
  const [executionType, setExecutionType] = useState('client_side');
  const [maxRetryAttempts, setMaxRetryAttempts] = useState(1);
  const [waitTimeInMillis, setWaitTimeInMillis] = useState(60000);
  const [includeExecutionSpecs, setIncludeExecutionSpecs] = useState(false);
  const [activeTab, setActiveTab] = useState<'form' | 'json'>('form');
  const [jsonInput, setJsonInput] = useState('');
  const [isJsonValid, setIsJsonValid] = useState(false);
  const [jsonError, setJsonError] = useState('');
  const [executionSpecsEditableJson, setExecutionSpecsEditableJson] = useState(false);

  // Property editing state
  const [propertyName, setPropertyName] = useState('');
  const [propertyNameError, setPropertyNameError] = useState('');
  const [propertyType, setPropertyType] = useState('string');
  const [propertyDescription, setPropertyDescription] = useState('');
  const [propertyDefaultValue, setPropertyDefaultValue] = useState('');
  const [propertyEnumValues, setPropertyEnumValues] = useState<string[]>([]);
  const [propertyObjectProperties, setPropertyObjectProperties] = useState<Record<string, PropertyDefinition>>({});
  const [propertyArrayItems, setPropertyArrayItems] = useState<PropertyDefinition | null>(null);
  const [editingPropertyName, setEditingPropertyName] = useState<string | null>(null);

  // Modal states
  const [objectModalOpen, setObjectModalOpen] = useState(false);
  const [arrayModalOpen, setArrayModalOpen] = useState(false);
  const [enumModalOpen, setEnumModalOpen] = useState(false);

  // Load existing tool configuration when editing
  useEffect(() => {
    if (open) {
      // Always default to Form Editor when opening the modal
      setActiveTab('form');
      if (initialTool) {
        setName(initialTool.name || '');
        setNameError('');
        setDescription(initialTool.description || '');
        setStrict(initialTool.strict ?? true);
        setAdditionalProperties(initialTool.parameters?.additionalProperties ?? false);
        
        // Load parameters
        if (initialTool.parameters?.properties) {
          const { execution_specs, ...otherProps } = initialTool.parameters.properties;
          setParameters(otherProps);
        } else {
          setParameters({});
        }
        
        // Load execution specs values if they exist
        if (initialTool.executionSpecs) {
          setExecutionType(initialTool.executionSpecs.type || 'client_side');
          setMaxRetryAttempts(initialTool.executionSpecs.maxRetryAttempts || 1);
          setWaitTimeInMillis(initialTool.executionSpecs.waitTimeInMillis || 60000);
        } else {
          setExecutionType('client_side');
          setMaxRetryAttempts(1);
          setWaitTimeInMillis(60000);
        }
        // Always start with execution specs disabled by default
        setIncludeExecutionSpecs(false);
        
        // Load required fields (exclude execution_specs as it's always included)
        if (initialTool.parameters?.required) {
          setRequiredFields(initialTool.parameters.required.filter(f => f !== 'execution_specs'));
        }
        
        setIsEditing(true);
      } else {
        resetForm();
      }
      // Do not prefill JSON editor; placeholder acts as non-editable hint until user types
    }
  }, [open, initialTool]);

  // Validate function name (only alphanumeric and underscores, must start with letter, minimum 3 characters)
  const validateFunctionName = (value: string) => {
    if (!value.trim()) {
      return 'Function name is required';
    }
    if (value.trim().length < 3) {
      return 'Function name must be at least 3 characters long';
    }
    if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(value)) {
      return 'Function name must start with a letter and can only contain letters, numbers, and underscores (e.g., add_two_numbers)';
    }
    return '';
  };

  const handleSaveFromJson = () => {
    const parsed = parseOpenAiFunctionSchema(jsonInput);
    if (!parsed) return;

    const props = { ...parsed.parameters.properties } as Record<string, PropertyDefinition>;
    (props as any).execution_specs = buildExecutionSpecsProperty();

    const tool: LocalTool = {
      ...parsed,
      parameters: {
        ...parsed.parameters,
        properties: props,
        required: (parsed.parameters.required || []).filter((r) => r !== 'execution_specs'),
      },
    };

    const existingTools = localStorage.getItem('platform_client_side_tools');
    let toolsMap: { [key: string]: LocalTool } = {};
    if (existingTools) {
      try {
        const existingArray = JSON.parse(existingTools);
        if (Array.isArray(existingArray)) {
          existingArray.forEach((t: LocalTool) => {
            toolsMap[t.name] = t;
          });
        } else {
          toolsMap = existingArray;
        }
      } catch (error) {
        console.error('Failed to parse existing tools:', error);
      }
    }

    toolsMap[tool.name] = tool;
    localStorage.setItem('platform_client_side_tools', JSON.stringify(toolsMap));

    toast.success(`Client-side tool "${tool.name}" saved successfully!`);
    setShowDownloadButton(true);
    onSave(tool);
    setLastCreatedToolName(tool.name);
    onOpenChange(false);
    if (!isEditing) setDownloadModalOpen(true);
  };

  const handleNameChange = (value: string) => {
    setName(value);
    const error = validateFunctionName(value);
    setNameError(error);
  };

  // Validate property name (same rules as function name)
  const validatePropertyName = (value: string) => {
    if (!value.trim()) {
      return 'Property name is required';
    }
    if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(value)) {
      return 'Property name must start with a letter and can only contain letters, numbers, and underscores';
    }
    return '';
  };

  const handlePropertyNameChange = (value: string) => {
    setPropertyName(value);
    const error = validatePropertyName(value);
    setPropertyNameError(error);
  };

  // Check if form is valid for enabling/disabling save button
  const isFormValid = () => {
    const nameValidationError = validateFunctionName(name);
    const hasValidName = !nameValidationError && name.trim();
    const hasValidDescription = description.trim();
    const hasValidPropertyName = !propertyName.trim() || !validatePropertyName(propertyName);
    
    return hasValidName && hasValidDescription && hasValidPropertyName;
  };

  // Helper function to parse default value based on type
  const parseDefaultValue = (value: string, type: string): any => {
    if (!value.trim()) return undefined;

    try {
      switch (type) {
        case 'number':
          return Number(value);
        case 'boolean':
          return value.toLowerCase() === 'true';
        case 'object':
        case 'array':
          return JSON.parse(value);
        case 'string':
        case 'enum':
        default:
          return value;
      }
    } catch (error) {
      // If parsing fails, return the original string value
      return value;
    }
  };

  // Helper functions for enum values
  const addEnumValue = () => {
    setEnumModalOpen(true);
  };

  const removeEnumValue = (index: number) => {
    setPropertyEnumValues(propertyEnumValues.filter((_, i) => i !== index));
  };

  // Modal handlers
  const handleObjectPropertiesSave = (properties: Record<string, PropertyDefinition>) => {
    setPropertyObjectProperties(properties);
  };

  const handleArrayItemsSave = (items: PropertyDefinition) => {
    setPropertyArrayItems(items);
  };

  const handleEnumValuesSave = (values: string[]) => {
    setPropertyEnumValues(values);
  };

  // Handlers for editing existing object properties and array items
  const handleEditObjectProperties = (propName: string) => {
    const property = parameters[propName];
    if (property && property.properties) {
      setPropertyObjectProperties(property.properties);
      setObjectModalOpen(true);
    }
  };

  const handleEditArrayItems = (propName: string) => {
    const property = parameters[propName];
    if (property && property.items) {
      setPropertyArrayItems(property.items);
      setArrayModalOpen(true);
    }
  };

  // Enhanced handlers for saving object properties and array items
  const handleObjectPropertiesSaveEnhanced = (properties: Record<string, PropertyDefinition>) => {
    if (editingPropertyName) {
      // Update existing parameter
      const updatedParameters = { ...parameters };
      updatedParameters[editingPropertyName] = {
        ...updatedParameters[editingPropertyName],
        properties
      };
      setParameters(updatedParameters);
    } else {
      // Update current form state
      setPropertyObjectProperties(properties);
    }
  };

  const handleArrayItemsSaveEnhanced = (items: PropertyDefinition) => {
    if (editingPropertyName) {
      // Update existing parameter
      const updatedParameters = { ...parameters };
      updatedParameters[editingPropertyName] = {
        ...updatedParameters[editingPropertyName],
        items
      };
      setParameters(updatedParameters);
    } else {
      // Update current form state
      setPropertyArrayItems(items);
    }
  };

  const resetForm = () => {
    setName('');
    setNameError('');
    setDescription('');
    setParameters({});
    setRequiredFields([]);
    setStrict(true);
    setAdditionalProperties(false);
    setIsEditing(false);
    setPropertyName('');
    setPropertyNameError('');
    setPropertyType('string');
    setPropertyDescription('');
    setPropertyDefaultValue('');
    setPropertyEnumValues([]);
    setPropertyObjectProperties({});
    setPropertyArrayItems(null);
    setEditingPropertyName(null);
    setExecutionType('client_side');
    setMaxRetryAttempts(1);
    setWaitTimeInMillis(60000);
    setIncludeExecutionSpecs(false);
    // Ensure JSON editor is clean for create mode
    setJsonInput('');
    setJsonError('');
    setIsJsonValid(false);
    setExecutionSpecsEditableJson(false);
  };

  const handleAddProperty = () => {
    const propertyNameValidationError = validatePropertyName(propertyName);
    if (propertyNameValidationError) {
      setPropertyNameError(propertyNameValidationError);
      toast.error(propertyNameValidationError);
      return;
    }

    // If editing, allow same name; otherwise check for duplicates
    if (!editingPropertyName && parameters[propertyName]) {
      toast.error('Property already exists');
      return;
    }

    // Validate enum type
    if (propertyType === 'enum' && propertyEnumValues.length === 0) {
      toast.error('Enum type requires at least one enum value');
      return;
    }

    // Validate default value for enum
    if (propertyType === 'enum' && propertyDefaultValue.trim() && !propertyEnumValues.includes(propertyDefaultValue.trim())) {
      toast.error('Default value must be one of the defined enum values');
      return;
    }

    // Validate object type
    if (propertyType === 'object' && Object.keys(propertyObjectProperties).length === 0) {
      toast.error('Object type requires at least one property');
      return;
    }

    // Validate array type
    if (propertyType === 'array' && !propertyArrayItems) {
      toast.error('Array type requires items definition');
      return;
    }

    const parsedDefaultValue = parseDefaultValue(propertyDefaultValue, propertyType);

    const newProperty: PropertyDefinition = {
      type: propertyType === 'enum' ? 'string' : propertyType,
      description: propertyDescription.trim() || undefined,
      ...(parsedDefaultValue !== undefined && { default: parsedDefaultValue }),
      ...(propertyType === 'enum' && propertyEnumValues.length > 0 && { enum: propertyEnumValues }),
      ...(propertyType === 'object' && Object.keys(propertyObjectProperties).length > 0 && { properties: propertyObjectProperties }),
      ...(propertyType === 'array' && propertyArrayItems && { items: propertyArrayItems }),
    };

    // If editing, remove the old property first
    if (editingPropertyName && editingPropertyName !== propertyName) {
      const newParams = { ...parameters };
      delete newParams[editingPropertyName];
      
      // Update required fields if property name changed
      const updatedRequired = requiredFields.map(f => 
        f === editingPropertyName ? propertyName : f
      );
      setRequiredFields(updatedRequired);
      
      setParameters({ ...newParams, [propertyName]: newProperty });
    } else {
      setParameters({ ...parameters, [propertyName]: newProperty });
      
      // Add new parameter to required fields by default
      if (!editingPropertyName && !requiredFields.includes(propertyName)) {
        setRequiredFields([...requiredFields, propertyName]);
      }
    }

    // Reset form
    setPropertyName('');
    setPropertyType('string');
    setPropertyDescription('');
    setPropertyDefaultValue('');
    setPropertyEnumValues([]);
    setPropertyObjectProperties({});
    setPropertyArrayItems(null);
    setEditingPropertyName(null);
  };

  const handleEditProperty = (propName: string) => {
    const property = parameters[propName];
    setPropertyName(propName);
    setPropertyNameError('');
    // If property has enum values, treat it as enum type in UI
    setPropertyType(property.enum && property.enum.length > 0 ? 'enum' : property.type);
    setPropertyDescription(property.description || '');
    setPropertyDefaultValue(property.default !== undefined ? JSON.stringify(property.default) : '');
    setPropertyEnumValues(property.enum || []);
    setPropertyObjectProperties(property.properties || {});
    setPropertyArrayItems(property.items || null);
    setEditingPropertyName(propName);
  };

  const handleCancelEdit = () => {
    setPropertyName('');
    setPropertyNameError('');
    setPropertyType('string');
    setPropertyDescription('');
    setPropertyDefaultValue('');
    setPropertyEnumValues([]);
    setPropertyObjectProperties({});
    setPropertyArrayItems(null);
    setEditingPropertyName(null);
  };

  const handleRemoveProperty = (propName: string) => {
    const newParams = { ...parameters };
    delete newParams[propName];
    setParameters(newParams);
    setRequiredFields(requiredFields.filter(f => f !== propName));
    
    // If this parameter was being edited, cancel the edit
    if (editingPropertyName === propName) {
      handleCancelEdit();
    }
  };

  const handleToggleRequired = (propName: string) => {
    if (requiredFields.includes(propName)) {
      setRequiredFields(requiredFields.filter(f => f !== propName));
    } else {
      setRequiredFields([...requiredFields, propName]);
    }
  };

  const handleSave = () => {
    // Validation
    const nameValidationError = validateFunctionName(name);
    if (nameValidationError) {
      setNameError(nameValidationError);
      toast.error(nameValidationError);
      return;
    }
    if (!description.trim()) {
      toast.error('Please enter a description');
      return;
    }

    // Build parameters object
    const finalParameters = { ...parameters } as Record<string, PropertyDefinition>;
    (finalParameters as any).execution_specs = buildExecutionSpecsProperty();
    
    // Create execution specs object - always save the current values
    const executionSpecs = {
      type: executionType,
      maxRetryAttempts: maxRetryAttempts,
      waitTimeInMillis: waitTimeInMillis,
    };

    // Create tool object
    const tool: LocalTool = {
      type: 'function',
      name: name.trim(),
      description: description.trim(),
      parameters: {
        type: 'object',
        properties: finalParameters,
        required: requiredFields,
        additionalProperties: additionalProperties,
      },
      strict: strict,
      executionSpecs: executionSpecs, // Always save execution specs values
    };


    // Save to localStorage
    const existingTools = localStorage.getItem('platform_client_side_tools');
    let toolsMap: { [key: string]: LocalTool } = {};
    
    if (existingTools) {
      try {
        const existingArray = JSON.parse(existingTools);
        if (Array.isArray(existingArray)) {
          existingArray.forEach((tool: LocalTool) => {
            toolsMap[tool.name] = tool;
          });
        } else {
          toolsMap = existingArray;
        }
      } catch (error) {
        console.error('Failed to parse existing tools:', error);
      }
    }

    if (isEditing && initialTool) {
      const oldName = initialTool.name;
      if (oldName !== tool.name) {
        delete toolsMap[oldName];
      }
    }
    
    toolsMap[tool.name] = tool;
    localStorage.setItem('platform_client_side_tools', JSON.stringify(toolsMap));
    
    toast.success(`Client-side tool "${name.trim()}" saved successfully!`);
    
    // Show download button after successful save
    setShowDownloadButton(true);
    
    onSave(tool);
    // Preserve the created tool name for the download modal (form resets on close)
    setLastCreatedToolName(tool.name);
    onOpenChange(false);
    
    // Open download modal after creating a new tool (not when editing)
    if (!isEditing) {
      setDownloadModalOpen(true);
    }
  };

  // Helper function to get names of currently selected client-side tools
  const getSelectedClientSideToolNames = () => {
    return selectedTools
      .filter(tool => tool.id === 'client_side_tool' && tool.clientSideToolConfig)
      .map(tool => tool.clientSideToolConfig.name);
  };

  const handleDownloadClientRuntime = async () => {
    try {
      const effectiveName = (name && name.trim()) || (lastCreatedToolName && lastCreatedToolName.trim()) || '';
      if (!effectiveName) {
        toast.error('Please provide a valid function name before downloading');
        return;
      }

      const profileId =
        localStorage.getItem('platform_userId') ||
        localStorage.getItem('platform_sessionId') ||
        '';

      // Get names of currently selected client-side tools (excluding current function)
      const selectedClientSideToolNames = getSelectedClientSideToolNames()
        .filter(toolName => toolName !== effectiveName); // Exclude current function to avoid duplication
      
      // Create array of function names (current function + selected tools)
      const allToolNames = [effectiveName, ...selectedClientSideToolNames];

      const response = await apiClient.rawRequest('/v1/dashboard/download', {
        method: 'POST',
        body: JSON.stringify({
          functionNames: allToolNames, // Array of function names
          profile: profileId,
          type: 'download_code_snippet',
          format: 'zip',
        }),
      });

      if (!response.ok) {
        const errorText = await response.text();
        throw new Error(errorText || `Download failed (HTTP ${response.status})`);
      }

      const blob = await response.blob();
      const filename = 'agc-runtime.zip';
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(url);

      toast.success('Download started');
    } catch (err) {
      const message = err instanceof Error ? err.message : 'Failed to download';
      toast.error(message);
    }
  };

  // Reset form when modal closes
  useEffect(() => {
    if (!open && !initialTool) {
      resetForm();
    }
  }, [open, initialTool]);

  // Copy to clipboard function
  const copyToClipboard = async (text: string, fieldName: string) => {
    try {
      await navigator.clipboard.writeText(text);
      toast.success(`${fieldName} copied to clipboard!`);
    } catch (error) {
      toast.error('Failed to copy to clipboard');
    }
  };

  // Beautify JSON preview
  const getJsonPreview = () => {
    const finalParameters = { ...parameters };
    
    // Always include execution_specs in preview (regardless of UI toggle)
    finalParameters.execution_specs = {
      type: 'object',
      properties: {
        type: {
          type: 'string',
          enum: [executionType],
        },
        maxRetryAttempts: {
          type: 'number',
          enum: [maxRetryAttempts],
        },
        waitTimeInMillis: {
          type: 'number',
          enum: [waitTimeInMillis],
        },
      },
    } as any;

    const tool = {
      type: 'function',
      name: name.trim() || 'function_name',
      description: description.trim() || 'Function description',
      parameters: {
        type: 'object',
        properties: finalParameters,
        required: requiredFields,
        additionalProperties: additionalProperties,
      },
      strict: strict,
    };

    return JSON.stringify(tool, null, 2);
  };

  const buildExecutionSpecsProperty = (): PropertyDefinition => {
    return {
      type: 'object',
      properties: {
        type: { type: 'string', enum: [executionType] } as any,
        maxRetryAttempts: { type: 'number', enum: [maxRetryAttempts] } as any,
        waitTimeInMillis: { type: 'number', enum: [waitTimeInMillis] } as any,
      } as any,
    } as any;
  };

  const parseOpenAiFunctionSchema = (raw: string): LocalTool | null => {
    let obj: any;
    try {
      obj = JSON.parse(raw);
    } catch (e) {
      toast.error('Invalid JSON');
      return null;
    }

    let fn: any = null;
    if (obj && obj.type === 'function' && obj.function && typeof obj.function === 'object') {
      fn = obj.function;
    } else if (obj && obj.type === 'function' && obj.name && obj.parameters) {
      fn = { name: obj.name, description: obj.description, parameters: obj.parameters, strict: obj.strict };
    } else if (obj && obj.name && obj.parameters) {
      fn = obj;
    }

    if (!fn) {
      toast.error('Schema must be an OpenAI function tool');
      return null;
    }

    const candidateName = (fn.name || '').trim();
    const nameValidationError = validateFunctionName(candidateName);
    if (nameValidationError) {
      toast.error(nameValidationError);
      return null;
    }

    if (!fn.description || typeof fn.description !== 'string' || !fn.description.trim()) {
      toast.error('Function description is required');
      return null;
    }

    const params = fn.parameters;
    if (!params || typeof params !== 'object' || params.type !== 'object') {
      toast.error('parameters.type must be "object"');
      return null;
    }

    const props: Record<string, PropertyDefinition> = (params.properties || {}) as any;
    const req: string[] = Array.isArray(params.required) ? params.required : [];
    const addl: boolean = !!params.additionalProperties;
    const strictFlag: boolean = fn.strict === undefined ? true : !!fn.strict;

    if (props.execution_specs) {
      delete (props as any).execution_specs;
    }

    const tool: LocalTool = {
      type: 'function',
      name: candidateName,
      description: fn.description.trim(),
      parameters: {
        type: 'object',
        properties: props,
        required: req.filter((r) => r !== 'execution_specs'),
        additionalProperties: addl,
      },
      strict: strictFlag,
      executionSpecs: {
        type: executionType,
        maxRetryAttempts,
        waitTimeInMillis,
      },
    };

    return tool;
  };

  const getAugmentedJsonPreviewFromJsonInput = () => {
    try {
      const parsed = parseOpenAiFunctionSchema(jsonInput);
      if (!parsed) return '';
      const props = { ...parsed.parameters.properties } as Record<string, PropertyDefinition>;
      (props as any).execution_specs = buildExecutionSpecsProperty();
      const tool = {
        type: 'function',
        name: parsed.name,
        description: parsed.description,
        parameters: {
          type: 'object',
          properties: props,
          required: (parsed.parameters.required || []).filter((r) => r !== 'execution_specs'),
          additionalProperties: parsed.parameters.additionalProperties,
        },
        strict: parsed.strict,
      } as any;
      return JSON.stringify(tool, null, 2);
    } catch {
      return '';
    }
  };

  // Silent validation for enabling the save button in JSON tab
  const getOpenAiSchemaValidationError = (raw: string): string => {
    if (!raw.trim()) return 'Paste a function schema to continue';
    try {
      const obj = JSON.parse(raw);
      let fn: any = null;
      if (obj && obj.type === 'function' && obj.function && typeof obj.function === 'object') {
        fn = obj.function;
      } else if (obj && obj.type === 'function' && obj.name && obj.parameters) {
        fn = { name: obj.name, description: obj.description, parameters: obj.parameters, strict: obj.strict };
      } else if (obj && obj.name && obj.parameters) {
        fn = obj;
      }
      if (!fn) return 'Schema must be an OpenAI function tool';
      const candidateName = (fn.name || '').trim();
      const nameErr = validateFunctionName(candidateName);
      if (nameErr) return nameErr;
      if (!fn.description || typeof fn.description !== 'string' || !fn.description.trim()) return 'Function description is required';
      const params = fn.parameters;
      if (!params || typeof params !== 'object') return 'parameters must be an object';
      if (params.type !== 'object') return 'parameters.type must be "object"';
      return '';
    } catch (e: any) {
      const msg = typeof e?.message === 'string' ? e.message.split('\n')[0] : 'Invalid JSON';
      return `Invalid JSON: ${msg}`;
    }
  };

  useEffect(() => {
    if (activeTab === 'json') {
      const err = getOpenAiSchemaValidationError(jsonInput);
      setJsonError(err);
      setIsJsonValid(!err);
    }
  }, [activeTab, jsonInput]);

  return (
    <>
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-full max-w-5xl max-h-[90vh] overflow-y-auto">
        <DialogHeader className="text-center pb-2">
          <div className="flex items-center justify-center space-x-3">
            <Plus className="h-6 w-6 text-foreground" />
            <DialogTitle className="text-xl font-semibold">
              {isEditing ? 'Edit Client-Side Tool' : 'Create Client-Side Tool'}
            </DialogTitle>
          </div>
        </DialogHeader>

        {/* Nudge: Download + How to Use */}
        <div className="mb-4 p-3 rounded-md border border-border bg-muted/30 flex items-center justify-between">
          <div className="pr-3">
            <div className="text-sm font-semibold text-foreground">Client‑Side Tool Runtime</div>
            <div className="text-xs text-muted-foreground">Supercharge your local tools — download the runtime and integrate in minutes.</div>
          </div>
          <div className="flex items-center gap-2">
            {isEditing && (
              <Button
                variant="outline"
                size="sm"
                onClick={handleDownloadClientRuntime}
                className="whitespace-nowrap border-positive-trend text-positive-trend hover:bg-positive-trend hover:text-white"
              >
                Download Runtime
              </Button>
            )}
            <Button
              variant="outline"
              size="sm"
              onClick={() => setHowToUseOpen(true)}
              className="whitespace-nowrap bg-blue-50 text-blue-600 hover:bg-blue-100 hover:text-blue-700 border-blue-200 hover:border-blue-300"
            >
              How to Use
            </Button>
          </div>
        </div>

        {isEditing ? (
          // Edit mode: show only Form Editor content
          <div className="grid grid-cols-2 gap-6">
          {/* Left Column - Configuration */}
          <div className="space-y-6">
            {/* Function Name */}
            <div className="space-y-2">
              <Label htmlFor="name" className="text-sm font-medium">
                Function Name *
              </Label>
              <Input
                id="name"
                value={name}
                onChange={(e) => handleNameChange(e.target.value)}
                placeholder="add_two_numbers"
                readOnly={isEditing}
                disabled={isEditing}
                className={`bg-muted/50 border focus:border-positive-trend/60 ${
                  nameError ? 'border-red-500 focus:border-red-500' : 'border-border'
                } ${isEditing ? 'opacity-70 cursor-not-allowed' : ''}`}
              />
              {nameError && (
                <p className="text-xs text-red-500 mt-1">{nameError}</p>
              )}
            </div>

            {/* Description */}
            <div className="space-y-2">
              <Label htmlFor="description" className="text-sm font-medium">
                Description *
              </Label>
              <Textarea
                id="description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Describe what this function does..."
                className="min-h-[80px] bg-muted/50 border border-border focus:border-positive-trend/60"
              />
            </div>

            {/* Add/Edit Property Section */}
            <div className="space-y-3 p-4 bg-muted/30 rounded-lg border border-border">
              <div className="flex items-center justify-between">
                <Label className="text-sm font-medium">
                  {editingPropertyName ? 'Edit Parameter' : 'Add Parameter'}
                </Label>
                {editingPropertyName && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleCancelEdit}
                    className="h-6 text-xs text-muted-foreground hover:text-foreground"
                  >
                    Cancel Edit
                  </Button>
                )}
              </div>
              
              <div className="grid grid-cols-2 gap-2">
                <div className="space-y-1">
                  <Input
                    value={propertyName}
                    onChange={(e) => handlePropertyNameChange(e.target.value)}
                    placeholder="Property name (e.g., 'a')"
                    className={`bg-background ${
                      propertyNameError ? 'border-red-500 focus:border-red-500' : ''
                    }`}
                    disabled={editingPropertyName !== null}
                  />
                  {propertyNameError && (
                    <p className="text-xs text-red-500">{propertyNameError}</p>
                  )}
                </div>
                <select
                  value={propertyType}
                  onChange={(e) => setPropertyType(e.target.value)}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                >
                  <option value="string">String</option>
                  <option value="number">Number</option>
                  <option value="boolean">Boolean</option>
                  <option value="object">Object</option>
                  <option value="array">Array</option>
                  <option value="enum">Enum</option>
                </select>
              </div>
              
              <Input
                value={propertyDescription}
                onChange={(e) => setPropertyDescription(e.target.value)}
                placeholder="Description (optional)"
                className="bg-background"
              />
              
              <div className="space-y-1">
                <Input
                  value={propertyDefaultValue}
                  onChange={(e) => setPropertyDefaultValue(e.target.value)}
                  placeholder={
                    propertyType === 'string' ? 'Enter default string value' :
                    propertyType === 'number' ? 'Enter default number (e.g., 42)' :
                    propertyType === 'boolean' ? 'Enter default boolean value (true or false)' :
                    propertyType === 'object' ? 'Enter default JSON object (e.g., {"key": "value"})' :
                    propertyType === 'array' ? 'Enter default JSON array (e.g., ["item1", "item2"])' :
                    propertyType === 'enum' ? 'Enter default enum value from the list below' :
                    'Enter default value'
                  }
                  className="bg-background text-sm"
                />
              </div>

              {/* Enum Values Section */}
              {propertyType === 'enum' && (
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Label className="text-xs text-muted-foreground">Enum Values</Label>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={addEnumValue}
                      className="h-6 text-xs"
                    >
                      <Plus className="h-3 w-3 mr-1" />
                      Add Value
                    </Button>
                  </div>

                  {propertyEnumValues.length > 0 ? (
                    <div className="space-y-1 max-h-24 overflow-y-auto">
                      {propertyEnumValues.map((value, index) => (
                        <div key={index} className="flex items-center justify-between p-2 bg-muted/50 rounded border">
                          <span className="text-sm font-mono">{value}</span>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => removeEnumValue(index)}
                            className="h-6 w-6 p-0 text-red-500 hover:text-red-600 hover:bg-red-500/10"
                          >
                            <Trash2 className="h-3 w-3" />
                          </Button>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p className="text-xs text-muted-foreground italic">
                      No enum values defined. Click "Add Value" to add options.
                    </p>
                  )}
                </div>
              )}

              {/* Object Properties Section */}
              {propertyType === 'object' && (
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Label className="text-xs text-muted-foreground">Object Properties</Label>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setObjectModalOpen(true)}
                      className="h-6 text-xs"
                    >
                      {Object.keys(propertyObjectProperties).length > 0 ? (
                        <>
                          <Pencil className="h-3 w-3 mr-1" />
                          Edit Properties
                        </>
                      ) : (
                        <>
                          <Plus className="h-3 w-3 mr-1" />
                          Define Properties
                        </>
                      )}
                    </Button>
                  </div>

                  {Object.keys(propertyObjectProperties).length > 0 ? (
                    <div className="space-y-1 max-h-24 overflow-y-auto">
                      {Object.entries(propertyObjectProperties).map(([propName, propDef]) => (
                        <div key={propName} className="flex items-center justify-between p-2 bg-muted/50 rounded border">
                          <div className="flex items-center space-x-2">
                            <span className="text-sm font-mono">{propName}</span>
                            <span className="text-xs text-muted-foreground px-1 py-0.5 bg-background rounded">
                              {propDef.type}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p className="text-xs text-muted-foreground italic">
                      No properties defined. Click "Define Properties" to add object structure.
                    </p>
                  )}
                </div>
              )}

              {/* Array Items Section */}
              {propertyType === 'array' && (
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Label className="text-xs text-muted-foreground">Array Items</Label>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setArrayModalOpen(true)}
                      className="h-6 text-xs"
                    >
                      {propertyArrayItems ? (
                        <>
                          <Pencil className="h-3 w-3 mr-1" />
                          Edit Items
                        </>
                      ) : (
                        <>
                          <Plus className="h-3 w-3 mr-1" />
                          Define Items
                        </>
                      )}
                    </Button>
                  </div>

                  {propertyArrayItems ? (
                    <div className="p-2 bg-muted/50 rounded border">
                      <div className="flex items-center space-x-2">
                        <span className="text-sm font-medium">Items Type:</span>
                        <span className="text-xs text-muted-foreground px-2 py-0.5 bg-background rounded">
                          {propertyArrayItems.type}
                        </span>
                      </div>
                      {propertyArrayItems.description && (
                        <p className="text-xs text-muted-foreground mt-1">
                          {propertyArrayItems.description}
                        </p>
                      )}
                    </div>
                  ) : (
                    <p className="text-xs text-muted-foreground italic">
                      No items defined. Click "Define Items" to specify array item structure.
                    </p>
                  )}
                </div>
              )}

              <Button
                size="sm"
                onClick={handleAddProperty}
                className="w-full bg-positive-trend hover:bg-positive-trend/90 text-white"
              >
                {editingPropertyName ? (
                  <>
                    <Pencil className="h-4 w-4 mr-2" />
                    Update Parameter
                  </>
                ) : (
                  <>
                    <Plus className="h-4 w-4 mr-2" />
                    Add Parameter
                  </>
                )}
              </Button>
              
              {/* Note about default required behavior */}
              {!editingPropertyName && (
                <div className="text-xs text-muted-foreground bg-blue-50 border border-blue-200 rounded-md p-2 mt-2">
                  <div className="flex items-center space-x-1">
                    <div className="w-1.5 h-1.5 bg-blue-500 rounded-full"></div>
                    <span>New parameters are required by default</span>
                  </div>
                </div>
              )}
            </div>

            {/* Parameters List */}
            {Object.keys(parameters).length > 0 && (
              <div className="space-y-2">
                <Label className="text-sm font-medium">Parameters</Label>
                <div className="space-y-2 max-h-[200px] overflow-y-auto">
                  {Object.entries(parameters).map(([propName, propDef]) => (
                    <div
                      key={propName}
                      className={`flex items-center justify-between p-3 rounded-md border transition-all ${
                        editingPropertyName === propName
                          ? 'bg-positive-trend/10 border-positive-trend/40'
                          : 'bg-muted/50 border-border'
                      }`}
                    >
                      <div className="flex-1">
                        <div className="flex items-center space-x-2 flex-wrap">
                          <span className="font-mono text-sm font-medium">{propName}</span>
                          <span className="text-xs text-muted-foreground px-2 py-0.5 bg-background rounded">
                            {propDef.enum && propDef.enum.length > 0 ? 'enum' : propDef.type}
                          </span>
                          {propDef.default !== undefined && (
                            <span className="text-xs text-green-600 px-2 py-0.5 bg-green-50 rounded border border-green-200">
                              default: {typeof propDef.default === 'string' ? `"${propDef.default}"` : JSON.stringify(propDef.default)}
                            </span>
                          )}
                        </div>
                        {propDef.description && (
                          <p className="text-xs text-muted-foreground mt-1">
                            {propDef.description}
                          </p>
                        )}
                        {/* {propDef.properties && Object.keys(propDef.properties).length > 0 && (
                          <div className="mt-2">
                            <p className="text-xs text-muted-foreground mb-1">Properties:</p>
                            <div className="space-y-1">
                              {Object.entries(propDef.properties).map(([nestedPropName, nestedPropDef]) => (
                                <div key={nestedPropName} className="flex items-center space-x-2 text-xs">
                                  <span className="font-mono text-muted-foreground">{nestedPropName}</span>
                                  <span className="px-1 py-0.5 bg-muted rounded text-muted-foreground">
                                    {nestedPropDef.type}
                                  </span>
                                </div>
                              ))}
                            </div>
                          </div>
                        )} */}
                        {/* {propDef.items && (
                          <div className="mt-2">
                            <p className="text-xs text-muted-foreground mb-1">Array Items:</p>
                            <div className="flex items-center space-x-2 text-xs">
                              <span className="px-1 py-0.5 bg-muted rounded text-muted-foreground">
                                {propDef.items.type}
                              </span>
                              {propDef.items.description && (
                                <span className="text-muted-foreground italic">
                                  {propDef.items.description}
                                </span>
                              )}
                            </div>
                          </div>
                        )} */}
                      </div>
                      <div className="flex items-center space-x-2">
                        <label className="flex items-center space-x-2 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={requiredFields.includes(propName)}
                            onChange={() => handleToggleRequired(propName)}
                            className="w-4 h-4 rounded border-gray-300 text-positive-trend focus:ring-positive-trend"
                          />
                          <span className="text-xs text-muted-foreground">Required</span>
                        </label>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleEditProperty(propName)}
                          className="h-8 w-8 p-0 text-blue-500 hover:text-blue-600 hover:bg-blue-500/10"
                          title="Edit parameter"
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleRemoveProperty(propName)}
                          className="h-8 w-8 p-0 text-red-500 hover:text-red-600 hover:bg-red-500/10"
                          title="Delete parameter"
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}

            {/* Execution Specs Section */}
            <div className="space-y-3 p-4 bg-muted/30 rounded-lg border border-border">
              <div className="flex items-center justify-between">
                <Label className="text-sm font-medium">Execution Specs (Optional)</Label>
                <Switch
                  checked={includeExecutionSpecs}
                  onCheckedChange={setIncludeExecutionSpecs}
                />
              </div>
              
              {includeExecutionSpecs && (
                <div className="space-y-3 pt-2">
                  <div className="space-y-2">
                    <Label htmlFor="executionType" className="text-xs">Execution Type</Label>
                    <Input
                      id="executionType"
                      value={executionType}
                      readOnly
                      disabled
                      placeholder="client_side"
                      className="bg-background text-sm opacity-70 cursor-not-allowed"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="maxRetry" className="text-xs">Max Retry Attempts</Label>
                    <Input
                      id="maxRetry"
                      type="number"
                      value={maxRetryAttempts}
                      onChange={(e) => setMaxRetryAttempts(Number(e.target.value))}
                      className="bg-background text-sm"
                    />
                  </div>
                  
                  <div className="space-y-2">
                    <Label htmlFor="waitTime" className="text-xs">Wait Time (milliseconds)</Label>
                    <Input
                      id="waitTime"
                      type="number"
                      value={waitTimeInMillis}
                      onChange={(e) => setWaitTimeInMillis(Number(e.target.value))}
                      className="bg-background text-sm"
                    />
                  </div>
                </div>
              )}
            </div>

            {/* Options */}
            <div className="space-y-3">
              <div className="flex items-center justify-between p-3 bg-muted/30 rounded-lg border border-border">
                <Label htmlFor="strict" className="text-sm font-medium cursor-pointer">
                  Strict Mode
                </Label>
                <Switch
                  id="strict"
                  checked={strict}
                  onCheckedChange={setStrict}
                />
              </div>
              
              <div className="flex items-center justify-between p-3 bg-muted/30 rounded-lg border border-border">
                <Label htmlFor="additionalProps" className="text-sm font-medium cursor-pointer">
                  Allow Additional Properties
                </Label>
                <Switch
                  id="additionalProps"
                  checked={additionalProperties}
                  onCheckedChange={setAdditionalProperties}
                />
              </div>
            </div>
          </div>

          {/* Right Column - JSON Preview */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-sm font-medium">JSON Preview</Label>
              <Button
                variant="ghost"
                size="sm"
                className="h-6 w-6 p-0"
                title="Copy JSON to clipboard"
                onClick={() => copyToClipboard(getJsonPreview(), 'JSON')}
              >
                <Copy className="h-3 w-3 text-muted-foreground" />
              </Button>
            </div>
            <div className="bg-gray-900 rounded-lg p-4 border border-border overflow-auto max-h-[calc(90vh-200px)]">
              <pre className="text-xs text-green-400 font-mono whitespace-pre-wrap">
                {getJsonPreview()}
              </pre>
            </div>
          </div>
          </div>
        ) : (
          // Create mode: show tabs for Form and JSON editors
          <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as any)} defaultValue="form">
            <div className="flex items-center justify-between mb-2">
              <TabsList>
                <TabsTrigger value="form" className="data-[state=active]:bg-positive-trend data-[state=active]:text-white">Form Editor</TabsTrigger>
                <TabsTrigger value="json" className="data-[state=active]:bg-positive-trend data-[state=active]:text-white">JSON Editor</TabsTrigger>
              </TabsList>
            </div>

            <TabsContent value="form">
        <div className="grid grid-cols-2 gap-6">
          {/* Left Column - Configuration */}
          <div className="space-y-6">
            {/* Function Name */}
            <div className="space-y-2">
              <Label htmlFor="name" className="text-sm font-medium">
                Function Name *
              </Label>
              <Input
                id="name"
                value={name}
                onChange={(e) => handleNameChange(e.target.value)}
                placeholder="add_two_numbers"
                readOnly={isEditing}
                disabled={isEditing}
                className={`bg-muted/50 border focus:border-positive-trend/60 ${
                  nameError ? 'border-red-500 focus:border-red-500' : 'border-border'
                } ${isEditing ? 'opacity-70 cursor-not-allowed' : ''}`}
              />
              {nameError && (
                <p className="text-xs text-red-500 mt-1">{nameError}</p>
              )}
            </div>
            {/* Description */}
            <div className="space-y-2">
              <Label htmlFor="description" className="text-sm font-medium">
                Description *
              </Label>
              <Textarea
                id="description"
                value={description}
                onChange={(e) => setDescription(e.target.value)}
                placeholder="Describe what this function does..."
                className="min-h-[80px] bg-muted/50 border border-border focus:border-positive-trend/60"
              />
            </div>
            {/* Add/Edit Property Section */}
            <div className="space-y-3 p-4 bg-muted/30 rounded-lg border border-border">
              <div className="flex items-center justify-between">
                <Label className="text-sm font-medium">
                  {editingPropertyName ? 'Edit Parameter' : 'Add Parameter'}
                </Label>
                {editingPropertyName && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={handleCancelEdit}
                    className="h-6 text-xs text-muted-foreground hover:text-foreground"
                  >
                    Cancel Edit
                  </Button>
                )}
              </div>
              <div className="grid grid-cols-2 gap-2">
                <div className="space-y-1">
                  <Input
                    value={propertyName}
                    onChange={(e) => handlePropertyNameChange(e.target.value)}
                    placeholder="Property name (e.g., 'a')"
                    className={`bg-background ${
                      propertyNameError ? 'border-red-500 focus:border-red-500' : ''
                    }`}
                    disabled={editingPropertyName !== null}
                  />
                  {propertyNameError && (
                    <p className="text-xs text-red-500">{propertyNameError}</p>
                  )}
                </div>
                <select
                  value={propertyType}
                  onChange={(e) => setPropertyType(e.target.value)}
                  className="flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm"
                >
                  <option value="string">String</option>
                  <option value="number">Number</option>
                  <option value="boolean">Boolean</option>
                  <option value="object">Object</option>
                  <option value="array">Array</option>
                  <option value="enum">Enum</option>
                </select>
              </div>
              <Input
                value={propertyDescription}
                onChange={(e) => setPropertyDescription(e.target.value)}
                placeholder="Description (optional)"
                className="bg-background"
              />
              <div className="space-y-1">
                <Input
                  value={propertyDefaultValue}
                  onChange={(e) => setPropertyDefaultValue(e.target.value)}
                  placeholder={
                    propertyType === 'string' ? 'Enter default string value' :
                    propertyType === 'number' ? 'Enter default number (e.g., 42)' :
                    propertyType === 'boolean' ? 'Enter default boolean value (true or false)' :
                    propertyType === 'object' ? 'Enter default JSON object (e.g., {"key": "value"})' :
                    propertyType === 'array' ? 'Enter default JSON array (e.g., ["item1", "item2"])' :
                    propertyType === 'enum' ? 'Enter default enum value from the list below' :
                    'Enter default value'
                  }
                  className="bg-background text-sm"
                />
              </div>
              {propertyType === 'enum' && (
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Label className="text-xs text-muted-foreground">Enum Values</Label>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={addEnumValue}
                      className="h-6 text-xs"
                    >
                      <Plus className="h-3 w-3 mr-1" />
                      Add Value
                    </Button>
                  </div>
                  {propertyEnumValues.length > 0 ? (
                    <div className="space-y-1 max-h-24 overflow-y-auto">
                      {propertyEnumValues.map((value, index) => (
                        <div key={index} className="flex items-center justify-between p-2 bg-muted/50 rounded border">
                          <span className="text-sm font-mono">{value}</span>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => removeEnumValue(index)}
                            className="h-6 w-6 p-0 text-red-500 hover:text-red-600 hover:bg-red-500/10"
                          >
                            <Trash2 className="h-3 w-3" />
                          </Button>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p className="text-xs text-muted-foreground italic">
                      No enum values defined. Click "Add Value" to add options.
                    </p>
                  )}
                </div>
              )}
              {propertyType === 'object' && (
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Label className="text-xs text-muted-foreground">Object Properties</Label>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setObjectModalOpen(true)}
                      className="h-6 text-xs"
                    >
                      {Object.keys(propertyObjectProperties).length > 0 ? (
                        <>
                          <Pencil className="h-3 w-3 mr-1" />
                          Edit Properties
                        </>
                      ) : (
                        <>
                          <Plus className="h-3 w-3 mr-1" />
                          Define Properties
                        </>
                      )}
                    </Button>
                  </div>
                  {Object.keys(propertyObjectProperties).length > 0 ? (
                    <div className="space-y-1 max-h-24 overflow-y-auto">
                      {Object.entries(propertyObjectProperties).map(([propName, propDef]) => (
                        <div key={propName} className="flex items-center justify-between p-2 bg-muted/50 rounded border">
                          <div className="flex items-center space-x-2">
                            <span className="text-sm font-mono">{propName}</span>
                            <span className="text-xs text-muted-foreground px-1 py-0.5 bg-background rounded">
                              {propDef.type}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  ) : (
                    <p className="text-xs text-muted-foreground italic">
                      No properties defined. Click "Define Properties" to add object structure.
                    </p>
                  )}
                </div>
              )}
              {propertyType === 'array' && (
                <div className="space-y-2">
                  <div className="flex items-center justify-between">
                    <Label className="text-xs text-muted-foreground">Array Items</Label>
                    <Button
                      type="button"
                      variant="outline"
                      size="sm"
                      onClick={() => setArrayModalOpen(true)}
                      className="h-6 text-xs"
                    >
                      {propertyArrayItems ? (
                        <>
                          <Pencil className="h-3 w-3 mr-1" />
                          Edit Items
                        </>
                      ) : (
                        <>
                          <Plus className="h-3 w-3 mr-1" />
                          Define Items
                        </>
                      )}
                    </Button>
                  </div>
                  {propertyArrayItems ? (
                    <div className="p-2 bg-muted/50 rounded border">
                      <div className="flex items-center space-x-2">
                        <span className="text-sm font-medium">Items Type:</span>
                        <span className="text-xs text-muted-foreground px-2 py-0.5 bg-background rounded">
                          {propertyArrayItems.type}
                        </span>
                      </div>
                      {propertyArrayItems.description && (
                        <p className="text-xs text-muted-foreground mt-1">
                          {propertyArrayItems.description}
                        </p>
                      )}
                    </div>
                  ) : (
                    <p className="text-xs text-muted-foreground italic">
                      No items defined. Click "Define Items" to specify array item structure.
                    </p>
                  )}
                </div>
              )}
              <Button
                size="sm"
                onClick={handleAddProperty}
                className="w-full bg-positive-trend hover:bg-positive-trend/90 text-white"
              >
                {editingPropertyName ? (
                  <>
                    <Pencil className="h-4 w-4 mr-2" />
                    Update Parameter
                  </>
                ) : (
                  <>
                    <Plus className="h-4 w-4 mr-2" />
                    Add Parameter
                  </>
                )}
              </Button>
              {!editingPropertyName && (
                <div className="text-xs text-muted-foreground bg-blue-50 border border-blue-200 rounded-md p-2 mt-2">
                  <div className="flex items-center space-x-1">
                    <div className="w-1.5 h-1.5 bg-blue-500 rounded-full"></div>
                    <span>New parameters are required by default</span>
                  </div>
                </div>
              )}
            </div>
            {Object.keys(parameters).length > 0 && (
              <div className="space-y-2">
                <Label className="text-sm font-medium">Parameters</Label>
                <div className="space-y-2 max-h-[200px] overflow-y-auto">
                  {Object.entries(parameters).map(([propName, propDef]) => (
                    <div
                      key={propName}
                      className={`flex items-center justify-between p-3 rounded-md border transition-all ${
                        editingPropertyName === propName
                          ? 'bg-positive-trend/10 border-positive-trend/40'
                          : 'bg-muted/50 border-border'
                      }`}
                    >
                      <div className="flex-1">
                        <div className="flex items-center space-x-2 flex-wrap">
                          <span className="font-mono text-sm font-medium">{propName}</span>
                          <span className="text-xs text-muted-foreground px-2 py-0.5 bg-background rounded">
                            {propDef.enum && propDef.enum.length > 0 ? 'enum' : propDef.type}
                          </span>
                          {propDef.default !== undefined && (
                            <span className="text-xs text-green-600 px-2 py-0.5 bg-green-50 rounded border border-green-200">
                              default: {typeof propDef.default === 'string' ? `"${propDef.default}"` : JSON.stringify(propDef.default)}
                            </span>
                          )}
                        </div>
                        {propDef.description && (
                          <p className="text-xs text-muted-foreground mt-1">
                            {propDef.description}
                          </p>
                        )}
                      </div>
                      <div className="flex items-center space-x-2">
                        <label className="flex items-center space-x-2 cursor-pointer">
                          <input
                            type="checkbox"
                            checked={requiredFields.includes(propName)}
                            onChange={() => handleToggleRequired(propName)}
                            className="w-4 h-4 rounded border-gray-300 text-positive-trend focus:ring-positive-trend"
                          />
                          <span className="text-xs text-muted-foreground">Required</span>
                        </label>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleEditProperty(propName)}
                          className="h-8 w-8 p-0 text-blue-500 hover:text-blue-600 hover:bg-blue-500/10"
                          title="Edit parameter"
                        >
                          <Pencil className="h-4 w-4" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleRemoveProperty(propName)}
                          className="h-8 w-8 p-0 text-red-500 hover:text-red-600 hover:bg-red-500/10"
                          title="Delete parameter"
                        >
                          <Trash2 className="h-4 w-4" />
                        </Button>
                      </div>
                    </div>
                  ))}
                </div>
              </div>
            )}
            {/* Execution Specs Section */}
            <div className="space-y-3 p-4 bg-muted/30 rounded-lg border border-border">
              <div className="flex items-center justify-between">
                <Label className="text-sm font-medium">Execution Specs (Optional)</Label>
                <Switch
                  checked={includeExecutionSpecs}
                  onCheckedChange={setIncludeExecutionSpecs}
                />
              </div>
              {includeExecutionSpecs && (
                <div className="space-y-3 pt-2">
                  <div className="space-y-2">
                    <Label htmlFor="executionType" className="text-xs">Execution Type</Label>
                    <Input
                      id="executionType"
                      value={executionType}
                      readOnly
                      disabled
                      placeholder="client_side"
                      className="bg-background text-sm opacity-70 cursor-not-allowed"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="maxRetry" className="text-xs">Max Retry Attempts</Label>
                    <Input
                      id="maxRetry"
                      type="number"
                      value={maxRetryAttempts}
                      onChange={(e) => setMaxRetryAttempts(Number(e.target.value))}
                      className="bg-background text-sm"
                    />
                  </div>
                  <div className="space-y-2">
                    <Label htmlFor="waitTime" className="text-xs">Wait Time (milliseconds)</Label>
                    <Input
                      id="waitTime"
                      type="number"
                      value={waitTimeInMillis}
                      onChange={(e) => setWaitTimeInMillis(Number(e.target.value))}
                      className="bg-background text-sm"
                    />
                  </div>
                </div>
              )}
            </div>
            {/* Options */}
            <div className="space-y-3">
              <div className="flex items-center justify-between p-3 bg-muted/30 rounded-lg border border-border">
                <Label htmlFor="strict" className="text-sm font-medium cursor-pointer">
                  Strict Mode
                </Label>
                <Switch
                  id="strict"
                  checked={strict}
                  onCheckedChange={setStrict}
                />
              </div>
              <div className="flex items-center justify-between p-3 bg-muted/30 rounded-lg border border-border">
                <Label htmlFor="additionalProps" className="text-sm font-medium cursor-pointer">
                  Allow Additional Properties
                </Label>
                <Switch
                  id="additionalProps"
                  checked={additionalProperties}
                  onCheckedChange={setAdditionalProperties}
                />
              </div>
            </div>
          </div>
          {/* Right Column - JSON Preview */}
          <div className="space-y-2">
            <div className="flex items-center justify-between">
              <Label className="text-sm font-medium">JSON Preview</Label>
              <Button
                variant="ghost"
                size="sm"
                className="h-6 w-6 p-0"
                title="Copy JSON to clipboard"
                onClick={() => copyToClipboard(getJsonPreview(), 'JSON')}
              >
                <Copy className="h-3 w-3 text-muted-foreground" />
              </Button>
            </div>
            <div className="bg-gray-900 rounded-lg p-4 border border-border overflow-auto max-h-[calc(90vh-200px)]">
              <pre className="text-xs text-green-400 font-mono whitespace-pre-wrap">
                {getJsonPreview()}
              </pre>
            </div>
          </div>
        </div>
            </TabsContent>

            <TabsContent value="json">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4 items-start">
              {/* Left: Paste schema and Execution Specs */}
              <div className="space-y-3">
                <Label className="text-sm font-medium">Paste Function Schema</Label>
                <Textarea
                  value={jsonInput}
                  onChange={(e) => setJsonInput(e.target.value)}
                  className="min-h-[360px] bg-background border border-input font-mono text-xs focus-visible:ring-2 focus-visible:ring-ring placeholder:text-muted-foreground"
                  placeholder={getJsonPreview()}
                />
                {jsonError && (
                  <div className="text-xs text-red-500">
                    {jsonError}
                  </div>
                )}
                {/* Execution Specs below textarea, collapsible */}
                <div className="space-y-3 p-4 bg-background rounded-xl border border-border shadow-sm">
                    <div className="flex items-center justify-between">
                      <Label className="text-sm font-medium">Execution Specs (Optional)</Label>
                      <Switch
                        checked={executionSpecsEditableJson}
                        onCheckedChange={setExecutionSpecsEditableJson}
                      />
                    </div>
                    {executionSpecsEditableJson && (
                      <div className="space-y-3 pt-2">
                        <div className="space-y-2">
                          <Label htmlFor="executionTypeJson" className="text-xs">Execution Type</Label>
                          <Input
                            id="executionTypeJson"
                            value={executionType}
                            readOnly
                            disabled
                            placeholder="client_side"
                            className="bg-background text-sm opacity-70 cursor-not-allowed"
                          />
                        </div>
                        <div className="space-y-2">
                          <Label htmlFor="maxRetryJson" className="text-xs">Max Retry Attempts</Label>
                          <Input
                            id="maxRetryJson"
                            type="number"
                            value={maxRetryAttempts}
                            onChange={(e) => setMaxRetryAttempts(Number(e.target.value))}
                            className="bg-background text-sm"
                          />
                        </div>
                        <div className="space-y-2">
                          <Label htmlFor="waitTimeJson" className="text-xs">Wait Time (milliseconds)</Label>
                          <Input
                            id="waitTimeJson"
                            type="number"
                            value={waitTimeInMillis}
                            onChange={(e) => setWaitTimeInMillis(Number(e.target.value))}
                            className="bg-background text-sm"
                          />
                        </div>
                      </div>
                    )}
                  </div>
              </div>

              {/* Right: JSON Preview 50% */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label className="text-sm font-medium">JSON Preview</Label>
                  <Button
                    variant="ghost"
                    size="sm"
                    className="h-6 w-6 p-0"
                    title="Copy JSON to clipboard"
                    onClick={() => copyToClipboard(getAugmentedJsonPreviewFromJsonInput(), 'JSON')}
                  >
                    <Copy className="h-3 w-3 text-muted-foreground" />
                  </Button>
                </div>
                <div className="bg-gray-900 rounded-lg p-4 border border-border overflow-auto max-h-[calc(90vh-200px)] min-h-[360px]">
                  <pre className="text-xs text-green-400 font-mono whitespace-pre-wrap">
                    {isJsonValid ? (isEditing ? jsonInput : getAugmentedJsonPreviewFromJsonInput()) : ''}
                  </pre>
                </div>
              </div>
            </div>
            </TabsContent>
          </Tabs>
        )}

        {/* Footer */}
        <div className="flex items-center justify-end space-x-2 pt-4 border-t">
          <Button
            variant="ghost"
            onClick={() => onOpenChange(false)}
            className="hover:bg-muted/50"
          >
            Cancel
          </Button>
          <Button
            onClick={() => (isEditing ? handleSave() : (activeTab === 'json' ? handleSaveFromJson() : handleSave()))}
            disabled={isEditing ? !isFormValid() : (activeTab === 'json' ? !isJsonValid : !isFormValid())}
          >
            {isEditing ? 'Update Tool' : 'Create Tool'}
          </Button>
        </div>
      </DialogContent>
    </Dialog>

    {/* How To Use Modal - Comprehensive flow explanation */}
    <Dialog open={howToUseOpen} onOpenChange={setHowToUseOpen}>
      <DialogContent className="w-full max-w-4xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="text-xl font-bold text-center mb-2">
            How to Use Client-Side Tools
          </DialogTitle>
          <DialogDescription className="text-center text-muted-foreground mb-4 text-sm">
            Follow these simple steps to get your client-side tool up and running
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-6">
          {/* Flow Steps */}
          <div className="space-y-4">
            {/* Step 1 */}
            <div className="flex items-start space-x-3 p-3 bg-muted/30 border border-border rounded-lg">
              <div className="flex-shrink-0 w-6 h-6 bg-positive-trend text-white rounded-full flex items-center justify-center font-bold text-xs">
                1
              </div>
              <div className="flex-1">
                <h3 className="text-base font-semibold text-foreground mb-1">Create Your Tool</h3>
                <p className="text-muted-foreground mb-2 text-sm">
                  Design your client-side tool using the form above. Define parameters, set execution specs, and configure all necessary settings.
                </p>
                <div className="bg-card p-2 rounded border border-border">
                  <code className="text-xs text-foreground">
                    ✓ Fill in tool name and description<br/>
                    ✓ Add required parameters<br/>
                    ✓ Configure execution settings<br/>
                    ✓ Click "Create Tool"
                  </code>
                </div>
              </div>
            </div>

            {/* Step 2 */}
            <div className="flex items-start space-x-3 p-3 bg-muted/30 border border-border rounded-lg">
              <div className="flex-shrink-0 w-6 h-6 bg-positive-trend text-white rounded-full flex items-center justify-center font-bold text-xs">
                2
              </div>
              <div className="flex-1">
                <div className="flex items justify-between mb-2">
                  <h3 className="text-base font-semibold text-foreground">Download Client Runtime</h3>
                </div>
                <div className="space-y-2">
                  <p className="text-muted-foreground text-sm">
                    Download the Java SDK client runtime to execute your tools locally.
                  </p>
                </div>
              </div>
            </div>

            {/* Step 3 */}
            <div className="flex items-start space-x-3 p-3 bg-muted/30 border border-border rounded-lg">
              <div className="flex-shrink-0 w-6 h-6 bg-positive-trend text-white rounded-full flex items-center justify-center font-bold text-xs">
                3
              </div>
              <div className="flex-1">
                <h3 className="text-base font-semibold text-foreground mb-1">Run the Runtime</h3>
                <p className="text-muted-foreground mb-2 text-sm">
                  Execute the Gradle wrapper to start your client-side tool runtime.
                </p>
                <div className="bg-card p-2 rounded border border-border">
                  <div className="text-xs text-foreground font-medium">Run on Unix/Mac:</div>
                  <div className="bg-accentGray-8 text-accentGray-2 p-2 mt-1 rounded font-mono text-xs">
                    <div className="flex items-center space-x-2">
                      <span className="text-accentGray-4">$</span>
                      <span>./gradlew run</span>
                    </div>
                  </div>
                  <div className="text-xs text-foreground font-medium mt-2">Run on Windows:</div>
                  <div className="bg-accentGray-8 text-accentGray-2 p-2 mt-1 rounded font-mono text-xs">
                    <span className="text-accentGray-3">gradlew.bat run</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Step 4 */}
            <div className="flex items-start space-x-3 p-3 bg-muted/30 border border-border rounded-lg">
              <div className="flex-shrink-0 w-6 h-6 bg-positive-trend text-white rounded-full flex items-center justify-center font-bold text-xs">
                4
              </div>
              <div className="flex-1">
                <h3 className="text-base font-semibold text-foreground mb-1">Done! 🎉</h3>
                <p className="text-muted-foreground mb-2 text-sm">
                  Your client-side tool is now running and ready to execute. The runtime will handle all the heavy lifting.
                </p>
              </div>
            </div>
          </div>

          {/* Additional Resources */}
          <div className="border-t border-border pt-4">
            <h3 className="text-base font-semibold mb-3 text-center text-foreground">Additional Resources</h3>
            <div className="flex justify-center">
              <div className="p-3 bg-muted/30 border border-border rounded-lg max-w-md w-full">
                <h4 className="font-medium text-foreground mb-1 text-sm">📖 README</h4>
                <p className="text-xs text-muted-foreground mb-2">
                  Quick start guide and examples
                </p>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => window.open('#', '_blank')}
                  className="w-full text-xs"
                  disabled
                >
                  README Link (Coming Soon)
                </Button>
              </div>
            </div>
          </div>

          {/* Footer */}
          <div className="text-center pt-3 border-t border-border">
            <p className="text-xs text-muted-foreground">
              Need help? Check our documentation or contact support.
            </p>
          </div>
        </div>
      </DialogContent>
    </Dialog>

    {/* Download Modal - Simple modal after tool creation */}
    <Dialog open={downloadModalOpen} onOpenChange={setDownloadModalOpen}>
      <DialogContent className="w-full max-w-md">
        <DialogHeader>
          <DialogTitle className="text-lg font-bold text-center">
            Client‑Side Tool Runtime
          </DialogTitle>
          <DialogDescription className="text-center text-muted-foreground text-sm">
            Download the runtime to execute your tool locally
          </DialogDescription>
        </DialogHeader>

        <div className="space-y-4 py-4">
          <div className="flex justify-center">
            <Button
              size="lg"
              onClick={handleDownloadClientRuntime}
              className="bg-positive-trend hover:bg-positive-trend/90 text-white"
            >
              Download Runtime
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>

    {/* Object Properties Modal */}
    <ObjectPropertiesModal
      open={objectModalOpen}
      onOpenChange={setObjectModalOpen}
      onSave={handleObjectPropertiesSaveEnhanced}
      initialProperties={propertyObjectProperties}
    />

    {/* Array Items Modal */}
    <ArrayItemsModal
      open={arrayModalOpen}
      onOpenChange={setArrayModalOpen}
      onSave={handleArrayItemsSaveEnhanced}
      initialItems={propertyArrayItems}
    />

    {/* Enum Values Modal */}
    <EnumValuesModal
      open={enumModalOpen}
      onOpenChange={setEnumModalOpen}
      onSave={handleEnumValuesSave}
      initialValues={propertyEnumValues}
    />
  </>
  );
};

export default LocalToolModal;

