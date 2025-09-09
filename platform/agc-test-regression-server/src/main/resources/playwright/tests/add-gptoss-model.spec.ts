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
  const groqApiKey = process.env.GROQ_API_KEY;
      if (!groqApiKey) throw new Error('GROQ_API_KEY not set');

      const agCUrl = process.env.AGC_UI_URL;
          if (!agCUrl) throw new Error('AGC_UI_URL not set');

      await page.goto(agCUrl);
      await page.getByRole('button', { name: 'Add Model' }).click();
      await page.getByRole('combobox', { name: 'https://api.example.com/v1' }).dblclick();
      await page.getByRole('combobox', { name: 'https://api.example.com/v1' }).fill('https://api.groq.com/openai/v1');
      await page.getByRole('textbox', { name: 'gpt-4o' }).click();
      await page.getByRole('textbox', { name: 'gpt-4o' }).fill('openai/gpt-oss-120b');
      await page.getByRole('textbox', { name: 'sk-' }).dblclick();
      await page.getByRole('textbox', { name: 'sk-' }).fill(groqApiKey);
      await page.getByRole('button', { name: 'Test Model Connectivity' }).click();
      await page.getByRole('button', { name: 'Save Model' }).click();
      await page.getByRole('button', { name: 'AgC API' }).click();
      await page.getByRole('button', { name: 'openai gpt-4o' }).click();
      await page.getByRole('button', { name: 'openai/gpt-oss-120b' }).click();
      await page.getByRole('textbox', { name: 'Chat with your prompt...' }).click();
      await page.getByRole('textbox', { name: 'Chat with your prompt...' }).fill('Hi, can you greet me good morning?');
      await page.getByRole('button', { name: 'Send message' }).click();
      await page.getByRole('button', { name: 'Copy response ID' }).click();

    const copiedText = await page.evaluate(() => navigator.clipboard.readText());
    console.log(JSON.stringify({ responseId: copiedText }));
});
