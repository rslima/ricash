# Ricash Frontend Walkthrough

A beginner-friendly tour of the frontend code, written for someone who knows a
little React but not much of the ecosystem around it. Part 1 covers the overall
architecture; Part 2 walks through one page (`pages/Accounts.tsx`) line by line
as a representative example.

_Last updated: 2026-07-15 (frontend 0.5.0-SNAPSHOT)._

---

## Part 1: The big picture

The frontend is a **single-page application (SPA)**: the browser loads one HTML
page once, and from then on JavaScript redraws the screen and fetches data from
the backend API as JSON. There is no "page reload" when you navigate — it just
*looks* like there are many pages.

It's written in **TypeScript** (`.ts`/`.tsx` files), which is JavaScript plus
type annotations. Types like `Promise<JsonApiResponse<AccountResource>>` are
erased when the code is built — they only exist so the compiler and your editor
can catch mistakes ("you passed a string where an account was expected") before
the code ever runs. Files ending in `.tsx` are TypeScript files that also
contain **JSX** — the HTML-looking syntax like
`<Route path="ledgers" element={<Ledgers />} />` that React uses to describe UI.

**Vite** is the build tool. In development (`npm run dev`) it serves files
instantly and hot-swaps code as you edit; for production (`npm run build`) it
bundles everything into optimized static files in `dist/`. Vite's dev server
also proxies API calls: the frontend requests `/api/v1/...`, and the proxy
forwards it to the Spring Boot backend as `/v1/...`.

One extra twist in this project: it uses **Capacitor** (`android/`, `ios/`
folders, `@capacitor/*` packages), which wraps the same web app into native
Android/iOS apps. Most code doesn't care; a few files (`lib/capacitor.ts`,
`lib/native-init.ts`, `use-native-auth-callback.ts`) handle the "am I running
inside a phone app?" differences, mainly around login.

### How the app boots

Everything starts at `frontend/src/main.tsx`:

```tsx
createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
```

`index.html` contains an empty `<div id="root">`. This code tells React: "take
over that div and render the `App` component into it." Everything you see on
screen is descended from that one call.

`App.tsx` (`frontend/src/App.tsx`) is the skeleton of the whole app, and it
shows two fundamental React patterns:

**1. Providers.** The JSX nests several wrapper components:

```tsx
<QueryClientProvider>      // makes the data-fetching cache available everywhere
  <ThemeProvider>          // light/dark theme
    <AuthProvider>         // who is logged in, and their access token
      <BrowserRouter>      // URL-based navigation
        ...the actual pages...
```

These are React **contexts**: a way to make a value (the logged-in user, the
theme) available to any component deep in the tree without passing it down
through every intermediate component as props. Any component can later call a
hook like `useAuth()` and get the current user "out of thin air" because a
provider above it supplied it.

**2. Routing.** `react-router-dom` maps the URL to a page component:

```tsx
<Route path="/" element={<Layout />}>
  <Route index element={<Dashboard />} />
  <Route path="ledgers" element={<Ledgers />} />
  <Route path="transactions" element={<Transactions />} />
  ...
```

So visiting `/transactions` renders the `Transactions` page *inside* `Layout`.
`Layout` (in `components/layout/`) is the shared shell — header, sidebar on
desktop, bottom nav on mobile — with a slot where the current page appears.
Paths like `ledgers/:ledgerSlug/accounts` contain a **URL parameter**:
`:ledgerSlug` matches whatever ledger name is in the URL, and the page reads it
to know which ledger to show. Most pages are registered twice (plain
`/accounts` and `/ledgers/:ledgerSlug/accounts`) so you can bookmark a specific
ledger.

Also note the `lazy(() => import("@/pages/Dashboard"))` lines: this is **code
splitting**. Instead of shipping all pages in one big JavaScript file, each
page is downloaded only when you first navigate to it.
`<Suspense fallback={<TableSkeleton />}>` shows a placeholder while that
download happens. (The `@/` in imports is just a shortcut for `src/`,
configured in Vite.)

### The folder map

```
src/
├── main.tsx          entry point
├── App.tsx           providers + routes
├── pages/            one component per screen (Dashboard, Transactions, Budget, ...)
├── components/
│   ├── layout/       app shell: Header, Sidebar, BottomNav
│   ├── ui/           generic building blocks: Button, Dialog, Table, Select, ...
│   ├── charts/       Recharts-based graphs for the dashboard/reports
│   └── (others)      app-specific widgets: LedgerSelector, ExportTransactionsDialog, ...
├── api/              everything about talking to the backend
├── hooks/            reusable stateful logic (pagination, forms, error toasts, ...)
├── contexts/         Auth and Theme providers
├── lib/              plain helper functions (dates, tree building, OIDC config, ...)
├── i18n/             translations (react-i18next)
└── test/             shared test setup and fixtures
```

The `components/ui/` folder deserves a note: those files (button.tsx,
dialog.tsx, ...) follow the **shadcn/ui** style — instead of installing a
component library as a dependency, the component source code is copied into
your project so you own and can edit it. Under the hood they use **Radix UI**
(the `@radix-ui/*` packages), which provides the hard, invisible parts of
components like dropdowns and dialogs — keyboard navigation, focus trapping,
accessibility — while the styling is done with **Tailwind CSS**, the
utility-class system responsible for all the
`className="flex items-center gap-2"` strings you'll see everywhere.

### The API layer — the most important pattern to understand

Talking to the backend is organized in three tiers inside `src/api/`, and
nearly every feature follows the same recipe.

**Tier 1: the HTTP client** (`api/client.ts`). A small class wrapping the
browser's built-in `fetch()`. It knows the base URL, attaches the JWT access
token as an `Authorization` header on every request, sets JSON:API
content-type headers, and converts failed responses into a typed `ApiError`.
One instance, `apiClient`, is shared by the whole app.

**Tier 2: endpoint functions** (e.g. `api/accounts.ts`). One plain async
function per backend endpoint, with typed inputs and outputs:

```ts
export async function getAccounts(ledgerSlug, params) {
  return apiClient.get(`/ledgers/${ledgerSlug}/accounts`, params)
}
```

No React here at all — just "call this URL, get this shape of data back." That
makes them trivially testable (`accounts.test.ts`).

**Tier 3: React Query hooks** (e.g. `api/accounts.hooks.ts`). This is where
**TanStack Query** (a.k.a. React Query) comes in, and it's the heart of how
this app manages server data. A component never calls `fetch` itself; it calls
a hook:

```ts
const { data, isLoading, error } = useAccounts(ledgerSlug)
```

`useQuery` gives you the request's full lifecycle for free: `isLoading` while
in flight, `error` if it fails, `data` when it arrives — and it **caches** the
result under a key like `["accounts", "my-ledger", "list"]`. Navigate away and
back, and the data appears instantly from cache while being silently
re-fetched.

Writes use `useMutation`, and here's the elegant trick — look at
`useCreateAccount` in `api/accounts.hooks.ts`:

```ts
onSuccess: () => queryClient.invalidateQueries({ queryKey: accountKeys.ledger(ledgerSlug) })
```

After creating an account, it doesn't try to manually update any list on
screen. It just marks every cached query for that ledger as stale
("invalidates" it), and React Query automatically re-fetches whatever is
currently visible. That's why the UI stays consistent without pages knowing
about each other. The `queryKeys.ts` file exists purely to keep those cache
keys consistent so invalidation reliably hits the right entries.

### Authentication

Login uses **OIDC** (OpenID Connect — Keycloak in dev, Auth0 in prod) via the
`react-oidc-context` library. The flow: the app redirects you to the identity
provider's login page; after you log in, you're sent back to `/callback` (the
`Callback` page finishes the handshake); the library then holds your tokens
and refreshes them.

`contexts/AuthProvider.tsx` adapts that library for the app: it extracts a
clean `AuthUser` (id, name, email) from the raw token claims, and — crucially —
pushes the current access token into the `apiClient` so every API call is
authenticated. Pages that need login render `SignInRequired` when there's no
user.

### What a typical page looks like

Every page in `pages/` follows roughly the same shape:

1. Read the current ledger from the URL or the ledger-selection hook
   (`hooks/use-ledger-selection.ts`).
2. Call query hooks for the data it needs (`useAccounts`, `useTransactions`, ...).
3. While loading, render a skeleton; on error, show a toast
   (`use-query-error-toast.ts` + the **sonner** toast library).
4. Render tables/cards using the `components/ui/` primitives, with pagination
   handled by `use-pagination.ts` + `TablePagination`.
5. Create/edit flows open a `Dialog` with a form, and submit via a mutation
   hook; success invalidates the cache and the list refreshes itself.

The custom hooks in `hooks/` are just extracted, reusable pieces of that logic
— e.g. `use-expandable-tree.ts` tracks which nodes of the account tree are
expanded, `use-transaction-form.ts` holds the multi-entry transaction form
state. A custom hook is nothing magical: it's a function that calls other
hooks (`useState`, `useQuery`, ...) so several pages can share the same
stateful behavior.

### Testing

Tests use **Vitest** (a Vite-native test runner, same idea as Jest) with
**Testing Library**, which renders components into a simulated DOM (jsdom) and
interacts with them the way a user would — "click the button labeled Export,
expect a dialog to appear." `test/test-utils.tsx` wraps components in the same
providers as the real app so hooks work in tests, and `test/fixtures.ts`
provides canned API responses. Run them with `npm test`.

---

## Part 2: Walking through `pages/Accounts.tsx`

This is the "Accounts" screen — the page that shows all accounts in a ledger
as collapsible trees grouped by type (Assets, Liabilities, …), and lets you
create, edit, and delete accounts through dialogs. It's one of the bigger
pages, but it's built from three components in one file, and once you see the
shape, every other page in the app will look familiar.

The file has three parts:

1. `AccountRow` — renders one row of the tree, and recursively its children.
2. `AccountForm` — the form fields, shared by the "create" and "edit" dialogs.
3. `Accounts` — the page itself, which owns all the state and wires everything
   together.

(Line numbers below refer to the file as of this writing; they will drift as
the file changes.)

### The `Accounts` component: state first

A React component is a function that runs top-to-bottom and returns JSX
describing the UI. When any of its **state** changes, React runs the function
again and updates the screen to match. So the top half of `Accounts` is all
about declaring "what can change," and the bottom half (`return (...)`)
describes "what the screen looks like given the current values."

**Lines 317–321** call the app's shared hooks: `useTranslation` gives `t()`,
which turns keys like `"accounts.title"` into translated text (that's why you
see no literal English strings). `useAuth` tells us if someone is logged in.
`useLedgerSelection` is a custom hook that knows which ledger is currently
selected (from the URL or a saved preference) and gives back the list of
ledgers plus a setter.

**Lines 322–340** are local UI state via `useState`:

```ts
const [isCreateDialogOpen, setIsCreateDialogOpen] = useState(false)
```

`useState` returns a pair: the current value, and a function to change it.
Calling the setter is what triggers a re-render. So "is the create dialog
open?" is just a boolean; the dialog appears or disappears because the JSX
below renders differently when it flips. Similarly, `formData` and
`editFormData` are plain objects holding what's typed into each form, and
`editingAccount` remembers *which* account the edit dialog is editing.

**Lines 343–355** are the server data, via the React Query hooks from the API
layer:

```ts
const accountsQuery = useAccounts(selectedLedgerSlug ?? "", { "page[size]": 200 }, isAuthenticated)
```

This says "fetch (or reuse the cached) account list for this ledger." The page
never calls `fetch` itself and never stores the account list in `useState` —
the query cache owns it. Same for envelopes and the account→envelope mapping.
Then four **mutation** hooks (`useCreateAccount`, etc.) are prepared; each is
an object with a `.mutate`/`.mutateAsync` method to call later and an
`.isPending` flag while the request is in flight — that's what disables the
submit button and shows "Creating…".

**Lines 357–362** are small conveniences: `combineQueries` merges the loading
flags of the four queries into one `isLoading`, and `useQueryErrorToast`
watches them and pops a toast notification if any fetch fails — so the page
body doesn't need error-handling clutter.

### Derived data: `useMemo`

**Lines 364–400** compute things *from* the fetched data rather than storing
them:

```ts
const accountTree = useMemo(
  () => buildTree(accounts, (a) => a.id, (a) => a.attributes.parentAccountId),
  [accounts]
)
```

The API returns accounts as a flat list, where each account may point at a
parent. `buildTree` (in `lib/tree.ts`) converts that flat list into nested
parent→children nodes. `useMemo` is an optimization: "only recompute this when
`accounts` changes" (the `[accounts]` array is the dependency list) —
otherwise every re-render (every keystroke in a form!) would rebuild the tree.

The same pattern groups the tree roots into the five account types
(`accountsByType`) and computes `validParentAccountsForEdit` — when editing an
account, you shouldn't be able to pick the account itself or any of its
descendants as its new parent (that would create a cycle), so those get
filtered out.

This is an important React habit: **state is only the raw facts** (fetched
accounts, form input); everything derivable is computed on the fly.

### The handlers

**`handleDelete`** counts the descendants so the confirmation message can say
"this will delete 3 child accounts too," asks with the browser's `confirm()`,
then fires `deleteAccountMutation.mutate(accountId, ...)`. Notice it never
removes the row from any local list — the mutation's `onSuccess` (defined back
in `accounts.hooks.ts`) invalidates the cache, React Query re-fetches, and the
row disappears on its own.

**`handleCreate`** is a form-submit handler. `e.preventDefault()` stops the
browser's default "reload the page on submit" behavior (a classic gotcha). It
then `await`s the create mutation; if an envelope was chosen, it does a second
step — fetch that envelope's current account list, append the new account's
id, and save it back — because the backend models envelope membership as a
separate resource. Finally it closes the dialog and resets the form. Errors
from either step land in the `catch` and become a toast via `handleError`.

**`handleCreateChild`** is a nice UX touch: choosing "create child account"
from a row's menu pre-fills the form with the parent's currency, type, and id,
then opens the same create dialog.

**`handleEdit`** copies the chosen account's current values into
`editFormData` and opens the edit dialog; **`handleUpdate`** saves it,
including the fiddly "move between envelopes" logic (remove from the old
envelope's list, add to the new one's) when the envelope changed.

### The render

**Line 530** is a guard: not logged in → render `<SignInRequired />` and stop.
Early returns like this are the idiomatic way to handle "this whole page needs
X."

Then the JSX. Things worth decoding:

- **The dialogs are always in the JSX** but only *visible* when their `open`
  prop is true. `onOpenChange={setIsCreateDialogOpen}` lets the dialog close
  itself (Escape key, clicking outside) by calling our setter — the state
  stays in charge.
- **Both dialogs render the same `AccountForm`**, just wired to different
  state (`formData` vs `editFormData`) and different submit handlers. That's
  the whole reason `AccountForm` exists as a separate component.
- **The card body is a chain of conditional renders**: loading → skeleton
  placeholder; no ledger selected → an `EmptyState` pointing to the Ledgers
  page; accounts exist → the grouped tables; otherwise → an `EmptyState` with
  a "create your first account" button. The `cond ? a : b` ternaries and
  `{condition && <X />}` sprinkles are how JSX does if/else.
- **The type-group loop** iterates the five account types with `.map()`,
  skipping empty groups (`return null` renders nothing), and renders one
  `<Table>` per group. Every element produced inside a `.map()` needs a unique
  `key` prop — that's how React matches items between renders.

### `AccountRow`: recursion and "lifting state up"

`AccountRow` renders one table row: an expand/collapse chevron (only if the
account has children), the account name as a `<Link>` to that account's
transactions page, currency, balance, and a "⋯" dropdown menu with Create
child / Edit / Delete.

Two concepts to notice:

**It's recursive.** If the row is expanded, it maps over its children and
renders `<AccountRow ... depth={depth + 1} />` for each — a component
rendering itself. `depth` only controls the indentation
(`paddingLeft: depth * 24px`), which is what makes it look like a tree inside
a flat HTML table.

**It owns no state.** Which rows are expanded (`expandedIds`), and what
happens on edit/delete, all live in the parent — the row just receives values
and callbacks as **props** and calls them (`onToggleExpand(account.id)`). This
is called *lifting state up*: since expand/collapse-all buttons and many rows
all need the same information, the state lives in the one common ancestor and
flows down. Props flow down, events bubble up via callbacks — that's the core
one-way data flow of React.

### `AccountForm`: controlled inputs

The form is likewise stateless — its whole state is the
`value: AccountFormData` object it receives, and every field follows the
**controlled input** pattern:

```tsx
<Input
  value={value.name}
  onChange={(e) => onChange({ ...value, name: e.target.value })}
/>
```

The input displays exactly what's in state, and every keystroke builds a *new*
object (`{ ...value, name: ... }` copies the old fields and replaces one —
React state must be replaced, never mutated) and hands it to the parent, which
stores it, re-renders, and the input shows the new text. It feels circular but
it means the data in state is always the single source of truth.

One bit of real logic lives here: `handleParentChange` — when you pick a
parent account, the form force-copies the parent's type and currency into the
child and disables the type selector (`disabled={!!value.parentAccountId}`),
because children must match their parent.

### The takeaway pattern

Almost every page in `pages/` is this same sandwich:

1. **Hooks at the top**: auth, ledger selection, queries for data, mutations
   for writes, `useState` for dialogs and forms.
2. **`useMemo` blocks** shaping raw API data into what the UI needs.
3. **Handler functions** that call mutations and rely on cache invalidation —
   never manual list surgery.
4. **JSX** that is a pure description of the current state, full of
   conditionals and `.map()`s, delegating repeated chunks to small prop-driven
   child components.

Open `pages/Envelopes.tsx` or `pages/Transactions.tsx` next and you'll
recognize the structure immediately — they differ mainly in which hooks they
call and what the table shows.
