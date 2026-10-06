package com.brunnen.vp.mcp.tools;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/** Tests for the pure actor-column placement used by addSystemBoundary. */
public class UseCaseLayoutTest {

  @Test
  public void keepsNonOverlappingDesiredPositions() {
    // Well-spaced desired Ys (heights 50, gap 40 => need >=90 apart): kept as-is.
    int[] ys = UseCaseLayout.stackYs(new int[] {0, 100, 300}, new int[] {50, 50, 50}, 40);
    assertArrayEquals(new int[] {0, 100, 300}, ys);
  }

  @Test
  public void pushesOverlappingActorsDown() {
    // All want y=100; each 50 tall with 40 gap => 90 apart.
    int[] ys = UseCaseLayout.stackYs(new int[] {100, 100, 100}, new int[] {50, 50, 50}, 40);
    assertArrayEquals(new int[] {100, 190, 280}, ys);
  }

  /**
   * Clinic: Doctor and Nurse are both children of Medical Staff, stacked under it. Nurse's line to
   * Medical Staff ran through Doctor, so Nurse moves left until it is clear; Doctor, next to its
   * parent, stays.
   */
  @Test
  public void movesGeneralizationChildAsideWhenSiblingIsInTheWay() {
    int[] dx =
        UseCaseLayout.staggerX(
            new int[] {280, 390, 500}, new int[] {60, 60, 60}, new int[] {-1, 0, 0}, 30, 80);
    assertArrayEquals(new int[] {0, 0, -160}, dx);
  }

  @Test
  public void leavesActorsWithoutAnyoneInTheWayInPlace() {
    int[] dx =
        UseCaseLayout.staggerX(
            new int[] {100, 190, 500}, new int[] {60, 60, 60}, new int[] {-1, 0, -1}, 30, 80);
    assertArrayEquals(new int[] {0, 0, 0}, dx);
  }

  private static Map<String, List<String>> map(String... kv) {
    Map<String, List<String>> m = new LinkedHashMap<>();
    for (int i = 0; i < kv.length; i += 2) {
      m.computeIfAbsent(kv[i], k -> new ArrayList<>());
      if (!kv[i + 1].isEmpty()) {
        m.get(kv[i]).add(kv[i + 1]);
      }
    }
    return m;
  }

  /** Clinic (Medical Staff with children Doctor and Nurse, Insurance System on three rows). */
  static UseCaseLayout.Input clinic() {
    Map<String, String> parent = new HashMap<>();
    parent.put("Doctor", "Medical Staff");
    parent.put("Nurse", "Medical Staff");
    return new UseCaseLayout.Input(
        Arrays.asList(
            "Schedule Appointment",
            "Cancel Appointment",
            "Verify Insurance Coverage",
            "View Patient History",
            "Record Vital Signs",
            "Write Prescription",
            "Request Prior Authorization",
            "Submit Insurance Claim",
            "Send Daily Appointment Reminders"),
        map(
            "Patient", "Schedule Appointment",
            "Patient", "Cancel Appointment",
            "Receptionist Desk", "Schedule Appointment",
            "Receptionist Desk", "Verify Insurance Coverage",
            "Medical Staff", "View Patient History",
            "Doctor", "Write Prescription",
            "Doctor", "Submit Insurance Claim",
            "Nurse", "Record Vital Signs",
            "Time", "Send Daily Appointment Reminders"),
        map(
            "Insurance System", "Verify Insurance Coverage",
            "Insurance System", "Submit Insurance Claim",
            "Insurance System", "Request Prior Authorization",
            "SMS Gateway", "Send Daily Appointment Reminders"),
        parent,
        Collections.singletonList(
            new String[] {"Write Prescription", "Request Prior Authorization"}),
        Collections.singleton("Write Prescription"),
        new HashSet<>(Arrays.asList("Time", "Insurance System", "SMS Gateway")));
  }

  @Test
  public void predictsLinesThroughShapesAndCrossings() {
    UseCaseLayout.Input in = clinic();
    Map<String, java.awt.Point> rules =
        UseCaseGrid.plan(in.useCases, in.primary, in.actorParent, in.deps, in.secondaryLinked());
    // The rules' layout: Submit Insurance Claim's straight line runs through Request Prior
    // Authorization (measured live with checkLayout before lines to secondary actors bent).
    assertTrue(
        UseCaseLayout.issues(UseCaseLayout.drawing(in, rules))
            .contains(
                "10 Insurance System - Submit Insurance Claim"
                    + " through Request Prior Authorization"));
  }

  /**
   * SmartTaxIS: the include CreateOrder -> Navigate passed 0.3 px outside SearchForTaxis's 2 px
   * inset, and checkLayout (1 px pen, whole-pixel ends) reported it through the shape.
   */
  @Test
  public void aLineGrazingAnEllipseByLessThanThePenHitsIt() {
    java.awt.geom.Rectangle2D searchForTaxis =
        new java.awt.geom.Rectangle2D.Double(340, 810, 200, 80);
    assertTrue(
        UseCaseLayout.hits(
            new java.awt.geom.Line2D.Double(440, 760, 1200, 1390), searchForTaxis, true));
    assertFalse(
        UseCaseLayout.hits(
            new java.awt.geom.Line2D.Double(440, 760, 1200, 1300), searchForTaxis, true));
  }

  /**
   * The SmartTaxIS of the external review.png, transcribed 1:1: «system» Time, Payment Provider and
   * Traffic Provider on the right, Premium User left (it starts ManageMembership).
   */
  static UseCaseLayout.Input reviewSmartTaxis() {
    Map<String, String> parent = new HashMap<>();
    parent.put("Premium User", "Registered User");
    parent.put("Free User", "Registered User");
    return new UseCaseLayout.Input(
        Arrays.asList(
            "RegisterAccount",
            "SearchForTaxis",
            "AdvancedSearch",
            "ManageMembership",
            "SendRenewalNotification",
            "CancelOrder",
            "IssueFine",
            "CreateOrder",
            "ApplyDiscount",
            "ViewOrderHistory",
            "BuyPremium",
            "ProvidePayment",
            "RenewMemberships",
            "Navigate",
            "SendBatteryStatus",
            "ReportIncident",
            "ManageCompanyAssets",
            "ViewStatistics"),
        map(
            "Unregistered User", "RegisterAccount",
            "Unregistered User", "SearchForTaxis",
            "Premium User", "ManageMembership",
            "Premium User", "SendRenewalNotification",
            "Registered User", "SearchForTaxis",
            "Registered User", "CancelOrder",
            "Registered User", "CreateOrder",
            "Registered User", "ViewOrderHistory",
            "Free User", "BuyPremium",
            "Manager", "ManageCompanyAssets",
            "Manager", "ViewStatistics",
            "Taxi", "Navigate",
            "Taxi", "SendBatteryStatus",
            "Taxi", "ReportIncident"),
        map(
            "Time", "SendRenewalNotification",
            "Time", "CancelOrder",
            "Time", "RenewMemberships",
            "Payment Provider", "ProvidePayment",
            "Traffic Provider", "Navigate"),
        parent,
        Arrays.asList(
            new String[] {"CreateOrder", "ProvidePayment"},
            new String[] {"RenewMemberships", "ProvidePayment"},
            new String[] {"BuyPremium", "ProvidePayment"},
            new String[] {"CreateOrder", "Navigate"},
            new String[] {"SearchForTaxis", "AdvancedSearch"},
            new String[] {"CancelOrder", "IssueFine"},
            new String[] {"CreateOrder", "ApplyDiscount"},
            new String[] {"SendBatteryStatus", "Navigate"}),
        new HashSet<>(
            Arrays.asList("SearchForTaxis", "CancelOrder", "CreateOrder", "SendBatteryStatus")),
        new HashSet<>(Arrays.asList("Time", "Payment Provider", "Traffic Provider")));
  }

  /** 4000 annealing steps froze at 3 crossings (12 points); the longer runs reach 1. */
  @Test
  public void planLeavesOneCrossingOnTheReviewSmartTaxis() {
    UseCaseLayout.Input in = reviewSmartTaxis();
    assertEquals(
        Collections.singletonList("4 Registered User - CancelOrder x Free User - BuyPremium"),
        UseCaseLayout.issues(UseCaseLayout.drawing(in, UseCaseLayout.plan(in))));
  }

  @Test
  public void planRemovesThePredictedProblems() {
    UseCaseLayout.Input in = clinic();
    assertEquals(
        Collections.emptyList(),
        UseCaseLayout.issues(UseCaseLayout.drawing(in, UseCaseLayout.plan(in))));
  }

  @Test
  public void planKeepsLayoutWithoutProblems() {
    // Bike sharing's rules layout has no predicted problem: nothing moves.
    UseCaseLayout.Input in =
        new UseCaseLayout.Input(
            Arrays.asList(
                "Rent Bike",
                "Return Bike",
                "Process Payment",
                "Report Damage",
                "Charge Subscription"),
            map(
                "Rider", "Rent Bike",
                "Rider", "Return Bike",
                "Time", "Charge Subscription"),
            map("Payment Gateway", "Process Payment"),
            new HashMap<>(),
            Arrays.asList(
                new String[] {"Return Bike", "Process Payment"},
                new String[] {"Charge Subscription", "Process Payment"},
                new String[] {"Return Bike", "Report Damage"}),
            Collections.singleton("Return Bike"),
            new HashSet<>(Arrays.asList("Time", "Payment Gateway")));
    assertEquals(
        UseCaseGrid.plan(in.useCases, in.primary, in.actorParent, in.deps, in.secondaryLinked()),
        UseCaseLayout.plan(in));
  }

  /**
   * University: the optimizer once put Exchange Student at the top and its parent Student at the
   * bottom; an unrelated actor between a child and its parent now counts as a problem.
   */
  @Test
  public void unrelatedActorBetweenChildAndParentCounts() {
    UseCaseLayout.Input in =
        new UseCaseLayout.Input(
            Arrays.asList("A", "B", "C"),
            map("Parent", "A", "Other", "B", "Child", "C"),
            new LinkedHashMap<>(),
            Collections.singletonMap("Child", "Parent"),
            Collections.emptyList(),
            Collections.emptySet(),
            Collections.emptySet());
    Map<String, java.awt.Point> cells = new LinkedHashMap<>();
    cells.put("A", new java.awt.Point(0, 0));
    cells.put("B", new java.awt.Point(0, 1));
    cells.put("C", new java.awt.Point(0, 2));
    assertTrue(
        UseCaseLayout.issues(UseCaseLayout.drawing(in, cells))
            .contains("6 Other between Child and Parent"));
  }

  @Test
  public void annealingIsRepeatableAndKeepsIncludeDirection() {
    UseCaseLayout.Input in = clinic();
    Map<String, java.awt.Point> rules =
        UseCaseGrid.plan(in.useCases, in.primary, in.actorParent, in.deps, in.secondaryLinked());
    Map<String, java.awt.Point> once = UseCaseLayout.anneal(in, rules, 10, 2000, 7);
    assertEquals(once, UseCaseLayout.anneal(in, rules, 10, 2000, 7));
    assertTrue(once.get("Request Prior Authorization").x > once.get("Write Prescription").x);
    assertEquals(in.useCases.size(), new HashSet<>(once.values()).size());
  }
}
