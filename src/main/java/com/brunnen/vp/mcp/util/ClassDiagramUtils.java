package com.brunnen.vp.mcp.util;

import com.vp.plugin.diagram.IClassDiagramUIModel;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.model.IClass;
import com.vp.plugin.model.IModelElement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Utility class for class diagram operations. */
public final class ClassDiagramUtils {

  private ClassDiagramUtils() {}

  /**
   * Get all classes in a specific class diagram.
   *
   * @param diagram the class diagram
   * @return list of classes in the diagram
   */
  public static List<IClass> getClassesInDiagram(IClassDiagramUIModel diagram) {
    List<IClass> classes = new ArrayList<>();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IModelElement model = ((IDiagramElement) obj).getModelElement();
        if (model instanceof IClass) {
          classes.add((IClass) model);
        }
      }
    }
    return classes;
  }
}
