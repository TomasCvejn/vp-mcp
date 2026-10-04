package com.brunnen.vp.mcp.tools;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure grid planner for use case diagrams (no VP API, unit-testable). Places every use case on a
 * (column, row) cell:
 *
 * <ul>
 *   <li>column = 0 for a use case nobody includes and that extends nothing, otherwise one more than
 *       its base (include: base -> included, extend: base -> extending);
 *   <li>primary actors are walked by number of use cases (most first, then by name), a
 *       generalization child right after its parent and an actor whose use case depends on another
 *       actor's use case right after that actor; each gets consecutive rows (use cases in the given
 *       order), use cases another actor depends on or is linked to last;
 *   <li>a deeper use case linked directly to a primary actor gets a row of its own with the cells
 *       left of it empty, so the actor's line does not run through another use case;
 *   <li>other deeper use cases take the nearest usable row to their base's row, never a cell on an
 *       actor's line (left of an actor-linked or right of a secondary-linked use case); above first
 *       when the base's row holds a use case with another base below, so its line from below does
 *       not cross this one.
 * </ul>
 */
final class UseCaseGrid {

  private UseCaseGrid() {}

  /**
   * Plan the grid cell of every use case.
   *
   * @param useCases all use cases, in the order rows follow (the spec's order, or by name: never
   *     VP's element order, which changes between sessions)
   * @param actorUseCases primary actor -> its directly associated use cases (insertion order kept)
   * @param actorParent generalization child actor -> parent actor
   * @param deps {base, dependent} pairs: include (base, included) and extend (base, extending)
   * @param secondaryLinked use cases with a line to a secondary actor (drawn to the right)
   * @return use case -> cell, x = column, y = row
   */
  static Map<String, Point> plan(
      List<String> useCases,
      Map<String, List<String>> actorUseCases,
      Map<String, String> actorParent,
      List<String[]> deps,
      Set<String> secondaryLinked) {
    Map<String, List<String>> bases = new HashMap<>();
    for (String[] d : deps) {
      bases.computeIfAbsent(d[1], k -> new ArrayList<>()).add(d[0]);
    }
    Map<String, Integer> column = new HashMap<>();
    for (String uc : useCases) {
      column(uc, bases, column, new HashSet<>());
    }
    Map<String, Integer> rank = new HashMap<>();
    for (String uc : useCases) {
      rank.putIfAbsent(uc, rank.size());
    }
    java.util.Comparator<String> given =
        java.util.Comparator.comparingInt(uc -> rank.getOrDefault(uc, Integer.MAX_VALUE));
    Grid grid = new Grid(secondaryLinked);
    Map<String, String> owner = owners(actorUseCases);
    for (String actor : actorOrder(actorUseCases, actorParent, bases)) {
      for (String uc : basesOfOthersLast(actor, actorUseCases, bases, owner, given)) {
        if (!grid.cells.containsKey(uc)) {
          // Every directly linked use case opens a new row; a deeper one leaves the cells left of
          // it empty, so the actor's line does not run through another use case.
          int col = column.getOrDefault(uc, 0);
          int row = grid.rows++;
          grid.put(uc, col, row);
          grid.leftLine.put(row, col);
        }
      }
    }
    // Deeper use cases follow their base; process by column so a base is always placed first
    // (and a use case drawing a line to the right is placed before anything right of it).
    List<String> rest = new ArrayList<>(useCases);
    rest.removeAll(grid.cells.keySet());
    rest.sort(given);
    rest.sort((a, b) -> column.getOrDefault(a, 0) - column.getOrDefault(b, 0));
    for (String uc : rest) {
      int col = column.getOrDefault(uc, 0);
      int base = grid.rows;
      for (String b : bases.getOrDefault(uc, new ArrayList<>())) {
        Point p = grid.cells.get(b);
        if (p != null) {
          base = p.y;
          break;
        }
      }
      // Nearest usable row to the base: base, +1, -1, +2, -2, ... or upwards first when the use
      // case already in the base's row has another base below (its line comes from below).
      int dir = 1;
      for (Map.Entry<String, Point> e : grid.cells.entrySet()) {
        if (e.getValue().equals(new Point(col, base))) {
          for (String b : bases.getOrDefault(e.getKey(), new ArrayList<>())) {
            Point bp = grid.cells.get(b);
            if (bp != null && bp.y > base) {
              dir = -1;
            }
          }
        }
      }
      int row = base;
      for (int d = 1; grid.blocked(col, row); d++) {
        row = base + dir * ((d & 1) == 1 ? (d + 1) / 2 : -(d / 2));
      }
      grid.put(uc, col, row);
      grid.rows = Math.max(grid.rows, row + 1);
    }
    return grid.cells;
  }

  /** Placed cells plus the rows that actor lines run along. */
  private static final class Grid {
    final Map<String, Point> cells = new LinkedHashMap<>();
    final Set<Point> taken = new HashSet<>();
    // row -> column of a use case linked from a primary actor on the left: cells left of it are
    // on that line.
    final Map<Integer, Integer> leftLine = new HashMap<>();
    // row -> column of a use case linked to a secondary actor on the right: cells right of it are
    // on that line.
    final Map<Integer, Integer> rightLine = new HashMap<>();
    final Set<String> secondaryLinked;
    int rows;

    Grid(Set<String> secondaryLinked) {
      this.secondaryLinked = secondaryLinked;
    }

    void put(String uc, int col, int row) {
      Point p = new Point(col, row);
      cells.put(uc, p);
      taken.add(p);
      if (secondaryLinked.contains(uc)) {
        rightLine.put(row, col);
      }
    }

    boolean blocked(int col, int row) {
      return row < 0
          || taken.contains(new Point(col, row))
          || (leftLine.containsKey(row) && col < leftLine.get(row))
          || (rightLine.containsKey(row) && col > rightLine.get(row));
    }
  }

  /** use case -> the first primary actor linked to it. */
  private static Map<String, String> owners(Map<String, List<String>> actorUseCases) {
    Map<String, String> owner = new HashMap<>();
    for (Map.Entry<String, List<String>> e : actorUseCases.entrySet()) {
      for (String uc : e.getValue()) {
        owner.putIfAbsent(uc, e.getKey());
      }
    }
    return owner;
  }

  /**
   * The actor's use cases with those that are a base of another actor's use case moved last, so
   * that other actor's rows (which follow) sit right below them.
   */
  private static List<String> basesOfOthersLast(
      String actor,
      Map<String, List<String>> actorUseCases,
      Map<String, List<String>> bases,
      Map<String, String> owner,
      java.util.Comparator<String> given) {
    Set<String> basesOfOthers = new HashSet<>();
    for (Map.Entry<String, List<String>> e : bases.entrySet()) {
      String depOwner = owner.get(e.getKey());
      if (depOwner != null && !depOwner.equals(actor)) {
        basesOfOthers.addAll(e.getValue());
      }
    }
    // A use case another primary actor is linked to as well goes last too: that actor stands
    // below, so its line reaches the use case without crossing this actor's other lines.
    for (Map.Entry<String, List<String>> e : actorUseCases.entrySet()) {
      if (!e.getKey().equals(actor)) {
        basesOfOthers.addAll(e.getValue());
      }
    }
    List<String> ordered = new ArrayList<>(actorUseCases.get(actor));
    ordered.sort(given);
    ordered.sort((a, b) -> Boolean.compare(basesOfOthers.contains(a), basesOfOthers.contains(b)));
    return ordered;
  }

  private static int column(
      String uc, Map<String, List<String>> bases, Map<String, Integer> memo, Set<String> path) {
    Integer known = memo.get(uc);
    if (known != null) {
      return known;
    }
    int col = 0;
    if (path.add(uc)) { // an include/extend cycle stops here instead of recursing forever
      for (String base : bases.getOrDefault(uc, new ArrayList<>())) {
        col = Math.max(col, column(base, bases, memo, path) + 1);
      }
      path.remove(uc);
    }
    memo.put(uc, col);
    return col;
  }

  /**
   * Actors in given order, but each generalization child directly after its parent, and a group
   * (actor with its descendants) whose use case depends on another group's use case right after
   * that group.
   */
  static List<String> actorOrder(
      Map<String, List<String>> actorUseCases,
      Map<String, String> actorParent,
      Map<String, List<String>> bases) {
    // Most use cases first, then by name: VP's element order changes between sessions, and the
    // main actor at the top keeps its long fan clear of the others.
    List<String> names = new ArrayList<>(actorUseCases.keySet());
    names.sort(
        java.util.Comparator.<String>comparingInt(a -> -actorUseCases.get(a).size())
            .thenComparing(java.util.Comparator.naturalOrder()));
    List<List<String>> groups = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (boolean cycles : new boolean[] {false, true}) {
      for (String actor : names) {
        if (!seen.contains(actor)
            && (cycles || !actorUseCases.containsKey(actorParent.get(actor)))) {
          List<String> group = new ArrayList<>();
          addWithChildren(actor, actorUseCases, actorParent, group);
          group.removeAll(seen);
          seen.addAll(group);
          groups.add(group);
        }
      }
    }
    Map<String, List<String>> groupOf = new HashMap<>();
    for (List<String> g : groups) {
      for (String actor : g) {
        for (String uc : actorUseCases.get(actor)) {
          groupOf.putIfAbsent(uc, g);
        }
      }
    }
    Map<List<String>, List<String>> hostOf = new HashMap<>();
    for (List<String> g : groups) {
      for (String actor : g) {
        for (String uc : actorUseCases.get(actor)) {
          for (String base : bases.getOrDefault(uc, new ArrayList<>())) {
            List<String> h = groupOf.get(base);
            if (h != null && !h.equals(g)) {
              hostOf.putIfAbsent(g, h);
            }
          }
        }
      }
    }
    List<List<String>> ordered = new ArrayList<>();
    List<List<String>> pending = new ArrayList<>();
    for (List<String> g : groups) {
      (hostOf.containsKey(g) ? pending : ordered).add(g);
    }
    // Insert each hosted group right after its host; repeat while hosts get placed (chains),
    // anything left (host cycles) goes to the end.
    for (boolean progress = true; progress && !pending.isEmpty(); ) {
      progress = false;
      for (List<String> g : new ArrayList<>(pending)) {
        int at = ordered.indexOf(hostOf.get(g));
        if (at >= 0) {
          ordered.add(at + 1, g);
          pending.remove(g);
          progress = true;
        }
      }
    }
    ordered.addAll(pending);
    List<String> order = new ArrayList<>();
    for (List<String> g : ordered) {
      order.addAll(g);
    }
    return order;
  }

  private static void addWithChildren(
      String actor,
      Map<String, List<String>> actorUseCases,
      Map<String, String> actorParent,
      List<String> order) {
    if (order.contains(actor)) {
      return;
    }
    order.add(actor);
    for (String child : actorUseCases.keySet()) {
      if (actor.equals(actorParent.get(child))) {
        addWithChildren(child, actorUseCases, actorParent, order);
      }
    }
  }
}
