import React, { useState, useEffect } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '@/components/ui/dialog';
import { Button } from '@/components/ui/button';
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from '@/components/ui/select';
import { Copy, Check, Code } from 'lucide-react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { atomDark, oneLight } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { PlaygroundRequest } from './PlaygroundRequest';
import { generateSnippets, type CodeSnippets } from './snippets/generators';

interface CodeTabsProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  lastRequest: PlaygroundRequest | null;
  baseUrl: string;
}



const CodeTabs: React.FC<CodeTabsProps> = ({
  open,
  onOpenChange,
  lastRequest,
  baseUrl
}) => {
  const [activeTab, setActiveTab] = useState<'curl' | 'python' | 'node'>('curl');
  const [snippets, setSnippets] = useState<CodeSnippets>({
    curl: '',
    python: '',
    node: ''
  });
  const [copiedStates, setCopiedStates] = useState<Record<string, boolean>>({
    curl: false,
    python: false,
    node: false
  });

  // Generate snippets when modal opens or request changes
  useEffect(() => {
    if (open && lastRequest) {
      try {
        const generatedSnippets = generateSnippets(lastRequest, baseUrl);
        setSnippets(generatedSnippets);
      } catch (error) {
        console.error('Error generating code snippets:', error);
        setSnippets({
          curl: 'Error generating cURL snippet',
          python: 'Error generating Python snippet',
          node: 'Error generating Node.js snippet'
        });
      }
    }
  }, [open, lastRequest, baseUrl]);

  const copyToClipboard = async (text: string, type: 'curl' | 'python' | 'node') => {
    try {
      await navigator.clipboard.writeText(text);
      setCopiedStates(prev => ({ ...prev, [type]: true }));
      setTimeout(() => {
        setCopiedStates(prev => ({ ...prev, [type]: false }));
      }, 2000);
    } catch (err) {
      console.error('Failed to copy:', err);
    }
  };

  const getLanguageForSyntaxHighlighter = (tab: string) => {
    switch (tab) {
      case 'curl':
        return 'bash';
      case 'python':
        return 'python';
      case 'node':
        return 'javascript';
      default:
        return 'text';
    }
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

  // Detect current theme
  const [isDarkMode, setIsDarkMode] = useState(false);

  useEffect(() => {
    const checkTheme = () => {
      const isDark = document.documentElement.classList.contains('dark');
      setIsDarkMode(isDark);
    };

    checkTheme();

    // Listen for theme changes
    const observer = new MutationObserver(checkTheme);
    observer.observe(document.documentElement, {
      attributes: true,
      attributeFilter: ['class']
    });

    return () => observer.disconnect();
  }, []);

  // Light mode syntax highlighting theme
  const lightSyntaxStyle = {
    'comment': { color: '#6b7280' },
    'string': { color: '#059669' }, // darker green for better contrast
    'number': { color: '#1f2937' }, // dark gray instead of white
    'boolean': { color: '#059669' },
    'keyword': { color: '#dc2626' }, // red for keywords
    'function': { color: '#1f2937' },
    'operator': { color: '#374151' },
    'punctuation': { color: '#6b7280' },
    'property': { color: '#1f2937' },
    'builtin': { color: '#059669' },
    'class-name': { color: '#1f2937' },
    'constant': { color: '#059669' },
    'symbol': { color: '#059669' },
    'deleted': { color: '#dc2626' },
    'inserted': { color: '#059669' },
    'entity': { color: '#1f2937' },
    'url': { color: '#059669' },
    'variable': { color: '#1f2937' },
    'atrule': { color: '#059669' },
    'attr-value': { color: '#059669' },
    'attr-name': { color: '#1f2937' },
    'tag': { color: '#dc2626' },
    'prolog': { color: '#6b7280' },
    'doctype': { color: '#6b7280' },
    'cdata': { color: '#6b7280' },
    'namespace': { color: '#1f2937' },
    'selector': { color: '#dc2626' },
    'important': { color: '#dc2626' },
    'bold': { fontWeight: 'bold' },
    'italic': { fontStyle: 'italic' }
  };

  // Dark mode syntax highlighting theme (original)
  const darkSyntaxStyle = {
    'comment': { color: '#6b7280' },
    'string': { color: '#10b981' },
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

  const currentSyntaxStyle = isDarkMode ? darkSyntaxStyle : lightSyntaxStyle;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-5xl w-[95vw] h-[90vh] flex flex-col bg-background border border-border shadow-xl p-0">
        {/* Header */}
        <DialogHeader className="shrink-0 px-6 py-3 border-b border-border">
          <div className="flex items-center space-x-3">
            <div className="w-8 h-8 bg-primary/10 rounded-md flex items-center justify-center">
              <Code className="h-4 w-4 text-primary" />
            </div>
            <DialogTitle className="text-lg font-semibold text-foreground">Code Snippets</DialogTitle>
          </div>
        </DialogHeader>

        {/* Code content with IDE styling */}
        <div className="flex-1 overflow-hidden px-6 pb-6 pt-2">
          <div className="h-full bg-card border border-border rounded-lg flex flex-col overflow-hidden shadow-sm">
            {/* IDE header with language selector and copy */}
            <div className="shrink-0 flex items-center justify-between px-4 py-2 bg-muted/50 border-b border-border">
              <Select value={activeTab} onValueChange={(value) => setActiveTab(value as 'curl' | 'python' | 'node')}>
                <SelectTrigger className="w-48 h-7 text-xs bg-background border-border hover:bg-accent focus:ring-1 focus:ring-positive-trend">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent className="bg-background border-border">
                  <SelectItem value="curl" className="text-xs hover:bg-accent focus:bg-positive-trend/10">cURL</SelectItem>
                  <SelectItem value="python" className="text-xs hover:bg-accent focus:bg-positive-trend/10">Python (OpenAI SDK)</SelectItem>
                  <SelectItem value="node" className="text-xs hover:bg-accent focus:bg-positive-trend/10">Node.js (OpenAI SDK)</SelectItem>
                </SelectContent>
              </Select>
              
              <Button
                variant="ghost"
                size="sm"
                onClick={() => copyToClipboard(snippets[activeTab], activeTab)}
                className="h-7 px-2 text-xs text-muted-foreground hover:text-foreground hover:bg-accent focus:bg-positive-trend/10 focus:text-positive-trend"
              >
                {copiedStates[activeTab] ? (
                  <>
                    <Check className="h-3 w-3 mr-1.5 text-positive-trend" />
                    Copied
                  </>
                ) : (
                  <>
                    <Copy className="h-3 w-3 mr-1.5" />
                    Copy
                  </>
                )}
              </Button>
            </div>
            
            {/* Code area */}
            <div className={`flex-1 overflow-auto ${isDarkMode ? 'bg-gray-900' : 'bg-gray-50'}`}>
              <SyntaxHighlighter
                language={getLanguageForSyntaxHighlighter(activeTab)}
                style={currentSyntaxStyle}
                customStyle={{
                  ...customStyle,
                  background: 'transparent'
                }}
                showLineNumbers={true}
                wrapLines={false}
                wrapLongLines={false}
                PreTag="div"
              >
                {snippets[activeTab]}
              </SyntaxHighlighter>
            </div>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
};

export default CodeTabs; 