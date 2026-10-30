# GoreeCloud Home Assistant — Architecture and Rollout

| Field | Value |
| --- | --- |
| Owner | GoreeCloud founder |
| Status | Android prototype source in progress; VPS not deployed |
| Priority | Native Android, then native web |
| Source | https://github.com/GoreeCloud/home-assistant |
| Date | 2026-10-30 |

## Product direction

I intend a privacy-first control surface for my home: device status, rooms, lights, switches, scenes, routines, energy and climate. GC UI is the design authority. I require a genuine Android application, not a WebView of a website.

## First source milestone

Android Kotlin/Compose screens, explicit HTTPS endpoint, manually issued development long-lived token, Android Keystore-encrypted local connection, authenticated REST reads, allowlisted command actions, local tests, and build CI. These do not establish successful installation, live control or Stable acceptance. Manual token onboarding must be replaced by Home Assistant's OAuth/IndieAuth flow for production release.

## Private VPS topology

A VPS can run Home Assistant Container but cannot natively discover the home LAN or attach home Zigbee/Z-Wave radios. A trusted home-side bridge or compatible integration linked through the authorized GoreeCloud private mesh/VPN is required for local devices. The Compose blueprint binds port 8123 to the VPS loopback only. A distinct authenticated HTTPS private access route must be implemented and tested before an Android phone can connect. Do **not** open raw 8123 publicly. Home Assistant Container has no Supervisor or add-ons.

## Deployment acceptance

1. Verify authorized VPS, resources, existing workloads and port conflicts, private network reachability, and backup storage.
2. Review and pin a vetted Home Assistant image digest; inspect supply chain and version. The current stable tag is an unpinned blueprint.
3. On VPS, create a restricted directory, protect config, and back up existing workloads. Run docker compose config and bring up the service only after review.
4. Confirm loopback exposure, logs, actual authenticated API response, restart, restore test in an isolated environment, and version-rollback plan.
5. Configure a private HTTPS endpoint with valid certificate and narrowly scoped access, then run the Android app on a representative device.
6. Replace the manual-token onboarding with OAuth, add WebSocket state subscriptions and security lifecycle checks; test accessibility and release signing.

## Further roadmap

**P1:** full real-device Android validation, OAuth, WebSocket subscriptions, native room navigation, offline read-only snapshots, accessible states.

**P2:** independent web app with GC UI tokens; hardened permissions, scene and automation editors, home roles.

**P3:** Android widgets, privacy-preserving notifications, energy history, smart-home local bridge, optional voice.

Sensitive controls (locks, alarms, garage doors, surveillance feeds) need separate capability-, privacy-, and confirmation-oriented implementation. Never log tokens or fabricate device states. Deployment, app-runtime testing and release distribution are currently pending.
