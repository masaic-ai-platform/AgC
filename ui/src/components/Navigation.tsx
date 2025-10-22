import React from 'react';
import VersionBadge from './VersionBadge';
import MasaicBrand from './MasaicBrand';
import ThemeToggle from './ui/theme-toggle';
import { Button } from '@/components/ui/button';
import { Github, MessageCircle } from 'lucide-react';

const Navigation: React.FC = () => {

  return (
    <div className="bg-background border-b border-border">
      {/* Navigation Header - Redesigned with Geist UI */}
      <div className="px-8 py-1">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-4">
            <MasaicBrand />
          </div>
          
          {/* Social Links, Version Badge and Theme Toggle in top-right corner */}
          <div className="flex items-center space-x-2 md:space-x-3">
            <Button
              variant="outline"
              size="icon"
              className="h-9 w-9 border-2 border-border/60 text-foreground hover:text-foreground hover:bg-accent hover:border-border hover:scale-105 transition-all duration-200 shadow-sm"
              onClick={() => window.open('https://github.com/masaic-ai-platform/AgC', '_blank')}
              title="GitHub"
            >
              <Github className="h-5 w-5 stroke-[2.5]" />
            </Button>
            <Button
              variant="outline"
              size="icon"
              className="h-9 w-9 border-2 border-border/60 text-foreground hover:text-foreground hover:bg-accent hover:border-border hover:scale-105 transition-all duration-200 shadow-sm"
              onClick={() => window.open('https://discord.com/channels/1335132819260702723/1354795442004820068', '_blank')}
              title="Discord"
            >
              <MessageCircle className="h-5 w-5 stroke-[2.5]" />
            </Button>
            <div className="w-px h-6 bg-border hidden md:block"></div>
            <div className="hidden md:block">
              <VersionBadge />
            </div>
            <ThemeToggle />
          </div>
        </div>
      </div>

    </div>
  );
};

export default Navigation; 
