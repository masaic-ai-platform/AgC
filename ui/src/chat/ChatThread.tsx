import React, { useEffect, useRef } from 'react';
import { Message, TextFormat } from './types';
import ChatMessage from '@/components/ChatMessage';

interface ChatThreadProps {
  messages: Message[];
  onRetry?: (messageId: string) => void;
  className?: string;
  textFormat?: TextFormat;
  apiKey?: string;
  baseUrl?: string;
  modelProvider?: string;
  modelName?: string;
  imageModelProvider?: string;
  imageModelName?: string;
  imageProviderKey?: string;
  selectedVectorStore?: string;
  instructions?: string;
}

export default function ChatThread({ 
  messages, 
  onRetry, 
  className = '',
  textFormat = 'text',
  apiKey = '',
  baseUrl = '',
  modelProvider = '',
  modelName = '',
  imageModelProvider = '',
  imageModelName = '',
  imageProviderKey = '',
  selectedVectorStore = '',
  instructions = ''
}: ChatThreadProps) {
  const messagesEndRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to bottom when messages change
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  return (
    <div className={`flex-1 overflow-y-auto px-6 py-8 ${className}`}>
      {messages.length === 0 ? (
        <div className="flex items-center justify-center h-full">
          <div className="text-center text-muted-foreground">
            <p className="text-sm">Start a conversation...</p>
          </div>
        </div>
      ) : (
        <div className="max-w-4xl mx-auto">
          {messages.map((message) => (
          <ChatMessage
            key={message.id}
            id={message.id}
            role={message.role}
            content={message.content}
            contentBlocks={message.contentBlocks}
            type={message.type}
            timestamp={message.timestamp}
            hasThinkTags={message.hasThinkTags}
            formatType={message.role === 'assistant' ? textFormat : 'text'}
            apiKey={apiKey}
            baseUrl={baseUrl}
            modelProvider={modelProvider}
            modelName={modelName}
            imageModelProvider={imageModelProvider}
            imageModelName={imageModelName}
            imageProviderKey={imageProviderKey}
            selectedVectorStore={selectedVectorStore}
            instructions={instructions}
            isLoading={message.isLoading}
            isStreaming={message.isStreaming}
            onRetry={onRetry}
          />
          ))}
        </div>
      )}
      <div ref={messagesEndRef} />
    </div>
  );
}
