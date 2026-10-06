package com.brunnen.vp.mcp.tools;

import java.awt.Point;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Where layoutUseCaseDiagram puts every shape, as a pure model: the use case grid, the actor
 * columns beside the boundary and the center-to-center lines between them (VP draws use case and
 * actor lines center to center). {@link #plan} starts from {@link UseCaseGrid}'s rules and moves
 * use cases while the predicted drawing gets fewer lines through shapes, through actor names and
 * crossing each other; a drawing without any keeps the rules' layout. Pure (no VP API).
 */
final class UseCaseLayout {

  // A 160x60 use case cell, columns 380 px and rows 90 px apart (30 px between use cases).
  static final int GRID_X = 360;
  static final int GRID_Y = 100;
  static final int COLUMN_STEP = 380;
  static final int ROW_STEP = 90;
  static final int CELL_W = 160;
  static final int CELL_H = 60;
  // A base use case with its extension points compartment; 80 px still leaves 20 px to the
  // neighbouring rows.
  static final int EXTENDED_W = 200;
  static final int EXTENDED_H = 80;
  // The system boundary around the use cases, and the actor columns beside it.
  static final int PAD = 40;
  static final int ACTOR_GAP = 70;
  static final int ACTOR_W = 40;
  static final int ACTOR_H = 60;
  static final int CAPTION_LINE = 15;
  static final int STACK_GAP = 30;
  static final int STAGGER = 80;
  static final int CAPTION_HALF = 30;

  // How much each kind of problem in the predicted drawing weighs.
  private static final int THROUGH_SHAPE = 10;
  private static final int THROUGH_NAME = 6;
  private static final int CROSSING = 4;
  private static final int UNRELATED_BETWEEN = 6;
  private static final int PARENT_BELOW = 2;
  private static final double GRAZE = 2;
  private static final int MAX_PASSES = 30;
  private static final int ANNEAL_RUNS = 8;
  // Room the searches may use: a dependent use case at most one column right of its depth, and
  // no row below the rules' layout plus one. With more, annealing spread E-shop to 5x23 cells
  // and 50 % longer lines to save a few crossings.
  private static final int COLUMN_SLACK = 1;
  // Line length in the annealing cost: 300 px weigh one problem point.
  private static final double LENGTH_PER_POINT = 300;
  private static final int ANNEAL_STEPS = 4000;

  private UseCaseLayout() {}

  /** What the layout is made from. */
  static final class Input {
    /** All use cases, in the order rows follow (the spec's, or by name). */
    final List<String> useCases;

    /** Primary actor (left) -> its use cases; actors without use cases have an empty list. */
    final Map<String, List<String>> primary;

    /** Secondary actor (right) -> the use cases with a line to it. */
    final Map<String, List<String>> secondary;

    /** Generalization child actor -> parent actor. */
    final Map<String, String> actorParent;

    /** {base, dependent}: include (base, included) and extend (base, extending). */
    final List<String[]> deps;

    /** Use cases drawn with an extension points compartment (larger). */
    final Set<String> extended;

    /** Actors with a stereotype (a second caption line). */
    final Set<String> stereotyped;

    Input(
        List<String> useCases,
        Map<String, List<String>> primary,
        Map<String, List<String>> secondary,
        Map<String, String> actorParent,
        List<String[]> deps,
        Set<String> extended,
        Set<String> stereotyped) {
      this.useCases = useCases;
      this.primary = primary;
      this.secondary = secondary;
      this.actorParent = actorParent;
      this.deps = deps;
      this.extended = extended;
      this.stereotyped = stereotyped;
    }

    Set<String> secondaryLinked() {
      Set<String> out = new LinkedHashSet<>();
      for (List<String> ucs : secondary.values()) {
        out.addAll(ucs);
      }
      return out;
    }
  }

  /** The drawing the model predicts: shape and caption bounds, and lines between centers. */
  static final class Drawing {
    final Map<String, Rectangle2D> shapes = new LinkedHashMap<>();
    final Set<String> ellipses = new LinkedHashSet<>();
    final Set<String> actors = new LinkedHashSet<>(); // the primary (left) column
    final Map<String, String> actorParent = new HashMap<>();
    final Map<String, Rectangle2D> captions = new LinkedHashMap<>();
    final List<String[]> lines = new ArrayList<>(); // {from, to}

    Point2D center(String shape) {
      Rectangle2D r = shapes.get(shape);
      return new Point2D.Double(r.getCenterX(), r.getCenterY());
    }
  }

  /**
   * Use case -> grid cell (x = column, y = row). A layout the grid rules make without any predicted
   * problem stays as it is. Otherwise: two greedy searches, the rules' layout with primary actors'
   * rows moved elsewhere in the order and the layered layout of {@link UseCaseSugiyama} (its family
   * order matters: without it University kept 18 problems instead of 4), each polished by {@link
   * #improve}; then simulated annealing ({@link #anneal}) from the better one, eight seeds in
   * parallel, kept if it has fewer problems. Measured on nine test diagrams: Airline 98 -> 32,
   * E-shop 92 -> 22, University 112 -> 10, Clinic 10 -> 0, with lines at most 20 % longer and at
   * most one more column than the rules' layout.
   */
  static Map<String, Point> plan(Input in) {
    List<String> order =
        UseCaseGrid.actorOrder(in.primary, in.actorParent, UseCaseGrid.bases(in.deps));
    Map<String, Point> rules = replan(in, order);
    if (problems(in, rules) == 0) {
      return rules;
    }
    int rowCap = 1;
    for (Point c : rules.values()) {
      rowCap = Math.max(rowCap, c.y + 1);
    }
    final int cap = rowCap;
    Map<String, Point> reordered = improve(in, reorder(in, order), cap);
    Map<String, Point> layered = improve(in, UseCaseSugiyama.plan(in), cap);
    Map<String, Point> greedy =
        problems(in, layered) < problems(in, reordered) ? layered : reordered;
    if (problems(in, greedy) == 0) {
      return greedy;
    }
    // The greedy searches stop in a local optimum: anneal from there, several independent runs
    // in parallel (results vary a lot by seed), and keep the best if it has fewer problems.
    Map<String, Point> start = greedy;
    List<Map<String, Point>> runs =
        java.util.stream.LongStream.rangeClosed(1, ANNEAL_RUNS)
            .parallel()
            .mapToObj(seed -> improve(in, anneal(in, start, cap, ANNEAL_STEPS, seed), cap))
            .collect(java.util.stream.Collectors.toList());
    Map<String, Point> best = greedy;
    for (Map<String, Point> run : runs) {
      if (problems(in, run) < problems(in, best)) {
        best = run;
      }
    }
    return best;
  }

  /** The rules' layout with a primary actor's rows moved elsewhere while that removes problems. */
  private static Map<String, Point> reorder(Input in, List<String> start) {
    List<String> order = start;
    Map<String, Point> cells = replan(in, order);
    int problems = problems(in, cells);
    for (int pass = 0; pass < MAX_PASSES && problems > 0; pass++) {
      List<String> bestOrder = null;
      Map<String, Point> best = null;
      int bestProblems = problems;
      for (int i = 0; i < order.size(); i++) {
        for (int j = 0; j < order.size(); j++) {
          if (i == j) {
            continue;
          }
          List<String> moved = new ArrayList<>(order);
          moved.add(j, moved.remove(i));
          Map<String, Point> candidate = replan(in, moved);
          int p = problems(in, candidate);
          if (p < bestProblems) {
            best = candidate;
            bestOrder = moved;
            bestProblems = p;
          }
        }
      }
      if (best == null) {
        break;
      }
      cells = best;
      order = bestOrder;
      problems = bestProblems;
    }
    return cells;
  }

  /**
   * Starting from {@code start}, moves a dependent use case to another free cell of its column or
   * the next (rows up to {@code rowCap}), or swaps two use cases of one column, while that removes
   * problems.
   */
  static Map<String, Point> improve(Input in, Map<String, Point> start, int rowCap) {
    Map<String, Integer> depth = UseCaseGrid.columns(in.useCases, in.deps);
    Map<String, Point> cells = new LinkedHashMap<>(start);
    int problems = problems(in, cells);
    for (int pass = 0; pass < MAX_PASSES && problems > 0; pass++) {
      Map<String, Point> best = null;
      int bestProblems = problems;
      double bestLength = Double.MAX_VALUE;
      int maxRow = 0;
      for (Point c : cells.values()) {
        maxRow = Math.max(maxRow, c.y);
      }
      List<Map<String, Point>> candidates = new ArrayList<>();
      for (String uc : in.useCases) {
        Integer d = depth.get(uc);
        if (d == null || d == 0) {
          continue;
        }
        for (int col = d; col <= d + COLUMN_SLACK; col++) {
          for (int row = 0; row <= Math.max(rowCap, maxRow); row++) {
            Point to = new Point(col, row);
            if (!cells.containsValue(to) && keepsDirection(uc, to, cells, in.deps)) {
              Map<String, Point> c = new LinkedHashMap<>(cells);
              c.put(uc, to);
              candidates.add(c);
            }
          }
        }
      }
      for (String a : in.useCases) {
        for (String b : in.useCases) {
          Point pa = cells.get(a);
          Point pb = cells.get(b);
          if (a.compareTo(b) < 0 && pa.x == pb.x) {
            Map<String, Point> c = new LinkedHashMap<>(cells);
            c.put(a, pb);
            c.put(b, pa);
            if (keepsDirection(a, pb, c, in.deps) && keepsDirection(b, pa, c, in.deps)) {
              candidates.add(c);
            }
          }
        }
      }
      for (Map<String, Point> c : candidates) {
        int p = problems(in, c);
        double length = length(drawing(in, c));
        if (p < bestProblems || (p == bestProblems && best != null && length < bestLength)) {
          best = c;
          bestProblems = p;
          bestLength = length;
        }
      }
      if (best == null) {
        break;
      }
      cells = best;
      problems = bestProblems;
    }
    return cells;
  }

  /**
   * Simulated annealing (Davidson and Harel, 1996) from {@code start}: a random use case moves to a
   * random free cell of its allowed columns, or swaps with another of its column; a worse layout is
   * accepted with probability exp(-worsening / temperature), the temperature falling geometrically,
   * so the search can leave a local optimum the greedy one stops in. Keeps the best layout seen; a
   * fixed seed makes it repeatable.
   */
  static Map<String, Point> anneal(
      Input in, Map<String, Point> start, int rowCap, int steps, long seed) {
    java.util.Random random = new java.util.Random(seed);
    Map<String, Integer> depth = UseCaseGrid.columns(in.useCases, in.deps);
    Map<String, Point> cells = new LinkedHashMap<>(start);
    double cost = cost(in, cells);
    Map<String, Point> best = cells;
    double bestCost = cost;
    double temperature = 20;
    double cooling = Math.pow(0.01 / temperature, 1.0 / steps); // ends at 0.01
    List<String> ucs = new ArrayList<>(cells.keySet());
    for (int step = 0; step < steps && bestCost >= 1; step++, temperature *= cooling) {
      int maxRow = 0;
      for (Point c : cells.values()) {
        maxRow = Math.max(maxRow, c.y);
      }
      String uc = ucs.get(random.nextInt(ucs.size()));
      Map<String, Point> next = new LinkedHashMap<>(cells);
      if (random.nextBoolean()) {
        int d = depth.getOrDefault(uc, 0);
        int col = d == 0 ? 0 : d + random.nextInt(COLUMN_SLACK + 1);
        Point to = new Point(col, random.nextInt(Math.max(rowCap, maxRow) + 1));
        if (cells.containsValue(to) || !keepsDirection(uc, to, cells, in.deps)) {
          continue;
        }
        next.put(uc, to);
      } else {
        String other = ucs.get(random.nextInt(ucs.size()));
        Point a = cells.get(uc);
        Point b = cells.get(other);
        if (other.equals(uc) || a.x != b.x) {
          continue;
        }
        next.put(uc, b);
        next.put(other, a);
        if (!keepsDirection(uc, b, next, in.deps) || !keepsDirection(other, a, next, in.deps)) {
          continue;
        }
      }
      double c = cost(in, next);
      if (c < cost || random.nextDouble() < Math.exp((cost - c) / temperature)) {
        cells = next;
        cost = c;
        if (c < bestCost) {
          best = next;
          bestCost = c;
        }
      }
    }
    return best;
  }

  /** Problems plus total line length: fewer crossings must not cost a sprawling diagram. */
  private static double cost(Input in, Map<String, Point> cells) {
    Drawing d = drawing(in, cells);
    return problems(d) + length(d) / LENGTH_PER_POINT;
  }

  /** Whether {@code actor} is a (grand)child of {@code ancestor}. */
  private static boolean descends(String actor, String ancestor, Map<String, String> parent) {
    Set<String> seen = new java.util.HashSet<>();
    for (String p = parent.get(actor); p != null && seen.add(p); p = parent.get(p)) {
      if (p.equals(ancestor)) {
        return true;
      }
    }
    return false;
  }

  private static Map<String, Point> replan(Input in, List<String> order) {
    return UseCaseGrid.plan(
        in.useCases, in.primary, in.actorParent, in.deps, in.secondaryLinked(), order);
  }

  /** Every base stays left of the use case and every dependent right of it. */
  private static boolean keepsDirection(
      String uc, Point to, Map<String, Point> cells, List<String[]> deps) {
    for (String[] d : deps) {
      Point base = cells.get(d[0]);
      Point dependent = cells.get(d[1]);
      if (d[1].equals(uc) && base != null && base.x >= to.x) {
        return false;
      }
      if (d[0].equals(uc) && dependent != null && dependent.x <= to.x) {
        return false;
      }
    }
    return true;
  }

  /** The bounds of a use case in a grid cell, centered in the cell. */
  static Rectangle2D useCaseBounds(Point cell, boolean extended) {
    int w = extended ? EXTENDED_W : CELL_W;
    int h = extended ? EXTENDED_H : CELL_H;
    return new Rectangle2D.Double(
        GRID_X + cell.x * COLUMN_STEP + (CELL_W - w) / 2,
        GRID_Y + cell.y * ROW_STEP + (CELL_H - h) / 2,
        w,
        h);
  }

  /** The predicted drawing of {@code cells}. */
  static Drawing drawing(Input in, Map<String, Point> cells) {
    Drawing d = new Drawing();
    d.actorParent.putAll(in.actorParent);
    double minX = Double.MAX_VALUE;
    double minY = Double.MAX_VALUE;
    double maxX = -Double.MAX_VALUE;
    for (Map.Entry<String, Point> e : cells.entrySet()) {
      Rectangle2D r = useCaseBounds(e.getValue(), in.extended.contains(e.getKey()));
      d.shapes.put(e.getKey(), r);
      d.ellipses.add(e.getKey());
      minX = Math.min(minX, r.getX());
      minY = Math.min(minY, r.getY());
      maxX = Math.max(maxX, r.getMaxX());
    }
    placeColumn(d, in, in.primary, minX - PAD - ACTOR_GAP - ACTOR_W, minY, true);
    placeColumn(d, in, in.secondary, maxX + PAD + ACTOR_GAP, minY, false);
    for (Map<String, List<String>> side : java.util.Arrays.asList(in.primary, in.secondary)) {
      for (Map.Entry<String, List<String>> e : side.entrySet()) {
        for (String uc : e.getValue()) {
          if (d.shapes.containsKey(uc)) {
            d.lines.add(new String[] {e.getKey(), uc});
          }
        }
      }
    }
    for (String[] dep : in.deps) {
      if (d.shapes.containsKey(dep[0]) && d.shapes.containsKey(dep[1])) {
        d.lines.add(new String[] {dep[0], dep[1]});
      }
    }
    for (Map.Entry<String, String> g : in.actorParent.entrySet()) {
      if (d.shapes.containsKey(g.getKey()) && d.shapes.containsKey(g.getValue())) {
        d.lines.add(new String[] {g.getKey(), g.getValue()});
      }
    }
    return d;
  }

  /**
   * One actor column, as addSystemBoundary places it: each actor level with the average of its use
   * cases (or at the top), stacked without overlap, generalization children moved aside.
   */
  private static void placeColumn(
      Drawing d, Input in, Map<String, List<String>> actors, double x, double top, boolean left) {
    List<String> names = new ArrayList<>(actors.keySet());
    Map<String, Integer> desired = new HashMap<>();
    for (String a : names) {
      double sum = 0;
      int n = 0;
      for (String uc : actors.get(a)) {
        Rectangle2D r = d.shapes.get(uc);
        if (r != null) {
          sum += (int) r.getCenterY();
          n++;
        }
      }
      desired.put(a, n == 0 ? (int) top : (int) (sum / n) - ACTOR_H / 2);
    }
    names.sort(
        java.util.Comparator.<String>comparingInt(desired::get)
            .thenComparing(java.util.Comparator.naturalOrder()));
    int[] want = new int[names.size()];
    int[] heights = new int[names.size()];
    int[] parent = new int[names.size()];
    for (int i = 0; i < names.size(); i++) {
      want[i] = desired.get(names.get(i));
      heights[i] = ACTOR_H + (in.stereotyped.contains(names.get(i)) ? CAPTION_LINE : 0);
      parent[i] = names.indexOf(in.actorParent.get(names.get(i)));
    }
    int[] ys = stackYs(want, heights, STACK_GAP);
    int[] dx = left ? staggerX(ys, heights, parent, CAPTION_HALF, STAGGER) : new int[ys.length];
    for (int i = 0; i < names.size(); i++) {
      String a = names.get(i);
      Rectangle2D r = new Rectangle2D.Double(x + dx[i], ys[i], ACTOR_W, ACTOR_H);
      d.shapes.put(a, r);
      d.captions.put(a, caption(a, in.stereotyped.contains(a), r));
      if (left) {
        d.actors.add(a);
      }
    }
  }

  /** The estimated name caption under an actor figure (VP lays captions out only on render). */
  static Rectangle2D caption(String name, boolean stereotyped, Rectangle2D figure) {
    double w = Math.max(stereotyped ? 70 : 40, 7 * name.length() + 10);
    return new Rectangle2D.Double(
        figure.getCenterX() - w / 2, figure.getMaxY(), w, stereotyped ? 32 : 16);
  }

  /** Weighted count of lines through shapes, through actor names and crossing each other. */
  static int problems(Input in, Map<String, Point> cells) {
    return problems(drawing(in, cells));
  }

  static int problems(Drawing d) {
    int total = 0;
    for (String issue : issues(d)) {
      total += Integer.parseInt(issue.substring(0, issue.indexOf(' ')));
    }
    return total;
  }

  /** Each problem of the drawing as "weight description". */
  static List<String> issues(Drawing d) {
    List<String> out = new ArrayList<>();
    List<Line2D> segments = new ArrayList<>();
    for (String[] l : d.lines) {
      segments.add(new Line2D.Double(d.center(l[0]), d.center(l[1])));
    }
    for (int i = 0; i < d.lines.size(); i++) {
      String[] l = d.lines.get(i);
      Line2D s = segments.get(i);
      for (Map.Entry<String, Rectangle2D> shape : d.shapes.entrySet()) {
        String name = shape.getKey();
        if (!name.equals(l[0])
            && !name.equals(l[1])
            && hits(s, shape.getValue(), d.ellipses.contains(name))) {
          out.add(THROUGH_SHAPE + " " + l[0] + " - " + l[1] + " through " + name);
        }
      }
      for (Map.Entry<String, Rectangle2D> cap : d.captions.entrySet()) {
        String owner = cap.getKey();
        if (!owner.equals(l[0]) && !owner.equals(l[1]) && s.intersects(cap.getValue())) {
          out.add(THROUGH_NAME + " " + l[0] + " - " + l[1] + " through name of " + owner);
        }
      }
    }
    for (int i = 0; i < d.lines.size(); i++) {
      for (int j = i + 1; j < d.lines.size(); j++) {
        String[] a = d.lines.get(i);
        String[] b = d.lines.get(j);
        String shared = shared(a, b);
        Point2D at = crossing(segments.get(i), segments.get(j));
        if (at != null && (shared == null || !grown(d.shapes.get(shared)).contains(at))) {
          out.add(CROSSING + " " + a[0] + " - " + a[1] + " x " + b[0] + " - " + b[1]);
        }
      }
    }
    familyApart(d, d.actorParent, out);
    return out;
  }

  /** Total line length: among equally good layouts the shorter one reads easier. */
  static double length(Drawing d) {
    double total = 0;
    for (String[] l : d.lines) {
      total += d.center(l[0]).distance(d.center(l[1]));
    }
    return total;
  }

  private static String shared(String[] a, String[] b) {
    for (String x : a) {
      for (String y : b) {
        if (x.equals(y)) {
          return x;
        }
      }
    }
    return null;
  }

  private static Rectangle2D grown(Rectangle2D r) {
    return new Rectangle2D.Double(
        r.getX() - GRAZE, r.getY() - GRAZE, r.getWidth() + 2 * GRAZE, r.getHeight() + 2 * GRAZE);
  }

  /** The segment enters the shape by more than GRAZE px (an ellipse, or a rectangle). */
  static boolean hits(Line2D s, Rectangle2D r, boolean ellipse) {
    if (!ellipse) {
      return s.intersects(
          r.getX() + GRAZE, r.getY() + GRAZE, r.getWidth() - 2 * GRAZE, r.getHeight() - 2 * GRAZE);
    }
    // Scale the ellipse to the unit circle: the segment hits it when it comes nearer than 1.
    double rx = r.getWidth() / 2 - GRAZE;
    double ry = r.getHeight() / 2 - GRAZE;
    Line2D unit =
        new Line2D.Double(
            (s.getX1() - r.getCenterX()) / rx,
            (s.getY1() - r.getCenterY()) / ry,
            (s.getX2() - r.getCenterX()) / rx,
            (s.getY2() - r.getCenterY()) / ry);
    return unit.ptSegDist(0, 0) < 1;
  }

  /** Where two segments cross, or null (also when they only meet at an end). */
  private static Point2D crossing(Line2D a, Line2D b) {
    if (!a.intersectsLine(b)) {
      return null;
    }
    double d =
        (a.getX2() - a.getX1()) * (b.getY2() - b.getY1())
            - (a.getY2() - a.getY1()) * (b.getX2() - b.getX1());
    if (d == 0) {
      return null; // parallel: overlapping collinear lines are caught as lines through shapes
    }
    double t =
        ((b.getX1() - a.getX1()) * (b.getY2() - b.getY1())
                - (b.getY1() - a.getY1()) * (b.getX2() - b.getX1()))
            / d;
    if (t < 1e-6 || t > 1 - 1e-6) {
      return null;
    }
    return new Point2D.Double(
        a.getX1() + t * (a.getX2() - a.getX1()), a.getY1() + t * (a.getY2() - a.getY1()));
  }

  /** Actors standing between a generalization child and its parent that are not its family. */
  private static void familyApart(Drawing d, Map<String, String> parent, List<String> out) {
    for (Map.Entry<String, String> g : parent.entrySet()) {
      Rectangle2D child = d.shapes.get(g.getKey());
      Rectangle2D up = d.shapes.get(g.getValue());
      if (child == null || up == null) {
        continue;
      }
      if (up.getCenterY() > child.getCenterY()) {
        // Generalization arrows usually point up: the parent above its children.
        out.add(PARENT_BELOW + " " + g.getValue() + " below its child " + g.getKey());
      }
      double lo = Math.min(child.getCenterY(), up.getCenterY());
      double hi = Math.max(child.getCenterY(), up.getCenterY());
      for (String other : d.actors) {
        double y = d.shapes.get(other).getCenterY();
        if (y > lo
            && y < hi
            && !other.equals(g.getKey())
            && !descends(other, g.getValue(), parent)) {
          out.add(
              UNRELATED_BETWEEN + " " + other + " between " + g.getKey() + " and " + g.getValue());
        }
      }
    }
  }

  /**
   * Non-overlapping Y positions for a column: each element sits at its desired Y, or just below the
   * previous element (its bottom plus {@code minGap}) if that would overlap. Expects inputs sorted
   * by desired Y.
   *
   * @param desiredY each element's preferred top Y
   * @param heights each element's height
   * @param minGap minimum vertical gap between stacked elements
   * @return the top Y of each element
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

  /**
   * Horizontal shifts (0 or negative) for a column of actors so that each generalization child's
   * straight line to its parent passes no other actor: two children stacked under one parent would
   * otherwise have the farther one's line run through the nearer one. A child is moved left by
   * {@code step}, then twice that, until its line is clear.
   *
   * @param ys each actor's top Y
   * @param heights each actor's height (figure, plus stereotype caption lines)
   * @param parent index of each actor's generalization parent in the column, or -1
   * @param captionHalf half the width reserved for an actor's name under its figure
   * @param step the shift
   */
  static int[] staggerX(int[] ys, int[] heights, int[] parent, int captionHalf, int step) {
    int[] dx = new int[ys.length];
    for (int i = 0; i < ys.length; i++) {
      int p = parent[i];
      if (p < 0) {
        continue;
      }
      for (int k = 0; k <= 2; k++) {
        Line2D line = new Line2D.Double(-k * step, ys[i] + 30, dx[p], ys[p] + 30);
        boolean clear = true;
        for (int j = 0; j < ys.length && clear; j++) {
          // The figure and the name caption hanging below it.
          clear =
              j == i
                  || j == p
                  || !line.intersects(
                      dx[j] - captionHalf, ys[j], 2 * captionHalf, heights[j] + CAPTION_LINE + 5);
        }
        dx[i] = -k * step;
        if (clear) {
          break;
        }
      }
    }
    return dx;
  }
}
