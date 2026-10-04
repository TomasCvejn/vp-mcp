package com.brunnen.vp.mcp.tools;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/** Tests for the model checks behind checkUseCaseDiagram. */
public class UseCaseCheckTest {

  private static final double[] IN = {360, 100, 160, 60};

  /** Bike sharing as built: boundary 300..940, primary actors left, the gateway right. */
  private static UseCaseCheck clean() {
    UseCaseCheck c = new UseCaseCheck();
    c.boundaries.add(new Object[] {"Bike Sharing System", 300.0, 60.0, 640.0, 770.0});
    c.actors.put("Rider", Collections.emptyList());
    c.actorX.put("Rider", 220.0);
    c.actors.put("Time", Collections.singletonList("time"));
    c.actorX.put("Time", 220.0);
    c.actors.put("Payment Gateway", Collections.singletonList("system"));
    c.actorX.put("Payment Gateway", 1030.0);
    for (String uc : Arrays.asList("Return Bike", "Charge Fee", "Process Payment", "Report")) {
      c.useCases.put(uc, IN);
    }
    c.associations.add(new String[] {"Rider", "Return Bike", "false", "false"});
    c.associations.add(new String[] {"Time", "Charge Fee", "false", "false"});
    c.associations.add(new String[] {"Process Payment", "Payment Gateway", "false", "true"});
    c.includes.add(new String[] {"Return Bike", "Process Payment"});
    c.includes.add(new String[] {"Charge Fee", "Process Payment"});
    c.extendsList.add(new String[] {"Report", "Return Bike", "bike damaged"});
    return c;
  }

  @Test
  public void cleanDiagramReportsOnlyCounts() {
    assertEquals(
        Collections.singletonList("3 actors, 4 use cases, 6 relationships"), clean().run());
  }

  @Test
  public void reportsEveryModelProblem() {
    UseCaseCheck c = clean();
    c.useCases.put("Outside", new double[] {900, 100, 160, 60}); // sticks out on the right
    c.extendsList.add(new String[] {"Outside", "Charge Fee", " "});
    c.extendsList.add(new String[] {"Unused", "Rent", "ExtensionPoint"}); // VP's default name
    c.actors.put("Time", Collections.emptyList());
    c.actorX.put("Payment Gateway", 500.0); // secondary, drawn inside the boundary
    c.actors.put("Bike Sharing System", Collections.emptyList());
    c.actorX.put("Bike Sharing System", 1000.0); // primary, right of the boundary
    c.associations.add(new String[] {"Rider", "Charge Fee", "false", "true"});
    c.associations.add(new String[] {"Time", "Report", "true", "true"});
    c.includes.add(new String[] {"Report", "Lonely Include"});
    c.useCases.put("Lonely Include", IN);
    c.useCases.put("Unused", IN);
    c.useCases.put("Lonely", IN);
    c.actors.put("Idle", Collections.emptyList());

    List<String> out = c.run();

    String all = String.join("\n", out);
    assertTrue(all, out.contains("SYN1: use case 'Outside' is outside the system boundary"));
    assertTrue(
        all,
        out.contains(
            "SYN5: 'Charge Fee' has no named extension point for the extend from 'Outside'"));
    assertTrue(
        all,
        out.contains("SYN5: 'Rent' has no named extension point for the extend from 'Unused'"));
    assertTrue(all, out.contains("C4/BP3: actor 'Time' has no «time» stereotype"));
    assertTrue(
        all, out.contains("C3: secondary actor 'Payment Gateway' is not right of the boundary"));
    assertTrue(
        all, out.contains("C3: primary actor 'Bike Sharing System' is not left of the boundary"));
    assertTrue(
        all, out.contains("§1.6: actor 'Bike Sharing System' stands for the modelled system"));
    assertTrue(all, all.contains("§1.5: arrowhead at use case 'Charge Fee' on the line from"));
    assertTrue(all, out.contains("§1.5: the line between 'Time' and 'Report' has two arrowheads"));
    assertTrue(
        all,
        out.contains(
            "§1.11: 'Lonely Include' is included by one use case only; put its steps into the"
                + " base"));
    assertTrue(all, out.contains("use case 'Lonely' has no relationship"));
    assertTrue(all, out.contains("actor 'Idle' has no relationship"));
  }

  @Test
  public void checksTheBoundaryItself() {
    UseCaseCheck none = clean();
    none.boundaries.clear();
    assertTrue(none.run().contains("SYN1: no system boundary"));

    UseCaseCheck two = clean();
    two.boundaries.add(new Object[] {"Other", 0.0, 0.0, 10.0, 10.0});
    assertTrue(two.run().contains("BP2: 2 system boundaries; model one system"));

    UseCaseCheck unnamed = clean();
    unnamed.boundaries.set(0, new Object[] {" ", 300.0, 60.0, 640.0, 770.0});
    assertTrue(unnamed.run().contains("BP1: the system boundary has no name"));

    UseCaseCheck system = clean();
    system.actors.put("System", Collections.singletonList("x"));
    system.actorX.put("System", 100.0);
    system.associations.add(new String[] {"System", "Report", "false", "false"});
    assertTrue(system.run().contains("§1.6: actor 'System' stands for the modelled system"));
  }
}
