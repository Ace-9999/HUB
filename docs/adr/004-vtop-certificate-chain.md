# ADR-004: Repair VTOP's incomplete public TLS chain without ignoring errors

12 November 2026. VTOP served one certificate: the `*.vit.ac.in` leaf, issued by
Sectigo Public Server Authentication CA DV R36. OpenSSL could not verify that
server-supplied chain. Adding the official missing intermediate verified the leaf
against the pre-existing Git/Mozilla CA bundle and Root R46 from that bundle.

The debug manifest uses Android Network Security Configuration for the exact
`vtopcc.vit.ac.in` hostname, with system anchors plus that verified public
intermediate. CA public-key pins cover R36 and its R46 root as a backup. No pin
expiration, remote trust update, hostname bypass, cleartext portal or
`SslErrorHandler.proceed()` exists. The configuration is not an OS trust-store
change and is excluded from release builds with the debug-only probe.

Public certificate provenance, SHA-256 and pins are recorded in
`../vtop_certificate_provenance.json`. If the issuer changes, fail closed and
reverify the new chain before updating the configuration. Production College
integration must revisit this decision when its live dataset gate opens.

The previous page-loaded assertion was insufficient: finishing an error page was
reported as successful. Errors now survive page-finished callbacks. The optional
public-page test checks a usable viewport and actual rendered pixels, not only
callbacks/DOM counts. Initial white viewport had 0/23,552 sampled nonwhite pixels;
after the verified chain repair it had 9,866/23,552. A native full-screen debug host
keeps the portal independent of the app's presentation and protects owner login
with FLAG_SECURE. Only an isolated demo public-page test may opt out of that flag;
it never authenticates. Public screenshots stay in ignored tooling output.

Official platform contract:
https://developer.android.com/privacy-and-security/security-config .
No authenticated account or academic data was used for this diagnosis.

## Addendum - 13 November 2026 (I04 production connector)

The same exact-host configuration (`vtopcc.vit.ac.in` only, system roots plus the
verified Sectigo R36 intermediate, R36/R46 SPKI pins, cleartext off) moved from the
debug source set to `app/src/main/res` because the production `CollegeConnectActivity`
needs it. Scope, pins, TLS-failure cancellation and rotation/rollback are unchanged.
Release builds now include this config; the debug probes remain debug-only.
