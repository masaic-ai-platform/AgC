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
import { Plus, Trash2, Check, X } from 'lucide-react';
import { toast } from 'sonner';

interface EnumValuesModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSave: (values: string[]) => void;
  initialValues?: string[];
}

const EnumValuesModal: React.FC<EnumValuesModalProps> = ({
  open,
  onOpenChange,
  onSave,
  initialValues = []
}) => {
  const [values, setValues] = useState<string[]>([]);
  const [newValue, setNewValue] = useState('');

  // Load initial values
  useEffect(() => {
    if (open) {
      setValues(initialValues);
      setNewValue('');
    }
  }, [open, initialValues]);

  const handleAddValue = () => {
    const trimmedValue = newValue.trim();
    if (!trimmedValue) {
      toast.error('Please enter a value');
      return;
    }
    if (values.includes(trimmedValue)) {
      toast.error('Value already exists');
      return;
    }
    setValues([...values, trimmedValue]);
    setNewValue('');
  };

  const handleRemoveValue = (index: number) => {
    setValues(values.filter((_, i) => i !== index));
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleAddValue();
    }
  };

  const handleSave = () => {
    if (values.length === 0) {
      toast.error('At least one enum value is required');
      return;
    }
    onSave(values);
    onOpenChange(false);
  };

  const handleCancel = () => {
    setValues(initialValues);
    setNewValue('');
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="w-full max-w-md">
        <DialogHeader>
          <DialogTitle className="flex items-center space-x-2">
            <Plus className="h-5 w-5" />
            <span>Enum Values</span>
          </DialogTitle>
        </DialogHeader>

        <div className="space-y-4">
          <div className="space-y-2">
            <Label className="text-sm font-medium">Add New Value</Label>
            <div className="flex space-x-2">
              <Input
                value={newValue}
                onChange={(e) => setNewValue(e.target.value)}
                onKeyPress={handleKeyPress}
                placeholder="Enter enum value..."
                className="flex-1"
              />
              <Button onClick={handleAddValue} size="sm">
                <Plus className="h-4 w-4" />
              </Button>
            </div>
          </div>

          <div className="space-y-2">
            <Label className="text-sm font-medium">Values ({values.length})</Label>
            {values.length > 0 ? (
              <div className="space-y-1 max-h-48 overflow-y-auto border rounded-md p-2">
                {values.map((value, index) => (
                  <div key={index} className="flex items-center justify-between p-2 bg-muted/50 rounded border">
                    <span className="text-sm font-mono">{value}</span>
                    <Button
                      variant="ghost"
                      size="sm"
                      onClick={() => handleRemoveValue(index)}
                      className="h-6 w-6 p-0 text-red-500 hover:text-red-600"
                    >
                      <Trash2 className="h-3 w-3" />
                    </Button>
                  </div>
                ))}
              </div>
            ) : (
              <div className="text-center py-4 text-muted-foreground border rounded-md">
                <p className="text-sm">No values defined</p>
                <p className="text-xs">Add values using the input above</p>
              </div>
            )}
          </div>
        </div>

        <DialogFooter>
          <Button variant="outline" onClick={handleCancel}>
            Cancel
          </Button>
          <Button onClick={handleSave}>
            Save Values
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};

export default EnumValuesModal;
