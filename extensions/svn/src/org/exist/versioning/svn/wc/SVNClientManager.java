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
package org.exist.versioning.svn.wc;

import org.exist.security.SecurityManager;
import org.exist.storage.BrokerPool;
import org.exist.storage.DBBroker;
import org.exist.versioning.svn.internal.wc.DefaultSVNOptions;
import org.exist.versioning.svn.wc.admin.SVNAdminClient;
import org.exist.versioning.svn.wc.admin.SVNLookClient;
import org.tmatesoft.svn.core.ISVNCanceller;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.wc.ISVNRepositoryPool;
import org.tmatesoft.svn.util.ISVNDebugLog;
import org.tmatesoft.svn.util.SVNDebugLog;

/**
 * The <b>SVNClientManager</b> class is used to manage <b>SVN</b>*<b>Client</b> 
 * objects as well as for providing them to a user what makes the user's work
 * easier and his code - pretty clear and flexible.
 * 
 * <p> 
 * When you don't have special needs to create, keep and manage 
 * separate <b>SVN</b>*<b>Client</b> objects by yourself, you should
 * use <b>SVNClientManager</b> that takes care of all that work for you.
 * These are some of advantages of using <b>SVNClientManager</b>:
 * <ol>
 * <li>If you instantiate an <b>SVN</b>*<b>Client</b> object by yourself 
 * you need to provide a run-time configuration driver - {@link ISVNOptions} - 
 * as well as an authentication and network layers driver - 
 * {@link org.tmatesoft.svn.core.auth.ISVNAuthenticationManager}. When
 * using an <b>SVNClientManager</b> you have multiple choices to provide
 * and use those drivers:
 * <pre class="javacode">
 *     <span class="javacomment">//1.default options and authentication drivers to use</span>
 *     SVNClientManager clientManager = SVNClientManager.newInstance();
 *     
 *     ...
 *     
 *     <span class="javacomment">//2.provided options and default authentication drivers to use</span>
 *     ISVNOptions myOptions;
 *     ...
 *     SVNClientManager clientManager = SVNClientManager.newInstance(myOptions);
 *     
 *     ...
 *     
 *     <span class="javacomment">//3.provided options and authentication drivers to use</span>
 *     ISVNOptions myOptions;
 *     ISVNAuthenticationManager myAuthManager;
 *     ...
 *     SVNClientManager clientManager = SVNClientManager.newInstance(myOptions, myAuthManager);
 *     
 *     ...
 *     
 *     <span class="javacomment">//4.provided options driver and user's credentials to make</span> 
 *     <span class="javacomment">//a default authentication driver use them</span> 
 *     ISVNOptions myOptions;
 *     ...
 *     SVNClientManager 
 *         clientManager = SVNClientManager.newInstance(myOptions, <span class="javastring">"name"</span>, <span class="javastring">"passw"</span>);
 *     </pre><br />
 * Having instantiated an <b>SVNClientManager</b> in one of these ways, all 
 * the <b>SVN</b>*<b>Client</b> objects it will provide you will share those
 * drivers, so you don't need to code much to provide the same drivers to each
 * <b>SVN</b>*<b>Client</b> instance by yourself.
 * <li>With <b>SVNClientManager</b> you don't need to create and keep your
 * <b>SVN</b>*<b>Client</b> objects by youself - <b>SVNClientManager</b> will
 * do all the work for you, so this will certainly bring down your efforts
 * on coding and your code will be clearer and more flexible. All you need is
 * to create an <b>SVNClientManager</b> instance.
 * <li>Actually every <b>SVN</b>*<b>Client</b> object is instantiated only at
 * the moment of the first call to an appropriate <b>SVNClientManager</b>'s 
 * <code>get</code> method:
 * <pre class="javacode">
 *     SVNClientManager clientManager;
 *     ...
 *     <span class="javacomment">//an update client will be created only at that moment when you</span> 
 *     <span class="javacomment">//first call this method for getting your update client, but if you</span>
 *     <span class="javacomment">//have already called it once before, then the method will return</span>
 *     <span class="javacomment">//that update client object instantiated in previous... so, it's</span>
 *     <span class="javacomment">//quite cheap, you see..</span> 
 *     SVNUpdateClient updateClient = clientManager.getUpdateClient();</pre><br />
 * <li>You can provide a single event handler that will be used by all 
 * <b>SVN</b>*<b>Client</b> objects provided by <b>SVNClientManager</b>:
 * <pre class="javacode">
 * <span class="javakeyword">import</span> org.tmatesoft.svn.core.wc.ISVNEventHandler;
 *     
 *     ...
 *     
 *     ISVNEventHandler commonEventHandler;
 *     SVNClientManager clientManager = SVNClientManager.newInstance();
 *     ...
 *     <span class="javacomment">//will be used by all SVN*Client objects</span>
 *     <span class="javacomment">//obtained from your client manager</span>
 *     clientManager.setEventHandler(commonEventHandler);
 * </pre>
 * <li>
 * </ol>
 * 
 * @version 1.3
 * @author  TMate Software Ltd.
 * @since   1.2
 * @see     ISVNEventHandler
 * @see     <a target="_top" href="http://svnkit.com/kb/examples/">Examples</a>
 */
public class SVNClientManager implements ISVNRepositoryPool {
    
    private ISVNOptions myOptions;
    
    private SVNCommitClient myCommitClient;
    private SVNCopyClient myCopyClient;
    private SVNDiffClient myDiffClient;
    private SVNLogClient myLogClient;
    private SVNMoveClient myMoveClient;
    private SVNStatusClient myStatusClient;
    private SVNUpdateClient myUpdateClient;
    private SVNWCClient myWCClient;
    private SVNChangelistClient myChangelistClient;
    private SVNAdminClient myAdminClient;
    private SVNLookClient myLookClient;
    
    private ISVNEventHandler myEventHandler;
    private ISVNRepositoryPool myRepositoryPool;
    private ISVNDebugLog myDebugLog;

    private boolean myIsIgnoreExternals;

    private SVNClientManager(ISVNOptions options, ISVNRepositoryPool repositoryPool) {
        myOptions = options;
        if (myOptions == null) {
            myOptions = SVNWCUtil.createDefaultOptions(true);
        }
        myRepositoryPool = repositoryPool;
    }

    private SVNClientManager(ISVNOptions options, final ISVNAuthenticationManager authManager) {
        this(options, new DefaultSVNRepositoryPool(authManager == null ? SVNWCUtil.createDefaultAuthenticationManager() : authManager, options));
    }
    
    /**
     * Creates a new instance of this class using default {@link ISVNOptions}
     * and {@link org.tmatesoft.svn.core.auth.ISVNAuthenticationManager} drivers. 
     * That means this <b>SVNClientManager</b> will use the SVN's default run-time 
     * configuration area. Default options are obtained via a call to 
     * {@link SVNWCUtil#createDefaultOptions(boolean)}.    
     * 
     * @return a new <b>SVNClientManager</b> instance
     */
    public static SVNClientManager newInstance() {
        return new SVNClientManager(null, (ISVNAuthenticationManager) null);
    }

    /**
     * Creates a new instance of this class using the provided {@link ISVNOptions}
     * and default {@link org.tmatesoft.svn.core.auth.ISVNAuthenticationManager} drivers. 
     * That means this <b>SVNClientManager</b> will use the caller's configuration options
     * (which correspond to options found in the default SVN's <i>config</i>
     * file) and the default SVN's <i>servers</i> configuration and auth storage.  
     * 
     * <p/>
     * If <code>options</code> is <span class="javakeyword">null</span>, default options are 
     * used which are obtained via a call to {@link SVNWCUtil#createDefaultOptions(boolean)}.  
     * 
     * @param  options  a config driver
     * @return          a new <b>SVNClientManager</b> instance
     */
    public static SVNClientManager newInstance(ISVNOptions options) {
        return new SVNClientManager(options, (ISVNAuthenticationManager) null);
    }

    /**
     * Creates a new instance of this class using the provided {@link ISVNOptions}
     * and {@link org.tmatesoft.svn.core.auth.ISVNAuthenticationManager} drivers. 
     * That means this <b>SVNClientManager</b> will use the caller's configuration options
     * (which correspond to options found in the default SVN's <i>config</i>
     * file) as well as authentication credentials and servers options (similar to
     * options found in the default SVN's <i>servers</i>).   
     *
     * @param  options     a config driver
     * @param  authManager an authentication driver
     * @return             a new <b>SVNClientManager</b> instance
     */
    public static SVNClientManager newInstance(ISVNOptions options, ISVNAuthenticationManager authManager) {
        return new SVNClientManager(options, authManager);
    }
    
    /**
     * Creates a new instance of this class using the provided
     * config driver and creator of of <b>SVNRepository</b> objects. 
     * 
     * @param  options         a config driver
     * @param  repositoryPool  a creator of <b>SVNRepository</b> objects
     * @return                 a new <b>SVNClientManager</b> instance
     */
    public static SVNClientManager newInstance(ISVNOptions options, ISVNRepositoryPool repositoryPool) {
        return new SVNClientManager(options, repositoryPool);
    }

    /**
     * Creates a new instance of this class using the provided {@link ISVNOptions}
     * driver and user's credentials to make a default implementation of
     * {@link org.tmatesoft.svn.core.auth.ISVNAuthenticationManager} use them. 
     * That means this <b>SVNClientManager</b> will use the caller's configuration options
     * (which correspond to options found in the default SVN's <i>config</i>
     * file), the default SVN's <i>servers</i> configuration and the caller's
     * credentials.
     * 
     * @param  options     a config driver
     * @param  userName    a user account name
     * @param  password    a user password 
     * @return             a new <b>SVNClientManager</b> instance
     */
    public static SVNClientManager newInstance(DefaultSVNOptions options, String userName, String password) {
//    	BrokerPool db = null;
//    	DBBroker broker = null;
//    	try {
//    		db = BrokerPool.getInstance();
//    		broker = db.get(SecurityManager.SYSTEM_USER);
//    		
            boolean storeAuth = options == null ? true : options.isAuthStorageEnabled();
            ISVNAuthenticationManager authManager = SVNWCUtil.createDefaultAuthenticationManager(null, userName, password, storeAuth);
            return new SVNClientManager(options, authManager);
//		} catch (Exception e) {
//			if (db != null) db.release(broker);
//			return null;
//		}
    }
    
    /**
     * Creates a low-level SVN protocol driver to directly work with
     * a repository. 
     * 
     * <p>
     * The driver created will be set a default {@link org.tmatesoft.svn.core.auth.ISVNAuthenticationManager} 
     * manager.
     * 
     * <p>
     * Used by <b>SVN</b>*<b>Client</b> objects (managed by this 
     * <b>SVNClientManager</b>) to access a repository when needed.
     * 
     * @param  url           a repository location to establish a 
     *                       connection with (will be the root directory
     *                       for the working session)
     * @param  mayReuse      if <span class="javakeyword">true</span> then tries
     *                       first tries to find a reusable driver or creates a new 
     *                       reusable one
     * @return               a low-level API driver for direct interacting
     *                       with a repository
     * @throws SVNException
     */
    public SVNRepository createRepository(SVNURL url, boolean mayReuse) throws SVNException {
        if (myRepositoryPool != null) {
            return myRepositoryPool.createRepository(url, mayReuse);
        }
        SVNRepository repository = SVNRepositoryFactory.create(url);
        repository.setAuthenticationManager(SVNWCUtil.createDefaultAuthenticationManager());
        repository.setDebugLog(getDebugLog());
        return repository;
    }

    /**
     * @param shutdownAll 
     * @deprecated          use {@link #dispose()} instead
     */
    public void shutdownConnections(boolean shutdownAll) {
        if (myRepositoryPool != null) {
            myRepositoryPool.dispose();
        }
    }

    /**
     * Disposes this client object.
     * Call this method when you've finished working with this object. This will close 
     * any open network sessions. 
     */
    public void dispose() {
        if (myRepositoryPool != null) {
            myRepositoryPool.dispose();
        }
    }
    
    /**
     * Returns the run-time configuration options driver
     * which kept by this object.
     * 
     * @return  a run-time options driver
     */
    public ISVNOptions getOptions() {
        return myOptions;
    }
    

    /**
     * Sets an event handler to all <b>SVN</b>*<b>Client</b> objects 
     * created and kept by this <b>SVNClientManager</b>.
     *   
     * <p>
     * The provided event handler will be set only to only those objects
     * that have been already created (<b>SVN</b>*<b>Client</b> objects are
     * instantiated by an <b>SVNClientManager</b> at the moment of the 
     * first call to a <code>get*Client()</code> method). So, the handler
     * won't be set for those ones that have never been requested. However
     * as they are first requested (and thus created) the handler will be 
     * set to them, too, since <b>SVNClientManager</b> is still keeping the handler.
     * 
     * @param handler an event handler
     */
    public void setEventHandler(ISVNEventHandler handler) {
        myEventHandler = handler;
        setCanceller(handler);
        if (myCommitClient != null) {
            myCommitClient.setEventHandler(handler);
        }
        if (myCopyClient != null) {
            myCopyClient.setEventHandler(handler);
        }
        if (myDiffClient != null) {
            myDiffClient.setEventHandler(handler);
        }
        if (myLogClient != null) {
            myLogClient.setEventHandler(handler);
        }
        if (myMoveClient != null) {
            myMoveClient.setEventHandler(handler);
        }
        if (myStatusClient != null) {
            myStatusClient.setEventHandler(handler);
        }
        if (myUpdateClient != null) {
            myUpdateClient.setEventHandler(handler);
        }
        if (myWCClient != null) {
            myWCClient.setEventHandler(handler);
        }
        if (myChangelistClient != null) {
            myChangelistClient.setEventHandler(handler);
        }
        if (myAdminClient != null) {
            myAdminClient.setEventHandler(handler);
        }
        if (myLookClient != null) {
            myLookClient.setEventHandler(handler);
        }
    }
    
    /**
     * Sets whether externals should be ignored or not by all of the <b>SVN*Clinet</b> objects which this client
     * manager will provide.
     * 
     * @param isIgnoreExternals   whether externals should be ignored or not
     * @since                     1.2.0 
     */
    public void setIgnoreExternals(boolean isIgnoreExternals) {
        myIsIgnoreExternals = isIgnoreExternals;
        if (myCommitClient != null) {
            myCommitClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        if (myCopyClient != null) {
            myCopyClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        if (myDiffClient != null) {
            myDiffClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        if (myLogClient != null) {
            myLogClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        if (myMoveClient != null) {
            myMoveClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        if (myStatusClient != null) {
            myStatusClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        if (myUpdateClient != null) {
            myUpdateClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        if (myWCClient != null) {
            myWCClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        if (myChangelistClient != null) {
            myChangelistClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        if (myAdminClient != null) {
            myAdminClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        if (myLookClient != null) {
            myLookClient.setIgnoreExternals(myIsIgnoreExternals);
        }
    }
    
    /**
     * Tells wheter externals are ignored or not.
     * @return  <span class="javakeyword">true</span> if externals are ignored; otherwise 
     *          <span class="javakeyword">false</span>
     * @since   1.2.0  
     */
    public boolean isIgnoreExternals() {
        return myIsIgnoreExternals;
    }

    /**
     * Sets global run-time configuration options to all of the <b>SVN*Client</b> objects provided by this 
     * client manager.
     * 
     * @param options  run-time configuration options 
     */
    public void setOptions(ISVNOptions options) {
        myOptions = options;
        if (myCommitClient != null) {
            myCommitClient.setOptions(options);
        }
        if (myCopyClient != null) {
            myCopyClient.setOptions(options);
        }
        if (myDiffClient != null) {
            myDiffClient.setOptions(options);
        }
        if (myLogClient != null) {
            myLogClient.setOptions(options);
        }
        if (myMoveClient != null) {
            myMoveClient.setOptions(options);
        }
        if (myStatusClient != null) {
            myStatusClient.setOptions(options);
        }
        if (myUpdateClient != null) {
            myUpdateClient.setOptions(options);
        }
        if (myWCClient != null) {
            myWCClient.setOptions(options);
        }
        if (myAdminClient != null) {
            myAdminClient.setOptions(options);
        }
        if (myLookClient != null) {
            myLookClient.setOptions(options);
        }
    }
    
    /**
     * Returns an instance of the {@link SVNCommitClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNCommitClient</b> instance
     */
    public SVNCommitClient getCommitClient() {
        if (myCommitClient == null) {
            myCommitClient = new SVNCommitClient(this, myOptions);
            myCommitClient.setEventHandler(myEventHandler);
            myCommitClient.setDebugLog(getDebugLog());
            myCommitClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        return myCommitClient;
    }

    /**
     * Returns an instance of the {@link org.tmatesoft.svn.core.wc.admin.SVNAdminClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNAdminClient</b> instance
     */
    public SVNAdminClient getAdminClient() {
        if (myAdminClient == null) {
            myAdminClient = new SVNAdminClient(this, myOptions);
            myAdminClient.setEventHandler(myEventHandler);
            myAdminClient.setDebugLog(getDebugLog());
            myAdminClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        return myAdminClient;
    }

    /**
     * Returns an instance of the {@link org.tmatesoft.svn.core.wc.admin.SVNLookClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNLookClient</b> instance
     */
    public SVNLookClient getLookClient() {
        if (myLookClient == null) {
            myLookClient = new SVNLookClient(this, myOptions);
            myLookClient.setEventHandler(myEventHandler);
            myLookClient.setDebugLog(getDebugLog());
            myLookClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        return myLookClient;
    }
    
    /**
     * Returns an instance of the {@link SVNCopyClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNCopyClient</b> instance
     */
    public SVNCopyClient getCopyClient() {
        if (myCopyClient == null) {
            myCopyClient = new SVNCopyClient(this, myOptions);
            myCopyClient.setEventHandler(myEventHandler);
            myCopyClient.setDebugLog(getDebugLog());
            myCopyClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        return myCopyClient;
    }

    /**
     * Returns an instance of the {@link SVNDiffClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNDiffClient</b> instance
     */
    public SVNDiffClient getDiffClient() {
        if (myDiffClient == null) {
            myDiffClient = new SVNDiffClient(this, myOptions);
            myDiffClient.setEventHandler(myEventHandler);
            myDiffClient.setDebugLog(getDebugLog());
            myDiffClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        return myDiffClient;
    }

    /**
     * Returns an instance of the {@link SVNLogClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNLogClient</b> instance
     */
    public SVNLogClient getLogClient() {
        if (myLogClient == null) {
            myLogClient = new SVNLogClient(this, myOptions);
            myLogClient.setEventHandler(myEventHandler);
            myLogClient.setDebugLog(getDebugLog());
            myLogClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        return myLogClient;
    }

    /**
     * Returns an instance of the {@link SVNMoveClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNMoveClient</b> instance
     */
    public SVNMoveClient getMoveClient() {
        if (myMoveClient == null) {
            myMoveClient = new SVNMoveClient(this, myOptions);
            myMoveClient.setEventHandler(myEventHandler);
            myMoveClient.setDebugLog(getDebugLog());
            myMoveClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        return myMoveClient;
    }

    /**
     * Returns an instance of the {@link SVNStatusClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNStatusClient</b> instance
     */
    public SVNStatusClient getStatusClient() {
        if (myStatusClient == null) {
            myStatusClient = new SVNStatusClient(this, myOptions);
            myStatusClient.setEventHandler(myEventHandler);
            myStatusClient.setDebugLog(getDebugLog());
            myStatusClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        return myStatusClient;
    }

    /**
     * Returns an instance of the {@link SVNUpdateClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNUpdateClient</b> instance
     */
    public SVNUpdateClient getUpdateClient() {
        if (myUpdateClient == null) {
            myUpdateClient = new SVNUpdateClient(this, myOptions);
            myUpdateClient.setEventHandler(myEventHandler);
            myUpdateClient.setDebugLog(getDebugLog());
            myUpdateClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        return myUpdateClient;
    }

    /**
     * Returns an instance of the {@link SVNWCClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNWCClient</b> instance
     */
    public SVNWCClient getWCClient() {
        if (myWCClient == null) {
            myWCClient = new SVNWCClient(this, myOptions);
            myWCClient.setEventHandler(myEventHandler);
            myWCClient.setDebugLog(getDebugLog());
            myWCClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        return myWCClient;
    }
    
    /**
     * Returns an instance of the {@link SVNChangelistClient} class. 
     * 
     * <p>
     * If it's the first time this method is being called the object is
     * created, initialized and then returned. Further calls to this
     * method will get the same object instantiated at that moment of 
     * the first call. <b>SVNClientManager</b> does not reinstantiate
     * its <b>SVN</b>*<b>Client</b> objects. 
     * 
     * @return an <b>SVNChangelistClient</b> instance
     * @since  1.2.0
     */
    public SVNChangelistClient getChangelistClient() {
        if (myChangelistClient == null) {
            myChangelistClient = new SVNChangelistClient(this, myOptions);
            myChangelistClient.setEventHandler(myEventHandler);
            myChangelistClient.setDebugLog(getDebugLog());
            myChangelistClient.setIgnoreExternals(myIsIgnoreExternals);
        }
        return myChangelistClient;
    }
    
    /**
     * Returns the debug logger currently in use.  
     * 
     * <p>
     * If no debug logger has been specified by the time this call occurs, 
     * a default one (returned by <code>org.tmatesoft.svn.util.SVNDebugLog.getDefaultLog()</code>) 
     * will be created and used.
     * 
     * @return a debug logger
     */
    public ISVNDebugLog getDebugLog() {
        if (myDebugLog == null) {
            return SVNDebugLog.getDefaultLog();
        }
        return myDebugLog;
    }

    /**
     * Sets a logger to write debug log information to. Sets this same logger
     * object to all <b>SVN</b>*<b>Client</b> objects instantiated by this 
     * moment. 
     * 
     * @param log a debug logger
     */
    public void setDebugLog(ISVNDebugLog log) {
        myDebugLog = log;
        if (myCommitClient != null) {
            myCommitClient.setDebugLog(log);
        }
        if (myCopyClient != null) {
            myCopyClient.setDebugLog(log);
        }
        if (myDiffClient != null) {
            myDiffClient.setDebugLog(log);
        }
        if (myLogClient != null) {
            myLogClient.setDebugLog(log);
        }
        if (myMoveClient != null) {
            myMoveClient.setDebugLog(log);
        }
        if (myStatusClient != null) {
            myStatusClient.setDebugLog(log);
        }
        if (myUpdateClient != null) {
            myUpdateClient.setDebugLog(log);
        }
        if (myWCClient != null) {
            myWCClient.setDebugLog(log);
        }
        if (myChangelistClient != null) {
            myChangelistClient.setDebugLog(log);
        }
        if (myAdminClient != null) {
            myAdminClient.setDebugLog(log);
        }
        if (myLookClient != null) {
            myLookClient.setDebugLog(log);
        }
        if (myRepositoryPool != null) {
            myRepositoryPool.setDebugLog(log);
        }
    }

    /**
     * Sets an authentication manager to this client manager.
     * This authentication manager will be used by all the <b>SVN*Client</b> objects provided by 
     * this client manager for authenticating the client side against the server side when needed (on demand)
     * or preliminarily (if specified).
     * 
     * @param authManager   user's implementation of the authentication manager interface 
     */
    public void setAuthenticationManager(ISVNAuthenticationManager authManager) {
        if (myRepositoryPool != null) {
            myRepositoryPool.setAuthenticationManager(authManager);
        }
    }

    /**
     * Sets a canceller to this client manager.
     * This canceller will be used by all the <b>SVN*Client</b> objects provided by this client manager.
     * 
     * @param canceller     user's implementation of the canceller interface
     * @since               1.2.0
     */
    public void setCanceller(ISVNCanceller canceller) {
        if (myRepositoryPool != null) {
            myRepositoryPool.setCanceller(canceller);
        }
    }

    /**
     * Returns the repository pool used by this client manager.
     * This pool is used to create and manage {@link SVNRepository} objects by all the <b>SVN*Client</b>
     * objects provided by this client manager.
     * 
     * @return        repository pool object
     * @since         1.2.0
     */
    public ISVNRepositoryPool getRepositoryPool() {
        return myRepositoryPool;
    }
}
