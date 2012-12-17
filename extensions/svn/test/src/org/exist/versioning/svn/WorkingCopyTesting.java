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

import static org.junit.Assert.fail;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.OutputStream;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.methods.PostMethod;
import org.exist.util.io.Resource;
import org.exist.xmldb.DatabaseInstanceManager;
import org.exist.xmldb.XmldbURI;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.util.SVNPathUtil;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.xmldb.api.DatabaseManager;
import org.xmldb.api.base.Collection;
import org.xmldb.api.base.Database;
import org.xmldb.api.base.XMLDBException;
import org.xmldb.api.modules.CollectionManagementService;

/*
 * This  is  a complex  example program that demonstrates how  you  can manage local
 * working copies as well as  URLs  (that is, items located  in  the  repository) by 
 * means of the  API  provided in the org.tmatesoft.svn.core.wc package. The package 
 * itself represents  a  high-level  API  consisting of classes and interfaces which 
 * allow to perform operations compatible with ones of the native Subversion command
 * line client.  These version control operations are logically grouped in a  set of 
 * classes which names meet  'SVN*Client'  pattern. For example, the package has the 
 * SVNUpdateClient class which is responsible for update-related operations (update,
 * switch, check out). Most of the  methods of these 'client' classes are named like
 * 'doSomething(..)' where 'Something' corresponds to the name of a version  control 
 * operation (usually similar to the name of the  corresponding  Subversion  command 
 * line client's command). So, for  users  familiar with the Subversion command line 
 * client it won't take much  effort and time  to  match a 'do*' method  against  an 
 * appropriate Subversion client's operation (or command, in other words).
 * 
 * Surely, it  may  seem  not  quite handy to deal with a number of classes that all 
 * need to be instantiated, initialized, kept.. For example, if a developer is going 
 * to use all (or several) of the SVN*Client classes and most of them will access  a 
 * repository (in that way when authentication is demanded), it becomes tiresome  to 
 * provide authentication info to every one of them. So, for managing purposes  like 
 * the previous one the  package  has got the class  called  SVNClientManager  whose 
 * get*Client() methods provide all necessary SVN*Client objects to a caller. 
 * 
 * A  developer once creates an instance of  SVNClientManager  providing (if needed) 
 * his  authentication  info/options (options  are  similar  to  the   SVN  run-time 
 * configuration settings that can be found in the config file) into an  appropriate 
 * SVNClientManager.newInstance(..) method. Further all SVN*Client objects  provided 
 * by the instance of SVNClientManager will use these authentication info/options.   
 *  
 * The program illustrates a  number  of  main  operations  usually carried out upon 
 * working copies and URLs. Here's a brief description  of  what  the  program  does 
 * (main steps):
 * 
 * 0)first of all initializes the  SVNKit  library  (it must be done prior to using 
 * the library);
 * 
 * 1)if the program was run with some in parameters, it fetches them out of the args 
 * parameter; the program expects the following input parameters: 
 * 
 * repositoryURL wcRootPath name password 
 * 
 * 2)next instantiates an SVNClientManager providing default options and  auth  info 
 * to it -  these parameters will be used by  all  SVN*Client  objects that will  be
 * created, kept and provided by the manager; default run-time options correspond to 
 * the client-side run-time settings found in the  'config'  file within the default 
 * SVN configuration area; also in this case the client manager will use the server-
 * side run-time settings found in the 'servers' file within the same area;
 * 
 * 3)the first operation  -  making an empty directory in a repository; that is like 
 * 'svn mkdir URL'  which  creates  a  new  directory  given  all  the  intermediate 
 * directories created); this operation is immediately committed to the repository;
 * 
 * 4)the next operation  - creating a new local directory (importDir) and a new file 
 * (importFile) in it and then importing the directory into the repository; 
 * 
 * 5)the next operation - checking out the directory created in the previous step to 
 * a local directory defined by the myWorkingCopyPath parameter ; 
 * 
 * 6)the next operation  -  recursively getting and displaying info for each item at 
 * the working revision in the working copy that was  checked  out  in  the previous 
 * step;
 * 
 * 7)the next operation - creating a new directory (newDir) with a file (newFile) in
 * the working copy and then  recursively  scheduling (if any subdirictories existed 
 * they would be also added:)) the created directory for addition;
 * 
 * 8)the next operation - recursively getting and displaying the working copy status
 * not including unchanged (normal) paths; the result must include those paths which
 * were scheduled for addition in the previous step; 
 * 
 * 9)the next operation  - recursively updating the working copy; if any local items
 * are out of date they will be updated to the latest revision;
 * 
 * 10)the next operation - committing local changes to the repository; this will add 
 * the directory with the file (that were  scheduled  for addition two steps before) 
 * to the repository;
 * 
 * 11)the next operation  -  locking  the  file  committed in the previous step (for 
 * example, if you temporarily need to keep a file locked to prevent someone's  else 
 * modifications);
 * 
 * 12)the next operation  -  showing status once again (here, to see that  the  file 
 * was locked);
 * 
 * 13)the next operation  -  copying  with  history  one  URL (url)  to another  one 
 * (copyURL) within the same repository;
 * 
 * 14)the next operation - switching the working copy to a different  URL  (copyURL)
 * where url was copied to in the previous step;
 * 
 * 15)the next operation  -  recursively  getting  and  displaying  info on the root 
 * directory of the working copy to demonstrate that the working copy is now  really
 * switched against copyURL;
 * 
 * 16)the next operation - scheduling the newDir directory for deletion;
 * 
 * 17)the next operation  -  showing  status  once  again  (here, to  see  that  the 
 * directory with all its entries were scheduled for deletion);
 * 
 * 18)the next operation - committing local changes to the repository; this operation
 * will delete the directory (newDir) with the file (newFile) that were scheduled for
 * deletion from the repository;
 * 
 *                                    * * *
 *                                    
 * This example can be  run  for a locally  installed  Subversion  repository via the 
 * svn:// protocol. This is how you can do it:
 * 
 * 1)after you install the Subversion on your machine (SVN is available for  download 
 * at  http://subversion.tigris.org/),  you should  create  a  new  repository  in  a 
 * directory, like this (in a command line under a Windows OS):
 * 
 * >svnadmin create X:\path\to\rep
 * 
 * 2)after the repository is created you can add a new account: open X:\path\to\rep\, 
 * then move into \conf and open the file - 'passwd'.  In  the file  you'll  see  the 
 * section [users]. Uncomment it and add a new account below the section name, like:
 * 
 * [users] 
 * userName = userPassword
 * 
 * In the program you may further use this account as user's credentials.
 * 
 * 3)the  next  step  is  to  launch  the  custom  Subversion  server (svnserve) in a 
 * background mode for the just created repository:
 * 
 * >svnserve -d -r X:\path\to
 * 
 * That's all. The repository is now available via  svn://localhost/rep.
 * 
 *                                    * * *
 * 
 * While  the  program  is  running, in your console  you'll see something like this:
  
	Making a new directory at 'svn://localhost/testRep/MyRepos'...
	Committed to revision 70
	
	Importing a new directory into 'svn://localhost/testRep/MyRepos/importDir'...
	Adding         importFile.txt
	Committed to revision 71
	
	Checking out a working copy from 'svn://localhost/testRep/MyRepos'...
	A         importDir
	A         importDir/importFile.txt
	At revision 71
	
	-----------------INFO-----------------
	Local Path: N:\MyWorkingCopy
	URL: svn://localhost/testRep/MyRepos
	Repository UUID: dbe83c44-f5aa-e043-94ec-ecdf6c56480f
	Revision: 71
	Node Kind: dir
	Schedule: normal
	Last Changed Author: userName
	Last Changed Revision: 71
	Last Changed Date: Thu Jul 21 23:43:15 NOVST 2005
	-----------------INFO-----------------
	Local Path: N:\MyWorkingCopy\importDir
	URL: svn://localhost/testRep/MyRepos/importDir
	Repository UUID: dbe83c44-f5aa-e043-94ec-ecdf6c56480f
	Revision: 71
	Node Kind: dir
	Schedule: normal
	Last Changed Author: userName
	Last Changed Revision: 71
	Last Changed Date: Thu Jul 21 23:43:15 NOVST 2005
	-----------------INFO-----------------
	Local Path: N:\MyWorkingCopy\importDir\importFile.txt
	URL: svn://localhost/testRep/MyRepos/importDir/importFile.txt
	Repository UUID: dbe83c44-f5aa-e043-94ec-ecdf6c56480f
	Revision: 71
	Node Kind: file
	Schedule: normal
	Last Changed Author: userName
	Last Changed Revision: 71
	Last Changed Date: Thu Jul 21 23:43:15 NOVST 2005
	Properties Last Updated: Thu Jul 21 23:43:16 NOVST 2005
	Text Last Updated: Thu Jul 21 23:43:16 NOVST 2005
	Checksum: 75e9e68e21ae4453f318424738aef57e
	
	Recursively scheduling a new directory 'N:\MyWorkingCopy\newDir' for addition...
	A     newDir
	A     newDir/newFile.txt
	
	Status for 'N:\MyWorkingCopy':
	A          0     ?    ?                 N:\MyWorkingCopy\newDir\newFile.txt
	A          0     ?    ?                 N:\MyWorkingCopy\newDir
	
	Updating 'N:\MyWorkingCopy'...
	At revision 71
	
	Committing changes for 'N:\MyWorkingCopy'...
	Adding         newDir
	Adding         newDir/newFile.txt
	Transmitting file data....
	Committed to revision 72
	
	Locking (with stealing if the entry is already locked) 'N:\MyWorkingCopy\newDir\newFile.txt'.
	L     newFile.txt
	
	Status for 'N:\MyWorkingCopy':
	     K     72    72    userName         N:\MyWorkingCopy\newDir\newFile.txt
	
	Copying 'svn://localhost/testRep/MyRepos' to 'svn://localhost/testRep/MyReposCopy'...
	Committed to revision 73
	
	Switching 'N:\MyWorkingCopy' to 'svn://localhost/testRep/MyReposCopy'...
	  B       newDir/newFile.txt
	At revision 73
	
	-----------------INFO-----------------
	Local Path: N:\MyWorkingCopy
	URL: svn://localhost/testRep/MyReposCopy
	Repository UUID: dbe83c44-f5aa-e043-94ec-ecdf6c56480f
	Revision: 73
	Node Kind: dir
	Schedule: normal
	Last Changed Author: userName
	Last Changed Revision: 73
	Last Changed Date: Thu Jul 21 23:43:19 NOVST 2005
	-----------------INFO-----------------
	Local Path: N:\MyWorkingCopy\importDir
	URL: svn://localhost/testRep/MyReposCopy/importDir
	Repository UUID: dbe83c44-f5aa-e043-94ec-ecdf6c56480f
	Revision: 73
	Node Kind: dir
	Schedule: normal
	Last Changed Author: userName
	Last Changed Revision: 71
	Last Changed Date: Thu Jul 21 23:43:15 NOVST 2005
	-----------------INFO-----------------
	Local Path: N:\MyWorkingCopy\importDir\importFile.txt
	URL: svn://localhost/testRep/MyReposCopy/importDir/importFile.txt
	Repository UUID: dbe83c44-f5aa-e043-94ec-ecdf6c56480f
	Revision: 73
	Node Kind: file
	Schedule: normal
	Last Changed Author: userName
	Last Changed Revision: 71
	Last Changed Date: Thu Jul 21 23:43:15 NOVST 2005
	Properties Last Updated: Thu Jul 21 23:43:16 NOVST 2005
	Text Last Updated: Thu Jul 21 23:43:16 NOVST 2005
	Checksum: 75e9e68e21ae4453f318424738aef57e
	-----------------INFO-----------------
	Local Path: N:\MyWorkingCopy\newDir
	URL: svn://localhost/testRep/MyReposCopy/newDir
	Repository UUID: dbe83c44-f5aa-e043-94ec-ecdf6c56480f
	Revision: 73
	Node Kind: dir
	Schedule: normal
	Last Changed Author: userName
	Last Changed Revision: 72
	Last Changed Date: Thu Jul 21 23:43:18 NOVST 2005
	-----------------INFO-----------------
	Local Path: N:\MyWorkingCopy\newDir\newFile.txt
	URL: svn://localhost/testRep/MyReposCopy/newDir/newFile.txt
	Repository UUID: dbe83c44-f5aa-e043-94ec-ecdf6c56480f
	Revision: 73
	Node Kind: file
	Schedule: normal
	Last Changed Author: userName
	Last Changed Revision: 72
	Last Changed Date: Thu Jul 21 23:43:18 NOVST 2005
	Properties Last Updated: Thu Jul 21 23:43:20 NOVST 2005
	Text Last Updated: Thu Jul 21 23:43:18 NOVST 2005
	Checksum: 023b67e9660b2faabaf84b10ba32c6cf
	
	Scheduling 'N:\MyWorkingCopy\newDir' for deletion ...
	D     newDir/newFile.txt
	D     newDir
	
	Status for 'N:\MyWorkingCopy':
	D          73    72    userName         N:\MyWorkingCopy\newDir\newFile.txt
	D          73    72    userName         N:\MyWorkingCopy\newDir
	
	Committing changes for 'N:\MyWorkingCopy'...
	Deleting   newDir
	Committed to revision 74
 * 
 */
public class WorkingCopyTesting {

    private static String myHomePath = "/db";
    private static String myWorkingCopyName = "svn-test";
    private static String myWorkingCopyPath = "/"+myWorkingCopyName;
    
    private static String importDirName = "importDir";
    private static String importDir = "/"+importDirName;
	
    private static String repositoryID = null;
	
	private String URL = "https://support.syntactica.com/exist_svn/";
//	private String URL = "svn://localhost/"; //change local = true if use local svn server

	private static boolean local = false;

    @Test
    public void test() throws SVNException {
    	
        /*
         * Default values:
         */
        
        /*
         * Assuming that URL+repositoryID is an existing repository path.
         * SVNURL is a wrapper for URL strings that refer to repository locations.
         */
        SVNURL repositoryURL = null;
        try {
            repositoryURL = SVNURL.parseURIEncoded(URL+repositoryID);
        } catch (SVNException e) {
            //
        }
        String username = "existtest";
        String password = "existtest";
//        String username = "harry";
//        String password = "harryssecret";

        String importFile = importDir + "/importFile.txt";
        String importFileText = "This unversioned file is imported into a repository";
        
        String newDir = "/newDir";
        String newFile = newDir + "/newFile.txt";
        String fileText = "This is a new file added to the working copy";
        String newFileXml = newDir + "/newFile.xml";
        String fileTextXml = "<test/>";

        /*
         * That's where a new directory will be created
         */
        SVNURL url = repositoryURL.appendPath("MyRepos", false);
        /*
         * That's where '/MyRepos' will be copied to (branched)
         */
        SVNURL copyURL = repositoryURL.appendPath("MyReposCopy", false);
        /*
         * That's where a local directory will be imported into.
         * Note that it's not necessary that the '/importDir' directory must already
         * exist - the SVN repository server will take care of creating it. 
         */
        SVNURL importToURL = url.appendPath(importDir, false);
              
    	WorkingCopy wc = new WorkingCopy(username, password);
        
        long committedRevision = -1;
        System.out.println("Making a new directory at '" + url + "'...");
        try{
            /*
             * creates a new version controlled directory in a repository and 
             * displays what revision the repository was committed to
             */
            committedRevision = wc.makeDirectory(url, "making a new directory at '" + url + "'").getNewRevision();
        }catch(SVNException svne){
            error("error while making a new directory at '" + url + "'", svne);
        }
        System.out.println("Committed to revision " + committedRevision);
        System.out.println();

        Resource anImportDir = new Resource(myHomePath+importDir);
        Resource anImportFile = new Resource(anImportDir, SVNPathUtil.tail(importFile));
        /*
         * creates a new local directory - './importDir' and a new file - 
         * './importDir/importFile.txt' that will be imported into the repository
         * into the '/MyRepos/importDir' directory 
         */
        createLocalDir(anImportDir, new Resource[]{anImportFile}, new String[]{importFileText});
        
        System.out.println("Importing a new directory into '" + importToURL + "'...");
        try{
            /*
             * recursively imports an unversioned directory into a repository 
             * and displays what revision the repository was committed to
             */
            boolean isRecursive = true;
            committedRevision = wc.importDirectory(anImportDir, importToURL, "importing a new directory '" + anImportDir.getAbsolutePath() + "'", isRecursive).getNewRevision();
        }catch(SVNException svne){
            error("error while importing a new directory '" + anImportDir.getAbsolutePath() + "' into '" + importToURL + "'", svne);
        }
        System.out.println("Committed to revision " + committedRevision);
        System.out.println();
        
        
        /*
         * creates a local directory where the working copy will be checked out into
         */
        Resource wcDir = new Resource(myHomePath+myWorkingCopyPath);
        if (wcDir.exists()) {
            error("the destination directory '"
                    + wcDir.getAbsolutePath() + "' already exists!", null);
        }
        wcDir.mkdirs();

        System.out.println("Checking out a working copy from '" + url + "'...");
        try {
            /*
             * recursively checks out a working copy from url into wcDir.
             * SVNRevision.HEAD means the latest revision to be checked out. 
             */
        	wc.checkout(url, SVNRevision.HEAD, wcDir, true);
        } catch (SVNException svne) {
            error("error while checking out a working copy for the location '"
                            + url + "'", svne);
        }
        System.out.println();
        
        /*
         * recursively displays info for wcDir at the current working revision 
         * in the manner of 'svn info -R' command
         */
        try {
        	wc.showInfo(wcDir, SVNRevision.WORKING, true, new InfoHandler());
        } catch (SVNException svne) {
            error("error while recursively getting info for the working copy at'"
                    + wcDir.getAbsolutePath() + "'", svne);
        }
        System.out.println();

        Resource aNewDir = new Resource(wcDir, newDir);
        Resource aNewFile = new Resource(aNewDir, SVNPathUtil.tail(newFile));
        Resource aNewFileXml = new Resource(aNewDir, SVNPathUtil.tail(newFileXml));
        /*
         * creates a new local directory - 'wcDir/newDir' and a new file - 
         * '/MyWorkspace/newDir/newFile.txt' 
         */
        createLocalDir(aNewDir, new Resource[]{aNewFile,aNewFileXml}, new String[]{fileText,fileTextXml});
        
        System.out.println("Recursively scheduling a new directory '" + aNewDir.getAbsolutePath() + "' for addition...");
        try {
            /*
             * recursively schedules aNewDir for addition
             */
        	wc.addEntry(aNewDir);
        } catch (SVNException svne) {
            error("error while recursively adding the directory '"
                    + aNewDir.getAbsolutePath() + "'", svne);
        }
        System.out.println();

        boolean isRecursive = true;
        boolean isRemote = true;
        boolean isReportAll = false;
        boolean isIncludeIgnored = true;
        boolean isCollectParentExternals = false;
        System.out.println("Status for '" + wcDir.getAbsolutePath() + "':");
        try {
            /*
             * gets and shows status information for the WC directory.
             * status will be recursive on wcDir, will also cover the repository, 
             * won't cover unmodified entries, will disregard 'svn:ignore' property 
             * ignores (if any), will ignore externals definitions.
             */
        	wc.showStatus(wcDir, isRecursive, isRemote, isReportAll,
                    isIncludeIgnored, isCollectParentExternals);
        } catch (SVNException svne) {
            error("error while recursively performing status for '"
                    + wcDir.getAbsolutePath() + "'", svne);
        }
        System.out.println();

        System.out.println("Updating '" + wcDir.getAbsolutePath() + "'...");
        try {
            /*
             * recursively updates wcDir to the latest revision (SVNRevision.HEAD)
             */
        	wc.update(wcDir, SVNRevision.HEAD, true);
        } catch (SVNException svne) {
            error("error while recursively updating the working copy at '"
                    + wcDir.getAbsolutePath() + "'", svne);
        }
        System.out.println("");
        
        System.out.println("Committing changes for '" + wcDir.getAbsolutePath() + "'...");
        try {
            /*
             * commits changes in wcDir to the repository with not leaving items 
             * locked (if any) after the commit succeeds; this will add aNewDir & 
             * aNewFile to the repository. 
             */
            committedRevision = wc.commit(wcDir, false,
                    "'/newDir' with '/newDir/newFile.txt' were added")
                    .getNewRevision();
        } catch (SVNException svne) {
            error("error while committing changes to the working copy at '"
                    + wcDir.getAbsolutePath()
                    + "'", svne);
        }
        System.out.println("Committed to revision " + committedRevision);
        System.out.println();

        System.out
                .println("Locking (with stealing if the entry is already locked) '"
                        + aNewFile.getAbsolutePath() + "'.");
        try {
            /*
             * locks aNewFile with stealing (if it has been already locked by someone
             * else), providing a lock comment
             */
        	wc.lock(aNewFile, true, "locking '/newDir/newFile.txt'");
        } catch (SVNException svne) {
            error("error while locking the working copy file '"
                    + aNewFile.getAbsolutePath() + "'", svne);
        }
        System.out.println();

        System.out.println("Status for '" + wcDir.getAbsolutePath() + "':");
        try {
            /*
             * displays status once again to see the file is really locked
             */
        	wc.showStatus(wcDir, isRecursive, isRemote, isReportAll,
                    isIncludeIgnored, isCollectParentExternals);
        } catch (SVNException svne) {
            error("error while recursively performing status for '"
                    + wcDir.getAbsolutePath() + "'", svne);
        }
        System.out.println();

        System.out.println("Copying '" + url + "' to '" + copyURL + "'...");
        try {
            /*
             * makes a branch of url at copyURL - that is URL->URL copying
             * with history
             */
            committedRevision = wc.copy(url, copyURL, false,
                    "remotely copying '" + url + "' to '" + copyURL + "'")
                    .getNewRevision();
        } catch (SVNException svne) {
            error("error while copying '" + url + "' to '"
                    + copyURL + "'", svne);
        }
       /*
        * displays what revision the repository was committed to
        */
        System.out.println("Committed to revision " + committedRevision);
        System.out.println();

        System.out.println("Switching '" + wcDir.getAbsolutePath() + "' to '"
                + copyURL + "'...");
        try {
            /*
             * recursively switches wcDir to copyURL in the latest revision 
             * (SVNRevision.HEAD)
             */
        	wc.switchToURL(wcDir, copyURL, SVNRevision.HEAD, true);
        } catch (SVNException svne) {
            error("error while switching '"
                    + wcDir.getAbsolutePath() + "' to '" + copyURL + "'", svne);
        }
        System.out.println();

        /*
         * recursively displays info for the working copy once again to see
         * it was really switched to a new URL
         */
        try {
        	wc.showInfo(wcDir, SVNRevision.WORKING, true, new InfoHandler());
        } catch (SVNException svne) {
            error("error while recursively getting info for the working copy at'"
                    + wcDir.getAbsolutePath() + "'", svne);
        }
        System.out.println();

        System.out.println("Scheduling '" + aNewDir.getAbsolutePath() + "' for deletion ...");
        try {
            /*
             * schedules aNewDir for deletion (with forcing)
             */
        	wc.delete(aNewDir, true);
        } catch (SVNException svne) {
            error("error while schediling '"
                    + wcDir.getAbsolutePath() + "' for deletion", svne);
        }
        System.out.println();

        System.out.println("Status for '" + wcDir.getAbsolutePath() + "':");
        try {
            /*
             * recursively displays status once more to see whether aNewDir
             * was really scheduled for deletion  
             */
        	wc.showStatus(wcDir, isRecursive, isRemote, isReportAll,
                    isIncludeIgnored, isCollectParentExternals);
        } catch (SVNException svne) {
            error("error while recursively performing status for '"
                    + wcDir.getAbsolutePath() + "'", svne);
        }
        System.out.println();

        System.out.println("Committing changes for '" + wcDir.getAbsolutePath() + "'...");
        try {
            /*
             * lastly commits changes in wcDir to the repository; all items that
             * were locked by the user (if any) will be unlocked after the commit 
             * succeeds; this commit will remove aNewDir from the repository. 
             */
            committedRevision = wc.commit(
                    wcDir,
                    false,
                    "deleting '" + aNewDir.getAbsolutePath()
                            + "' from the filesystem as well as from the repository").getNewRevision();
        } catch (SVNException svne) {
            error("error while committing changes to the working copy '"
                    + wcDir.getAbsolutePath()
                    + "'", svne);
        }
        System.out.println("Committed to revision " + committedRevision);

        deleteRepository(repositoryID);
    }

    /*
     * Displays error information and exits. 
     */
    private static void error(String message, Exception e){
//    	e.printStackTrace();
    	throw new RuntimeException(message+(e!=null ? ": "+e.getMessage() : ""));
//        Assert.assertTrue(message+(e!=null ? ": "+e.getMessage() : ""), false);
    }
    
    /*
     * This method does not relate to SVNKit API. Just a method which creates
     * local directories and files :)
     */
    private static final void createLocalDir(Resource aNewDir, Resource[] localFiles, String[] fileContents){
        if (!aNewDir.mkdirs()) {
            error("failed to create a new directory '" + aNewDir.getAbsolutePath() + "'.", null);
        }
        for(int i=0; i < localFiles.length; i++){
        	Resource aNewFile = localFiles[i];
            try {
	            if (!aNewFile.createNewFile()) {
	                error("failed to create a new file '"
	                        + aNewFile.getAbsolutePath() + "'.", null);
	            }
	        } catch (IOException ioe) {
	            aNewFile.delete();
	            error("error while creating a new file '"
	                    + aNewFile.getAbsolutePath() + "'", ioe);
	        }
	
	        String contents = null;
	        if(i > fileContents.length-1){
	            continue;
	        }
            contents = fileContents[i];
	        
	        /*
	         * writing a text into the file
	         */
	        OutputStream fos = null;
	        try {
	        	fos = aNewFile.getOutputStream();
	            fos.write(contents.getBytes());
	        } catch (FileNotFoundException fnfe) {
	            error("the file '" + aNewFile.getAbsolutePath() + "' is not found", fnfe);
	        } catch (IOException ioe) {
	            error("error while writing into the file '"
	                    + aNewFile.getAbsolutePath() + "'", ioe);
	        } finally {
	            if (fos != null) {
	                try {
	                    fos.close();
	                } catch (IOException ioe) {
	                    //
	                }
	            }
	        }
        }
    }

	@BeforeClass
	public static void initDB() {
		// initialize XML:DB driver
		try {
			Class<?> cl = Class.forName("org.exist.xmldb.DatabaseImpl");
			Database database = (Database) cl.newInstance();
			database.setProperty("create-database", "true");
			DatabaseManager.registerDatabase(database);

		    repositoryID = createRepository();
//		    myWorkingCopyName = repositoryID;
//		    myWorkingCopyPath = "/"+myWorkingCopyName;
			
			org.xmldb.api.base.Collection root = DatabaseManager.getCollection(
					XmldbURI.LOCAL_DB, "admin", "");
			CollectionManagementService mgmt = (CollectionManagementService) root.getService("CollectionManagementService", "1.0");
			try {
				mgmt.removeCollection(myWorkingCopyName);
				mgmt.removeCollection(importDirName);
			} catch (XMLDBException e) {
			}

		} catch (Exception e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	@AfterClass
	public static void closeDB() {
		try {
			if (repositoryID != null)
				deleteRepository(repositoryID);

		    Collection root = DatabaseManager.getCollection(XmldbURI.LOCAL_DB, "admin", "");
			// CollectionManagementService cmgr = (CollectionManagementService)
			// root.getService("CollectionManagementService", "1.0");
			// cmgr.removeCollection("test");

			DatabaseInstanceManager mgr = (DatabaseInstanceManager) root.getService("DatabaseInstanceManager", "1.0");
			mgr.shutdown(2);
		} catch (XMLDBException e) {
			e.printStackTrace();
			fail(e.getMessage());
		}
	}

	private static String createRepository() {
		if (local) return "svn";
		
		HttpClient client = new HttpClient();

		PostMethod method = new PostMethod("http://support.syntactica.com/cgi-bin/3A075407-AC4E-3308-9616FD4EB832EDBB.pl");

		try {
			int statusCode = client.executeMethod(method);

			if (statusCode != HttpStatus.SC_OK) {
		        return null;
		    }
			
			return method.getResponseBodyAsString();
		} catch (Exception e) {
			return null;
		}
	}

	private static void deleteRepository(String id) {
		if (local) return;

		HttpClient client = new HttpClient();

		PostMethod method = new PostMethod("http://support.syntactica.com/cgi-bin/938A1512-5156-11DF-A4C4-D82A2BCCFF1C.pl?d="+id);

		try {
			client.executeMethod(method);
		} catch (Exception e) {
		}
	}

}