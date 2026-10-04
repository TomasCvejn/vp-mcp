package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IConnectorUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.diagram.IShapeUIModel;
import com.vp.plugin.diagram.IUseCaseDiagramUIModel;
import com.vp.plugin.model.IActor;
import com.vp.plugin.model.IAssociation;
import com.vp.plugin.model.IAssociationEnd;
import com.vp.plugin.model.IExtend;
import com.vp.plugin.model.IExtensionPoint;
import com.vp.plugin.model.IGeneralization;
import com.vp.plugin.model.IInclude;
import com.vp.plugin.model.IModelElement;
import com.vp.plugin.model.IRelationship;
import com.vp.plugin.model.ISystem;
import com.vp.plugin.model.IUseCase;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** MCP tools for Visual Paradigm Use Case diagram operations. */
public class UseCaseMcpTools extends AbstractDiagramMcpTools {

  @Tool(
      name = "createUseCaseDiagram",
      description = "Create a new use case diagram in Visual Paradigm")
  public String createUseCaseDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_USE_CASE_DIAGRAM);
            diagram.setName(diagramName);
            dm.openDiagram(diagram);
            return "Created use case diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating use case diagram: " + e.getMessage();
    }
  }

  @Tool(name = "addActor", description = "Add an actor to a use case diagram")
  public String addActor(String actorName, String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IActor actor = getModelElementFactory().createActor();
            addToDiagram(diagram, actor, actorName);

            return "Added actor '" + actorName + "' to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding actor: " + e.getMessage();
    }
  }

  @Tool(name = "addUseCase", description = "Add a use case to a use case diagram")
  public String addUseCase(String useCaseName, String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            IUseCase useCase = getModelElementFactory().createUseCase();
            addToDiagram(diagram, useCase, useCaseName);

            return "Added use case '" + useCaseName + "' to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding use case: " + e.getMessage();
    }
  }

  @Tool(
      name = "addRelationship",
      description =
          "Add a relationship between elements in a use case diagram. Direction by type: "
              + "Include -> source=base (main), target=included (sub); "
              + "Extend -> source=extending (sub), target=extended (main, owns extension point); "
              + "Generalization -> source=child, target=parent; "
              + "Association -> plain line, no arrow (DEFAULT for actor<->use case); "
              + "DirectedAssociation -> arrow from source to target, use ONLY when the "
              + "interaction is genuinely one-directional "
              + "(e.g. actor->use case, or use case->secondary actor)")
  public String addRelationship(
      String diagramName, String sourceName, String targetName, String relationshipType) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            // Search both IUseCase and IActor for source
            IModelElement source = findModelElement(sourceName, IUseCase.class, diagram);
            if (source == null) {
              source = findModelElement(sourceName, IActor.class, diagram);
            }
            if (source == null) {
              return "Source element not found: " + sourceName;
            }

            // Search both IUseCase and IActor for target
            IModelElement target = findModelElement(targetName, IUseCase.class, diagram);
            if (target == null) {
              target = findModelElement(targetName, IActor.class, diagram);
            }
            if (target == null) {
              return "Target element not found: " + targetName;
            }

            IDiagramElement fromElement = findDiagramElementByName(diagram, sourceName);
            IDiagramElement toElement = findDiagramElementByName(diagram, targetName);
            if (fromElement == null || toElement == null) {
              return "Element not on diagram: " + (fromElement == null ? sourceName : targetName);
            }

            DiagramManager dm = getDiagramManager();

            if ("Include".equalsIgnoreCase(relationshipType)) {
              IInclude include = getModelElementFactory().createInclude();
              include.setFrom(source);
              include.setTo(target);
              dm.createConnector(diagram, include, fromElement, toElement, null);
              return "Added Include from '" + sourceName + "' to '" + targetName + "'";
            } else if ("Extend".equalsIgnoreCase(relationshipType)) {
              // VP puts the extension point on the extend's `from`, so from = base (target).
              IExtend extend = getModelElementFactory().createExtend();
              extend.setFrom(target);
              extend.setTo(source);
              dm.createConnector(diagram, extend, toElement, fromElement, null);
              return "Added Extend from '" + sourceName + "' to '" + targetName + "'";
            } else if ("Generalization".equalsIgnoreCase(relationshipType)) {
              // VP draws the triangle at the generalization's `from`, so from = parent (target).
              IGeneralization gen = getModelElementFactory().createGeneralization();
              gen.setFrom(target);
              gen.setTo(source);
              dm.createConnector(diagram, gen, toElement, fromElement, null);
              return "Added Generalization from '" + sourceName + "' to '" + targetName + "'";
            } else if ("Association".equalsIgnoreCase(relationshipType)
                || "DirectedAssociation".equalsIgnoreCase(relationshipType)) {
              boolean directed = "DirectedAssociation".equalsIgnoreCase(relationshipType);
              IAssociation assoc = getModelElementFactory().createAssociation();
              assoc.setFrom(source);
              assoc.setTo(target);
              // VP defaults a fresh association end to NAVIGABLE. Pin both ends explicitly: a plain
              // association has no navigability arrows (both UNSPECIFIED); a directed one is
              // navigable only at the target. Leaving a plain association at the default makes its
              // actor end read as navigable, which addSystemBoundary misreads as "system calls the
              // actor" and pushes every primary actor to the secondary (right) column.
              IAssociationEnd fromEnd = (IAssociationEnd) assoc.getFromEnd();
              if (fromEnd != null) {
                fromEnd.setNavigable(IAssociationEnd.NAVIGABLE_UNSPECIFIED);
              }
              IAssociationEnd toEnd = (IAssociationEnd) assoc.getToEnd();
              if (toEnd != null) {
                toEnd.setNavigable(
                    directed
                        ? IAssociationEnd.NAVIGABLE_NAVIGABLE
                        : IAssociationEnd.NAVIGABLE_UNSPECIFIED);
              }
              dm.createConnector(diagram, assoc, fromElement, toElement, null);
              return "Added "
                  + (directed ? "DirectedAssociation" : "Association")
                  + " from '"
                  + sourceName
                  + "' to '"
                  + targetName
                  + "'";
            } else {
              return "Unknown relationship type: "
                  + relationshipType
                  + ". Use Include, Extend, Generalization, Association, or DirectedAssociation.";
            }
          });
    } catch (Exception e) {
      return "Error adding relationship: " + e.getMessage();
    }
  }

  @Tool(
      name = "addActorStereotype",
      description =
          "Add a stereotype to an actor of a use case diagram, e.g. 'system' for an actor that"
              + " is another system")
  public String addActorStereotype(String diagramName, String actorName, String stereotype) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IModelElement actor = findModelElement(actorName, IActor.class, diagram);
            if (actor == null) {
              return "Actor not found on diagram: " + actorName;
            }
            if (stereotype == null || stereotype.trim().isEmpty()) {
              return "stereotype is required";
            }
            ((IActor) actor).addStereotype(stereotype.trim());
            return "Added «" + stereotype.trim() + "» to actor '" + actorName + "'";
          });
    } catch (Exception e) {
      return "Error adding stereotype: " + e.getMessage();
    }
  }

  @Tool(
      name = "removeUseCaseElement",
      description =
          "Delete an actor or use case (and its relationships) from the MODEL by name, scoped to "
              + "the given use case diagram")
  public String removeUseCaseElement(String diagramName, String elementName) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            IModelElement element = findModelElement(elementName, IUseCase.class, diagram);
            if (element == null) {
              element = findModelElement(elementName, IActor.class, diagram);
            }
            if (element == null) {
              return "Element not found on diagram: " + elementName;
            }
            element.delete();
            return "Removed '" + elementName + "' from the model";
          });
    } catch (Exception e) {
      return "Error removing element: " + e.getMessage();
    }
  }

  @Tool(
      name = "removeUseCaseRelationship",
      description =
          "Delete relationship(s) between two elements on a use case diagram from the MODEL. "
              + "relationshipType: Include, Extend, Generalization or Association. Matches either "
              + "direction, so source/target order does not matter")
  public String removeUseCaseRelationship(
      String diagramName, String sourceName, String targetName, String relationshipType) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            List<IRelationship> matches =
                findUseCaseRelationships(diagram, sourceName, targetName, relationshipType);
            if (matches.isEmpty()) {
              return "No "
                  + relationshipType
                  + " found between '"
                  + sourceName
                  + "' and '"
                  + targetName
                  + "'";
            }
            for (IRelationship rel : matches) {
              rel.delete();
            }
            return "Removed "
                + matches.size()
                + " "
                + relationshipType
                + " between '"
                + sourceName
                + "' and '"
                + targetName
                + "'";
          });
    } catch (Exception e) {
      return "Error removing relationship: " + e.getMessage();
    }
  }

  @Tool(
      name = "nameExtensionPoint",
      description =
          "Set the name of the extension point of an Extend relationship (owned by the base/main "
              + "use case). Identify the extend by its two use cases (order-independent).")
  public String nameExtensionPoint(
      String diagramName, String extendingUseCase, String baseUseCase, String name) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            if (name == null || name.trim().isEmpty()) {
              return "Name is required";
            }
            List<IRelationship> matches =
                findUseCaseRelationships(diagram, extendingUseCase, baseUseCase, "Extend");
            if (matches.isEmpty()) {
              return "No Extend found between '" + extendingUseCase + "' and '" + baseUseCase + "'";
            }
            IExtend extend = (IExtend) matches.get(0);
            IExtensionPoint ep = extend.getExtensionPoint();
            if (ep == null) {
              ep = getModelElementFactory().createExtensionPoint();
              extend.setExtensionPoint(ep);
            }
            ep.setName(name.trim());
            return "Named extension point '"
                + name.trim()
                + "' on the extend between '"
                + extendingUseCase
                + "' and '"
                + baseUseCase
                + "'";
          });
    } catch (Exception e) {
      return "Error naming extension point: " + e.getMessage();
    }
  }

  @Tool(
      name = "nameUseCaseRelationship",
      description =
          "Set the name/label of relationship(s) between two elements on a use case diagram "
              + "(e.g. to denote timing on an association). relationshipType: Include, Extend, "
              + "Generalization or Association. Matches either direction; pass an empty name to "
              + "clear the label.")
  public String nameUseCaseRelationship(
      String diagramName,
      String sourceName,
      String targetName,
      String relationshipType,
      String name) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            List<IRelationship> matches =
                findUseCaseRelationships(diagram, sourceName, targetName, relationshipType);
            if (matches.isEmpty()) {
              return "No "
                  + relationshipType
                  + " found between '"
                  + sourceName
                  + "' and '"
                  + targetName
                  + "'";
            }
            String label = name == null ? "" : name.trim();
            for (IRelationship rel : matches) {
              rel.setName(label);
            }
            return "Named "
                + matches.size()
                + " "
                + relationshipType
                + " between '"
                + sourceName
                + "' and '"
                + targetName
                + "' to '"
                + label
                + "'";
          });
    } catch (Exception e) {
      return "Error naming relationship: " + e.getMessage();
    }
  }

  /** Relationships of the given type between two named elements, matching either direction. */
  private List<IRelationship> findUseCaseRelationships(
      IUseCaseDiagramUIModel diagram, String nameA, String nameB, String relationshipType) {
    String type =
        relationshipType == null ? "" : relationshipType.trim().toLowerCase(java.util.Locale.ROOT);
    List<IRelationship> result = new ArrayList<>();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (!(obj instanceof IDiagramElement)) {
        continue;
      }
      IModelElement model = ((IDiagramElement) obj).getModelElement();
      if (!(model instanceof IRelationship) || result.contains(model)) {
        continue;
      }
      IRelationship rel = (IRelationship) model;
      String from = rel.getFrom() != null ? rel.getFrom().getName() : null;
      String to = rel.getTo() != null ? rel.getTo().getName() : null;
      boolean endpointsMatch =
          (nameA.equals(from) && nameB.equals(to)) || (nameA.equals(to) && nameB.equals(from));
      if (!endpointsMatch) {
        continue;
      }
      boolean typeMatch;
      switch (type) {
        case "include":
          typeMatch = model instanceof IInclude;
          break;
        case "extend":
          typeMatch = model instanceof IExtend;
          break;
        case "generalization":
          typeMatch = model instanceof IGeneralization;
          break;
        case "association":
        case "directedassociation":
          typeMatch = model instanceof IAssociation;
          break;
        default:
          typeMatch = false;
      }
      if (typeMatch) {
        result.add(rel);
      }
    }
    return result;
  }

  @Tool(
      name = "addSystemBoundary",
      description =
          "Wrap all use cases of a use case diagram in a labeled system boundary rectangle "
              + "and move actors outside it: primary (initiating) actors left, secondary "
              + "(system-called) actors right. Call AFTER autoLayoutDiagram.")
  public String addSystemBoundary(String diagramName, String systemName) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            // Collect use case shapes (for the bounding box) and actor shapes (to place outside).
            List<IUseCase> useCases = new ArrayList<>();
            java.util.Map<IModelElement, IDiagramElement> ucDeByModel = new java.util.HashMap<>();
            List<IDiagramElement> actorDes = new ArrayList<>();
            List<IAssociation> associations = new ArrayList<>();
            List<IDiagramElement> systemDes = new ArrayList<>();
            int minX = Integer.MAX_VALUE;
            int minY = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE;
            int maxY = Integer.MIN_VALUE;
            Iterator<?> iter = diagram.diagramElementIterator();
            while (iter.hasNext()) {
              Object obj = iter.next();
              if (obj instanceof IDiagramElement) {
                IDiagramElement de = (IDiagramElement) obj;
                IModelElement model = de.getModelElement();
                if (model instanceof IUseCase) {
                  useCases.add((IUseCase) model);
                  ucDeByModel.put(model, de);
                  minX = Math.min(minX, de.getX());
                  minY = Math.min(minY, de.getY());
                  maxX = Math.max(maxX, de.getX() + de.getWidth());
                  maxY = Math.max(maxY, de.getY() + de.getHeight());
                } else if (model instanceof IActor) {
                  actorDes.add(de);
                } else if (model instanceof IAssociation) {
                  associations.add((IAssociation) model);
                } else if (model instanceof ISystem) {
                  systemDes.add(de);
                }
              }
            }
            if (useCases.isEmpty()) {
              return "No use cases found on diagram '" + diagramName + "' to wrap";
            }

            // Reuse an existing boundary so calling this again re-wraps instead of stacking a
            // second box; any further boundaries left empty by the move are deleted.
            IDiagramElement sysDe = systemDes.isEmpty() ? null : systemDes.get(0);
            ISystem system =
                sysDe != null
                    ? (ISystem) sysDe.getModelElement()
                    : getModelElementFactory().createSystem();
            system.setName(systemName);
            for (IUseCase uc : useCases) {
              system.addUseCase(uc);
            }
            for (IDiagramElement extra :
                systemDes.subList(Math.min(1, systemDes.size()), systemDes.size())) {
              IModelElement extraModel = extra.getModelElement();
              for (IShapeUIModel child : extra.toChildArray()) {
                extra.removeChild(child);
                sysDe.addChild(child);
              }
              if (extraModel.childCount() == 0) {
                extraModel.delete();
              }
            }

            int pad = 40;
            if (sysDe == null) {
              sysDe = getDiagramManager().createDiagramElement(diagram, system);
            }
            if (sysDe instanceof IShapeUIModel) {
              IShapeUIModel shape = (IShapeUIModel) sysDe;
              shape.setCustomText(systemName);
              shape.setBounds(minX - pad, minY - pad, maxX - minX + 2 * pad, maxY - minY + 2 * pad);
              shape.sendToBack();
            }

            // Move actors outside the box: primary (initiators) left, secondary (system-called)
            // right. Each actor is aligned vertically with the use cases it connects to, which
            // keeps association lines short and uncrossed; overlapping actors are then spread
            // apart.
            int boxLeft = minX - pad;
            int boxRight = maxX + pad;
            int gap = 70;
            List<ActorSlot> leftSlots = new ArrayList<>();
            List<ActorSlot> rightSlots = new ArrayList<>();
            for (IDiagramElement actorDe : actorDes) {
              IModelElement actorModel = actorDe.getModelElement();
              String actorName = actorModel.getName();
              // Secondary = a use case points a navigable arrow at the actor (system calls it),
              // or it carries the «System» stereotype. Everything else is a primary actor.
              boolean secondary = actorModel.hasStereotype("System");
              List<Integer> connectedCenters = new ArrayList<>();
              for (IAssociation a : associations) {
                IModelElement fromM = a.getFrom();
                IModelElement toM = a.getTo();
                String fromN = fromM != null ? fromM.getName() : null;
                String toN = toM != null ? toM.getName() : null;
                IAssociationEnd actorEnd = null;
                IModelElement otherM = null;
                if (actorName != null && actorName.equals(fromN)) {
                  actorEnd = (IAssociationEnd) a.getFromEnd();
                  otherM = toM;
                } else if (actorName != null && actorName.equals(toN)) {
                  actorEnd = (IAssociationEnd) a.getToEnd();
                  otherM = fromM;
                }
                if (actorEnd == null || !(otherM instanceof IUseCase)) {
                  continue;
                }
                if (actorEnd.getNavigable() == IAssociationEnd.NAVIGABLE_NAVIGABLE) {
                  secondary = true;
                }
                IDiagramElement ucDe = ucDeByModel.get(otherM);
                if (ucDe != null) {
                  connectedCenters.add(ucDe.getY() + ucDe.getHeight() / 2);
                }
              }
              int h = actorDe.getHeight();
              int desiredY;
              if (connectedCenters.isEmpty()) {
                desiredY = minY; // unconnected actor: top of the use-case band
              } else {
                int sum = 0;
                for (int c : connectedCenters) {
                  sum += c;
                }
                desiredY = sum / connectedCenters.size() - h / 2;
              }
              (secondary ? rightSlots : leftSlots).add(new ActorSlot(actorDe, desiredY));
            }
            placeActorColumn(leftSlots, boxLeft - gap, true);
            placeActorColumn(rightSlots, boxRight + gap, false);

            // Moving the actors leaves their association connectors anchored at the old positions,
            // so the arrows no longer touch the actor. Re-center every association connector (in a
            // UC diagram associations are exactly the actor<->use-case links; include/extend/
            // generalization keep their laid-out routing).
            Iterator<?> connIter = diagram.diagramElementIterator();
            while (connIter.hasNext()) {
              Object obj = connIter.next();
              if (obj instanceof IConnectorUIModel
                  && ((IConnectorUIModel) obj).getModelElement() instanceof IAssociation) {
                centerConnector((IConnectorUIModel) obj);
              }
            }

            return "Added system boundary '"
                + systemName
                + "' wrapping "
                + useCases.size()
                + " use case(s) on diagram '"
                + diagramName
                + "'";
          });
    } catch (Exception e) {
      return "Error adding system boundary: " + e.getMessage();
    }
  }

  /** An actor shape and the Y it would like to sit at (centred on its connected use cases). */
  private static final class ActorSlot {
    final IDiagramElement de;
    final int desiredY;

    ActorSlot(IDiagramElement de, int desiredY) {
      this.de = de;
      this.desiredY = desiredY;
    }
  }

  /**
   * Stack actors in one flanking column: sorted by desired Y, pulled apart so they never overlap.
   *
   * @param slots the actors to place
   * @param edgeX the column's inner edge (right edge of the shape when leftSide, else the left
   *     edge)
   * @param leftSide true for the primary-actor column left of the box, false for the right column
   */
  private static void placeActorColumn(List<ActorSlot> slots, int edgeX, boolean leftSide) {
    slots.sort((p, q) -> Integer.compare(p.desiredY, q.desiredY));
    int[] desired = new int[slots.size()];
    int[] heights = new int[slots.size()];
    for (int i = 0; i < slots.size(); i++) {
      desired[i] = slots.get(i).desiredY;
      heights[i] = slots.get(i).de.getHeight();
    }
    int[] ys = stackYs(desired, heights, 40);
    for (int i = 0; i < slots.size(); i++) {
      IDiagramElement de = slots.get(i).de;
      int w = de.getWidth();
      de.setBounds(leftSide ? edgeX - w : edgeX, ys[i], w, de.getHeight());
      de.resetCaption(); // keep the actor name under the moved figure
    }
  }

  /**
   * Non-overlapping Y positions for a column: each element sits at its desired Y, or just below the
   * previous element (its bottom plus {@code minGap}) if that would overlap. Expects inputs sorted
   * by desired Y. Pure function, unit-tested.
   *
   * @param desiredY each element's preferred top Y
   * @param heights each element's height
   * @param minGap minimum vertical gap between stacked elements
   * @return the resolved top Y of each element
   */
  static int[] stackYs(int[] desiredY, int[] heights, int minGap) {
    int[] ys = new int[desiredY.length];
    int cursor = Integer.MIN_VALUE;
    for (int i = 0; i < desiredY.length; i++) {
      int y = Math.max(desiredY[i], cursor);
      ys[i] = y;
      cursor = y + heights[i] + minGap;
    }
    return ys;
  }

  @Tool(
      name = "generateUseCaseReport",
      description = "Generate a use case analysis report for a diagram")
  public String generateReport(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }

            List<String> actorNames = new ArrayList<>();
            List<String> useCaseNames = new ArrayList<>();
            List<String> relationshipDetails = new ArrayList<>();
            java.util.Map<IModelElement, String> nameMap = new java.util.LinkedHashMap<>();

            Iterator<?> iter = diagram.diagramElementIterator();
            while (iter.hasNext()) {
              Object obj = iter.next();
              if (obj instanceof IDiagramElement) {
                IDiagramElement de = (IDiagramElement) obj;
                IModelElement model = de.getModelElement();
                String displayName = model.getName();
                if (de instanceof IShapeUIModel) {
                  String caption = ((IShapeUIModel) de).getCustomText();
                  if (caption != null && !caption.isEmpty()) {
                    displayName = caption;
                  }
                }
                nameMap.put(model, displayName);
                if (model instanceof IActor) {
                  actorNames.add(displayName);
                } else if (model instanceof IUseCase) {
                  useCaseNames.add(displayName);
                } else if (model instanceof IRelationship) {
                  String relType;
                  if (model instanceof IInclude) {
                    relType = "Include";
                  } else if (model instanceof IExtend) {
                    relType = "Extend";
                  } else if (model instanceof IGeneralization) {
                    relType = "Generalization";
                  } else if (model instanceof IAssociation) {
                    relType = "Association";
                  } else {
                    relType = "Relationship";
                  }
                  IRelationship rel = (IRelationship) model;
                  String from =
                      rel.getFrom() != null
                          ? nameMap.getOrDefault(rel.getFrom(), rel.getFrom().getName())
                          : "?";
                  String to =
                      rel.getTo() != null
                          ? nameMap.getOrDefault(rel.getTo(), rel.getTo().getName())
                          : "?";
                  relationshipDetails.add(relType + ": " + from + " -> " + to);
                }
              }
            }

            StringBuilder report = new StringBuilder();
            report.append("USE CASE REPORT: ").append(diagramName).append("\n");
            report.append("================================\n");
            report.append("Actors (").append(actorNames.size()).append("):\n");
            for (String name : actorNames) {
              report.append("  - ").append(name).append("\n");
            }
            report.append("Use Cases (").append(useCaseNames.size()).append("):\n");
            for (String name : useCaseNames) {
              report.append("  - ").append(name).append("\n");
            }
            report.append("Relationships (").append(relationshipDetails.size()).append("):\n");
            for (String rel : relationshipDetails) {
              report.append("  - ").append(rel).append("\n");
            }
            return report.toString();
          });
    } catch (Exception e) {
      return "Error generating report: " + e.getMessage();
    }
  }
}
