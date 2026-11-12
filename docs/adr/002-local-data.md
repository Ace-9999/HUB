# ADR-002: Domain-owned local state and privacy

Accepted 12 November 2026. Room per implemented domain; DataStore for nonsecret
preferences. UTC instants plus original/reporting zone; injected clocks and IDs.
Sensitive data excluded from OS backup. No telemetry, hosted proxy or runtime AI.
Secrets will use Android Keystore when the production College gate opens.
Never replace unknown data with zero. The full/readable JSON requirement remains
authoritative: automatic internal snapshots include retained sensitive records,
review exports omit them by default, and neither ever includes secrets/grants.

No destructive migrations. Additive schema changes and exported schema files;
tests must verify upgrades before claiming release safety. Device-only gates stay
pending, even when synthetic replay tests pass. No production source adapter is
enabled before its I02 physical feasibility gate. App-private backup removal on
uninstall must be explained; external exports remain owner-managed.
