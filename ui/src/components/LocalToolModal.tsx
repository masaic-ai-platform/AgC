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

interface LocalToolModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSave: (tool: LocalTool) => void;
  initialTool?: LocalTool | null;
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
}

interface PropertyDefinition {
  type: string;
  description?: string;
  enum?: any[];
  properties?: Record<string, PropertyDefinition>;
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

  // Property editing state
  const [propertyName, setPropertyName] = useState('');
  const [propertyNameError, setPropertyNameError] = useState('');
  const [propertyType, setPropertyType] = useState('string');
  const [propertyDescription, setPropertyDescription] = useState('');
  const [editingPropertyName, setEditingPropertyName] = useState<string | null>(null);

  // Load existing tool configuration when editing
  useEffect(() => {
    if (open) {
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
        
        // Always set execution specs toggle to false when editing
        setIncludeExecutionSpecs(false);
        
        // Load required fields (exclude execution_specs as it's always included)
        if (initialTool.parameters?.required) {
          setRequiredFields(initialTool.parameters.required.filter(f => f !== 'execution_specs'));
        }
        
        setIsEditing(true);
      } else {
        resetForm();
      }
    }
  }, [open, initialTool]);

  // Validate function name (only alphanumeric and underscores, must start with letter)
  const validateFunctionName = (value: string) => {
    if (!value.trim()) {
      return 'Function name is required';
    }
    if (!/^[a-zA-Z][a-zA-Z0-9_]*$/.test(value)) {
      return 'Function name must start with a letter and can only contain letters, numbers, and underscores (e.g., add_two_numbers)';
    }
    return '';
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
    setEditingPropertyName(null);
    setExecutionType('client_side');
    setMaxRetryAttempts(1);
    setWaitTimeInMillis(60000);
    setIncludeExecutionSpecs(false);
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

    const newProperty: PropertyDefinition = {
      type: propertyType,
      description: propertyDescription.trim() || undefined,
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
    }

    // Reset form
    setPropertyName('');
    setPropertyType('string');
    setPropertyDescription('');
    setEditingPropertyName(null);
  };

  const handleEditProperty = (propName: string) => {
    const property = parameters[propName];
    setPropertyName(propName);
    setPropertyNameError('');
    setPropertyType(property.type);
    setPropertyDescription(property.description || '');
    setEditingPropertyName(propName);
  };

  const handleCancelEdit = () => {
    setPropertyName('');
    setPropertyNameError('');
    setPropertyType('string');
    setPropertyDescription('');
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
    const finalParameters = { ...parameters };
    
    // Always add execution_specs with default values (regardless of UI toggle)
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

      const response = await apiClient.rawRequest('/v1/dashboard/download', {
        method: 'POST',
        body: JSON.stringify({
          functionName: effectiveName,
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
      const contentDisposition = response.headers.get('content-disposition') || '';
      const filenameMatch = contentDisposition.match(/filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/);
      const filename =
        (filenameMatch && (decodeURIComponent(filenameMatch[1] || filenameMatch[2])) ) ||
        `${effectiveName}_client_runtime.zip`;

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
                </select>
              </div>
              
              <Input
                value={propertyDescription}
                onChange={(e) => setPropertyDescription(e.target.value)}
                placeholder="Description (optional)"
                className="bg-background"
              />
              
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
                        <div className="flex items-center space-x-2">
                          <span className="font-mono text-sm font-medium">{propName}</span>
                          <span className="text-xs text-muted-foreground px-2 py-0.5 bg-background rounded">
                            {propDef.type}
                          </span>
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
            onClick={handleSave}
            disabled={!isFormValid()}
            className={`${
              isFormValid() 
                ? 'bg-positive-trend hover:bg-positive-trend/90 text-white' 
                : 'bg-gray-400 text-gray-200 cursor-not-allowed'
            }`}
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
            <div className="flex items-start space-x-3 p-3 bg-green-50 border border-green-200 rounded-lg">
              <div className="flex-shrink-0 w-6 h-6 bg-green-500 text-white rounded-full flex items-center justify-center font-bold text-xs">
                1
              </div>
              <div className="flex-1">
                <h3 className="text-base font-semibold text-green-800 mb-1">Create Your Tool</h3>
                <p className="text-green-700 mb-2 text-sm">
                  Design your client-side tool using the form above. Define parameters, set execution specs, and configure all necessary settings.
                </p>
                <div className="bg-white p-2 rounded border border-green-200">
                  <code className="text-xs text-gray-700">
                    ✓ Fill in tool name and description<br/>
                    ✓ Add required parameters<br/>
                    ✓ Configure execution settings<br/>
                    ✓ Click "Create Tool"
                  </code>
                </div>
              </div>
            </div>

            {/* Step 2 */}
            <div className="flex items-start space-x-3 p-3 bg-blue-50 border border-blue-200 rounded-lg">
              <div className="flex-shrink-0 w-6 h-6 bg-blue-500 text-white rounded-full flex items-center justify-center font-bold text-xs">
                2
              </div>
              <div className="flex-1">
                <div className="flex items justify-between mb-2">
                  <h3 className="text-base font-semibold text-blue-800">Download Client Runtime</h3>
                </div>
                <div className="space-y-2">
                  <p className="text-blue-700 text-sm">
                    Download the Java SDK client runtime to execute your tools locally.
                  </p>
                </div>
              </div>
            </div>

            {/* Step 3 */}
            <div className="flex items-start space-x-3 p-3 bg-purple-50 border border-purple-200 rounded-lg">
              <div className="flex-shrink-0 w-6 h-6 bg-purple-500 text-white rounded-full flex items-center justify-center font-bold text-xs">
                3
              </div>
              <div className="flex-1">
                <h3 className="text-base font-semibold text-purple-800 mb-1">Run the Runtime</h3>
                <p className="text-purple-700 mb-2 text-sm">
                  Execute the Gradle wrapper to start your client-side tool runtime.
                </p>
                <div className="bg-white p-2 rounded border border-purple-200">
                  <div className="text-xs text-gray-700 font-medium">Run on Unix/Mac:</div>
                  <div className="bg-gray-900 text-green-400 p-2 mt-1 rounded font-mono text-xs">
                    <div className="flex items-center space-x-2">
                      <span className="text-gray-400">$</span>
                      <span>./gradlew run</span>
                    </div>
                  </div>
                  <div className="text-xs text-gray-700 font-medium mt-2">Run on Windows:</div>
                  <div className="bg-gray-900 text-green-400 p-2 mt-1 rounded font-mono text-xs">
                    <span className="text-gray-300">gradlew.bat run</span>
                  </div>
                </div>
              </div>
            </div>

            {/* Step 4 */}
            <div className="flex items-start space-x-3 p-3 bg-emerald-50 border border-emerald-200 rounded-lg">
              <div className="flex-shrink-0 w-6 h-6 bg-emerald-500 text-white rounded-full flex items-center justify-center font-bold text-xs">
                4
              </div>
              <div className="flex-1">
                <h3 className="text-base font-semibold text-emerald-800 mb-1">Done! 🎉</h3>
                <p className="text-emerald-700 mb-2 text-sm">
                  Your client-side tool is now running and ready to execute. The runtime will handle all the heavy lifting.
                </p>
              </div>
            </div>
          </div>

          {/* Additional Resources */}
          <div className="border-t pt-4">
            <h3 className="text-base font-semibold mb-3 text-center">Additional Resources</h3>
            <div className="flex justify-center">
              <div className="p-3 bg-gray-50 border border-gray-200 rounded-lg max-w-md w-full">
                <h4 className="font-medium text-gray-800 mb-1 text-sm">📖 README</h4>
                <p className="text-xs text-gray-600 mb-2">
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
          <div className="text-center pt-3 border-t">
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
  </>
  );
};

export default LocalToolModal;

