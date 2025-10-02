import { test, expect } from '@playwright/test';

import * as dotenv from 'dotenv';
dotenv.config();

test.beforeEach(async ({ context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write'], {
    origin: 'http://localhost:6645'
  });
});

test('test', async ({ page }) => {
    test.setTimeout(300_000);
        const openAiApiKey = process.env.OPENAI_API_KEY;
            if (!openAiApiKey) throw new Error('OPENAI_API_KEY not set');

        const agCUrl = process.env.AGC_UI_URL;
            if (!agCUrl) throw new Error('AGC_UI_URL not set');

  await page.goto(agCUrl);
  await page.getByRole('button', { name: '+' }).click();
  await page.getByRole('button', { name: 'ModelContextProtocol MCP' }).click();
  await page.getByRole('textbox', { name: 'URL' }).fill('https://mcp.notion.com/mcp');
  await page.getByRole('textbox', { name: 'Label' }).click();
  await page.getByRole('textbox', { name: 'Label' }).fill('notion-test');
  await page.getByRole('combobox').click();
  await page.getByRole('option', { name: 'OAuth' }).click();
  await page.getByRole('button', { name: '⚡ Connect' }).click();
  await page.getByRole('button', { name: 'Authenticate with mcp.notion.' }).click();
  await page.getByRole('textbox', { name: 'Enter your email address...' }).fill('jb@masaic.ai');
  await page.getByRole('button', { name: 'Continue', exact: true }).click();
  await page.getByRole('textbox', { name: 'Enter code' }).dblclick();
//   await page.getByRole('textbox', { name: 'Enter code' }).fill('fzoWMq');
  await page.getByRole('button', { name: 'Continue', exact: true }).click();
  await page.locator('div').filter({ hasText: /^I recognize and trust this URL$/ }).locator('div').first().click();
  await page.getByRole('button', { name: 'Continue' }).click();
  await page.locator('div').filter({ hasText: /^Tools0 selected$/ }).getByRole('checkbox').click();
  await page.getByRole('button', { name: 'Update', exact: true }).click();
  await page.getByRole('button', { name: 'openai gpt-4o' }).click();
  await page.getByRole('button', { name: 'gpt-4.1-mini' }).click();
  await page.getByRole('textbox', { name: 'Enter openai API key (' }).dblclick();
  await page.getByRole('textbox', { name: 'Enter openai API key (' }).fill(openAiApiKey);
  await page.getByRole('button', { name: 'Save API Keys' }).click();
  await page.getByRole('button', { name: 'openai gpt-4o' }).click();
  await page.getByRole('button', { name: 'gpt-4.1-mini' }).click();
  await page.getByRole('textbox', { name: 'Describe desired model' }).click();
  await page.getByRole('textbox', { name: 'Describe desired model' }).fill('Use the notion tools to answer every question.');
  await page.getByRole('textbox', { name: 'Chat with your prompt...' }).dblclick();
//   await page.getByRole('textbox', { name: 'Chat with your prompt...' }).fill('Who all are authors of AgC pages');
//   await page.getByText('User11:56Who all are authors of AgC pagesAssistant11:').click();
//   await page.getByRole('textbox', { name: 'Chat with your prompt...' }).click();
  await page.getByRole('textbox', { name: 'Chat with your prompt...' }).fill('What all pages about AgC you have... give title of each page');
  await page.getByRole('button', { name: 'Send message' }).click();
    await page.getByRole('button', { name: 'Copy response ID' }).click();

      const copiedText = await page.evaluate(() => navigator.clipboard.readText());
      console.log(JSON.stringify({ responseId: copiedText }));
});
