// PROTOTYPE stub for pkd/frontend/src/lib/api.js — replaces the real apiFetch/apiGet
// so link-suggestion.js ("[[" doc search) can import without pulling in cookies/CSRF.
// The app doesn't have a document search endpoint wired into this prototype yet.
export async function apiGet() {
  return []
}
