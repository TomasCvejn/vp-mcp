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

### MCP Tool Services (59 tools total)

| Category | Tools | Count |
|----------|-------|-------|
| Management | listDiagrams, getDiagramElements, autoLayoutDiagram, removeDiagramElement, getElementCounts, addStereotype, checkLayout, renameElement | 8 |
| Use Case | create, addActor, addUseCase, addRelationship, removeUseCaseElement, removeUseCaseRelationship, nameExtensionPoint, nameUseCaseRelationship, addSystemBoundary, layoutUseCaseDiagram, generateReport | 11 |
| Class | create, addClass, addAttribute, addOperation, addAssociation, addGeneralization, addAggregation, addComposition, addDependency, addRealization, addInterface, addPackage, setClassColor, generateReport, setElementBounds, addStereotypeToClasses, removeRelationship, setAssociationProperties, getRelationshipDetails, rerouteConnectors, layoutConnectorLabels, exportDiagramImage | 22 |
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
  shape are exempt, e.g. an actor's fan). Pure logic in `LayoutCheck` (`LayoutCheckTest`); run it
  after `exportDiagramImage` so connector routes are current. Verified live: `OK` on the clean
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
| `StandaloneServer.java` | Standalone entry for Docker (no VP dependency) |
| `tools/AbstractDiagramMcpTools.java` | Base class with zone-aware positioning, layout, and management tools |
| `tools/UseCaseMcpTools.java` | 5 use case diagram tools |
| `tools/ClassDiagramMcpTools.java` | 22 class diagram tools + 4 project tools |
| `tools/ErdMcpTools.java` | 7 ERD tools |
| `tools/SequenceDiagramMcpTools.java` | 7 sequence diagram tools |
| `util/DiagramUtils.java` | Shared VP API helpers (diagram/element lookup) |
| `util/DiagramLayoutEngine.java` | Zone-aware positioning + parameterized VP LayoutOption construction |

### Dependencies

- **Jackson 2.17.2** - JSON parsing
- **Undertow 2.2.30.Final** - Embedded HTTP server
- **VP OpenAPI 17.2** - Visual Paradigm plugin API (system scope)
- **Java 11** - Target runtime

### Docker

```bash
./run docker-build   # Build Docker image (Java 11)
./run docker-up      # Start MCP server on port 2026
./run docker-down    # Stop MCP server
./run docker-logs    # View server logs
```

Docker uses multi-stage build with VP API stub JAR for compilation.

### MCP Endpoints

- **SSE**: `http://localhost:2026/sse` - Establish SSE connection, returns session ID
- **Messages**: `http://localhost:2026/mcp/messages?sessionId=<id>` - Send JSON-RPC requests

### Verified

- [x] Custom MCP server compiles and runs on Java 11
- [x] SSE transport works (endpoint event, keep-alive, session management)
- [x] MCP protocol: initialize, tools/list, tools/call
- [x] 35 tools registered and invocable via JSON-RPC
- [x] Docker build succeeds with Java 11
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
