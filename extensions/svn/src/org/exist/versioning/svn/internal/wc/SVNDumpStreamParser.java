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

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.CharsetDecoder;
import java.util.Map;

import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.wc.ISVNLoadHandler;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SVNDumpStreamParser {
    private ISVNCanceller myCanceller;
    
    public SVNDumpStreamParser(ISVNCanceller canceller) {
        myCanceller = canceller;
    }
    
    public void parseDumpStream(InputStream dumpStream, ISVNLoadHandler handler, CharsetDecoder decoder) throws SVNException {
        String line = null;
        int version = -1;
        StringBuffer buffer = new StringBuffer();
        try {
            line = SVNFileUtil.readLineFromStream(dumpStream, buffer, decoder);
            if (line == null) {
                SVNAdminHelper.generateIncompleteDataError();
            }

            //parse format
            if (!line.startsWith(SVNAdminHelper.DUMPFILE_MAGIC_HEADER + ":")) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                        "Malformed dumpfile header");
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }
            
            try {
                line = line.substring(SVNAdminHelper.DUMPFILE_MAGIC_HEADER.length() + 1);
                line = line.trim();
                version = Integer.parseInt(line);
                if (version > SVNAdminHelper.DUMPFILE_FORMAT_VERSION) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                            "Unsupported dumpfile version: {0}", new Integer(version));
                    SVNErrorManager.error(err, SVNLogType.FSFS);
                }
            } catch (NumberFormatException nfe) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                        "Malformed dumpfile header");
                SVNErrorManager.error(err, nfe, SVNLogType.FSFS);
            }
        
            while (true) {
                myCanceller.checkCancelled();
                boolean foundNode = false;
            
                //skip empty lines
                buffer.setLength(0);
                line = SVNFileUtil.readLineFromStream(dumpStream, buffer, decoder);
                if (line == null) {
                    if (buffer.length() > 0) {
                        SVNAdminHelper.generateIncompleteDataError();
                    } else {
                        break;
                    }
                } 

                if (line.length() == 0 || Character.isWhitespace(line.charAt(0))) {
                    continue;
                }
            
                Map headers = readHeaderBlock(dumpStream, line, decoder);
                if (headers.containsKey(SVNAdminHelper.DUMPFILE_REVISION_NUMBER)) {
                    handler.closeRevision();
                    handler.openRevision(headers);
                } else if (headers.containsKey(SVNAdminHelper.DUMPFILE_NODE_PATH)) {
                    handler.openNode(headers);
                    foundNode = true;
                } else if (headers.containsKey(SVNAdminHelper.DUMPFILE_UUID)) {
                    String uuid = (String) headers.get(SVNAdminHelper.DUMPFILE_UUID);
                    handler.parseUUID(uuid);
                } else if (headers.containsKey(SVNAdminHelper.DUMPFILE_MAGIC_HEADER)) {
                    try {
                        version = Integer.parseInt((String) headers.get(SVNAdminHelper.DUMPFILE_MAGIC_HEADER));    
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                                "Malformed dumpfile header");
                        SVNErrorManager.error(err, nfe, SVNLogType.FSFS);
                    }
                } else {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                            "Unrecognized record type in stream");
                    SVNErrorManager.error(err, SVNLogType.FSFS);
                }
                
                String contentLength = (String) headers.get(SVNAdminHelper.DUMPFILE_CONTENT_LENGTH);
                String propContentLength = (String) headers.get(SVNAdminHelper.DUMPFILE_PROP_CONTENT_LENGTH);
                String textContentLength = (String) headers.get(SVNAdminHelper.DUMPFILE_TEXT_CONTENT_LENGTH);
                
                boolean isOldVersion = version == 1 && contentLength != null && propContentLength == null && 
                textContentLength == null;
                
                long actualPropLength = 0;
                if (propContentLength != null || isOldVersion) {
                    String delta = (String) headers.get(SVNAdminHelper.DUMPFILE_PROP_DELTA);
                    boolean isDelta = delta != null && "true".equals(delta);
                    
                    if (foundNode && !isDelta) {
                        handler.removeNodeProperties();
                    }
                    
                    long length = 0;
                    try {
                        length = Long.parseLong(propContentLength != null ? propContentLength : contentLength);
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                                "Malformed dumpfile header: can't parse property block length header");
                        SVNErrorManager.error(err, nfe, SVNLogType.FSFS);
                    }
                    actualPropLength += parsePropertyBlock(dumpStream, handler, decoder, length, foundNode);
                }
                
                if (textContentLength != null) {
                    String delta = (String) headers.get(SVNAdminHelper.DUMPFILE_TEXT_DELTA);
                    boolean isDelta = delta != null && "true".equals(delta);
                    long length = 0;
                    try {
                        length = Long.parseLong(textContentLength);
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                                "Malformed dumpfile header: can't parse text block length header");
                        SVNErrorManager.error(err, nfe, SVNLogType.FSFS);
                    }
                    handler.parseTextBlock(dumpStream, length, isDelta);
                } else if (isOldVersion) {
                    long length = 0;
                    try {
                        length = Long.parseLong(contentLength);
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                                "Malformed dumpfile header: can't parse content length header");
                        SVNErrorManager.error(err, nfe, SVNLogType.FSFS);
                    }
                    
                    length -= actualPropLength;
                    
                    if (length > 0 || SVNNodeKind.parseKind((String)headers.get(SVNAdminHelper.DUMPFILE_NODE_KIND)) == SVNNodeKind.FILE) {
                        handler.parseTextBlock(dumpStream, length, false);
                    }
                }
                
                if (contentLength != null && !isOldVersion) {
                    long remaining = 0;
                    try {
                        remaining = Long.parseLong(contentLength);
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                                "Malformed dumpfile header: can't parse content length header");
                        SVNErrorManager.error(err, nfe, SVNLogType.FSFS);
                    }

                    long propertyContentLength = 0;
                    if (propContentLength != null) {
                        try {
                            propertyContentLength = Long.parseLong(propContentLength);
                        } catch (NumberFormatException nfe) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                                    "Malformed dumpfile header: can't parse property block length header");
                            SVNErrorManager.error(err, nfe, SVNLogType.FSFS);
                        }
                    }
                    remaining -= propertyContentLength; 

                    long txtContentLength = 0;
                    if (textContentLength != null) {
                        try {
                            txtContentLength = Long.parseLong(textContentLength);
                        } catch (NumberFormatException nfe) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                                    "Malformed dumpfile header: can't parse text block length header");
                            SVNErrorManager.error(err, nfe, SVNLogType.FSFS);
                        }
                    }
                    remaining -= txtContentLength; 
                    
                    if (remaining < 0) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                                "Sum of subblock sizes larger than total block content length");
                        SVNErrorManager.error(err, SVNLogType.FSFS);
                    }
                    
                    byte buf[] = new byte[SVNFileUtil.STREAM_CHUNK_SIZE];

                    long numRead = 0;
                    long numToRead = remaining;
                    while (remaining > 0) {
                        int readSize = remaining >= SVNFileUtil.STREAM_CHUNK_SIZE ? 
                                SVNFileUtil.STREAM_CHUNK_SIZE : (int) remaining;

                        int r = dumpStream.read(buf, 0, readSize);
                        if (r < 0) {
                            break;
                        }
                        
                        numRead += r;
                        remaining -= r;
                    }

                    if (numRead != numToRead) {
                        SVNAdminHelper.generateIncompleteDataError();
                    }

                }
                
                if (foundNode) {
                    handler.closeNode();
                    foundNode = false;
                }
            }

            handler.closeRevision();
            
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe, SVNLogType.FSFS);
        }
    }
    
    private long parsePropertyBlock(InputStream dumpStream, ISVNLoadHandler handler, CharsetDecoder decoder, 
            long contentLength, boolean isNode) throws SVNException {
        long actualLength = 0;
        StringBuffer buffer = new StringBuffer();
        String line = null;
        
        try {
            while (contentLength != actualLength) {
                buffer.setLength(0);
                line = SVNFileUtil.readLineFromStream(dumpStream, buffer, decoder);
                
                if (line == null) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                            "Incomplete or unterminated property block");
                    SVNErrorManager.error(err, SVNLogType.FSFS);
                }
                
                //including '\n'
                actualLength += line.length() + 1;
                if ("PROPS-END".equals(line)) {
                    break;
                } else if (line.charAt(0) == 'K' && line.charAt(1) == ' ') {
                    int len = 0;
                    try {
                        len = Integer.parseInt(line.substring(2));    
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                                "Malformed dumpfile header: can't parse node property key length");
                        SVNErrorManager.error(err, nfe, SVNLogType.FSFS);
                    }
                    
                    byte[] buff = new byte[len + 1];
                    actualLength += SVNAdminHelper.readKeyOrValue(dumpStream, buff, len + 1);
                    String propName = new String(buff, 0, len, "UTF-8");
                    
                    buffer.setLength(0);
                    line = SVNFileUtil.readLineFromStream(dumpStream, buffer, decoder);
                    if (line == null) {
                        SVNAdminHelper.generateIncompleteDataError();
                    }
                    
                    //including '\n'
                    actualLength += line.length() + 1;
                    if (line.charAt(0) == 'V' && line.charAt(1) == ' ') {
                        try {
                            len = Integer.parseInt(line.substring(2));    
                        } catch (NumberFormatException nfe) {
                            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                                    "Malformed dumpfile header: can't parse node property value length");
                            SVNErrorManager.error(err, nfe, SVNLogType.FSFS);
                        }
    
                        buff = new byte[len + 1];
                        actualLength += SVNAdminHelper.readKeyOrValue(dumpStream, buff, len + 1);
                        SVNPropertyValue propValue = SVNPropertyValue.create(propName, buff, 0, len);
                        if (isNode) {
                            handler.setNodeProperty(propName, propValue);
                        } else {
                            handler.setRevisionProperty(propName, propValue);
                        }
                    } else {
                        SVNAdminHelper.generateStreamMalformedError();
                    }
                } else if (line.charAt(0) == 'D' && line.charAt(1) == ' ') {
                    int len = 0;
                    try {
                        len = Integer.parseInt(line.substring(2));    
                    } catch (NumberFormatException nfe) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                                "Malformed dumpfile header: can't parse node property key length");
                        SVNErrorManager.error(err, nfe, SVNLogType.FSFS);
                    }
                    
                    byte[] buff = new byte[len + 1];
                    actualLength += SVNAdminHelper.readKeyOrValue(dumpStream, buff, len + 1);
                    
                    if (!isNode) {
                        SVNAdminHelper.generateStreamMalformedError();
                    }
                    
                    String propName = new String(buff, 0, len, "UTF-8");
                    handler.deleteNodeProperty(propName);
                } else {
                    SVNAdminHelper.generateStreamMalformedError();
                }
            }
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe, SVNLogType.FSFS);
        }
        
        return actualLength;
    }

    private Map readHeaderBlock(InputStream dumpStream, String firstHeader, CharsetDecoder decoder) throws SVNException, IOException {
        Map headers = new SVNHashMap();
        StringBuffer buffer = new StringBuffer();
    
        while (true) {
            String header = null;
            buffer.setLength(0);
            if (firstHeader != null) {
                header = firstHeader;
                firstHeader = null;
            } else {
                header = SVNFileUtil.readLineFromStream(dumpStream, buffer, decoder);
                if (header == null && buffer.length() > 0) {
                    SVNAdminHelper.generateIncompleteDataError();
                } else if (buffer.length() == 0) {
                    break;
                }
            }
        
            int colonInd = header.indexOf(':');
            if (colonInd == -1) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                        "Dump stream contains a malformed header (with no '':'') at ''{0}''", 
                        header.length() > 20 ? header.substring(0, 19) : header);
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }
        
            String name = header.substring(0, colonInd);
            if (colonInd + 2 > header.length()) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_MALFORMED_DATA, 
                        "Dump stream contains a malformed header (with no value) at ''{0}''", 
                        header.length() > 20 ? header.substring(0, 19) : header);
                SVNErrorManager.error(err, SVNLogType.FSFS);
            }
            String value = header.substring(colonInd + 2);
            headers.put(name, value);
        }
    
        return headers;
    }
    
}
