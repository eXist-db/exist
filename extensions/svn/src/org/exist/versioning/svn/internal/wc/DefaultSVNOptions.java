/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn.internal.wc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.Locale;
import java.util.Map;
import java.util.StringTokenizer;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.wc.ISVNConflictHandler;
import org.exist.versioning.svn.wc.ISVNMerger;
import org.exist.versioning.svn.wc.ISVNMergerFactory;
import org.exist.versioning.svn.wc.ISVNOptions;
import org.exist.versioning.svn.wc.SVNWCUtil;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.svn.ISVNConnector;
import org.tmatesoft.svn.core.internal.io.svn.SVNTunnelConnector;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;

/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class DefaultSVNOptions implements ISVNOptions, ISVNMergerFactory {

    private static final String MISCELLANY_GROUP = "miscellany";
    private static final String AUTH_GROUP = "auth";
    private static final String AUTOPROPS_GROUP = "auto-props";
    private static final String SVNKIT_GROUP = "svnkit";
    private static final String OLD_SVNKIT_GROUP = "javasvn";
    private static final String HELPERS_GROUP = "helpers";
    
    private static final String USE_COMMIT_TIMES = "use-commit-times";
    private static final String GLOBAL_IGNORES = "global-ignores";
    private static final String ENABLE_AUTO_PROPS = "enable-auto-props";
    private static final String STORE_AUTH_CREDS = "store-auth-creds";
    private static final String KEYWORD_TIMEZONE = "keyword_timezone";
    private static final String KEYWORD_LOCALE = "keyword_locale";
    private static final String EDITOR_CMD = "editor-cmd";
    private static final String DIFF_CMD = "diff-cmd";
    private static final String MERGE_TOOL_CMD = "merge-tool-cmd";
    private static final String NO_UNLOCK = "no-unlock";
    private static final String PRESERVED_CONFLICT_FILE_EXTENSIONS = "preserved-conflict-file-exts";
    private static final String INTERACTIVE_COFLICTS = "interactive-conflicts";
    private static final String MIME_TYPES_FILE = "mime-types-file";
    private static final String DEFAULT_IGNORES = "*.o *.lo *.la #*# .*.rej *.rej .*~ *~ .#* .DS_Store";
    private static final String YES = "yes";
    private static final String NO = "no";
    
    private static final String DEFAULT_LOCALE = Locale.getDefault().toString();
    private static final String DEFAULT_TIMEZONE = TimeZone.getDefault().getID();

    private boolean myIsReadonly;
    private File myConfigDirectory;
    private SVNCompositeConfigFile myConfigFile;
    private ISVNMergerFactory myMergerFactory;
    private ISVNConflictHandler myConflictResolver;
    
    private String myKeywordLocale = DEFAULT_LOCALE; 
    private String myKeywordTimezone = DEFAULT_TIMEZONE;
    private SimpleDateFormat myKeywordDateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss' 'ZZZZ' ('E', 'dd' 'MMM' 'yyyy')'");
    private Map myConfigOptions;
    
    public DefaultSVNOptions() {
        this(null, true);
    }

    public DefaultSVNOptions(File directory, boolean readOnly) {
        myConfigDirectory = directory == null ? getDefaultConfigDir() : directory;
        myIsReadonly = readOnly;
    }

    public boolean isUseCommitTimes() {
        String value = getConfigFile().getPropertyValue(MISCELLANY_GROUP, USE_COMMIT_TIMES);
        return getBooleanValue(value, false);
    }

    public void setInMemoryConfigOptions(Map configOptions) {
        myConfigOptions = configOptions;
        if (myConfigFile != null) {
            myConfigFile.setGroupsToOptions(myConfigOptions);
        }
    }
    
    /**
     * Enables or disables the commit-times option.
     *
     * <p>
     * The commit-times option makes checkout/update/switch/revert operations put
     * last-committed timestamps on every file they touch.
     *
     * <p>
     * This option corresponds to
     * the <i>'use-commit-times'</i> option that can be found in the
     * SVN's <i>config</i> file under the <i>[miscellany]</i> section.
     *
     * @param useCommitTimes  <span class="javakeyword">true</span> to
     *                        enable commit-times, <span class="javakeyword">false</span>
     *                        to disable
     * @see                   #isUseCommitTimes()
     */
    public void setUseCommitTimes(boolean useCommitTimes) {
        getConfigFile().setPropertyValue(MISCELLANY_GROUP, USE_COMMIT_TIMES, useCommitTimes ? YES : NO, !myIsReadonly);
    }

    /**
     * Determines if the autoproperties option is enabled.
     *
     * <p>
     * Autoproperties are the properties that are automatically set
     * on files when they are added or imported.
     *
     * <p>
     * This option corresponds to the <i>'enable-auto-props'</i> option
     * that can be found in the SVN's <i>config</i> file under the
     * <i>[miscellany]</i> section.
     *
     * @return  <span class="javakeyword">true</span> if autoproperties
     *          are enabled, otherwise <span class="javakeyword">false</span>
     */
    private boolean isUseAutoProperties() {
        String value = getConfigFile().getPropertyValue(MISCELLANY_GROUP, ENABLE_AUTO_PROPS);
        return getBooleanValue(value, false);
    }

    /**
     * Enables or disables the autoproperties option.
     *
     * <p>
     * Autoproperties are the properties that are automatically set
     * on files when they are added or imported.
     *
     * <p>
     * This option corresponds to the <i>'enable-auto-props'</i> option
     * that can be found in the SVN's <i>config</i> file under the
     * <i>[miscellany]</i> section.
     *
     * @param useAutoProperties  <span class="javakeyword">true</span> to
     *                           enable autoproperties, <span class="javakeyword">false</span>
     *                           to disable
     * @see                      #isUseAutoProperties()
     */
    public void setUseAutoProperties(boolean useAutoProperties) {
        getConfigFile().setPropertyValue(MISCELLANY_GROUP, ENABLE_AUTO_PROPS, useAutoProperties ? YES : NO, !myIsReadonly);
    }
    
    /**
     * Determines if the authentication storage is enabled.
     *
     * <p>
     * The auth storage is used for disk-caching of all
     * authentication information: usernames, passwords, server certificates,
     * and any other types of cacheable credentials.
     *
     * <p>
     * This option corresponds to the
     * <i>'store-auth-creds'</i> option that can be found
     * in the SVN's <i>config</i> file under the <i>[auth]</i> section.
     *
     * @return  <span class="javakeyword">true</span> if auth storage
     *          is enabled, otherwise <span class="javakeyword">false</span>
     */
    public boolean isAuthStorageEnabled() {
        String value = getConfigFile().getPropertyValue(AUTH_GROUP, STORE_AUTH_CREDS);
        return getBooleanValue(value, true);
    }

    public boolean isKeepLocks() {
        String value = getConfigFile().getPropertyValue(MISCELLANY_GROUP, NO_UNLOCK);
        return getBooleanValue(value, false);
    }

    /**
     * Enables or disables the authentication storage.
     *
     * <p>
     * The auth storage is used for disk-caching of all
     * authentication information: usernames, passwords, server certificates,
     * and any other types of cacheable credentials.
     *
     * <p>
     * This option corresponds to the
     * <i>'store-auth-creds'</i> option that can be found
     * in the SVN's <i>config</i> file under the <i>[auth]</i> section.
     *
     * @param storeAuth  <span class="javakeyword">true</span> to
     *                   enable the auth storage, <span class="javakeyword">false</span>
     *                   to disable
     * @see              #isAuthStorageEnabled()
     */
    public void setAuthStorageEnabled(boolean storeAuth) {
        getConfigFile().setPropertyValue(AUTH_GROUP, STORE_AUTH_CREDS, storeAuth ? YES : NO, !myIsReadonly);
    }

    public void setKeepLocks(boolean keep) {
        getConfigFile().setPropertyValue(MISCELLANY_GROUP, NO_UNLOCK, keep ? YES : NO, !myIsReadonly);
    }

    public static boolean isIgnored(ISVNOptions options, String name) {
        String[] patterns = options.getIgnorePatterns();
        for (int i = 0; patterns != null && i < patterns.length; i++) {
            String pattern = patterns[i];
            if (matches(pattern, name)) {
                return true;
            }
        }
        return false;
    }

    public String[] getIgnorePatterns() {
        String value = getConfigFile().getPropertyValue(MISCELLANY_GROUP, GLOBAL_IGNORES);
        if (value == null) {
            value = DEFAULT_IGNORES;
        }
        Collection<String> tokensList = new ArrayList<String>();
        for (StringTokenizer tokens = new StringTokenizer(value, " \t"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if ("".equals(token)) {
                continue;
            }
            tokensList.add(token);
        }
        return tokensList.toArray(new String[tokensList.size()]);
    }

    /**
     * Sets global ignore patterns.
     *
     * <p>
     * The global ignore patterns describe the names of
     * files and directories that SVNKit should ignore during status, add and
     * import operations. Similar to the
     * <i>'global-ignores'</i> option that can be found in the SVN's <i>config</i>
     * file under the <i>[miscellany]</i> section.
     *
     * <p>
     * For example, to set all <code>.exe</code> files to be ignored include
     * <code>"*.exe"</code> pattern into <code>patterns</code>.
     *
     * <p>
     * If <code>patterns</code> is <span class="javakeyword">null</span> or
     * empty then all the patterns will be removed.
     *
     * @param patterns  an array of patterns (that usually contain wildcards)
     *                  that specify file and directory names to be ignored until
     *                  they are versioned
     * @see             #getIgnorePatterns()
     */
    public void setIgnorePatterns(String[] patterns) {
        if (patterns == null || patterns.length == 0) {
            getConfigFile().setPropertyValue(MISCELLANY_GROUP, GLOBAL_IGNORES, null, !myIsReadonly);
            return;
        }
        StringBuffer value = new StringBuffer();
        for (int i = 0; i < patterns.length; i++) {
            String pattern = patterns[i];
            if (pattern != null && !"".equals(pattern.trim())) {
                value.append(pattern);
                value.append(" ");
            }
        }
        String valueStr = value.toString().trim();
        if ("".equals(valueStr)) {
            valueStr = null;
        }
        getConfigFile().setPropertyValue(MISCELLANY_GROUP, GLOBAL_IGNORES, valueStr, !myIsReadonly);
    }

    /**
     * Removes a particular global ignore pattern.
     *
     * @param pattern a patterna to be removed
     * @see           #addIgnorePattern(String)
     */
    public void deleteIgnorePattern(String pattern) {
        if (pattern == null) {
            return;
        }
        String[] patterns = getIgnorePatterns();
        Collection<String> newPatterns = new ArrayList<String>();
        for (int i = 0; i < patterns.length; i++) {
            String s = patterns[i];
            if (!s.equals(pattern)) {
                newPatterns.add(s);
            }
        }
        patterns = newPatterns.toArray(new String[newPatterns.size()]);
        setIgnorePatterns(patterns);
    }

    /**
     * Adds a new particular ignore pattern to global
     * ignore patterns.
     *
     * @param pattern an ignore pattern to be added
     * @see           #deleteIgnorePattern(String)
     */
    public void addIgnorePattern(String pattern) {
        if (pattern == null) {
            return;
        }
        String[] patterns = getIgnorePatterns();
        Collection oldPatterns = new ArrayList(Arrays.asList(patterns));
        if (!oldPatterns.contains(pattern)) {
            oldPatterns.add(pattern);
            patterns = (String[]) oldPatterns.toArray(new String[oldPatterns.size()]);
            setIgnorePatterns(patterns);
        }
    }

    /**
     * Returns autoproperties as a {@link java.util.Map}
     * where each key is a file name pattern and the corresponding
     * value is a string in the form of <code>"propName=propValue"</code>.
     *
     * @return a {@link java.util.Map} containing autoproperties
     */
    public Map getAutoProperties() {
        return getConfigFile().getProperties(AUTOPROPS_GROUP);
    }

    /**
     * Sets autoproperties that will be automatically put on all files
     * that will be added or imported.
     *
     * <p>
     * There can be several properties specified for one file pattern -
     * they should be delimited by ";".
     *
     * @param autoProperties  a {@link java.util.Map} which keys are file
     *                        name patterns and their values are strings
     *                        in the form of <code>"propName=propValue"</code>
     * @see                   #getAutoProperties()
     */
    public void setAutoProperties(Map autoProperties) {
        autoProperties = autoProperties == null ? Collections.EMPTY_MAP : autoProperties;
        Map existingProperties = getAutoProperties();
        for (Iterator<String> names = existingProperties.keySet().iterator(); names.hasNext();) {
            String pattern = names.next();
            String value = (String) existingProperties.get(pattern);
            if (value.equals(autoProperties.get(pattern))) {
                continue;
            }
            getConfigFile().setPropertyValue(AUTOPROPS_GROUP, pattern, null, false);
            names.remove();
        }
        // add all new
        for (Iterator<String> names = autoProperties.keySet().iterator(); names.hasNext();) {
            String pattern = names.next();
            String value = (String) autoProperties.get(pattern);
            if (value.equals(existingProperties.get(pattern))) {
                continue;
            }
            getConfigFile().setPropertyValue(AUTOPROPS_GROUP, pattern, value, false);
        }
        if (!myIsReadonly) {
            getConfigFile().save();
        }
    }

    public String getEditor() {
        return getConfigFile().getPropertyValue(HELPERS_GROUP, EDITOR_CMD);
    }

    public String getMergeTool() {
        return getConfigFile().getPropertyValue(HELPERS_GROUP, MERGE_TOOL_CMD);
    }

    /**
     * Removes a particular autoproperty by specifying a file name
     * pattern.
     *
     * @param pattern a file name pattern
     * @see           #setAutoProperty(String, String)
     *
     */
    public void deleteAutoProperty(String pattern) {
        getConfigFile().setPropertyValue(AUTOPROPS_GROUP, pattern, null, !myIsReadonly);
    }

    /**
     * Sets an autoproperty - binds a file name pattern with a
     * string in the form of <code>"propName=propValue"</code>.
     *
     * @param pattern      a file name pattern (usually containing
     *                     wildcards)
     * @param properties   a property for <code>pattern</code>
     */
    public void setAutoProperty(String pattern, String properties) {
        getConfigFile().setPropertyValue(AUTOPROPS_GROUP, pattern, properties, !myIsReadonly);
    }

    public boolean isInteractiveConflictResolution() {
        String value = getConfigFile().getPropertyValue(MISCELLANY_GROUP, INTERACTIVE_COFLICTS);
        return getBooleanValue(value, true);
    }

    public void setInteractiveConflictResolution(boolean interactive) {
        getConfigFile().setPropertyValue(MISCELLANY_GROUP, INTERACTIVE_COFLICTS, interactive ? YES : NO, !myIsReadonly);
    }

    public Map applyAutoProperties(File file, Map target) {
        String fileName = file.getName();
        target = target == null ? new SVNHashMap() : target;
        if (!isUseAutoProperties()) {
            return target;
        }
        Map autoProperties = getAutoProperties();
        for (Iterator<String> names = autoProperties.keySet().iterator(); names.hasNext();) {
            String pattern = names.next();
            String value = (String) autoProperties.get(pattern);
            if (value != null && !"".equals(value) && matches(pattern, fileName)) {
                StringBuffer token = new StringBuffer();
                for (int i = 0; i < value.length(); i++) {
                    char ch = value.charAt(i);
                    if (ch == ';' || i == value.length() - 1) {
                        if (i + 1 < value.length() && value.charAt(i + 1) == ';') {
                            // escaped ;
                            token.append(';');
                            i++;
                            if (i < value.length() - 1) {
                                continue;
                            }
                            // escaped at the end of the line.
                        } 
                        if (ch != ';') {
                            // just last character.
                            token.append(ch);
                        }
                        // another token.
                        String t = token.toString().trim();
                        int index = t.indexOf('=');
                        if (index < 0) {
                            target.put(t, "");
                        } else {
                            String name = t.substring(0, index).trim();
                            String pValue = index == t.length() - 1 ? "" : t.substring(index + 1).trim();
                            if (!"".equals(name.trim())) {
                                if (pValue.startsWith("\"") && pValue.endsWith("\"") && pValue.length() > 1) {
                                    pValue = pValue.substring(1, pValue.length() - 1);
                                } else if (pValue.startsWith("\'") && pValue.endsWith("\'") && pValue.length() > 1) {
                                    pValue = pValue.substring(1, pValue.length() - 1);
                                }
                                target.put(name, pValue);
                            }
                        }
                        token = token.delete(0, token.length());
                    } else {
                        token.append(ch);
                    }
                }
            }
        }
        return target;
    }
    
    public ISVNMergerFactory getMergerFactory() {
        if (myMergerFactory == null) {
            return this;
        }
        return myMergerFactory;
    }

    /**
     * Sets a factory object which is responsible for creating
     * merger drivers.
     *
     * @param merger  a factory that produces merger drivers
     *                for merge operations
     * @see           #getMergerFactory()
     */
    public void setMergerFactory(ISVNMergerFactory mergerFactory) {
        myMergerFactory = mergerFactory;
    }

    /**
     * Returns the value of a property from the <i>[svnkit]</i> section
     * of the <i>config</i> file.
     *
     * @param   propertyName a SVNKit specific config property name
     * @return the value of the property
     */
    public String getPropertyValue(String propertyName) {
        if (propertyName == null) {
            return null;
        }
        String value = getConfigFile().getPropertyValue(SVNKIT_GROUP, propertyName);
        if (value == null) {
            value = getConfigFile().getPropertyValue(OLD_SVNKIT_GROUP, propertyName);
        }
        return value;
    }

    /**
     * Sets the value of a property from the <i>[svnkit]</i> section
     * of the <i>config</i> file. 
     *
     * @param   propertyName   a SVNKit specific config property name
     * @param   propertyValue  a new value for the property; if
     *                         <span class="javakeyword">null</span> the
     *                         property is removed
     */
    public void setPropertyValue(String propertyName, String propertyValue) {
        if (propertyName == null || "".equals(propertyName.trim())) {
            return;
        }
        getConfigFile().setPropertyValue(SVNKIT_GROUP, propertyName, propertyValue, !myIsReadonly);
    }
    
    public void setConflictHandler(ISVNConflictHandler resolver) {
        myConflictResolver = resolver;
    }

    public static boolean matches(String pattern, String fileName) {
        if (pattern == null || fileName == null) {
            return false;
        }
        Pattern compiled = compileNamePatter(pattern);
        if (compiled != null) {
            return compiled.matcher(fileName).matches();
        }
        return false;
    }

    public ISVNMerger createMerger(byte[] conflictStart, byte[] conflictSeparator, byte[] conflictEnd) {
        return new DefaultSVNMerger(conflictStart, conflictSeparator, conflictEnd, myConflictResolver);
    }

    public ISVNConnector createTunnelConnector(SVNURL url) {
	    String subProtocolName = url.getProtocol().substring("svn+".length());
        if (subProtocolName == null) {
            return null;
        }
        Map<String,String> tunnels = getConfigFile().getProperties("tunnels");
        final String tunnel = tunnels.get(subProtocolName);
        if (tunnel == null) {
            return null;
        }
        return new SVNTunnelConnector(subProtocolName, tunnel);
    }

    public DateFormat getKeywordDateFormat() {
        String localeID = getConfigFile().getPropertyValue(SVNKIT_GROUP, KEYWORD_LOCALE);
        if (localeID == null) {
            localeID = DEFAULT_LOCALE;
        }
        String tzID = getConfigFile().getPropertyValue(SVNKIT_GROUP, KEYWORD_TIMEZONE);
        if (tzID == null) {
            tzID = DEFAULT_TIMEZONE;
        }
        if (!myKeywordTimezone.equals(tzID)) {
            TimeZone tz = TimeZone.getTimeZone(tzID);
            myKeywordTimezone = tzID;
            synchronized (myKeywordDateFormat) {
               myKeywordDateFormat.setTimeZone(tz);
            }
        }
        if (!myKeywordLocale.equals(localeID)) {
            Locale newLocale = toLocale(localeID);
            if (newLocale == null) {
                newLocale = Locale.getDefault();
            }
            myKeywordLocale = localeID;
            synchronized (myKeywordDateFormat) {
               myKeywordDateFormat.setCalendar(Calendar.getInstance(myKeywordDateFormat.getTimeZone(), newLocale));
               myKeywordDateFormat.setDateFormatSymbols(new DateFormatSymbols(newLocale));
            }
        }
        return myKeywordDateFormat;
    }

    public String[] getPreservedConflictFileExtensions() {
        String value = getConfigFile().getPropertyValue(MISCELLANY_GROUP, PRESERVED_CONFLICT_FILE_EXTENSIONS);
        if (value == null) {
            value = "";
        }
        Collection<String> tokensList = new ArrayList<String>();
        for (StringTokenizer tokens = new StringTokenizer(value, " \n\r\t"); tokens.hasMoreTokens();) {
            String token = tokens.nextToken();
            if ("".equals(token)) {
                continue;
            }
            tokensList.add(token);
        }
        return tokensList.toArray(new String[tokensList.size()]);
    }

    public boolean isAllowAllForwardMergesFromSelf() {
        return false;
    }

    public String getNativeCharset() {
        return System.getProperty("file.encoding");
    }

    public byte[] getNativeEOL() {
        return System.getProperty("line.separator").getBytes();
    }

    public Map<String,String> getFileExtensionsToMimeTypes() {
        String mimeTypesFile = getConfigFile().getPropertyValue(MISCELLANY_GROUP, MIME_TYPES_FILE);
        if (mimeTypesFile == null) {
            return null;
        }
        
        BufferedReader reader = null;
        Map<String,String> extensionsToMimeTypes = new SVNHashMap();
        try {
            reader = new BufferedReader(new FileReader(mimeTypesFile));
            LinkedList<String> tokensList = new LinkedList<String>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#")) {
                    continue;
                }

                tokensList.clear();
                for (StringTokenizer tokens = new StringTokenizer(line, " \t"); tokens.hasMoreTokens();) {
                    String token = tokens.nextToken();
                    if ("".equals(token)) {
                        continue;
                    }
                    tokensList.add(token);
                }
                if (tokensList.size() < 2) {
                    continue;
                }
                
                String mimeType = tokensList.get(0);
                for (int i = 1; i < tokensList.size(); i++) {
                    String extension = tokensList.get(i);
                    extensionsToMimeTypes.put(extension, mimeType);
                }
            }
        } catch (IOException e) {
            return null;
        } finally {
            SVNFileUtil.closeFile(reader);
        }

        return extensionsToMimeTypes;
    }

    public String getDiffCommand() {
        return getConfigFile().getPropertyValue(HELPERS_GROUP, DIFF_CMD);
    }

    public void setDiffCommand(String diffCmd) {
        getConfigFile().setPropertyValue(HELPERS_GROUP, DIFF_CMD, diffCmd, !myIsReadonly);
    }

    private SVNCompositeConfigFile getConfigFile() {
        if (myConfigFile == null) {
            SVNConfigFile.createDefaultConfiguration(myConfigDirectory);
            SVNConfigFile userConfig = new SVNConfigFile(new Resource(myConfigDirectory, "config"));
            SVNConfigFile systemConfig = new SVNConfigFile(new Resource(SVNFileUtil.getSystemConfigurationDirectory(), "config"));
            myConfigFile = new SVNCompositeConfigFile(systemConfig, userConfig);
            myConfigFile.setGroupsToOptions(myConfigOptions);
        }
        return myConfigFile;
    }

    private static Pattern compileNamePatter(String wildcard) {
        if (wildcard == null) {
            return null;
        }
        StringBuffer result = new StringBuffer();
        for (int i = 0; i < wildcard.length(); i++) {
            char ch = wildcard.charAt(i);
            switch (ch) {
            case '?':
                result.append(".");
                break;
            case '*':
                result.append(".*");
                break;

            case '.':
            case '!':
            case '$':
            case '(':
            case ')':
            case '+':
            case '<':
            case '>':
            case '|':
            case '\\':
            case '^':
            case '{':
            case '}':
                result.append("\\");
            default:
                result.append(ch);
            }
        }
        try {
            return Pattern.compile(result.toString());
        } catch (PatternSyntaxException e) {
            return null;
        }
    }

    private static Locale toLocale(String str) {
        if (str == null) {
            return null;
        }
        int len = str.length();
        if (len != 2 && len != 5 && len < 7) {
            return null;
        }
        char ch0 = str.charAt(0);
        char ch1 = str.charAt(1);
        if (ch0 < 'a' || ch0 > 'z' || ch1 < 'a' || ch1 > 'z') {
            return null;
        }
        if (len == 2) {
            return new Locale(str, "");
        }
        if (str.charAt(2) != '_') {
            return null;
        }
        char ch3 = str.charAt(3);
        char ch4 = str.charAt(4);
        if (ch3 < 'A' || ch3 > 'Z' || ch4 < 'A' || ch4 > 'Z') {
            return null;
        }
        if (len == 5) {
            return new Locale(str.substring(0, 2), str.substring(3, 5));
        }
        if (str.charAt(5) != '_') {
            return null;
        }
        return new Locale(str.substring(0, 2), str.substring(3, 5), str.substring(6));
    }

    private static File getDefaultConfigDir() {
        return SVNWCUtil.getDefaultConfigurationDirectory();
    }

    public static boolean getBooleanValue(String value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        value = value.trim();
        return YES.equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)
                || "on".equalsIgnoreCase(value);
    }

}
