package com.brunnen.vp.mcp.tools;

import java.awt.BasicStroke;
import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;

/**
 * Pure geometry checks of a laid-out diagram, independent of the VP API so they are unit-testable:
 * overlapping shapes, shapes straddling a container edge, lines running through a shape they do not
 * connect, and crossing lines.
 */
final class LayoutCheck {

  /** A shape on the diagram. Containers (system boundary, package) may enclose other shapes. */
  static final class Box {
    final String name;
    final boolean ellipse;
    final boolean container;
    final Rectangle2D bounds;
    // For a caption drawn outside its shape (an actor's name): the shape it labels, else null.
    final String owner;

    Box(String name, boolean ellipse, boolean container, int x, int y, int w, int h) {
      this(name, ellipse, container, x, y, w, h, null);
    }

    private Box(
        String name, boolean ellipse, boolean container, int x, int y, int w, int h, String owner) {
      this.name = name;
      this.ellipse = ellipse;
      this.container = container;
      this.bounds = new Rectangle2D.Double(x, y, w, h);
      this.owner = owner;
    }

    /** The caption of shape {@code owner}, drawn outside it (e.g. below an actor). */
    static Box caption(String owner, int x, int y, int w, int h) {
      return new Box("caption of " + owner, false, false, x, y, w, h, owner);
    }

    /** The «include»/«extend» label of the line named {@code line}. */
    static Box label(String line, int x, int y, int w, int h) {
      return new Box("label of " + line, false, false, x, y, w, h, line);
    }

    /** Whether the two boxes are a shape and its own caption, or two captions of one shape. */
    boolean sameElementAs(Box other) {
      String mine = owner != null ? owner : name;
      String theirs = other.owner != null ? other.owner : other.name;
      return mine.equals(theirs);
    }

    Area area(double inset) {
      double x = bounds.getX() + inset;
      double y = bounds.getY() + inset;
      double w = Math.max(0, bounds.getWidth() - 2 * inset);
      double h = Math.max(0, bounds.getHeight() - 2 * inset);
      Shape s = ellipse ? new Ellipse2D.Double(x, y, w, h) : new Rectangle2D.Double(x, y, w, h);
      return new Area(s);
    }
  }

  /** A connector between two named shapes, drawn as a polyline through {@code points}. */
  static final class Line {
    final String name;
    final String from;
    final String to;
    final Point[] points;

    Line(String name, String from, String to, Point... points) {
      this.name = name;
      this.from = from;
      this.to = to;
      this.points = points.clone();
    }

    boolean touches(String shape) {
      return shape.equals(from) || shape.equals(to);
    }
  }

  // A line counts as passing through a shape only when it enters it by more than this many px,
  // so a line merely grazing an outline is not reported.
  private static final float GRAZE = 2f;
  // Gap between a relationship label and its own line.
  private static final int LABEL_GAP = 4;

  private LayoutCheck() {}

  /** Every layout problem found, one human-readable line each; empty when the layout is clean. */
  static List<String> check(List<Box> boxes, List<Line> lines) {
    List<String> issues = new ArrayList<>();
    for (int i = 0; i < boxes.size(); i++) {
      for (int j = i + 1; j < boxes.size(); j++) {
        Box a = boxes.get(i);
        Box b = boxes.get(j);
        if (a.container == b.container) {
          if (!a.container && !a.sameElementAs(b) && intersect(a.area(0), b.area(0))) {
            issues.add("overlap: '" + a.name + "' and '" + b.name + "'");
          }
        } else {
          Box c = a.container ? a : b;
          Box s = a.container ? b : a;
          if (c.bounds.intersects(s.bounds) && !c.bounds.contains(s.bounds)) {
            issues.add("straddles boundary: '" + s.name + "' crosses the edge of '" + c.name + "'");
          }
        }
      }
    }
    for (Line line : lines) {
      Area stroke = stroke(line);
      for (Box box : boxes) {
        boolean own =
            line.touches(box.name)
                || (box.owner != null && (line.touches(box.owner) || line.name.equals(box.owner)));
        if (!box.container && !own && intersect(stroke, box.area(GRAZE))) {
          issues.add("line through shape: " + line.name + " crosses '" + box.name + "'");
        }
      }
    }
    for (int i = 0; i < lines.size(); i++) {
      for (int j = i + 1; j < lines.size(); j++) {
        Line a = lines.get(i);
        Line b = lines.get(j);
        String shared = a.touches(b.from) ? b.from : a.touches(b.to) ? b.to : null;
        Point2D at = crossing(a, b);
        // Lines sharing a shape may meet at it (an actor's fan), but not cross outside it: VP's
        // corner routing once made two includes cross 20 px before their arrowheads.
        if (at != null
            && !(endsNear(a, at) && endsNear(b, at))
            && (shared == null || !near(boxes, shared, at))) {
          issues.add("crossing: " + a.name + " x " + b.name);
        }
      }
    }
    return issues;
  }

  /**
   * Where a line from a use case to a secondary actor should bend, or null when the straight line
   * is clear or no bend is. A bent line runs level from the use case to the bend, then on to the
   * actor. Bends are tried from {@code maxX} (just outside the boundary) leftwards in 20 px steps,
   * so the line bends as late as it can; the last segment must miss every obstacle and pass above
   * the actor's name caption (a steep segment from below ended on the caption, not on the figure).
   *
   * @param from the use case's center
   * @param to the actor's center
   * @param obstacles every other shape
   * @param caption the actor's name caption under its figure
   */
  static Point secondaryBend(
      Point from, Point to, List<Box> obstacles, Rectangle2D caption, int minX, int maxX) {
    if (clear(from, to, obstacles, caption)) {
      return null;
    }
    for (int x = maxX; x >= minX; x -= 20) {
      Point bend = new Point(x, from.y);
      if (clear(from, bend, obstacles, caption) && clear(bend, to, obstacles, caption)) {
        return bend;
      }
    }
    // No clear bend: a forced one ran through more than the straight line (E-shop).
    return null;
  }

  private static boolean clear(Point a, Point b, List<Box> obstacles, Rectangle2D caption) {
    Line2D segment = new Line2D.Double(a, b);
    if (segment.intersects(caption)) {
      return false;
    }
    Area stroke = new Area(new BasicStroke(1f).createStrokedShape(segment));
    for (Box box : obstacles) {
      if (!box.container && intersect(stroke, box.area(GRAZE))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Where to put a w x h label of the line {@code own} so that it is clearly that line's: just
   * beside it, at least {@code LABEL_GAP} px off every one of the {@code obstacles} (shapes,
   * captions, labels placed before) and not across another line. Spots beside the middle come
   * first, then beside 40 %, 60 %, 30 %, ... of the line, the side farther from the {@code others}
   * first; the first free spot whose own line is clearly the nearest (by 12 px) wins, a label
   * nearer another line read as that line's. Failing that, the free spot where its own line is
   * nearest relative to the others; failing that, beside the middle.
   *
   * @return the label's top-left corner
   */
  static Point labelSpot(Line2D own, List<Line2D> others, List<Box> obstacles, int w, int h) {
    Point best = labelSpotAt(own, 0.5, others, w, h);
    double bestMargin = Double.NEGATIVE_INFINITY;
    for (double t : new double[] {0.5, 0.4, 0.6, 0.3, 0.7, 0.2, 0.8}) {
      for (boolean otherSide : new boolean[] {false, true}) {
        Point spot = labelSpotAt(own, t, others, w, h, otherSide);
        if (!free(new Rectangle2D.Double(spot.x, spot.y, w, h), others, obstacles)) {
          continue;
        }
        double cx = spot.x + w / 2.0;
        double cy = spot.y + h / 2.0;
        double nearestOther = 1000;
        for (Line2D other : others) {
          nearestOther = Math.min(nearestOther, other.ptSegDist(cx, cy));
        }
        double margin = nearestOther - own.ptSegDist(cx, cy);
        if (margin >= 12) {
          return spot;
        }
        if (margin > bestMargin) {
          bestMargin = margin;
          best = spot;
        }
      }
    }
    return best;
  }

  private static boolean free(Rectangle2D label, List<Line2D> others, List<Box> obstacles) {
    for (Line2D other : others) {
      if (other.intersects(label)) {
        return false;
      }
    }
    Area area = new Area(label);
    for (Box box : obstacles) {
      // Kept LABEL_GAP off: checkLayout tests overlaps exactly, and VP may resize a label.
      if (!box.container && intersect(area, box.area(-LABEL_GAP))) {
        return false;
      }
    }
    return true;
  }

  private static Point labelSpotAt(Line2D own, double t, List<Line2D> others, int w, int h) {
    return labelSpotAt(own, t, others, w, h, false);
  }

  /** Beside the point at {@code t} of the line, on the side farther from the others (or not). */
  private static Point labelSpotAt(
      Line2D own, double t, List<Line2D> others, int w, int h, boolean otherSide) {
    double mx = own.getX1() + t * (own.getX2() - own.getX1());
    double my = own.getY1() + t * (own.getY2() - own.getY1());
    double dx = own.getX2() - own.getX1();
    double dy = own.getY2() - own.getY1();
    double len = Math.hypot(dx, dy);
    // Unit normal; a degenerate line gets the vertical one.
    double nx = len == 0 ? 0 : -dy / len;
    double ny = len == 0 ? 1 : dx / len;
    // Far enough that the label's nearest corner clears the line, whatever its slope.
    double offset = (Math.abs(nx) * w + Math.abs(ny) * h) / 2 + LABEL_GAP;
    Point best = null;
    Point worst = null;
    double bestClearance = -1;
    for (int side : new int[] {1, -1}) {
      double cx = mx + side * nx * offset;
      double cy = my + side * ny * offset;
      double clearance = Double.MAX_VALUE;
      for (Line2D other : others) {
        clearance = Math.min(clearance, other.ptSegDist(cx, cy));
      }
      Point spot = new Point((int) Math.round(cx - w / 2.0), (int) Math.round(cy - h / 2.0));
      if (clearance > bestClearance) {
        bestClearance = clearance;
        worst = best;
        best = spot;
      } else {
        worst = spot;
      }
    }
    return otherSide ? worst : best;
  }

  private static boolean intersect(Area a, Area b) {
    Area x = new Area(a);
    x.intersect(b);
    return !x.isEmpty();
  }

  private static Area stroke(Line line) {
    Area area = new Area();
    BasicStroke pen = new BasicStroke(1f);
    for (int k = 0; k + 1 < line.points.length; k++) {
      area.add(
          new Area(pen.createStrokedShape(new Line2D.Double(line.points[k], line.points[k + 1]))));
    }
    return area;
  }

  /** Where the two polylines first cross, or null. */
  private static Point2D crossing(Line a, Line b) {
    for (int i = 0; i + 1 < a.points.length; i++) {
      for (int j = 0; j + 1 < b.points.length; j++) {
        Point p1 = a.points[i];
        Point p2 = a.points[i + 1];
        Point q1 = b.points[j];
        Point q2 = b.points[j + 1];
        if (Line2D.linesIntersect(p1.x, p1.y, p2.x, p2.y, q1.x, q1.y, q2.x, q2.y)) {
          double d =
              (p2.x - p1.x) * (double) (q2.y - q1.y) - (p2.y - p1.y) * (double) (q2.x - q1.x);
          if (d == 0) {
            return new Point2D.Double(p2.x, p2.y); // collinear overlap
          }
          double t =
              ((q1.x - p1.x) * (double) (q2.y - q1.y) - (q1.y - p1.y) * (double) (q2.x - q1.x)) / d;
          return new Point2D.Double(p1.x + t * (p2.x - p1.x), p1.y + t * (p2.y - p1.y));
        }
      }
    }
    return null;
  }

  /** The point is within GRAZE px of one end of the line: lines meeting there do not cross. */
  private static boolean endsNear(Line line, Point2D at) {
    return at.distance(line.points[0]) <= GRAZE
        || at.distance(line.points[line.points.length - 1]) <= GRAZE;
  }

  /** The point lies on or within GRAZE px of the shape named {@code name}. */
  private static boolean near(List<Box> boxes, String name, Point2D at) {
    for (Box box : boxes) {
      if (box.name.equals(name) && box.area(-GRAZE).contains(at)) {
        return true;
      }
    }
    return false;
  }
}
