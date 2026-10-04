package com.brunnen.vp.mcp.tool;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a tool parameter that MCP clients may omit: it is left out of the input schema's "required"
 * list and arrives as null (or 0 / false for primitives) when not given.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface OptionalParam {}
