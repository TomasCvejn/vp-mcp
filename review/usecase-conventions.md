# Use Case Diagram — Layout conventions (house style)

House rules this project's generator should follow and the reviewer should check,
**in addition to** the error catalog in `usecase.md`. These are `[Recommendation]`-level
(severity `warn`), not UML errors.

**Telling primary from secondary in the picture:** a plain line with no arrowhead at the
actor = **primary** actor (they initiate the use case); an association whose arrowhead
points **at the actor** = **secondary** actor (the actor receives / is notified).

## C1 — Each actor's connectors share one anchor point

All association lines of a single actor form **one fan aimed at a single point** (the
actor's center), not lines scattered over the figure and its name label in different
directions.
- Check: for each actor, do all its lines, extended into the figure, meet in one point?
  Visual Paradigm clips each line at the actor's bounding box, so a fan's start points
  sit a few to a few dozen pixels apart on the figure's edge; that is expected and **not**
  a violation. A line starting at the name label, or aimed away from the common point, is.
- Fix: re-anchor every connector of that actor to its center (`rerouteConnectors`).

## C2 — A secondary actor's line runs all the way to the actor

For a secondary actor (directed association, arrowhead at the actor), the connector must
be drawn **all the way to the actor**, not stopping short at the boundary or mid-canvas.
- Check: does each directed actor link reach the actor symbol?
- Fix: extend/route the connector to the actor.
- A line to a secondary actor is straight unless a straight line would run through another
  shape; then it runs level from its use case and bends once towards the actor. That is
  intended and **not** a violation; its last segment still aims at the actor's center (C1).

## C3 — Primary actors on the left, secondary actors on the right

Primary actors sit on the **left** of the system boundary; secondary actors sit on the
**right**.
- Check: are all primary actors left of the boundary and all secondary actors right?
- Fix: move secondary actors to the right column, primary actors to the left.

## C4 — A Time actor carries the «time» stereotype

An actor that stands for the passing of time (scheduled / periodic triggers, e.g. "Time")
is drawn with the **«time»** stereotype above its name.
- Check: does every Time actor show «time»?
- Fix: add the stereotype (`addStereotype(diagram, "Time", "time")`).
