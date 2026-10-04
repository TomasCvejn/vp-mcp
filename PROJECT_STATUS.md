# PROJECT_STATUS.md

## Current State: Custom MCP Server (Java 11, Undertow)

### Overview
Replaced Spring Boot/Spring AI MCP stack with a custom lightweight MCP server using Undertow HTTP + Jackson. The server runs on Java 11 (compatible with Visual Paradigm's JVM) and implements the MCP JSON-RPC protocol over SSE transport.

### Architecture

- **MCP Server**: Custom `McpServer.java` using Undertow embedded HTTP server
- **Transport**: SSE (Server-Sent Events) - GET `/sse` for event stream, POST `/mcp/messages` for JSON-RPC
- **Protocol**: MCP JSON-RPC 2.0 (initialize, tools/list, tools/call)
- **Tool Discovery**: Custom `@Tool` annotation + Java reflection (replaces Spring AI)
- **Port**: 2026 (configurable)

### MCP Tool Services (62 tools total)

| Category | Tools | Count |
|----------|-------|-------|
| Management | listDiagrams, getDiagramElements, autoLayoutDiagram, removeDiagramElement, getElementCounts, addStereotype, checkLayout, renameElement, setElementBounds, rerouteConnectors, exportDiagramImage, getRelationshipDetails | 12 |
| Use Case | create, addActor, addUseCase, addRelationship, removeUseCaseElement, removeUseCaseRelationship, nameExtensionPoint, nameUseCaseRelationship, addSystemBoundary, layoutUseCaseDiagram, buildUseCaseDiagram, checkUseCaseDiagram, deleteUseCaseDiagram, generateReport | 14 |
| Class | create, addClass, addAttribute, addOperation, addAssociation, addGeneralization, addAggregation, addComposition, addDependency, addRealization, addInterface, addPackage, setClassColor, generateReport, addStereotypeToClasses, removeRelationship, setAssociationProperties, layoutConnectorLabels | 18 |
| Project | newProject, saveProject, saveProjectAs, getProjectInfo | 4 |
| ERD | create, addTable, addColumn, addForeignKey, addTableRelationship, generateDdl, generateReport | 7 |
| Sequence | create, addLifeline, addActivation, addMessage, addReturnMessage, addCombinedFragment, generateReport | 7 |

### Reference-style rendering (matches the course's Visual Paradigm samples)

- **Blue fill (#7AD2FF)** is applied automatically to actors, use cases, lifelines and
  activation bars (use-case and sequence diagrams) via `applyConventionalFill`. Class boxes and
  ERD tables keep Visual Paradigm's default white — matching the reference exports in
  `exports/services/screenshots` and `exports/account/screenshots`.
- **Association navigability**: `addRelationship` now pins BOTH ends of every actor↔use-case link
  explicitly (VP defaults a fresh end to navigable). A plain `Association` gets both ends
  `NAVIGABLE_UNSPECIFIED` (no arrows); a `DirectedAssociation` keeps the target end navigable. This
  was a regression after plain `Association` became the default link: leaving the plain association
  at VP's default made its actor end read as navigable, so `addSystemBoundary` misclassified every
  primary actor as secondary and pushed them all to the right column. Verified live: a diagram with
  plain actor links now places primary actors left, secondary (directed-to) actors right.
- **`addSystemBoundary(diagramName, systemName)`** wraps all use cases of a UC diagram in a
  labeled system rectangle (the module box). Call it AFTER `autoLayoutDiagram` so the box encloses
  the laid-out use cases; it computes the use-case bounding box, reparents the use cases into an
  `ISystem`, and sends the rectangle to back. Actors stay outside the box.
- **Connector re-anchoring**: after `addSystemBoundary` moves the actors, it re-centers every
  association connector (`centerConnector`, now shared in `AbstractDiagramMcpTools`) so the arrows
  follow the actor to its new position. In a UC diagram associations are exactly the
  actor↔use-case links, so include/extend/generalization keep their laid-out routing untouched.
- **Actor vertical alignment**: when placing actors beside the box, each actor is positioned at the
  average vertical center of the use cases it is associated with (primary actors left, secondary
  right), instead of being naively stacked from the top. This keeps association lines short and
  mostly un-crossed. Overlapping actors on the same side are spread apart by the pure, unit-tested
  `UseCaseMcpTools.stackYs` helper (see `UseCaseLayoutTest`).

### Iterative diagram fixing with review agents (2026-10-04)

Loop: edit via MCP → `exportDiagramImage` → `diagram-reviewer` + `visual-reviewer` agents in
parallel → fix → repeat. Plugin fixes found along the way (verified live on "SmartTaxIS Use Cases"):

- **`addSystemBoundary` is idempotent**: it reuses the diagram's existing `ISystem` boundary
  instead of creating a new one (repeated calls used to stack duplicate boxes), moves use cases
  and child shapes of further boundaries into it and deletes those once empty.
- **`removeDiagramElement`** detaches a nested shape from its parent first and reports a failure
  when the element is still on the diagram (it used to claim "Removed" for boundaries).
- **Captions follow moved shapes**: `setElementBounds`, actor placement in `addSystemBoundary`
  and `centerConnector` call `resetCaption()` (`setRequestResetCaption` alone had no effect), so
  actor names and «include»/«extend» labels no longer stay at the old position.
- **C1 actor fans**: `centerConnector` sets actor shapes to
  `CONNECTION_POINT_TYPE_CENTER`, so all of an actor's lines aim at its center. VP ignores explicit
  connector end points (and rewrites them on render), so a literal single start point is not
  possible; `review/usecase-conventions.md` C1 was clarified accordingly. Actor shapes of 40x60
  (instead of 160x60) make lines reach the figure.
- **`getRelationshipDetails`** works on any diagram type (connector points, diffs, captions).
- **Stereotypes** via the generic `addStereotype(diagram, element, stereotype)`: «system» for
  actors that are other systems (catalog §1.15), «time» for the Time actor (convention C4).
- **`exportDiagramImage`**: diagram-not-found is checked before the activation retry loop
  (SpotBugs `NP_BOOLEAN_RETURN_NULL`).
- **`checkLayout(diagramName)`** (any diagram type): geometric check for overlapping shapes
  (exact ellipse geometry for use cases), shapes straddling a system boundary/package edge, lines
  through a shape they do not connect (2 px graze tolerance) and crossing lines (lines sharing a
  shape are exempt, e.g. an actor's fan). Pure logic in `LayoutCheck` (`LayoutCheckTest`). VP
  routes connectors only when it draws a diagram, so `checkLayout` first renders it (export to a
  temp PNG, deleted) instead of relying on the caller to export first. Verified live: `OK` on the clean
  SmartTaxIS diagram, exactly the 3 planted problems after moving shapes. The `visual-reviewer`
  agent treats its output as ground truth for overlaps/crossings/lines-through-shapes.
- **`layoutUseCaseDiagram(diagramName, systemName)`**: one-call house-style layout. Pure planner
  `UseCaseGrid` (`UseCaseGridTest`) puts use cases on a grid: column = include/extend depth;
  primary actors walked in order with a generalization child right after its parent and an actor
  whose use case depends on another actor's use case right after that actor; use cases another
  actor depends on go last in their actor's rows; an actor-linked deeper use case gets its own row
  with the cells left of it empty; other dependents take the nearest usable row to their base
  (never a cell on a line to a primary actor on the left or to a secondary actor on the right).
  Then `addSystemBoundary` and re-anchoring of all lines. Verified live: SmartTaxIS scrambled with
  `autoLayoutDiagram`, then one `layoutUseCaseDiagram` call → `checkLayout` `OK`.
- **`addSystemBoundary`** recognizes «system» in any case via the shared `isSecondary` helper.
  Actors stay aligned with the average of their use cases: with C1 clarified (lines aim at the
  center), a line VP clips at the name caption still belongs to the actor's fan.
- **`getRelationshipDetails`** also reports each association end's navigability.
- **`removeDiagramElement` dissolves a system boundary**: VP keeps a boundary shape while its
  `ISystem` exists, so the shapes are moved onto the diagram, the use cases out of the system (to
  its owner, or top level), each is checked to still exist, and only an empty system is deleted.
- **`renameElement(diagram, element, newName)`** (any diagram): refuses a name already used on the
  diagram (tools look elements up by name) and updates a boundary's custom caption too. Verified
  live on SmartTaxIS: boundary dissolved (14 use cases, 8 actors, 18 links kept) and restored,
  actor and boundary renamed and back, duplicate name refused, `checkLayout` OK. Re-targeting a
  relationship was skipped: remove + add loses nothing for use case links.
- **Generic tools live in `AbstractDiagramMcpTools`**: `setElementBounds`, `rerouteConnectors`,
  `exportDiagramImage`, `getRelationshipDetails` and the project tools moved out of
  `ClassDiagramMcpTools` (names unchanged; the server registers each tool name once). Only
  class-specific tools remain in `ClassDiagramMcpTools`.
- **`checkLayout` checks captions drawn outside their shape** (an actor's name below the figure):
  they count as boxes that must not overlap other shapes/captions or carry foreign lines (the
  actor's own lines are exempt). VP's caption coordinates depend on the side: outside captions
  (`SIDE_NORTH/EAST/SOUTH/WEST/FREEMOVE`) are absolute, inside ones (`SIDE_CENTER`, `INSIDE*`) are
  relative to the shape and are skipped (`outsideCaption`). `getDiagramElements` lists outside
  captions ("caption at (x,y) size wxh"). Verified live on SmartTaxIS: clean diagram OK; moving
  Payment Provider 25 px up into Traffic Provider's «system» caption reported
  "overlap: 'caption of Traffic Provider' and 'Payment Provider'".
- **Compact use case rows**: `layoutUseCaseDiagram` puts rows 90 px apart (was 110; 30 px between
  ellipses), so tall diagrams are ~16 % shorter (Wolt boundary 1460 -> 1220 px). Actors stack with
  a 30 px gap so one-row-apart actors stay level with their use cases; an actor with a stereotype
  reserves one more caption line (15 px), otherwise the next actor's head touched its «system»
  caption (seen on SmartTaxIS; checkLayout does not check captions). A real second use case column
  was rejected: lines from left-side primary actors (C3) would cross the first column.
- **«include»/«extend» labels**: VP put every label below-right of its line's midpoint, so one
  could sit between two lines (reviewers flagged it as ambiguous). `layoutUseCaseDiagram` now
  renders the diagram (shared `renderDiagram`: export to a temp PNG; routes and caption sizes are
  only known then) and moves each label beside the middle of its own line, on the side farther
  from the other lines (pure `LayoutCheck.labelSpot`, tested). Caption bounds are absolute diagram
  coordinates; verified live that VP keeps them on later renders.
- **Optional tool parameters**: the input schema used to mark every parameter required, so strict
  MCP clients had to send values for optional ones (elementType, discardChanges, addClass
  options, multiplicities, ...). A parameter annotated `@OptionalParam` (tool package) is now left
  out of "required"; omitted it arrives as null / 0 / false, as `ToolDefinition.execute` already
  did. 17 tools annotated, only where the code has a sensible default (not `isNullable` in
  addColumn, whose silent default would mean NOT NULL). `ToolDefinitionTest` covers schema and
  binding.
- **`buildUseCaseDiagram(diagramName, systemName, spec)`**: a whole use case diagram in one call
  instead of ~45. `spec` is JSON (actors, stereotypes, useCases, links = primary actor -> use
  case, calls = use case -> secondary actor, includes, extends with optional extension point,
  generalizations), parsed and validated as a whole by the pure `UseCaseSpec` (`UseCaseSpecTest`):
  every problem is listed and nothing is created. Then the single-step tools build it,
  `layoutUseCaseDiagram` lays it out and `checkLayout` is appended. Verified live: a bad spec
  listed 3 problems and created no diagram; the 16-use-case Wolt diagram was built in one call,
  `checkLayout` OK, identical to the hand-built one.
- **Wolt diagram (wolt.vpp) fixes**: actors are created 40x60 (their stick figure;
  `DiagramLayoutEngine.ACTOR_WIDTH`) and `addSystemBoundary` normalizes actor width, so arrows to
  secondary actors no longer stop short (C2); a freshly created boundary gets `resetCaption()`
  (its name was not shown); `UseCaseGrid` orders actors by number of use cases (most first, then
  by name) and use cases by name, so the layout no longer depends on VP's element order, which
  changes between sessions (the same diagram passed `checkLayout` before a VP restart and failed
  after it). Generate-and-test over actor orders was considered and dropped as too big.
- **One name lookup**: `findElement(diagram, name, types...)` in `AbstractDiagramMcpTools` returns
  the single element (shape and model together) named so on the diagram and throws when none or
  several different elements match; tools report the message (e.g. "2 elements named 'Payment' on
  diagram 'A' (Actor, UseCase); rename one with renameElement(..., elementType)"). It replaces
  `findDiagramElementByName` (untyped, first match: model and shape could belong to different
  same-named elements), `findDiagramElementByModel` (name fallback) and the cross-diagram fallback
  of `findModelElement` (with a diagram given it now never leaves it; without one it requires a
  unique name in the project). Fixed by it: `removeUseCaseElement` could delete a same-named
  element from another diagram; `addClass` silently skipped an unknown/ambiguous extends/implements
  (now resolved before anything is created); relationship and lifeline lookups validate their
  names. `renameElement` takes an optional `elementType` to resolve a clash. Verified live in a
  scratch project (ambiguous link refused, other-diagram delete refused, rename by type, extends of
  a missing class refused with nothing created). Note: VP's `createUseCase()` itself adds a
  hidden «UseCase» stereotype.
- **Ponytail audit cuts**: removed the undocumented Docker proxy mode (`StandaloneServer`,
  `ProxyToolDefinition`, `/api/tools` + `/api/execute`, Dockerfile, compose, stub generator) and
  `test_connect.py`; MCP clients connect straight to `http://localhost:2026/sse`. The server now
  binds to 127.0.0.1 (it bound 0.0.0.0 for the container; `/api/execute` ran any tool without
  auth). Single-caller `ClassDiagramUtils`/`ErdUtils`/`SequenceDiagramUtils` inlined into their
  tool classes; `getSemanticTypeName` uses `getModelType()` with 4 renames (checked against the
  `IModelElementFactory.MODEL_TYPE_*` constants); dead helpers and `./run` help entries removed.
- **`getDiagramElements`** lists stereotypes (`Actor: Time «time»`) and where an association draws
  arrowheads (`{arrow at: Payment Provider}`); unknown element types use `getModelType()`
  (`System: SmartTaxIS`) instead of VP's obfuscated class name (`dgz`), also in getElementCounts.
- **`addRelationship` description**: it used to give "actor->use case" as an example for
  `DirectedAssociation`, so generated diagrams drew arrowheads at use cases for primary actors.
  Now: plain `Association` always for a primary actor, `DirectedAssociation` only use case ->
  secondary actor (verified via the new navigability dump; SmartTaxIS had all ends `navigable`).
- **Tests run again**: `./run test` and `./run all` execute the JUnit 4 suite (the main build had
  `skipTests=true` and unused JUnit 5/Mockito made surefire pick the JUnit 5 provider, finding 0
  tests). `./run install` stays quick with `-DskipTests`. JaCoCo's 85 % instruction coverage rule
  applies to the VP-free classes only (`LayoutCheck*`, `UseCaseGrid*`); add a pure class there
  together with its tests. VP API glue is verified live.
- **Dev-cycle guards**: `getProjectInfo` reports `modified` (unsaved changes) and
  `plugin.{loadedAt, installedAt, stale}`; `stale: true` means a newer build is installed than the
  one running, so restart Visual Paradigm. `newProject(discardChanges)` refuses to drop unsaved
  changes unless `discardChanges` is true. Verified live (VP may report a freshly opened project
  as modified; after `saveProject` it is false and read-only tools keep it false).
- **`usecase-diagram` skill** (`.claude/skills/usecase-diagram/SKILL.md`), written from a real
  run (bike sharing): guard → spec (catalog rules) → `buildUseCaseDiagram` → export →
  `diagram-reviewer` + `visual-reviewer` in parallel → fix loop. Model findings are fixed in
  the spec and rebuilt; layout findings by hand (`setElementBounds` with 160x60, else it shrinks
  the ellipse; then `rerouteConnectors`, lines do not follow a moved shape); requirement
  questions go to the user. One shared `diagram-reviewer` for all types; a skill per type.
- **Shared actors/use cases**: VP refuses a second same-named element of a type and silently
  kept its default name ("Actor2"), while `addActor`/`addUseCase` reported success, so a second
  diagram with e.g. `Time` could not be built. They now show the project's existing element
  (UML: one actor, many diagrams); `addToDiagram` throws when VP refuses a name (all diagram
  types). `removeUseCaseElement` only removes a shared element (and its relationships) from the
  given diagram. `buildUseCaseDiagram` accepts a stereotype a shared actor already has.
- **`buildUseCaseDiagram(..., replace)`**: `replace=true` deletes the existing diagram first
  (boundary dissolved, so its system cannot take shared use cases along), so a corrected spec
  is rebuilt instead of patched; a failed build no longer blocks its name.
- **`UseCaseGrid`**: a dependent whose base row is taken by a use case with another base below
  goes above first (bike sharing: Report Damage above Process Payment, which Charge
  Subscription includes from far below; checkLayout reported the crossing).
- **Transport**: deprecated `exchange.dispatch()` + a new single-thread executor per request
  replaced by `dispatch(BLOCKING, task)` on one shared cached pool. Checkstyle warnings in test
  names fixed; the build is warning-free.
- Verified live: shared `Time` (with its «time») built on a second diagram, SmartTaxIS
  unchanged (`checkLayout` OK), `replace` rebuilt a partly built diagram; after the planner fix
  the bike sharing spec builds with `checkLayout` OK and no manual move, over the new transport.
- **Server instructions**: `initialize` returns `instructions` (`McpServer.INSTRUCTIONS`, tested
  in `McpServerTest`), which MCP clients show to the model next to the 60 tools: getProjectInfo
  guard, a use case diagram is one `buildUseCaseDiagram` (rebuilt with `replace=true`), then
  `exportDiagramImage` + `checkLayout`, `rerouteConnectors` after manual moves. Verified live: after
  a reconnect Claude Code shows them as the server's instructions.
- **Spec checks** (`UseCaseSpec`, `UseCaseSpecTest`): a cycle in includes/extends or in
  generalizations is a problem (nothing is created). The spec warnings that followed were
  replaced by `checkUseCaseDiagram` (below), which checks the built diagram instead. Verified live: a cycle spec created nothing; a spec with six
  planted mistakes was built (`checkLayout` OK) and listed exactly those six warnings.
- **Generic `diagram-reviewer`**: its hard-coded use case checklist is gone (it had drifted: no
  C4, and it cited §1.10 and §1.14, which `review/usecase.md` does not contain). The agent walks
  every numbered section of `review/<type>.md` and every `C<n>` of the conventions file, so a
  new diagram type needs only its handbook. `review/usecase.md` stays a 1:1 transcription.
  `review/usecase-checklist.md` (from the user): Syntax/Best practices/Semantics/Style items
  with ids (SYN1, BP1, SEM1, STY1) and a severity per group; the reviewer reads
  `review/<type>-checklist.md` too and cites those ids.
  §1.10 (textual specification, Figures 19–26) and §1.14 (Figures 32–33) are left out of the
  handbook on purpose (user's decision); its intro now says so.
- **Skill flow**: the AI lists the assumptions it made and asks the user about the ones that
  change the spec, in one question, before building (one question instead of a review round);
  `diagram-reviewer` gets the requirements (optional input: every requirement covered, nothing
  invented, ambiguities reported as questions); `visual-reviewer` runs only when `checkLayout`
  is not OK (with clean geometry it found nits only in three runs).
- **`UseCaseGrid`**: a use case also linked to another primary actor goes last in its owner's
  rows (fitness center: Register Membership, linked to Member and Receptionist, was between
  Member's use cases and Receptionist's line crossed Member's line to Reserve Class; checkLayout
  and both reviewers reported it). Verified live: the same spec rebuilt with `checkLayout` OK.
- **`checkUseCaseDiagram(diagramName)`**: the checklist items a use case diagram's model
  answers for sure, so the LLM reviewer stops estimating them from pixels (it had miscounted
  use cases and rated the same issue nit in one run, warn in the next). Pure `UseCaseCheck`
  (`UseCaseCheckTest`, in the JaCoCo rule): exact counts; ground truth for SYN1 (inside one
  boundary), SYN5 (named extension points), BP1/BP2 (one named system), C3 (primary left,
  secondary right, by arrowheads), C4 (Time has «time»), §1.11 (include with one base); it also
  reports an arrowhead at a use case or at both ends (§1.5), an actor named System or like the
  boundary (§1.6) and elements without relationships. `buildUseCaseDiagram` appends it; the
  skill passes its output to `diagram-reviewer`, which reports its problems and does not
  contradict it on those ids. SYN1 is decided by geometry (use case shape wholly within the
  boundary rectangle): use case shapes are not child shapes of the boundary (`addSystemBoundary`
  only adds them to the `ISystem` model and draws the box around them), and the model owner is
  wrong for a shared use case. VP gives a new extend an extension point
  named "ExtensionPoint" (then "ExtensionPoint2", ...), which counts as unnamed for SYN5. VP's
  `toStereotypeModelArray()` returns null, not an empty array, for an element without
  stereotypes (the first live run failed on it).
  Verified live: OK with exact counts on Bike Sharing, City Library and Fitness Center; Lint
  Test listed exactly its planted problems; SmartTaxIS showed three real §1.11 findings no
  reviewer had reported; after moving Payment Gateway left and View Statistics out of the box it
  reported exactly C3 and SYN1, and `diagram-reviewer` took both and its counts from it, adding
  only what it judged from the picture (the lines left behind by the moves).
- **Layout polish** (from reviewer nits in the test runs): `buildUseCaseDiagram` passes the
  spec's use case order to the planner, so rows follow it ("Reserve Class" before "Cancel
  Reservation"); `layoutUseCaseDiagram` alone still sorts by name (VP's element order changes
  between sessions). A base use case with extension points gets at least 200x80 (VP's fitted
  ~180x62 looked cramped, STY1). The "Visual Paradigm Standard(...)" text at the top left of
  exports is the edition's licence watermark: neither the empty `setWatermark` nor
  `setImageMargin(Default)` removes or moves it (tried live), so it stays. `deleteUseCaseDiagram`
  exposes the delete
  behind `replace=true` (boundary dissolved, shared elements stay on other diagrams).

### Class diagram editing, audit and project tools (server version 1.27.8)

- **Scoped lookups**: `addAttribute(..., diagramName)` only uses the class shown on that diagram, so
  same-named classes in other diagrams/packages are never modified. `addClass` accepts `x`, `y` and
  `modelPackage` (model-only package, no package shape) to keep same-named classes apart.
- **Generalization direction**: VP stores a generalization as from = general (parent), to =
  specific (child). `addGeneralization(fromClass = child, toClass = parent)` and
  `addClass(extendsClass)` now create it that way, so the triangle is drawn at the parent.
- **Connector anchoring**: with null points VP anchors connector ends at the shapes' top-left
  corners. Class connectors are created with explicit center points; `rerouteConnectors` re-anchors
  all connectors after moving shapes. VP then draws an axis-aligned line through the middle of the
  shapes' overlap, or a center-to-center diagonal when they do not overlap. Open (or export) a
  diagram before rerouting it after a restart, otherwise the ends fall back to the corners.
- **Labels**: `layoutConnectorLabels` places multiplicities next to each end (absolute diagram
  coordinates), the association name mid-line and hides role names; ends leaving a shape in a fan
  are staggered. Shape wrappers returned by the API are not canonical objects, compare them by id.
- **Data-model helpers**: `addStereotypeToClasses(diagram, "*", "ORM Persistable")`,
  `removeRelationship` (deletes the model element), `setAssociationProperties` (edits
  association/aggregation/composition in either direction; role `-` clears a role name; VP's ORM
  support may auto-name roles of associations created between persistable classes).
- **Audit/export**: `getRelationshipDetails` returns JSON (classes with abstract flag, stereotypes,
  owner, attributes, bounds; relationships with both ends' multiplicity, aggregation kind, role,
  connector points and label rectangles). `exportDiagramImage` writes a PNG with an empty
  watermark.
- **Project**: `newProject`, `saveProject`, `saveProjectAs` (never overwrites an existing file) and
  `getProjectInfo` (name and file path, useful as a guard before editing).

### Key Files

| File | Purpose |
|------|---------|
| `McpServer.java` | Undertow-based MCP server with SSE transport |
| `tool/Tool.java` | Custom `@Tool` annotation |
| `tool/ToolDefinition.java` | Reflection-based tool scanning + JSON Schema generation (hierarchy-aware) |
| `VPMcpPlugin.java` | VP plugin entry point, registers tools with McpServer |
| `tools/AbstractDiagramMcpTools.java` | Base class with zone-aware positioning, layout, and management tools |
| `tools/UseCaseMcpTools.java` | 11 use case diagram tools |
| `tools/ClassDiagramMcpTools.java` | 18 class diagram tools (generic and project tools live in the base class) |
| `tools/ErdMcpTools.java` | 7 ERD tools |
| `tools/SequenceDiagramMcpTools.java` | 7 sequence diagram tools |
| `util/DiagramUtils.java` | Shared VP API helpers (diagram/element lookup) |
| `util/DiagramLayoutEngine.java` | Zone-aware positioning + parameterized VP LayoutOption construction |

### Dependencies

- **Jackson 2.17.2** - JSON parsing
- **Undertow 2.2.30.Final** - Embedded HTTP server
- **VP OpenAPI 17.2** - Visual Paradigm plugin API (system scope)
- **Java 11** - Target runtime

### MCP Endpoints

- **SSE**: `http://localhost:2026/sse` - Establish SSE connection, returns session ID (bound to
  127.0.0.1 only: the tools edit and save the open project)
- **Messages**: `http://localhost:2026/mcp/messages?sessionId=<id>` - Send JSON-RPC requests

### Verified

- [x] Custom MCP server compiles and runs on Java 11
- [x] SSE transport works (endpoint event, keep-alive, session management)
- [x] MCP protocol: initialize, tools/list, tools/call
- [x] 35 tools registered and invocable via JSON-RPC
- [x] VP plugin loads successfully (verified in VP log)
- [x] Connectors use `createConnector()` with IDiagramElement refs (not `createDiagramElement`)
- [x] Diagram management tools: listDiagrams, getDiagramElements, autoLayoutDiagram, removeDiagramElement, getElementCounts
- [x] Zone-aware element positioning (actors left, UCs right; boundary/DAO/entity layers)
- [x] Parameterized VP LayoutOption: Hierarchical (UC/Class/Sequence), SmartOrganic (ERD)
- [x] UC diagram span reduced from 2300px to ~260px
- [x] Class diagram span reduced from 3375px to ~205px
- [x] Rich verification: getDiagramElements shows class attrs/ops, table columns, lifeline classifiers, connector from->to
- [x] Rich reports: generateUseCaseReport/generateClassReport/generateErdReport/generateSequenceReport show element names and details
- [x] UC addRelationship supports IActor + Association type + diagramName param
- [x] addForeignKey resolves column references via setIndexColumn
- [x] findDiagramElementByModel uses object identity instead of name matching
- [x] findOrCreateActivation returns last (most recent) activation
- [x] addCombinedFragment warns about unfound lifelines
- [x] New class/project tools exercised end-to-end in VP 18.1: two e-commerce class diagrams built,
  saved with saveProjectAs into three files, converted to data models, and checked against the
  saved .vpp files (SQLite) with zero differences
