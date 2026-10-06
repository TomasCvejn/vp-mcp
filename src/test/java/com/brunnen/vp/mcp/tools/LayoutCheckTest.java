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
  public void reportsLineThroughForeignShapeButNotThroughItsOwnEnds() {
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

  @Test
  public void labelGoesBesideItsLineOnTheSideAwayFromOthers() {
    java.awt.geom.Line2D own = new java.awt.geom.Line2D.Double(0, 100, 200, 100);
    // Another line 30 px below: the 80x16 label goes above its own line (center y 100-12).
    java.awt.geom.Line2D below = new java.awt.geom.Line2D.Double(0, 130, 200, 130);
    assertEquals(
        new Point(60, 80),
        LayoutCheck.labelSpot(
            own, Collections.singletonList(below), Collections.emptyList(), 80, 16));
    // The other line above instead: the label goes below (center y 100+12).
    java.awt.geom.Line2D above = new java.awt.geom.Line2D.Double(0, 70, 200, 70);
    assertEquals(
        new Point(60, 104),
        LayoutCheck.labelSpot(
            own, Collections.singletonList(above), Collections.emptyList(), 80, 16));
  }

  @Test
  public void captionsCountAsShapesExceptAgainstTheirOwnActor() {
    Box upper = new Box("Traffic", false, false, 100, 100, 40, 60);
    Box upperCaption = Box.caption("Traffic", 80, 160, 80, 32);
    Box lower = new Box("Payment", false, false, 100, 185, 40, 60); // head inside the caption
    Box lowerCaption = Box.caption("Payment", 80, 245, 80, 32);
    List<String> issues =
        check(
            Arrays.asList(upper, upperCaption, lower, lowerCaption),
            // The actor's own line may run through its caption; another line may not.
            new Line("U-Traffic", "U", "Traffic", new Point(85, 200), new Point(120, 130)),
            new Line("V-Payment", "V", "Payment", new Point(130, 170), new Point(170, 170)));
    assertEquals(
        Arrays.asList(
            "overlap: 'caption of Traffic' and 'Payment'",
            "line through shape: V-Payment crosses 'caption of Traffic'"),
        issues);
  }

  /**
   * Measured on Three Bases: VP ran both includes along its bounding box corners, so they crossed
   * at Process Payment's bottom-left corner, outside its ellipse. Center-to-center lines into the
   * same use case only meet inside it.
   */
  @Test
  public void reportsLinesIntoOneShapeCrossingOutsideIt() {
    List<Box> boxes =
        Arrays.asList(
            uc("Process Payment", 740, 460),
            new Box("Process Book Return", true, false, 340, 540, 200, 80),
            uc("Charge Fee", 360, 640));
    Line cornerA =
        new Line("PBR-PP", "Process Book Return", "Process Payment", pt(489, 545), pt(780, 516));
    Line cornerB = new Line("CF-PP", "Charge Fee", "Process Payment", pt(502, 650), pt(758, 510));
    assertEquals(
        Collections.singletonList("crossing: PBR-PP x CF-PP"), check(boxes, cornerA, cornerB));

    Line centerA =
        new Line("PBR-PP", "Process Book Return", "Process Payment", pt(525, 560), pt(753, 506));
    Line centerB = new Line("CF-PP", "Charge Fee", "Process Payment", pt(489, 647), pt(771, 513));
    assertEquals(Collections.emptyList(), check(boxes, centerA, centerB));
  }

  private static Point pt(int x, int y) {
    return new Point(x, y);
  }

  /**
   * Clinic: Insurance System (center 1030,460) sits at the average height of its three use cases.
   * Verify Insurance Coverage's straight line is clear; Submit Insurance Claim's runs through
   * Request Prior Authorization, and bending just outside the boundary (x 960) would make its last
   * segment climb into the actor's name caption, so it bends further left, under Request Prior
   * Authorization.
   */
  @Test
  public void bendsSecondaryLineOnlyWhenBlockedAndAboveTheCaption() {
    Point actor = pt(1030, 460);
    java.awt.geom.Rectangle2D caption = new java.awt.geom.Rectangle2D.Double(970, 490, 120, 32);
    List<Box> others =
        Arrays.asList(
            uc("Request Prior Authorization", 740, 460),
            uc("View Patient History", 360, 370),
            new Box("Write Prescription", true, false, 340, 450, 200, 80));
    assertEquals(null, LayoutCheck.secondaryBend(pt(440, 310), actor, others, caption, 540, 960));
    assertEquals(
        pt(780, 580), LayoutCheck.secondaryBend(pt(440, 580), actor, others, caption, 540, 960));
  }

  /**
   * Hotel: beside the middle of Book Room -> Process Payment, the «include» label sat on Add
   * Breakfast (above the line) or Add Parking (below it); it moves along the line to a free spot.
   * checkLayout reports a label on a shape, and a foreign line through a label, but not its own.
   */
  @Test
  public void labelAvoidsShapesAndCheckLayoutReportsLabelsOnShapes() {
    List<Box> shapes = Arrays.asList(uc("Add Breakfast", 740, 100), uc("Add Parking", 740, 190));
    java.awt.geom.Line2D own = new java.awt.geom.Line2D.Double(536, 141, 1124, 211);
    Point spot = LayoutCheck.labelSpot(own, Collections.emptyList(), shapes, 84, 16);
    Line include =
        new Line("Include BR -> PP", "Book Room", "Process Payment", pt(536, 141), pt(1124, 211));
    assertEquals(
        Collections.emptyList(),
        check(
            Arrays.asList(
                shapes.get(0), shapes.get(1), Box.label(include.name, spot.x, spot.y, 84, 16)),
            include));

    // The old spot beside the midpoint, on Add Breakfast.
    Line through = new Line("Assoc A -> B", "A", "B", pt(780, 165), pt(880, 165));
    assertEquals(
        Arrays.asList(
            "overlap: 'Add Breakfast' and 'label of Include BR -> PP'",
            "line through shape: Assoc A -> B crosses 'label of Include BR -> PP'"),
        check(
            Arrays.asList(shapes.get(0), Box.label(include.name, 790, 151, 84, 16)),
            include,
            through));
  }

  /**
   * Hotel, measured: the «extend» label of Apply Discount Code sat between its own line and the
   * Cancel Booking -> Settle Bill line, so it read as either's. It must end up nearer its own line
   * than any other.
   */
  @Test
  public void labelEndsUpNearerItsOwnLineThanAnyOther() {
    java.awt.geom.Line2D own = new java.awt.geom.Line2D.Double(510, 163, 771, 287);
    List<java.awt.geom.Line2D> others =
        Arrays.asList(
            new java.awt.geom.Line2D.Double(529, 151, 753, 204),
            new java.awt.geom.Line2D.Double(489, 243, 771, 377));
    Point spot = LayoutCheck.labelSpot(own, others, Collections.emptyList(), 81, 16);
    double cx = spot.x + 40.5;
    double cy = spot.y + 8;
    for (java.awt.geom.Line2D other : others) {
      assertTrue(
          spot + " nearer another line", own.ptSegDist(cx, cy) + 10 < other.ptSegDist(cx, cy));
    }
  }
}
