import React, { useState, useEffect } from 'react';
import { Sparkles } from 'lucide-react';

// Add CSS for infinite scroll animation
const styles = `
  @keyframes infiniteScroll {
    0% {
      transform: translateY(0);
    }
    100% {
      transform: translateY(-160px); /* 5 messages * 32px each */
    }
  }
`;

interface AgentBuilderRollingMessagesProps {
  isRolling?: boolean;
  className?: string;
}

const messages = [
  "Build agents with AgC",
  "In built prompt designer", 
  "In built tool selector",
  "Agents with in built memory",
  "Access agent with API"
];

const AgentBuilderRollingMessages: React.FC<AgentBuilderRollingMessagesProps> = ({ 
  isRolling = false,
  className = ""
}) => {
  const [currentMessageIndex, setCurrentMessageIndex] = useState(0);

  useEffect(() => {
    if (!isRolling) {
      return;
    }

    const interval = setInterval(() => {
      setCurrentMessageIndex((prev) => (prev + 1) % messages.length);
    }, 2000); // Change message every 2 seconds

    return () => clearInterval(interval);
  }, [isRolling]);

  // Create duplicated messages for seamless infinite scrolling
  const duplicatedMessages = [...messages, ...messages];

  return (
    <>
      {/* Inject CSS styles */}
      <style dangerouslySetInnerHTML={{ __html: styles }} />
      
      <div className={`flex items-center justify-center h-full ${className}`}>
        <div className="text-center max-w-md">
          <Sparkles className={`h-16 w-16 mx-auto mb-6 ${
            isRolling 
              ? 'text-green-500 animate-pulse' 
              : 'text-muted-foreground'
          }`} />
          
          {/* Vertical rolling container */}
          <div className="h-8 mb-4 overflow-hidden relative">
            {isRolling ? (
              // Continuous rolling animation
              <div 
                style={{
                  animation: 'infiniteScroll 10s linear infinite'
                }}
              >
                {duplicatedMessages.map((message, index) => (
                  <div 
                    key={index}
                    className="h-8 flex items-center justify-center"
                  >
                    <h3 className="text-lg font-medium text-foreground">
                      {message}
                    </h3>
                  </div>
                ))}
              </div>
            ) : (
              // Static display
              <div className="h-8 flex items-center justify-center">
                <h3 className="text-lg font-medium text-green-600 dark:text-green-400">
                  {messages[0]}
                </h3>
              </div>
            )}
          </div>
        </div>
      </div>
    </>
  );
};

export default AgentBuilderRollingMessages;
