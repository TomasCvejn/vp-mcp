package com.brunnen.vp.mcp.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.brunnen.vp.mcp.tools.LayoutCheck.Box;
import com.brunnen.vp.mcp.tools.LayoutCheck.Line;
import java.awt.Point;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Tests for the pure geometry behind checkLayout. */
public class LayoutCheckTest {

  private static Box uc(String name, int x, int y) {
    return new Box(name, true, false, x, y, 160, 60);
  }

  private static List<String> check(List<Box> boxes, Line... lines) {
    return LayoutCheck.check(boxes, Arrays.asList(lines));
  }

  @Test
  public void cleanLayoutHasNoIssues() {
    Box sys = new Box("Sys", false, true, 300, 0, 400, 300);
    Box actor = new Box("A", false, false, 100, 100, 40, 60);
    Box u1 = uc("U1", 360, 40);
    Box u2 = uc("U2", 360, 160);
    List<String> issues =
        check(
            Arrays.asList(sys, actor, u1, u2),
            new Line("A-U1", "A", "U1", new Point(140, 130), new Point(360, 70)),
            new Line("A-U2", "A", "U2", new Point(140, 130), new Point(360, 190)));
    assertEquals(Collections.emptyList(), issues);
  }

  @Test
  public void reportsOverlappingEllipsesButNotTheirTouchingBoundingBoxCorners() {
    // Bounding boxes overlap at the corners, the ellipses themselves do not.
    assertEquals(Collections.emptyList(), check(Arrays.asList(uc("U1", 0, 0), uc("U2", 150, 55))));
    assertEquals(
        Collections.singletonList("overlap: 'U1' and 'U2'"),
        check(Arrays.asList(uc("U1", 0, 0), uc("U2", 100, 20))));
  }

  @Test
  public void reportsShapeStraddlingTheBoundary() {
    Box sys = new Box("Sys", false, true, 300, 0, 400, 300);
    assertEquals(
        Collections.singletonList("straddles boundary: 'U' crosses the edge of 'Sys'"),
        check(Arrays.asList(sys, uc("U", 250, 100))));
  }

  @Test
  public void reportsLineThroughAForeignShapeButNotThroughItsOwnEnds() {
    // A -> U2 runs straight through U1 (the Time line through Manage Assets case).
    Box actor = new Box("A", false, false, 0, 100, 40, 60);
    List<String> issues =
        check(
            Arrays.asList(actor, uc("U1", 200, 100), uc("U2", 500, 100)),
            new Line("A-U2", "A", "U2", new Point(40, 130), new Point(500, 130)));
    assertEquals(Collections.singletonList("line through shape: A-U2 crosses 'U1'"), issues);
  }

  @Test
  public void lineGrazingAnOutlineIsNotReported() {
    // Passes 1px inside the top of the ellipse's bounding box: only touches the outline.
    List<String> issues =
        check(
            Collections.singletonList(uc("U", 200, 100)),
            new Line("X", "P", "Q", new Point(0, 101), new Point(600, 101)));
    assertEquals(Collections.emptyList(), issues);
  }

  @Test
  public void reportsCrossingLinesButNotAnActorsOwnFan() {
    Line l1 = new Line("A-U1", "A", "U1", new Point(0, 0), new Point(100, 100));
    Line l2 = new Line("B-U2", "B", "U2", new Point(0, 100), new Point(100, 0));
    Line fan = new Line("A-U3", "A", "U3", new Point(0, 0), new Point(100, 50));
    List<String> issues = LayoutCheck.check(Collections.emptyList(), Arrays.asList(l1, l2, fan));
    assertTrue(issues.contains("crossing: A-U1 x B-U2"));
    assertTrue(issues.contains("crossing: B-U2 x A-U3"));
    assertEquals(2, issues.size());
  }
}
