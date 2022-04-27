/*
 * Copyright 2004-2021 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.opscenter.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import org.h2.message.DbException;
import org.lealone.db.session.ServerSession;

/**
 * The web session keeps all data of a user session.
 * This class is used by the H2 Console.
 */
class ServiceSession {

    private static final int MAX_HISTORY = 1000;

    /**
     * The last time this client sent a request.
     */
    long lastAccess;

    /**
     * The session attribute map.
     */
    final HashMap<String, Object> map = new HashMap<>();

    /**
     * The current locale.
     */
    Locale locale;

    /**
     * The currently executing statement.
     */
    Statement executingStatement;

    /**
     * The current updatable result set.
     */
    ResultSet result;

    private final ServiceConfig server;

    private final ArrayList<String> commandHistory;

    final ArrayList<String> columnNames = new ArrayList<>();
    final ArrayList<ArrayList<String>> rows = new ArrayList<>();
    String queryInfo;

    final ArrayList<TableInfo> tableList = new ArrayList<>();
    final ArrayList<NodeInfo> nodeList = new ArrayList<>();

    private Connection conn;
    private boolean shutdownServerOnDisconnect;
    private ServerSession serverSession;

    ServiceSession(ServiceConfig server) {
        this.server = server;
        // This must be stored in the session rather than in the server.
        // Otherwise, one client could allow
        // saving history for others (insecure).
        this.commandHistory = server.getCommandHistoryList();
    }

    void addTable(String name, String columns, int id) {
        tableList.add(new TableInfo(id, name, columns));
    }

    protected ServerSession getServerSession() {
        return serverSession;
    }

    protected void setServerSession(ServerSession serverSession) {
        this.serverSession = serverSession;
    }

    void addNode(int id, int level, int type, String icon, String text) {
        addNode(id, level, type, icon, text, null);
    }

    void addNode(int id, int level, int type, String icon, String text, String link) {
        nodeList.add(new NodeInfo(id, level, type, icon, text, link));
    }

    /**
     * Put an attribute value in the map.
     *
     * @param key the key
     * @param value the new value
     */
    void put(String key, Object value) {
        map.put(key, value);
    }

    /**
     * Get the value for the given key.
     *
     * @param key the key
     * @return the value
     */
    Object get(String key) {
        if ("sessions".equals(key)) {
            return server.getSessions();
        }
        return map.get(key);
    }

    String i18n(String key) {
        if (key.startsWith("text."))
            key = key.substring(5);
        @SuppressWarnings("unchecked")
        HashMap<String, Object> m = (HashMap<String, Object>) map.get("text");
        return m.get(key).toString();
    }

    /**
     * Remove a session attribute from the map.
     *
     * @param key the key
     * @return value that was associated with the key, or null
     */
    Object remove(String key) {
        return map.remove(key);
    }

    /**
     * Get the SQL statement from history.
     *
     * @param id the history id
     * @return the SQL statement
     */
    String getCommand(int id) {
        return commandHistory.get(id);
    }

    /**
     * Add a SQL statement to the history.
     *
     * @param sql the SQL statement
     */
    void addCommand(String sql) {
        if (sql == null) {
            return;
        }
        sql = sql.trim();
        if (sql.isEmpty()) {
            return;
        }
        if (commandHistory.size() > MAX_HISTORY) {
            commandHistory.remove(0);
        }
        int idx = commandHistory.indexOf(sql);
        if (idx >= 0) {
            commandHistory.remove(idx);
        }
        commandHistory.add(sql);
        if (server.isCommandHistoryAllowed()) {
            server.saveCommandHistoryList(commandHistory);
        }
    }

    /**
     * Get the list of SQL statements in the history.
     *
     * @return the commands
     */
    ArrayList<String> getCommandHistory() {
        return commandHistory;
    }

    /**
     * Update session meta data information and get the information in a map.
     *
     * @return a map containing the session meta data
     */
    HashMap<String, Object> getInfo() {
        HashMap<String, Object> m = new HashMap<>();
        m.putAll(map);
        m.put("lastAccess", new Timestamp(lastAccess).toString());
        try {
            m.put("url", conn == null ? "${text.admin.notConnected}" : conn.getMetaData().getURL());
            m.put("user", conn == null ? "-" : conn.getMetaData().getUserName());
            m.put("lastQuery", commandHistory.isEmpty() ? "" : commandHistory.get(0));
            m.put("executing", executingStatement == null ? "${text.admin.no}" : "${text.admin.yes}");
        } catch (SQLException e) {
            DbException.traceThrowable(e);
        }
        return m;
    }

    void setConnection(Connection conn) throws SQLException {
        this.conn = conn;
    }

    Connection getConnection() {
        return conn;
    }

    public boolean isH2() {
        return true;
    }

    /**
     * Shutdown the server when disconnecting.
     */
    void setShutdownServerOnDisconnect() {
        this.shutdownServerOnDisconnect = true;
    }

    boolean getShutdownServerOnDisconnect() {
        return shutdownServerOnDisconnect;
    }

    /**
     * Close the connection and stop the statement if one is currently
     * executing.
     */
    void close() {
        if (executingStatement != null) {
            try {
                executingStatement.cancel();
            } catch (Exception e) {
                // ignore
            }
        }
        if (conn != null) {
            try {
                conn.close();
            } catch (Exception e) {
                // ignore
            }
        }
    }

}
