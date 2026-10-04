package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.OptionalParam;
import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramLayoutEngine;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vp.plugin.ApplicationManager;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.ExportDiagramAsImageOption;
import com.vp.plugin.ExportDiagramAsImageWatermark;
import com.vp.plugin.diagram.ICaptionUIModel;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.connector.IHasRoleConnectorUIModel;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IAssociationEnd;
import com.vp.plugin.model.IAttribute;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IDBColumn;
import com.vp.plugin.model.IDBForeignKey;
import com.vp.plugin.model.IDBTable;
import com.vp.plugin.model.IExtend;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IInclude;
import com.vp.plugin.model.IInteractionLifeLine;
import com.vp.plugin.model.IMessage;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IOperation;
import com.vp.plugin.model.IParameter;
import com.vp.plugin.model.IProject;
import com.vp.plugin.model.IRelationship;
import com.vp.plugin.model.IUseCase;
import com.vp.plugin.model.factory.IModelElementFactory;
import java.awt.Point;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Callable;
import javax.swing.SwingUtilities;

/**
 * Base class for all MCP tool services. Provides shared layout, VP API access, EDT dispatch, and
 * diagram management tools.
 */
public abstract class AbstractDiagramMcpTools {

  /** When Visual Paradigm loaded this plugin build (tool classes load with the plugin). */
  protected static final long LOADED_AT = System.currentTimeMillis();

  private final java.util.Map<String, Integer> elementZoneCounts = new HashMap<>();

  /**
   * Run a callable on the Swing EDT and return the result.
   *
   * @param callable the callable to execute
   * @param <T> the return type
   * @return the result
   * @throws Exception if the callable throws
   */
  protected <T> T runOnEdt(Callable<T> callable) throws Exception {
    final Object[] result = new Object[1];
    final Exception[] error = new Exception[1];
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            result[0] = callable.call();
          } catch (Exception e) {
            error[0] = e;
          }
        });
    if (error[0] != null) {
      throw error[0];
    }
    @SuppressWarnings("unchecked")
    T typed = (T) result[0];
    return typed;
  }

  /**
   * Run a runnable on the Swing EDT.
   *
   * @param runnable the runnable to execute
   * @throws Exception if the runnable throws
   */
  protected void runOnEdt(Runnable runnable) throws Exception {
    final Exception[] error = new Exception[1];
    SwingUtilities.invokeAndWait(
        () -> {
          try {
            runnable.run();
          } catch (Exception e) {
            error[0] = e;
          }
        });
    if (error[0] != null) {
      throw error[0];
    }
  }

  /**
   * Add a model element to a diagram. Sets the name on the model element, creates the diagram
   * element, and sets the visual caption text. Positions using VP's built-in layout.
   *
   * @param diagram the diagram
   * @param element the model element
   * @param name the display name for the element
   * @return the diagram element
   */
  protected IDiagramElement addToDiagram(
      IDiagramUIModel diagram, IModelElement element, String name) {
    DiagramManager dm = ApplicationManager.instance().getDiagramManager();
    element.setName(name);
    IDiagramElement diagramElement = dm.createDiagramElement(diagram, element);
    if (diagramElement instanceof IShapeUIModel) {
      ((IShapeUIModel) diagramElement).setCustomText(name);
    }
    applyConventionalFill(diagramElement, element);
    String key = diagram.getName();
    DiagramLayoutEngine.ElementZone zone =
        DiagramLayoutEngine.classifyElement(diagram.getType(), element);
    String zoneKey = key + ":" + zone;
    int indexInZone = elementZoneCounts.getOrDefault(zoneKey, 0);
    int[] bounds =
        DiagramLayoutEngine.calculateInitialBounds(diagram.getType(), element, indexInZone);
    diagramElement.setBounds(bounds[0], bounds[1], bounds[2], bounds[3]);
    elementZoneCounts.put(zoneKey, indexInZone + 1);
    return diagramElement;
  }

  /**
   * The project's convention fill color (#7AD2FF) for use-case-style shapes. Matches the blue used
   * in the reference Visual Paradigm diagrams and the cnpm PlantUML theme.
   */
  protected static final java.awt.Color VP_FILL_BLUE = new java.awt.Color(0x7A, 0xD2, 0xFF);

  /**
   * Apply the conventional fill color to a freshly added shape. Actors, use cases and lifelines get
   * the project blue (#7AD2FF) to match the reference diagrams; class boxes and ERD tables keep
   * Visual Paradigm's default white.
   *
   * @param de the diagram element just created
   * @param element the underlying model element
   */
  protected void applyConventionalFill(IDiagramElement de, IModelElement element) {
    if (element instanceof IActor
        || element instanceof IUseCase
        || element instanceof IInteractionLifeLine) {
      applyBlueFill(de);
    }
  }

  /**
   * Fill a shape with the project blue (#7AD2FF). No-op for non-shape elements.
   *
   * @param de the diagram element to color
   */
  protected void applyBlueFill(IDiagramElement de) {
    if (de instanceof IShapeUIModel) {
      com.vp.plugin.diagram.format.IShapeUIModelFillColor fill =
          ((IShapeUIModel) de).getFillColor();
      if (fill != null) {
        fill.setColor1(VP_FILL_BLUE, true);
      }
    }
  }

  /**
   * Grow every on-diagram shape of this model element to fit its content. Call after adding
   * attributes/operations/columns so class and table boxes are not clipped to their initial size.
   *
   * @param element the model element whose shapes should be refitted
   */
  protected void fitShapesForModel(IModelElement element) {
    if (element == null) {
      return;
    }
    IProject project = ApplicationManager.instance().getProjectManager().getProject();
    if (project == null) {
      return;
    }
    Iterator<?> diagramIter = project.diagramIterator();
    while (diagramIter.hasNext()) {
      Object diagramObj = diagramIter.next();
      if (diagramObj instanceof IDiagramUIModel) {
        for (IDiagramElement de : getDiagramElementsList((IDiagramUIModel) diagramObj)) {
          IModelElement m = de.getModelElement();
          if (de instanceof IShapeUIModel && m != null && m.getId().equals(element.getId())) {
            ((IShapeUIModel) de).fitSize();
          }
        }
      }
    }
  }

  /**
   * The one element on {@code diagram} named {@code name} (model name or a shape's custom caption)
   * whose model is one of {@code types} (any type when none are given). Tools look elements up by
   * name, so a missing or ambiguous name is an error the tool reports, never a silent pick of the
   * first match or of an element on another diagram.
   *
   * @throws IllegalArgumentException when no element or several different elements match
   */
  protected IDiagramElement findElement(IDiagramUIModel diagram, String name, Class<?>... types) {
    List<IDiagramElement> found = elementsNamed(diagram, name, types);
    if (found.isEmpty()) {
      throw new IllegalArgumentException(
          "No "
              + typeLabel(types)
              + " named '"
              + name
              + "' on diagram '"
              + diagram.getName()
              + "'");
    }
    if (found.size() > 1) {
      List<String> kinds = new ArrayList<>();
      for (IDiagramElement de : found) {
        kinds.add(getSemanticTypeName(de.getModelElement()));
      }
      throw new IllegalArgumentException(
          found.size()
              + " elements named '"
              + name
              + "' on diagram '"
              + diagram.getName()
              + "' ("
              + String.join(", ", kinds)
              + "); rename one with renameElement(..., elementType)");
    }
    return found.get(0);
  }

  /** Every distinct element on the diagram matching name and types (one view per model). */
  protected List<IDiagramElement> elementsNamed(
      IDiagramUIModel diagram, String name, Class<?>... types) {
    List<IDiagramElement> result = new ArrayList<>();
    if (diagram == null || name == null) {
      return result;
    }
    java.util.Set<String> seenModels = new java.util.HashSet<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement model = de.getModelElement();
      if (model == null || !isAnyOf(model, types)) {
        continue;
      }
      boolean named =
          name.equals(model.getName())
              || (de instanceof IShapeUIModel && name.equals(((IShapeUIModel) de).getCustomText()));
      if (named && seenModels.add(model.getId())) {
        result.add(de);
      }
    }
    return result;
  }

  /**
   * The model element named {@code name} of {@code type}: the one shown on {@code diagram}, or,
   * when no diagram is given, the one in the whole project.
   *
   * @throws IllegalArgumentException when no element or several different elements match
   */
  protected <T extends IModelElement> T findModelElement(
      String name, Class<T> type, IDiagramUIModel diagram) {
    if (diagram != null) {
      return type.cast(findElement(diagram, name, type).getModelElement());
    }
    List<T> found = new ArrayList<>();
    IProject project = requireProject();
    Iterator<?> iter = project.allLevelModelElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (type.isInstance(obj) && name != null && name.equals(type.cast(obj).getName())) {
        found.add(type.cast(obj));
      }
    }
    if (found.size() != 1) {
      throw new IllegalArgumentException(
          (found.isEmpty() ? "No " : found.size() + " ")
              + type.getSimpleName().substring(1)
              + (found.isEmpty() ? "" : "s")
              + " named '"
              + name
              + "' in the project"
              + (found.isEmpty() ? "" : "; pass diagramName to pick the one on that diagram"));
    }
    return found.get(0);
  }

  private static boolean isAnyOf(IModelElement model, Class<?>... types) {
    if (types.length == 0) {
      return true;
    }
    for (Class<?> type : types) {
      if (type.isInstance(model)) {
        return true;
      }
    }
    return false;
  }

  private static String typeLabel(Class<?>... types) {
    if (types.length == 0) {
      return "element";
    }
    List<String> names = new ArrayList<>();
    for (Class<?> type : types) {
      names.add(type.getSimpleName().substring(1)); // IActor -> Actor
    }
    return String.join(" or ", names);
  }

  /**
   * Get all diagram elements on a diagram as a list.
   *
   * @param diagram the diagram
   * @return list of diagram elements
   */
  protected List<IDiagramElement> getDiagramElementsList(IDiagramUIModel diagram) {
    List<IDiagramElement> elements = new ArrayList<>();
    if (diagram == null) {
      return elements;
    }
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        elements.add((IDiagramElement) obj);
      }
    }
    return elements;
  }

  // --- Diagram Management Tools ---

  @Tool(
      name = "listDiagrams",
      description =
          "List all diagrams in the project, optionally filtered by type "
              + "(UseCase, Class, Sequence, ER)")
  public String listDiagrams(@OptionalParam String type) {
    try {
      return runOnEdt(
          () -> {
            IProject project = requireProject();
            List<String> diagrams = new ArrayList<>();
            Iterator<?> iter = project.diagramIterator();
            while (iter.hasNext()) {
              Object obj = iter.next();
              if (obj instanceof IDiagramUIModel) {
                IDiagramUIModel d = (IDiagramUIModel) obj;
                String diagramType = d.getType();
                if (type == null
                    || type.trim().isEmpty()
                    || diagramType
                        .toLowerCase(Locale.ROOT)
                        .contains(type.toLowerCase(Locale.ROOT))) {
                  diagrams.add(d.getName() + " (" + diagramType + ")");
                }
              }
            }
            if (diagrams.isEmpty()) {
              return "No diagrams found" + (type != null ? " of type: " + type : "");
            }
            StringBuilder sb = new StringBuilder();
            sb.append("Diagrams (").append(diagrams.size()).append("):\n");
            for (String d : diagrams) {
              sb.append("  - ").append(d).append("\n");
            }
            return sb.toString();
          });
    } catch (Exception e) {
      return "Error listing diagrams: " + e.getMessage();
    }
  }

  @Tool(
      name = "getDiagramElements",
      description =
          "Get all elements (shapes and connectors) on a diagram with their names, types,"
              + " stereotypes («...»), details and positions; associations show where arrowheads"
              + " are drawn ({arrow at: ...})")
  public String getDiagramElements(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            List<IDiagramElement> elements = getDiagramElementsList(diagram);
            if (elements.isEmpty()) {
              return "Diagram '" + diagramName + "' is empty";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Elements on '")
                .append(diagramName)
                .append("' (")
                .append(elements.size())
                .append("):\n");
            for (IDiagramElement de : elements) {
              IModelElement model = de.getModelElement();
              String type = getSemanticTypeName(model);
              String name = model != null ? model.getName() : "(unnamed)";

              if (model instanceof IInclude
                  || model instanceof IExtend
                  || model instanceof IGeneralization
                  || model instanceof IAssociation
                  || model instanceof IDBForeignKey
                  || model instanceof IMessage) {
                // Connectors: show from -> to
                if (model instanceof IRelationship) {
                  IRelationship rel = (IRelationship) model;
                  String from = rel.getFrom() != null ? rel.getFrom().getName() : "?";
                  String to = rel.getTo() != null ? rel.getTo().getName() : "?";
                  sb.append("  - ")
                      .append(type)
                      .append(": ")
                      .append(from)
                      .append(" -> ")
                      .append(to);
                  if (model instanceof IAssociation) {
                    IAssociationEnd toEnd = (IAssociationEnd) ((IAssociation) model).getToEnd();
                    if (toEnd != null && toEnd.getMultiplicity() != null) {
                      sb.append(" [").append(toEnd.getMultiplicity()).append("]");
                    }
                    IAssociationEnd fromEnd = (IAssociationEnd) ((IAssociation) model).getFromEnd();
                    List<String> arrows = new ArrayList<>();
                    if (fromEnd != null
                        && fromEnd.getNavigable() == IAssociationEnd.NAVIGABLE_NAVIGABLE) {
                      arrows.add(from);
                    }
                    if (toEnd != null
                        && toEnd.getNavigable() == IAssociationEnd.NAVIGABLE_NAVIGABLE) {
                      arrows.add(to);
                    }
                    if (!arrows.isEmpty()) {
                      sb.append(" {arrow at: ").append(String.join(", ", arrows)).append("}");
                    }
                  }
                  sb.append("\n");
                } else {
                  sb.append("  - ").append(type).append(": ").append(name).append("\n");
                }
              } else if (model instanceof IClass) {
                // Classes: show attributes and operations
                sb.append("  - ").append(type).append(": ").append(name);
                sb.append(stereotypeSuffix(model));
                sb.append(" at (").append(de.getX()).append(",").append(de.getY());
                sb.append(") size ").append(de.getWidth()).append("x").append(de.getHeight());
                sb.append("\n");
                // Attributes
                List<String> attrs = new ArrayList<>();
                Iterator<?> attrIter = ((IClass) model).attributeIterator();
                while (attrIter.hasNext()) {
                  Object attrObj = attrIter.next();
                  if (attrObj instanceof IAttribute) {
                    IAttribute attr = (IAttribute) attrObj;
                    String vis = attr.getVisibility();
                    String attrStr =
                        (vis != null ? vis : "")
                            + attr.getName()
                            + (attr.getType() != null ? ":" + attr.getType() : "");
                    attrs.add(attrStr);
                  }
                }
                if (!attrs.isEmpty()) {
                  sb.append("    Attributes: ").append(String.join(", ", attrs)).append("\n");
                }
                // Operations
                List<String> ops = new ArrayList<>();
                Iterator<?> opIter = ((IClass) model).operationIterator();
                while (opIter.hasNext()) {
                  Object opObj = opIter.next();
                  if (opObj instanceof IOperation) {
                    IOperation op = (IOperation) opObj;
                    StringBuilder opStr = new StringBuilder();
                    String opVis = op.getVisibility();
                    if (opVis != null) {
                      opStr.append(opVis);
                    }
                    opStr.append(op.getName()).append("(");
                    List<String> params = new ArrayList<>();
                    Iterator<?> paramIter = op.parameterIterator();
                    while (paramIter.hasNext()) {
                      Object paramObj = paramIter.next();
                      if (paramObj instanceof IParameter) {
                        IParameter p = (IParameter) paramObj;
                        String paramStr = p.getName();
                        if (p.getType() != null) {
                          paramStr += ":" + p.getType();
                        }
                        params.add(paramStr);
                      }
                    }
                    opStr.append(String.join(", ", params)).append(")");
                    if (op.getReturnType() != null) {
                      opStr.append(":").append(op.getReturnType());
                    }
                    ops.add(opStr.toString());
                  }
                }
                if (!ops.isEmpty()) {
                  sb.append("    Operations: ").append(String.join(", ", ops)).append("\n");
                }
              } else if (model instanceof IDBTable) {
                // Tables: show columns
                sb.append("  - ").append(type).append(": ").append(name);
                sb.append(" at (").append(de.getX()).append(",").append(de.getY());
                sb.append(") size ").append(de.getWidth()).append("x").append(de.getHeight());
                sb.append("\n");
                List<String> cols = new ArrayList<>();
                Iterator<?> colIter = ((IDBTable) model).dBColumnIterator();
                while (colIter.hasNext()) {
                  Object colObj = colIter.next();
                  if (colObj instanceof IDBColumn) {
                    IDBColumn col = (IDBColumn) colObj;
                    String colStr = col.getName();
                    if (col.getTypeInText() != null) {
                      colStr += "(" + col.getTypeInText() + ")";
                    }
                    if (col.isPrimaryKey()) {
                      colStr += ",PK";
                    }
                    cols.add(colStr);
                  }
                }
                if (!cols.isEmpty()) {
                  sb.append("    Columns: ").append(String.join(", ", cols)).append("\n");
                }
              } else if (model instanceof IInteractionLifeLine) {
                // Lifelines: show base classifier
                sb.append("  - ").append(type).append(": ").append(name);
                Object classifierObj = ((IInteractionLifeLine) model).getBaseClassifier();
                if (classifierObj instanceof IModelElement) {
                  sb.append(" [").append(((IModelElement) classifierObj).getName()).append("]");
                }
                sb.append(" at (").append(de.getX()).append(",").append(de.getY());
                sb.append(") size ").append(de.getWidth()).append("x").append(de.getHeight());
                sb.append("\n");
              } else {
                // Default: type + name + stereotypes + position
                sb.append("  - ").append(type).append(": ").append(name);
                if (model != null) {
                  sb.append(stereotypeSuffix(model));
                }
                sb.append(" at (").append(de.getX()).append(",").append(de.getY());
                sb.append(") size ").append(de.getWidth()).append("x").append(de.getHeight());
                ICaptionUIModel cap = outsideCaption(de);
                if (cap != null) {
                  sb.append(" caption at (")
                      .append(cap.getX())
                      .append(",")
                      .append(cap.getY())
                      .append(") size ")
                      .append(cap.getWidth())
                      .append("x")
                      .append(cap.getHeight());
                }
                sb.append("\n");
              }
            }
            return sb.toString();
          });
    } catch (Exception e) {
      return "Error getting diagram elements: " + e.getMessage();
    }
  }

  @Tool(
      name = "autoLayoutDiagram",
      description =
          "Apply structured layout to a diagram. MUST be called AFTER adding all elements. "
              + "UC: actors left, use cases right. Class: boundary/DAO/entity layers. "
              + "ERD: compact organic.")
  public String autoLayoutDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            layoutDiagram(diagram);
            return "Auto-layout applied to diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error applying auto-layout: " + e.getMessage();
    }
  }

  protected void layoutDiagram(IDiagramUIModel diagram) {
    DiagramLayoutEngine.applyStructuredLayout(getDiagramManager(), diagram);
  }

  /**
   * Make VP draw the diagram (export to a temp PNG, deleted), which is when it routes connectors
   * and sizes captions. Not on the EDT.
   *
   * @return null when rendered, else the export's error message
   */
  protected String renderDiagram(String diagramName) throws java.io.IOException {
    File rendered = File.createTempFile("render", ".png");
    try {
      String exported = exportDiagramImage(diagramName, rendered.getPath());
      return exported.startsWith("Exported") ? null : exported;
    } finally {
      if (!rendered.delete()) {
        rendered.deleteOnExit();
      }
    }
  }

  @Tool(
      name = "checkLayout",
      description =
          "Geometric layout check of a diagram: overlapping shapes, shapes straddling a system"
              + " boundary or package edge, lines running through a shape they do not connect,"
              + " captions drawn outside their shape (actor names) colliding with shapes or lines,"
              + " and crossing lines. Returns 'OK' or one issue per line")
  public String checkLayout(String diagramName) {
    try {
      // VP routes connectors only when it draws the diagram: render it first, or the check would
      // see the routes from before the last move.
      String renderError = renderDiagram(diagramName);
      if (renderError != null) {
        return renderError;
      }
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            List<LayoutCheck.Box> boxes = new ArrayList<>();
            List<LayoutCheck.Line> lines = new ArrayList<>();
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              IModelElement model = de.getModelElement();
              if (model == null) {
                continue;
              }
              if (de instanceof IConnectorUIModel) {
                IConnectorUIModel c = (IConnectorUIModel) de;
                Point[] points = c.getPoints();
                if (c.getFromShape() == null || c.getToShape() == null || points == null) {
                  continue;
                }
                String from = c.getFromShape().getModelElement().getName();
                String to = c.getToShape().getModelElement().getName();
                String name = model.getModelType() + " " + from + " -> " + to;
                lines.add(new LayoutCheck.Line(name, from, to, points));
              } else if (de instanceof IShapeUIModel) {
                boolean container =
                    model instanceof com.vp.plugin.model.ISystem
                        || model instanceof com.vp.plugin.model.IPackage;
                boxes.add(
                    new LayoutCheck.Box(
                        model.getName(),
                        model instanceof IUseCase,
                        container,
                        de.getX(),
                        de.getY(),
                        de.getWidth(),
                        de.getHeight()));
                // A caption drawn outside its shape (an actor's name below the figure) can
                // collide with other shapes too; captions inside their shape cannot.
                ICaptionUIModel cap = outsideCaption(de);
                if (cap != null) {
                  boxes.add(
                      LayoutCheck.Box.caption(
                          model.getName(),
                          cap.getX(),
                          cap.getY(),
                          cap.getWidth(),
                          cap.getHeight()));
                }
              }
            }
            List<String> issues = LayoutCheck.check(boxes, lines);
            return issues.isEmpty() ? "OK" : String.join("\n", issues);
          });
    } catch (Exception e) {
      return "Error checking layout: " + e.getMessage();
    }
  }

  /**
   * VP keeps a boundary shape while its ISystem model exists, so the shape cannot be removed on its
   * own: move the use cases out of the system (model and shapes), then delete the system. On the
   * EDT.
   */
  private String removeSystemBoundary(
      IDiagramUIModel diagram, IDiagramElement boundary, String elementName) {
    com.vp.plugin.model.ISystem system = (com.vp.plugin.model.ISystem) boundary.getModelElement();
    for (IShapeUIModel child : boundary.toChildArray()) {
      boundary.removeChild(child);
      diagram.addDiagramElement(child);
    }
    IModelElement owner = system.getParent();
    IUseCase[] useCases = system.toUseCaseArray();
    for (IUseCase uc : useCases) {
      if (owner != null) {
        owner.addChild(uc);
      } else {
        system.removeUseCase(uc); // the system is top level: the use case becomes top level too
      }
    }
    for (IUseCase uc : useCases) {
      if (requireProject().getModelElementById(uc.getId()) == null) {
        return "Error: use case '" + uc.getName() + "' disappeared while leaving the boundary";
      }
    }
    // Deleting the system deletes what it still owns, so never delete it while it owns anything.
    if (system.childCount() > 0) {
      return "Could not remove boundary '"
          + elementName
          + "': its use cases could not be moved out ("
          + system.childCount()
          + " still owned)";
    }
    system.delete();
    return "Removed boundary '" + elementName + "'; its use cases stay on the diagram";
  }

  @Tool(
      name = "renameElement",
      description =
          "Rename an element shown on a diagram (actor, use case, class, table, system"
              + " boundary, ...). Refuses a name already used on that diagram, because tools find"
              + " elements by name. elementType (optional, e.g. Actor or UseCase) picks one of"
              + " several elements sharing the name")
  public String renameElement(
      String diagramName, String elementName, String newName, @OptionalParam String elementType) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            if (newName == null || newName.trim().isEmpty()) {
              return "newName is required";
            }
            String name = newName.trim();
            IDiagramElement de;
            if (elementType == null || elementType.trim().isEmpty()) {
              de = findElement(diagram, elementName);
            } else {
              List<IDiagramElement> ofType = new ArrayList<>();
              for (IDiagramElement candidate : elementsNamed(diagram, elementName)) {
                if (elementType
                    .trim()
                    .equalsIgnoreCase(getSemanticTypeName(candidate.getModelElement()))) {
                  ofType.add(candidate);
                }
              }
              if (ofType.size() != 1) {
                return ofType.size()
                    + " "
                    + elementType.trim()
                    + " element(s) named '"
                    + elementName
                    + "' on diagram '"
                    + diagramName
                    + "'";
              }
              de = ofType.get(0);
            }
            if (!elementsNamed(diagram, name).isEmpty()) {
              return "'" + name + "' is already used on diagram '" + diagramName + "'";
            }
            de.getModelElement().setName(name);
            // A boundary also shows a custom caption (set by addSystemBoundary).
            if (de instanceof IShapeUIModel
                && elementName.equals(((IShapeUIModel) de).getCustomText())) {
              ((IShapeUIModel) de).setCustomText(name);
            }
            return "Renamed '" + elementName + "' to '" + name + "'";
          });
    } catch (Exception e) {
      return "Error renaming element: " + e.getMessage();
    }
  }

  @Tool(
      name = "removeDiagramElement",
      description =
          "Remove an element (shape or connector) from a diagram by its model element name. A"
              + " system boundary is dissolved: its use cases stay on the diagram")
  public String removeDiagramElement(String diagramName, String elementName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IDiagramElement element = findElement(diagram, elementName);
            if (element.getModelElement() instanceof com.vp.plugin.model.ISystem) {
              return removeSystemBoundary(diagram, element, elementName);
            }
            // A shape nested in a container (e.g. a use case in a system boundary) is owned by
            // its parent shape, so detach it there first.
            if (element instanceof IShapeUIModel) {
              IDiagramElement parent = ((IShapeUIModel) element).getParent();
              if (parent != null) {
                parent.removeChild((IShapeUIModel) element);
              }
            }
            diagram.removeDiagramElement(element);
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              if (de.getId().equals(element.getId())) {
                return "Could not remove element '" + elementName + "': it is still on the diagram";
              }
            }
            return "Removed element '" + elementName + "' from diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error removing element: " + e.getMessage();
    }
  }

  @Tool(
      name = "getElementCounts",
      description =
          "Get a summary of element types on a diagram (actors, use cases, classes, tables, etc.)")
  public String getElementCounts(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            java.util.Map<String, Integer> counts = new HashMap<>();
            Iterator<?> iter = diagram.diagramElementIterator();
            while (iter.hasNext()) {
              Object obj = iter.next();
              if (obj instanceof IDiagramElement) {
                IDiagramElement de = (IDiagramElement) obj;
                IModelElement model = de.getModelElement();
                if (model != null) {
                  String typeName = getSemanticTypeName(model);
                  counts.merge(typeName, 1, Integer::sum);
                }
              }
            }

            StringBuilder sb = new StringBuilder();
            sb.append("Element counts for '").append(diagramName).append("':\n");
            counts.forEach(
                (type, count) ->
                    sb.append("  ").append(type).append(": ").append(count).append("\n"));
            return sb.toString();
          });
    } catch (Exception e) {
      return "Error getting element counts: " + e.getMessage();
    }
  }

  @Tool(
      name = "addStereotype",
      description =
          "Apply a stereotype (e.g. System, Time) to an element (actor, use case, class, "
              + "table, ...) by name on a diagram")
  public String addStereotype(String diagramName, String elementName, String stereotype) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            if (stereotype == null || stereotype.trim().isEmpty()) {
              return "Stereotype is required";
            }
            IModelElement model = findElement(diagram, elementName).getModelElement();
            String stereo = stereotype.trim();
            if (model.hasStereotype(stereo)) {
              return "'" + elementName + "' already has stereotype '" + stereo + "'";
            }
            model.addStereotype(stereo);
            return "Added stereotype '" + stereo + "' to '" + elementName + "'";
          });
    } catch (Exception e) {
      return "Error adding stereotype: " + e.getMessage();
    }
  }

  @Tool(
      name = "setElementBounds",
      description =
          "Move/resize a shape on a diagram. width or height <= 0 keeps the position and fits the"
              + " shape to its content (e.g. after adding attributes)")
  public String setElementBounds(
      String diagramName,
      String elementName,
      int x,
      int y,
      @OptionalParam int width,
      @OptionalParam int height) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IDiagramElement de = findElement(diagram, elementName);
            if (width <= 0 || height <= 0) {
              if (de instanceof IShapeUIModel) {
                ((IShapeUIModel) de).fitSize();
              }
              de.setBounds(x, y, de.getWidth(), de.getHeight());
            } else {
              de.setBounds(x, y, width, height);
            }
            // Otherwise the name caption (e.g. an actor's label) stays at the old position.
            de.resetCaption();
            return "Bounds of '"
                + elementName
                + "': "
                + de.getX()
                + ","
                + de.getY()
                + " "
                + de.getWidth()
                + "x"
                + de.getHeight();
          });
    } catch (Exception e) {
      return "Error setting bounds: " + e.getMessage();
    }
  }

  @Tool(
      name = "getRelationshipDetails",
      description =
          "Audit dump (JSON) of a diagram: classes (abstract, stereotypes, owner, attributes,"
              + " bounds) and every relationship with both ends (multiplicity, aggregation kind,"
              + " role), name and connector geometry. Works for any diagram type")
  public String getRelationshipDetails(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            ObjectNode root = JSON.createObjectNode();
            root.put("diagram", diagramName);
            ArrayNode classes = root.putArray("classes");
            ArrayNode relationships = root.putArray("relationships");
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              IModelElement model = de.getModelElement();
              if (model instanceof IClass) {
                IClass cls = (IClass) model;
                ObjectNode c = classes.addObject();
                c.put("name", cls.getName());
                c.put("abstract", cls.isAbstract());
                c.put("owner", nameOf(cls.getParent()));
                ArrayNode st = c.putArray("stereotypes");
                for (String s : cls.toStereotypeArray()) {
                  st.add(s);
                }
                ArrayNode attrs = c.putArray("attributes");
                Iterator<?> it = cls.attributeIterator();
                while (it.hasNext()) {
                  Object o = it.next();
                  if (o instanceof IAttribute) {
                    IAttribute a = (IAttribute) o;
                    ObjectNode an = attrs.addObject();
                    an.put("name", a.getName());
                    an.put("type", a.getTypeAsString());
                    an.put("visibility", a.getVisibility());
                  }
                }
                c.putArray("bounds")
                    .add(de.getX())
                    .add(de.getY())
                    .add(de.getWidth())
                    .add(de.getHeight());
              } else if (model instanceof IRelationship) {
                IRelationship rel = (IRelationship) model;
                ObjectNode r = relationships.addObject();
                r.put("type", model.getModelType());
                r.put("from", nameOf(rel.getFrom()));
                r.put("to", nameOf(rel.getTo()));
                r.put("name", model.getName());
                if (model instanceof IGeneralization) {
                  r.put("parent", nameOf(rel.getFrom()));
                  r.put("child", nameOf(rel.getTo()));
                }
                if (model instanceof IAssociation) {
                  IAssociation assoc = (IAssociation) model;
                  putEnd(r.putObject("fromEnd"), (IAssociationEnd) assoc.getFromEnd());
                  putEnd(r.putObject("toEnd"), (IAssociationEnd) assoc.getToEnd());
                }
                if (de instanceof IConnectorUIModel) {
                  IConnectorUIModel conn = (IConnectorUIModel) de;
                  ArrayNode pts = r.putArray("points");
                  Point[] points = conn.getPoints();
                  if (points != null) {
                    for (Point pt : points) {
                      pts.addArray().add(pt.x).add(pt.y);
                    }
                  }
                  r.putArray("fromDiff")
                      .add(conn.getFromShapeXDiff())
                      .add(conn.getFromShapeYDiff());
                  r.putArray("toDiff").add(conn.getToShapeXDiff()).add(conn.getToShapeYDiff());
                  r.put(
                      "shapeFrom",
                      conn.getFromShape() == null
                          ? null
                          : nameOf(conn.getFromShape().getModelElement()));
                  r.put(
                      "shapeTo",
                      conn.getToShape() == null
                          ? null
                          : nameOf(conn.getToShape().getModelElement()));
                  r.putArray("connectorBounds")
                      .add(conn.getX())
                      .add(conn.getY())
                      .add(conn.getWidth())
                      .add(conn.getHeight());
                  if (conn instanceof IHasRoleConnectorUIModel) {
                    IHasRoleConnectorUIModel hr = (IHasRoleConnectorUIModel) conn;
                    putRect(r, "multA", hr.getMultiplicityARectangle());
                    putRect(r, "multB", hr.getMultiplicityBRectangle());
                    putRect(r, "roleA", hr.getRoleARectangle());
                    putRect(r, "roleB", hr.getRoleBRectangle());
                  }
                  ICaptionUIModel cap = conn.getCaptionUIModel();
                  if (cap != null) {
                    r.putArray("caption")
                        .add(cap.getX())
                        .add(cap.getY())
                        .add(cap.getWidth())
                        .add(cap.getHeight());
                  }
                }
              }
            }
            return JSON.writeValueAsString(root);
          });
    } catch (Exception e) {
      return "Error reading relationship details: " + e.getMessage();
    }
  }

  @Tool(
      name = "exportDiagramImage",
      description = "Open a diagram and export it as a PNG image to an absolute file path")
  public String exportDiagramImage(String diagramName, String filePath) {
    try {
      if (filePath == null || filePath.trim().isEmpty()) {
        return "filePath is required";
      }
      final File file = new File(filePath.trim());
      File dir = file.getAbsoluteFile().getParentFile();
      if (dir != null && !dir.isDirectory() && !dir.mkdirs()) {
        return "Cannot create directory: " + dir;
      }
      // Activating a diagram can fail on the first try when the project has been idle
      // (the EDT needs to process the open before getActiveDiagram reflects it). Retry the
      // open+activate a few times, sleeping off the EDT between attempts.
      if (runOnEdt(() -> DiagramUtils.findDiagramByName(diagramName) == null)) {
        return "Diagram not found: " + diagramName;
      }
      boolean activated = false;
      for (int attempt = 0; attempt < 3 && !activated; attempt++) {
        if (attempt > 0) {
          Thread.sleep(300L);
        }
        activated =
            runOnEdt(
                () -> {
                  IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
                  if (diagram == null) {
                    return false;
                  }
                  getDiagramManager().openDiagram(diagram);
                  IDiagramUIModel active = getDiagramManager().getActiveDiagram();
                  return active != null && active.getId().equals(diagram.getId());
                });
      }
      if (!activated) {
        return "Diagram could not be activated: " + diagramName;
      }
      return runOnEdt(
          () -> {
            ExportDiagramAsImageOption exportOption =
                new ExportDiagramAsImageOption(ExportDiagramAsImageOption.IMAGE_TYPE_PNG);
            ExportDiagramAsImageWatermark emptyWatermark = (graphics, width, height) -> {};
            exportOption.setWatermark(emptyWatermark);
            ApplicationManager.instance()
                .getModelConvertionManager()
                .exportActiveDiagramAsImage(file, exportOption);
            return file.isFile()
                ? "Exported '" + diagramName + "' to " + file + " (" + file.length() + " bytes)"
                : "Export did not produce a file: " + file;
          });
    } catch (Exception e) {
      return "Error exporting diagram: " + e.getMessage();
    }
  }

  @Tool(
      name = "newProject",
      description =
          "VP File > New Project: close the open project and start a new empty one; save it"
              + " with saveProjectAs. Refuses while the open project has unsaved changes unless"
              + " discardChanges is true")
  public String newProject(@OptionalParam Boolean discardChanges) {
    try {
      return runOnEdt(
          () -> {
            IProject current = DiagramUtils.getProject();
            if (current != null && current.isModified() && !Boolean.TRUE.equals(discardChanges)) {
              return "Project '"
                  + current.getName()
                  + "' has unsaved changes: saveProject first, or pass discardChanges=true";
            }
            boolean ok = ApplicationManager.instance().getProjectManager().newProject();
            IProject project = DiagramUtils.getProject();
            return ok && project != null
                ? "Created new project: " + project.getName()
                : "New project failed";
          });
    } catch (Exception e) {
      return "Error creating project: " + e.getMessage();
    }
  }

  @Tool(
      name = "saveProjectAs",
      description =
          "VP File > Save As: save the open project to a new .vpp path (parent folder must exist,"
              + " existing files are not overwritten); the new file becomes the open project")
  public String saveProjectAs(String filePath) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            if (filePath == null || filePath.trim().isEmpty()) {
              return "filePath is required";
            }
            String path = filePath.trim();
            File file =
                new File(path.toLowerCase(Locale.ROOT).endsWith(".vpp") ? path : path + ".vpp");
            File dir = file.getAbsoluteFile().getParentFile();
            if (dir == null || !dir.isDirectory()) {
              return "Folder does not exist: " + dir;
            }
            if (file.exists()) {
              return "File already exists (not overwritten): " + file;
            }
            boolean ok = ApplicationManager.instance().getProjectManager().saveProjectAs(file);
            IProject project = DiagramUtils.getProject();
            return ok
                ? "Saved project as " + project.getProjectFile()
                : "Save As failed for " + file;
          });
    } catch (Exception e) {
      return "Error in Save As: " + e.getMessage();
    }
  }

  @Tool(name = "saveProject", description = "Save the currently open project to its file")
  public String saveProject() {
    try {
      return runOnEdt(
          () -> {
            IProject project = requireProject();
            boolean ok = ApplicationManager.instance().getProjectManager().saveProject();
            return (ok ? "Saved project to " : "Save failed for ") + project.getProjectFile();
          });
    } catch (Exception e) {
      return "Error saving project: " + e.getMessage();
    }
  }

  @Tool(
      name = "getProjectInfo",
      description =
          "Name, file path and unsaved-changes flag of the project open in Visual Paradigm, plus"
              + " when the running plugin was loaded and whether a newer build is installed"
              + " (stale = restart Visual Paradigm to load it)")
  public String getProjectInfo() {
    try {
      return runOnEdt(
          () -> {
            IProject project = DiagramUtils.getProject();
            if (project == null) {
              return "No project is open";
            }
            ObjectNode root = JSON.createObjectNode();
            root.put("name", project.getName());
            File file = project.getProjectFile();
            root.put("file", file != null ? file.getAbsolutePath() : null);
            root.put("diagramCount", DiagramUtils.findAllDiagrams(IDiagramUIModel.class).size());
            root.put("modified", project.isModified());
            root.set("plugin", pluginInfo());
            return JSON.writeValueAsString(root);
          });
    } catch (Exception e) {
      return "Error reading project info: " + e.getMessage();
    }
  }

  @Tool(
      name = "rerouteConnectors",
      description =
          "Re-anchor every connector of a diagram to the centers of its shapes (straight"
              + " center-to-center lines); run after moving shapes")
  public String rerouteConnectors(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            int count = 0;
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              if (de instanceof IConnectorUIModel) {
                centerConnector((IConnectorUIModel) de);
                count++;
              }
            }
            return "Rerouted " + count + " connector(s) on '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error rerouting connectors: " + e.getMessage();
    }
  }

  /** When this plugin build was loaded, and whether the installed jar has changed since. */
  protected static ObjectNode pluginInfo() {
    ObjectNode info = JSON.createObjectNode();
    info.put("loadedAt", java.time.Instant.ofEpochMilli(LOADED_AT).toString());
    java.security.CodeSource source =
        AbstractDiagramMcpTools.class.getProtectionDomain().getCodeSource();
    File jar = null;
    try {
      jar = source == null ? null : new File(source.getLocation().toURI());
    } catch (java.net.URISyntaxException e) {
      jar = null;
    }
    if (jar != null && jar.isFile()) {
      info.put("jar", jar.getAbsolutePath());
      info.put("installedAt", java.time.Instant.ofEpochMilli(jar.lastModified()).toString());
      info.put("stale", jar.lastModified() > LOADED_AT);
    }
    return info;
  }

  protected static void putRect(ObjectNode node, String key, java.awt.Rectangle rect) {
    if (rect != null) {
      node.putArray(key).add(rect.x).add(rect.y).add(rect.width).add(rect.height);
    }
  }

  protected static final ObjectMapper JSON = new ObjectMapper();

  protected static String nameOf(IModelElement element) {
    return element != null ? element.getName() : null;
  }

  protected static void putEnd(ObjectNode node, IAssociationEnd end) {
    if (end == null) {
      return;
    }
    node.put("class", nameOf(end.getTypeAsElement()));
    node.put("multiplicity", end.getMultiplicity());
    node.put("aggregation", end.getAggregationKind());
    node.put("role", end.getName());
    int nav = end.getNavigable();
    node.put(
        "navigable",
        nav == IAssociationEnd.NAVIGABLE_NAVIGABLE
            ? "navigable"
            : nav == IAssociationEnd.NAVIGABLE_NON_NAVIGABLE ? "non-navigable" : "unspecified");
  }

  /**
   * The shape's visible caption when VP draws it outside the shape (e.g. an actor's name below the
   * figure), else null. Such a caption's bounds are absolute diagram coordinates; a caption inside
   * its shape (center or inside-* sides) has bounds relative to the shape instead.
   */
  protected static ICaptionUIModel outsideCaption(IDiagramElement shape) {
    ICaptionUIModel cap = shape.getCaptionUIModel();
    if (cap == null || !cap.isVisible() || cap.getWidth() <= 0 || cap.getHeight() <= 0) {
      return null;
    }
    switch (cap.getSide()) {
      case ICaptionUIModel.SIDE_NORTH:
      case ICaptionUIModel.SIDE_EAST:
      case ICaptionUIModel.SIDE_SOUTH:
      case ICaptionUIModel.SIDE_WEST:
      case ICaptionUIModel.SIDE_FREEMOVE:
        return cap;
      default:
        return null;
    }
  }

  // --- Type Name Helper ---

  private static String getSemanticTypeName(IModelElement model) {
    if (model == null) {
      return "unknown";
    }
    if (model instanceof IClass && model.hasStereotype("Interface")) {
      return "Interface";
    }
    // getModelType() is the API's type name ("Actor", "UseCase", "System", ...); only the names
    // these tools always reported differently are mapped. Never use getClass(): VP's
    // implementation classes are obfuscated (e.g. "dgz" for a system boundary).
    String type = model.getModelType();
    switch (type) {
      case IModelElementFactory.MODEL_TYPE_DB_TABLE:
        return "Table";
      case IModelElementFactory.MODEL_TYPE_DB_FOREIGN_KEY:
        return "ForeignKey";
      case IModelElementFactory.MODEL_TYPE_INTERACTION_LIFE_LINE:
        return "Lifeline";
      default:
        return type;
    }
  }

  /** " «a, b»" for an element's stereotypes, or "" when it has none. */
  private static String stereotypeSuffix(IModelElement model) {
    com.vp.plugin.model.IStereotype[] stereotypes = model.toStereotypeModelArray();
    if (stereotypes == null || stereotypes.length == 0) {
      return "";
    }
    List<String> names = new ArrayList<>();
    for (com.vp.plugin.model.IStereotype st : stereotypes) {
      names.add(st.getName());
    }
    return " «" + String.join(", ", names) + "»";
  }

  /** Re-anchor an existing connector to the current centers of its two shapes. */
  protected static void centerConnector(IConnectorUIModel connector) {
    IShapeUIModel from = connector.getFromShape();
    IShapeUIModel to = connector.getToShape();
    if (from == null || to == null) {
      return;
    }
    // Aim an actor's lines at its center so they all fan out of one point (house convention
    // C1); by default VP spreads the ends around the figure and its caption.
    for (IShapeUIModel shape : new IShapeUIModel[] {from, to}) {
      if (shape.getModelElement() instanceof IActor) {
        shape.setConnectionPointType(IShapeUIModel.CONNECTION_POINT_TYPE_CENTER);
      }
    }
    connector.clearPoints();
    connector.addPoint(center(from));
    connector.addPoint(center(to));
    connector.setUseFromShapeCenter(true);
    connector.setUseToShapeCenter(true);
    connector.setRequestRebuild(true);
    // Re-place the «include»/«extend» caption on the new route instead of the old one.
    connector.resetCaption();
  }

  /** The center point of a shape, in diagram coordinates. */
  protected static Point center(IDiagramElement shape) {
    return new Point(shape.getX() + shape.getWidth() / 2, shape.getY() + shape.getHeight() / 2);
  }

  // --- VP API Accessors ---

  protected IProject requireProject() {
    IProject project = ApplicationManager.instance().getProjectManager().getProject();
    if (project == null) {
      throw new IllegalStateException("No project is open");
    }
    return project;
  }

  protected DiagramManager getDiagramManager() {
    return ApplicationManager.instance().getDiagramManager();
  }

  protected IModelElementFactory getModelElementFactory() {
    return IModelElementFactory.instance();
  }
}
