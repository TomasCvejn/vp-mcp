# Use Case Diagram — Layout conventions (house style)

House rules this project's generator should follow and the reviewer should check,
**in addition to** the error catalog in `usecase.md`. These are `[Recommendation]`-level
(severity `warn`), not UML errors.

**Telling primary from secondary in the picture:** a plain line with no arrowhead at the
actor = **primary** actor (they initiate the use case); an association whose arrowhead
points **at the actor** = **secondary** actor (the actor receives / is notified).

## C1 — Each actor's connectors share one anchor point

All association lines of a single actor emanate from **one shared point** at the actor
symbol (a clean fan out of a single anchor), not from several scattered points along the
figure.
- Check: for each actor, do all its lines start at the same point on the actor?
- Fix: re-anchor every connector of that actor to the one point.

## C2 — A secondary actor's line runs all the way to the actor

For a secondary actor (directed association, arrowhead at the actor), the connector must
be drawn **all the way to the actor**, not stopping short at the boundary or mid-canvas.
- Check: does each directed actor link reach the actor symbol?
- Fix: extend/route the connector to the actor.

## C3 — Primary actors on the left, secondary actors on the right

Primary actors sit on the **left** of the system boundary; secondary actors sit on the
**right**.
- Check: are all primary actors left of the boundary and all secondary actors right?
- Fix: move secondary actors to the right column, primary actors to the left.
