/*
 * Copyright 2004-2021 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.lealone.opscenter.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import org.h2.engine.Constants;
import org.h2.engine.SysProperties;
import org.h2.message.DbException;
import org.h2.security.SHA256;
import org.h2.server.ShutdownHandler;
import org.h2.store.fs.FileUtils;
import org.h2.util.MathUtils;
import org.h2.util.SortedProperties;
import org.h2.util.StringUtils;
import org.h2.util.Tool;
import org.h2.util.Utils;

/**
 * The web server is a simple standalone HTTP server that implements the H2
 * Console application. It is not optimized for performance.
 */
public class ServiceConfig {

    public static ServiceConfig instance = new ServiceConfig();

    static final String[][] LANGUAGES = { { "cs", "\u010ce\u0161tina" }, { "de", "Deutsch" }, { "en", "English" },
            { "es", "Espa\u00f1ol" }, { "fr", "Fran\u00e7ais" }, { "hu", "Magyar" }, { "ko", "\ud55c\uad6d\uc5b4" },
            { "in", "Indonesia" }, { "it", "Italiano" }, { "ja", "\u65e5\u672c\u8a9e" }, { "nl", "Nederlands" },
            { "pl", "Polski" }, { "pt_BR", "Portugu\u00eas (Brasil)" }, { "pt_PT", "Portugu\u00eas (Europeu)" },
            { "ru", "\u0440\u0443\u0441\u0441\u043a\u0438\u0439" }, { "sk", "Slovensky" }, { "tr", "T\u00fcrk\u00e7e" },
            { "uk", "\u0423\u043A\u0440\u0430\u0457\u043D\u0441\u044C\u043A\u0430" },
            { "zh_CN", "\u4e2d\u6587 (\u7b80\u4f53)" }, { "zh_TW", "\u4e2d\u6587 (\u7e41\u9ad4)" }, };

    private static final String COMMAND_HISTORY = "commandHistory";

    private static final String DEFAULT_LANGUAGE = "en";

    private static final String[] GENERIC = { //
            "Generic Lealone (Server)|org.lealone.client.jdbc.JdbcDriver|" //
                    + "jdbc:lealone:tcp://127.0.0.1:9210/lealone|root", //
            // this will be listed on top for new installations
            "Generic Lealone (Embedded)|org.lealone.client.jdbc.JdbcDriver|" //
                    + "jdbc:lealone:embed:lealone|root", };

    private static int ticker;

    /**
     * The session timeout (the default is 30 minutes).
     */
    private static final long SESSION_TIMEOUT = SysProperties.CONSOLE_TIMEOUT;

    private int port;
    private boolean allowOthers;
    private boolean ssl;
    private byte[] adminPassword;
    private final HashMap<String, ConnectionInfo> connInfoMap = new HashMap<>();

    private long lastTimeoutCheck;
    private final HashMap<String, ServiceSession> sessions = new HashMap<>();
    private final HashSet<String> languages = new HashSet<>();
    private String startDateTime;
    private ShutdownHandler shutdownHandler;
    private String key;
    private boolean trace;
    private TranslateThread translateThread;
    private boolean allowChunked = false;
    private String serverPropertiesDir = Constants.SERVER_PROPERTIES_DIR;
    // null means the history is not allowed to be stored
    private String commandHistoryString;

    /**
     * Read the given file from the file system or from the resources.
     *
     * @param file the file name
     * @return the data
     */
    byte[] getFile(String file) throws IOException {
        trace("getFile <" + file + ">");
        byte[] data = Utils.getResource("/org/h2/server/web/res/" + file);
        if (data == null) {
            trace(" null");
        } else {
            trace(" size=" + data.length);
        }
        return data;
    }

    private static String generateSessionId() {
        byte[] buff = MathUtils.secureRandomBytes(16);
        return StringUtils.convertBytesToHex(buff);
    }

    /**
     * Get the web session object for the given session id.
     *
     * @param sessionId the session id
     * @return the web session or null
     */
    ServiceSession getSession(String sessionId) {
        long now = System.currentTimeMillis();
        if (lastTimeoutCheck + SESSION_TIMEOUT < now) {
            for (String id : new ArrayList<>(sessions.keySet())) {
                ServiceSession session = sessions.get(id);
                if (session.lastAccess + SESSION_TIMEOUT < now) {
                    trace("timeout for " + id);
                    sessions.remove(id);
                }
            }
            lastTimeoutCheck = now;
        }
        ServiceSession session = sessions.get(sessionId);
        if (session != null) {
            session.lastAccess = System.currentTimeMillis();
        }
        return session;
    }

    /**
     * Create a new web session id and object.
     *
     * @param hostAddr the host address
     * @return the web session object
     */
    ServiceSession createNewSession(String hostAddr) {
        String newId;
        do {
            newId = generateSessionId();
        } while (sessions.get(newId) != null);
        ServiceSession session = new ServiceSession(this);
        session.lastAccess = System.currentTimeMillis();
        session.put("sessionId", newId);
        session.put("ip", hostAddr);
        session.put("language", DEFAULT_LANGUAGE);
        session.put("frame-border", "0");
        session.put("frameset-border", "4");
        sessions.put(newId, session);
        // always read the english translation,
        // so that untranslated text appears at least in english
        readTranslations(session, DEFAULT_LANGUAGE);
        return getSession(newId);
    }

    ServiceSession removeSession(String sessionId) {
        return sessions.remove(sessionId);
    }

    String getStartDateTime() {
        if (startDateTime == null) {
            startDateTime = DateTimeFormatter.ofPattern("EEE, d MMM yyyy HH:mm:ss z", Locale.ENGLISH)
                    .format(ZonedDateTime.now(ZoneId.of("UTC")));
        }
        return startDateTime;
    }

    /**
     * Returns the key for privileged connections.
     *
     * @return key key, or null
     */
    String getKey() {
        return key;
    }

    /**
     * Sets the key for privileged connections.
     *
     * @param key key, or null
     */
    public void setKey(String key) {
        if (!allowOthers) {
            this.key = key;
        }
    }

    public void init(String... args) {
        // set the serverPropertiesDir, because it's used in loadProperties()
        for (int i = 0; args != null && i < args.length; i++) {
            if ("-properties".equals(args[i])) {
                serverPropertiesDir = args[++i];
            }
        }
        Properties prop = loadProperties();
        port = SortedProperties.getIntProperty(prop, "webPort", Constants.DEFAULT_HTTP_PORT);
        ssl = SortedProperties.getBooleanProperty(prop, "webSSL", false);
        allowOthers = SortedProperties.getBooleanProperty(prop, "webAllowOthers", false);
        setAdminPassword(SortedProperties.getStringProperty(prop, "webAdminPassword", null));
        commandHistoryString = prop.getProperty(COMMAND_HISTORY);
        for (int i = 0; args != null && i < args.length; i++) {
            String a = args[i];
            if (Tool.isOption(a, "-webPort")) {
                port = Integer.decode(args[++i]);
            } else if (Tool.isOption(a, "-webSSL")) {
                ssl = true;
            } else if (Tool.isOption(a, "-webAllowOthers")) {
                allowOthers = true;
            } else if (Tool.isOption(a, "-baseDir")) {
                String baseDir = args[++i];
                SysProperties.setBaseDir(baseDir);
            } else if (Tool.isOption(a, "-webAdminPassword")) {
                setAdminPassword(args[++i]);
            } else if (Tool.isOption(a, "-properties")) {
                // already set
                i++;
            } else if (Tool.isOption(a, "-trace")) {
                trace = true;
            }
        }
        // if (driverList != null) {
        // try {
        // String[] drivers =
        // StringUtils.arraySplit(driverList, ',', false);
        // URL[] urls = new URL[drivers.length];
        // for(int i=0; i<drivers.length; i++) {
        // urls[i] = new URL(drivers[i]);
        // }
        // urlClassLoader = URLClassLoader.newInstance(urls);
        // } catch (MalformedURLException e) {
        // TraceSystem.traceThrowable(e);
        // }
        // }
        for (String[] lang : LANGUAGES) {
            languages.add(lang[0]);
        }
        if (allowOthers) {
            key = null;
        }
    }

    /**
     * Write trace information if trace is enabled.
     *
     * @param s the message to write
     */
    void trace(String s) {
        if (trace) {
            System.out.println(s);
        }
    }

    /**
     * Write the stack trace if trace is enabled.
     *
     * @param e the exception
     */
    void traceError(Throwable e) {
        if (trace) {
            e.printStackTrace();
        }
    }

    /**
     * Check if this language is supported / translated.
     *
     * @param language the language
     * @return true if a translation is available
     */
    public boolean supportsLanguage(String language) {
        return languages.contains(language);
    }

    public Locale getLocale(String token) {
        if (ServiceConfig.instance.supportsLanguage(token)) {
            Locale locale;
            int dash = token.indexOf('_');
            if (dash >= 0) {
                String language = token.substring(0, dash);
                String country = token.substring(dash + 1);
                locale = new Locale(language, country);
            } else {
                locale = new Locale(token, "");
            }
            return locale;
        }
        return null;
    }

    /**
     * Read the translation for this language and save them in the 'text'
     * property of this session.
     *
     * @param session the session
     * @param language the language
     */
    void readTranslations(ServiceSession session, String language) {
        Properties text = new Properties();
        try {
            trace("translation: " + language);
            byte[] trans = getFile("_text_" + language + ".prop");
            trace("  " + new String(trans));
            text = SortedProperties.fromLines(new String(trans, StandardCharsets.UTF_8));
            // remove starting # (if not translated yet)
            for (Entry<Object, Object> entry : text.entrySet()) {
                String value = (String) entry.getValue();
                if (value.startsWith("#")) {
                    entry.setValue(value.substring(1));
                }
            }
        } catch (IOException e) {
            DbException.traceThrowable(e);
        }
        session.put("text", new HashMap<>(text));
    }

    ArrayList<HashMap<String, Object>> getSessions() {
        ArrayList<HashMap<String, Object>> list = new ArrayList<>(sessions.size());
        for (ServiceSession s : sessions.values()) {
            list.add(s.getInfo());
        }
        return list;
    }

    void setAllowOthers(boolean b) {
        if (b) {
            key = null;
        }
        allowOthers = b;
    }

    public boolean getAllowOthers() {
        return allowOthers;
    }

    void setSSL(boolean b) {
        ssl = b;
    }

    void setPort(int port) {
        this.port = port;
    }

    boolean getSSL() {
        return ssl;
    }

    public int getPort() {
        return port;
    }

    public boolean isCommandHistoryAllowed() {
        return commandHistoryString != null;
    }

    public void setCommandHistoryAllowed(boolean allowed) {
        if (allowed) {
            if (commandHistoryString == null) {
                commandHistoryString = "";
            }
        } else {
            commandHistoryString = null;
        }
    }

    public ArrayList<String> getCommandHistoryList() {
        ArrayList<String> result = new ArrayList<>();
        if (commandHistoryString == null) {
            return result;
        }

        // Split the commandHistoryString on non-escaped semicolons
        // and unescape it.
        StringBuilder sb = new StringBuilder();
        for (int end = 0;; end++) {
            if (end == commandHistoryString.length() || commandHistoryString.charAt(end) == ';') {
                if (sb.length() > 0) {
                    result.add(sb.toString());
                    sb.delete(0, sb.length());
                }
                if (end == commandHistoryString.length()) {
                    break;
                }
            } else if (commandHistoryString.charAt(end) == '\\' && end < commandHistoryString.length() - 1) {
                sb.append(commandHistoryString.charAt(++end));
            } else {
                sb.append(commandHistoryString.charAt(end));
            }
        }
        return result;
    }

    /**
     * Save the command history to the properties file.
     *
     * @param commandHistory the history
     */
    public void saveCommandHistoryList(ArrayList<String> commandHistory) {
        StringBuilder sb = new StringBuilder();
        for (String s : commandHistory) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            sb.append(s.replace("\\", "\\\\").replace(";", "\\;"));
        }
        commandHistoryString = sb.toString();
        saveProperties(null);
    }

    /**
     * Get the connection information for this setting.
     *
     * @param name the setting name
     * @return the connection information
     */
    ConnectionInfo getSetting(String name) {
        return connInfoMap.get(name);
    }

    /**
     * Update a connection information setting.
     *
     * @param info the connection information
     */
    void updateSetting(ConnectionInfo info) {
        connInfoMap.put(info.name, info);
        info.lastAccess = ticker++;
    }

    /**
     * Remove a connection information setting from the list
     *
     * @param name the setting to remove
     */
    void removeSetting(String name) {
        connInfoMap.remove(name);
    }

    private Properties loadProperties() {
        try {
            if ("null".equals(serverPropertiesDir)) {
                return new Properties();
            }
            return SortedProperties.loadProperties(serverPropertiesDir + "/" + Constants.SERVER_PROPERTIES_NAME);
        } catch (Exception e) {
            DbException.traceThrowable(e);
            return new Properties();
        }
    }

    /**
     * Get the list of connection information setting names.
     *
     * @return the connection info names
     */
    String[] getSettingNames() {
        ArrayList<ConnectionInfo> list = getSettings();
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) {
            names[i] = list.get(i).name;
        }
        return names;
    }

    /**
     * Get the list of connection info objects.
     *
     * @return the list
     */
    synchronized ArrayList<ConnectionInfo> getSettings() {
        ArrayList<ConnectionInfo> settings = new ArrayList<>();
        if (connInfoMap.isEmpty()) {
            Properties prop = loadProperties();
            if (prop.isEmpty()) {
                for (String gen : GENERIC) {
                    ConnectionInfo info = new ConnectionInfo(gen);
                    settings.add(info);
                    updateSetting(info);
                }
            } else {
                for (int i = 0;; i++) {
                    String data = prop.getProperty(Integer.toString(i));
                    if (data == null) {
                        break;
                    }
                    ConnectionInfo info = new ConnectionInfo(data);
                    settings.add(info);
                    updateSetting(info);
                }
            }
        } else {
            settings.addAll(connInfoMap.values());
        }
        Collections.sort(settings);
        return settings;
    }

    /**
     * Save the settings to the properties file.
     *
     * @param prop null or the properties webPort, webAllowOthers, and webSSL
     */
    synchronized void saveProperties(Properties prop) {
        try {
            if (prop == null) {
                Properties old = loadProperties();
                prop = new SortedProperties();
                prop.setProperty("webPort", Integer.toString(SortedProperties.getIntProperty(old, "webPort", port)));
                prop.setProperty("webAllowOthers",
                        Boolean.toString(SortedProperties.getBooleanProperty(old, "webAllowOthers", allowOthers)));
                prop.setProperty("webSSL", Boolean.toString(SortedProperties.getBooleanProperty(old, "webSSL", ssl)));
                if (adminPassword != null) {
                    prop.setProperty("webAdminPassword", StringUtils.convertBytesToHex(adminPassword));
                }
                if (commandHistoryString != null) {
                    prop.setProperty(COMMAND_HISTORY, commandHistoryString);
                }
            }
            ArrayList<ConnectionInfo> settings = getSettings();
            int len = settings.size();
            for (int i = 0; i < len; i++) {
                ConnectionInfo info = settings.get(i);
                if (info != null) {
                    prop.setProperty(Integer.toString(len - i - 1), info.getString());
                }
            }
            if (!"null".equals(serverPropertiesDir)) {
                OutputStream out = FileUtils
                        .newOutputStream(serverPropertiesDir + "/" + Constants.SERVER_PROPERTIES_NAME, false);
                prop.store(out, "H2 Server Properties");
                out.close();
            }
        } catch (Exception e) {
            DbException.traceThrowable(e);
        }
    }

    /**
     * Shut down the web server.
     */
    void shutdown() {
        if (shutdownHandler != null) {
            shutdownHandler.shutdown();
        }
    }

    public void setShutdownHandler(ShutdownHandler shutdownHandler) {
        this.shutdownHandler = shutdownHandler;
    }

    /**
     * The translate thread reads and writes the file translation.properties
     * once a second.
     */
    private class TranslateThread extends Thread {

        private final Path file = Paths.get("translation.properties");
        private final Map<Object, Object> translation;
        private volatile boolean stopNow;

        TranslateThread(Map<Object, Object> translation) {
            this.translation = translation;
        }

        public String getFileName() {
            return file.toAbsolutePath().toString();
        }

        public void stopNow() {
            this.stopNow = true;
            try {
                join();
            } catch (InterruptedException e) {
                // ignore
            }
        }

        @Override
        public void run() {
            while (!stopNow) {
                try {
                    SortedProperties sp = new SortedProperties();
                    if (Files.exists(file)) {
                        InputStream in = Files.newInputStream(file);
                        sp.load(in);
                        translation.putAll(sp);
                    } else {
                        OutputStream out = Files.newOutputStream(file);
                        sp.putAll(translation);
                        sp.store(out, "Translation");
                    }
                    Thread.sleep(1000);
                } catch (Exception e) {
                    traceError(e);
                }
            }
        }

    }

    /**
     * Start the translation thread that reads the file once a second.
     *
     * @param translation the translation map
     * @return the name of the file to translate
     */
    String startTranslate(Map<Object, Object> translation) {
        if (translateThread != null) {
            translateThread.stopNow();
        }
        translateThread = new TranslateThread(translation);
        translateThread.setDaemon(true);
        translateThread.start();
        return translateThread.getFileName();
    }

    void setAllowChunked(boolean allowChunked) {
        this.allowChunked = allowChunked;
    }

    boolean getAllowChunked() {
        return allowChunked;
    }

    byte[] getAdminPassword() {
        return adminPassword;
    }

    void setAdminPassword(String password) {
        if (password == null || password.isEmpty()) {
            adminPassword = null;
            return;
        }
        if (password.length() == 128) {
            try {
                adminPassword = StringUtils.convertHexToBytes(password);
                return;
            } catch (Exception ex) {
            }
        }
        byte[] salt = MathUtils.secureRandomBytes(32);
        byte[] hash = SHA256.getHashWithSalt(password.getBytes(StandardCharsets.UTF_8), salt);
        byte[] total = Arrays.copyOf(salt, 64);
        System.arraycopy(hash, 0, total, 32, 32);
        adminPassword = total;
    }

    /**
     * Check the admin password.
     *
     * @param password the password to test
     * @return true if admin password not configure, or admin password correct
     */
    boolean checkAdminPassword(String password) {
        if (adminPassword == null) {
            return false;
        }
        byte[] salt = Arrays.copyOf(adminPassword, 32);
        byte[] hash = new byte[32];
        System.arraycopy(adminPassword, 32, hash, 0, 32);
        return Utils.compareSecure(hash, SHA256.getHashWithSalt(password.getBytes(StandardCharsets.UTF_8), salt));
    }

}
