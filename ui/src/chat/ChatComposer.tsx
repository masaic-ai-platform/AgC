import React, { useState, useRef } from 'react';
import { Button } from '@/components/ui/button';
import { Textarea } from '@/components/ui/textarea';
import { Send, Loader2, Save } from 'lucide-react';

interface ChatComposerProps {
  onSendMessage: (message: string) => void;
  isLoading?: boolean;
  placeholder?: string;
  className?: string;
  // Suggested queries support
  suggestedQueries?: string[];
  onSuggestedQuerySelect?: (query: string) => void;
  showSuggested?: boolean;
  // Model test mode support - EXACT COPY from old implementation
  modelTestMode?: boolean;
  showSaveModel?: boolean;
  saveModelState?: 'success' | 'tool_issue' | 'error' | null;
  onSaveModel?: () => void;
}

export default function ChatComposer({
  onSendMessage,
  isLoading = false,
  placeholder = 'Chat with your prompt...',
  className = '',
  suggestedQueries = [],
  onSuggestedQuerySelect,
  showSuggested = false,
  // Model test mode props
  modelTestMode = false,
  showSaveModel = false,
  saveModelState = null,
  onSaveModel
}: ChatComposerProps) {
  const [inputValue, setInputValue] = useState('');

  // EXACT COPY: Auto-resize logic from old implementation
  const handleTextareaChange = (e: React.ChangeEvent<HTMLTextAreaElement>) => {
    const textarea = e.target;
    setInputValue(textarea.value);
    
    // Auto-resize functionality with minimum and maximum heights
    const minHeight = 96;
    const maxHeight = Math.round(window.innerHeight * 0.4);
    
    // Reset height to auto to get proper scrollHeight
    textarea.style.height = 'auto';
    
    // Set new height
    const newHeight = Math.max(Math.min(textarea.scrollHeight, maxHeight), minHeight);
    textarea.style.height = `${newHeight}px`;
  };

  // EXACT COPY: Submit handling from old implementation
  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (inputValue.trim() && !isLoading) {
      onSendMessage(inputValue.trim());
      setInputValue('');
    }
  };

  // EXACT COPY: Key press handling from old implementation
  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSubmit(e);
    }
  };

  // EXACT COPY: Suggested query handling from old implementation
  const handleSuggestedQuerySelect = (query: string) => {
    if (onSuggestedQuerySelect) {
      onSuggestedQuerySelect(query);
    } else {
      setInputValue(query);
    }
  };

  return (
    // EXACT COPY: Input Area structure from old implementation
    <div className="bg-background px-6 py-4">
      {/* EXACT COPY: Model Test Mode Save Button Area from old implementation */}
      {modelTestMode && showSaveModel ? (
        <div className="max-w-4xl mx-auto space-y-2">
          {saveModelState === 'success' && (
            <Button 
              onClick={onSaveModel}
              className="w-full h-12 bg-positive-trend hover:bg-positive-trend/90 text-white rounded-xl font-medium"
            >
              <Save className="h-4 w-4 mr-2" />
              Save Model
            </Button>
          )}
          
          {saveModelState === 'tool_issue' && (
            <>
              <p className="text-xs text-yellow-600 text-center">Model has problem with tool calling</p>
              <Button 
                onClick={onSaveModel}
                className="w-full h-12 bg-yellow-500 hover:bg-yellow-600 text-white rounded-xl font-medium"
              >
                <Save className="h-4 w-4 mr-2" />
                Save Model
              </Button>
            </>
          )}
          
          {saveModelState === 'error' && (
            <>
              <p className="text-xs text-red-600 text-center">Model connectivity test was not complete</p>
              <Button 
                onClick={onSaveModel}
                className="w-full h-12 bg-red-500 hover:bg-red-600 text-white rounded-xl font-medium"
              >
                <Save className="h-4 w-4 mr-2" />
                Save Model
              </Button>
            </>
          )}
        </div>
      ) : !modelTestMode ? (
        <div className="max-w-4xl mx-auto">
          {/* EXACT COPY: Suggested Queries from old implementation */}
          {suggestedQueries.length > 0 && showSuggested && (
            <div className="mb-4">
              <p className="text-xs text-muted-foreground mb-2">Suggested queries:</p>
              <div className="flex flex-wrap gap-2">
                {suggestedQueries.map((query, index) => (
                  <button
                    key={index}
                    onClick={() => handleSuggestedQuerySelect(query)}
                    className="px-3 py-1.5 text-xs bg-muted hover:bg-muted/80 border border-border rounded-lg text-foreground transition-colors duration-200 truncate max-w-xs"
                    title={query}
                  >
                    {query}
                  </button>
                ))}
              </div>
            </div>
          )}

          {/* EXACT COPY: Form from old implementation */}
          <form onSubmit={handleSubmit}>
            <div className="relative">
              <Textarea
                value={inputValue}
                onChange={handleTextareaChange}
                onKeyPress={handleKeyPress}
                placeholder={placeholder}
                className="chat-input-textarea w-full min-h-[96px] max-h-[40vh] resize-none rounded-xl border border-border bg-muted/50 px-4 py-4 pr-12 text-sm placeholder:text-muted-foreground focus:outline-none focus:border-positive-trend/60 focus:ring-0 focus:ring-offset-0 focus:shadow-none focus-visible:outline-none focus-visible:ring-0 focus-visible:ring-offset-0 focus-visible:border-positive-trend/60 transition-all duration-200"
                disabled={isLoading}
                rows={1}
                style={{ 
                  lineHeight: '1.5',
                  paddingTop: '18px',
                  paddingBottom: '18px',
                  boxShadow: 'none !important',
                  outline: 'none !important'
                }}
              />
              <div className="absolute bottom-3 right-3">
                <Button
                  type="submit"
                  disabled={!inputValue.trim() || isLoading}
                  className="h-8 w-8 p-0 bg-positive-trend hover:bg-positive-trend/90 text-white rounded-lg transition-all duration-200 disabled:opacity-50 disabled:cursor-not-allowed"
                  title={
                    !inputValue.trim()
                      ? "Type a message to send"
                      : "Send message"
                  }
                >
                  {isLoading ? (
                    <Loader2 className="h-3 w-3 animate-spin" />
                  ) : (
                    <Send className="h-3 w-3" />
                  )}
                </Button>
              </div>
            </div>
          </form>
          
        </div>
      ) : null}
    </div>
  );
}
