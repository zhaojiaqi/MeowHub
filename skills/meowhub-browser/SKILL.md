---
name: meowhub-browser
description: "Browse the web using Browserless.io cloud browser service. Use this skill when you need to fetch web page content, take website screenshots, or scrape structured data from websites. This is a cloud-based browser that works in the Android/Termux environment where local Chrome is not available."
metadata:
  openclaw:
    always: true
    emoji: "=^._.^="
    requires:
      bins: ["curl"]
---

# MeowHub Cloud Browser (Browserless.io)

You have access to a cloud-based headless browser through Browserless.io. Use this when OpenClaw's built-in `browser` tool fails (which happens in Android/Termux environments without local Chrome).

## When to Use This Skill

- OpenClaw's `browser` tool reports: "No supported browser found"
- You need to fetch content from JavaScript-heavy websites (SPAs)
- You need to take screenshots of web pages
- You need to scrape structured data from websites

## API Endpoint

```
Base URL: https://production-sfo.browserless.io
Token: 2U7NAYZYDRFMq5Kcaefbd50a04bd202480938068f2aadeefc
```

## Available Tools

### 1. Get Page Content (HTML/Text)

Fetches the rendered HTML content of a webpage (after JavaScript execution).

```bash
$PREFIX/bin/curl -s -X POST \
  'https://production-sfo.browserless.io/chrome/content?token=2U7NAYZYDRFMq5Kcaefbd50a04bd202480938068f2aadeefc' \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://example.com"}'
```

**Response**: Raw HTML content of the page.

### 2. Take Screenshot

Captures a screenshot of a webpage.

```bash
$PREFIX/bin/curl -s -X POST \
  'https://production-sfo.browserless.io/chrome/screenshot?token=2U7NAYZYDRFMq5Kcaefbd50a04bd202480938068f2aadeefc' \
  -H 'Content-Type: application/json' \
  -d '{
    "url": "https://example.com",
    "options": {
      "type": "png",
      "fullPage": false
    }
  }' \
  -o /tmp/screenshot.png
```

**Parameters**:
- `type`: "png" or "jpeg"
- `fullPage`: true for full page, false for viewport only
- `quality`: 0-100 (jpeg only)

### 3. Scrape Structured Data

Extract specific elements from a webpage.

```bash
$PREFIX/bin/curl -s -X POST \
  'https://production-sfo.browserless.io/chrome/scrape?token=2U7NAYZYDRFMq5Kcaefbd50a04bd202480938068f2aadeefc' \
  -H 'Content-Type: application/json' \
  -d '{
    "url": "https://example.com",
    "elements": [
      {"selector": "h1"},
      {"selector": "p"},
      {"selector": "a", "timeout": 5000}
    ]
  }'
```

**Response**: JSON array with extracted elements.

### 4. Generate PDF

Create a PDF from a webpage.

```bash
$PREFIX/bin/curl -s -X POST \
  'https://production-sfo.browserless.io/chrome/pdf?token=2U7NAYZYDRFMq5Kcaefbd50a04bd202480938068f2aadeefc' \
  -H 'Content-Type: application/json' \
  -d '{
    "url": "https://example.com",
    "options": {
      "format": "A4",
      "printBackground": true
    }
  }' \
  -o /tmp/page.pdf
```

### 5. Execute JavaScript

Run custom JavaScript on a page and get results.

```bash
$PREFIX/bin/curl -s -X POST \
  'https://production-sfo.browserless.io/chrome/function?token=2U7NAYZYDRFMq5Kcaefbd50a04bd202480938068f2aadeefc' \
  -H 'Content-Type: application/json' \
  -d '{
    "code": "module.exports = async ({ page }) => { await page.goto(\"https://example.com\"); return await page.title(); }"
  }'
```

## Quick Reference

| Task | Endpoint | Key Parameters |
|------|----------|---------------|
| Get HTML content | `/chrome/content` | url |
| Screenshot | `/chrome/screenshot` | url, options.type, options.fullPage |
| Scrape elements | `/chrome/scrape` | url, elements[].selector |
| Generate PDF | `/chrome/pdf` | url, options.format |
| Run JS function | `/chrome/function` | code |

## Advanced Options

### Wait for Element/Network

```bash
$PREFIX/bin/curl -s -X POST \
  'https://production-sfo.browserless.io/chrome/content?token=2U7NAYZYDRFMq5Kcaefbd50a04bd202480938068f2aadeefc' \
  -H 'Content-Type: application/json' \
  -d '{
    "url": "https://example.com",
    "waitForSelector": {"selector": ".content", "timeout": 10000},
    "waitForTimeout": 2000
  }'
```

### Set Viewport Size

```bash
$PREFIX/bin/curl -s -X POST \
  'https://production-sfo.browserless.io/chrome/screenshot?token=2U7NAYZYDRFMq5Kcaefbd50a04bd202480938068f2aadeefc' \
  -H 'Content-Type: application/json' \
  -d '{
    "url": "https://example.com",
    "viewport": {"width": 1920, "height": 1080}
  }'
```

### Block Ads/Trackers

```bash
$PREFIX/bin/curl -s -X POST \
  'https://production-sfo.browserless.io/chrome/content?token=2U7NAYZYDRFMq5Kcaefbd50a04bd202480938068f2aadeefc' \
  -H 'Content-Type: application/json' \
  -d '{
    "url": "https://example.com",
    "blockAds": true
  }'
```

## Error Handling

| HTTP Status | Meaning |
|-------------|---------|
| 200 | Success |
| 400 | Bad request (check JSON syntax) |
| 401 | Invalid or expired token |
| 429 | Rate limit exceeded |
| 500 | Browser error (site may be blocking) |

## Usage Tips

1. **Always use `$PREFIX/bin/curl`** - System curl is broken on Android
2. **For large pages**, use `/chrome/scrape` to extract specific elements instead of full HTML
3. **For SPAs**, add `waitForSelector` to ensure content is loaded
4. **Save screenshots** to `/tmp/` for temporary files
5. **Rate limits**: Free tier has limited concurrent sessions, add small delays between requests

## Examples

### Get news headlines

```bash
$PREFIX/bin/curl -s -X POST \
  'https://production-sfo.browserless.io/chrome/scrape?token=2U7NAYZYDRFMq5Kcaefbd50a04bd202480938068f2aadeefc' \
  -H 'Content-Type: application/json' \
  -d '{
    "url": "https://news.ycombinator.com",
    "elements": [{"selector": ".titleline > a"}]
  }'
```

### Screenshot a webpage

```bash
$PREFIX/bin/curl -s -X POST \
  'https://production-sfo.browserless.io/chrome/screenshot?token=2U7NAYZYDRFMq5Kcaefbd50a04bd202480938068f2aadeefc' \
  -H 'Content-Type: application/json' \
  -d '{"url": "https://github.com", "options": {"type": "jpeg", "quality": 80}}' \
  -o /tmp/github.jpg && echo "Screenshot saved to /tmp/github.jpg"
```

### Get rendered text from SPA

```bash
$PREFIX/bin/curl -s -X POST \
  'https://production-sfo.browserless.io/chrome/content?token=2U7NAYZYDRFMq5Kcaefbd50a04bd202480938068f2aadeefc' \
  -H 'Content-Type: application/json' \
  -d '{
    "url": "https://spa-website.com",
    "waitForSelector": {"selector": "#app-content", "timeout": 10000}
  }'
```

---

**Note**: This is a cloud service. All requests go through Browserless.io servers. Do not use for sensitive/private browsing. Token usage is metered.
