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
  await page.getByRole('button', { name: 'openai gpt-4o' }).click();
  await page.getByRole('button', { name: 'gpt-4.1-mini' }).click();
  await page.getByRole('textbox', { name: 'Enter openai API key (' }).dblclick();
  await page.getByRole('textbox', { name: 'Enter openai API key (' }).fill(openAiApiKey);
  await page.getByRole('button', { name: 'Save API Keys' }).click();
  await page.getByRole('button', { name: 'openai gpt-4o' }).click();
  await page.getByRole('button', { name: 'gpt-4.1-mini' }).click();
  await page.getByRole('textbox', { name: 'Describe desired model' }).dblclick();
  await page.getByRole('textbox', { name: 'Describe desired model' }).fill('1. Use file search tool to frame your answers.\n2.Give answer in bullet points which are easy to read.\n3. In your response always provide citation to indicate the source of answer');
  await page.getByRole('textbox', { name: 'Chat with your prompt...' }).dblclick();
  await page.getByRole('textbox', { name: 'Chat with your prompt...' }).fill('What are balanced chemical equations. Describe in 100 words with two examples of Iron');
  await page.getByRole('button', { name: '+' }).click();
  await page.getByRole('button', { name: 'File Search', exact: true }).click();
  await page.getByText('regression-test-store').click();
  await page.getByRole('combobox').filter({ hasText: 'Select embedding model...' }).click();
  await page.getByText('text-embedding-3-small').click();
  await page.getByRole('button', { name: 'Save Configuration' }).click();
  await page.getByRole('button', { name: 'Send message' }).click();
  await page.getByRole('button', { name: 'Copy response ID' }).click();

    const copiedText = await page.evaluate(() => navigator.clipboard.readText());
    console.log(JSON.stringify({ responseId: copiedText }));
});
