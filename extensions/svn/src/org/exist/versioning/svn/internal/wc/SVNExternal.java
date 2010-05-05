/*
 * ====================================================================
 * Copyright (c) 2004-2010 TMate Software Ltd.  All rights reserved.
 *
 * This software is licensed as described in the file COPYING, which
 * you should have received as part of this distribution.  The terms
 * are also available at http://svnkit.com/license.html.
 * If newer versions of this license are posted there, you may use a
 * newer version instead, at your option.
 * ====================================================================
 */
package org.exist.versioning.svn.internal.wc;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNExternal {
    
    private SVNRevision myRevision;
    private SVNRevision myPegRevision;
    private String myURL;
    private String myPath;
    private SVNURL myResolvedURL;
    private boolean myIsRevisionExplicit;
    private boolean myIsPegRevisionExplicit;
    private boolean myIsNewFormat;
    private String myRawValue;
    
    private SVNExternal() {
        myRevision = SVNRevision.UNDEFINED;
        myPegRevision = SVNRevision.UNDEFINED;
    }
    
    public SVNExternal(String target, String url, SVNRevision pegRevision, SVNRevision revision, 
            boolean isRevisionExplicit, boolean isPegRevisionExplicit, boolean isNewFormat) {
        myPath = target;
        myURL = url;
        myRevision = revision;
        myPegRevision = pegRevision;
        myIsRevisionExplicit = isRevisionExplicit;
        myIsPegRevisionExplicit = isPegRevisionExplicit;
        myIsNewFormat = isNewFormat; 
    }
    
    public SVNRevision getRevision() {
        return myRevision;
    }
    
    public SVNRevision getPegRevision() {
        return myPegRevision;
    }
    
    public String getPath() {
        return myPath;
    }

    public String getUnresolvedUrl() {
        return myURL;
    }

    public String getRawValue() {
        return myRawValue;
    }

    public boolean isRevisionExplicit() {
        return myIsRevisionExplicit;
    }
    
    public boolean isPegRevisionExplicit() {
        return myIsPegRevisionExplicit;
    }
    
    public boolean isNewFormat() {
        return myIsNewFormat;
    }

    public SVNURL getResolvedURL() {
        return myResolvedURL;
    }

    public SVNURL resolveURL(SVNURL rootURL, SVNURL ownerURL) throws SVNException {
        String canonicalURL = SVNPathUtil.canonicalizePath(myURL);
        if (SVNPathUtil.isURL(canonicalURL)) {
            myResolvedURL = SVNURL.parseURIEncoded(canonicalURL); 
            return getResolvedURL();
        }
        if (myURL.startsWith("../") || myURL.startsWith("^/")) {
            // ../ relative to the parent directory of the external
            // ^/     relative to the repository root
            String[] base = myURL.startsWith("../") ? ownerURL.getPath().split("/") : rootURL.getPath().split("/");
            LinkedList baseList = new LinkedList(Arrays.asList(base));
            if (canonicalURL.startsWith("^/")) {
                canonicalURL = canonicalURL.substring("^/".length());
            }
            String[] relative = canonicalURL.split("/");
            for (int i = 0; i < relative.length; i++) {
                if ("..".equals(relative[i])) {
                    // remove last from base.
                    if (!baseList.isEmpty()) {
                        baseList.removeLast();
                    }
                } else {
                    baseList.add(relative[i]);
                }
            }
            String finalPath = "/";
            for (Iterator segments = baseList.iterator(); segments.hasNext();) {
                String segment = (String) segments.next();
                finalPath = SVNPathUtil.append(finalPath, segment);
            }
            myResolvedURL = ownerURL.setPath(finalPath, true);
            return getResolvedURL();
        }
        
        if (myURL.indexOf("/../") >= 0 || myURL.startsWith("../") || myURL.endsWith("/..")) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "The external relative URL ''{0}'' cannot have backpaths, i.e. ''..''.", myURL);
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        
        if (myURL.startsWith("//")) {
            // //     relative to the scheme
            myResolvedURL = SVNURL.parseURIEncoded(SVNPathUtil.canonicalizePath(rootURL.getProtocol() + ":" + myURL));
            return getResolvedURL();
        } else if (myURL.startsWith("/")) {
            // /      relative to the server's host
            myResolvedURL = ownerURL.setPath(myURL, true);
            return getResolvedURL();
        }
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_URL, "Unrecognized format for the relative external URL ''{0}''.", myURL);
        SVNErrorManager.error(err, SVNLogType.DEFAULT);
        return null;
    }

    public String toString() {
        String value = "";
        String path = quotePath(myPath);
        String url = quotePath(myURL);
        
        if (myIsPegRevisionExplicit && SVNRevision.isValidRevisionNumber(myPegRevision.getNumber())) {
            if (myIsRevisionExplicit && SVNRevision.isValidRevisionNumber(myRevision.getNumber())) {
                value += "-r" + myRevision + " ";
            }
            value += url + "@" + myPegRevision + " " + path;
        } else {
            if (myIsNewFormat) {
                if (myIsRevisionExplicit && SVNRevision.isValidRevisionNumber(myRevision.getNumber())) {
                    value += "-r" + myRevision + " ";
                }
                value += url + " " + path;
            } else {
                value += path; 
                if (myIsRevisionExplicit && SVNRevision.isValidRevisionNumber(myRevision.getNumber())) {
                    value += " -r" + myRevision;
                }            
                value += " " + url;
            }
        }
        return value;
    }

    public static SVNExternal[] parseExternals(String owner, String description) throws SVNException {
        List lines = new ArrayList();
        for(StringTokenizer tokenizer = new StringTokenizer(description, "\r\n"); tokenizer.hasMoreTokens();) {
            lines.add(tokenizer.nextToken());
        }
        Collection externals = new ArrayList();
        for (int i = 0; i < lines.size(); i++) {
            String line = ((String) lines.get(i)).trim();
            if ("".equals(line) || line.startsWith("#")) {
                continue;
            }
            List tokens = new ArrayList();
            for(Iterator tokenizer = new ExternalTokenizer(line); tokenizer.hasNext();) {
                tokens.add(tokenizer.next());
            }
            if (tokens.size() < 2 || tokens.size() > 4) {
                reportParsingError(owner, line);
            }
            SVNExternal external = new SVNExternal();
            int revisionToken = fetchRevision(external, owner, line, tokens);
            String token0 = (String) tokens.get(0);
            String token1 = (String) tokens.get(1);
            boolean token0isURL = SVNPathUtil.isURL(token0); 
            boolean token1isURL = SVNPathUtil.isURL(token1);
            
            if (token0isURL && token1isURL) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_EXTERNALS_DESCRIPTION, 
                        "Invalid svn:external property on ''{0}'': cannot use two absolute URLs (''{1}'' and ''{2}'') in an external; " +
                        "one must be a path where an absolute or relative URL is checked out to", new Object[] {owner, token0, token1});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (revisionToken == 0 && token1isURL) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_EXTERNALS_DESCRIPTION, 
                        "Invalid svn:external property on ''{0}'': cannot use a URL ''{1}'' as the target directory for an external definition",
                        new Object[] {owner, token1});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            if (revisionToken == 1 && token0isURL) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_EXTERNALS_DESCRIPTION, 
                        "Invalid svn:external property on ''{0}'': cannot use a URL ''{1}'' as the target directory for an external definition",
                        new Object[] {owner, token0});
                SVNErrorManager.error(err, SVNLogType.WC);
            }
            
            if (revisionToken == 0 || (revisionToken == -1 && (token0isURL || !token1isURL))) {
                external.myPath = token1;
                boolean schemeRelative = token0.startsWith("//");
                if (schemeRelative) {
                    token0 = token0.substring(2);
                }
            
                SVNPath path = new SVNPath(token0, true);
                external.myURL = schemeRelative ? "//" + path.getTarget() : path.getTarget();
                external.myPegRevision = path.getPegRevision();
                if (external.myPegRevision == SVNRevision.BASE) {
                    external.myPegRevision = SVNRevision.HEAD;
                }
                
                if (external.myPegRevision != SVNRevision.UNDEFINED) {
                    external.myIsPegRevisionExplicit = true;
                }
                
                external.myIsNewFormat = true;
            } else {
                external.myPath = token0;
                external.myURL = token1;
                external.myPegRevision = external.myRevision;
            }
            
            if (external.myPegRevision == SVNRevision.UNDEFINED) {
                external.myPegRevision = SVNRevision.HEAD;
            } 
            
            if (external.myRevision == SVNRevision.UNDEFINED) {
                external.myRevision = external.myPegRevision;
            }
            
            external.myPath = SVNPathUtil.canonicalizePath(external.myPath.replace(File.separatorChar, '/'));
            
            if (external.myPath.length() == 0 || 
                    external.myPath.equals(".") || 
                    external.myPath.equals("..") || 
                    external.myPath.startsWith("../") || 
                    external.myPath.startsWith("/") || 
                    external.myPath.indexOf("/../") > 0 || 
                    external.myPath.endsWith("/..")) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_EXTERNALS_DESCRIPTION,
                        "Invalid {0} property on ''{1}'': target ''{2}'' is an absolute path or involves ''..''", new Object[] {SVNProperty.EXTERNALS, owner, external.myPath});
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
            
            external.myRawValue = line;
            if (external.myURL != null && SVNPathUtil.isURL(external.myURL)) {
                SVNURL.parseURIEncoded(external.myURL);
            }
            externals.add(external);
        }
        return (SVNExternal[]) externals.toArray(new SVNExternal[externals.size()]);
    }
    
    private static String quotePath(String path) {
        for(int i = 0; i < path.length(); i++) {
            if (Character.isWhitespace(path.charAt(i))) {
                return "\"" + path + "\"";
            }
        }
        return path;
    }
    
    private static int fetchRevision(SVNExternal external, String owner, String line, List tokens) throws SVNException {
        for (int i = 0; i < tokens.size() && i < 2; i++) {
            String token = (String) tokens.get(i);
            String revisionStr = null;
            if (token.length() >= 2 && 
                    token.charAt(0) == '-' && token.charAt(1) == 'r') {
                if (token.length() == 2 && tokens.size() == 4) {
                    revisionStr = (String) tokens.get(i + 1);
                    // remove separate '-r' token.
                    tokens.remove(i);
                } else if (tokens.size() == 3) {
                    revisionStr = token.substring(2); 
                }
                if (revisionStr == null || "".equals(revisionStr)) {
                    reportParsingError(owner, line);
                }
                long revNumber = -1;
                try {
                    revNumber = Long.parseLong(revisionStr);
                    if (revNumber < 0) {
                        SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "Negative revision number found parsing '" + revisionStr + "'");
                        reportParsingError(owner, line);
                    }
                } catch (NumberFormatException nfe) {
                    SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, "Invalid revision number found parsing '" + revisionStr + "'");
                    reportParsingError(owner, line);
                }
                external.myRevision = SVNRevision.create(revNumber);
                external.myIsRevisionExplicit = true;
                tokens.remove(i);
                return i;
            }
        }
        if (tokens.size() == 2) {
            return -1;
        }
        reportParsingError(owner, line);
        return -1;
    }
    
    private static void reportParsingError(String owner, String line) throws SVNException {
        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_INVALID_EXTERNALS_DESCRIPTION,
                "Error parsing {0} property on ''{1}'': ''{2}''", new Object[] {SVNProperty.EXTERNALS, owner, line});
        SVNErrorManager.error(err, SVNLogType.DEFAULT);
    }

    private static class ExternalTokenizer implements Iterator {
        
        private String myNextToken;
        private String myLine;

        public ExternalTokenizer(String line) {
            myLine = line;
            myNextToken = advance();
        }
        public boolean hasNext() {
            return myNextToken != null;
        }
        public Object next() {
            String next = myNextToken;
            myNextToken = advance();
            return next;
        }
        public void remove() {
            throw new UnsupportedOperationException();
        }
        
        private String advance() {
            while(myLine.length() > 0 && Character.isWhitespace(myLine.charAt(0))) {
                myLine = myLine.substring(1);
            }
            if (myLine.length() == 0) {
                return null;
            }
            char ch = myLine.charAt(0);
            int quouteType = ch == '\'' ? 1 : (ch == '\"' ? 2 : 0);
            if (quouteType != 0) {
                myLine = myLine.substring(1);
            }
            int index = 0;
            StringBuffer result = new StringBuffer();
            while(index < myLine.length()) {
                ch = myLine.charAt(index);                
                if (quouteType == 0) {
                    if (Character.isWhitespace(ch)) {
                        break;
                    }
                } else if (quouteType == 1) {
                    if (ch == '\'') {
                        break;
                    }
                } else if (quouteType == 2) {
                    if (ch == '\"') {
                        break;
                    }
                }
                if (ch == '\\') {
                    // append qouted character, so far whitespace only
                    if (index + 1 < myLine.length()) {
                        char escaped = myLine.charAt(index + 1);
                        if (escaped == ' ' || escaped == '\'' || escaped == '\"') {
                            // append escaped char instead of backslash
                            result.append(escaped);
                            index++;
                            index++;
                            continue;
                        }
                    } 
                }
                result.append(ch);
                index++;
                
            }
            if (index + 1 < myLine.length()) {
                myLine = myLine.substring(index + 1);
            } else {
                myLine = "";
            }
            return result.toString();
        }
    }

}
