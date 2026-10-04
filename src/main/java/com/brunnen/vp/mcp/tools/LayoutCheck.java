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

    Box(String name, boolean ellipse, boolean container, int x, int y, int w, int h) {
      this.name = name;
      this.ellipse = ellipse;
      this.container = container;
      this.bounds = new Rectangle2D.Double(x, y, w, h);
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

  private LayoutCheck() {}

  /** Every layout problem found, one human-readable line each; empty when the layout is clean. */
  static List<String> check(List<Box> boxes, List<Line> lines) {
    List<String> issues = new ArrayList<>();
    for (int i = 0; i < boxes.size(); i++) {
      for (int j = i + 1; j < boxes.size(); j++) {
        Box a = boxes.get(i);
        Box b = boxes.get(j);
        if (a.container == b.container) {
          if (!a.container && intersect(a.area(0), b.area(0))) {
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
        if (!box.container && !line.touches(box.name) && intersect(stroke, box.area(GRAZE))) {
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
