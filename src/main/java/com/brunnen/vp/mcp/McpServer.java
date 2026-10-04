package com.brunnen.vp.mcp;

import com.brunnen.vp.mcp.tool.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.undertow.Undertow;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import io.undertow.util.Methods;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

/**
 * Lightweight MCP server using Undertow HTTP server. Implements SSE transport and MCP JSON-RPC
 * protocol without any Spring dependencies.
 */
public class McpServer {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  // SSE streams and tool calls block (stream writes, the EDT); idle threads end after 60 s.
  private static final java.util.concurrent.ExecutorService BLOCKING =
      Executors.newCachedThreadPool();
  // Loopback only: the MCP clients run on the same machine as Visual Paradigm, and the tools can
  // edit and save the open project, so they must not be reachable from the network.
  private static final String BIND_ADDRESS = "127.0.0.1"; // NOPMD AvoidUsingHardCodedIP
  private Undertow server;
  private final List<ToolDefinition> tools = new ArrayList<>();
  // sessionId -> the live SSE output stream, so message responses are pushed back over SSE (MCP SSE
  // transport). Without this the client waits forever for a response that was only sent as the POST
  // body.
  private final Map<String, OutputStream> sseStreams = new ConcurrentHashMap<>();
  private static final int PORT = 2026;
  // Sent once at initialize; MCP clients show it to the model next to the tool list.
  static final String INSTRUCTIONS =
      "Edits the project open in Visual Paradigm. Start with getProjectInfo: stale=true means"
          + " restart Visual Paradigm first; never save or discard the project unless asked."
          + " Elements are found by name within a diagram.\n"
          + "Use case diagram: one buildUseCaseDiagram call with the whole JSON spec (it"
          + " validates, builds, lays out and runs checkLayout and checkUseCaseDiagram); to"
          + " change it, edit the spec and"
          + " call it again with replace=true instead of patching with single-step tools.\n"
          + "After changing any diagram: exportDiagramImage to look at it, and checkLayout for"
          + " overlaps and crossings. After moving shapes by hand: rerouteConnectors.";

  /** Register tool objects (scan for @Tool annotations). Skips duplicate tool names. */
  public void registerTools(Object... toolObjects) {
    java.util.Set<String> registered = new java.util.HashSet<>();
    for (Object obj : toolObjects) {
      for (ToolDefinition td : ToolDefinition.scanTools(obj, MAPPER)) {
        if (registered.add(td.getName())) {
          tools.add(td);
        }
      }
    }
  }

  /** Start the MCP server. */
  public void start() {
    server =
        Undertow.builder()
            .addHttpListener(PORT, BIND_ADDRESS)
            .setHandler(this::handleRequest)
            .setIoThreads(4)
            .setWorkerThreads(16)
            .build();
    server.start();
    System.out.println("MCP Server started on port " + PORT + " with " + tools.size() + " tools");
  }

  /** Stop the MCP server. */
  public void stop() {
    if (server != null) {
      server.stop();
      sseStreams.clear();
      System.out.println("MCP Server stopped");
    }
  }

  // --- Request Router ---

  private void handleRequest(HttpServerExchange exchange) throws Exception {
    String path = exchange.getRequestPath();
    if ("/sse".equals(path)) {
      handleSse(exchange);
    } else if ("/mcp/messages".equals(path)) {
      handleMessage(exchange);
    } else {
      exchange.setStatusCode(404);
      exchange.endExchange();
    }
  }

  // --- SSE Transport ---

  private void handleSse(HttpServerExchange exchange) {
    if (!exchange.getRequestMethod().equals(Methods.GET)) {
      exchange.setStatusCode(405);
      exchange.endExchange();
      return;
    }

    String sessionId = UUID.randomUUID().toString();

    // Set SSE headers
    exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/event-stream; charset=UTF-8");
    exchange.getResponseHeaders().put(new HttpString("Cache-Control"), "no-cache");
    exchange.setStatusCode(200);

    // Run the SSE loop with blocking I/O on its own thread (not one of the few Undertow workers)
    exchange.dispatch(
        BLOCKING,
        () -> {
          exchange.startBlocking();
          OutputStream out = exchange.getOutputStream();
          sseStreams.put(sessionId, out);
          try {
            // Send endpoint event
            String endpointUrl = "/mcp/messages?sessionId=" + sessionId;
            String sseMsg = "event: endpoint\ndata: " + endpointUrl + "\n\n";
            synchronized (out) {
              out.write(sseMsg.getBytes(StandardCharsets.UTF_8));
              out.flush();
            }

            // Keep connection alive
            while (!Thread.currentThread().isInterrupted() && exchange.getConnection().isOpen()) {
              Thread.sleep(15000);
              try {
                synchronized (out) {
                  out.write(":\n\n".getBytes(StandardCharsets.UTF_8));
                  out.flush();
                }
              } catch (IOException e) {
                break;
              }
            }
          } catch (IOException | InterruptedException e) {
            // Client disconnected
          } finally {
            sseStreams.remove(sessionId);
          }
        });
  }

  // --- Message Handler ---

  private void handleMessage(HttpServerExchange exchange) {
    // CORS preflight
    if (exchange.getRequestMethod().equals(Methods.OPTIONS)) {
      exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");
      exchange
          .getResponseHeaders()
          .put(new HttpString("Access-Control-Allow-Methods"), "POST, OPTIONS");
      exchange
          .getResponseHeaders()
          .put(new HttpString("Access-Control-Allow-Headers"), "Content-Type");
      exchange.setStatusCode(204);
      exchange.endExchange();
      return;
    }

    if (!exchange.getRequestMethod().equals(Methods.POST)) {
      exchange.setStatusCode(405);
      exchange.endExchange();
      return;
    }

    exchange.getResponseHeaders().put(new HttpString("Access-Control-Allow-Origin"), "*");

    // Extract sessionId from query
    String sid = null;
    Map<String, Deque<String>> params = exchange.getQueryParameters();
    if (params.containsKey("sessionId")) {
      sid = params.get("sessionId").getFirst();
    }
    final String sessionId = sid;

    exchange.dispatch(
        BLOCKING,
        () -> {
          exchange.startBlocking();
          try {
            String body =
                new String(exchange.getInputStream().readAllBytes(), StandardCharsets.UTF_8);

            JsonNode request = MAPPER.readTree(body);
            JsonNode response = processRequest(request);

            // Notifications (no id) don't get a response
            if (request.has("id") && !request.get("id").isNull()) {
              // Push the response over the SSE stream if one is open for this session (MCP SSE
              // transport); otherwise return it as the HTTP response body.
              OutputStream sseOut = sessionId != null ? sseStreams.get(sessionId) : null;
              if (sseOut != null) {
                String json = MAPPER.writeValueAsString(response);
                String sseMsg = "event: message\ndata: " + json + "\n\n";
                synchronized (sseOut) {
                  sseOut.write(sseMsg.getBytes(StandardCharsets.UTF_8));
                  sseOut.flush();
                }
                exchange.setStatusCode(202);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                exchange.getOutputStream().write("{}".getBytes(StandardCharsets.UTF_8));
              } else {
                byte[] respBytes = MAPPER.writeValueAsBytes(response);
                exchange.setStatusCode(200);
                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "application/json");
                exchange.getOutputStream().write(respBytes);
              }
            } else {
              exchange.setStatusCode(200);
            }
            exchange.getOutputStream().close();
          } catch (IOException | RuntimeException e) {
            try {
              exchange.setStatusCode(500);
              exchange
                  .getOutputStream()
                  .write(("Error: " + e.getMessage()).getBytes(StandardCharsets.UTF_8));
              exchange.getOutputStream().close();
            } catch (IOException suppressed) {
              System.err.println("Failed to send error response: " + suppressed.getMessage());
            }
          }
        });
  }

  // --- MCP Protocol ---

  JsonNode processRequest(JsonNode request) {
    String method = request.has("method") ? request.get("method").asText() : "";
    JsonNode id = request.get("id");
    JsonNode params = request.get("params");

    switch (method) {
      case "initialize":
        return handleInitialize(id);
      case "notifications/initialized":
        return null;
      case "tools/list":
        return handleToolsList(id);
      case "tools/call":
        return handleToolsCall(id, params);
      default:
        return createErrorResponse(id, -32601, "Method not found: " + method);
    }
  }

  private JsonNode handleInitialize(JsonNode id) {
    ObjectNode result = MAPPER.createObjectNode();

    ObjectNode serverInfo = MAPPER.createObjectNode();
    serverInfo.put("name", "visual-paradigm-mcp-server");
    serverInfo.put("version", "1.27.8-ecom");
    result.set("serverInfo", serverInfo);

    result.put("protocolVersion", "2024-11-05");
    result.put("instructions", INSTRUCTIONS);

    ObjectNode capabilities = MAPPER.createObjectNode();
    ObjectNode toolsCap = MAPPER.createObjectNode();
    toolsCap.put("listChanged", false);
    capabilities.set("tools", toolsCap);
    result.set("capabilities", capabilities);

    return createSuccessResponse(id, result);
  }

  private JsonNode handleToolsList(JsonNode id) {
    ObjectNode result = MAPPER.createObjectNode();
    ArrayNode toolsArray = MAPPER.createArrayNode();

    for (ToolDefinition tool : tools) {
      ObjectNode toolObj = MAPPER.createObjectNode();
      toolObj.put("name", tool.getName());
      toolObj.put("description", tool.getDescription());
      toolObj.set("inputSchema", tool.getInputSchema());
      toolsArray.add(toolObj);
    }

    result.set("tools", toolsArray);
    return createSuccessResponse(id, result);
  }

  private JsonNode handleToolsCall(JsonNode id, JsonNode params) {
    if (params == null) {
      return createErrorResponse(id, -32602, "Missing params");
    }

    String toolName = params.has("name") ? params.get("name").asText() : "";
    JsonNode argsNode = params.get("arguments");

    ToolDefinition tool = null;
    for (ToolDefinition t : tools) {
      if (t.getName().equals(toolName)) {
        tool = t;
        break;
      }
    }

    if (tool == null) {
      return createErrorResponse(id, -32602, "Unknown tool: " + toolName);
    }

    try {
      String result = invokeTool(tool, argsNode);
      ObjectNode resultObj = MAPPER.createObjectNode();
      ArrayNode content = MAPPER.createArrayNode();
      ObjectNode textBlock = MAPPER.createObjectNode();
      textBlock.put("type", "text");
      textBlock.put("text", result);
      content.add(textBlock);
      resultObj.set("content", content);
      resultObj.put("isError", false);
      return createSuccessResponse(id, resultObj);
    } catch (Exception e) {
      ObjectNode resultObj = MAPPER.createObjectNode();
      ArrayNode content = MAPPER.createArrayNode();
      ObjectNode textBlock = MAPPER.createObjectNode();
      textBlock.put("type", "text");
      textBlock.put("text", "Error: " + e.getMessage());
      content.add(textBlock);
      resultObj.set("content", content);
      resultObj.put("isError", true);
      return createSuccessResponse(id, resultObj);
    }
  }

  private String invokeTool(ToolDefinition tool, JsonNode argsNode) throws Exception {
    return tool.execute(argsNode);
  }

  // --- JSON-RPC Helpers ---

  private ObjectNode createSuccessResponse(JsonNode id, JsonNode result) {
    ObjectNode response = MAPPER.createObjectNode();
    response.put("jsonrpc", "2.0");
    response.set("id", id);
    response.set("result", result);
    return response;
  }

  private ObjectNode createErrorResponse(JsonNode id, int code, String message) {
    ObjectNode response = MAPPER.createObjectNode();
    response.put("jsonrpc", "2.0");
    if (id != null) {
      response.set("id", id);
    } else {
      response.putNull("id");
    }
    ObjectNode error = MAPPER.createObjectNode();
    error.put("code", code);
    error.put("message", message);
    response.set("error", error);
    return response;
  }
}
