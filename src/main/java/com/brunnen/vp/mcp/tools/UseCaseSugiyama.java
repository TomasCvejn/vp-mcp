package com.brunnen.vp.mcp.tools;

import java.awt.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The use case grid by the layered graph drawing method of Sugiyama, Tagawa and Toda (1981), the
 * method behind Graphviz dot (Gansner et al., 1993). Layers, left to right: primary actors, one per
 * use case column (include/extend depth), secondary actors. A line spanning several layers gets a
 * virtual node in each layer between, which keeps a cell free for it. Crossings are reduced by
 * alternate sweeps ordering each layer by the barycenter of its neighbours in the previous one,
 * each followed by transposing adjacent nodes while that removes crossings; the best order wins.
 * Rows then put every node as near the average row of its neighbours as the order allows (isotonic
 * regression, pool adjacent violators). Pure (no VP API).
 */
final class UseCaseSugiyama {

  private static final int SWEEPS = 24;
  private static final int ROW_PASSES = 8;

  private UseCaseSugiyama() {}

  /** Use case -> grid cell (x = column, y = row). */
  static Map<String, Point> plan(UseCaseLayout.Input in) {
    Map<String, Integer> depth = in.columns();
    int columns = 1;
    for (int d : depth.values()) {
      columns = Math.max(columns, d + 1);
    }
    int last = columns + 1; // the secondary actors' layer
    Graph g = new Graph(last + 1);
    // Starting order: the rules' layout, so a sweep starts from something sensible.
    Map<String, List<String>> bases = UseCaseGrid.bases(in.deps);
    for (String a : UseCaseGrid.actorOrder(in.primary, in.actorParent, bases)) {
      g.add(a, 0);
    }
    Map<String, Point> rules =
        UseCaseGrid.plan(in.useCases, in.primary, in.actorParent, in.deps, in.secondaryLinked());
    List<String> byRow = new ArrayList<>(in.useCases);
    byRow.sort(
        java.util.Comparator.comparingInt((String uc) -> rules.get(uc).y)
            .thenComparing(java.util.Comparator.naturalOrder()));
    for (String uc : byRow) {
      g.add(uc, depth.getOrDefault(uc, 0) + 1);
    }
    for (String s : in.secondary.keySet()) {
      g.add(s, last);
    }
    for (Map.Entry<String, List<String>> e : in.primary.entrySet()) {
      for (String uc : e.getValue()) {
        g.connect(e.getKey(), uc);
      }
    }
    for (String[] d : in.deps) {
      g.connect(d[0], d[1]);
    }
    for (Map.Entry<String, List<String>> e : in.secondary.entrySet()) {
      for (String uc : e.getValue()) {
        g.connect(uc, e.getKey());
      }
    }
    g.minimizeCrossings(in.actorParent);
    Map<String, Double> rows = g.assignRows();
    double min = Double.MAX_VALUE;
    for (int layer = 1; layer < last; layer++) {
      for (String n : g.layers.get(layer)) {
        min = Math.min(min, rows.get(n));
      }
    }
    Map<String, Point> cells = new LinkedHashMap<>();
    for (String uc : in.useCases) {
      cells.put(uc, new Point(depth.getOrDefault(uc, 0), (int) Math.round(rows.get(uc) - min)));
    }
    return cells;
  }

  /** A layered graph whose edges join adjacent layers only. */
  static final class Graph {
    final List<List<String>> layers = new ArrayList<>();
    final Map<String, Integer> layerOf = new HashMap<>();
    final Map<String, List<String>> left = new HashMap<>(); // neighbours one layer left
    final Map<String, List<String>> right = new HashMap<>(); // neighbours one layer right
    private int dummies;

    Graph(int count) {
      for (int i = 0; i < count; i++) {
        layers.add(new ArrayList<>());
      }
    }

    void add(String node, int layer) {
      if (!layerOf.containsKey(node)) {
        layers.get(layer).add(node);
        layerOf.put(node, layer);
        left.put(node, new ArrayList<>());
        right.put(node, new ArrayList<>());
      }
    }

    /** An edge, with a virtual node in every layer between its ends. */
    void connect(String a, String b) {
      Integer la = layerOf.get(a);
      Integer lb = layerOf.get(b);
      if (la == null || lb == null || la.equals(lb)) {
        return;
      }
      String from = la < lb ? a : b;
      String to = la < lb ? b : a;
      String prev = from;
      for (int layer = Math.min(la, lb) + 1; layer < Math.max(la, lb); layer++) {
        String dummy = "\u0000" + dummies++;
        add(dummy, layer);
        link(prev, dummy);
        prev = dummy;
      }
      link(prev, to);
    }

    private void link(String l, String r) {
      right.get(l).add(r);
      left.get(r).add(l);
    }

    /** Crossings between layer i and i + 1 (pairs of edges whose ends are in opposite order). */
    int crossings(int i) {
      List<int[]> edges = new ArrayList<>();
      Map<String, Integer> posR = positions(i + 1);
      List<String> layer = layers.get(i);
      for (int p = 0; p < layer.size(); p++) {
        for (String r : right.get(layer.get(p))) {
          edges.add(new int[] {p, posR.get(r)});
        }
      }
      int count = 0;
      for (int a = 0; a < edges.size(); a++) {
        for (int b = a + 1; b < edges.size(); b++) {
          int[] e = edges.get(a);
          int[] f = edges.get(b);
          if ((e[0] - f[0]) * (e[1] - f[1]) < 0) {
            count++;
          }
        }
      }
      return count;
    }

    int crossings() {
      int total = 0;
      for (int i = 0; i + 1 < layers.size(); i++) {
        total += crossings(i);
      }
      return total;
    }

    private Map<String, Integer> positions(int layer) {
      Map<String, Integer> pos = new HashMap<>();
      List<String> nodes = layers.get(layer);
      for (int p = 0; p < nodes.size(); p++) {
        pos.put(nodes.get(p), p);
      }
      return pos;
    }

    /** Barycenter sweeps with transposition; keeps the order with the fewest crossings. */
    void minimizeCrossings(Map<String, String> actorParent) {
      // Families together from the start, whatever order came in.
      orderFamilies(actorParent, positions(1));
      List<List<String>> best = copy();
      int fewest = crossings();
      for (int sweep = 0; sweep < SWEEPS && fewest > 0; sweep++) {
        boolean down = sweep % 2 == 0;
        if (down) {
          for (int i = 1; i < layers.size(); i++) {
            order(i, positions(i - 1), left);
          }
        } else {
          for (int i = layers.size() - 2; i >= 0; i--) {
            if (i == 0) {
              orderFamilies(actorParent, positions(1));
            } else {
              order(i, positions(i + 1), right);
            }
          }
        }
        transpose();
        int c = crossings();
        if (c < fewest) {
          fewest = c;
          best = copy();
        }
      }
      for (int i = 0; i < layers.size(); i++) {
        layers.set(i, best.get(i));
      }
    }

    /** Sorts layer i by the barycenter of its neighbours in the reference layer. */
    private void order(int i, Map<String, Integer> ref, Map<String, List<String>> side) {
      List<String> nodes = layers.get(i);
      Map<String, Double> key = new HashMap<>();
      for (int p = 0; p < nodes.size(); p++) {
        key.put(nodes.get(p), barycenter(nodes.get(p), p, ref, side));
      }
      nodes.sort(java.util.Comparator.comparingDouble(key::get));
    }

    private double barycenter(
        String node, int at, Map<String, Integer> ref, Map<String, List<String>> side) {
      List<String> ns = side.get(node);
      if (ns.isEmpty()) {
        return at; // no neighbour there: stays where it is
      }
      double sum = 0;
      for (String n : ns) {
        sum += ref.get(n);
      }
      return sum / ns.size();
    }

    /**
     * The primary actors by barycenter, but each generalization child right under its parent: whole
     * families move, ordered by their members' average barycenter.
     */
    private void orderFamilies(Map<String, String> actorParent, Map<String, Integer> ref) {
      List<String> nodes = layers.get(0);
      Map<String, Double> key = new HashMap<>();
      for (int p = 0; p < nodes.size(); p++) {
        key.put(nodes.get(p), barycenter(nodes.get(p), p, ref, right));
      }
      List<String> roots = new ArrayList<>();
      for (String a : nodes) {
        if (!nodes.contains(actorParent.get(a))) {
          roots.add(a);
        }
      }
      Map<String, List<String>> family = new HashMap<>();
      for (String r : roots) {
        List<String> members = new ArrayList<>();
        addFamily(r, nodes, actorParent, key, members);
        family.put(r, members);
      }
      Map<String, Double> familyKey = new HashMap<>();
      for (String r : roots) {
        double sum = 0;
        for (String m : family.get(r)) {
          sum += key.get(m);
        }
        familyKey.put(r, sum / family.get(r).size());
      }
      roots.sort(java.util.Comparator.comparingDouble(familyKey::get));
      List<String> ordered = new ArrayList<>();
      for (String r : roots) {
        ordered.addAll(family.get(r));
      }
      layers.set(0, ordered);
    }

    private void addFamily(
        String actor,
        List<String> nodes,
        Map<String, String> actorParent,
        Map<String, Double> key,
        List<String> out) {
      if (out.contains(actor)) {
        return;
      }
      out.add(actor);
      List<String> children = new ArrayList<>();
      for (String a : nodes) {
        if (actor.equals(actorParent.get(a))) {
          children.add(a);
        }
      }
      children.sort(java.util.Comparator.comparingDouble(key::get));
      for (String c : children) {
        addFamily(c, nodes, actorParent, key, out);
      }
    }

    /** Swaps adjacent nodes (not in the actor layers) while that removes crossings. */
    private void transpose() {
      for (boolean improved = true; improved; ) {
        improved = false;
        for (int i = 1; i + 1 < layers.size(); i++) {
          List<String> nodes = layers.get(i);
          for (int p = 0; p + 1 < nodes.size(); p++) {
            int before = crossings(i - 1) + crossings(i);
            java.util.Collections.swap(nodes, p, p + 1);
            if (crossings(i - 1) + crossings(i) < before) {
              improved = true;
            } else {
              java.util.Collections.swap(nodes, p, p + 1);
            }
          }
        }
      }
    }

    private List<List<String>> copy() {
      List<List<String>> out = new ArrayList<>();
      for (List<String> l : layers) {
        out.add(new ArrayList<>(l));
      }
      return out;
    }

    /**
     * A row for every node: actors at the average row of their use cases (as addSystemBoundary
     * places them), every node of a use case column as near the average row of its neighbours as
     * its layer's order allows, at least one row below the node above it.
     */
    Map<String, Double> assignRows() {
      Map<String, Double> rows = new HashMap<>();
      for (List<String> layer : layers) {
        for (int p = 0; p < layer.size(); p++) {
          rows.put(layer.get(p), (double) p);
        }
      }
      int lastLayer = layers.size() - 1;
      for (int pass = 0; pass < ROW_PASSES; pass++) {
        for (int i : new int[] {0, lastLayer}) {
          for (String a : layers.get(i)) {
            List<String> ns = i == 0 ? right.get(a) : left.get(a);
            if (!ns.isEmpty()) {
              rows.put(a, average(ns, rows));
            }
          }
        }
        boolean down = pass % 2 == 0;
        for (int k = 1; k < lastLayer; k++) {
          int i = down ? k : lastLayer - k;
          List<String> layer = layers.get(i);
          double[] want = new double[layer.size()];
          for (int p = 0; p < layer.size(); p++) {
            List<String> ns = new ArrayList<>(left.get(layer.get(p)));
            ns.addAll(right.get(layer.get(p)));
            want[p] = ns.isEmpty() ? rows.get(layer.get(p)) : average(ns, rows);
          }
          double[] got = spaced(want);
          for (int p = 0; p < layer.size(); p++) {
            rows.put(layer.get(p), got[p]);
          }
        }
      }
      return rows;
    }

    private static double average(List<String> nodes, Map<String, Double> rows) {
      double sum = 0;
      for (String n : nodes) {
        sum += rows.get(n);
      }
      return sum / nodes.size();
    }
  }

  /**
   * The integer rows nearest {@code want} (least squares) that keep their order with at least one
   * row between neighbours: with x[i] = row[i] - i the constraint is "x never decreases", solved by
   * pool adjacent violators.
   */
  static double[] spaced(double[] want) {
    int n = want.length;
    double[] value = new double[n];
    int[] size = new int[n];
    int blocks = 0;
    for (int i = 0; i < n; i++) {
      value[blocks] = want[i] - i;
      size[blocks] = 1;
      blocks++;
      while (blocks > 1 && value[blocks - 2] > value[blocks - 1]) {
        int s = size[blocks - 2] + size[blocks - 1];
        value[blocks - 2] =
            (value[blocks - 2] * size[blocks - 2] + value[blocks - 1] * size[blocks - 1]) / s;
        size[blocks - 2] = s;
        blocks--;
      }
    }
    double[] rows = new double[n];
    int i = 0;
    double previous = -Double.MAX_VALUE;
    for (int b = 0; b < blocks; b++) {
      for (int k = 0; k < size[b]; k++, i++) {
        double r = Math.round(value[b] + i);
        rows[i] = Math.max(r, previous + 1);
        previous = rows[i];
      }
    }
    return rows;
  }
}
