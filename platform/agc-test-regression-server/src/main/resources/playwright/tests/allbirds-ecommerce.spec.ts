import { test, expect } from '@playwright/test';
import * as dotenv from 'dotenv';
dotenv.config();

test.beforeEach(async ({ context }) => {
  await context.grantPermissions(['clipboard-read', 'clipboard-write'], {
    origin: 'http://localhost:6645'
  });
});

test('test', async ({ page }) => {
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
      await page.getByRole('textbox', { name: 'Describe desired model' }).click();
      await page.getByRole('textbox', { name: 'Describe desired model' }).fill('You are an ecommerce assistant who can help in product selection, add to cart, provide checkout link.\n1. Use all birds tool for product search and update cart.\n2. return image url in a proper markdown format.\n3. whenever you update cart, share checkout link also.');
      await page.getByRole('button', { name: '+' }).click();
      await page.getByRole('button', { name: 'ModelContextProtocol MCP' }).click();
      await page.getByRole('textbox', { name: 'URL' }).fill('https://allbirds.com/api/mcp');
      await page.getByRole('textbox', { name: 'Label' }).click();
      await page.getByRole('textbox', { name: 'Label' }).fill('allbrids');
      await page.getByRole('combobox').click();
      await page.getByRole('option', { name: 'None' }).click();
      await page.getByRole('button', { name: '⚡ Connect' }).click();
      await page.getByRole('button', { name: 'Add' }).click();
      await page.getByRole('textbox', { name: 'Chat with your prompt...' }).click();
      await page.getByRole('textbox', { name: 'Chat with your prompt...' }).fill('Add men\'s black runner jet black, black sole of size 9 in the cart.');
      await page.getByRole('button', { name: 'Send message' }).click();
      await page.getByRole('button', { name: 'Copy response ID' }).click();

      const copiedText = await page.evaluate(() => navigator.clipboard.readText());
      console.log(JSON.stringify({ responseId: copiedText }));
});
