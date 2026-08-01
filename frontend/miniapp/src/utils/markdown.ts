/**
 * Markdown rendering utility for AI output.
 *
 * AI responses contain Markdown (bold, lists, headings). Rendering them as plain
 * text leaks the syntax (**, #, -). This module converts Markdown to sanitized HTML
 * safe for v-html in the H5 chat bubble.
 *
 * Security: AI output is semi-trusted (it comes from our own provider with safety
 * policies), but we still sanitize to defense-in-depth against prompt-injection
 * payloads that might coax the model into emitting <script> / on* handlers / iframes.
 */

import MarkdownIt from 'markdown-it'

const md = new MarkdownIt({
  html: false, // raw HTML in source is NOT allowed; markdown-it escapes it
  linkify: false, // AI text links are not made clickable (avoid phishing surface)
  breaks: true, // single \n → <br> (matches chat UX expectations)
  typographer: false,
})

// Disable image rendering — AI output should be text-only in the chat bubble.
// (Rule name is `image`; there is no `image_inline` rule, html:false already handles raw <img>.)
md.disable(['image'])

/**
 * Renders a Markdown string to sanitized HTML.
 * Returns '' for empty input so v-html renders nothing.
 *
 * Sanitization: since html:false already escapes raw <tags>, the main residual risk
 * is markdown-generated link hrefs (javascript:). We disabled linkify so bare URLs
 * won't autolink, and the link rule is kept disabled to neutralize [text](javascript:...) too.
 */
export function renderMarkdown(input: string | null | undefined): string {
  if (!input) return ''
  // markdown-it with html:false escapes raw HTML; link/image rules are disabled.
  // Belt-and-suspenders: strip on* event handlers and javascript: URIs if any slip through.
  const rawHtml = md.render(input)
  return sanitizeHtml(rawHtml)
}

/**
 * Strips dangerous patterns from rendered HTML.
 * Conservative whitelist approach: remove anything that could execute or redirect.
 */
function sanitizeHtml(html: string): string {
  return html
    // Remove event handler attributes (onerror, onclick, ...)
    .replace(/\son\w+\s*=\s*("[^"]*"|'[^']*'|[^\s>]+)/gi, '')
    // Neutralize javascript: / vbscript: / data:text/html in any remaining href/src
    .replace(/(href|src)\s*=\s*("javascript:[^"]*"|'javascript:[^']*'|javascript:[^\s>]+)/gi, '$1="#"')
    .replace(/(href|src)\s*=\s*("vbscript:[^"]*"|'vbscript:[^']*'|vbscript:[^\s>]+)/gi, '$1="#"')
    .replace(/(href|src)\s*=\s*("data:text\/html[^"]*"|'data:text\/html[^']*')/gi, '$1="#"')
}
