---
name: usecase-diagram
description: Create or fix a UML use case diagram in Visual Paradigm from requirements text, through the visual-paradigm MCP server, and review it until it is clean. Use when the user asks for a use case diagram (or "UC diagram", "diagram případů užití").
---

# Use case diagram

The diagram is generated from one JSON spec. The spec is the source of truth: fix the spec
and rebuild, do not patch the diagram with single-step tools.

## 1. Guard

`getProjectInfo`: `stale: true` → ask the user to restart Visual Paradigm and stop.
Note `modified`; never save or discard the project unless asked.

## 2. Write the spec

From the requirements, before calling any tool:

- **Use cases** = goals an actor reaches *with the system*, named verb + object
  ("Rent Bike"). Nothing that happens outside the system (§1.1).
- **Primary actors** start a use case → `links: [[actor, useCase]]`.
  **Secondary actors** are called by the system → `calls: [[useCase, actor]]`.
  An actor is never both for the same use case.
- Another system → stereotype `"system"`; a scheduled trigger → actor `Time` with `"time"`.
  Put the period into the use case name ("Charge Monthly Subscription", checklist BP3a).
  Never an actor for the modelled system itself (§1.6).
- `includes: [[base, included]]` only for behaviour shared by **two or more** bases
  (§1.11); never as functional decomposition (§1.7).
- `extends: [[extending, base, extensionPoint]]` for optional behaviour; always name the
  extension point.
- `generalizations: [[child, parent]]` only for a real "is a" with all inherited use cases
  wanted (§1.8, §1.9, §1.12).

When unsure about a rule, read `review/usecase.md` (§), `review/usecase-conventions.md` (C)
or `review/usecase-checklist.md` (SYN/BP/SEM/STY).

## 3. Build

`buildUseCaseDiagram(diagramName, systemName, spec, replace)`. It validates the whole spec
first, builds, lays out and appends `checkLayout`. Use `replace: true` when rebuilding.
Names already used in the project are shared with other diagrams (one actor, many diagrams).
`Spec warnings` in the result are catalog mistakes seen in the spec alone: fix them in the
spec and rebuild before the review, or keep one on purpose and say so in the report.

## 4. Review

1. `exportDiagramImage` to the scratchpad.
2. In parallel: `diagram-reviewer` (type `usecase`, the PNG path) and `visual-reviewer`
   (the PNG path, plus the `checkLayout` output).

## 5. Fix loop

- **Model findings** (`diagram-reviewer`) → change the spec → step 3 with `replace: true` →
  step 4.
- **Layout findings** (`visual-reviewer`, `checkLayout`) cannot be fixed in the spec: the
  layout is computed and a rebuild draws the same picture. Move the shape by hand:
  `setElementBounds` **with** `width: 160, height: 60` (without them it shrinks the ellipse
  to its text), then `rerouteConnectors` (lines do not follow a moved shape), then
  `checkLayout`. A later rebuild drops manual moves, so fix the model first.
- A finding that is a question about the requirements (e.g. "may report damage" = only on
  return, or any time?) goes to the user instead of being guessed.

Stop when both reviewers have nothing above `nit`, or after 3 rounds.
A finding you think is wrong is still reported to the user, never silently dropped.

## 6. Report

The PNG, the final spec, and every remaining finding with its id (§, C, SYN/BP/SEM/STY).
