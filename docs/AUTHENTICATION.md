# GoreeCloud Home Assistant — Authentication Plan

| Field | Value |
| --- | --- |
| Owner | GoreeCloud founder |
| Status | PKCE protocol primitives implemented; full Android OAuth login **not yet connected** |
| Priority | Android production authentication |
| Source | https://github.com/GoreeCloud/home-assistant |
| Date | 2026-10-30 |

## My requirements

I require trusted user-initiated authentication, local ownership of credentials, and a secure native Android experience. Long-lived personal access tokens are a temporary development bridge, **not** the intended consumer login experience.

Home Assistant's authentication API supports an authorization-code flow with PKCE S256, followed by bearer access tokens and refresh-token renewal. Its client IDs are HTTPS website URLs; a native-app redirect scheme must be explicitly declared by client-ID metadata on the host. Reference: [Home Assistant Authentication API](https://developers.home-assistant.io/docs/auth_api/).

## Current implementation

The Android Kotlin source includes \`HaOAuthPkce\` with a fresh cryptographic code verifier, SHA-256 challenge, random state, authorization URL construction and strict callback matching. JVM unit tests use synthetic identities and tokens. The helper has no access to private keys and makes no network requests.

**Not yet implemented:** registered client-ID metadata, hosted redirect declaration, system-browser login, Android deep-link callback routing, authorization-code token exchange, encrypted refresh-token storage, rotation/expiry, revocation on sign-out, session migration from manual tokens, and live-instance validation.

## Release acceptance

1. Verify an authoritative GoreeCloud-controlled HTTPS client-ID metadata endpoint and precisely one approved Android redirect URI. Do not invent or assume a live endpoint.
2. Use an Android system browser or trusted app-auth flow, not an embedded WebView.
3. Route callbacks only into the intended exported Android handler and reject wrong origin, state, duplicate codes, errors and stale sessions.
4. Exchange one-time codes over trusted HTTPS with \`code_verifier\`, store refresh tokens using Android Keystore-encrypted storage, refresh proactively and revoke on disconnect.
5. Verify handling of expired tokens, denied consent, disabled users, network loss, process restart, app upgrades, multiple accounts and session logout.
6. Pass unit, emulator, actual Android device, security and accessibility checks before calling OAuth complete.

The metadata registration and production flow are pending. No user sign-in is being claimed by this document.
