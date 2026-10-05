package com.brunnen.vp.mcp.tools;

import com.brunnen.vp.mcp.tool.OptionalParam;
import com.brunnen.vp.mcp.tool.Tool;
import com.vp.plugin.DiagramManager;
import com.vp.plugin.diagram.IDiagramElement;
import com.vp.plugin.diagram.IDiagramTypeConstants;
import com.vp.plugin.diagram.IDiagramUIModel;
import com.vp.plugin.model.IDBColumn;
import com.vp.plugin.model.IDBForeignKey;
import com.vp.plugin.model.IDBTable;
import com.vp.plugin.model.IModelElement;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** MCP tools for Visual Paradigm ERD operations. */
public class ErdMcpTools extends AbstractDiagramMcpTools {

  @Tool(
      name = "createErd",
      description = "Create a new Entity-Relationship diagram in Visual Paradigm")
  public String createErd(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            requireProject();
            DiagramManager dm = getDiagramManager();
            IDiagramUIModel diagram =
                dm.createDiagram(IDiagramTypeConstants.DIAGRAM_TYPE_ER_DIAGRAM);
            diagram.setName(diagramName);
            dm.openDiagram(diagram);
            return "Created ER diagram: " + diagramName;
          });
    } catch (Exception e) {
      return "Error creating ER diagram: " + e.getMessage();
    }
  }

  @Tool(name = "addTable", description = "Add a table/entity to an ER diagram")
  public String addTable(String diagramName, String tableName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = requireDiagram(diagramName, IDiagramUIModel.class);

            IDBTable table = getModelElementFactory().createDBTable();
            addToDiagram(diagram, table, tableName);

            return "Added table '" + tableName + "' to diagram '" + diagramName + "'";
          });
    } catch (Exception e) {
      return "Error adding table: " + e.getMessage();
    }
  }

  @Tool(name = "addColumn", description = "Add a column to a database table")
  public String addColumn(
      String tableName,
      String columnName,
      @OptionalParam String columnType,
      @OptionalParam int length,
      @OptionalParam int scale,
      @OptionalParam boolean isPrimaryKey,
      boolean isNullable) {
    try {
      return runOnEdt(
          () -> {
            IDBTable table = findModelElement(tableName, IDBTable.class, null);

            // Duplicate column guard
            Iterator<?> existingCols = table.dBColumnIterator();
            while (existingCols.hasNext()) {
              Object obj = existingCols.next();
              if (obj instanceof IDBColumn && columnName.equals(((IDBColumn) obj).getName())) {
                return "Column '" + columnName + "' already exists in table '" + tableName + "'";
              }
            }

            IDBColumn col = getModelElementFactory().createDBColumn();
            col.setName(columnName);
            if (columnType != null && !columnType.trim().isEmpty()) {
              col.setType(columnType.trim(), length, scale);
            }
            col.setPrimaryKey(isPrimaryKey);
            col.setNullable(isNullable);
            table.addDBColumn(col);
            fitShapesForModel(table);

            return "Added column '" + columnName + "' to table '" + tableName + "'";
          });
    } catch (Exception e) {
      return "Error adding column: " + e.getMessage();
    }
  }

  @Tool(name = "addForeignKey", description = "Add a foreign key relationship between two tables")
  public String addForeignKey(
      String diagramName,
      String fromTable,
      String toTable,
      @OptionalParam String fromColumn,
      String toColumn,
      @OptionalParam String relationshipName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = requireDiagram(diagramName, IDiagramUIModel.class);
            IDiagramElement fromElement = findElement(diagram, fromTable, IDBTable.class);
            IDiagramElement toElement = findElement(diagram, toTable, IDBTable.class);
            IDBTable source = (IDBTable) fromElement.getModelElement();
            IDBTable target = (IDBTable) toElement.getModelElement();

            IDBForeignKey fk = getModelElementFactory().createDBForeignKey();
            fk.setFrom(source);
            fk.setTo(target);
            if (relationshipName != null && !relationshipName.trim().isEmpty()) {
              fk.setName(relationshipName.trim());
            }
            fk.setFromMultiplicity("1");
            fk.setToMultiplicity("*");

            // Resolve column references
            if (fromColumn != null && !fromColumn.trim().isEmpty()) {
              IDBColumn fromCol = findColumnByName(source, fromColumn.trim());
              if (fromCol == null) {
                return "Column '" + fromColumn + "' not found in table '" + fromTable + "'";
              }
              fk.setIndexColumn(fromCol);
            }

            getDiagramManager().createConnector(diagram, fk, fromElement, toElement, null);

            return "Added foreign key from '" + fromTable + "' to '" + toTable + "'";
          });
    } catch (Exception e) {
      return "Error adding foreign key: " + e.getMessage();
    }
  }

  @Tool(
      name = "addTableRelationship",
      description = "Add a relationship between tables (identifying or non-identifying)")
  public String addTableRelationship(
      String diagramName,
      String fromTable,
      String toTable,
      String type,
      @OptionalParam String fromMultiplicity,
      @OptionalParam String toMultiplicity) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = requireDiagram(diagramName, IDiagramUIModel.class);
            IDiagramElement fromElement = findElement(diagram, fromTable, IDBTable.class);
            IDiagramElement toElement = findElement(diagram, toTable, IDBTable.class);
            IDBTable source = (IDBTable) fromElement.getModelElement();
            IDBTable target = (IDBTable) toElement.getModelElement();

            IDBForeignKey fk = getModelElementFactory().createDBForeignKey();
            fk.setFrom(source);
            fk.setTo(target);
            if (fromMultiplicity != null && !fromMultiplicity.trim().isEmpty()) {
              fk.setFromMultiplicity(fromMultiplicity.trim());
            }
            if (toMultiplicity != null && !toMultiplicity.trim().isEmpty()) {
              fk.setToMultiplicity(toMultiplicity.trim());
            }
            if ("identifying".equalsIgnoreCase(type)) {
              fk.setIdentifying(true);
            } else {
              fk.setIdentifying(false);
            }
            getDiagramManager().createConnector(diagram, fk, fromElement, toElement, null);

            return "Added " + type + " relationship from '" + fromTable + "' to '" + toTable + "'";
          });
    } catch (Exception e) {
      return "Error adding relationship: " + e.getMessage();
    }
  }

  @Tool(
      name = "generateDdl",
      description = "Generate CREATE TABLE DDL statements for all tables in an ER diagram")
  public String generateDdl(String diagramName) {
    try {
      return runOnEdt(
          () -> {
            IDiagramUIModel diagram = requireDiagram(diagramName, IDiagramUIModel.class);

            List<IDBTable> tables = getTablesInDiagram(diagram);
            if (tables.isEmpty()) {
              return "No tables found in diagram: " + diagramName;
            }

            StringBuilder ddl = new StringBuilder();
            for (IDBTable table : tables) {
              ddl.append(generateCreateTableSql(table)).append("\n\n");
            }
            return ddl.toString();
          });
    } catch (Exception e) {
      return "Error generating DDL: " + e.getMessage();
    }
  }

  private IDBColumn findColumnByName(IDBTable table, String columnName) {
    Iterator<?> iter = table.dBColumnIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDBColumn) {
        IDBColumn col = (IDBColumn) obj;
        if (columnName.equals(col.getName())) {
          return col;
        }
      }
    }
    return null;
  }

  // --- Helpers ---

  /**
   * Get all tables in a specific ER diagram.
   *
   * @param diagram the ER diagram
   * @return list of tables in the diagram
   */
  private static List<IDBTable> getTablesInDiagram(IDiagramUIModel diagram) {
    List<IDBTable> tables = new ArrayList<>();
    Iterator<?> iter = diagram.diagramElementIterator();
    while (iter.hasNext()) {
      Object obj = iter.next();
      if (obj instanceof IDiagramElement) {
        IModelElement model = ((IDiagramElement) obj).getModelElement();
        if (model instanceof IDBTable) {
          tables.add((IDBTable) model);
        }
      }
    }
    return tables;
  }

  /**
   * Generate CREATE TABLE DDL for a table.
   *
   * @param table the table
   * @return the DDL string
   */
  private static String generateCreateTableSql(IDBTable table) {
    StringBuilder sql = new StringBuilder();
    sql.append("CREATE TABLE ").append(table.getName()).append(" (\n");

    List<String> primaryKeys = new ArrayList<>();
    Iterator<?> colIter = table.dBColumnIterator();
    boolean first = true;
    while (colIter.hasNext()) {
      Object obj = colIter.next();
      if (obj instanceof IDBColumn) {
        IDBColumn col = (IDBColumn) obj;
        if (!first) {
          sql.append(",\n");
        }
        sql.append("  ").append(col.getName()).append(" ").append(col.getTypeInText());
        if (col.getLength() > 0) {
          sql.append("(").append(col.getLength());
          if (col.getScale() > 0) {
            sql.append(",").append(col.getScale());
          }
          sql.append(")");
        }
        if (!col.isNullable()) {
          sql.append(" NOT NULL");
        }
        if (col.isPrimaryKey()) {
          primaryKeys.add(col.getName());
        }
        first = false;
      }
    }

    if (!primaryKeys.isEmpty()) {
      sql.append(",\n  PRIMARY KEY (").append(String.join(", ", primaryKeys)).append(")");
    }

    sql.append("\n);");
    return sql.toString();
  }
}
