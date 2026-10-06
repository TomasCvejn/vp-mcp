package com.brunnen.vp.mcp.tools;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The checklist items of a use case diagram that its model answers for sure, so the reviewer does
 * not have to estimate them from the picture. Pure (no VP API): checkUseCaseDiagram fills the
 * fields from the diagram.
 */
final class UseCaseCheck {

  /**
   * Ids the model answers completely; for these the result is the ground truth. It also reports the
   * parts of §1.5, §1.6 and BP3 the model shows (a wrong arrowhead, an actor named like the system,
   * Time without «time»), but whether an arrow or a Time actor is missing where the meaning needs
   * one stays the reviewer's call.
   */
  static final String COVERS = "SYN1 SYN5 BP1 BP2 C3 C4 §1.11";

  /** actor -> its stereotypes. */
  final Map<String, List<String>> actors = new LinkedHashMap<>();

  /** actor -> x of its center. */
  final Map<String, Double> actorX = new HashMap<>();

  /** use case -> its shape {x, y, width, height}. */
  final Map<String, double[]> useCases = new LinkedHashMap<>();

  /** {name, x, y, width, height} of each system boundary. */
  final List<Object[]> boundaries = new ArrayList<>();

  /** {from, to, "arrow at from" ("true"/"false"), "arrow at to"}. */
  final List<String[]> associations = new ArrayList<>();

  /** {base, included}. */
  final List<String[]> includes = new ArrayList<>();

  /** {extending, base, extension point name or ""}. */
  final List<String[]> extendsList = new ArrayList<>();

  /** {child, parent}. */
  final List<String[]> generalizations = new ArrayList<>();

  /** The counts line followed by one line per problem, each starting with its id. */
  List<String> run() {
    List<String> out = new ArrayList<>();
    out.add(
        actors.size()
            + " actors, "
            + useCases.size()
            + " use cases, "
            + (associations.size() + includes.size() + extendsList.size() + generalizations.size())
            + " relationships");
    String system = boundaries.size() == 1 ? (String) boundaries.get(0)[0] : null;
    if (boundaries.isEmpty()) {
      out.add("SYN1: no system boundary");
    } else if (boundaries.size() > 1) {
      out.add("BP2: " + boundaries.size() + " system boundaries; model one system");
    } else if (system == null || system.trim().isEmpty()) {
      out.add("BP1: the system boundary has no name");
    }
    for (Map.Entry<String, double[]> uc : useCases.entrySet()) {
      if (!boundaries.isEmpty() && !insideSomeBoundary(uc.getValue())) {
        out.add("SYN1: use case '" + uc.getKey() + "' is outside the system boundary");
      }
    }
    for (String[] e : extendsList) {
      // VP names a new extension point "ExtensionPoint" (then "ExtensionPoint2", ...).
      if (e[2].trim().isEmpty() || e[2].trim().matches("ExtensionPoint\\d*")) {
        out.add(
            "SYN5: '" + e[1] + "' has no named extension point for the extend from '" + e[0] + "'");
      }
    }
    for (String[] a : associations) {
      boolean arrowFrom = Boolean.parseBoolean(a[2]);
      boolean arrowTo = Boolean.parseBoolean(a[3]);
      if (arrowFrom && arrowTo) {
        out.add("§1.5: the line between '" + a[0] + "' and '" + a[1] + "' has two arrowheads");
      }
      for (int end = 0; end < 2; end++) {
        String at = a[end];
        String other = a[1 - end];
        if (!(end == 0 ? arrowFrom : arrowTo)) {
          continue;
        }
        if (!actors.containsKey(at) && actors.containsKey(other)) {
          out.add(
              "§1.5: arrowhead at use case '"
                  + at
                  + "' on the line from '"
                  + other
                  + "'; an actor starting a use case has a plain line, an arrow points only at"
                  + " a secondary actor");
        }
      }
    }
    if (boundaries.size() == 1) {
      Set<String> secondary = rightSide();
      double left = num(boundaries.get(0)[1]);
      double right = left + num(boundaries.get(0)[3]);
      for (String actor : actors.keySet()) {
        Double x = actorX.get(actor);
        if (x == null) {
          continue;
        }
        if (secondary.contains(actor) && x < right) {
          out.add(
              "C3: secondary, «system» or «time» actor '"
                  + actor
                  + "' is not right of the boundary");
        } else if (!secondary.contains(actor) && x > left) {
          out.add("C3: primary actor '" + actor + "' is not left of the boundary");
        }
      }
    }
    Set<String> used = new HashSet<>();
    for (List<String[]> rel :
        java.util.Arrays.asList(associations, includes, extendsList, generalizations)) {
      for (String[] r : rel) {
        used.add(r[0]);
        used.add(r[1]);
      }
    }
    for (Map.Entry<String, List<String>> a : actors.entrySet()) {
      String actor = a.getKey();
      if (!used.contains(actor)) {
        out.add("actor '" + actor + "' has no relationship");
      }
      if ("System".equalsIgnoreCase(actor) || actor.equalsIgnoreCase(system)) {
        out.add("§1.6: actor '" + actor + "' stands for the modelled system");
      }
      if ("Time".equalsIgnoreCase(actor) && !hasIgnoreCase(a.getValue(), "time")) {
        out.add("C4/BP3: actor '" + actor + "' has no «time» stereotype");
      }
    }
    for (String uc : useCases.keySet()) {
      if (!used.contains(uc)) {
        out.add("use case '" + uc + "' has no relationship");
      }
    }
    Map<String, Integer> bases = new LinkedHashMap<>();
    for (String[] i : includes) {
      bases.merge(i[1], 1, Integer::sum);
    }
    for (Map.Entry<String, Integer> b : bases.entrySet()) {
      if (b.getValue() == 1) {
        out.add(
            "§1.11: '"
                + b.getKey()
                + "' is included by one use case only; put its steps into the base");
      }
    }
    return out;
  }

  /**
   * The actors drawn right of the boundary (C3): an actor that is only ever called, and every actor
   * with the «system» or «time» stereotype. Any other actor that starts a use case (no arrowhead at
   * it) goes left, even if another use case calls it. A generalization tree stays together on one
   * side: left when any of its unstereotyped actors starts a use case.
   */
  Set<String> rightSide() {
    Set<String> starts = new HashSet<>();
    Set<String> right = new HashSet<>();
    for (String[] a : associations) {
      for (int end = 0; end < 2; end++) {
        if (actors.containsKey(a[end])) {
          (Boolean.parseBoolean(a[2 + end]) ? right : starts).add(a[end]);
        }
      }
    }
    for (Map.Entry<String, List<String>> a : actors.entrySet()) {
      if (hasIgnoreCase(a.getValue(), "system") || hasIgnoreCase(a.getValue(), "time")) {
        starts.remove(a.getKey());
        right.add(a.getKey());
      }
    }
    spreadOverGeneralizations(starts);
    right.removeAll(starts);
    spreadOverGeneralizations(right); // only trees without a left member are left in it
    return right;
  }

  /** Adds every actor of a generalization tree that has a member in {@code side}. */
  private void spreadOverGeneralizations(Set<String> side) {
    for (boolean grew = true; grew; ) {
      grew = false;
      for (String[] g : generalizations) {
        if (side.contains(g[0]) || side.contains(g[1])) {
          grew |= side.add(g[0]) | side.add(g[1]);
        }
      }
    }
  }

  /** The shape lies wholly within one boundary (SYN1 is about the picture, not the model). */
  private boolean insideSomeBoundary(double[] r) {
    for (Object[] b : boundaries) {
      double x = num(b[1]);
      double y = num(b[2]);
      if (r[0] >= x && r[1] >= y && r[0] + r[2] <= x + num(b[3]) && r[1] + r[3] <= y + num(b[4])) {
        return true;
      }
    }
    return false;
  }

  private static double num(Object o) {
    return ((Number) o).doubleValue();
  }

  private static boolean hasIgnoreCase(List<String> values, String wanted) {
    for (String v : values) {
      if (wanted.equalsIgnoreCase(v)) {
        return true;
      }
    }
    return false;
  }
}
