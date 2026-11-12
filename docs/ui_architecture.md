# Keeping the UI replaceable

Functionality is the current priority. The owner has requested a later visual
redesign; existing screens are functional scaffolding, not approved final design.

Shared colors, shapes, cards and status presentation live in
`core/designsystem/src/main/kotlin/dev/personalhub/design/Design.kt`. The app owns
navigation/composition. Pure domain models/calculations live outside Android UI;
Room and platform integrations stay in their data/integration modules. Feature
screens will consume public repository interfaces and immutable ViewModel states.
Changes to typography, layouts, cards, navigation presentation or charts must not
change data schemas or business calculations.

The VTOP debug host is separate from its WebView/session policy. Its temporary
native layout can be replaced with final College presentation without rewriting
origin checks, privacy boundaries or the later validated parser contracts. It is
not the intended final College screen. No live portal CSS or proprietary branding
is copied into native application components.

For later redesign: adjust shared theme tokens first, then feature presentation
and app navigation layouts. Reuse repository/domain tests; rerun UI accessibility,
font-size and navigation checks. Preserve stable IDs, persistence and source-state
semantics while changing appearance.
