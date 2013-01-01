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
package org.exist.versioning.svn;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import org.exist.util.io.Resource;
import org.exist.versioning.svn.internal.wc.SVNErrorManager;
import org.exist.versioning.svn.internal.wc.SVNEventFactory;
import org.exist.versioning.svn.internal.wc.SVNFileUtil;
import org.exist.versioning.svn.internal.wc.admin.SVNTranslatorInputStream;
import org.exist.versioning.svn.wc.ISVNEventHandler;
import org.exist.versioning.svn.wc.SVNEvent;
import org.exist.versioning.svn.wc.SVNEventAction;
import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNProperties;
import org.tmatesoft.svn.core.SVNProperty;
import org.tmatesoft.svn.core.SVNRevisionProperty;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNDate;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.io.ISVNFileRevisionHandler;
import org.tmatesoft.svn.core.io.SVNFileRevision;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.diff.SVNDeltaProcessor;
import org.tmatesoft.svn.core.io.diff.SVNDiffWindow;
import org.tmatesoft.svn.core.wc.ISVNAnnotateHandler;
import org.tmatesoft.svn.core.wc.SVNDiffOptions;
import org.tmatesoft.svn.util.SVNLogType;

import de.regnis.q.sequence.QSequenceDifferenceBlock;
import de.regnis.q.sequence.line.QSequenceLineMedia;
import de.regnis.q.sequence.line.QSequenceLineRAFileData;
import de.regnis.q.sequence.line.QSequenceLineResult;
import de.regnis.q.sequence.line.simplifier.QSequenceLineDummySimplifier;
import de.regnis.q.sequence.line.simplifier.QSequenceLineEOLUnifyingSimplifier;
import de.regnis.q.sequence.line.simplifier.QSequenceLineSimplifier;
import de.regnis.q.sequence.line.simplifier.QSequenceLineTeeSimplifier;
import de.regnis.q.sequence.line.simplifier.QSequenceLineWhiteSpaceReducingSimplifier;
import de.regnis.q.sequence.line.simplifier.QSequenceLineWhiteSpaceSkippingSimplifier;


/**
 * The <b>SVNAnnotationGenerator</b> class is used to annotate files - that is
 * to place author and revision information in-line for the specified
 * file.
 * 
 * <p>
 * Since <b>SVNAnnotationGenerator</b> implements <b>ISVNFileRevisionHandler</b>,
 * it is merely passed to a {@link org.tmatesoft.svn.core.io.SVNRepository#getFileRevisions(String, long, long, ISVNFileRevisionHandler) getFileRevisions()} 
 * method of <b>SVNRepository</b>. After that you handle the resultant annotated 
 * file line-by-line providing an <b>ISVNAnnotateHandler</b> implementation to the {@link #reportAnnotations(ISVNAnnotateHandler, String) reportAnnotations()}
 * method:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.SVNAnnotationGenerator;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.io.SVNRepositoryFactory;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.io.SVNRepository;
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.SVNAnnotateHandler;
 * ...
 * 
 *     File tmpFile;
 *     SVNRepository repos;
 *     ISVNAnnotateHandler annotateHandler;
 *     ISVNEventHandler cancelHandler;
 *     <span class="javakeyword">long</span> startRev = 0;
 *     <span class="javakeyword">long</span> endRev = 150;
 *     ...
 *     
 *     SVNAnnotationGenerator generator = <span class="javakeyword">new</span> SVNAnnotationGenerator(path, tmpFile, cancelHandler);
 *     <span class="javakeyword">try</span> {
 *         repos.getFileRevisions(<span class="javastring">""</span>, startRev, endRev, generator);
 *         generator.reportAnnotations(annotateHandler, <span class="javakeyword">null</span>);
 *     } <span class="javakeyword">finally</span> {
 *         generator.dispose();
 *     }
 * ...</pre>
 *   
 * @version 1.3
 * @since   1.2
 * @author  TMate Software Ltd.
 */
public class SVNAnnotationGenerator implements ISVNFileRevisionHandler {

    private File myTmpDirectory;
    private boolean myIsTmpDirCreated;
    private String myPath;

    private long myCurrentRevision;
    private String myCurrentAuthor;
    private Date myCurrentDate;
    private boolean myIsCurrentResultOfMerge;
    private String myCurrentPath;
    
    private File myPreviousFile;
    private File myPreviousOriginalFile;
    private File myCurrentFile;

    private List myMergeBlameChunks;
    private List myBlameChunks;
    private SVNDeltaProcessor myDeltaProcessor;
    private ISVNEventHandler myCancelBaton;
    private long myStartRevision;
    private boolean myIsForce;
    private boolean myIncludeMergedRevisions;
    private SVNDiffOptions myDiffOptions;
    private QSequenceLineSimplifier mySimplifier;
    private ISVNAnnotateHandler myFileHandler;
    private String myEncoding;
    private boolean myIsLastRevisionReported;
    
    /**
     * Constructs an annotation generator object. 
     * 
     * <p>
     * This constructor is equivalent to 
     * <code>SVNAnnotationGenerator(path, tmpDirectory, startRevision, false, cancelBaton)</code>.
     * 
     * @param path           a file path (relative to a repository location)
     * @param tmpDirectory   a revision to stop at
     * @param startRevision  a start revision to begin annotation with
     * @param cancelBaton    a baton which is used to check if an operation 
     *                       is cancelled
     */
    public SVNAnnotationGenerator(String path, File tmpDirectory, long startRevision, ISVNEventHandler cancelBaton) {
        this(path, tmpDirectory, startRevision, false, cancelBaton);
        
    }
    
    /**
     * Constructs an annotation generator object. 
     * 
     * <p/>
     * This constructor is identical to <code>SVNAnnotationGenerator(path, tmpDirectory, startRevision, force, new SVNDiffOptions(), cancelBaton)</code>.
     * 
     * @param path           a file path (relative to a repository location)
     * @param tmpDirectory   a revision to stop at
     * @param startRevision  a start revision to begin annotation with
     * @param force          forces binary files processing  
     * @param cancelBaton    a baton which is used to check if an operation 
     *                       is cancelled
     */
    public SVNAnnotationGenerator(String path, File tmpDirectory, long startRevision, boolean force, ISVNEventHandler cancelBaton) {
        this(path, tmpDirectory, startRevision, force, new SVNDiffOptions(), cancelBaton);
    }

    /**
     * Constructs an annotation generator object.
     * 
     * <p/>
     * This constructor is identical to <code>SVNAnnotationGenerator(path, tmpDirectory, startRevision, force, false, diffOptions, null, null, cancelBaton)</code>.
     * 
     * @param path           a file path (relative to a repository location)
     * @param tmpDirectory   a revision to stop at
     * @param startRevision  a start revision to begin annotation with
     * @param force          forces binary files processing  
     * @param diffOptions    diff options 
     * @param cancelBaton    a baton which is used to check if an operation 
     *                       is cancelled
     */
    public SVNAnnotationGenerator(String path, File tmpDirectory, long startRevision, boolean force, SVNDiffOptions diffOptions, ISVNEventHandler cancelBaton) {
        this(path, tmpDirectory, startRevision, force, false, diffOptions, null, null, cancelBaton);
    }

    /**
     * Constructs an annotation generator object.
     * 
     * @param path                    a file path (relative to a repository location)
     * @param tmpDirectory            a revision to stop at
     * @param startRevision           a start revision to begin annotation with
     * @param force                   forces binary files processing  
     * @param includeMergedRevisions  whether to include merged revisions or not
     * @param diffOptions             diff options 
     * @param encoding                charset name to use to encode annotation result
     * @param handler                 caller's annotation handler implementation 
     * @param cancelBaton             a baton which is used to check if an operation 
     *                                is cancelled
     * @since                         1.2.0 
     */
    public SVNAnnotationGenerator(String path, File tmpDirectory, long startRevision, boolean force, boolean includeMergedRevisions, 
            SVNDiffOptions diffOptions, String encoding, ISVNAnnotateHandler handler, ISVNEventHandler cancelBaton) {
        myTmpDirectory = tmpDirectory;
        myCancelBaton = cancelBaton;
        myPath = path;
        myIsForce = force;
        
        // TODO fail if file has been specified.
        if (!myTmpDirectory.isDirectory()) {
            myTmpDirectory.mkdirs();
            myIsTmpDirCreated = true;
        }
        myMergeBlameChunks = new ArrayList();
        myBlameChunks = new ArrayList();
        myDeltaProcessor = new SVNDeltaProcessor();
        myStartRevision = startRevision;
        myDiffOptions = diffOptions;
        myIncludeMergedRevisions = includeMergedRevisions;
        myFileHandler = handler;
        myEncoding = encoding;
    }
    
    /**
     * Handles a next revision.
     * @param fileRevision
     * @throws SVNException if one of the following occurs:
     *                      <ul>
     *                      <li>exception with {@link SVNErrorCode#CLIENT_IS_BINARY_FILE} error code - if the file is binary and no 
     *                      forcing is specified 
     *                      <li>operation is cancelled
     *                      </ul>
     */
    public void openRevision(SVNFileRevision fileRevision) throws SVNException {
        SVNProperties propDiff = fileRevision.getPropertiesDelta();
        String newMimeType = propDiff != null ? propDiff.getStringValue(SVNProperty.MIME_TYPE) : null;
        if (!myIsForce && SVNProperty.isBinaryMimeType(newMimeType)) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.CLIENT_IS_BINARY_FILE, "Cannot calculate blame information for binary file ''{0}''", myPath);
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        myCurrentRevision = fileRevision.getRevision();
        boolean known = fileRevision.getRevision() >= myStartRevision;
        if (myCancelBaton != null) {
            File file = SVNPathUtil.isURL(myPath) ? null : new Resource(myPath);
            SVNEvent event = SVNEventFactory.createSVNEvent(file, SVNNodeKind.NONE, null, myCurrentRevision, SVNEventAction.ANNOTATE, null, null, null);
            if (file == null) {
                event.setURL(SVNURL.parseURIDecoded(myPath));
            }
            myCancelBaton.handleEvent(event, ISVNEventHandler.UNKNOWN);
            myCancelBaton.checkCancelled();
        }
        SVNProperties props = fileRevision.getRevisionProperties();
        if (known && props != null && props.getStringValue(SVNRevisionProperty.AUTHOR) != null) {
            myCurrentAuthor = props.getStringValue(SVNRevisionProperty.AUTHOR);
        } else {
            myCurrentAuthor = null;
        }
        if (known && props != null && props.getStringValue(SVNRevisionProperty.DATE) != null) {
            myCurrentDate = SVNDate.parseDate(fileRevision.getRevisionProperties().getStringValue(SVNRevisionProperty.DATE));
        } else {
            myCurrentDate = null;
        }
        
        myIsCurrentResultOfMerge = fileRevision.isResultOfMerge();
        if (myIncludeMergedRevisions) {
            myCurrentPath = fileRevision.getPath();
        }
    }
    
    /**
     * Does nothing.
     * 
     * @param token       
     * @throws SVNException
     */
    public void closeRevision(String token) throws SVNException {
    }
    
    /**
     * Creates a temporary file for delta application.
     * 
     * @param  token             not used in this method 
     * @param  baseChecksum      not used in this method
     * @throws SVNException 
     */
    public void applyTextDelta(String token, String baseChecksum) throws SVNException {
        if (myCurrentFile == null) {
            myCurrentFile = SVNFileUtil.createUniqueFile(myTmpDirectory, "annotate", ".tmp", false);
        }
        myDeltaProcessor.applyTextDelta(myPreviousFile, myCurrentFile, false);
    }

    /**
     * Applies a next text delta chunk.
     *  
     * @param  token          not used in this method 
     * @param  diffWindow     next diff window 
     * @return                dummy output stream
     * @throws SVNException 
     */
    public OutputStream textDeltaChunk(String token, SVNDiffWindow diffWindow) throws SVNException {
        return myDeltaProcessor.textDeltaChunk(diffWindow);
    }
    
    /**
     * Marks the end of the text delta series.
     * @param token          not used in this method
     * @throws SVNException 
     */
    public void textDeltaEnd(String token) throws SVNException {
	    myIsLastRevisionReported = false;
        myDeltaProcessor.textDeltaEnd();
        
        if (myIncludeMergedRevisions) {
            myMergeBlameChunks = addFileBlame(myPreviousFile, myCurrentFile, myMergeBlameChunks);
            if (!myIsCurrentResultOfMerge) {
                myBlameChunks = addFileBlame(myPreviousOriginalFile, myCurrentFile, myBlameChunks);
                if (myPreviousOriginalFile == null) {
                    myPreviousOriginalFile = myCurrentFile;
                    myCurrentFile = null;
                } else {
                    SVNFileUtil.rename(myCurrentFile, myPreviousOriginalFile);    
                }
                
                myPreviousFile = myPreviousOriginalFile;
            } else {
                if (myPreviousFile != null && myPreviousFile != myPreviousOriginalFile) {
                    SVNFileUtil.rename(myCurrentFile, myPreviousFile);    
                } else {
                    myPreviousFile = myCurrentFile;
                    myCurrentFile = null;
                }
            }
        } else {
            myBlameChunks = addFileBlame(myPreviousFile, myCurrentFile, myBlameChunks);
            if (myPreviousFile == null) {
                myPreviousFile = myCurrentFile;
                myCurrentFile = null;
            } else {
                SVNFileUtil.rename(myCurrentFile, myPreviousFile);
            }
        }

        if (myFileHandler != null) {
            boolean generate = myFileHandler.handleRevision(myCurrentDate, myCurrentDate != null ? myCurrentRevision : -1, myCurrentAuthor, myPreviousFile);
            if (generate) {
                myIsLastRevisionReported = true;
                reportAnnotations(myFileHandler, myEncoding);
            }
        }
    }
    
    /**
     * This method is used by <code>SVNKit</code> internals and is not intended for API users.
     * @return whether the last revision was reported or not yet
     * @since  1.2.0
     */
    public boolean isLastRevisionReported() {
        return myIsLastRevisionReported;
    }
       
    /**
     * Dispatches file lines along with author & revision info to the provided
     * annotation handler.  
     * 
     * <p>
     * If <code>inputEncoding</code> is <span class="javakeyword">null</span> then 
     * <span class="javastring">"file.encoding"</span> system property is used. 
     * 
     * @param  handler        an annotation handler that processes file lines with
     *                        author & revision info
     * @param  inputEncoding  a desired character set (encoding) of text lines
     * @throws SVNException
     */
    public void reportAnnotations(ISVNAnnotateHandler handler, String inputEncoding) throws SVNException {
        if (handler == null) {
            return;
        }

        if (myPreviousFile == null) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, 
                    "ASSERTION FAILURE in SVNAnnotationGenerator.reportAnnotations(): myPreviousFile is null, " +
                    "generator has to have been called at least once");
            SVNErrorManager.error(err, SVNLogType.DEFAULT);
        }
        int mergedCount = -1;
        if (myIncludeMergedRevisions) {
            if (myBlameChunks.isEmpty()) {
                BlameChunk chunk = new BlameChunk();
                chunk.blockStart = 0;
                chunk.author = myCurrentAuthor;
                chunk.date = myCurrentDate;
                chunk.revision = myCurrentRevision;
                chunk.path = myCurrentPath;
                myBlameChunks.add(chunk);
            }
            normalizeBlames(myBlameChunks, myMergeBlameChunks);
            mergedCount = 0;
        }
        
        inputEncoding = inputEncoding == null ? System.getProperty("file.encoding") : inputEncoding;
        CharsetDecoder decoder = Charset.forName(inputEncoding).newDecoder();

        InputStream stream = null;
        try {
            stream = new SVNTranslatorInputStream(SVNFileUtil.openFileForReading(myPreviousFile), 
                                                  SVNProperty.EOL_LF_BYTES, true, null, false);
            
            StringBuffer buffer = new StringBuffer();
            for (int i = 0; i < myBlameChunks.size(); i++) {
                BlameChunk chunk = (BlameChunk) myBlameChunks.get(i);
                String mergedAuthor = null;
                long mergedRevision = SVNRepository.INVALID_REVISION;
                Date mergedDate = null;
                String mergedPath = null;
                if (mergedCount >= 0) {
                    BlameChunk mergedChunk = (BlameChunk) myMergeBlameChunks.get(mergedCount++);
                    mergedAuthor = mergedChunk.author;
                    mergedRevision = mergedChunk.revision;
                    mergedDate = mergedChunk.date;
                    mergedPath = mergedChunk.path;
                }
                
                BlameChunk nextChunk = null;
                if (i < myBlameChunks.size() - 1) {
                    nextChunk = (BlameChunk) myBlameChunks.get(i + 1);
                }
                
                for (int lineNo = chunk.blockStart; nextChunk == null || lineNo < nextChunk.blockStart; lineNo++) {
                    myCancelBaton.checkCancelled();
                    buffer.setLength(0);
                    String line = SVNFileUtil.readLineFromStream(stream, buffer, decoder);
                    boolean isEOF = false;
                    if (line == null) {
                        isEOF = true;
                        if (buffer.length() > 0) {
                            line = buffer.toString();
                        }
                    }
                    
                    if (!isEOF || line != null) {
                        handler.handleLine(chunk.date, chunk.revision, chunk.author, 
                                           line, mergedDate, mergedRevision, mergedAuthor, 
                                           mergedPath, lineNo);
                    }                    
                    if (isEOF) {
                        break;
                    }
                }
            }
            handler.handleEOF();
        } catch (IOException ioe) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.IO_ERROR, ioe.getLocalizedMessage());
            SVNErrorManager.error(err, ioe, SVNLogType.DEFAULT);
        } finally {
            SVNFileUtil.closeFile(stream);
        }
    }

    /**
     * Finalizes an annotation operation releasing resources involved
     * by this generator. Should be called after {@link #reportAnnotations(ISVNAnnotateHandler, String) reportAnnotations()}. 
     *
     */
    public void dispose() {
        myIsCurrentResultOfMerge = false;
        if (myCurrentFile != null) {
            SVNFileUtil.deleteAll(myCurrentFile, true);
        }
        if (myPreviousFile != null) {
            SVNFileUtil.deleteAll(myPreviousFile, true);
            myPreviousFile = null;
        }
        if (myPreviousOriginalFile != null) {
            SVNFileUtil.deleteAll(myPreviousOriginalFile, true);
            myPreviousOriginalFile = null;
        }
        if (myIsTmpDirCreated) {
            SVNFileUtil.deleteAll(myTmpDirectory, true);
        }        
        myBlameChunks.clear();
        myMergeBlameChunks.clear();
    }

    private List addFileBlame(File previousFile, File currentFile, List chain) throws SVNException {
        if (previousFile == null) {
            BlameChunk chunk = new BlameChunk();
            chunk.author = myCurrentAuthor;
            chunk.revision = myCurrentDate != null ? myCurrentRevision : -1;
            chunk.date = myCurrentDate;
            chunk.blockStart = 0;
            chunk.path = myCurrentPath;
            chain.add(chunk);
            return chain;
        }
        
        RandomAccessFile left = null;
        RandomAccessFile right = null;
        try {
            left = new RandomAccessFile(previousFile, "r");
            right = new RandomAccessFile(currentFile, "r");

            final QSequenceLineResult result = QSequenceLineMedia.createBlocks(new QSequenceLineRAFileData(left), new QSequenceLineRAFileData(right), createSimplifier());
            try {
                List blocksList = result.getBlocks();
                for(int i = 0; i < blocksList.size(); i++) {
                    QSequenceDifferenceBlock block = (QSequenceDifferenceBlock) blocksList.get(i);
                    if (block.getLeftSize() > 0) {
                        deleteBlameChunk(block.getRightFrom(), block.getLeftSize(), chain);
                    }
                    if (block.getRightSize() > 0) {
                        insertBlameChunk(myCurrentRevision, myCurrentAuthor, 
                                         myCurrentDate, myCurrentPath, 
                                         block.getRightFrom(), block.getRightSize(), chain);
                    }
                }
            } finally {
                result.close();
            }
        } catch (Throwable e) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Exception while generating annotation: {0}", e.getMessage());
            SVNErrorManager.error(err, e, SVNLogType.DEFAULT);
        } finally {
            if (left != null) {
                SVNFileUtil.closeFile(left);
            }
            if (right != null) {
                SVNFileUtil.closeFile(right);
            }
        }

        return chain;
    }
    
    private void insertBlameChunk(long revision, String author, Date date, String path, int start, int length, List chain) {
        int[] index = new int[1];
        BlameChunk startPoint = findBlameChunk(chain, start, index);
        int adjustFromIndex = -1;
        if (startPoint.blockStart == start) {
            BlameChunk insert = new BlameChunk();
            insert.copy(startPoint);
            insert.blockStart = start + length;
            chain.add(index[0] + 1, insert);

            startPoint.author = author;
            startPoint.revision = revision;
            startPoint.date = date;
            startPoint.path = path;
            adjustFromIndex = index[0] + 2;
        } else {
            BlameChunk middle = new BlameChunk();
            middle.author = author;
            middle.revision = revision;
            middle.date = date;
            middle.path = path;
            middle.blockStart = start;
            
            BlameChunk insert = new BlameChunk();
            insert.copy(startPoint);
            insert.blockStart = start + length;
            chain.add(index[0] + 1, middle);
            chain.add(index[0] + 2, insert);
            adjustFromIndex = index[0] + 3;
        }
        
        adjustBlameChunks(chain, adjustFromIndex, length);
    }
    
    private void deleteBlameChunk(int start, int length, List chain) {
        int[] ind = new int[1];
        
        BlameChunk first = findBlameChunk(chain, start, ind);
        int firstInd = ind[0];
        
        BlameChunk last = findBlameChunk(chain, start + length, ind);
        int lastInd = ind[0];
        
        if (first != last) {
            int deleteCount = lastInd - firstInd - 1;
            for (int i = 0; i < deleteCount; i++) {
                chain.remove(firstInd + 1);
            }
            lastInd -= deleteCount;
            
            last.blockStart = start;
            if (first.blockStart == start) {
                first.copy(last);
                chain.remove(lastInd);
                lastInd--;
                last = first;
            }
        }

        int tailInd = lastInd < chain.size() - 1 ? lastInd + 1 : -1;
        BlameChunk tail = tailInd > 0 ? (BlameChunk)chain.get(tailInd) : null;

        if (tail != null && tail.blockStart == last.blockStart + length) {
            last.copy(tail);
            chain.remove(tail);
            tailInd--;
            tail = last;
        }
        
        if (tail != null) {
            adjustBlameChunks(chain, tailInd, -length);
        }
    }
    
    private void adjustBlameChunks(List chain, int startIndex, int adjust) {
        for (int i = startIndex; i < chain.size(); i++) {
            BlameChunk curChunk = (BlameChunk) chain.get(i);
            curChunk.blockStart += adjust;
        }
    }
    
    private BlameChunk findBlameChunk(List chain, int offset, int[] index) {
        BlameChunk prevChunk = null;
        index[0] = -1; 
        for (Iterator chunks = chain.iterator(); chunks.hasNext();) {
            BlameChunk chunk = (BlameChunk) chunks.next();
            if (chunk.blockStart > offset) {
                break;
            }
            prevChunk = chunk;
            index[0]++;
        }
        return prevChunk;
    }
    
    private void normalizeBlames(List chain, List mergedChain) throws SVNException {
        int i = 0, k = 0;
        for (; i < chain.size() - 1 && k < mergedChain.size() - 1; i++, k++) {
            BlameChunk chunk = (BlameChunk) chain.get(i);
            BlameChunk mergedChunk = (BlameChunk) mergedChain.get(k);
            if (chunk.blockStart != mergedChunk.blockStart) {
                SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN,                               
                        "ASSERTION FAILURE in SVNAnnotationGenerator.normalizeBlames():" +
                        "current chunks should always start at the same offset");
                SVNErrorManager.error(err, SVNLogType.DEFAULT);
            }

            BlameChunk nextChunk = (BlameChunk) chain.get(i + 1);
            BlameChunk nextMergedChunk = (BlameChunk) mergedChain.get(k + 1);
            if (nextChunk.blockStart < nextMergedChunk.blockStart) {
                BlameChunk tmpChunk = new BlameChunk();
                tmpChunk.copy(mergedChunk);
                tmpChunk.blockStart = nextChunk.blockStart;
                mergedChain.add(k + 1, tmpChunk);
                nextMergedChunk = tmpChunk;
            }
            if (nextChunk.blockStart > nextMergedChunk.blockStart) {
                BlameChunk tmpChunk = new BlameChunk();
                tmpChunk.copy(chunk);
                tmpChunk.blockStart = nextMergedChunk.blockStart;
                chain.add(i + 1, tmpChunk);
            }
        }

        if ((i == chain.size() - 1) && (k == mergedChain.size() - 1)) {
            return;
        }
        
        if (k == mergedChain.size() - 1) {
            for (i += 1; i < chain.size(); i++) {
                BlameChunk chunk = (BlameChunk) chain.get(i);
                BlameChunk mergedChunk = (BlameChunk) mergedChain.get(mergedChain.size() - 1);

                BlameChunk insert = new BlameChunk();
                insert.copy(mergedChunk);
                insert.blockStart = chunk.blockStart;
                mergedChain.add(insert);
                k++;
            }
        }

        if (i == chain.size() - 1) {
            for (k += 1; k < mergedChain.size(); k++) {
                BlameChunk mergedChunk = (BlameChunk) mergedChain.get(k);
                BlameChunk chunk = (BlameChunk) chain.get(chain.size() - 1);

                BlameChunk insert = new BlameChunk();
                insert.copy(chunk);
                insert.blockStart = mergedChunk.blockStart;
                chain.add(insert);
                i++;
            }
        }
    }
    
    private QSequenceLineSimplifier createSimplifier() {
        if (mySimplifier == null) {
            QSequenceLineSimplifier first = myDiffOptions.isIgnoreEOLStyle() ? 
                    (QSequenceLineSimplifier) new QSequenceLineEOLUnifyingSimplifier() :
                    (QSequenceLineSimplifier) new QSequenceLineDummySimplifier();
            QSequenceLineSimplifier second = new QSequenceLineDummySimplifier();
            if (myDiffOptions.isIgnoreAllWhitespace()) {
                second = new QSequenceLineWhiteSpaceSkippingSimplifier();
            } else if (myDiffOptions.isIgnoreAmountOfWhitespace()) {
                second = new QSequenceLineWhiteSpaceReducingSimplifier();
            }
            mySimplifier = new QSequenceLineTeeSimplifier(first, second);
        }
        return mySimplifier;
    }

    private static class BlameChunk {
        public int blockStart;
        public long revision;
        public String author;
        public Date date;
        public String path;
        
        public void copy(BlameChunk chunk) {
            author = chunk.author;
            date = chunk.date;
            revision = chunk.revision;
            path = chunk.path;
            blockStart = chunk.blockStart;
        }
        
        public String toString() {
            StringBuffer buf = new StringBuffer();
            buf.append("\n----\nPath: " + path);
            buf.append("\nRevision: " + revision);
            buf.append("\nAuthor: " + author);
            buf.append("\nDate: " + SVNDate.formatConsoleShortDate(date));
            buf.append("\nBlock start: " + blockStart);
            buf.append("\n");
            return buf.toString();
        }
    }
    
}
