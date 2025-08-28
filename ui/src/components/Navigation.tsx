import React from 'react';
import VersionBadge from './VersionBadge';
import MasaicBrand from './MasaicBrand';
import ThemeToggle from './ui/theme-toggle';

const Navigation: React.FC = () => {

  return (
    <div className="bg-background border-b border-border">
      {/* Navigation Header - Redesigned with Geist UI */}
      <div className="px-8 py-1">
        <div className="flex items-center justify-between">
          <div className="flex items-center space-x-4">
            <MasaicBrand />
          </div>
          
          {/* Version Badge and Theme Toggle in top-right corner */}
          <div className="flex items-center space-x-4">
            <VersionBadge />
            <ThemeToggle />
          </div>
        </div>
      </div>

    </div>
  );
};

export default Navigation; 