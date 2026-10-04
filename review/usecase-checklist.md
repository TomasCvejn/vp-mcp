# Use Case Diagram — Checklist

A checklist for a finished use case diagram, checked **in addition to** the error catalog
in `usecase.md` and the house style in `usecase-conventions.md`. Cite an item by its id.
Severity: Syntax and Semantics items are `blocker`, Best practices `warn`, Style `nit`
(a Style item `checkLayout` reports, e.g. crossing lines, is `warn`).

## Syntax

- **SYN1** All use cases are encapsulated inside the system boundary
- **SYN2** All «include»/«extend» relationship arrows are dashed
- **SYN3** «include» relationships have arrows going from the main activity to the sub-activity
- **SYN4** «extend» relationships have arrows going from the sub-activity to the main activity
- **SYN5** Main use cases of «extend» relationships have appropriately-named extension points

## Best practices

- **BP1** The system is named
- **BP2** Only one system is modelled
- **BP3** Time-dependent activities are performed by a special «Time» actor
  - **BP3a** The information about the time is denoted on the relationship or via the
    Actor's naming
- **BP4** «extend»ed use cases are complete without their extensions

## Semantics

- **SEM1** One-directional interaction between an actor and a use case is denoted by an
  arrow (→)
- **SEM2** All activities are performable _in the system_
- **SEM3** Actor generalisation signifies an 'is-a' relationship -- no activities are
  propagated into inappropriate contexts
- **SEM4** Use case names include verbs (and represent actions)
- **SEM5** External system actors are denoted by a «System» tag

## Style

- **STY1** The diagram looks tidy and is readable
- **STY2** Naming conventions are consistent
- **STY3** Lines do not cross each other if it can be avoided
