package com.brunnen.vp.mcp.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The JSON Schema of a structured tool parameter (declared as {@code JsonNode}), sent in the input
 * schema so MCP clients and the model see its structure instead of a JSON-in-a-string.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ParamSchema {
  /** The parameter's JSON Schema, as JSON. */
  String value();
}
