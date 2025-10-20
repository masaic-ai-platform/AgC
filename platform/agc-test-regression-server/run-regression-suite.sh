#!/bin/bash

# AgC Regression Suite Launcher
# This script launches:
# 1. AgC Regression Server (backend on port 6649)
# 2. UI Application (frontend on port 6650)

set -e

# Color codes for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# Store PIDs for cleanup
BACKEND_PID=""
UI_PID=""

# Cleanup function
cleanup() {
    echo -e "\n${YELLOW}Shutting down services...${NC}"
    
    if [ ! -z "$BACKEND_PID" ]; then
        echo -e "${BLUE}Stopping backend server (PID: $BACKEND_PID)...${NC}"
        kill -TERM $BACKEND_PID 2>/dev/null || true
        wait $BACKEND_PID 2>/dev/null || true
    fi
    
    if [ ! -z "$UI_PID" ]; then
        echo -e "${BLUE}Stopping UI application (PID: $UI_PID)...${NC}"
        kill -TERM $UI_PID 2>/dev/null || true
        wait $UI_PID 2>/dev/null || true
    fi
    
    echo -e "${GREEN}Cleanup complete${NC}"
    exit 0
}

# Trap signals for cleanup
trap cleanup SIGINT SIGTERM EXIT

# Get script directory and navigate to project root
SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
# Navigate to project root (from agc-test-regression-server: up 2 levels)
PROJECT_ROOT="$( cd "$SCRIPT_DIR/../.." && pwd )"
cd "$PROJECT_ROOT"

echo -e "${GREEN}=== AgC Regression Suite Launcher ===${NC}\n"

# Start Backend Server
echo -e "${BLUE}Starting AgC Regression Server on port 6649...${NC}"
cd platform
./gradlew :agc-test-regression-server:bootRun --console=plain &
BACKEND_PID=$!
cd "$PROJECT_ROOT"
echo -e "${GREEN}Backend server started (PID: $BACKEND_PID)${NC}\n"

# Wait for backend to be ready
echo -e "${BLUE}Waiting for backend server to be ready...${NC}"
MAX_ATTEMPTS=30
ATTEMPT=0
while [ $ATTEMPT -lt $MAX_ATTEMPTS ]; do
    if curl -s http://localhost:6649/actuator/health > /dev/null 2>&1; then
        echo -e "${GREEN}Backend server is ready!${NC}\n"
        break
    fi
    ATTEMPT=$((ATTEMPT + 1))
    if [ $ATTEMPT -eq $MAX_ATTEMPTS ]; then
        echo -e "${RED}Backend server failed to start within expected time${NC}"
        exit 1
    fi
    echo -e "${YELLOW}Waiting... ($ATTEMPT/$MAX_ATTEMPTS)${NC}"
    sleep 2
done

# Start UI Application
echo -e "${BLUE}Starting UI application on port 6650...${NC}"
cd "$PROJECT_ROOT/ui"
VITE_DASHBOARD_API_URL=http://localhost:6649 npm run dev -- --port 6650 --host &
UI_PID=$!
cd "$PROJECT_ROOT"
echo -e "${GREEN}UI application started (PID: $UI_PID)${NC}\n"

# Display access information
echo -e "${GREEN}=== Services Running ===${NC}"
echo -e "${BLUE}Backend:${NC}  http://localhost:6649"
echo -e "${BLUE}Frontend:${NC} http://localhost:6650"
echo -e "\n${YELLOW}Press Ctrl+C to stop all services${NC}\n"

# Wait for processes
wait $BACKEND_PID $UI_PID

