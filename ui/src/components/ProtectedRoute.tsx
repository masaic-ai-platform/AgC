import React, { useEffect } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { useNavigate, useLocation } from 'react-router-dom';
import { Loader2 } from 'lucide-react';

interface ProtectedRouteProps {
  children: React.ReactNode;
}

const ProtectedRoute: React.FC<ProtectedRouteProps> = ({ children }) => {
  const { isAuthenticated, isLoading, authEnabled, apiError, tokenExpired } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  useEffect(() => {
    // If auth is enabled and user is not authenticated, redirect to login
    if (authEnabled && !isLoading && (!isAuthenticated || tokenExpired)) {
      navigate('/login', { state: { from: location.pathname, apiError } });
    }
  }, [authEnabled, isAuthenticated, isLoading, tokenExpired, navigate, location.pathname, apiError]);

  // If auth is disabled, render children directly
  if (!authEnabled) {
    return <>{children}</>;
  }

  // If loading, show spinner
  if (isLoading) {
    return (
      <div className="h-full w-full flex items-center justify-center">
        <div className="flex items-center space-x-2">
          <Loader2 className="h-6 w-6 animate-spin" />
          <span>Loading...</span>
        </div>
      </div>
    );
  }

  // If not authenticated or token expired, return null (redirect happens in useEffect)
  if (!isAuthenticated || tokenExpired) {
    return null;
  }

  // If authenticated, render children
  return <>{children}</>;
};

export default ProtectedRoute; 