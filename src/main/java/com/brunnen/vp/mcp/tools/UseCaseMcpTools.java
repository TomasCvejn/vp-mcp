package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.DiagramManager;
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
              if (directed) {
                IAssociationEnd fromEnd = (IAssociationEnd) assoc.getFromEnd();
                if (fromEnd != null) {
                  fromEnd.setNavigable(IAssociationEnd.NAVIGABLE_UNSPECIFIED);
                }
                IAssociationEnd toEnd = (IAssociationEnd) assoc.getToEnd();
                if (toEnd != null) {
                  toEnd.setNavigable(IAssociationEnd.NAVIGABLE_NAVIGABLE);
                }
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
            List<IDiagramElement> actorDes = new ArrayList<>();
            List<IAssociation> associations = new ArrayList<>();
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
                  minX = Math.min(minX, de.getX());
                  minY = Math.min(minY, de.getY());
                  maxX = Math.max(maxX, de.getX() + de.getWidth());
                  maxY = Math.max(maxY, de.getY() + de.getHeight());
                } else if (model instanceof IActor) {
                  actorDes.add(de);
                } else if (model instanceof IAssociation) {
                  associations.add((IAssociation) model);
                }
              }
            }
            if (useCases.isEmpty()) {
              return "No use cases found on diagram '" + diagramName + "' to wrap";
            }

            ISystem system = getModelElementFactory().createSystem();
            system.setName(systemName);
            for (IUseCase uc : useCases) {
              system.addUseCase(uc);
            }

            int pad = 40;
            IDiagramElement sysDe = getDiagramManager().createDiagramElement(diagram, system);
            if (sysDe instanceof IShapeUIModel) {
              IShapeUIModel shape = (IShapeUIModel) sysDe;
              shape.setCustomText(systemName);
              shape.setBounds(minX - pad, minY - pad, maxX - minX + 2 * pad, maxY - minY + 2 * pad);
              shape.sendToBack();
            }

            // Move actors outside the box: primary (initiators) left, secondary (system-called)
            // right, each stacked vertically.
            int boxLeft = minX - pad;
            int boxRight = maxX + pad;
            int gap = 70;
            int leftY = minY - pad;
            int rightY = minY - pad;
            for (IDiagramElement actorDe : actorDes) {
              IModelElement actorModel = actorDe.getModelElement();
              String actorName = actorModel.getName();
              // Secondary = a use case points a navigable arrow at the actor (system calls it),
              // or it carries the «System» stereotype. Everything else is a primary actor.
              boolean secondary = actorModel.hasStereotype("System");
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
                if (actorEnd != null
                    && otherM instanceof IUseCase
                    && actorEnd.getNavigable() == IAssociationEnd.NAVIGABLE_NAVIGABLE) {
                  secondary = true;
                  break;
                }
              }
              int w = actorDe.getWidth();
              int h = actorDe.getHeight();
              if (secondary) {
                actorDe.setBounds(boxRight + gap, rightY, w, h);
                rightY += h + 40;
              } else {
                actorDe.setBounds(boxLeft - gap - w, leftY, w, h);
                leftY += h + 40;
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
