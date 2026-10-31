# GoreeCloud Home Assistant

GoreeCloud Home Assistant is an early **native Android client** for the upstream [Home Assistant](https://www.home-assistant.io/) platform. An independent web client is planned. This repository does **not** represent a deployed backend, an accepted Android release, or a GoreeCloud-authored home-automation engine.

## Current Android foundation

The Kotlin/Jetpack Compose source includes an explicit HTTPS connection flow using a manually provisioned long-lived token for development, Android Keystore-backed local credential storage, authenticated entity reads, and restricted actions for lights, switches, input booleans and scenes. A foreground-only WebSocket subscription can request REST state refreshes when entities change. These are source-level capabilities; live Home Assistant integration and physical-device validation are pending.

The repository also contains a PKCE helper and callback-validation tests. **Full OAuth login is not connected.** A hosted client-ID declaration, Android redirect registration, token exchange and refresh lifecycle must be implemented and verified before consumer onboarding. See the [authentication plan](docs/AUTHENTICATION.md).

## Build and verification

Development builds require JDK 17, Android SDK 36 and Gradle 8.13. The repository does not include a Gradle wrapper JAR; CI provisions Gradle separately.

```sh
gradle --no-daemon :app:testDebugUnitTest :app:assembleDebug
```

The [Android CI workflow](.github/workflows/android.yml) runs these tasks; [run 41160718246](https://github.com/GoreeCloud/home-assistant/actions/runs/41160718246) passed on the reviewed `main` revision (`56aeb86`). This confirms a debug build and automated source tests, **not** a working installation or reliable control of real devices. The internal debug APK is not a signed production installer and has not been accepted for standalone delivery.

## Home Assistant backend: blueprint only

[`deploy/compose.yaml`](deploy/compose.yaml) is a private VPS deployment blueprint, not proof of a running service. Its image reference must be pinned to a reviewed immutable digest before deployment. The plan keeps port **8123** bound to VPS loopback; Android access requires a separately tested, authenticated HTTPS path over authorized private networking.

A VPS does not automatically discover a home's LAN devices or connect to Zigbee/Z-Wave radios. Any bridge to home hardware needs separate private-network design and testing. Do not expose raw port 8123 to the public Internet or treat a successful Compose configuration check as operational acceptance.

See [deployment instructions](deploy/README.md) and [architecture](docs/ARCHITECTURE.md). No deployment is asserted here.

## Release blockers

- Replace manual development tokens with end-to-end OAuth login, renewal and revocation.
- Test REST actions and WebSocket reconnect behavior against an authorized live instance; then validate Android foreground/background behavior.
- Perform physical-device testing, accessibility review, credential-handling review and release-signing checks.
- Verify standalone APK delivery through the authorized `GoreeCloud/Artifacts` Google Drive folder before claiming that an installer has been distributed.

Sensitive operations such as locks and alarms require separate safeguards and acceptance. No live home device or backend state should be inferred from source tests.

## License and provenance

GoreeCloud-authored Android client source is licensed under [Apache-2.0](LICENSE). Home Assistant Container is an independent upstream product with its own terms and trademarks; this project is not endorsed by the Home Assistant organization.
