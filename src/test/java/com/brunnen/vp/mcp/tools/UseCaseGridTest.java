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
    // Unregistered User comes first in this input; its Search for Vehicle depends on Registered
    // User's Place Order, so it must still end up right after the Registered/Premium group.
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

    // Registered User has the most use cases, so it comes first (by name, Place Order last as
    // Unregistered User's use case depends on it), then Premium User (its child) and
    // Unregistered User (dependent), then System Manager and Time.
    assertEquals(new Point(0, 0), p.get("Cancel Ride"));
    assertEquals(new Point(0, 1), p.get("Confirm Order"));
    assertEquals(new Point(0, 3), p.get("View Order History"));
    assertEquals(new Point(0, 4), p.get("Place Order"));
    assertEquals(new Point(0, 5), p.get("Cancel Subscription"));
    assertEquals(new Point(1, 6), p.get("Search for Vehicle")); // own row, column 0 empty
    assertEquals(new Point(0, 7), p.get("Manage Assets"));
    assertEquals(new Point(0, 9), p.get("Renew Subscription"));
    // Dependents (by name) on the nearest usable row to their base.
    assertEquals(new Point(1, 1), p.get("Compute Route"));
    assertEquals(new Point(1, 2), p.get("Process Payment"));
    assertEquals(new Point(1, 4), p.get("Specify Equipment Requirements"));
    // Rows 1 and 2 carry lines to secondary actors on the right (Compute Route, Process Payment),
    // so Report Incident goes one row up instead of sitting on one of them.
    assertEquals(new Point(2, 0), p.get("Report Incident"));

    // VP's element order differs between sessions: the plan must not depend on it.
    Map<String, List<String>> reversed = new LinkedHashMap<>();
    List<String> names = new java.util.ArrayList<>(actors.keySet());
    java.util.Collections.reverse(names);
    for (String n : names) {
      List<String> ucs = new java.util.ArrayList<>(actors.get(n));
      java.util.Collections.reverse(ucs);
      reversed.put(n, ucs);
    }
    List<String> reversedUseCases = new java.util.ArrayList<>(useCases);
    java.util.Collections.reverse(reversedUseCases);
    assertEquals(p, UseCaseGrid.plan(reversedUseCases, reversed, parent, deps, secondaryLinked));
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

  /**
   * Bike sharing: Process Payment (included by Return Bike and by Charge Subscription far below)
   * takes Return Bike's row, so the extending Report Damage goes above it, not into the path of the
   * include line from below.
   */
  @Test
  public void putsDependentAboveWhenBaseRowHasLineFromBelow() {
    List<String> useCases =
        Arrays.asList(
            "Rent Bike", "Return Bike", "Process Payment", "Report Damage", "Charge Subscription");
    Map<String, List<String>> actors = new LinkedHashMap<>();
    actors.put("Rider", Arrays.asList("Rent Bike", "Return Bike"));
    actors.put("Time", Collections.singletonList("Charge Subscription"));
    List<String[]> deps =
        Arrays.asList(
            new String[] {"Return Bike", "Process Payment"},
            new String[] {"Charge Subscription", "Process Payment"},
            new String[] {"Return Bike", "Report Damage"});

    Map<String, Point> p =
        UseCaseGrid.plan(
            useCases,
            actors,
            new HashMap<>(),
            deps,
            new HashSet<>(Collections.singletonList("Process Payment")));

    assertEquals(new Point(0, 1), p.get("Return Bike"));
    assertEquals(new Point(1, 1), p.get("Process Payment"));
    assertEquals(new Point(1, 0), p.get("Report Damage"));
  }

  /**
   * Fitness center: Register Membership is linked to Member and Receptionist. It goes last in
   * Member's rows, so Receptionist's line from below does not cross Member's line to Reserve Class.
   */
  @Test
  public void putsUseCaseSharedWithAnotherActorLast() {
    Map<String, List<String>> actors = new LinkedHashMap<>();
    actors.put(
        "Member", Arrays.asList("Cancel Reservation", "Register Membership", "Reserve Class"));
    actors.put("Receptionist", Collections.singletonList("Register Membership"));

    Map<String, Point> p =
        UseCaseGrid.plan(
            Arrays.asList("Cancel Reservation", "Register Membership", "Reserve Class"),
            actors,
            new HashMap<>(),
            Collections.emptyList(),
            new HashSet<>());

    assertEquals(new Point(0, 0), p.get("Cancel Reservation"));
    assertEquals(new Point(0, 1), p.get("Reserve Class"));
    assertEquals(new Point(0, 2), p.get("Register Membership"));
  }
}
