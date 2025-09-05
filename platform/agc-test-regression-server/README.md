# AgC Regression Server

## Overview

The AgC Regression Server is a Spring Boot application that provides automated testing capabilities for the AgC platform. This server can run Playwright scripts and provides essential tools to generate comprehensive testing trails, enabling efficient regression testing and validation of platform functionality.

## Playwright Installation

Navigate to the Playwright directory and install the required dependencies:

```bash
cd platform/agc-test-regression-server/src/main/resources/playwright
npm init -y
npm i -D @playwright/test
npm install dotenv --save-dev
npx playwright install
```

## Playwright Script Setup

1. Generate your Playwright test scripts
2. Add the generated scripts to the tests directory:
   ```
   platform/agc-test-regression-server/src/main/resources/playwright/tests
   ```

## Recording and playing Playwright script
1. The following command would create script allbirds-ecommerce.spec.ts inside tests folder from UI hosted at http://localhost:6645.
```bash
npx playwright codegen http://localhost:6645 --output tests/allbirds-ecommerce.spec.ts
```
2. Run the recorded script with below command, (if want to run standalone)
```bash
npx playwright test tests/allbirds-ecommerce.spec.ts --headed
```

## Configuration

Before running the AgC regression Spring Boot server, configure the environment:

1. Copy the environment example file:
   ```bash
   cp platform/agc-test-regression-server/src/main/resources/playwright/env.example platform/agc-test-regression-server/src/main/resources/playwright/.env
   ```

2. Update the `.env` file with your specific configuration values

## Running the AgC Regression Server

Start the AgC regression server with the required Spring profiles:

```bash
java -jar target/agc-test-regression-server.jar --spring.profiles.active=platform,regression
```

Or if running from IDE, set the active profiles:
```
spring.profiles.active=platform,regression
```

The server will be available and ready to execute Playwright test scripts for regression testing.
