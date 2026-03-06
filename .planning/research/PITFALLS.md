# Pitfalls Research

**Domain:** Adding React SPA web client to existing Fastify/Android mobile backend (OutlinerGod v0.8)
**Researched:** 2026-03-06
**Confidence:** HIGH (backend code inspected directly; web patterns verified against official docs and community sources)

---

## Critical Pitfalls

### Pitfall 1: @fastify/static wildcard conflicts with SPA catch-all

**What goes wrong:**
Registering `@fastify/static` with `wildcard: true` (the default) adds a catch-all `GET /*` route internally. Any subsequent attempt to register your own catch-all route for serving `index.html` on unknown paths causes a "Method already declared" duplicate route error. The SPA's client-side router (React Router) never receives control, so deep-linking to any URL other than `/` returns a 404.

**Why it happens:**
Developers register `@fastify/static` first, then add `app.get('*', ...)` for the SPA fallback, not knowing the static plugin already registered a wildcard. Fastify's route uniqueness check then throws at startup. The existing `index.ts` has a placeholder `app.get('/', ...)` that will need to be replaced; this pitfall surfaces the moment `@fastify/static` is added alongside it.

**How to avoid:**
Register `@fastify/static` with `wildcard: false`. This makes the plugin enumerate files at startup (acceptable since the `dist/` bundle is static). Then register your own explicit catch-all after all `/api/*` routes:

```typescript
// Register static plugin WITHOUT wildcard
await app.register(staticPlugin, {
  root: path.join(distPath),
  prefix: '/',
  wildcard: false,
})

// SPA fallback — must come AFTER all /api routes
app.setNotFoundHandler((_req, reply) => {
  return reply.sendFile('index.html')
})
```

The `setNotFoundHandler` approach is more reliable than `app.get('*', ...)` because Fastify calls it when no other route matches, including paths with slashes that `*` can miss under certain Fastify versions.

**Warning signs:**
- Server throws "Route GET:/* already declared" at startup
- Navigating directly to `/documents` or `/editor/123` in the browser returns a 404 JSON response
- Only the root `/` URL works

**Phase to address:**
Phase 1 (Fastify static serving setup) — must be the first thing tested before any React routing is built.

---

### Pitfall 2: Google OAuth audience mismatch — web client ID vs. Android client ID

**What goes wrong:**
The existing backend `verifyGoogle()` in `auth.ts` calls `client.verifyIdToken({ audience: process.env.GOOGLE_CLIENT_ID })`. The `GOOGLE_CLIENT_ID` env var is currently set to the **web server client ID** (used by Android as `serverClientId`). When the browser's Google Identity Services (GIS) library signs in, it issues an ID token whose `aud` claim is the **web OAuth 2.0 client ID** for the browser application — which may be a different client ID from the one set in `GOOGLE_CLIENT_ID` if a new GCP OAuth credential was created for the browser. `verifyIdToken` will reject it with audience mismatch, returning a 401.

**Why it happens:**
The GCP project has (at minimum) two OAuth clients: the Android client (package+SHA1) and the web/server client. The Android flow uses the web client ID as `serverClientId`, so Android-issued tokens have `aud = WEB_CLIENT_ID`. The browser GIS library issues tokens where `aud = BROWSER_CLIENT_ID` (whatever client ID was passed to `google.accounts.id.initialize`). If `BROWSER_CLIENT_ID !== WEB_CLIENT_ID`, verification fails.

**How to avoid:**
Use the **same** web client ID for both Android `serverClientId` and GIS `client_id` initialization. Do not create a new GCP credential for the browser — reuse the existing web application credential. Pass that same client ID to `google.accounts.id.initialize({ client_id: '<WEB_CLIENT_ID>' })`. The backend verifies against `WEB_CLIENT_ID` and both token sources will match.

If a separate browser credential is ever needed, update `verifyIdToken` to accept an array of audience IDs:
```typescript
await client.verifyIdToken({
  idToken,
  audience: [process.env.GOOGLE_CLIENT_ID!, process.env.GOOGLE_WEB_CLIENT_ID!],
})
```

**Warning signs:**
- Browser login returns 401 from `/api/auth/google`
- Android continues to work fine (same endpoint, same env var)
- Google token verification error message mentions "audience" or "aud"

**Phase to address:**
Phase 2 (Auth integration) — test browser login before building any other web features.

---

### Pitfall 3: GIS credential callback receives `credential` field, not `id_token`

**What goes wrong:**
The backend `/api/auth/google` endpoint expects the request body field `id_token`. The Google Identity Services library's `CredentialResponse` object uses the field name `credential`. Developers copy-paste the Android naming and send `{ id_token: response.credential }` — but when debugging with `console.log(response)` they see `{ credential: '...' }` and sometimes accidentally wire `{ id_token: response.id_token }` which is `undefined`, causing a 400 "Missing id_token" from the backend.

**Why it happens:**
GIS is the newer library (replacing the deprecated `gapi.auth2`). Its response shape differs from older Google Sign-In documentation that used `id_token` directly. The backend field name `id_token` is a backend-defined contract, not a reflection of the GIS response shape.

**How to avoid:**
In the React login component, explicitly map:
```typescript
google.accounts.id.initialize({
  client_id: GOOGLE_CLIENT_ID,
  callback: (response: google.accounts.id.CredentialResponse) => {
    // response.credential is the ID token
    fetch('/api/auth/google', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ id_token: response.credential }), // explicit mapping
    })
  },
})
```

**Warning signs:**
- Backend returns 400 "Missing id_token" after successful Google popup
- `console.log(response)` in the callback shows `credential` present but `id_token` undefined

**Phase to address:**
Phase 2 (Auth integration) — add a comment in the auth route documenting the field name contract.

---

### Pitfall 4: contenteditable cursor jumping on every React re-render

**What goes wrong:**
When a node's content is stored in React state and the contenteditable element is re-rendered after every keystroke, the cursor jumps to position 0 (start of element) or to a wrong position. This is especially bad in Safari and Firefox but also affects Chrome. For an outliner editor, this makes typing impossible.

**Why it happens:**
React's reconciliation algorithm compares the virtual DOM and may update `innerHTML` or `textContent` even when the value hasn't changed (due to stale closures, key changes, or parent re-renders). Any DOM mutation to a contenteditable resets the browser's caret position. The `onChange` → state → re-render cycle is too slow to preserve caret position.

**How to avoid:**
Treat contenteditable as an **uncontrolled component**. Do not use `dangerouslySetInnerHTML` with state-driven content. Instead:

1. Use `useRef` to hold the DOM node. Never set `innerHTML` in response to user input.
2. Read content from the DOM in event handlers (`ref.current.textContent`), not from state.
3. Only write to the DOM when an external value change arrives (e.g., sync pull bringing a remote update) — in that case use `useLayoutEffect` (synchronous, before paint) to restore caret position after the mutation.
4. Use `input` event (not `onChange`) for reading keystroke-by-keystroke changes.

For the WYSIWYG inline formatting (bold/italic markers), apply formatting via `document.execCommand` or `Selection`/`Range` APIs rather than re-rendering with new HTML content. If React controls the DOM for formatting, save and restore cursor position with `Range` snapshots in `useLayoutEffect`.

**Warning signs:**
- Typing causes cursor to jump to beginning after each character
- Ctrl+B (bold) moves cursor to start
- Works in controlled `<input>` but breaks in `<div contenteditable>`
- IME composition (Korean, CJK) fires `onChange` mid-composition, corrupting input

**Phase to address:**
Phase 3 (Node editor) — prototype contenteditable approach before wiring to state management.

---

### Pitfall 5: IME / composition events fire onChange mid-composition

**What goes wrong:**
React fires `onChange` (which maps to the DOM `input` event) on every IME keystroke during composition, including partial/uncommitted characters. For languages like Korean, Chinese, and Japanese, this means state updates happen with half-formed characters. For the outliner, this triggers debounced syncs and node splits (Enter key handling) at wrong times.

**Why it happens:**
React normalizes `onChange` to fire on every `input` event regardless of composition state. The `compositionstart` / `compositionend` lifecycle is not automatically respected. This is a known long-standing React issue (GitHub #3926, #8683).

**How to avoid:**
Track composition state with a ref:
```typescript
const composingRef = useRef(false)

<div
  contentEditable
  onCompositionStart={() => { composingRef.current = true }}
  onCompositionEnd={(e) => {
    composingRef.current = false
    handleInput(e.currentTarget.textContent ?? '')
  }}
  onInput={(e) => {
    if (!composingRef.current) {
      handleInput(e.currentTarget.textContent ?? '')
    }
  }}
  onKeyDown={(e) => {
    if (e.key === 'Enter' && !composingRef.current) {
      handleEnter(e)
    }
  }}
/>
```

**Warning signs:**
- Korean/CJK typing produces doubled or split characters
- Enter creates new node mid-IME composition
- Sync push fires with partial characters mid-composition

**Phase to address:**
Phase 3 (Node editor) — must be addressed alongside the cursor pitfall above.

---

### Pitfall 6: HLC clock using floating-point Date.now() in browser

**What goes wrong:**
The existing backend and Android HLC use 13-digit decimal millisecond wall timestamps (e.g., `1709737200000`). `Date.now()` in browsers returns an integer ms value — correct. However, if the browser clock is behind the server clock by more than the HLC max drift (typically 500ms), the HLC receive logic will advance the logical counter indefinitely rather than the wall clock, eventually producing timestamps that look far in the future or cause overflow. In a browser context, `performance.now()` returns sub-millisecond floats; if accidentally used instead of `Date.now()`, the HLC format breaks (non-integer timestamp violates the `<13-digit-decimal-wall>` contract).

**Why it happens:**
Developers reach for `performance.now()` for "better precision" not realizing HLC requires integer milliseconds. Clock skew between the user's browser and the Docker server is common in home lab setups where NTP is not tightly synchronized.

**How to avoid:**
- Always use `Math.floor(Date.now())` as the wall clock source in the browser HLC implementation.
- Mirror the existing backend `hlc.ts` exactly: same decimal format, same counter arithmetic.
- Add a max-drift guard: if incoming server HLC wall is more than 5 minutes ahead of browser clock, log a warning rather than silently advancing the counter to an enormous value.
- Test the browser HLC against the backend's HLC comparison function using the same test vectors from the Android tests.

**Warning signs:**
- HLC timestamps from the browser have decimal points (`.`)
- After a sync pull, the browser's HLC counter grows to 4-digit or 5-digit values when it should be near 0
- Server rejects sync pushes due to malformed HLC strings

**Phase to address:**
Phase 4 (Sync integration) — port `hlc.ts` to the web client with explicit test coverage before wiring sync.

---

### Pitfall 7: JWT stored in localStorage is readable by any injected script

**What goes wrong:**
The most convenient approach — store the JWT access token in `localStorage` — exposes it to XSS attacks. Any third-party script (analytics, CDN-hosted library) that is compromised can read `localStorage` and exfiltrate the token, giving an attacker full API access to the user's notes.

**Why it happens:**
`localStorage` is the path of least resistance in browser SPAs. The Android client stores tokens in DataStore (sandboxed), so developers assume the same simplicity applies to the web.

**How to avoid:**
For this self-hosted, single-user-per-installation tool, the pragmatic balance is:

- Store the **access token** (1h expiry) in **memory only** (React context / module-level variable). It is lost on page refresh.
- Store the **refresh token** in a `SameSite=Strict; Secure; Path=/api/auth/refresh` cookie set by the backend on login. The browser sends it automatically. Never access it from JS.
- On page load: if the in-memory access token is missing, call `/api/auth/refresh` (cookie is sent automatically) to silently obtain a new access token.

This requires the backend's `/api/auth/refresh` route to accept either the JSON body `{ refresh_token }` (Android) or the cookie (web). Add cookie-based refresh as an alternative path, or accept both.

**Warning signs:**
- Access token is visible in the Application > Local Storage tab of DevTools
- On page refresh, the user is always logged out (means it's stored only in memory — correct behavior)
- On page refresh, the user stays logged in without any API call (means it's in localStorage — wrong)

**Phase to address:**
Phase 2 (Auth integration) — design the token storage strategy before writing any authenticated API calls.

---

## Technical Debt Patterns

| Shortcut | Immediate Benefit | Long-term Cost | When Acceptable |
|----------|-------------------|----------------|-----------------|
| Store JWT in `localStorage` | Simple, survives refresh | XSS vulnerability exposes all user notes | Never — use memory + httpOnly cookie |
| Use `dangerouslySetInnerHTML` with state | Easy React-style updates | Cursor jumps, IME breaks, re-render thrash | Never for contenteditable editors |
| Skip `wildcard: false` on @fastify/static | One less config line | Deep links 404, SPA routing broken | Never |
| Share the Vite dev proxy config as production routing | Works in dev instantly | Proxy does not exist in production build | Never — Fastify serves the bundle in prod |
| Create a new GCP OAuth web credential | Clean separation | Audience mismatch breaks backend verify | Only if audience array updated in backend |
| Poll sync every 5 seconds instead of debounced push | Simple timer | Excessive DB load, battery drain | Never — use push-on-change with debounce |
| Render every tree node without memoization | Simpler code | Re-renders entire tree on every keystroke | Acceptable in prototype; fix before v0.8 ship |

---

## Integration Gotchas

| Integration | Common Mistake | Correct Approach |
|-------------|----------------|------------------|
| Google GIS + backend `/api/auth/google` | Send `response.id_token` (undefined) instead of `response.credential` | Map explicitly: `{ id_token: response.credential }` |
| Google OAuth multi-client | Create new GCP credential for web, forget to update backend audience | Reuse existing web client ID; if creating new one, pass audience array to `verifyIdToken` |
| GIS + React | Load `accounts.google.com/gsi/client` script in `index.html`, race condition where `google` global is undefined when component mounts | Use `onload` callback or dynamic script injection with Promise resolution |
| Fastify CORS during development | Vite dev server on port 5173, Fastify on 3000 — CORS blocks all API calls | Enable `@fastify/cors` in development only OR use Vite proxy (`/api` → `http://localhost:3000`) — proxy is simpler |
| @fastify/static + React Router | Direct URL navigation returns 404 | Set `wildcard: false`, use `setNotFoundHandler` to serve `index.html` |
| Vite build output path | `vite build` outputs to `web/dist` but Fastify root points to wrong path | Align `vite.config.ts` `build.outDir` with `@fastify/static` `root` option |
| HLC format web vs. backend | Browser HLC uses different separator or format than `<13-digit>-<5-digit>-<deviceId>` | Copy backend `hlc.ts` verbatim to web client; share test vectors |
| Sync push `device_id` in browser | Android uses a persistent device UUID from DataStore; browser has no equivalent | Generate a UUID on first visit and store in `localStorage` as `device_id`; this is safe (not a secret) |
| Refresh token rotation + browser | Android sends `device_id` in refresh body; backend inserts it; web must also send a stable device ID | Use the same localStorage `device_id` for both initial login and all refresh calls |
| @dnd-kit DragOverlay + useSortable | Render the same component that calls `useSortable` inside `DragOverlay` — causes duplicate hook calls | Create a separate presentational clone component for the drag overlay |

---

## Performance Traps

| Trap | Symptoms | Prevention | When It Breaks |
|------|----------|------------|----------------|
| Re-rendering entire node tree on every keystroke | Visible lag at 50+ nodes; CPU spike in profiler | `React.memo` on `NodeRow`; separate editor state from tree structure state | ~30 nodes in the editor |
| Debounced sync push fires on every node re-render | Sync push every keystroke, SQLite write storm | Debounce at 500ms at the React state layer, not per-node | Immediately visible in DevTools Network tab |
| Pulling full document on every sync | Increasing pull latency as documents grow | Always use `since_hlc` query parameter on sync pull; never pull without it | Documents with >200 nodes |
| DnD `useSortable` on every node without stable keys | Drag stutter, ghost item misalignment | Stable `key` prop = node ID (never index); `items` array passed to `SortableContext` must be stable reference | >20 nodes during drag |
| `document.execCommand` for formatting triggering a re-render | Every bold/italic adds a history entry and re-renders | Use `execCommand` then read from DOM; do not sync execCommand result back into React state on the same tick | Immediate — any formatting action |

---

## Security Mistakes

| Mistake | Risk | Prevention |
|---------|------|------------|
| JWT in `localStorage` | XSS steals token; attacker reads/modifies all user notes | Access token in memory; refresh token in `httpOnly; SameSite=Strict` cookie |
| No `SameSite` on refresh token cookie | CSRF could trigger a refresh from a third-party page | Set `SameSite=Strict` and `Secure` on the refresh token cookie; backend already has rate limiting on auth routes |
| Serving `web/dist` at root without stripping `.map` files | Source maps expose full application source to anyone | Add `sourcemap: false` in Vite production build config, or serve maps only to authenticated users |
| Allowing `localhost` origin in production CORS | Attacker on same machine can make cross-origin requests | When `@fastify/cors` is added, restrict `origin` to the production domain `https://notes.gregorymaingret.fr`; no wildcard |
| Not validating file UUID before serving via `@fastify/static` | Path traversal if UUID contains `../` sequences | The existing `routes/files.ts` already validates UUIDs — ensure the new static root for the web bundle is separate from the uploads path |
| GIS `redirect_uri` includes `localhost` in production | OAuth phishing vector | Register only `https://notes.gregorymaingret.fr` as authorized JavaScript origin in GCP Console; no localhost in the live credential |

---

## UX Pitfalls

| Pitfall | User Impact | Better Approach |
|---------|-------------|-----------------|
| No visual feedback while Google sign-in popup loads | User clicks button, nothing happens, clicks again, double auth | Disable the button and show a spinner immediately on click; re-enable on callback |
| contenteditable node loses focus on sync pull | User typing is interrupted every time sync runs | Debounce sync push; never trigger a sync pull while the user has an active selection in a contenteditable |
| Tab key indents the browser focus instead of indenting the node | Pressing Tab moves focus out of the editor | Intercept `keydown` for Tab/Shift+Tab in the node's `onKeyDown` handler; call `e.preventDefault()` |
| Enter in a note textarea creates a new sibling node | Note field is for multi-line text; Enter should newline | Note fields must use `<textarea>` (not contenteditable) and must NOT share the same Enter key handler as content fields |
| Zoom navigation has no Back button affordance | User zooms in, can't figure out how to zoom out | Add a breadcrumb or "zoomed to: [node title]" banner with a click-to-unzoom action; replicate Android's back-stack pattern |
| DnD drag fails silently on touch screens | Web is desktop-first per spec, but touch users exist | Document explicitly that DnD is desktop/mouse only; @dnd-kit requires PointerSensor configuration for touch |

---

## "Looks Done But Isn't" Checklist

- [ ] **SPA routing:** Verify that navigating directly to `https://notes.gregorymaingret.fr/documents/123` in a fresh browser tab loads the app (not a 404). This only passes if the Fastify catch-all is correctly configured.
- [ ] **Google OAuth:** Verify that the GCP Console's "Authorized JavaScript origins" includes the production domain, not just localhost. Check that the credential used in GIS matches the one the backend verifies against.
- [ ] **Token refresh:** Verify that after the 1-hour JWT expiry, the next API call silently refreshes the token and retries — the user should see no interruption. Test by manually advancing time or shortening the JWT expiry to 10 seconds in dev.
- [ ] **HLC format parity:** Verify that HLC strings generated by the browser client pass the backend's HLC comparison and parsing logic. Run the existing `hlc.test.ts` vectors against the browser implementation.
- [ ] **Sync push device_id stability:** Verify that the `device_id` used by the browser persists across page refreshes. A new UUID on every load means the backend can never merge correctly with mobile data.
- [ ] **Sync pull `since_hlc`:** Verify that the web client always passes `since_hlc` on pulls (never a full pull after first load). Check the Network tab: pull responses should shrink after the first sync.
- [ ] **contenteditable Enter key:** Verify Enter creates a new sibling (not a `<br>` or `<div>`) in Chrome, Firefox, and Safari — browsers have different default contenteditable behavior for Enter.
- [ ] **IME input:** Verify that typing Korean or Chinese does not split a node mid-composition. Test with Chrome's built-in CJK IME or the OS IME.
- [ ] **Vite build served correctly:** After `npm run build` and container restart, verify the production bundle is served (check for `.vite` or unminified code in DevTools Sources — these indicate the dev server is running instead of the static bundle).

---

## Recovery Strategies

| Pitfall | Recovery Cost | Recovery Steps |
|---------|---------------|----------------|
| SPA catch-all not configured | LOW | Add `setNotFoundHandler` in `index.ts`, rebuild container |
| Cursor jumping in contenteditable | HIGH | Refactor node editor to use uncontrolled DOM pattern; requires rewriting state binding logic |
| Google audience mismatch | LOW | Confirm GCP client IDs, update env var or add audience array to `verifyIdToken` call |
| JWT stored in localStorage | MEDIUM | Replace with memory storage + httpOnly cookie refresh; requires backend route change for cookie-based refresh |
| HLC format mismatch | MEDIUM | Port backend `hlc.ts` to web client verbatim; run comparison tests; existing server data is unaffected |
| Sync pushes full document instead of delta | MEDIUM | Add `since_hlc` query parameter support to sync pull call; existing backend already supports it |
| DnD performance regression | MEDIUM | Add `React.memo` to `NodeRow`, stabilize `items` array ref in `SortableContext` |

---

## Pitfall-to-Phase Mapping

| Pitfall | Prevention Phase | Verification |
|---------|------------------|--------------|
| @fastify/static wildcard vs. SPA catch-all | Phase 1: Fastify static serving | `curl https://notes.gregorymaingret.fr/documents` returns 200 with HTML |
| Google OAuth audience mismatch | Phase 2: Auth integration | Browser login succeeds; check token `aud` claim matches `GOOGLE_CLIENT_ID` |
| GIS credential field name (`credential` vs `id_token`) | Phase 2: Auth integration | POST to `/api/auth/google` returns 200 with JWT |
| JWT in localStorage | Phase 2: Auth integration | DevTools Application tab shows no token in localStorage; Network tab shows silent refresh on reload |
| contenteditable cursor jumping | Phase 3: Node editor | Rapid typing test: 50 keystrokes without cursor movement |
| IME composition mid-Enter | Phase 3: Node editor | Korean IME test: compose character, press Enter — no new node created mid-composition |
| HLC browser format parity | Phase 4: Sync | HLC strings from browser parse correctly through backend `hlc.ts` comparison |
| HLC clock drift in browser | Phase 4: Sync | Sync push succeeds after manually setting browser clock 30s behind server |
| Sync `device_id` stability | Phase 4: Sync | Reload page, trigger sync — server receives same device_id as prior session |
| DnD `DragOverlay` duplicate hook | Phase 5: DnD | No React console errors during drag; drag overlay renders correctly |
| DnD performance with many nodes | Phase 5: DnD | Editor with 100 nodes: drag is smooth (no frame drops in Performance tab) |
| Vite proxy vs. production serving | Phase 1: Fastify static serving | Production container serves minified bundle; dev server uses proxy; both work |

---

## Sources

- [facebook/react#2047 — ContentEditable caret position jumps](https://github.com/facebook/react/issues/2047)
- [codegenes.net — Caret position reverts to start in contenteditable (Safari/Firefox)](https://www.codegenes.net/blog/caret-position-reverts-to-start-of-contenteditable-span-on-re-render-in-react-in-safari-and-firefox/)
- [facebook/react#3926 — Change event fires before IME composition ends](https://github.com/facebook/react/issues/3926)
- [facebook/react#8683 — Composition events with Chinese/Japanese IME](https://github.com/facebook/react/issues/8683)
- [fastify/fastify-static#64 — Can no longer use catch-all route](https://github.com/fastify/fastify-static/issues/64)
- [fastify/fastify-vite#184 — Method already declared with SPA mode](https://github.com/fastify/fastify-vite/issues/184)
- [Google Developers — Verify the Google ID token on your server side](https://developers.google.com/identity/gsi/web/guides/verify-google-id-token)
- [Google Developers — Sign in with Google JS API reference (credential callback)](https://developers.google.com/identity/gsi/web/reference/js-reference)
- [Google Developers — Authenticate with a backend server (audience)](https://developers.google.com/identity/sign-in/web/backend-auth)
- [vitejs/vite discussion#8043 — Proxy doesn't work after npm run build](https://github.com/vitejs/vite/discussions/8043)
- [cybersierra.co — JWT Storage in React: Local Storage vs Cookies](https://cybersierra.co/blog/react-jwt-storage-guide/)
- [clauderic/dnd-kit discussions#1070 — Drag into sortable tree and adjust hierarchy](https://github.com/clauderic/dnd-kit/discussions/1070)
- [clauderic/dnd-kit#898 — Major performance issues with sortable tree example](https://github.com/clauderic/dnd-kit/issues/898)
- [dnd-kit Accessibility documentation](https://docs.dndkit.com/guides/accessibility)
- [Typeonce — Hybrid Logical Clock implementation in TypeScript](https://www.typeonce.dev/snippet/hybrid-logical-clock-implementation-typescript)
- Backend source code inspected directly: `backend/src/routes/auth.ts`, `backend/src/index.ts`

---
*Pitfalls research for: OutlinerGod v0.8 — adding React web client to existing Fastify/Android backend*
*Researched: 2026-03-06*
