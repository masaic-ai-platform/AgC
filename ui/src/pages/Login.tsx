import React, { useEffect } from 'react';
import { useAuth } from '@/contexts/AuthContext';
import { GoogleLogin as GoogleLoginButton } from '@react-oauth/google';
import { useNavigate, useLocation } from 'react-router-dom';
import { Button } from '@/components/ui/button';
import { Github, MessageCircle } from 'lucide-react';

const Login: React.FC = () => {
  const { login, isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  
  // Get the path they were trying to access and API error state
  const from = (location.state as any)?.from || '/';
  const apiError = (location.state as any)?.apiError || false;

  // If already authenticated, redirect to intended destination
  useEffect(() => {
    if (isAuthenticated) {
      navigate(from, { replace: true });
    }
  }, [isAuthenticated, navigate, from]);

  const handleGoogleSuccess = async (credentialResponse: any) => {
    try {
      if (credentialResponse.credential) {
        await login(credentialResponse.credential);
        // Navigation will happen via useEffect when isAuthenticated changes
      } else {
        throw new Error('No credential received from Google');
      }
    } catch (error) {
      console.error('Google login failed:', error);
    }
  };

  const handleGoogleError = () => {
    console.error('Google login was cancelled or failed');
  };

  return (
    <div className="min-h-screen flex bg-black lg:bg-transparent relative">
      {/* Social Links - Top Right (Absolute Position) */}
      <div className="absolute top-6 right-6 z-20 flex gap-3">
        <Button
          variant="outline"
          size="icon"
          className="h-12 w-12 bg-white/5 border-2 border-white/20 text-white hover:bg-white/10 hover:border-white/40 hover:scale-110 transition-all duration-200 shadow-lg backdrop-blur-sm"
          onClick={() => window.open('https://github.com/masaic-ai-platform/AgC', '_blank')}
          title="GitHub"
        >
          <Github className="h-7 w-7 stroke-[2.5]" />
        </Button>
        <Button
          variant="outline"
          size="icon"
          className="h-12 w-12 bg-white/5 border-2 border-white/20 text-white hover:bg-white/10 hover:border-white/40 hover:scale-110 transition-all duration-200 shadow-lg backdrop-blur-sm"
          onClick={() => window.open('https://discord.com/channels/1335132819260702723/1354795442004820068', '_blank')}
          title="Discord"
        >
          <MessageCircle className="h-7 w-7 stroke-[2.5]" />
        </Button>
      </div>

      {/* Left Side - Gradient Background with Text (60% width) - Desktop Only */}
      <div className="hidden lg:flex lg:w-[60%] relative items-end justify-start p-16 pb-20 overflow-hidden bg-gradient-to-br from-gray-900 via-gray-800 to-black">
        <div className="relative z-10 max-w-2xl">
          <h1 className="text-5xl lg:text-6xl font-bold text-white leading-tight">
            The Platform
            <br />
            Layer for
            <br />
            Agentic Systems
          </h1>
          <p className="text-lg lg:text-xl text-white/90 leading-relaxed mt-6">
            Empowering engineers to build their own enterprise platforms. Built By Engineers.
          </p>
        </div>
      </div>

      {/* Right Side - Login Form (40% width on desktop, full on mobile) */}
      <div className="w-full lg:w-[40%] flex items-center justify-center bg-black lg:bg-background p-8">
        <div className="w-full max-w-md">
          {/* API Error Alert */}
          {apiError && (
            <div className="mb-6 bg-red-50 dark:bg-red-950/20 border border-red-200 dark:border-red-800 rounded-md p-4">
              <div className="flex items-center">
                <div className="w-2 h-2 bg-red-500 rounded-full mr-2"></div>
                <h3 className="text-sm font-medium text-red-800 dark:text-red-200">
                  API Connection Error
                </h3>
              </div>
              <div className="mt-2 text-sm text-red-700 dark:text-red-300">
                Cannot connect to API server.
                <br />
                Please check your API server connection and try again.
              </div>
            </div>
          )}

          {/* Logo and Title */}
          <div className="flex items-center justify-center mb-12">
            <img 
              src="/Masaic-M-Logo.png" 
              alt="AgC Logo" 
              className="h-12 w-12 mr-3"
            />
            <h2 className="text-3xl font-bold text-white lg:text-foreground">AgC</h2>
          </div>

          {/* Welcome Title */}
          <h3 className="text-2xl font-semibold text-center mb-3 text-white lg:text-foreground">Welcome back</h3>
          
          {/* Subtitle */}
          <p className="text-center text-gray-400 lg:text-muted-foreground mb-8">
            Sign in to your account to continue your journey
            <br />
            with AgC
          </p>

          {/* Google Login Button */}
          <div className="flex justify-center mb-8">
            <div className="relative inline-block">
              <GoogleLoginButton
                onSuccess={handleGoogleSuccess}
                onError={handleGoogleError}
                useOneTap={false}
                theme="filled_blue"
                size="large"
                text="signin_with"
                shape="rectangular"
              />
            </div>
          </div>
        </div>
      </div>
    </div>
  );
};

export default Login;

