package com.brunnen.vp.mcp.tools;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import org.junit.Test;

/** Tests for the buildUseCaseDiagram spec parser. */
public class UseCaseSpecTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private static UseCaseSpec parse(String json) {
    try {
      return UseCaseSpec.parse(json == null ? null : MAPPER.readTree(json));
    } catch (java.io.IOException e) {
      throw new AssertionError(e);
    }
  }

  private static String problems(String json) {
    try {
      parse(json);
    } catch (IllegalArgumentException e) {
      return e.getMessage();
    }
    fail("expected the spec to be rejected: " + json);
    return null;
  }

  @Test
  public void parsesWholeDiagram() {
    UseCaseSpec s =
        parse(
            "{\"actors\": [\"Customer\", \"Payment Gateway\", \"Premium\"],"
                + " \"stereotypes\": {\"Payment Gateway\": \"system\"},"
                + " \"useCases\": [\"Place Order\", \"Pay for Order\", \"Apply Promo Code\"],"
                + " \"links\": [[\"Customer\", \"Place Order\"]],"
                + " \"calls\": [[\"Pay for Order\", \"Payment Gateway\"]],"
                + " \"includes\": [[\"Place Order\", \"Pay for Order\"]],"
                + " \"extends\": [[\"Apply Promo Code\", \"Place Order\", \"promo code\"]],"
                + " \"generalizations\": [[\"Premium\", \"Customer\"]]}");
    assertEquals(
        Arrays.asList("Customer", "Payment Gateway", "Premium"),
        new java.util.ArrayList<>(s.actors));
    assertEquals(3, s.useCases.size());
    assertEquals("system", s.stereotypes.get("Payment Gateway"));
    assertArrayEquals(new String[] {"Customer", "Place Order"}, s.links.get(0));
    assertArrayEquals(new String[] {"Pay for Order", "Payment Gateway"}, s.calls.get(0));
    assertArrayEquals(new String[] {"Place Order", "Pay for Order"}, s.includes.get(0));
    assertArrayEquals(
        new String[] {"Apply Promo Code", "Place Order", "promo code"}, s.extendsList.get(0));
    assertArrayEquals(new String[] {"Premium", "Customer"}, s.generalizations.get(0));
  }

  @Test
  public void listsEveryProblemAtOnce() {
    String msg =
        problems(
            "{\"actors\": [\"Customer\", \"Customer\", \"Order\"],"
                + " \"useCases\": [\"Order\", \"Pay\"],"
                + " \"stereotypes\": {\"Ghost\": \"system\"},"
                + " \"links\": [[\"Pay\", \"Customer\"], [\"Customer\"]],"
                + " \"includes\": [[\"Pay\", \"Pay\"]],"
                + " \"colour\": \"blue\"}");
    assertTrue(msg, msg.contains("actors: 'Customer' listed twice"));
    assertTrue(msg, msg.contains("'Order' is both an actor and a use case"));
    assertTrue(msg, msg.contains("stereotypes: unknown element 'Ghost'"));
    // A link runs from a primary actor to a use case, not the other way round.
    assertTrue(msg, msg.contains("links: 'Pay' is not a declared actor"));
    assertTrue(msg, msg.contains("links: 'Customer' is not a declared use case"));
    assertTrue(msg, msg.contains("must be [from, to]"));
    assertTrue(msg, msg.contains("includes: 'Pay' cannot point to itself"));
    assertTrue(msg, msg.contains("unknown key 'colour'"));
  }

  @Test
  public void rejectsNonObjectsAndBrokenJson() {
    assertTrue(problems("[1, 2]").contains("must be a JSON object"));
    assertTrue(problems(null).contains("must be a JSON object"));
    assertTrue(problems("{\"actors\": [\" \"]}").contains("actors: empty name"));
  }

  @Test
  public void rejectsCycles() {
    String msg =
        problems(
            "{\"actors\": [\"A\", \"B\"], \"useCases\": [\"X\", \"Y\", \"Z\"],"
                + " \"includes\": [[\"X\", \"Y\"]],"
                + " \"extends\": [[\"X\", \"Y\", \"p\"]],"
                + " \"generalizations\": [[\"A\", \"B\"], [\"B\", \"A\"]]}");
    // X includes Y (X -> Y) and X extends Y (base Y -> X) close a loop.
    assertTrue(msg, msg.contains("includes/extends: cycle X -> Y -> X"));
    assertTrue(msg, msg.contains("generalizations: cycle A -> B -> A"));
  }

  /** A broken schema would only fail when Visual Paradigm loads the plugin. */
  @Test
  public void schemaIsValidJsonListingEverySpecKey() throws Exception {
    com.fasterxml.jackson.databind.JsonNode schema = MAPPER.readTree(UseCaseSpec.SCHEMA);
    assertEquals("[\"actors\",\"useCases\"]", schema.get("required").toString());
    assertEquals(
        Arrays.asList(
            "actors",
            "stereotypes",
            "useCases",
            "links",
            "calls",
            "includes",
            "extends",
            "generalizations"),
        names(schema.get("properties").fieldNames()));
  }

  private static java.util.List<String> names(java.util.Iterator<String> it) {
    java.util.List<String> out = new java.util.ArrayList<>();
    it.forEachRemaining(out::add);
    return out;
  }
}
