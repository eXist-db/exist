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

package org.exist.versioning.svn;

import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

import org.tmatesoft.svn.core.SVNCommitInfo;
import org.tmatesoft.svn.core.SVNDepth;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.wc.SVNClientManager;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc.SVNUpdateClient;
import org.tmatesoft.svn.core.wc.admin.SVNAdminClient;


/**
 * @version 1.3
 * @author  TMate Software Ltd.
 */
public class SamplesUtility {
    
    private static final String IOTA = "iota";
    private static final String MU = "mu";
    private static final String LAMBDA = "lambda";
    private static final String ALPHA = "alpha";
    private static final String BETA = "beta";
    private static final String GAMMA = "gamma";
    private static final String PI = "pi";
    private static final String RHO = "rho";
    private static final String TAU = "tau";
    private static final String CHI = "chi";
    private static final String OMEGA = "omega";
    private static final String PSI = "psi";
        
    private static final Map<String, String> ourGreekTreeFiles = new HashMap<String, String>();
    
    static {
        ourGreekTreeFiles.put(IOTA, "This is the file 'iota'.\n");
        ourGreekTreeFiles.put(MU, "This is the file 'A/mu'.\n");
        ourGreekTreeFiles.put(LAMBDA, "This is the file 'A/B/lambda'.\n");
        ourGreekTreeFiles.put(ALPHA, "This is the file 'A/B/E/alpha'.\n");
        ourGreekTreeFiles.put(BETA, "This is the file 'A/B/E/beta'.\n");
        ourGreekTreeFiles.put(GAMMA, "This is the file 'A/D/gamma'.\n");
        ourGreekTreeFiles.put(PI, "This is the file 'A/D/G/pi'.\n");
        ourGreekTreeFiles.put(RHO, "This is the file 'A/D/G/rho'.\n");
        ourGreekTreeFiles.put(TAU, "This is the file 'A/D/G/tau'.\n");
        ourGreekTreeFiles.put(CHI, "This is the file 'A/D/H/chi'.\n");
        ourGreekTreeFiles.put(OMEGA, "This is the file 'A/D/H/omega'.\n");
        ourGreekTreeFiles.put(PSI, "This is the file 'A/D/H/psi'.\n");
    }
    
    public static void createRepository(File reposRoot) throws SVNException {
        SVNAdminClient adminClient = SVNClientManager.newInstance().getAdminClient();
        adminClient.doCreateRepository(reposRoot, null, true, false, false, false);
    }
    
    /**
     * Here we use the SVNKit low-level API to create a tree in a repository entirely from our code, 
     * without a working copy at all.
     * 
     * The resultant tree committed into the repository will look like this:
     * 
     *                                    Repository Root
     *                                       ____|____
     *                                      /         \
     *                                     /           \
     *                                    /             \
     *                                   A             iota
     *          _______________________//|\
     *         /         ______________/ | \_____________
     *        mu        /                |               \
     *                 /                 |                \
     *                B                  C                 D
     *          _____/|\_____                _____________/|\
     *         /      |      \              /              | \
     *        /       |       \            /              /   \
     *     lambda     |        F          /              /     \
     *                E                gamma            /       \
     *               / \                               /         \ 
     *              /   \                     ________/           \________
     *          alpha   beta                 /                             \           
     *                                      /                               \
     *                                     /                                 \
     *                                    G                                   H
     *                           ________/|\_______                   _______/|\______
     *                          /         |        \                 /        |       \                       
     *                         /          |         \               /         |        \
     *                        /           |          \             /          |         \
     *                       pi          rho         tau          chi        psi       omega
     * 
     * 
     * @param reposRoot repository root location; will be converted into a file:/// url to the repository.  
     */
    public static SVNCommitInfo createRepositoryTree(File reposRoot) throws SVNException {
        SVNURL reposURL = SVNURL.fromFile(reposRoot);
        SVNRepository repos = SVNRepositoryFactory.create(reposURL);
        
        ISVNEditor commitEditor = repos.getCommitEditor("initializing the repository with a greek tree", 
                null, false, null, null);

        SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
        
        commitEditor.openRoot(SVNRepository.INVALID_REVISION);

        //add /iota file
        commitEditor.addFile(IOTA, null, SVNRepository.INVALID_REVISION);
        commitEditor.applyTextDelta(IOTA, null);
        String fileText = (String) ourGreekTreeFiles.get(IOTA);
        String checksum = deltaGenerator.sendDelta(IOTA, new ByteArrayInputStream(fileText.getBytes()), 
                commitEditor, true);
        commitEditor.closeFile(IOTA, checksum);
        
        //add /A directory
        commitEditor.addDir("A", null, SVNRepository.INVALID_REVISION);
        
        //add /A/mu file
        String muPath = "A/" + MU;
        commitEditor.addFile(muPath, null, SVNRepository.INVALID_REVISION);
        commitEditor.applyTextDelta(muPath, null);
        fileText = (String) ourGreekTreeFiles.get(MU);
        checksum = deltaGenerator.sendDelta(muPath, new ByteArrayInputStream(fileText.getBytes()), commitEditor, 
                true);
        commitEditor.closeFile(muPath, checksum);
        
        commitEditor.addDir("A/B", null, SVNRepository.INVALID_REVISION);
        
        //add /A/B/lambda file
        String lambdaPath = "A/B/" + LAMBDA;
        commitEditor.addFile(lambdaPath, null, SVNRepository.INVALID_REVISION);
        commitEditor.applyTextDelta(lambdaPath, null);
        fileText = (String) ourGreekTreeFiles.get(LAMBDA);
        checksum = deltaGenerator.sendDelta(lambdaPath, new ByteArrayInputStream(fileText.getBytes()), commitEditor, 
                true);
        commitEditor.closeFile(lambdaPath, checksum);
        
        commitEditor.addDir("A/B/E", null, SVNRepository.INVALID_REVISION);

        //add /A/B/E/alpha file
        String alphaPath = "A/B/E/" + ALPHA;
        commitEditor.addFile(alphaPath, null, SVNRepository.INVALID_REVISION);
        commitEditor.applyTextDelta(alphaPath, null);
        fileText = (String) ourGreekTreeFiles.get(ALPHA);
        checksum = deltaGenerator.sendDelta(alphaPath, new ByteArrayInputStream(fileText.getBytes()), commitEditor, 
                true);
        commitEditor.closeFile(alphaPath, checksum);
        
        //add /A/B/E/beta file
        String betaPath = "A/B/E/" + BETA;
        commitEditor.addFile(betaPath, null, SVNRepository.INVALID_REVISION);
        commitEditor.applyTextDelta(betaPath, null);
        fileText = (String) ourGreekTreeFiles.get(BETA);
        checksum = deltaGenerator.sendDelta(betaPath, new ByteArrayInputStream(fileText.getBytes()), commitEditor, 
                true);
        commitEditor.closeFile(betaPath, checksum);
        
        //close /A/B/E
        commitEditor.closeDir();
        
        //add /A/B/F
        commitEditor.addDir("A/B/F", null, SVNRepository.INVALID_REVISION);
        
        //close /A/B/F
        commitEditor.closeDir();
        
        //close /A/B
        commitEditor.closeDir();

        //add /A/C
        commitEditor.addDir("A/C", null, SVNRepository.INVALID_REVISION);
        
        //close /A/C
        commitEditor.closeDir();

        //add /A/D
        commitEditor.addDir("A/D", null, SVNRepository.INVALID_REVISION);
        
        //add /A/D/gamma file
        String gammaPath = "A/D/" + GAMMA;
        commitEditor.addFile(gammaPath, null, SVNRepository.INVALID_REVISION);
        commitEditor.applyTextDelta(gammaPath, null);
        fileText = (String) ourGreekTreeFiles.get(GAMMA);
        checksum = deltaGenerator.sendDelta(gammaPath, new ByteArrayInputStream(fileText.getBytes()), commitEditor, 
                true);
        commitEditor.closeFile(gammaPath, checksum);
        
        //add /A/D/G
        commitEditor.addDir("A/D/G", null, SVNRepository.INVALID_REVISION);
        
        //add /A/D/G/pi file
        String piPath = "A/D/G/" + PI;
        commitEditor.addFile(piPath, null, SVNRepository.INVALID_REVISION);
        commitEditor.applyTextDelta(piPath, null);
        fileText = (String) ourGreekTreeFiles.get(PI);
        checksum = deltaGenerator.sendDelta(piPath, new ByteArrayInputStream(fileText.getBytes()), commitEditor, 
                true);
        //close /A/D/G/pi file
        commitEditor.closeFile(piPath, checksum);

        //add /A/D/G/rho file
        String rhoPath = "A/D/G/" + RHO;
        commitEditor.addFile(rhoPath, null, SVNRepository.INVALID_REVISION);
        commitEditor.applyTextDelta(rhoPath, null);
        fileText = (String) ourGreekTreeFiles.get(RHO);
        checksum = deltaGenerator.sendDelta(rhoPath, new ByteArrayInputStream(fileText.getBytes()), commitEditor, 
                true);
        //close /A/D/G/rho file
        commitEditor.closeFile(rhoPath, checksum);

        //add /A/D/G/tau file
        String tauPath = "A/D/G/" + TAU;
        commitEditor.addFile(tauPath, null, SVNRepository.INVALID_REVISION);
        commitEditor.applyTextDelta(tauPath, null);
        fileText = (String) ourGreekTreeFiles.get(TAU);
        checksum = deltaGenerator.sendDelta(tauPath, new ByteArrayInputStream(fileText.getBytes()), commitEditor, 
                true);
        //close /A/D/G/tau file
        commitEditor.closeFile(tauPath, checksum);
        
        //close /A/D/G
        commitEditor.closeDir();

        //add /A/D/H
        commitEditor.addDir("A/D/H", null, SVNRepository.INVALID_REVISION);

        //add /A/D/H/chi file
        String chiPath = "A/D/H/" + CHI;
        commitEditor.addFile(chiPath, null, SVNRepository.INVALID_REVISION);
        commitEditor.applyTextDelta(chiPath, null);
        fileText = (String) ourGreekTreeFiles.get(CHI);
        checksum = deltaGenerator.sendDelta(chiPath, new ByteArrayInputStream(fileText.getBytes()), commitEditor, 
                true);
        //close /A/D/H/chi file
        commitEditor.closeFile(chiPath, checksum);

        //add /A/D/H/omega file
        String omegaPath = "A/D/H/" + OMEGA;
        commitEditor.addFile(omegaPath, null, SVNRepository.INVALID_REVISION);
        commitEditor.applyTextDelta(omegaPath, null);
        fileText = (String) ourGreekTreeFiles.get(OMEGA);
        checksum = deltaGenerator.sendDelta(omegaPath, new ByteArrayInputStream(fileText.getBytes()), commitEditor, 
                true);
        //close /A/D/H/omega file
        commitEditor.closeFile(omegaPath, checksum);

        //add /A/D/H/psi file
        String psiPath = "A/D/H/" + PSI;
        commitEditor.addFile(psiPath, null, SVNRepository.INVALID_REVISION);
        commitEditor.applyTextDelta(psiPath, null);
        fileText = (String) ourGreekTreeFiles.get(PSI);
        checksum = deltaGenerator.sendDelta(psiPath, new ByteArrayInputStream(fileText.getBytes()), commitEditor, 
                true);
        //close /A/D/H/psi file
        commitEditor.closeFile(psiPath, checksum);

        //close /A/D/H
        commitEditor.closeDir();
        
        //close /A/D
        commitEditor.closeDir();
        
        //close /A
        commitEditor.closeDir();
        //close the root of the edit - / (repository root) in our case
        commitEditor.closeDir();
        
        return commitEditor.closeEdit();
    }
    
    public static void checkOutWorkingCopy(SVNURL url, File wcRoot) throws SVNException {
        SVNUpdateClient updateClient = SVNClientManager.newInstance().getUpdateClient();
        updateClient.doCheckout(url, wcRoot, SVNRevision.UNDEFINED, SVNRevision.HEAD, SVNDepth.INFINITY, 
                false);
    }
    
    public static void writeToFile(File file, String text, boolean append) throws IOException {
        if (file.getParentFile() != null && !file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        
        OutputStream outputStream = null;
        try {
            outputStream = new BufferedOutputStream(new FileOutputStream(file, append));
            outputStream.write(text.getBytes());
        } finally {
            if (outputStream != null) {
                outputStream.close();
            }
        }
    }
    
    public static void initializeFSFSprotocol() {
        /*
         * Call this setup method only once in you program and prior to using SVNKit.
         */
        FSRepositoryFactory.setup();
    }
}
