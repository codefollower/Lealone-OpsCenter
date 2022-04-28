/*
 * Copyright 2004-2021 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.opscenter.service;

import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;

import org.lealone.common.util.StringUtils;
import org.lealone.common.util.Utils;
import org.lealone.db.Database;
import org.lealone.db.auth.User;
import org.lealone.db.schema.Schema;
import org.lealone.db.session.ServerSession;
import org.lealone.db.table.Column;
import org.lealone.db.table.Table;
import org.lealone.db.table.TableView;

import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class DatabaseService extends Service {

    public String readAllDatabaseObjects(String jsessionid) {
        session = ServiceConfig.instance.getSession(jsessionid);

        ServerSession serverSession = session.getServerSession();
        Database db = serverSession.getDatabase();
        try {
            session.addNode(0, 0, 0, "database", db.getName());
            int treeIndex = 1;

            for (Schema schema : db.getAllSchemas()) {
                session.addNode(treeIndex, 0, 1, "folder", schema.getName());
                treeIndex++;
                treeIndex = addTablesAndViews(schema, false, treeIndex);
            }
            int i = 0;
            for (User user : db.getAllUsers()) {
                if (i == 0) {
                    session.addNode(treeIndex, 0, 1, "users", session.i18n("text.tree.users"));
                    treeIndex++;
                }
                i++;
                session.addNode(treeIndex, 1, 1, "user", user.getName());
                treeIndex++;
                if (user.isAdmin()) {
                    session.addNode(treeIndex, 2, 2, "type", session.i18n("text.tree.admin"));
                    treeIndex++;
                }
            }

            // for (int i = 0; rs.next(); i++) {
            // if (i == 0) {
            // session.addNode(treeIndex, 0, 1, "sequences", session.i18n("text.tree.sequences"));
            // treeIndex++;
            // }
            // String name = rs.getString(1);
            // String currentBase = rs.getString(2);
            // String increment = rs.getString(3);
            // session.addNode(treeIndex, 1, 1, "sequence", name);
            // treeIndex++;
            // session.addNode(treeIndex, 2, 2, "type",
            // session.i18n("text.tree.current") + ": " + currentBase);
            // treeIndex++;
            // if (!"1".equals(increment)) {
            // session.addNode(treeIndex, 2, 2, "type",
            // session.i18n("text.tree.increment") + ": " + increment);
            // treeIndex++;
            // }
            // }
            String version = Utils.getReleaseVersionString();
            session.addNode(treeIndex, 0, 0, "info", version);
        } catch (Exception e) {
            session.put("error", getStackTrace(0, e, session.isH2()));
        }
        JsonObject json = new JsonObject();
        json.put("tables", new JsonArray(session.tableList));
        json.put("nodes", new JsonArray(session.nodeList));
        String str = json.encode();
        session.tableList.clear();
        session.nodeList.clear();
        return str;
    }

    private int addTablesAndViews(Schema schema, boolean mainSchema, int treeIndex) throws SQLException {
        if (schema == null) {
            return treeIndex;
        }
        int level = mainSchema ? 0 : 1;
        boolean showColumns = mainSchema;
        String indentation = ", " + level + ", " + (showColumns ? "1" : "2") + ", ";
        String indentNode = ", " + (level + 1) + ", 2, ";
        ArrayList<Table> tables = schema.getAllTablesAndViews();
        if (tables == null) {
            return treeIndex;
        }
        for (Table table : tables) {
            if (table instanceof TableView) {
                continue;
            }
            treeIndex = addTableOrView(schema, mainSchema, treeIndex, showColumns, indentation, table, false,
                    indentNode);
        }
        for (Table table : tables) {
            if (!(table instanceof TableView)) {
                continue;
            }
            treeIndex = addTableOrView(schema, mainSchema, treeIndex, showColumns, indentation, table, true,
                    indentNode);
        }
        return treeIndex;
    }

    private int addTableOrView(Schema schema, boolean mainSchema, int treeIndex, boolean showColumns,
            String indentation, Table table, boolean isView, String indentNode) throws SQLException {
        int tableId = treeIndex;
        String tab = table.getSQL();
        if (!mainSchema) {
            // tab = schema.getSQL() + '.' + tab;
        }
        tab = escapeIdentifier(tab);
        String[] a = indentation.split(",");
        session.addNode(treeIndex, Integer.parseInt(a[1].trim()), Integer.parseInt(a[2].trim()),
                isView ? "view" : "table", table.getName(), tab);
        treeIndex++;
        if (showColumns) {
            StringBuilder columnsBuilder = new StringBuilder();
            treeIndex = addColumns(mainSchema, table, treeIndex, true, columnsBuilder);
            // treeIndex = addIndexes(mainSchema, meta, table.getName(), schema.name, treeIndex);
            session.addTable(table.getName(), columnsBuilder.toString(), tableId);
        }
        return treeIndex;
    }

    private int addColumns(boolean mainSchema, Table table, int treeIndex, boolean showColumnTypes,
            StringBuilder columnsBuilder) {
        Column[] columns = table.getColumns();
        for (int i = 0; columns != null && i < columns.length; i++) {
            Column column = columns[i];
            if (columnsBuilder.length() > 0) {
                columnsBuilder.append(' ');
            }
            columnsBuilder.append(column.getName());
            String col = escapeIdentifier(column.getName());
            String level = mainSchema ? ", 1, 1" : ", 2, 2";
            String[] a = level.split(",");
            session.addNode(treeIndex, Integer.parseInt(a[1].trim()), Integer.parseInt(a[2].trim()), "column",
                    column.getName(), col);
            treeIndex++;
            if (mainSchema && showColumnTypes) {
                session.addNode(treeIndex, 2, 2, "type", column.getType() + "");
                treeIndex++;
            }
        }
        return treeIndex;
    }

    /**
     * This class represents index information for the GUI.
     */
    private static class IndexInfo {

        /**
         * The index name.
         */
        String name;

        /**
         * The index type name.
         */
        String type;

        /**
         * The indexed columns.
         */
        String columns;
    }

    @SuppressWarnings("unused")
    private int addIndexes(boolean mainSchema, DatabaseMetaData meta, String table, String schema, int treeIndex)
            throws SQLException {
        ResultSet rs;
        try {
            rs = meta.getIndexInfo(null, schema, table, false, true);
        } catch (SQLException e) {
            // SQLite
            return treeIndex;
        }
        HashMap<String, IndexInfo> indexMap = new HashMap<>();
        while (rs.next()) {
            String name = rs.getString("INDEX_NAME");
            IndexInfo info = indexMap.get(name);
            if (info == null) {
                int t = rs.getInt("TYPE");
                String type;
                if (t == DatabaseMetaData.tableIndexClustered) {
                    type = "";
                } else if (t == DatabaseMetaData.tableIndexHashed) {
                    type = " (" + session.i18n("text.tree.hashed") + ")";
                } else if (t == DatabaseMetaData.tableIndexOther) {
                    type = "";
                } else {
                    type = null;
                }
                if (name != null && type != null) {
                    info = new IndexInfo();
                    info.name = name;
                    type = (rs.getBoolean("NON_UNIQUE") ? session.i18n("text.tree.nonUnique")
                            : session.i18n("text.tree.unique")) + type;
                    info.type = type;
                    info.columns = rs.getString("COLUMN_NAME");
                    indexMap.put(name, info);
                }
            } else {
                info.columns += ", " + rs.getString("COLUMN_NAME");
            }
        }
        rs.close();
        if (indexMap.size() > 0) {
            String level = mainSchema ? ", 1, 1" : ", 2, 1";
            String levelIndex = mainSchema ? ", 2, 1" : ", 3, 1";
            String levelColumnType = mainSchema ? ", 3, 2" : ", 4, 2";
            String[] a = level.split(",");
            session.addNode(treeIndex, Integer.parseInt(a[1].trim()), Integer.parseInt(a[2].trim()), "index_az",
                    session.i18n("text.tree.indexes"));
            treeIndex++;
            String[] a1 = levelIndex.split(",");
            String[] a2 = levelColumnType.split(",");
            for (IndexInfo info : indexMap.values()) {
                session.addNode(treeIndex, Integer.parseInt(a1[1].trim()), Integer.parseInt(a1[2].trim()), "index",
                        info.name);
                treeIndex++;
                session.addNode(treeIndex, Integer.parseInt(a2[1].trim()), Integer.parseInt(a2[2].trim()), "type",
                        info.type);
                treeIndex++;
                session.addNode(treeIndex, Integer.parseInt(a2[1].trim()), Integer.parseInt(a2[2].trim()), "type",
                        info.columns);
                treeIndex++;
            }
        }
        return treeIndex;
    }

    private static String escapeIdentifier(String name) {
        return StringUtils.urlEncode(escapeJavaScript(name)).replace('+', ' ');
    }

    /**
     * Escape text as a the javascript string.
     *
     * @param s the text
     * @return the javascript string
     */
    private static String escapeJavaScript(String s) {
        if (s == null) {
            return null;
        }
        int length = s.length();
        if (length == 0) {
            return "";
        }
        StringBuilder buff = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            char c = s.charAt(i);
            switch (c) {
            case '"':
                buff.append("\\\"");
                break;
            case '\'':
                buff.append("\\'");
                break;
            case '\\':
                buff.append("\\\\");
                break;
            case '\n':
                buff.append("\\n");
                break;
            case '\r':
                buff.append("\\r");
                break;
            case '\t':
                buff.append("\\t");
                break;
            default:
                buff.append(c);
                break;
            }
        }
        return buff.toString();
    }
}
