/*
 * eXist Open Source Native XML Database
 * Copyright (C) 2001-2013 The eXist Project
 * http://exist-db.org
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *  
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301, USA.
 *  
 *  $Id$
 */
package org.exist.xquery.functions.securitymanager;

import org.exist.collections.Collection;
import org.exist.dom.DocumentImpl;
import org.exist.dom.QName;
import org.exist.memtree.MemTreeBuilder;
import org.exist.security.ACLPermission.ACE_ACCESS_TYPE;
import org.exist.security.ACLPermission.ACE_TARGET;
import org.exist.security.AbstractUnixStylePermission;
import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.security.PermissionFactory;
import org.exist.security.PermissionFactory.PermissionModifier;
import org.exist.security.SimpleACLPermission;
import org.exist.security.Subject;
import org.exist.util.SyntaxException;
import org.exist.xmldb.XmldbURI;
import org.exist.xquery.BasicFunction;
import org.exist.xquery.Cardinality;
import org.exist.xquery.FunctionSignature;
import org.exist.xquery.XPathException;
import org.exist.xquery.XQueryContext;
import org.exist.xquery.value.AnyURIValue;
import org.exist.xquery.value.BooleanValue;
import org.exist.xquery.value.FunctionParameterSequenceType;
import org.exist.xquery.value.FunctionReturnSequenceType;
import org.exist.xquery.value.Sequence;
import org.exist.xquery.value.SequenceType;
import org.exist.xquery.value.StringValue;
import org.exist.xquery.value.Type;

/**
 *
 * @author Adam Retter <adam@existsolutions.com>
 */
public class PermissionsFunction extends BasicFunction {
    
    private final static QName qnGetPermissions = new QName("get-permissions", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnAddUserACE = new QName("add-user-ace", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnAddGroupACE = new QName("add-group-ace", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnInsertUserACE = new QName("insert-user-ace", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnInsertGroupACE = new QName("insert-group-ace", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnModifyACE = new QName("modify-ace", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnRemoveACE = new QName("remove-ace", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnClearACL = new QName("clear-acl", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);

    private final static QName qnChMod = new QName("chmod", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnChOwn = new QName("chown", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnChGrp = new QName("chgrp", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    
    private final static QName qnHasAccess = new QName("has-access", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    
    private final static QName qnModeToOctal = new QName("mode-to-octal", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);
    private final static QName qnOctalToMode = new QName("octal-to-mode", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX);

    public final static FunctionSignature FNS_GET_PERMISSIONS = new FunctionSignature(
        qnGetPermissions,
        "Gets the permissions of a resource or collection.",
        new SequenceType[] {
            new FunctionParameterSequenceType("path", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The path to the resource or collection to get permissions of.")
        },
        new FunctionReturnSequenceType(Type.DOCUMENT, Cardinality.ONE, "The permissions of the resource or collection")
    );
    
    public final static FunctionSignature FNS_ADD_USER_ACE = new FunctionSignature(
        qnAddUserACE,
        "Adds a User ACE to the ACL of a resource or collection.",
        new SequenceType[] {
            new FunctionParameterSequenceType("path", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The path to the resource or collection whoose ACL you wish to add the ACE to."),
            new FunctionParameterSequenceType("user-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the user to create an ACE for."),
            new FunctionParameterSequenceType("allowed", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if the ACE is allowing the permission mode, or false() if we are denying the permission mode"),
            new FunctionParameterSequenceType("mode", Type.STRING, Cardinality.EXACTLY_ONE, "The mode to set on the ACE e.g. 'rwx'"),
        },
        new SequenceType(Type.EMPTY, Cardinality.ZERO)
    );
    
    public final static FunctionSignature FNS_ADD_GROUP_ACE = new FunctionSignature(
        qnAddGroupACE,
        "Adds a Group ACE to the ACL of a resource or collection.",
        new SequenceType[] {
            new FunctionParameterSequenceType("path", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The path to the resource or collection whoose ACL you wish to add the ACE to."),
            new FunctionParameterSequenceType("group-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the group to create an ACE for."),
            new FunctionParameterSequenceType("allowed", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if the ACE is allowing the permission mode, or false() if we are denying the permission mode"),
            new FunctionParameterSequenceType("mode", Type.STRING, Cardinality.EXACTLY_ONE, "The mode to set on the ACE e.g. 'rwx'"),
        },
        new SequenceType(Type.EMPTY, Cardinality.ZERO)
    );
    
    public final static FunctionSignature FNS_INSERT_USER_ACE = new FunctionSignature(
        qnInsertUserACE,
        "Inserts a User ACE into the ACL of a resource or collection.",
        new SequenceType[] {
            new FunctionParameterSequenceType("path", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The path to the resource or collection whoose ACL you wish to add the ACE to."),
            new FunctionParameterSequenceType("index", Type.INT, Cardinality.EXACTLY_ONE, "The index in the ACL to insert the ACE before, subsequent entries will be renumbered"),
            new FunctionParameterSequenceType("user-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the user to create an ACE for."),
            new FunctionParameterSequenceType("allowed", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if the ACE is allowing the permission mode, or false() if we are denying the permission mode"),
            new FunctionParameterSequenceType("mode", Type.STRING, Cardinality.EXACTLY_ONE, "The mode to set on the ACE e.g. 'rwx'"),
        },
        new SequenceType(Type.EMPTY, Cardinality.ZERO)
    );
    
    public final static FunctionSignature FNS_INSERT_GROUP_ACE = new FunctionSignature(
        qnInsertGroupACE,
        "Inserts a Group ACE into the ACL of a resource or collection.",
        new SequenceType[] {
            new FunctionParameterSequenceType("path", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The path to the resource or collection whoose ACL you wish to add the ACE to."),
            new FunctionParameterSequenceType("index", Type.INT, Cardinality.EXACTLY_ONE, "The index in the ACL to insert the ACE before, subsequent entries will be renumbered"),
            new FunctionParameterSequenceType("group-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the group to create an ACE for."),
            new FunctionParameterSequenceType("allowed", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if the ACE is allowing the permission mode, or false() if we are denying the permission mode"),
            new FunctionParameterSequenceType("mode", Type.STRING, Cardinality.EXACTLY_ONE, "The mode to set on the ACE e.g. 'rwx'"),
        },
        new SequenceType(Type.EMPTY, Cardinality.ZERO)
    );
    
    public final static FunctionSignature FNS_MODIFY_ACE = new FunctionSignature(
        qnModifyACE,
        "Modified an ACE of an ACL of a resource or collection.",
        new SequenceType[] {
            new FunctionParameterSequenceType("path", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The path to the resource or collection whoose ACL you wish to modify the ACE of."),
            new FunctionParameterSequenceType("index", Type.INT, Cardinality.EXACTLY_ONE, "The index of the ACE in the ACL to modify"),
            new FunctionParameterSequenceType("allowed", Type.BOOLEAN, Cardinality.EXACTLY_ONE, "true() if the ACE is allowing the permission mode, or false() if we are denying the permission mode"),
            new FunctionParameterSequenceType("mode", Type.STRING, Cardinality.EXACTLY_ONE, "The mode to set on the ACE e.g. 'rwx'"),
        },
        new SequenceType(Type.EMPTY, Cardinality.ZERO)
    );
    
    public final static FunctionSignature FNS_REMOVE_ACE = new FunctionSignature(
        qnRemoveACE,
        "Removes an ACE from the ACL of a resource or collection.",
        new SequenceType[] {
            new FunctionParameterSequenceType("path", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The path to the resource or collection whoose ACL you wish to remove the ACE from."),
            new FunctionParameterSequenceType("index", Type.INT, Cardinality.EXACTLY_ONE, "The index of the ACE in the ACL to remove, subsequent entries will be renumbered")
        },
        new SequenceType(Type.EMPTY, Cardinality.ZERO)
    );
    
    public final static FunctionSignature FNS_CLEAR_ACL = new FunctionSignature(
        qnClearACL,
        "Removes all ACEs from the ACL of a resource or collection.",
        new SequenceType[] {
            new FunctionParameterSequenceType("path", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The path to the resource or collection whoose ACL you wish to clear.")
        },
        new SequenceType(Type.EMPTY, Cardinality.ZERO)
    );
    
    public final static FunctionSignature FNS_CHMOD = new FunctionSignature(
        qnChMod,
        "Changes the mode of a resource or collection.",
        new SequenceType[] {
            new FunctionParameterSequenceType("path", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The path to the resource or collection whoose mode you wish to set"),
            new FunctionParameterSequenceType("mode", Type.STRING, Cardinality.EXACTLY_ONE, "The mode to set on the resource or collection e.g. 'rwxrwxrwx'"),
        },
        new SequenceType(Type.EMPTY, Cardinality.ZERO)
    );
    
    public final static FunctionSignature FNS_CHOWN = new FunctionSignature(
        qnChOwn,
        "Changes the owner of a resource or collection.",
        new SequenceType[] {
            new FunctionParameterSequenceType("path", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The path to the resource or collection whoose owner you wish to set"),
            new FunctionParameterSequenceType("owner", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the user owner to set on the resource or collection e.g. 'guest'. You may also provide a group owner, by using the syntax 'user:group' if you wish."),
        },
        new SequenceType(Type.EMPTY, Cardinality.ZERO)
    );
    
    public final static FunctionSignature FNS_CHGRP = new FunctionSignature(
        qnChGrp,
        "Changes the group owner of a resource or collection.",
        new SequenceType[] {
            new FunctionParameterSequenceType("path", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The path to the resource or collection whoose group owner you wish to set"),
            new FunctionParameterSequenceType("group-name", Type.STRING, Cardinality.EXACTLY_ONE, "The name of the user group owner to set on the resource or collection e.g. 'guest'"),
        },
        new SequenceType(Type.EMPTY, Cardinality.ZERO)
    );
    
    public final static FunctionSignature FNS_HAS_ACCESS = new FunctionSignature(
        qnHasAccess,
        "Checks whether the current user has access to the resource or collection.",
        new SequenceType[] {
            new FunctionParameterSequenceType("path", Type.ANY_URI, Cardinality.EXACTLY_ONE, "The path to the resource or collection whoose acess of which you wish to check"),
            new FunctionParameterSequenceType("mode", Type.STRING, Cardinality.EXACTLY_ONE, "The partial mode to check against the resource or collection e.g. 'rwx'")
        },
        new SequenceType(Type.BOOLEAN, Cardinality.EXACTLY_ONE)
     );
    
    public final static FunctionSignature FNS_MODE_TO_OCTAL = new FunctionSignature(
        qnModeToOctal,
        "Converts a mode string e.g. 'rwxrwxrwx' to an octal number e.g. 0777.",
        new SequenceType[] {
            new FunctionParameterSequenceType("mode", Type.STRING, Cardinality.EXACTLY_ONE, "The mode to convert to an octal string.")
        },
        new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
    );
    
    public final static FunctionSignature FNS_OCTAL_TO_MODE = new FunctionSignature(
        qnOctalToMode,
        "Converts an octal string e.g. '0777' to a mode string e.g. 'rwxrwxrwx'.",
        new SequenceType[] {
            new FunctionParameterSequenceType("octal", Type.STRING, Cardinality.EXACTLY_ONE, "The octal string to convert to a mode.")
        },
        new SequenceType(Type.STRING, Cardinality.EXACTLY_ONE)
    );

    final static char OWNER_GROUP_SEPARATOR = ':';

    public PermissionsFunction(final XQueryContext context, final FunctionSignature signature) {
        super(context, signature);
    }


    @Override
    public Sequence eval(final Sequence[] args, final Sequence contextSequence) throws XPathException {

        Sequence result = Sequence.EMPTY_SEQUENCE;

        if(isCalledAs(qnModeToOctal.getLocalName())) {
            final String mode = args[0].itemAt(0).getStringValue();
            result = functionModeToOctal(mode);
        } else if(isCalledAs(qnOctalToMode.getLocalName())) {
            final String octal = args[0].itemAt(0).getStringValue();
            result = functionOctalToMode(octal);
        } else {
        
            //all functions below take a path as the first arg
            final XmldbURI pathUri = ((AnyURIValue)args[0].itemAt(0)).toXmldbURI();

            try {
                if(isCalledAs(qnGetPermissions.getLocalName())) {
                    result = functionGetPermissions(pathUri);
                } else if(isCalledAs(qnAddUserACE.getLocalName()) || isCalledAs(qnAddGroupACE.getLocalName())) {
                    final ACE_TARGET target = isCalledAs(qnAddUserACE.getLocalName()) ? ACE_TARGET.USER : ACE_TARGET.GROUP;
                    final String name = args[1].getStringValue();
                    final ACE_ACCESS_TYPE access_type = args[2].effectiveBooleanValue() ? ACE_ACCESS_TYPE.ALLOWED : ACE_ACCESS_TYPE.DENIED;
                    final String mode = args[3].itemAt(0).getStringValue();
                    result = functionAddACE(pathUri, target, name, access_type, mode);
                } else if(isCalledAs(qnInsertUserACE.getLocalName()) || isCalledAs(qnInsertGroupACE.getLocalName())) {
                    final ACE_TARGET target = isCalledAs(qnInsertUserACE.getLocalName()) ? ACE_TARGET.USER : ACE_TARGET.GROUP;
                    final int index = ((Integer)args[1].itemAt(0).toJavaObject(Integer.class));
                    final String name = args[2].getStringValue();
                    final ACE_ACCESS_TYPE access_type = args[3].effectiveBooleanValue() ? ACE_ACCESS_TYPE.ALLOWED : ACE_ACCESS_TYPE.DENIED;
                    final String mode = args[4].itemAt(0).getStringValue();
                    result = functionInsertACE(pathUri, index, target, name, access_type, mode);
                } else if(isCalledAs(qnModifyACE.getLocalName())) {
                    final int index = ((Integer)args[1].itemAt(0).toJavaObject(Integer.class));
                    final ACE_ACCESS_TYPE access_type = args[2].effectiveBooleanValue() ? ACE_ACCESS_TYPE.ALLOWED : ACE_ACCESS_TYPE.DENIED;
                    final String mode = args[3].itemAt(0).getStringValue();
                    result = functionModifyACE(pathUri, index, access_type, mode);
                } else if(isCalledAs(qnRemoveACE.getLocalName())) {
                    final int index = ((Integer)args[1].itemAt(0).toJavaObject(Integer.class));
                    result = functionRemoveACE(pathUri, index);
                } else if(isCalledAs(qnClearACL.getLocalName())) {
                    result = functionClearACL(pathUri);
                } else if(isCalledAs(qnChMod.getLocalName())) {
                    final String mode = args[1].itemAt(0).getStringValue();
                    result = functionChMod(pathUri, mode);
                } else if(isCalledAs(qnChOwn.getLocalName())) {
                    final String owner = args[1].itemAt(0).getStringValue();
                    result = functionChOwn(pathUri, owner);
                }  else if(isCalledAs(qnChGrp.getLocalName())) {
                    final String groupname = args[1].itemAt(0).getStringValue();
                    result = functionChGrp(pathUri, groupname);
                } else if(isCalledAs(qnHasAccess.getLocalName())) {
                    final String mode = args[1].itemAt(0).getStringValue();
                    result = functionHasAccess(pathUri, mode);
                }
            } catch(final PermissionDeniedException pde) {
              throw new XPathException(this, pde);
            }
        }

        return result;
    }

    private org.exist.memtree.DocumentImpl functionGetPermissions(final XmldbURI pathUri) throws XPathException {
        try {
            return permissionsToXml(getPermissions(pathUri));
        } catch(final PermissionDeniedException pde) {
            throw new XPathException(this, "Permission to retrieve permissions is denied for user '" + context.getSubject().getName() + "' on '" + pathUri.toString() + "': " + pde.getMessage(), pde);
        }
    }

    private Sequence functionAddACE(final XmldbURI pathUri, final ACE_TARGET target, final String name, final ACE_ACCESS_TYPE access_type, final String mode) throws PermissionDeniedException {
        PermissionFactory.updatePermissions(context.getBroker(), pathUri, new PermissionModifier(){
            @Override
            public void modify(final Permission permission) throws PermissionDeniedException {
                if(permission instanceof SimpleACLPermission) {
                    //add the ace
                    final SimpleACLPermission aclPermission = ((SimpleACLPermission)permission);
                    aclPermission.addACE(access_type, target, name, mode);
                } else {
                    throw new PermissionDeniedException("ACL like permissions have not been enabled");
                }
            }
        });
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence functionInsertACE(final XmldbURI pathUri, final int index, final ACE_TARGET target, final String name, final ACE_ACCESS_TYPE access_type, final String mode) throws PermissionDeniedException {
        PermissionFactory.updatePermissions(context.getBroker(), pathUri, new PermissionModifier(){
            @Override
            public void modify(final Permission permission) throws PermissionDeniedException {
                if(permission instanceof SimpleACLPermission) {
                    //insert the ace
                    final SimpleACLPermission aclPermission = ((SimpleACLPermission)permission);
                    aclPermission.insertACE(index, access_type, target, name, mode);
                } else {
                    throw new PermissionDeniedException("ACL like permissions have not been enabled");
                }
            }
        });
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence functionModifyACE(final XmldbURI pathUri, final int index, final ACE_ACCESS_TYPE access_type, final String mode) throws PermissionDeniedException {
        PermissionFactory.updatePermissions(context.getBroker(), pathUri, new PermissionModifier(){
            @Override
            public void modify(final Permission permission) throws PermissionDeniedException {
                if(permission instanceof SimpleACLPermission) {
                    //insert the ace
                    final SimpleACLPermission aclPermission = ((SimpleACLPermission)permission);
                    aclPermission.modifyACE(index, access_type, mode);
                } else {
                    throw new PermissionDeniedException("ACL like permissions have not been enabled");
                }
            }
        });
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence functionRemoveACE(final XmldbURI pathUri, final int index) throws PermissionDeniedException {
        PermissionFactory.updatePermissions(context.getBroker(), pathUri, new PermissionModifier(){
            @Override
            public void modify(final Permission permission) throws PermissionDeniedException {
                if(permission instanceof SimpleACLPermission) {
                    //remove the ace
                    final SimpleACLPermission aclPermission = ((SimpleACLPermission)permission);
                    aclPermission.removeACE(index);
                } else {
                    throw new PermissionDeniedException("ACL like permissions have not been enabled");
                }
            }
        });
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence functionClearACL(final XmldbURI pathUri) throws PermissionDeniedException {
        PermissionFactory.updatePermissions(context.getBroker(), pathUri, new PermissionModifier(){
            @Override
            public void modify(final Permission permission) throws PermissionDeniedException {
                if(permission instanceof SimpleACLPermission) {
                    //clear the acl
                    final SimpleACLPermission aclPermission = ((SimpleACLPermission)permission);
                    aclPermission.clear();
                } else {
                    throw new PermissionDeniedException("ACL like permissions have not been enabled");
                }
            }
        });
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence functionChMod(final XmldbURI pathUri, final String modeStr) throws PermissionDeniedException {
        PermissionFactory.updatePermissions(context.getBroker(), pathUri, new PermissionModifier(){
            @Override
            public void modify(final Permission permission) throws PermissionDeniedException {
                try {
                    permission.setMode(modeStr);
                } catch(final SyntaxException se) {
                    throw new PermissionDeniedException("Unrecognised mode syntax: " + se.getMessage(), se);
                }
            }
        });
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence functionChOwn(final XmldbURI pathUri, final String owner) throws PermissionDeniedException {
        PermissionFactory.updatePermissions(context.getBroker(), pathUri, new PermissionModifier(){
            @Override
            public void modify(final Permission permission) throws PermissionDeniedException {

                if(owner.indexOf(OWNER_GROUP_SEPARATOR) > -1) {
                    permission.setOwner(owner.substring(0, owner.indexOf((OWNER_GROUP_SEPARATOR))));
                    permission.setGroup(owner.substring(owner.indexOf(OWNER_GROUP_SEPARATOR) + 1));
                } else {
                    permission.setOwner(owner);
                }
            }
        });
        return Sequence.EMPTY_SEQUENCE;
    }

    private Sequence functionChGrp(final XmldbURI pathUri, final String groupname) throws PermissionDeniedException {
        PermissionFactory.updatePermissions(context.getBroker(), pathUri, new PermissionModifier(){
            @Override
            public void modify(final Permission permission) throws PermissionDeniedException {
                permission.setGroup(groupname);
            }
        });
        return Sequence.EMPTY_SEQUENCE;
    }
    
    private Sequence functionHasAccess(final XmldbURI pathUri, final String modeStr) throws XPathException {
        if(modeStr == null || modeStr.length() == 0 || modeStr.length() > 3) {
            throw new XPathException("Mode string must be partial i.e. rwx not rwxrwxrwx");
        }
        
        int mode = 0;
        if(modeStr.indexOf(Permission.READ_CHAR) > -1) {
            mode |= Permission.READ;
        }
        if(modeStr.indexOf(Permission.WRITE_CHAR) > -1) {
            mode |= Permission.WRITE;
        }
        if(modeStr.indexOf(Permission.EXECUTE_CHAR) > -1) {
            mode |= Permission.EXECUTE;
        }
        
        final Subject effectiveSubject = context.getEffectiveUser();
        try {
            final boolean hasAccess = getPermissions(pathUri).validate(effectiveSubject, mode);
            return BooleanValue.valueOf(hasAccess);
        } catch(final XPathException xpe) {
            LOG.error(xpe.getMessage(), xpe);
            return BooleanValue.FALSE;
        } catch(final PermissionDeniedException pde) {
            return BooleanValue.FALSE;
        }
    }
    
    private Sequence functionModeToOctal(final String modeStr) throws XPathException {
        try {
            final int mode = AbstractUnixStylePermission.simpleSymbolicModeToInt(modeStr);
            final String octal = mode == 0 ? "0" : "0" + Integer.toOctalString(mode);
            return new StringValue(octal);
        } catch(final SyntaxException se) {
            throw new XPathException(se.getMessage(), se);
        }
    }
    
    private Sequence functionOctalToMode(final String octal) {
        final int mode = Integer.parseInt(octal, 8);
        return new StringValue(AbstractUnixStylePermission.modeToSimpleSymbolicMode(mode));
    }
    
    private Permission getPermissions(final XmldbURI pathUri) throws XPathException, PermissionDeniedException {
        final Permission permissions;
        final Collection col = context.getBroker().getCollection(pathUri);
        if(col != null) {
            permissions = col.getPermissionsNoLock();
        } else {
            final DocumentImpl doc = context.getBroker().getResource(pathUri, Permission.READ);
            if(doc != null) {
                permissions = doc.getPermissions();
            } else {
                throw new XPathException("Resource or collection '" + pathUri.toString() + "' does not exist.");
            }
        }

        return permissions;
    }

    private org.exist.memtree.DocumentImpl permissionsToXml(final Permission permission) {
        final MemTreeBuilder builder = context.getDocumentBuilder();
        builder.startDocument();

        builder.startElement(new QName("permission", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX), null);
        builder.addAttribute(new QName("owner"), permission.getOwner().getName());
        builder.addAttribute(new QName("group"), permission.getGroup().getName());
        builder.addAttribute(new QName("mode"), permission.toString());

        if(permission instanceof SimpleACLPermission) {
            final SimpleACLPermission aclPermission = (SimpleACLPermission)permission;
            builder.startElement(new QName("acl", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX), null);
            builder.addAttribute(new QName("entries"), String.valueOf(aclPermission.getACECount()));

            for(int i = 0; i < aclPermission.getACECount(); i++) {
                builder.startElement(new QName("ace", SecurityManagerModule.NAMESPACE_URI, SecurityManagerModule.PREFIX), null);
                builder.addAttribute(new QName("index"), String.valueOf(i));
                builder.addAttribute(new QName("target"), aclPermission.getACETarget(i).name());
                builder.addAttribute(new QName("who"), aclPermission.getACEWho(i));
                builder.addAttribute(new QName("access_type"), aclPermission.getACEAccessType(i).name());
                builder.addAttribute(new QName("mode"), aclPermission.getACEModeString(i));
                builder.endElement();
            }

            builder.endElement();
        }

        builder.endElement();

        builder.endDocument();

        return builder.getDocument();
    }
}
