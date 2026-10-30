# GoreeCloud Home Assistant — Private VPS Deployment

**Status:** Deployment preparation only. There is no verified live Home Assistant Container deployment from this repository.

## My deployment requirements

I require a privacy-first Home Assistant backend hosted on my authorized GoreeCloud VPS without disrupting existing services. The raw Home Assistant server must bind to the local loopback interface, not the public Internet. My native Android client requires a separate authenticated and verified HTTPS access path through an approved private VPN/mesh or a reviewed restricted gateway.

The official Home Assistant Container image is an upstream dependency and has no Supervisor or add-ons. An ordinary remote VPS has no direct home LAN multicast discovery or USB radio access; any home-side bridge requires a separate implementation and verification.

## Configuration and acceptance

The Compose blueprint requires a reviewed upstream image digest in `HOME_ASSISTANT_IMAGE`. Floating tags and placeholder digests must not be deployed.

1. Restore access and inspect existing workloads, mounts, open ports, firewall policies, private network topology, storage and backup arrangements.
2. Review the upstream image release and pin a real immutable SHA-256 digest appropriate to the VPS architecture.
3. Create an isolated deployment directory on the VPS and copy `.env.example` to `.env`. Set the verified image digest and actual timezone; restrict permissions. Never commit credentials or runtime config.
4. Run `bash preflight.sh` for **configuration-only checks**. A pass does not establish image provenance, readiness or security.
5. After a verified backup and conflict review, perform controlled `docker compose pull` and `docker compose up -d` on the authorized server.
6. Verify the loopback listener, service startup and authenticated API, restricted TLS access route, restart recovery and backup restoration on an isolated target.
7. Test native Android connectivity over trusted HTTPS. Do not label this deployed or stable without runtime evidence.

No deployment commands have been run on the VPS. See [architecture](../docs/ARCHITECTURE.md) and the [upstream Home Assistant Container instructions](https://www.home-assistant.io/installation/linux/).
