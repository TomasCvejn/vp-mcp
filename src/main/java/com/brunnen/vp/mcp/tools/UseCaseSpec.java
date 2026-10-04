package com.brunnen.vp.mcp.tools;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A whole use case diagram as one JSON document, for buildUseCaseDiagram. Pure (no VP API) and
 * validated as a whole, so a bad spec is rejected with every problem listed before anything is
 * created.
 *
 * <pre>
 * {"actors": ["Customer", "Payment Gateway"],
 *  "stereotypes": {"Payment Gateway": "system"},
 *  "useCases": ["Place Order", "Pay for Order", "Apply Promo Code"],
 *  "links": [["Customer", "Place Order"]],              primary actor - use case (plain line)
 *  "calls": [["Pay for Order", "Payment Gateway"]],     use case -> secondary actor (arrow)
 *  "includes": [["Place Order", "Pay for Order"]],      base -> included
 *  "extends": [["Apply Promo Code", "Place Order", "promo code"]],  extending -> base [, point]
 *  "generalizations": [["Premium User", "Customer"]]}   child -> parent
 * </pre>
 */
final class UseCaseSpec {

  final Set<String> actors = new LinkedHashSet<>();
  final Set<String> useCases = new LinkedHashSet<>();
  final Map<String, String> stereotypes = new LinkedHashMap<>();
  final List<String[]> links = new ArrayList<>();
  final List<String[]> calls = new ArrayList<>();
  final List<String[]> includes = new ArrayList<>();
  final List<String[]> extendsList = new ArrayList<>();
  final List<String[]> generalizations = new ArrayList<>();

  private UseCaseSpec() {}

  /**
   * Parse and validate a spec.
   *
   * @throws IllegalArgumentException listing every problem found
   */
  static UseCaseSpec parse(String json) {
    JsonNode root;
    try {
      root = new ObjectMapper().readTree(json == null ? "" : json);
    } catch (IOException e) {
      throw new IllegalArgumentException("spec is not valid JSON: " + e.getMessage(), e);
    }
    if (root == null || !root.isObject()) {
      throw new IllegalArgumentException("spec must be a JSON object");
    }
    UseCaseSpec spec = new UseCaseSpec();
    List<String> problems = new ArrayList<>();
    names(root, "actors", spec.actors, problems);
    names(root, "useCases", spec.useCases, problems);
    for (String name : spec.actors) {
      if (spec.useCases.contains(name)) {
        problems.add("'" + name + "' is both an actor and a use case");
      }
    }
    JsonNode st = root.path("stereotypes");
    Iterator<Map.Entry<String, JsonNode>> fields = st.fields();
    while (fields.hasNext()) {
      Map.Entry<String, JsonNode> e = fields.next();
      if (!spec.actors.contains(e.getKey()) && !spec.useCases.contains(e.getKey())) {
        problems.add("stereotypes: unknown element '" + e.getKey() + "'");
      }
      spec.stereotypes.put(e.getKey(), e.getValue().asText());
    }
    pairs(root, "links", spec.links, spec.actors, "actor", spec.useCases, "use case", 2, problems);
    pairs(root, "calls", spec.calls, spec.useCases, "use case", spec.actors, "actor", 2, problems);
    pairs(
        root,
        "includes",
        spec.includes,
        spec.useCases,
        "use case",
        spec.useCases,
        "use case",
        2,
        problems);
    pairs(
        root,
        "extends",
        spec.extendsList,
        spec.useCases,
        "use case",
        spec.useCases,
        "use case",
        3,
        problems);
    pairs(
        root,
        "generalizations",
        spec.generalizations,
        spec.actors,
        "actor",
        spec.actors,
        "actor",
        2,
        problems);
    Iterator<String> keys = root.fieldNames();
    while (keys.hasNext()) {
      String key = keys.next();
      if (!java.util.Arrays.asList(
              "actors",
              "useCases",
              "stereotypes",
              "links",
              "calls",
              "includes",
              "extends",
              "generalizations")
          .contains(key)) {
        problems.add("unknown key '" + key + "'");
      }
    }
    if (!problems.isEmpty()) {
      throw new IllegalArgumentException("Invalid spec:\n- " + String.join("\n- ", problems));
    }
    return spec;
  }

  private static void names(JsonNode root, String key, Set<String> out, List<String> problems) {
    for (JsonNode n : root.path(key)) {
      String name = n.asText().trim();
      if (name.isEmpty()) {
        problems.add(key + ": empty name");
      } else if (!out.add(name)) {
        problems.add(key + ": '" + name + "' listed twice");
      }
    }
  }

  /** Read [from, to(, optional extra)] entries whose ends must be of the given kinds. */
  private static void pairs(
      JsonNode root,
      String key,
      List<String[]> out,
      Set<String> fromKind,
      String fromLabel,
      Set<String> toKind,
      String toLabel,
      int maxSize,
      List<String> problems) {
    for (JsonNode n : root.path(key)) {
      if (!n.isArray() || n.size() < 2 || n.size() > maxSize) {
        problems.add(
            key + ": " + n + " must be [from, to" + (maxSize > 2 ? "(, point)" : "") + "]");
        continue;
      }
      String from = n.get(0).asText().trim();
      String to = n.get(1).asText().trim();
      if (!fromKind.contains(from)) {
        problems.add(key + ": '" + from + "' is not a declared " + fromLabel);
      }
      if (!toKind.contains(to)) {
        problems.add(key + ": '" + to + "' is not a declared " + toLabel);
      }
      if (from.equals(to)) {
        problems.add(key + ": '" + from + "' cannot point to itself");
      }
      out.add(
          n.size() == 3
              ? new String[] {from, to, n.get(2).asText().trim()}
              : new String[] {from, to});
    }
  }
}
