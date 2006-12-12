/*
 * LDAPbindSecurityManager.java
 *
 * Created on September 11, 2006, 9:00 AM
 *
 * (C) Andrew Hart
 */

package org.exist.security;

import java.util.Hashtable;

import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;

import org.apache.log4j.Logger;

/**
 * Code to use LDAP's bind to authenticate technology
 * @author Andrew Hart
 */
public class LDAPbindSecurityManager extends LDAPSecurityManager implements SecurityManager
{
   private final static Logger LOG = Logger.getLogger(SecurityManager.class);
   public boolean bind(String user, String passwd)
   {
      /**
       * @param user 
       * @param passwd
       */
      Hashtable env = getDirectoryEnvironment();
      env.put(Context.SECURITY_CREDENTIALS, passwd);
      env.put(Context.SECURITY_PRINCIPAL, "uid=" + user+","+userBase);

      try {
         // Create initial context
         DirContext ctx = new InitialDirContext(env);
         // Check that the credentials work
         LOG.info(ctx.lookup("uid="+user+","+userBase));

         ctx.close();
      } catch (NamingException e) {
         LOG.warn("Invalid Credentials for user: uid="+user+","+userBase, e);
         return false;
      }

      LOG.warn("User " + user + ", bind successful.");
      return true;
   }
}
