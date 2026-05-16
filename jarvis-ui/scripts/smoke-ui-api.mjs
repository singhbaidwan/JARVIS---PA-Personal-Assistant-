import { chromium } from "playwright-core";

const browser = await chromium.launch({
  headless: true,
  executablePath: "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
});

const page = await browser.newPage({ viewport: { width: 1440, height: 1050 }, deviceScaleFactor: 1 });
await page.goto("http://127.0.0.1:5173/", { waitUntil: "networkidle" });

await page.getByRole("button", { name: "Check" }).click();
await page.getByText("Core online", { exact: true }).waitFor({ timeout: 10000 });
await page.getByText("AI online", { exact: true }).waitFor({ timeout: 10000 });

await page.getByRole("button", { name: "Send" }).click();
await page.locator(".action-row").filter({ hasText: "SEARCH_FILES" }).first().waitFor({ timeout: 15000 });

await page.getByRole("button", { name: "Search" }).click();
await page.getByLabel("Smart Search").getByText("Indexed", { exact: false }).waitFor({ timeout: 15000 });

const result = await page.evaluate(() => ({
  coreOnline: document.body.textContent?.includes("Core online") ?? false,
  aiOnline: document.body.textContent?.includes("AI online") ?? false,
  suggestedAction: document.body.textContent?.includes("SEARCH_FILES") ?? false,
  indexed: document.body.textContent?.includes("Indexed") ?? false,
}));

await browser.close();
console.log(JSON.stringify(result, null, 2));
