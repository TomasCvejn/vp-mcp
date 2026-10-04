package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.OptionalParam;
import com.brunnen.vp.mcp.tool.Tool;
import com.brunnen.vp.mcp.util.DiagramLayoutEngine;
import com.brunnen.vp.mcp.util.DiagramUtils;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.ICaptionUIModel;
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

  @Tool(
      name = "addActor",
      description =
          "Add an actor to a use case diagram. An actor of that name already in the project (on"
              + " another diagram) is shown here too instead of creating a second one")
  public String addActor(String actorName, String diagramName) {
    try {
      return runOnEdt(
          () ->
              show(
                  diagramName,
                  actorName,
                  DiagramUtils.findModelElementByName(actorName, IActor.class),
                  getModelElementFactory()::createActor,
                  "actor"));
    } catch (Exception e) {
      return "Error adding actor: " + e.getMessage();
    }
  }

  @Tool(
      name = "addUseCase",
      description =
          "Add a use case to a use case diagram. A use case of that name already in the project"
              + " (on another diagram) is shown here too instead of creating a second one")
  public String addUseCase(String useCaseName, String diagramName) {
    try {
      return runOnEdt(
          () ->
              show(
                  diagramName,
                  useCaseName,
                  DiagramUtils.findModelElementByName(useCaseName, IUseCase.class),
                  getModelElementFactory()::createUseCase,
                  "use case"));
    } catch (Exception e) {
      return "Error adding use case: " + e.getMessage();
    }
  }

  /**
   * Show {@code existing} on the diagram, or a new element from {@code create} when there is none:
   * in UML one actor appears on several diagrams, and VP refuses a second same-named element of a
   * type anyway.
   */
  private String show(
      String diagramName,
      String name,
      IModelElement existing,
      java.util.function.Supplier<IModelElement> create,
      String label) {
    IUseCaseDiagramUIModel diagram =
        (IUseCaseDiagramUIModel)
            DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
    if (diagram == null) {
      return "Diagram not found: " + diagramName;
    }
    if (existing != null && shownOn(existing, diagram) != null) {
      return "The " + label + " '" + name + "' is already on diagram '" + diagramName + "'";
    }
    addToDiagram(diagram, existing != null ? existing : create.get(), name);
    return "Added "
        + label
        + " '"
        + name
        + "' to diagram '"
        + diagramName
        + "'"
        + (existing != null ? " (the project's existing " + label + ", shared)" : "");
  }

  /** The shape of {@code model} on {@code diagram}, or null. */
  private static IDiagramElement shownOn(IModelElement model, IDiagramUIModel diagram) {
    for (IDiagramElement de : model.getDiagramElements()) {
      if (de.getDiagramUIModel() != null
          && diagram.getId().equals(de.getDiagramUIModel().getId())) {
        return de;
      }
    }
    return null;
  }

  @Tool(
      name = "addRelationship",
      description =
          "Add a relationship between elements in a use case diagram. Direction by type: "
              + "Include -> source=base (main), target=included (sub); "
              + "Extend -> source=extending (sub), target=extended (main, owns extension point); "
              + "Generalization -> source=child, target=parent; "
              + "Association -> plain line, no arrow: ALWAYS for a primary actor (the actor"
              + " starts the use case; source=actor, target=use case); "
              + "DirectedAssociation -> arrow at the target: ONLY use case -> secondary actor"
              + " (the system calls the actor; source=use case, target=actor). Never use it"
              + " from an actor to a use case")
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

            IDiagramElement fromElement =
                findElement(diagram, sourceName, IUseCase.class, IActor.class);
            IDiagramElement toElement =
                findElement(diagram, targetName, IUseCase.class, IActor.class);
            IModelElement source = fromElement.getModelElement();
            IModelElement target = toElement.getModelElement();

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
      name = "removeUseCaseElement",
      description =
          "Delete an actor or use case (and its relationships) from the MODEL by name, scoped to "
              + "the given use case diagram. One also shown on other diagrams is only removed from"
              + " this diagram, with its relationships here")
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
            // Only an element shown on this diagram: never a same-named one elsewhere.
            return removeFrom(
                diagram, findElement(diagram, elementName, IUseCase.class, IActor.class));
          });
    } catch (Exception e) {
      return "Error removing element: " + e.getMessage();
    }
  }

  /**
   * Delete a shape's model element, or, when other diagrams show it too, only the shape and its
   * relationships on this diagram.
   */
  private static String removeFrom(IUseCaseDiagramUIModel diagram, IDiagramElement shape) {
    IModelElement model = shape.getModelElement();
    String name = model.getName();
    if (model.getDiagramElements().length > 1) {
      List<IConnectorUIModel> lines = new ArrayList<>();
      lines.addAll(java.util.Arrays.asList(shape.toFromConnectorArray()));
      lines.addAll(java.util.Arrays.asList(shape.toToConnectorArray()));
      for (IConnectorUIModel line : lines) {
        if (line.getModelElement() != null) {
          line.getModelElement().delete();
        }
      }
      diagram.removeDiagramElement(shape);
      return "Removed '"
          + name
          + "' and its relationships from diagram '"
          + diagram.getName()
          + "'; it stays in the model, shown on other diagrams";
    }
    model.delete();
    return "Removed '" + name + "' from the model";
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
    // Both names must identify one actor/use case on the diagram; then matching relationship
    // ends by name below cannot pick up a same-named element.
    findElement(diagram, nameA, IUseCase.class, IActor.class);
    findElement(diagram, nameB, IUseCase.class, IActor.class);
    String type =
        relationshipType == null ? "" : relationshipType.trim().toLowerCase(java.util.Locale.ROOT);
    List<IRelationship> result = new ArrayList<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      IModelElement model = de.getModelElement();
      if (!(model instanceof IRelationship) || result.contains(model)) {
        continue;
      }
      IRelationship rel = (IRelationship) model;
      String from = nameOf(rel.getFrom());
      String to = nameOf(rel.getTo());
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
      name = "buildUseCaseDiagram",
      description =
          "Create a whole use case diagram in one call from a JSON spec, lay it out by the house"
              + " conventions inside a boundary named systemName and check the layout. spec:"
              + " {\"actors\": [names], \"stereotypes\": {name: \"system\"|\"time\"},"
              + " \"useCases\": [names], \"links\": [[primaryActor, useCase]] (plain line),"
              + " \"calls\": [[useCase, secondaryActor]] (arrow at the actor),"
              + " \"includes\": [[base, included]],"
              + " \"extends\": [[extending, base, extensionPoint?]],"
              + " \"generalizations\": [[childActor, parentActor]]}. The whole spec is validated"
              + " first; nothing is created when it has problems (also a cycle). The result ends"
              + " with checkLayout and checkUseCaseDiagram. replace=true first deletes an"
              + " existing diagram of that name with its elements (shared ones stay on other"
              + " diagrams), so a corrected spec can be rebuilt")
  public String buildUseCaseDiagram(
      String diagramName, String systemName, String spec, @OptionalParam boolean replace) {
    UseCaseSpec s;
    try {
      s = UseCaseSpec.parse(spec);
      boolean exists =
          runOnEdt(
              () ->
                  DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class)
                      != null);
      if (exists && !replace) {
        return "Diagram '" + diagramName + "' already exists; pick another name or replace=true";
      }
      if (exists) {
        String removed = runOnEdt(() -> deleteDiagram(diagramName));
        if (!removed.startsWith("Removed")) {
          return "Could not replace '" + diagramName + "': " + removed;
        }
      }
    } catch (Exception e) {
      return e.getMessage();
    }
    // Build with the single-step tools (their success messages start with Created/Added/Named;
    // a shared actor may already carry its stereotype), then report the first failed step.
    List<String> steps = new ArrayList<>();
    steps.add(createUseCaseDiagram(diagramName));
    for (String actor : s.actors) {
      steps.add(addActor(actor, diagramName));
    }
    for (String uc : s.useCases) {
      steps.add(addUseCase(uc, diagramName));
    }
    for (java.util.Map.Entry<String, String> st : s.stereotypes.entrySet()) {
      steps.add(addStereotype(diagramName, st.getKey(), st.getValue()));
    }
    for (String[] l : s.links) {
      steps.add(addRelationship(diagramName, l[0], l[1], "Association"));
    }
    for (String[] c : s.calls) {
      steps.add(addRelationship(diagramName, c[0], c[1], "DirectedAssociation"));
    }
    for (String[] i : s.includes) {
      steps.add(addRelationship(diagramName, i[0], i[1], "Include"));
    }
    for (String[] e : s.extendsList) {
      steps.add(addRelationship(diagramName, e[0], e[1], "Extend"));
      if (e.length == 3 && !e[2].isEmpty()) {
        steps.add(nameExtensionPoint(diagramName, e[0], e[1], e[2]));
      }
    }
    for (String[] g : s.generalizations) {
      steps.add(addRelationship(diagramName, g[0], g[1], "Generalization"));
    }
    for (String step : steps) {
      if (!step.startsWith("Created")
          && !step.startsWith("Added")
          && !step.startsWith("Named")
          && !step.contains("already has stereotype")) {
        return "Stopped building '" + diagramName + "' (partly created): " + step;
      }
    }
    // Rows follow the spec's use case order, so the AI can put them in a logical order.
    String layout = layout(diagramName, systemName, new ArrayList<>(s.useCases));
    return "Built '"
        + diagramName
        + "': "
        + s.actors.size()
        + " actors, "
        + s.useCases.size()
        + " use cases, "
        + (s.links.size()
            + s.calls.size()
            + s.includes.size()
            + s.extendsList.size()
            + s.generalizations.size())
        + " relationships\n"
        + layout
        + "\ncheckLayout: "
        + checkLayout(diagramName)
        + "\ncheckUseCaseDiagram: "
        + checkUseCaseDiagram(diagramName);
  }

  @Tool(
      name = "deleteUseCaseDiagram",
      description =
          "Delete a use case diagram with its actors, use cases and relationships. Elements also"
              + " shown on other diagrams stay there; the project is not saved")
  public String deleteUseCaseDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            if (DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class) == null) {
              return "Diagram not found: " + diagramName;
            }
            String r = deleteDiagram(diagramName);
            return r.startsWith("Removed") ? "Deleted diagram '" + diagramName + "'" : r;
          });
    } catch (Exception e) {
      return "Error deleting diagram: " + e;
    }
  }

  /**
   * Delete a use case diagram with its actors, use cases and relationships ({@link #removeFrom}).
   * The boundary is dissolved first: deleting its system would delete the use cases it owns, also
   * ones shown on other diagrams. On the EDT.
   */
  private String deleteDiagram(String diagramName) {
    IUseCaseDiagramUIModel diagram =
        (IUseCaseDiagramUIModel)
            DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
    for (IDiagramElement de : diagram.toDiagramElementArray()) {
      if (de.getModelElement() instanceof ISystem) {
        String r = removeSystemBoundary(diagram, de, de.getModelElement().getName());
        if (!r.startsWith("Removed")) {
          return r;
        }
      }
    }
    for (IDiagramElement de : diagram.toDiagramElementArray()) {
      IModelElement m = de.getModelElement();
      if (m instanceof IActor || m instanceof IUseCase) {
        removeFrom(diagram, de);
      }
    }
    diagram.delete();
    return "Removed";
  }

  @Tool(
      name = "checkUseCaseDiagram",
      description =
          "Check a use case diagram's model against the checklist items it answers for sure:"
              + " use cases inside one named boundary, named extension points, primary actors"
              + " left and secondary right, «time» on Time, arrowheads only at secondary actors,"
              + " includes with one base, elements without relationships, plus exact counts."
              + " Pass its output to the diagram reviewer as ground truth")
  public String checkUseCaseDiagram(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IUseCaseDiagramUIModel diagram =
                (IUseCaseDiagramUIModel)
                    DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
            if (diagram == null) {
              return "Diagram not found: " + diagramName;
            }
            UseCaseCheck c = new UseCaseCheck();
            for (IDiagramElement de : diagram.toDiagramElementArray()) {
              IModelElement m = de.getModelElement();
              if (m instanceof IActor) {
                List<String> stereotypes = new ArrayList<>();
                com.vp.plugin.model.IStereotype[] sts = m.toStereotypeModelArray();
                for (com.vp.plugin.model.IStereotype st :
                    sts != null ? sts : new com.vp.plugin.model.IStereotype[0]) { // null if none
                  stereotypes.add(st.getName());
                }
                c.actors.put(m.getName(), stereotypes);
                c.actorX.put(m.getName(), de.getX() + de.getWidth() / 2.0);
              } else if (m instanceof IUseCase) {
                // Inside is decided by geometry: use case shapes are not child shapes of the
                // boundary, and the model owner is wrong for a shared use case.
                c.useCases.put(
                    m.getName(),
                    new double[] {de.getX(), de.getY(), de.getWidth(), de.getHeight()});
              } else if (m instanceof ISystem) {
                c.boundaries.add(
                    new Object[] {
                      m.getName(),
                      (double) de.getX(),
                      (double) de.getY(),
                      (double) de.getWidth(),
                      (double) de.getHeight()
                    });
              } else if (m instanceof IAssociation) {
                IAssociation a = (IAssociation) m;
                c.associations.add(
                    new String[] {
                      a.getFrom().getName(),
                      a.getTo().getName(),
                      String.valueOf(navigable((IAssociationEnd) a.getFromEnd())),
                      String.valueOf(navigable((IAssociationEnd) a.getToEnd()))
                    });
              } else if (m instanceof IInclude) {
                IRelationship r = (IRelationship) m; // from = base, to = included
                c.includes.add(new String[] {r.getFrom().getName(), r.getTo().getName()});
              } else if (m instanceof IExtend) {
                IExtend e = (IExtend) m; // from = base, to = extending
                IExtensionPoint ep = e.getExtensionPoint();
                c.extendsList.add(
                    new String[] {
                      e.getTo().getName(),
                      e.getFrom().getName(),
                      ep != null && ep.getName() != null ? ep.getName() : ""
                    });
              } else if (m instanceof IGeneralization) {
                IRelationship r = (IRelationship) m; // from = parent, to = child
                c.generalizations.add(new String[] {r.getTo().getName(), r.getFrom().getName()});
              }
            }
            List<String> out = c.run();
            return out.get(0)
                + "\n"
                + (out.size() == 1 ? "OK" : String.join("\n", out.subList(1, out.size())))
                + "\n(ground truth for "
                + UseCaseCheck.COVERS
                + ")";
          });
    } catch (Exception e) {
      return "Error checking use case diagram: " + e;
    }
  }

  private static boolean navigable(IAssociationEnd end) {
    return end != null && end.getNavigable() == IAssociationEnd.NAVIGABLE_NAVIGABLE;
  }

  @Tool(
      name = "layoutUseCaseDiagram",
      description =
          "Lay out a use case diagram by the house conventions: use cases in a grid (column by"
              + " include/extend depth, rows grouped per primary actor, a generalization child"
              + " right after its parent), then wrap them in a system boundary named systemName,"
              + " place actors (primary left, secondary right) and re-anchor all lines. Follow"
              + " with exportDiagramImage and checkLayout")
  public String layoutUseCaseDiagram(String diagramName, String systemName) {
    return layout(diagramName, systemName, null);
  }

  /** {@link #layoutUseCaseDiagram} with rows in {@code order}, or by name when it is null. */
  private String layout(String diagramName, String systemName, List<String> order) {
    try {
      String placed =
          runOnEdt(
              () -> {
                IUseCaseDiagramUIModel diagram =
                    (IUseCaseDiagramUIModel)
                        DiagramUtils.findDiagramByName(diagramName, IUseCaseDiagramUIModel.class);
                if (diagram == null) {
                  return null;
                }
                return placeUseCasesOnGrid(diagram, order);
              });
      if (placed == null) {
        return "Diagram not found: " + diagramName;
      }
      String boundary = addSystemBoundary(diagramName, systemName);
      runOnEdt(
          () -> {
            IDiagramUIModel diagram = DiagramUtils.findDiagramByName(diagramName);
            for (IDiagramElement de : getDiagramElementsList(diagram)) {
              if (de instanceof IConnectorUIModel) {
                centerConnector((IConnectorUIModel) de);
              }
            }
          });
      String renderError = renderDiagram(diagramName);
      if (renderError == null) {
        runOnEdt(() -> placeRelationshipLabels(DiagramUtils.findDiagramByName(diagramName)));
      }
      return placed + "\n" + boundary;
    } catch (Exception e) {
      return "Error laying out diagram: " + e.getMessage();
    }
  }

  // Grid geometry: a 160x60 use case cell, columns 380 px and rows 90 px apart.
  private static final int GRID_X = 360;
  private static final int GRID_Y = 100;
  private static final int COLUMN_STEP = 380;
  private static final int CAPTION_LINE = 15;
  private static final int ROW_STEP = 90; // 30 px between use cases keeps tall diagrams compact
  private static final int CELL_W = 160;
  private static final int CELL_H = 60;
  // A base use case with its extension points compartment; 80 px still leaves 20 px to the
  // neighbouring rows.
  private static final int EXTENDED_W = 200;
  private static final int EXTENDED_H = 80;

  /**
   * Put each «include»/«extend» label beside the middle of its own line, on the side away from the
   * other lines, so it cannot be read as another line's label. On the EDT, after rendering (routes
   * and caption sizes are only known then).
   */
  private void placeRelationshipLabels(IDiagramUIModel diagram) {
    List<IConnectorUIModel> connectors = new ArrayList<>();
    for (IDiagramElement de : getDiagramElementsList(diagram)) {
      if (de instanceof IConnectorUIModel && ((IConnectorUIModel) de).getPoints() != null) {
        connectors.add((IConnectorUIModel) de);
      }
    }
    for (IConnectorUIModel c : connectors) {
      IModelElement model = c.getModelElement();
      ICaptionUIModel cap = c.getCaptionUIModel();
      if (!(model instanceof IInclude || model instanceof IExtend) || cap == null) {
        continue;
      }
      List<java.awt.geom.Line2D> others = new ArrayList<>();
      for (IConnectorUIModel o : connectors) {
        if (!o.getId().equals(c.getId())) {
          others.add(segment(o));
        }
      }
      java.awt.Point spot =
          LayoutCheck.labelSpot(segment(c), others, cap.getWidth(), cap.getHeight());
      cap.setBounds(spot.x, spot.y, cap.getWidth(), cap.getHeight());
    }
  }

  /** A connector as the straight line between its first and last point. */
  private static java.awt.geom.Line2D segment(IConnectorUIModel c) {
    java.awt.Point[] p = c.getPoints();
    return new java.awt.geom.Line2D.Double(p[0], p[p.length - 1]);
  }

  /** Reads the diagram into {@link UseCaseGrid}, moves every use case to its cell. On the EDT. */
  private String placeUseCasesOnGrid(IUseCaseDiagramUIModel diagram, List<String> order) {
    List<IDiagramElement> elements = getDiagramElementsList(diagram);
    java.util.Map<String, IDiagramElement> ucShapes = new java.util.LinkedHashMap<>();
    List<IModelElement> actors = new ArrayList<>();
    List<IAssociation> associations = new ArrayList<>();
    List<String[]> deps = new ArrayList<>();
    java.util.Map<String, String> actorParent = new java.util.HashMap<>();
    java.util.Set<String> extended = new java.util.HashSet<>(); // bases with extension points
    for (IDiagramElement de : elements) {
      IModelElement m = de.getModelElement();
      if (m instanceof IUseCase) {
        ucShapes.put(m.getName(), de);
      } else if (m instanceof IActor) {
        actors.add(m);
      } else if (m instanceof IAssociation) {
        associations.add((IAssociation) m);
      } else if (m instanceof IInclude || m instanceof IExtend) {
        // Both are stored from = base use case, to = included / extending use case.
        IRelationship r = (IRelationship) m;
        deps.add(new String[] {r.getFrom().getName(), r.getTo().getName()});
        if (m instanceof IExtend) {
          extended.add(r.getFrom().getName());
        }
      } else if (m instanceof IGeneralization) {
        IRelationship r = (IRelationship) m; // from = parent, to = child
        actorParent.put(r.getTo().getName(), r.getFrom().getName());
      }
    }
    List<String> rows = new ArrayList<>(order != null ? order : ucShapes.keySet());
    if (order == null) {
      java.util.Collections.sort(rows); // VP's element order changes between sessions
    }
    java.util.Map<String, List<String>> actorUseCases = new java.util.LinkedHashMap<>();
    java.util.Set<String> secondaryLinked = new java.util.HashSet<>();
    for (IModelElement actor : actors) {
      boolean secondary = isSecondary(actor, associations);
      List<String> linked = new ArrayList<>();
      for (IAssociation a : associations) {
        IModelElement other = otherEnd(a, actor);
        if (other instanceof IUseCase) {
          linked.add(other.getName());
        }
      }
      if (secondary) {
        secondaryLinked.addAll(linked);
      } else {
        actorUseCases.put(actor.getName(), linked);
      }
    }
    java.util.Map<String, java.awt.Point> cells =
        UseCaseGrid.plan(rows, actorUseCases, actorParent, deps, secondaryLinked);
    for (java.util.Map.Entry<String, java.awt.Point> e : cells.entrySet()) {
      IDiagramElement de = ucShapes.get(e.getKey());
      int w = de.getWidth();
      int h = de.getHeight();
      if (extended.contains(e.getKey())) {
        // Name, "extension points" and the point: VP's fitted size left the text cramped.
        w = Math.max(w, EXTENDED_W);
        h = Math.max(h, EXTENDED_H);
      }
      // Center in the cell, so bigger ellipses (extension points) stay on the row/column axis.
      de.setBounds(
          GRID_X + e.getValue().x * COLUMN_STEP + (CELL_W - w) / 2,
          GRID_Y + e.getValue().y * ROW_STEP + (CELL_H - h) / 2,
          w,
          h);
      de.resetCaption();
    }
    return "Placed " + cells.size() + " use case(s) on a grid";
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
              // A freshly created boundary keeps its caption at the pre-bounds spot (not shown).
              shape.resetCaption();
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
              boolean secondary = isSecondary(actorModel, associations);
              int sum = 0;
              int count = 0;
              for (IAssociation a : associations) {
                IModelElement other = otherEnd(a, actorModel);
                IDiagramElement ucDe = other instanceof IUseCase ? ucDeByModel.get(other) : null;
                if (ucDe != null) {
                  sum += ucDe.getY() + ucDe.getHeight() / 2;
                  count++;
                }
              }
              int desiredY =
                  count == 0
                      ? minY // unconnected actor: top of the use-case band
                      : sum / count - actorDe.getHeight() / 2;
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

  /**
   * Secondary actor = the system calls it: a linked use case points a navigable arrow at it, or it
   * carries the «system» stereotype (any case). Everything else is a primary actor.
   */
  static boolean isSecondary(IModelElement actor, List<IAssociation> associations) {
    if (actor instanceof IActor) {
      for (String st : ((IActor) actor).toStereotypeArray()) {
        if ("system".equalsIgnoreCase(st)) {
          return true;
        }
      }
    }
    for (IAssociation a : associations) {
      IAssociationEnd end = null;
      if (sameElement(a.getFrom(), actor)) {
        end = (IAssociationEnd) a.getFromEnd();
      } else if (sameElement(a.getTo(), actor)) {
        end = (IAssociationEnd) a.getToEnd();
      }
      if (end != null && end.getNavigable() == IAssociationEnd.NAVIGABLE_NAVIGABLE) {
        return true;
      }
    }
    return false;
  }

  /** The element at the other end of {@code rel} from {@code element}, or null if not attached. */
  private static IModelElement otherEnd(IRelationship rel, IModelElement element) {
    if (sameElement(rel.getFrom(), element)) {
      return rel.getTo();
    }
    return sameElement(rel.getTo(), element) ? rel.getFrom() : null;
  }

  /** VP returns non-canonical wrappers, so model elements are compared by id. */
  private static boolean sameElement(IModelElement a, IModelElement b) {
    return a != null && b != null && a.getId().equals(b.getId());
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
      // The name caption hangs below the figure; a stereotype adds a second caption line.
      IModelElement actor = slots.get(i).de.getModelElement();
      int captionLines = actor != null && actor.stereotypeCount() > 0 ? 2 : 1;
      heights[i] = slots.get(i).de.getHeight() + (captionLines - 1) * CAPTION_LINE;
    }
    // A 30 px gap keeps plain actors (60 tall) one row (90) apart level with their use cases;
    // an actor with a two-line caption pushes the next one down by a caption line.
    int[] ys = stackYs(desired, heights, 30);
    for (int i = 0; i < slots.size(); i++) {
      IDiagramElement de = slots.get(i).de;
      // The stick figure is ~40 px wide; a wider box makes arrows stop short of it (C2).
      int w = DiagramLayoutEngine.ACTOR_WIDTH;
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
