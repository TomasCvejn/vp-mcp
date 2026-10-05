package com.brunnen.vp.mcp.tool;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.Test;

/** Tests for the input schema and argument binding of scanned tools. */
public class ToolDefinitionTest {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  /** A tool with one required and two optional parameters. */
  public static class Sample {
    @Tool(name = "greet", description = "test tool")
    public String greet(String name, @OptionalParam String title, @OptionalParam int times) {
      return (title == null ? "" : title + " ") + name + " x" + times;
    }
  }

  /** A tool with a structured parameter. */
  public static class Structured {
    @Tool(name = "count", description = "test tool")
    public String count(
        @ParamSchema("{\"type\": \"object\", \"required\": [\"items\"]}") JsonNode spec) {
      return spec.get("items").size() + " items";
    }
  }

  @Test
  public void structuredParameterGetsItsSchemaAndArrivesAsJson() throws Exception {
    ToolDefinition count = ToolDefinition.scanTools(new Structured(), MAPPER).get(0);
    assertEquals(
        "{\"type\":\"object\",\"required\":[\"items\"]}",
        count.getInputSchema().at("/properties/spec").toString());
    assertEquals("2 items", count.execute(MAPPER.readTree("{\"spec\": {\"items\": [1, 2]}}")));
  }

  private static ToolDefinition greet() {
    List<ToolDefinition> tools = ToolDefinition.scanTools(new Sample(), MAPPER);
    assertEquals(1, tools.size());
    return tools.get(0);
  }

  @Test
  public void optionalParametersAreNotRequired() {
    JsonNode schema = greet().getInputSchema();
    assertEquals("[\"name\"]", schema.get("required").toString());
    assertEquals("string", schema.at("/properties/title/type").asText());
    assertEquals("integer", schema.at("/properties/times/type").asText());
  }

  @Test
  public void omittedOptionalParametersGetDefaults() throws Exception {
    assertEquals("Ada x0", greet().execute(MAPPER.readTree("{\"name\": \"Ada\"}")));
    assertEquals(
        "Dr Ada x2",
        greet().execute(MAPPER.readTree("{\"name\": \"Ada\", \"title\": \"Dr\", \"times\": 2}")));
  }
}
