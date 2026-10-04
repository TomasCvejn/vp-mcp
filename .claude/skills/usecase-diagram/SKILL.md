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
  ("Rent Bike"). Nothing that happens outside the system (§1.1). List them in reading
  order ("Reserve Class" before "Cancel Reservation"): each actor's rows follow it.
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

**Assumptions.** Next to the spec, list every guess the requirements left open (a "may",
who starts a use case, whether something only happens inside another one). Ask the user
about the ones that change the spec, all in one question, before building; the others go
into the report. One question now is cheaper than a review round.

## 3. Build

`buildUseCaseDiagram(diagramName, systemName, spec, replace)`. It validates the whole spec
first, builds, lays out and appends `checkLayout` and `checkUseCaseDiagram`. Use
`replace: true` when rebuilding.
Names already used in the project are shared with other diagrams (one actor, many diagrams).
Problems `checkUseCaseDiagram` lists are certain (it reads the model): fix them in the spec
and rebuild before the review, or keep one on purpose and say so in the report.

## 4. Review

1. `exportDiagramImage` to the scratchpad.
2. `diagram-reviewer` with the type `usecase`, the PNG path, **the requirements text** (it
   checks that every requirement is covered) and **the `checkUseCaseDiagram` output** (ground
   truth for the ids it names, so the reviewer judges only what needs judgement).
3. `visual-reviewer` (the PNG path and the `checkLayout` output) only when `checkLayout` is
   not `OK`: with a clean geometry it finds nits only. Run both in parallel when both run.

## 5. Fix loop

- **Model findings** (`diagram-reviewer`) → change the spec → step 3 with `replace: true` →
  step 4.
- **Layout findings** (`visual-reviewer`, `checkLayout`) cannot be fixed in the spec: the
  layout is computed and a rebuild draws the same picture. Move the shape by hand:
  `setElementBounds` **with** `width: 160, height: 60` (without them it shrinks the ellipse
  to its text), then `rerouteConnectors` (lines do not follow a moved shape), then
  `checkLayout` and `checkUseCaseDiagram` (a moved actor can break C3). A later rebuild drops manual moves, so fix the model first.
- A finding that is a question about the requirements (e.g. "may report damage" = only on
  return, or any time?) goes to the user instead of being guessed.

Stop when the reviewers that ran have nothing above `nit`, or after 3 rounds.
A finding you think is wrong is still reported to the user, never silently dropped.

## 6. Report

The PNG, the final spec, the assumptions, and every remaining finding with its id (§, C,
SYN/BP/SEM/STY).
