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
  await page.getByRole('button', { name: 'Mocky' }).click();
  await page.getByRole('button', { name: 'openai gpt-4o' }).click();
  await page.getByRole('button', { name: 'gpt-4.1-mini' }).click();
  await page.getByRole('textbox', { name: 'Enter openai API key (' }).dblclick();
  await page.getByRole('textbox', { name: 'Enter openai API key (' }).fill(openAiApiKey);
  await page.getByRole('button', { name: 'Save API Keys' }).click();
  await page.getByRole('button', { name: 'openai gpt-4o' }).click();
  await page.getByRole('button', { name: 'gpt-4.1', exact: true }).click();
  await page.getByRole('textbox', { name: 'Chat with your prompt...' }).fill('Create a function to add two numbers. Function can take two numbers as input and return result as a sum of both numbers. \nCreate a mock with input values= 5 and 7, return result= 12\nAssume rest of the need inputs yourself, do not ask for approval and give me function + mock. I need only 1 mock for this function.');
  await page.getByRole('button', { name: 'Send message' }).click();
  await page.getByRole('button', { name: 'Copy response ID' }).click();
  await page.getByRole('button', { name: 'Refresh functions' }).click();
  await page.locator('div').filter({ hasText: /^Mock Servers$/ }).getByRole('button').click();
  await page.getByRole('textbox', { name: 'my-mock-server' }).click();
  await page.getByRole('textbox', { name: 'my-mock-server' }).fill('maths-server');
  await page.getByText('View FunctionView Mocks').nth(2).click();
  await page.getByRole('button', { name: 'Close' }).click();
  await page.getByLabel('Add Mock Server').getByText('add_two_numbersAdds two').click();
  await page.getByRole('button', { name: 'Create' }).click();
  await page.getByRole('button', { name: 'AgC API' }).click();
  await page.getByRole('button', { name: '+' }).click();
  await page.getByRole('button', { name: 'ModelContextProtocol MCP' }).click();
  await page.getByRole('textbox', { name: 'URL' }).fill('ma');
  await page.getByRole('button', { name: 'maths-server https://' }).click();
  await page.getByRole('button', { name: '⚡ Connect' }).click();
  await page.getByRole('button', { name: 'Add', exact: true }).click();
  await page.getByRole('textbox', { name: 'Chat with your prompt...' }).dblclick();
  await page.getByRole('textbox', { name: 'Chat with your prompt...' }).fill('Use the given tool to add two number.\nAdd 7 and 5');
  await page.getByRole('button', { name: 'Send message' }).click();
  await page.getByRole('button', { name: 'Copy response ID' }).click();

      const copiedText = await page.evaluate(() => navigator.clipboard.readText());
      console.log(JSON.stringify({ responseId: copiedText }));
});
