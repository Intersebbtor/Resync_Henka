# Sync for Reddit v23.06.30: reverse-engineering notes

APK: `com.laurencedawson.reddit_sync` v23.06.30-13:39 (versionCode 23033), minSdk 23, R8-obfuscated.
Tools: apktool 3.0.3 (smali), jadx 1.5.6 (Java view). Class names below are the obfuscated ones.

## How Boost does it

Boost's repo (rubenmayayo/boost-for-reddit) has no source, only docs. Since Reddit stopped issuing
new API keys, Boost asks the user for three values and borrows RedReader's registration:

| value        | RedReader's                              |
|--------------|------------------------------------------|
| client ID    | from Reddit's "you authorized a new app" email after logging into RedReader (`App ID: ...`) |
| redirect URI | `redreader://rr_oauth_redir`             |
| user agent   | `org.quantumbadger.redreader/1.25.1`     |

RedReader no longer hard-codes its ID in source (`GlobalConfig.appId` is set at build time), hence the email trick.

## Where Sync keeps its Reddit credentials

All OAuth lives in **`w8/b`** (`OAuthLoginHelper`):

- `d()`: login URL `https://ssl.reddit.com/api/v1/authorize.compact?client_id=Q43fSpTe8LckEg&...&redirect_uri=` + `f()`, `duration=permanent`, full scope list.
- `f()`: redirect URI `http://redditsync/auth`. `g(url)` = `url.startsWith(f())`.
- `c()`: `Authorization` header for token calls. It's assembled from an obfuscated string and decodes to
  `Basic base64("Q43fSpTe8LckEg:<redacted>")`. Sync was a "web app" with a secret; installed
  apps like RedReader have an empty secret, so the patched header is `Basic base64("<id>:")`.

Callers:
- `w8/a` (`OAuthLoginFragment`): an in-app **WebView** loads `d()`. Its `shouldOverrideUrlLoading` catches any URL
  starting with the redirect URI and reads `state`/`code`. No intent filter is involved, so any redirect URI
  scheme works without manifest changes.
- `b9/a`: `grant_type=authorization_code` (login)
- `b9/b`: `grant_type=https://oauth.reddit.com/grants/installed_client` (logged-out browsing)
- `b9/c`, `b9/d`: `grant_type=refresh_token`
  All POST to `https://ssl.reddit.com/api/v1/access_token`.

User-Agent: `com/laurencedawson/reddit_sync/singleton/a.g()` returns
`android:com.laurencedawson.reddit_sync:vv23.06.30-13:39 (by /u/ljdawson)`; `a()`/`e(token)` build headers for every request.
API calls go to `https://oauth.reddit.com/...` with `bearer <token>`.

## Why ssl.reddit.com had to go

`ssl.reddit.com/*` now answers `301 -> www.reddit.com/*`. That's fine in a WebView, but the token requests are
POSTs, and OkHttp (Volley's stack here) follows a 301 on POST as a GET without a body, so token exchange
would fail. All 8 `https://ssl.reddit.com/` strings were rewritten to `www` (w8/b, w8/a$a, b9/a-d, y8/k0).

## Shutdown behaviour

No remote kill switch was found. The shutdown is purely "the API key was revoked", plus:
- `aa/c`: bottom sheet "Sync for Reddit will shutdown on June 30, 2023 ... Sync for Lemmy". Shown from
  `MainActivity` via `s9.g.f(aa.c.class, fragmentManager)`. The patch drops that call.

## Other things checked

- **PiracyChecker** (javiersantos) is bundled, but no app code references it, so re-signing doesn't trip it.
- Sync-owned servers (as of 2026-11-04): `ap.syncforreddit.com` (image/thumbnail proxy for imgur/redgifs/gfycat) is **up**;
  `todo.syncforreddit.com` up; `images.syncforreddit.com` **down** (image hosting, `g8/b`, `g8/d`);
  `raw.githubusercontent.com/laurencedawson/reddit-sync-development/...` assets up.
- `y8/a1`, `y8/o` reference `https://oauth.reddit.com/u/<name>/about.json` (cache keys). ReVanced once
  needed a "/u/ -> /user/" fix for Sync; still to check on device whether profiles load.
- `t8/c`: redgifs/gfycat `client_credentials` request with a hard-coded key. Likely dead, so expect some redgifs to fail.
- Manifest: `allowBackup=false`, `extractNativeLibs=false` (hence `zipalign -P 16`).

## Install notes

The patched APK is signed with a new key (`sync-patch.keystore`), so Android won't install it over the
Play-signed original. Uninstall the original first. Keep the keystore: future rebuilds must use the same key
to update in place.
