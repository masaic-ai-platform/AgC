import { Toaster } from "@/components/ui/toaster";
import { Toaster as Sonner } from "@/components/ui/sonner";
import { TooltipProvider } from "@/components/ui/tooltip";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { BrowserRouter, Routes, Route } from "react-router-dom";
import { PlatformProvider } from "./contexts/PlatformContext";
import { AuthProvider } from "./contexts/AuthContext";
import { GoogleOAuthProvider } from "@react-oauth/google";
import ProtectedRoute from "./components/ProtectedRoute";
import Layout from "./components/Layout";
import Chat from "./pages/Chat";
import AgentBuilder from "./pages/AgentBuilder";
import NotFound from "./pages/NotFound";
import Login from "./pages/Login";
import { AUTH_CONFIG } from "./config/auth";

const queryClient = new QueryClient();

const App = () => (
  <QueryClientProvider client={queryClient}>
    <GoogleOAuthProvider clientId={AUTH_CONFIG.googleClientId}>
      <AuthProvider>
        <PlatformProvider>
          <TooltipProvider>
            <Toaster />
            <Sonner />
            <BrowserRouter>
              <Routes>
                {/* Public Routes */}
                <Route path="/login" element={<Login />} />
                
                {/* Protected Routes */}
                <Route path="/*" element={
                  <Layout>
                    <ProtectedRoute>
                      <Routes>
                        <Route path="/" element={<Chat />} />
                        <Route path="/agent-builder" element={<AgentBuilder />} />
                        {/* ADD ALL CUSTOM ROUTES ABOVE THE CATCH-ALL "*" ROUTE */}
                        <Route path="*" element={<NotFound />} />
                      </Routes>
                    </ProtectedRoute>
                  </Layout>
                } />
              </Routes>
            </BrowserRouter>
          </TooltipProvider>
        </PlatformProvider>
      </AuthProvider>
    </GoogleOAuthProvider>
  </QueryClientProvider>
);

export default App;
