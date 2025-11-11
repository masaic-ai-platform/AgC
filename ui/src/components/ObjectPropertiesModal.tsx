import React, { useState, useEffect } from 'react';
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogFooter,
} from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Textarea } from '@/components/ui/textarea';
import { Plus, Trash2, Pencil, Check, X } from 'lucide-react';
import { toast } from 'sonner';

interface PropertyDefinition {
  type: string;
  description?: string;
  default?: any;
  enum?: any[];
}

interface ObjectPropertiesModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSave: (properties: Record<string, PropertyDefinition>) => void;
  initialProperties?: Record<string, PropertyDefinition>;
}

const ObjectPropertiesModal: React.FC<ObjectPropertiesModalProps> = ({
  open,
  onOpenChange,
  onSave,
  initialProperties = {}
}) => {
  const [properties, setProperties] = useState<Record<string, PropertyDefinition>>({});
  const [editingProperty, setEditingProperty] = useState<string | null>(null);
  
  // Property form state
  const [propertyName, setPropertyName] = useState('');
  const [propertyNameError, setPropertyNameError] = useState('');
  const [propertyType, setPropertyType] = useState('string');
  const [propertyDescription, setPropertyDescription] = useState('');
  const [propertyDefaultValue, setPropertyDefaultValue] = useState('');
  const [propertyEnumValues, setPropertyEnumValues] = useState<string[]>([]);

  // Load initial properties
  useEffect(() => {
    if (open) {
      setProperties(initialProperties);
    }
  }, [open, initialProperties]);

  const validatePropertyName = (name: string): string | null => {
    if (!name.trim()) return 'Property name is required';
    if (!/^[a-zA-Z_][a-zA-Z0-9_]*$/.test(name.trim())) {
      return 'Property name must start with letter or underscore and contain only letters, numbers, and underscores';
    }
    if (editingProperty !== name && properties[name.trim()]) {
      return 'Property name already exists';
    }
    return null;
  };

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
      return value;
    }
  };

  const addEnumValue = () => {
    const newValue = prompt('Enter enum value:');
    if (newValue && newValue.trim() && !propertyEnumValues.includes(newValue.trim())) {
      setPropertyEnumValues([...propertyEnumValues, newValue.trim()]);
    }
  };

  const removeEnumValue = (index: number) => {
    setPropertyEnumValues(propertyEnumValues.filter((_, i) => i !== index));
  };

  const handleAddProperty = () => {
    const nameValidationError = validatePropertyName(propertyName);
    if (nameValidationError) {
      setPropertyNameError(nameValidationError);
      toast.error(nameValidationError);
      return;
    }

    if (propertyType === 'enum' && propertyEnumValues.length === 0) {
      toast.error('Enum type requires at least one enum value');
      return;
    }

    if (propertyType === 'enum' && propertyDefaultValue.trim() && !propertyEnumValues.includes(propertyDefaultValue.trim())) {
      toast.error('Default value must be one of the defined enum values');
      return;
    }

    const parsedDefaultValue = parseDefaultValue(propertyDefaultValue, propertyType);
    
    const newProperty: PropertyDefinition = {
      type: propertyType === 'enum' ? 'string' : propertyType,
      description: propertyDescription.trim() || undefined,
      ...(parsedDefaultValue !== undefined && { default: parsedDefaultValue }),
      ...(propertyType === 'enum' && propertyEnumValues.length > 0 && { enum: propertyEnumValues }),
    };

    if (editingProperty && editingProperty !== propertyName) {
      const newProps = { ...properties };
      delete newProps[editingProperty];
      setProperties({ ...newProps, [propertyName]: newProperty });
    } else {
      setProperties({ ...properties, [propertyName]: newProperty });
    }

    resetPropertyForm();
  };

  const handleEditProperty = (propName: string) => {
    const property = properties[propName];
    setPropertyName(propName);
    setPropertyNameError('');
    // If property has enum values, treat it as enum type in UI (even if saved type is 'string')
    setPropertyType(property.enum && property.enum.length > 0 ? 'enum' : property.type);
    setPropertyDescription(property.description || '');
    setPropertyDefaultValue(property.default !== undefined ? JSON.stringify(property.default) : '');
    setPropertyEnumValues(property.enum || []);
    setEditingProperty(propName);
  };

  const handleRemoveProperty = (propName: string) => {
    const newProps = { ...properties };
    delete newProps[propName];
    setProperties(newProps);
    
    if (editingProperty === propName) {
      resetPropertyForm();
    }
  };

  const resetPropertyForm = () => {
    setPropertyName('');
    setPropertyNameError('');
    setPropertyType('string');
    setPropertyDescription('');
    setPropertyDefaultValue('');
    setPropertyEnumValues([]);
    setEditingProperty(null);
  };

  const handleSave = () => {
    onSave(properties);
    onOpenChange(false);
  };

  const handleCancel = () => {
    setProperties(initialProperties);
    resetPropertyForm();
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-full max-w-4xl max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle className="flex items-center space-x-2">
            <Pencil className="h-5 w-5" />
            <span>Object Properties</span>
          </DialogTitle>
        </DialogHeader>

        <div className="grid grid-cols-2 gap-6">
          {/* Left Column - Add/Edit Property */}
          <div className="space-y-4">
            <div className="space-y-3 p-4 bg-muted/30 rounded-lg border">
              <div className="flex items-center justify-between">
                <Label className="text-sm font-medium">
                  {editingProperty ? 'Edit Property' : 'Add Property'}
                </Label>
                {editingProperty && (
                  <Button
                    variant="ghost"
                    size="sm"
                    onClick={resetPropertyForm}
                    className="h-6 text-xs"
                  >
                    <X className="h-3 w-3 mr-1" />
                    Cancel
                  </Button>
                )}
              </div>

              <div className="grid grid-cols-2 gap-2">
                <div className="space-y-1">
                  <Input
                    value={propertyName}
                    onChange={(e) => {
                      setPropertyName(e.target.value);
                      setPropertyNameError('');
                    }}
                    placeholder="Property name"
                    className={propertyNameError ? 'border-red-500' : ''}
                    disabled={editingProperty !== null}
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

              <Textarea
                value={propertyDescription}
                onChange={(e) => setPropertyDescription(e.target.value)}
                placeholder="Description (optional)"
                className="min-h-[60px]"
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
                  className="text-sm"
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
                    <div className="space-y-1 max-h-20 overflow-y-auto">
                      {propertyEnumValues.map((value, index) => (
                        <div key={index} className="flex items-center justify-between p-2 bg-muted/50 rounded border">
                          <span className="text-sm font-mono">{value}</span>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => removeEnumValue(index)}
                            className="h-6 w-6 p-0 text-red-500 hover:text-red-600"
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

              <Button
                onClick={handleAddProperty}
                className="w-full"
                size="sm"
              >
                {editingProperty ? (
                  <>
                    <Check className="h-4 w-4 mr-2" />
                    Update Property
                  </>
                ) : (
                  <>
                    <Plus className="h-4 w-4 mr-2" />
                    Add Property
                  </>
                )}
              </Button>
            </div>
          </div>

          {/* Right Column - Properties List */}
          <div className="space-y-4">
            <Label className="text-sm font-medium">Properties ({Object.keys(properties).length})</Label>
            
            {Object.keys(properties).length > 0 ? (
              <div className="space-y-2 max-h-[400px] overflow-y-auto">
                {Object.entries(properties).map(([propName, propDef]) => (
                  <div
                    key={propName}
                    className={`p-3 rounded-md border transition-all ${
                      editingProperty === propName
                        ? 'bg-blue-50 border-blue-200'
                        : 'bg-muted/50 border-border'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <div className="flex-1">
                        <div className="flex items-center space-x-2 flex-wrap">
                          <span className="font-mono text-sm font-medium">{propName}</span>
                          <span className="text-xs text-muted-foreground px-2 py-0.5 bg-background rounded">
                            {propDef.type}
                          </span>
                          {propDef.default !== undefined && (
                            <span className="text-xs text-green-600 px-2 py-0.5 bg-green-50 rounded border border-green-200">
                              default: {typeof propDef.default === 'string' ? `"${propDef.default}"` : JSON.stringify(propDef.default)}
                            </span>
                          )}
                          {propDef.enum && propDef.enum.length > 0 && (
                            <span className="text-xs text-blue-600 px-2 py-0.5 bg-blue-50 rounded border border-blue-200">
                              enum: [{propDef.enum.map(v => `"${v}"`).join(', ')}]
                            </span>
                          )}
                        </div>
                        {propDef.description && (
                          <p className="text-xs text-muted-foreground mt-1">
                            {propDef.description}
                          </p>
                        )}
                      </div>
                      <div className="flex items-center space-x-1">
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleEditProperty(propName)}
                          className="h-6 w-6 p-0 text-blue-500 hover:text-blue-600"
                        >
                          <Pencil className="h-3 w-3" />
                        </Button>
                        <Button
                          variant="ghost"
                          size="sm"
                          onClick={() => handleRemoveProperty(propName)}
                          className="h-6 w-6 p-0 text-red-500 hover:text-red-600"
                        >
                          <Trash2 className="h-3 w-3" />
                        </Button>
                      </div>
                    </div>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-8 text-muted-foreground">
                <p className="text-sm">No properties defined</p>
                <p className="text-xs">Add properties using the form on the left</p>
              </div>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleCancel}>
            Cancel
          </Button>
          <Button onClick={handleSave}>
            Save Properties
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default ObjectPropertiesModal;
