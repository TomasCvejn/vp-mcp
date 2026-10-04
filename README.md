# Visual Paradigm MCP Plugin

A Model Context Protocol (MCP) server plugin for Visual Paradigm that provides
AI applications with seamless integration to Visual Paradigm's modeling and
diagramming modeling capabilities. The MCP server is embedded directly within
the Visual Paradigm plugin, starting automatically when the plugin loads.

## Architecture

- **Undertow + Jackson**: Embedded HTTP server and JSON processing for the MCP server
- **Visual Paradigm 17.2**: UML modeling platform integration
- **Maven**: Build automation and dependency management
- **MCP Transport**: SSE (Server-Sent Events) communication with MCP clients
- **Visual Paradigm Plugin API**: Integration with Visual Paradigm's modeling capabilities
- **JUnit & Mockito**: Comprehensive testing framework

## Features

### MCP Server Integration

The plugin includes an embedded MCP server that:

- **Auto-starts** when Visual Paradigm plugin is loaded
- **Auto-stops** when Visual Paradigm plugin is unloaded
- Runs on **port 2026** with SSE stream at `/sse` and messages at `/mcp/messages`
- Provides **tool capabilities** for external MCP clients

#### Available MCP Tools (59 total)

##### Diagram Management (12 tools)
- **listDiagrams()**: List all diagrams in the project
- **getDiagramElements(diagramName)**: List elements of a diagram with details
- **autoLayoutDiagram(diagramName)**: Apply automatic layout to a diagram
- **removeDiagramElement(diagramName, elementName)**: Remove an element from a diagram (a system boundary is dissolved, its use cases stay)
- **renameElement(diagramName, elementName, newName, elementType)**: Rename an element (refuses a name already used on the diagram; optional elementType, e.g. Actor, picks one of several same-named elements)
- **getElementCounts(diagramName)**: Count elements by type in a diagram
- **addStereotype(diagramName, elementName, stereotype)**: Apply a stereotype (e.g. System, Time) to any element
- **checkLayout(diagramName)**: Geometric check for overlapping shapes, lines through shapes and crossing lines (renders the diagram first, so routes are current)
- **setElementBounds(diagramName, elementName, x, y, width, height)**: Set element position and size
- **rerouteConnectors(diagramName)**: Re-anchor all connectors after moving shapes
- **exportDiagramImage(diagramName, filePath)**: Export a diagram to a PNG image
- **getRelationshipDetails(diagramName)**: JSON audit of any diagram: classes, relationships with both ends (multiplicity, navigability), connector points and labels

##### Use Case Diagram (11 tools)
- **createUseCaseDiagram(diagramName)**: Create new use case diagrams
- **addActor(actorName, diagramName)**: Add actors to specific diagrams
- **addUseCase(useCaseName, diagramName)**: Add use cases to diagrams
- **addRelationship(sourceName, targetName, relationshipType)**: Create Include/Extend/Generalization/Association/DirectedAssociation relationships (plain Association for primary actors; DirectedAssociation only use case → secondary actor)
- **removeUseCaseElement(diagramName, elementName)**: Delete an actor or use case (and its relationships) from the model
- **removeUseCaseRelationship(diagramName, sourceName, targetName, relationshipType)**: Delete a relationship between two elements from the model
- **nameExtensionPoint(diagramName, extendingUseCase, baseUseCase, name)**: Name the extension point of an Extend relationship
- **nameUseCaseRelationship(diagramName, sourceName, targetName, relationshipType, name)**: Name a relationship
- **addSystemBoundary(diagramName, systemName)**: Wrap the use cases in a labeled system boundary (reuses an existing one)
- **layoutUseCaseDiagram(diagramName, systemName)**: House-style layout in one call: use case grid, boundary, actors, lines
- **generateUseCaseReport(diagramName)**: Generate use case analysis report

##### Class Diagram (18 tools)
- **createClassDiagram(diagramName)**: Create new class diagrams
- **addClass(diagramName, className)**: Add classes to diagrams
- **addAttribute(className, attributeName, attributeType, visibility)**: Add attributes to classes
- **addOperation(className, operationName, returnType, params)**: Add operations/methods to classes
- **addAssociation(diagramName, fromClass, toClass, fromMultiplicity, toMultiplicity, name)**: Add associations
- **addGeneralization(diagramName, fromClass, toClass)**: Add inheritance relationships
- **addAggregation(diagramName, fromClass, toClass, fromMultiplicity, toMultiplicity)**: Add aggregation
- **addComposition(diagramName, fromClass, toClass, fromMultiplicity, toMultiplicity)**: Add composition
- **addDependency(diagramName, fromClass, toClass)**: Add dependency relationships
- **addRealization(diagramName, fromClass, toClass)**: Add interface realization
- **addInterface(diagramName, interfaceName)**: Add interfaces with stereotype
- **addPackage(diagramName, packageName)**: Add packages to diagrams
- **setClassColor(diagramName, className, color)**: Set a class box fill color
- **addStereotypeToClasses(diagramName, classNames, stereotype)**: Apply a stereotype to classes
- **removeRelationship(diagramName, fromClass, toClass)**: Remove a relationship model element
- **setAssociationProperties(...)**: Edit association/aggregation/composition ends and roles
- **layoutConnectorLabels(diagramName)**: Position multiplicity and association-name labels
- **generateClassReport(diagramName)**: Generate class diagram analysis report

##### Project (4 tools)
- **newProject(discardChanges)**: Create a new, empty project (refuses to drop unsaved changes unless `discardChanges` is true)
- **saveProject()**: Save the current project
- **saveProjectAs(filePath)**: Save the project to a new file (never overwrites an existing file)
- **getProjectInfo()**: Return the project name, file path and unsaved-changes flag, plus whether the running plugin build is stale (restart Visual Paradigm)

##### ERD - Entity Relationship Diagram (7 tools)
- **createErd(diagramName)**: Create new ER diagrams
- **addTable(diagramName, tableName)**: Add tables to ER diagrams
- **addColumn(tableName, columnName, columnType, length, scale, isPrimaryKey, isNullable)**: Add columns
- **addForeignKey(diagramName, fromTable, toTable, fromColumn, toColumn, relationshipName)**: Add FK relationships
- **addTableRelationship(diagramName, fromTable, toTable, type, fromMultiplicity, toMultiplicity)**: Add identifying/non-identifying relationships
- **generateDdl(diagramName)**: Generate CREATE TABLE DDL statements
- **generateErdReport(diagramName)**: Generate ERD analysis report

##### Sequence Diagram (7 tools)
- **createSequenceDiagram(diagramName)**: Create new sequence diagrams
- **addLifeline(diagramName, lifelineName, className)**: Add lifelines (participants)
- **addActivation(diagramName, lifelineName)**: Add activation bars
- **addMessage(diagramName, fromLifeline, toLifeline, messageName, sequenceNumber, messageType)**: Add sync/async messages
- **addReturnMessage(diagramName, fromLifeline, toLifeline, messageName, sequenceNumber)**: Add return messages
- **addCombinedFragment(diagramName, operator, guard, coveredLifelines)**: Add alt/opt/loop fragments
- **generateSequenceReport(diagramName)**: Generate sequence diagram analysis report

### Plugin Integration

- **Automatic Lifecycle Management**: MCP server starts/stops with plugin
- **Error Handling**: Robust startup/shutdown with detailed logging
- **Embedded HTTP Server**: Undertow-based MCP server running inside the Visual Paradigm process

## Usage

### Installation

Build, test and install with the `./run` command:

1. **Build the plugin**:

   ```bash
   ./run build
   ```

2. **Package for distribution**:

   ```bash
   ./run package
   ```

3. **Install to Visual Paradigm**:

   ```bash
   ./run install
   ```

4. **Start Visual Paradigm** - the MCP server will start automatically

### Using the MCP Server

Once Visual Paradigm is running with the plugin:

- **MCP Server Endpoint**: `http://localhost:2026/sse` (SSE)
- **Server Name**: `visual-paradigm-mcp-server`
- **Available Tools**: 55 diagram operations (Management, Use Case, Class, Project, ERD, Sequence)

#### Connecting with Claude or MCP Clients

Configure your MCP client to connect to:

```json
{
  "transport": {
    "type": "sse",
    "url": "http://localhost:2026/sse"
  }
}
```

## Development

### Building

```bash
./run build
./run test
./run package
./run all          # Build, test, package, and install
```

### Code Quality

```bash
./run format       # Format code with Google Java Format
./run spotbugs     # Run SpotBugs static analysis
./run pmd          # Run PMD static analysis
```

### Testing

- **Unit and Integration Tests**: Comprehensive Mockito/JUnit tests for all components
- **System Tests**: MCP Inspector protocol validation
- **Manual Testing**: Visual Paradigm UI integration testing
- **Tool Service Tests**: Validate all MCP tool implementations
- **Plugin Lifecycle Tests**: Test integration with Visual Paradigm

#### Unit and Integration Tests

```bash
./run test
```

#### MCP Protocol Testing (Future)

Test with MCP Inspector for protocol validation:

```bash
./run inspector test
```

Display all MCP features and their descriptions:

```bash
./run inspector list
```

### Debugging

**MCP Server Logging**: Check Visual Paradigm console output for:

- `"MCP Server started on port 2026"` - successful startup
- `"MCP Server stopped"` - clean shutdown
- Error messages if startup fails

**Configuration**: The port is set in `VPMcpPlugin.java` (`mcpServer.setPort(2026)`).

### Support

- **MCP Protocol**: [Model Context Protocol Specification](https://modelcontextprotocol.io/specification/2025-06-18/architecture)
- **Visual Paradigm**: [Plugin API Documentation](https://www.visual-paradigm.com/support/documents/pluginjavadoc/)
