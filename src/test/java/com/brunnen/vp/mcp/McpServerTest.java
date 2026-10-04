package com.brunnen.vp.mcp;

import static org.junit.Assert.assertEquals;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

/** Tests for the MCP JSON-RPC handling of McpServer. */
public class McpServerTest {

  @Test
  public void initializeSendsInstructions() throws Exception {
    JsonNode request =
        new ObjectMapper().readTree("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\"}");

    JsonNode result = new McpServer().processRequest(request).get("result");

    assertEquals(McpServer.INSTRUCTIONS, result.get("instructions").asText());
  }
}
