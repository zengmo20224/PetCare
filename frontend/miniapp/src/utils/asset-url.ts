/**
 * Asset URL resolver for cross-platform (H5 + WeChat mini-program) image sources.
 *
 * Backend returns relative paths like `/uploads/xxx.png`. On H5 the Vite dev
 * server proxy (or same-origin in production) makes relative paths work, so
 * `VITE_API_BASE_URL` is empty. On WeChat mini-program, however, `<image>`
 * requires an absolute URL — a relative src silently fails to load. The
 * mini-program base URL lives in `VITE_MP_API_BASE_URL`, which
 * `getPrimaryApiBaseUrl()` already resolves (with multi-base fallback support).
 *
 * Centralizing asset URL resolution here fixes the "images render blank in the
 * mini-program" regression introduced when image helpers across the app read
 * `VITE_API_BASE_URL` directly (empty under mp-weixin).
 *
 * Usage: `assetFullUrl(post.coverUrl)`
 */
import { getPrimaryApiBaseUrl } from '@/api/request'

/**
 * Resolve a backend-relative path to an absolute asset URL.
 * - null/empty → '' (caller decides placeholder)
 * - already absolute (http/https/blob/wxfile) → returned as-is
 * - relative (`/uploads/...`) → prefixed with the active API base
 */
export function assetFullUrl(url: string | null | undefined): string {
  if (!url) return ''
  // Already absolute (covers http(s), plus mini-program local schemes).
  if (/^(https?:|blob:|wxfile:)/i.test(url)) return url
  return getPrimaryApiBaseUrl() + url
}
