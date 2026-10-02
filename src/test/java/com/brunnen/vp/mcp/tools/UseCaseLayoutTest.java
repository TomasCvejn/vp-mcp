package com.brunnen.vp.mcp.tools;

import static org.junit.Assert.assertArrayEquals;

import org.junit.Test;

/** Tests for the pure actor-column stacking used by addSystemBoundary. */
public class UseCaseLayoutTest {

  @Test
  public void keepsNonOverlappingDesiredPositions() {
    // Well-spaced desired Ys (heights 50, gap 40 => need >=90 apart): kept as-is.
    int[] ys = UseCaseMcpTools.stackYs(new int[] {0, 100, 300}, new int[] {50, 50, 50}, 40);
    assertArrayEquals(new int[] {0, 100, 300}, ys);
  }

  @Test
  public void pushesOverlappingActorsDown() {
    // All want y=100; each 50 tall with 40 gap => 90 apart.
    int[] ys = UseCaseMcpTools.stackYs(new int[] {100, 100, 100}, new int[] {50, 50, 50}, 40);
    assertArrayEquals(new int[] {100, 190, 280}, ys);
  }
}
