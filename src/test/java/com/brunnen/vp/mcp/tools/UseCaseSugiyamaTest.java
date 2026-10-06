package com.brunnen.vp.mcp.tools;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.awt.Point;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import org.junit.Test;

/** Tests for the layered (Sugiyama) layout of use case diagrams. */
public class UseCaseSugiyamaTest {

  @Test
  public void rowsKeepTheOrderOneApartAndStayNearTheirWish() {
    // Already spaced: kept.
    assertArrayEquals(new double[] {0, 2, 5}, UseCaseSugiyama.spaced(new double[] {0, 2, 5}), 0);
    // All want row 3: spread around it, one apart.
    assertArrayEquals(new double[] {2, 3, 4}, UseCaseSugiyama.spaced(new double[] {3, 3, 3}), 0);
    // Out of order wishes are pooled.
    assertArrayEquals(new double[] {1, 2}, UseCaseSugiyama.spaced(new double[] {2, 1}), 0);
  }

  @Test
  public void sweepsRemoveCrossingsOfTwoLayers() {
    UseCaseSugiyama.Graph g = new UseCaseSugiyama.Graph(2);
    for (String n : Arrays.asList("a", "b", "c")) {
      g.add(n, 0);
    }
    for (String n : Arrays.asList("x", "y", "z")) {
      g.add(n, 1);
    }
    g.connect("a", "z");
    g.connect("b", "y");
    g.connect("c", "x");
    assertEquals(3, g.crossings());
    g.minimizeCrossings(Collections.emptyMap());
    assertEquals(0, g.crossings());
  }

  @Test
  public void longEdgesGetVirtualNodesInTheLayersBetween() {
    UseCaseSugiyama.Graph g = new UseCaseSugiyama.Graph(4);
    g.add("actor", 0);
    g.add("deep", 3);
    g.connect("actor", "deep");
    assertEquals(1, g.layers.get(1).size());
    assertEquals(1, g.layers.get(2).size());
  }

  @Test
  public void familiesStayTogetherWithTheParentFirst() {
    UseCaseSugiyama.Graph g = new UseCaseSugiyama.Graph(2);
    for (String n : Arrays.asList("Child", "Other", "Parent")) {
      g.add(n, 0);
    }
    for (String n : Arrays.asList("u1", "u2", "u3")) {
      g.add(n, 1);
    }
    // Child's use case is at the top, the parent's at the bottom, Other's in the middle.
    g.connect("Child", "u1");
    g.connect("Other", "u2");
    g.connect("Parent", "u3");
    g.minimizeCrossings(Collections.singletonMap("Child", "Parent"));
    int parent = g.layers.get(0).indexOf("Parent");
    assertEquals(parent + 1, g.layers.get(0).indexOf("Child"));
  }

  @Test
  public void clinicGetsOneCellPerUseCaseInItsColumn() {
    UseCaseLayout.Input in = UseCaseLayoutTest.clinic();
    Map<String, Point> cells = UseCaseSugiyama.plan(in);
    assertEquals(in.useCases.size(), new HashSet<>(cells.values()).size());
    assertEquals(1, cells.get("Request Prior Authorization").x);
    assertEquals(0, cells.get("Write Prescription").x);
    for (Point p : cells.values()) {
      assertTrue(p.toString(), p.y >= 0);
    }
  }
}
