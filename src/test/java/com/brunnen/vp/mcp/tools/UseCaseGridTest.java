package com.brunnen.vp.mcp.tools;

import static org.junit.Assert.assertEquals;

import java.awt.Point;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/** Tests for the use case grid planner behind layoutUseCaseDiagram. */
public class UseCaseGridTest {

  /** The SmartTaxIS diagram the planner was derived from (laid out by hand first). */
  @Test
  public void plansSmartTaxIs() {
    List<String> useCases =
        Arrays.asList(
            "View Statistics",
            "Manage Assets",
            "Report Incident",
            "Renew Subscription",
            "Cancel Subscription",
            "Subscribe to Premium",
            "View Order History",
            "Cancel Ride",
            "Compute Route",
            "Process Payment",
            "Confirm Order",
            "Specify Equipment Requirements",
            "Place Order",
            "Search for Vehicle");
    Map<String, List<String>> actors = new LinkedHashMap<>();
    // Unregistered User comes first here, but its Search for Vehicle depends on Registered User's
    // Place Order, so it must end up right after the Registered/Premium group.
    actors.put("Unregistered User", Collections.singletonList("Search for Vehicle"));
    actors.put("Time", Collections.singletonList("Renew Subscription"));
    actors.put("System Manager", Arrays.asList("Manage Assets", "View Statistics"));
    actors.put("Premium User", Collections.singletonList("Cancel Subscription"));
    actors.put(
        "Registered User",
        Arrays.asList(
            "Place Order",
            "Subscribe to Premium",
            "View Order History",
            "Cancel Ride",
            "Confirm Order"));
    Map<String, String> parent = Collections.singletonMap("Premium User", "Registered User");
    List<String[]> deps =
        Arrays.asList(
            new String[] {"Place Order", "Search for Vehicle"},
            new String[] {"Confirm Order", "Process Payment"},
            new String[] {"Confirm Order", "Compute Route"},
            new String[] {"Place Order", "Specify Equipment Requirements"},
            new String[] {"Compute Route", "Report Incident"});
    Set<String> secondaryLinked =
        new HashSet<>(Arrays.asList("Process Payment", "Compute Route", "Report Incident"));

    Map<String, Point> p = UseCaseGrid.plan(useCases, actors, parent, deps, secondaryLinked);

    assertEquals(new Point(0, 0), p.get("Renew Subscription"));
    assertEquals(new Point(0, 1), p.get("Manage Assets"));
    assertEquals(new Point(0, 2), p.get("View Statistics"));
    assertEquals(new Point(0, 3), p.get("Subscribe to Premium"));
    assertEquals(new Point(0, 6), p.get("Confirm Order"));
    // Place Order is a base of Unregistered User's use case: last in Registered User's rows.
    assertEquals(new Point(0, 7), p.get("Place Order"));
    assertEquals(new Point(0, 8), p.get("Cancel Subscription")); // Premium right after parent
    assertEquals(new Point(1, 9), p.get("Search for Vehicle")); // own row, column 0 empty
    // Dependents (in diagram order) on the nearest usable row to their base.
    assertEquals(new Point(1, 6), p.get("Compute Route"));
    assertEquals(new Point(1, 7), p.get("Process Payment"));
    assertEquals(new Point(1, 8), p.get("Specify Equipment Requirements"));
    // Rows 6 and 7 carry lines to secondary actors on the right (Compute Route, Process Payment),
    // so Report Incident goes one row up instead of sitting on one of them.
    assertEquals(new Point(2, 5), p.get("Report Incident"));
    assertEquals(useCases.size(), p.size());
  }

  @Test
  public void includeCycleAndUnlinkedUseCasesStillGetCells() {
    Map<String, Point> p =
        UseCaseGrid.plan(
            Arrays.asList("A", "B", "Lonely"),
            new LinkedHashMap<>(),
            new HashMap<>(),
            Arrays.asList(new String[] {"A", "B"}, new String[] {"B", "A"}),
            Collections.emptySet());
    assertEquals(3, p.size());
    assertEquals(3, new java.util.HashSet<>(p.values()).size()); // no two share a cell
  }

  @Test
  public void generalizationCycleKeepsBothActors() {
    Map<String, List<String>> actors = new LinkedHashMap<>();
    actors.put("X", Collections.singletonList("U1"));
    actors.put("Y", Collections.singletonList("U2"));
    Map<String, String> parent = new HashMap<>();
    parent.put("X", "Y");
    parent.put("Y", "X");
    assertEquals(Arrays.asList("X", "Y"), UseCaseGrid.actorOrder(actors, parent, new HashMap<>()));
  }
}
