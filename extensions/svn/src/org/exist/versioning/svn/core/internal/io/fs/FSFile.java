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
package org.exist.versioning.svn.core.internal.io.fs;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.MalformedInputException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.logging.Level;

import org.apache.commons.io.IOUtils;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNPropertyValue;
import org.tmatesoft.svn.core.internal.io.fs.PathInfo;
import org.tmatesoft.svn.core.internal.util.SVNHashMap;
import org.tmatesoft.svn.core.internal.wc.SVNErrorManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.util.SVNDebugLog;
import org.tmatesoft.svn.util.SVNLogType;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class FSFile {
    
    private File myFile;
//    private FileChannel myChannel = null;
    private InputStream myInputStream;
    private long myPosition;
    
    private long myBufferPosition;
    
    private ByteBuffer myBuffer;
    private ByteBuffer myReadLineBuffer;
    private CharsetDecoder myDecoder;
    private MessageDigest myDigest;
    
    public FSFile(File file) {
        myFile = file;
        myPosition = 0;
        myBufferPosition = 0;
        myBuffer = ByteBuffer.allocate(1024);
        myReadLineBuffer = ByteBuffer.allocate(1024);
        myDecoder = Charset.forName("UTF-8").newDecoder();
        myDecoder = myDecoder.onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT);
    }
    
    public void seek(long position) {
        myPosition = position;
    }

    public long position() {
        return myPosition;
    }

    public long size() {
        return myFile.length();
    }
    
    public void resetDigest() {
        if (myDigest == null) {
            try {
                myDigest = MessageDigest.getInstance("MD5");
            } catch (NoSuchAlgorithmException e) {
            }
        }
        myDigest.reset();
    }
    
    public String digest() {
        String digest =  SVNFileUtil.toHexDigest(myDigest);
        myDigest = null;
        return digest;
    }
    
    public int readInt() throws SVNException, NumberFormatException {
        String line = readLine(80);
        if (line == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, 
                    "First line of ''{0}'' contains non-digit", myFile);
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        return Integer.parseInt(line);
    }

    public long readLong() throws SVNException, NumberFormatException {
        String line = readLine(80);
        if (line == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.BAD_VERSION_FILE_FORMAT, 
                    "First line of ''{0}'' contains non-digit", myFile);
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        return Long.parseLong(line);
    }

    public String readLine(int limit) throws SVNException {
        allocateReadBuffer(limit);
        try {
            while(myReadLineBuffer.hasRemaining()) {
                int b = read();
                if (b < 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, "Can''t read length line from file {0}", getFile());
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                } else if (b == '\n') {
                    break;
                }
                myReadLineBuffer.put((byte) (b & 0XFF));
            }
            myReadLineBuffer.flip();
            return myDecoder.decode(myReadLineBuffer).toString();
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Can''t read length line from file {0}: {1}", new Object[]{getFile(), e.getLocalizedMessage()});
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
        }
        return null;
    }

    public String readLine(StringBuffer buffer) throws SVNException {
        if (buffer == null) {
            buffer = new StringBuffer();
        }
        boolean endOfLineMet = false;
        boolean lineStart = true;
        try {
            while (!endOfLineMet) {
                allocateReadBuffer(160);
                while(myReadLineBuffer.hasRemaining()) {
                    int b = read();
                    if (b < 0) {
                        SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.STREAM_UNEXPECTED_EOF, 
                                "Can''t read length line from file {0}", getFile());
                        SVNErrorManager.error(err, lineStart ? Level.FINEST : Level.FINE, SVNLogType.DEFAULT);
                    } else if (b == '\n') {
                        endOfLineMet = true;
                        break;
                    }
                    myReadLineBuffer.put((byte) (b & 0XFF));
	                lineStart = false;
                }
                myReadLineBuffer.flip();
                buffer.append(myDecoder.decode(myReadLineBuffer).toString());
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "Can''t read length line from file {0}: {1}", new Object[]{getFile(), e.getLocalizedMessage()});
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
        }
        return buffer.toString();
    }

    public SVNProperties readProperties(boolean allowEOF, boolean allowBinaryValues) throws SVNException {
        SVNProperties properties = new SVNProperties();
        String line = null;
        try {
            while(true) {
                try {
                    line = readLine(160); // K length or END, there may be EOF.
                } catch (SVNException e) {
                    if (allowEOF && e.getErrorMessage().getErrorCode() == SVNErrorCode.STREAM_UNEXPECTED_EOF) {
                        break;
                    }
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
                }
                if (line == null || "".equals(line)) {
                    break;
                } else if (!allowEOF && "END".equals(line)) {
                    break;
                }
                char kind = line.charAt(0);
                int length = -1;
                if ((kind != 'K' && kind != 'D') || line.length() < 3 || line.charAt(1) != ' ' || line.length() < 3) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                } 
                try {
                    length = Integer.parseInt(line.substring(2));
                } catch (NumberFormatException nfe) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                }
                if (length < 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                }
                allocateReadBuffer(length + 1);
                read(myReadLineBuffer);
                myReadLineBuffer.flip();
                myReadLineBuffer.limit(myReadLineBuffer.limit() - 1);
                int pos = myReadLineBuffer.position();
                int limit = myReadLineBuffer.limit();
                String key = null;
                try {
                    key = myDecoder.decode(myReadLineBuffer).toString();
                } catch (MalformedInputException mfi) {
                    key = new String(myReadLineBuffer.array(), myReadLineBuffer.arrayOffset() + pos, limit - pos);
                }
                if (kind == 'D') {
                    properties.put(key, (SVNPropertyValue) null);
                    continue;
                }
                line = readLine(160);
                if (line == null || line.length() < 3 || line.charAt(0) != 'V' || line.charAt(1) != ' ') {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                }
                try {
                    length = Integer.parseInt(line.substring(2));
                } catch (NumberFormatException nfe) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                }
                if (length < 0) {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
                    SVNErrorManager.error(err, SVNLogType.DEFAULT);
                }
                allocateReadBuffer(length + 1);
                read(myReadLineBuffer);
                myReadLineBuffer.flip();
                myReadLineBuffer.limit(myReadLineBuffer.limit() - 1);
                pos = myReadLineBuffer.position();
                limit = myReadLineBuffer.limit();
                try {
                    properties.put(key, myDecoder.decode(myReadLineBuffer).toString());
                } catch (CharacterCodingException cce) {
                    if (allowBinaryValues){
                        byte[] dst = new byte[limit - pos];
                        myReadLineBuffer.position(pos);
                        myReadLineBuffer.get(dst);
                        properties.put(key, dst);                                                
                    } else {
                        SVNErrorMessage error = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, "File ''{0}'' contains unexpected binary property value", getFile());
                        SVNErrorManager.error(error, cce, SVNLogType.DEFAULT);
                    }
                }
            }
        } catch (IOException e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.MALFORMED_FILE);
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
        }
        return properties;
    }
    
    public Map readHeader() throws SVNException {
        Map map = new SVNHashMap();
        String line;
        while(true) {
            line = readLine(1024);
            if ("".equals(line)) {
                break;
            }
            int colonIndex = line.indexOf(':');
            if (colonIndex <= 0 || line.length() <= colonIndex + 2) {
                SVNDebugLog.getDefaultLog().logFine(SVNLogType.DEFAULT, line);
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, 
                        "Found malformed header in revision file");
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            } else if (line.charAt(colonIndex + 1) != ' ') {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.FS_CORRUPT, 
                        "Found malformed header in revision file");
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }
            String key = line.substring(0, colonIndex);
            String value = line.substring(colonIndex + 2);
            map.put(key, value);
        }
        return map;
    }
    
    public int read() throws IOException {
        if (myInputStream == null || myPosition < myBufferPosition || myPosition >= myBufferPosition + myBuffer.limit()) {
            if (fill() <= 0) {
                return -1;
            }
        } else {
            myBuffer.position((int) (myPosition - myBufferPosition));
        }
        int r = (myBuffer.get() & 0xFF);
        if (myDigest != null) {
            myDigest.update((byte) r);
        }
        myPosition++;
        return r;
    }

    public int read(ByteBuffer target) throws IOException {
        int read = 0;
        while(target.hasRemaining()) {
            if (fill() < 0) {
                return read > 0 ? read : -1;
            }
            myBuffer.position((int) (myPosition - myBufferPosition));

            int couldRead = Math.min(myBuffer.remaining(), target.remaining());
            int readFrom = myBuffer.position() + myBuffer.arrayOffset();
            target.put(myBuffer.array(), readFrom, couldRead);
            if (myDigest != null) {
                myDigest.update(myBuffer.array(), readFrom, couldRead);
            }
            myPosition += couldRead;
            read += couldRead;
            myBuffer.position(myBuffer.position() + couldRead);
        }
        return read;
    }

    public int read(byte[] buffer, int offset, int length) throws IOException {
        int read = 0;
        int toRead = length;
        while(toRead > 0) {
            if (fill() < 0) {
                return read > 0 ? read : -1;
            }
            myBuffer.position((int) (myPosition - myBufferPosition));

            int couldRead = Math.min(myBuffer.remaining(), toRead);
            myBuffer.get(buffer, offset, couldRead);
            if (myDigest != null) {
                myDigest.update(buffer, offset, couldRead);
            }
            toRead -= couldRead;
            offset += couldRead;
            myPosition += couldRead;
            read += couldRead;
        }
        return read;
    }

    public File getFile() {
        return myFile;
    }

    public void close() {
//        if (myChannel != null) {
//            try {
//                myChannel.close();
//            } catch (IOException e) {}
//            SVNFileUtil.closeFile(myInputStream);
//            myChannel = null;
//            myInputStream = null;
//            myPosition = 0;
//            myDigest = null;
//        }
        if (myInputStream != null) {
            SVNFileUtil.closeFile(myInputStream);
            myInputStream = null;
            myPosition = 0;
            myDigest = null;
        }
        
    }
    
    private InputStream resetInputStream() throws IOException {
    	  if (myInputStream == null) {
    	      myInputStream = SVNFileUtil.createFileInputStream(myFile);
    	  } else {
    		  SVNFileUtil.closeFile(myInputStream);
    	      myInputStream = SVNFileUtil.createFileInputStream(myFile);
    	  }
    	  return myInputStream;
	}
    
    private int readFromInputStream(long fromPos, ByteBuffer buffer) throws IOException {
    	
    	resetInputStream().skip(fromPos);
    	
    	int capacity = buffer.capacity();

    	byte[] b;
    	
    	if (myFile.length() == -1) {
    		
        	b = new byte[capacity];
        	int readed = myInputStream.read(b);
    		
    		
    	} else {
        	int bufferSize = capacity;

        	//BUG: buffer limit by int!
			long longSize = myFile.length() - fromPos;
			
			if (longSize > capacity)
				bufferSize = capacity;
			else 
				bufferSize = (int)longSize;
    		
	    	b = new byte[bufferSize];
			myInputStream.read(b);
		
    	}
		buffer.put(b);
		return b.length;
    }
    
    private int fill() throws IOException {
        if (myInputStream == null || myPosition < myBufferPosition || myPosition >= myBufferPosition + myBuffer.limit()) {
            myBufferPosition = myPosition;
//            setPosition(myBufferPosition);
            myBuffer.clear();
            int read = readFromInputStream(myBufferPosition, myBuffer);
            myBuffer.position(0);
            myBuffer.limit(read >= 0 ? read : 0);
            return read;
        } 
        return 0;
    }
    
    private void allocateReadBuffer(int limit) {
        if (limit > myReadLineBuffer.capacity()) {
            myReadLineBuffer = ByteBuffer.allocate(limit*3/2);
        }
        myReadLineBuffer.clear();
        myReadLineBuffer.limit(limit);
    }
    
//    private FileChannel getChannel() throws IOException {
//        if (myChannel == null) {
//            myInputStream = SVNFileUtil.createFileInputStream(myFile);
//            myChannel = myInputStream.getChannel();
//        }
//        return myChannel;
//    }
    
    public PathInfo readPathInfoFromReportFile() throws IOException, SVNException {
        int firstByte = read();
        if (firstByte == -1 || firstByte == '-') {
            return null;
        }
        String path = readStringFromReportFile();
        String linkPath = read() == '+' ? readStringFromReportFile() : null;
        long revision = readRevisionFromReportFile();
        SVNDepth depth = SVNDepth.INFINITY;
        if (read() == '+') {
            int id = read();
            switch(id) {
                case 'X':
                    depth = SVNDepth.EXCLUDE;
                    break;
                case 'E':
                    depth = SVNDepth.EMPTY;
                    break;
                case 'F':
                    depth = SVNDepth.FILES;
                    break;
                case 'M':
                    depth = SVNDepth.IMMEDIATES;
                    break;
                default: {
                    SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.REPOS_BAD_REVISION_REPORT, "Invalid depth ({0}) for path ''{1}''", new Object[]{new Integer(id), path});
                    SVNErrorManager.error(err, SVNLogType.WC);
                }
            }
        }
        boolean startEmpty = read() == '+';
        String lockToken = read() == '+' ? readStringFromReportFile() : null;
        return new PathInfo(path, linkPath, lockToken, revision, depth, startEmpty);
    }

    private String readStringFromReportFile() throws IOException {
        int length = readNumberFromReportFile();
        if (length == 0) {
            return "";
        }
        byte[] buffer = new byte[length];
        read(buffer, 0, length);
        return new String(buffer, "UTF-8");
    }

    private int readNumberFromReportFile() throws IOException {
        int b;
        ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
        while ((b = read()) != ':') {
            resultStream.write(b);
        }
        return Integer.parseInt(new String(resultStream.toByteArray(), "UTF-8"), 10);
    }

    private long readRevisionFromReportFile() throws IOException {
        if (read() == '+') {
            return readNumberFromReportFile();
        }
        return SVNRepository.INVALID_REVISION;
    }
    
}