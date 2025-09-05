import { test, expect } from '@playwright/test';
import * as dotenv from 'dotenv';
dotenv.config();

test.beforeEach(async ({ context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write'], {
    origin: 'http://localhost:6645'
  });
});

test('test', async ({ page }) => {
    test.setTimeout(120_000);
    const openAiApiKey = process.env.OPENAI_API_KEY;
            if (!openAiApiKey) throw new Error('OPENAI_API_KEY not set');

        const agCUrl = process.env.AGC_UI_URL;
            if (!agCUrl) throw new Error('AGC_UI_URL not set');
  await page.goto(agCUrl);
  await page.getByRole('button', { name: 'Agents' }).click();
  await page.getByRole('button', { name: 'Ecommerce-Agent: This agent' }).click();
  await page.getByRole('button', { name: 'openai gpt-4o' }).click();
  await page.getByRole('button', { name: 'gpt-4.1-mini' }).click();
  await page.getByRole('textbox', { name: 'Enter openai API key (' }).dblclick();
  await page.getByRole('textbox', { name: 'Enter openai API key (' }).fill(openAiApiKey);
  await page.getByRole('button', { name: 'Save API Keys' }).click();
  await page.getByRole('button', { name: 'Find me sneakers of size 8 in' }).click();
  await page.getByRole('button', { name: 'openai gpt-4o' }).click();
  await page.getByRole('button', { name: 'gpt-4.1-mini' }).click();
  await page.getByRole('button', { name: 'Copy response ID' }).click();

      const copiedText = await page.evaluate(() => navigator.clipboard.readText());
      console.log(JSON.stringify({ responseId: copiedText }));
});
