package com.brunnen.vp.mcp.tools;

import java.awt.BasicStroke;
import java.awt.Point;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
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
        boolean own = line.touches(box.name) || (box.owner != null && line.touches(box.owner));
        if (!box.container && !own && intersect(stroke, box.area(GRAZE))) {
          issues.add("line through shape: " + line.name + " crosses '" + box.name + "'");
        }
      }
    }
    for (int i = 0; i < lines.size(); i++) {
      for (int j = i + 1; j < lines.size(); j++) {
        Line a = lines.get(i);
        Line b = lines.get(j);
        // Lines sharing a shape meet at it by design (an actor's fan, include chains).
        if (!a.touches(b.from) && !a.touches(b.to) && cross(a, b)) {
          issues.add("crossing: " + a.name + " x " + b.name);
        }
      }
    }
    return issues;
  }

  /**
   * Where to put a w x h label of the line {@code own} so that it is clearly that line's: centered
   * just beside the midpoint, on whichever side is farther from the {@code others} lines.
   *
   * @return the label's top-left corner
   */
  static Point labelSpot(Line2D own, List<Line2D> others, int w, int h) {
    double mx = (own.getX1() + own.getX2()) / 2;
    double my = (own.getY1() + own.getY2()) / 2;
    double dx = own.getX2() - own.getX1();
    double dy = own.getY2() - own.getY1();
    double len = Math.hypot(dx, dy);
    // Unit normal; a degenerate line gets the vertical one.
    double nx = len == 0 ? 0 : -dy / len;
    double ny = len == 0 ? 1 : dx / len;
    // Far enough that the label's nearest corner clears the line, whatever its slope.
    double offset = (Math.abs(nx) * w + Math.abs(ny) * h) / 2 + LABEL_GAP;
    Point best = null;
    double bestClearance = -1;
    for (int side : new int[] {1, -1}) {
      double cx = mx + side * nx * offset;
      double cy = my + side * ny * offset;
      double clearance = Double.MAX_VALUE;
      for (Line2D other : others) {
        clearance = Math.min(clearance, other.ptSegDist(cx, cy));
      }
      if (clearance > bestClearance) {
        bestClearance = clearance;
        best = new Point((int) Math.round(cx - w / 2.0), (int) Math.round(cy - h / 2.0));
      }
    }
    return best;
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

  private static boolean cross(Line a, Line b) {
    for (int i = 0; i + 1 < a.points.length; i++) {
      for (int j = 0; j + 1 < b.points.length; j++) {
        if (Line2D.linesIntersect(
            a.points[i].x,
            a.points[i].y,
            a.points[i + 1].x,
            a.points[i + 1].y,
            b.points[j].x,
            b.points[j].y,
            b.points[j + 1].x,
            b.points[j + 1].y)) {
          return true;
        }
      }
    }
    return false;
  }
}
