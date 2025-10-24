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
import { Plus, Trash2, Pencil,Check, X } from 'lucide-react';
import { toast } from 'sonner';
import ObjectPropertiesModal from './ObjectPropertiesModal';

interface PropertyDefinition {
  type: string;
  description?: string;
  default?: any;
  enum?: any[];
  properties?: Record<string, PropertyDefinition>;
  items?: PropertyDefinition;
}

interface ArrayItemsModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSave: (items: PropertyDefinition) => void;
  initialItems?: PropertyDefinition | null;
}

const ArrayItemsModal: React.FC<ArrayItemsModalProps> = ({
  open,
  onOpenChange,
  onSave,
  initialItems = null
}) => {
  const [items, setItems] = useState<PropertyDefinition | null>(null);
  const [propertyType, setPropertyType] = useState('string');
  const [propertyDescription, setPropertyDescription] = useState('');
  const [propertyDefaultValue, setPropertyDefaultValue] = useState('');
  const [propertyEnumValues, setPropertyEnumValues] = useState<string[]>([]);
  const [propertyObjectProperties, setPropertyObjectProperties] = useState<Record<string, PropertyDefinition>>({});

  // Modal states
  const [objectModalOpen, setObjectModalOpen] = useState(false);

  // Load initial items
  useEffect(() => {
    if (open) {
      if (initialItems) {
        setItems(initialItems);
        setPropertyType(initialItems.type);
        setPropertyDescription(initialItems.description || '');
        setPropertyDefaultValue(initialItems.default !== undefined ? JSON.stringify(initialItems.default) : '');
        setPropertyEnumValues(initialItems.enum || []);
        setPropertyObjectProperties(initialItems.properties || {});
      } else {
        resetForm();
      }
    }
  }, [open, initialItems]);

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

  // Modal handlers
  const handleObjectPropertiesSave = (properties: Record<string, PropertyDefinition>) => {
    setPropertyObjectProperties(properties);
  };

  const handleSave = () => {
    if (propertyType === 'enum' && propertyEnumValues.length === 0) {
      toast.error('Enum type requires at least one enum value');
      return;
    }

    if (propertyType === 'enum' && propertyDefaultValue.trim() && !propertyEnumValues.includes(propertyDefaultValue.trim())) {
      toast.error('Default value must be one of the defined enum values');
      return;
    }

    // Validate object type
    if (propertyType === 'object' && Object.keys(propertyObjectProperties).length === 0) {
      toast.error('Object type requires at least one property');
      return;
    }

    const parsedDefaultValue = parseDefaultValue(propertyDefaultValue, propertyType);
    
    const newItems: PropertyDefinition = {
      type: propertyType,
      description: propertyDescription.trim() || undefined,
      ...(parsedDefaultValue !== undefined && { default: parsedDefaultValue }),
      ...(propertyType === 'enum' && propertyEnumValues.length > 0 && { enum: propertyEnumValues }),
      ...(propertyType === 'object' && Object.keys(propertyObjectProperties).length > 0 && { properties: propertyObjectProperties }),
    };

    onSave(newItems);
    onOpenChange(false);
  };

  const handleCancel = () => {
    if (initialItems) {
      setItems(initialItems);
      setPropertyType(initialItems.type);
      setPropertyDescription(initialItems.description || '');
      setPropertyDefaultValue(initialItems.default !== undefined ? JSON.stringify(initialItems.default) : '');
      setPropertyEnumValues(initialItems.enum || []);
    } else {
      resetForm();
    }
    onOpenChange(false);
  };

  const resetForm = () => {
    setItems(null);
    setPropertyType('string');
    setPropertyDescription('');
    setPropertyDefaultValue('');
    setPropertyEnumValues([]);
    setPropertyObjectProperties({});
  };

  return (
    <>
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-full max-w-2xl">
        <DialogHeader>
          <DialogTitle className="flex items-center space-x-2">
            <Pencil className="h-5 w-5" />
            <span>Array Items Definition</span>
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-6">
          <div className="space-y-4 p-4 bg-muted/30 rounded-lg border">
            <div className="space-y-2">
              <Label className="text-sm font-medium">Items Type</Label>
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

            <div className="space-y-2">
              <Textarea
                value={propertyDescription}
                onChange={(e) => setPropertyDescription(e.target.value)}
                placeholder="Describe (optional) what each array item represents..."
                className="min-h-[60px]"
              />
            </div>

            <div className="space-y-2">
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
                  <Label className="text-sm font-medium">Enum Values</Label>
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

            {/* Object Properties Section */}
            {propertyType === 'object' && (
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <Label className="text-sm font-medium">Object Properties</Label>
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


            {/* Preview */}
            {items && (
              <div className="space-y-2">
                <Label className="text-sm font-medium">Preview</Label>
                <div className="p-3 bg-muted/50 rounded border">
                  <div className="flex items-center space-x-2 flex-wrap">
                    <span className="text-sm font-medium">Array Items:</span>
                    <span className="text-xs text-muted-foreground px-2 py-0.5 bg-background rounded">
                      {propertyType}
                    </span>
                    {propertyDefaultValue && (
                      <span className="text-xs text-green-600 px-2 py-0.5 bg-green-50 rounded border border-green-200">
                        default: {typeof parseDefaultValue(propertyDefaultValue, propertyType) === 'string' ? `"${parseDefaultValue(propertyDefaultValue, propertyType)}"` : JSON.stringify(parseDefaultValue(propertyDefaultValue, propertyType))}
                      </span>
                    )}
                    {propertyEnumValues.length > 0 && (
                      <span className="text-xs text-blue-600 px-2 py-0.5 bg-blue-50 rounded border border-blue-200">
                        enum: [{propertyEnumValues.map(v => `"${v}"`).join(', ')}]
                      </span>
                    )}
                  </div>
                  {propertyDescription && (
                    <p className="text-xs text-muted-foreground mt-1">
                      {propertyDescription}
                    </p>
                  )}
                  {propertyObjectProperties && Object.keys(propertyObjectProperties).length > 0 && (
                    <div className="mt-2">
                      <p className="text-xs text-muted-foreground mb-1">Properties:</p>
                      <div className="space-y-1">
                        {Object.entries(propertyObjectProperties).map(([propName, propDef]) => (
                          <div key={propName} className="flex items-center space-x-2 text-xs">
                            <span className="font-mono text-muted-foreground">{propName}</span>
                            <span className="px-1 py-0.5 bg-muted rounded text-muted-foreground">
                              {propDef.type}
                            </span>
                          </div>
                        ))}
                      </div>
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleCancel}>
            Cancel
          </Button>
          <Button onClick={handleSave}>
            Save Items Definition
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>

    {/* Nested Object Properties Modal */}
    <ObjectPropertiesModal
      open={objectModalOpen}
      onOpenChange={setObjectModalOpen}
      onSave={handleObjectPropertiesSave}
      initialProperties={propertyObjectProperties}
    />
  </>
  );
};

export default ArrayItemsModal;
