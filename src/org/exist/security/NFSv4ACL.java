package org.exist.security;

/**
 *
 * http://tools.ietf.org/html/rfc3530#page-50
 *
 * @author Adam Retter <adam@exist-db.org>
 */
public class NFSv4ACL {

    private class nfs4ace {
      int type;
      int flag;
      int access_mask;
      String who;
    }


    public final static int ACL4_SUPPORT_ALLOW_ACL    = 0x00000001;
    public final static int ACL4_SUPPORT_DENY_ACL     = 0x00000002;
    public final static int ACL4_SUPPORT_AUDIT_ACL    = 0x00000004;
    public final static int ACL4_SUPPORT_ALARM_ACL    = 0x00000008;

    //TODO add support for ALARM_ACL
    public final static int getaclsupport = ACL4_SUPPORT_ALLOW_ACL | ACL4_SUPPORT_DENY_ACL | ACL4_SUPPORT_AUDIT_ACL;

    //nfs4ace.type
    public final static int ACE4_ACCESS_ALLOWED_ACE_TYPE      = 0x00000000;
    public final static int ACE4_ACCESS_DENIED_ACE_TYPE       = 0x00000001;
    public final static int ACE4_SYSTEM_AUDIT_ACE_TYPE        = 0x00000002;
    public final static int ACE4_SYSTEM_ALARM_ACE_TYPE        = 0x00000003;

    /*
    Clients should not attempt to set an ACE unless the server claims
    support for that ACE type.  If the server receives a request to set
    an ACE that it cannot store, it MUST reject the request with
    NFS4ERR_ATTRNOTSUPP.  If the server receives a request to set an ACE
    that it can store but cannot enforce, the server SHOULD reject the
    request with NFS4ERR_ATTRNOTSUPP.

    Example: suppose a server can enforce NFS ACLs for NFS access but
    cannot enforce ACLs for local access.  If arbitrary processes can run
    on the server, then the server SHOULD NOT indicate ACL support.  On
    the other hand, if only trusted administrative programs run locally,
    then the server may indicate ACL support.
    */
    //NFS4ERR_ATTRNOTSUPP
    //NFS4ERR_ATTRNOTSUPP

  
    //nfs4ace.access_mask
    public final static int ACE4_READ_DATA            = 0x00000001;
    public final static int ACE4_LIST_DIRECTORY       = 0x00000001;
    public final static int ACE4_WRITE_DATA           = 0x00000002;
    public final static int ACE4_ADD_FILE             = 0x00000002;
    public final static int ACE4_APPEND_DATA          = 0x00000004;
    public final static int ACE4_ADD_SUBDIRECTORY     = 0x00000004;
    public final static int ACE4_READ_NAMED_ATTRS     = 0x00000008;
    public final static int ACE4_WRITE_NAMED_ATTRS    = 0x00000010;
    public final static int ACE4_EXECUTE              = 0x00000020;
    public final static int ACE4_DELETE_CHILD         = 0x00000040;
    public final static int ACE4_READ_ATTRIBUTES      = 0x00000080;
    public final static int ACE4_WRITE_ATTRIBUTES     = 0x00000100;
    public final static int ACE4_DELETE               = 0x00010000;
    public final static int ACE4_READ_ACL             = 0x00020000;
    public final static int ACE4_WRITE_ACL            = 0x00040000;
    public final static int ACE4_WRITE_OWNER          = 0x00080000;
    public final static int ACE4_SYNCHRONIZE          = 0x00100000;


    //nfs4ace.flag
    public final static int ACE4_FILE_INHERIT_ACE             = 0x00000001;
    public final static int ACE4_DIRECTORY_INHERIT_ACE        = 0x00000002;
    public final static int ACE4_NO_PROPAGATE_INHERIT_ACE     = 0x00000004;
    public final static int ACE4_INHERIT_ONLY_ACE             = 0x00000008;
    public final static int ACE4_SUCCESSFUL_ACCESS_ACE_FLAG   = 0x00000010;
    public final static int ACE4_FAILED_ACCESS_ACE_FLAG       = 0x00000020;
    public final static int ACE4_IDENTIFIER_GROUP             = 0x00000040;

    /*
    A server need not support any of these flags.  If the server supports
    flags that are similar to, but not exactly the same as, these flags,
    the implementation may define a mapping between the protocol-defined
    flags and the implementation-defined flags.  Again, the guiding
    principle is that the file not appear to be more secure than it
    really is.

    For example, suppose a client tries to set an ACE with
    ACE4_FILE_INHERIT_ACE set but not ACE4_DIRECTORY_INHERIT_ACE.  If the
    server does not support any form of ACL inheritance, the server
    should reject the request with NFS4ERR_ATTRNOTSUPP.  If the server
    supports a single "inherit ACE" flag that applies to both files and
    directories, the server may reject the request (i.e., requiring the
    client to set both the file and directory inheritance flags).  The
    server may also accept the request and silently turn on the
    ACE4_DIRECTORY_INHERIT_ACE flag.
    */


    public final static String WHO4_OWNER = "OWNER@";
    public final static String WHO4_GROUP = "GROUP@";
    public final static String WHO4_EVERYONE = "EVERYONE@";
    public final static String WHO4_INTERACTIVE = "INTERACTIVE@";
    public final static String WHO4_NETWORK = "NETWORK@";
    public final static String WHO4_DIALUP = "DIALUP@";
    public final static String WHO4_BATCH = "BATCH@";
    public final static String WHO4_ANONYMOUS = "ANONYMOUS@";
    public final static String WHO4_AUTHENTICATED = "AUTHENTICATED@";
    public final static String WHO4_SERVICE = "SERVICE@";

    public final static int MODE4_SUID = 0x800;  /* set user id on execution */
    public final static int MODE4_SGID = 0x400;  /* set group id on execution */
    public final static int MODE4_SVTX = 0x200;  /* save text even after use */
    public final static int MODE4_RUSR = 0x100;  /* read permission: owner */
    public final static int MODE4_WUSR = 0x080;  /* write permission: owner */
    public final static int MODE4_XUSR = 0x040;  /* execute permission: owner */
    public final static int MODE4_RGRP = 0x020;  /* read permission: group */
    public final static int MODE4_WGRP = 0x010;  /* write permission: group */
    public final static int MODE4_XGRP = 0x008;  /* execute permission: group */
    public final static int MODE4_ROTH = 0x004;  /* read permission: other */
    public final static int MODE4_WOTH = 0x002;  /* write permission: other */
    public final static int MODE4_XOTH = 0x001;  /* execute permission: other */
}