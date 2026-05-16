import { chromium } from "playwright-core";
import { mkdir } from "node:fs/promises";
import { resolve } from "node:path";

const chromePath = "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome";
const outputDir = resolve("artifacts");
await mkdir(outputDir, { recursive: true });

const browser = await chromium.launch({
  headless: true,
  executablePath: chromePath,
});

async function inspectViewport(name, viewport) {
  const page = await browser.newPage({ viewport, deviceScaleFactor: name === "mobile" ? 2 : 1 });
  await page.goto("http://127.0.0.1:5173/", { waitUntil: "networkidle" });
  await page.waitForSelector("canvas", { timeout: 10000 });
  const screenshotPath = resolve(outputDir, `${name}.png`);
  await page.screenshot({ path: screenshotPath, fullPage: true });
  const info = await page.evaluate(() => {
    const canvas = document.querySelector("canvas");
    let nonBlankPixels = 0;
    if (canvas) {
      const sample = document.createElement("canvas");
      sample.width = 80;
      sample.height = 80;
      const ctx = sample.getContext("2d");
      if (ctx) {
        ctx.drawImage(canvas, 0, 0, 80, 80);
        const data = ctx.getImageData(0, 0, 80, 80).data;
        for (let i = 0; i < data.length; i += 4) {
          if (data[i] || data[i + 1] || data[i + 2] || data[i + 3]) {
            nonBlankPixels += 1;
          }
        }
      }
    }

    const rects = [...document.querySelectorAll(".panel, .avatar-stage")].map((element) => {
      const rect = element.getBoundingClientRect();
      return { left: rect.left, top: rect.top, right: rect.right, bottom: rect.bottom };
    });
    const overlaps = [];
    for (let i = 0; i < rects.length; i += 1) {
      for (let j = i + 1; j < rects.length; j += 1) {
        const a = rects[i];
        const b = rects[j];
        const overlap =
          Math.max(0, Math.min(a.right, b.right) - Math.max(a.left, b.left)) *
          Math.max(0, Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top));
        if (overlap > 2) {
          overlaps.push([i, j, overlap]);
        }
      }
    }

    return {
      title: document.title,
      h1: document.querySelector("h1")?.textContent,
      canvasCount: document.querySelectorAll("canvas").length,
      nonBlankPixels,
      panelCount: rects.length,
      overlaps,
      bodyWidth: document.body.scrollWidth,
      viewportWidth: window.innerWidth,
      buttonCount: document.querySelectorAll("button").length,
    };
  });
  await page.close();
  return { screenshotPath, info };
}

const desktop = await inspectViewport("desktop", { width: 1440, height: 1050 });
const mobile = await inspectViewport("mobile", { width: 390, height: 900 });
await browser.close();

console.log(JSON.stringify({ desktop, mobile }, null, 2));
