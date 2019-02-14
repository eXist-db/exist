package org.exist.fluent;

import java.util.*;
import java.util.regex.*;

import org.exist.security.Permission;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;

/**
 * A named resource in the contents tree of the database:  either a folder or a document.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public abstract class NamedResource extends Resource {
	
	/**
	 * The metadata facet of a named resource.  Allows access to and manipulation of various aspects
	 * of a resource's metadata, including its permissions and various timestamps.
	 * NOTE:  The permissions part of the interface is at a bare minimum right now, until I gather
	 * more use cases and flesh it out.
	 */
	public static abstract class MetadataFacet {
		private static final Pattern INSTRUCTIONS_REGEX =
			Pattern.compile("(a|(u?g?o?){1,3})((=r?w?u?)|([-+](r?w?u?){1,3}))(,(a|(u?g?o?){1,3})((=r?w?u?)|([-+](r?w?u?){1,3})))*");
		private static final Pattern SEGMENT_REGEX = Pattern.compile("([augo]+)([-+=])([rwu]*)");
		
		private Permission permissions;
                private final Database db;

		protected MetadataFacet(Permission permissions, Database db) {
			this.permissions = permissions;
                        this.db = db;
		}
		
		/**
		 * Return the time at which this resource was originally created.
		 *
		 * @return the creation date of this resource
		 */
		public abstract Date creationDate();
		
		/**
		 * Return the user who owns this resource for purposes of permission management.
		 * 
		 * @return the owner of this resource
		 */
		public String owner() {return permissions.getOwner().getName();}
		
		/**
		 * Set the owner of this resource for purposes of permission management.
		 *
		 * @param owner the new owner of this resource
		 */
		public void owner(String owner) {
                    DBBroker broker = null;
                    try {
                        broker = db.acquireBroker();
                        permissions.setOwner(owner);
                    } catch(PermissionDeniedException pde) {
                        throw new DatabaseException(pde.getMessage(), pde);
                    } finally {
                        if(broker != null) {
                            db.releaseBroker(broker);
                        }
                    }
                }
		
		/**
		 * Return the group who has privileged access to this resource for purposes of permission management.
		 * 
		 * @return the owning group of this resource
		 */
		public String group() {return permissions.getGroup().getName();}
		
		/**
		 * Set the group that will have privileged access to this resource for purposes of permission management.
		 *
		 * @param group the new owning group of this resource
		 */
		public void group(String group) {
                    DBBroker broker = null;
                    try {
                        broker = db.acquireBroker();
                        permissions.setGroup(group);
                    } catch(PermissionDeniedException pde) {
                        throw new DatabaseException(pde.getMessage(), pde);
                    } finally {
                        if(broker != null) {
                            db.releaseBroker(broker);
                        }
                    }
                }
		
		/**
		 * Return whether the given subject has the given permission.  The "who" character refers to
		 * subjects as follows: <ul>
		 * <li>'u' stands for "user", the owner of the resource</li>
		 * <li>'g' stands for "group", the owning group of the resource</li>
		 * <li>'o' stands for "other", all users</li>
		 * <li>'a' stands for "all", a shortcut that refers to all 3 subjects above simultaneously</li></ul>
		 * The "what" character refers to the permissions to check: <ul>
		 * <li>'r' stands for read access</li>
		 * <li>'w' stands for write access</li>
		 * <li>'u' stands for update rights</li></ul>
		 * 
		 * @param who the subject to check for permission
		 * @param what the access right to check for
		 * @return <code>true</code> if the given subject has the given permission, <code>false</code> otherwise
		 */
		public boolean hasPermission(final char who, final char what) {
			int mask = convertPermissionBit(what);
			switch(who) {
				case Permission.ALL_CHAR:
					mask = mask | (mask << 3) | (mask << 6);
					break;

				case Permission.USER_CHAR:
					mask <<= 6;
					break;

				case Permission.GROUP_CHAR:
					mask <<= 3;
					break;

				case Permission.OTHER_CHAR:
					break;

				default:
					throw new IllegalArgumentException("illegal permission \"who\" code '" + who + "'");
			}
			return (permissions.getMode() & mask) == mask;
		}
		
		private int convertPermissionBit(char what) {
			switch(what) {
				case Permission.READ_CHAR: return Permission.READ;
				case Permission.WRITE_CHAR: return Permission.WRITE;
				case Permission.EXECUTE_CHAR: return Permission.EXECUTE;
				default: throw new IllegalArgumentException("illegal permission \"what\" code '" + what + "'");
			}
		}
		
		private int convertPermissionBits(String what) {
			int perms = 0;
			for (int i=0; i<what.length(); i++) perms |= convertPermissionBit(what.charAt(i));
			return perms;
		}
		
		/**
		 * Change the permissions of the underlying resource.  The format of the instructions is based upon
		 * that of the Unix chmod command:  one or more letters representing the subject to modify, followed
		 * by an operation sign, followed by zero or more letters representing the permissions to modify.  The
		 * subject and permission letters are the ones listed for {@link #hasPermission(char, char)}, except that
		 * the 'a' subject cannot be mixed with any of the other ones.  If multiple subjects or permissions are
		 * listed, they must be listed in the canonical order shown.  The operation signs are: <ul>
		 * <li>= to overwrite the permissions for the given subjects; if no permission letters follow the equals
		 *   sign, rescind all permissions for the given subjects</li>
		 * <li>+ to grant additional permissions for the given subjects; at least one permission letter must be
		 *   specified, and the given permissions will be added to any the subjects already possess</li>
		 * <li>- to rescind permissions from the given subjects; at least one permission letter must be specified,
		 *   and the given permissions will be rescinded from the given subjects, without affecting any other
		 *   permissions previously granted</li></ul>
		 * You can combine multiple such subjects/operation/permissions segments by separating them with
		 * commas (no spaces); they will be processed in the given order, so late segments can override the
		 * effects of earlier ones.  Some examples:<ul>
		 * <li><tt>u-w</tt> rescinds the write permission from the owner, write-protecting the resource</li>
		 * <li><tt>ug+rw,o=</tt> grants read and write permissions to the owner and group, leaves the owner's
		 *   and group's update permission set to its previous value, and rescinds all permissions from everybody else</li>
		 * <li><tt>a=r,u+w</tt> sets everyone to have only read permission, then grants write permission to just the
		 *   owner</li></ul>
		 * 
		 * @param instructions an instruction string encoding the desired changes to the permissions
		 */
		public void changePermissions(String instructions) {
                    if (!INSTRUCTIONS_REGEX.matcher(instructions).matches())
                            throw new IllegalArgumentException("bad permissions instructions: " + instructions);
                    StringTokenizer tokenizer = new StringTokenizer(instructions, ",");

                    try {
                        while (tokenizer.hasMoreTokens()) {
                                Matcher matcher = SEGMENT_REGEX.matcher(tokenizer.nextToken());
                                if (!matcher.matches()) throw new RuntimeException("internal error: illegal segment got through syntax regex, instruction string " + instructions);
                                int perms = convertPermissionBits(matcher.group(3));
                                int mask = 0;
                                boolean all = matcher.group(1).equals("a");
                                if (all || matcher.group(1).indexOf('u') != -1) mask |= perms << 6;
                                if (all || matcher.group(1).indexOf('g') != -1) mask |= perms << 3;
                                if (all || matcher.group(1).indexOf('o') != -1) mask |= perms;
                                int newPerms;
                                switch(matcher.group(2).charAt(0)) {
										case '=':
											newPerms = mask;
											break;
                                        case '+':
											newPerms = permissions.getMode() | mask;
											break;
                                        case '-':
											newPerms = permissions.getMode() & ~mask;
											break;
                                        default:
											throw new RuntimeException("internal error: illegal segment operator got through syntax regex, instruction string " + instructions);
                                }
                                permissions.setMode(newPerms);
                        }
                    } catch(PermissionDeniedException pde) {
                        throw new DatabaseException(pde.getMessage(), pde);
                    }
		}
		
		public String toString() {
			StringBuilder buf = new StringBuilder();
			if (permissions.getOwnerMode() == permissions.getGroupMode()
					&& permissions.getOwnerMode() == permissions.getOtherMode()) {
				appendPermissions('a', 'u', buf); buf.append(' ');
			} else {
				appendPermissions('u', 'u', buf);  buf.append(',');
				appendPermissions('g', 'g', buf);  buf.append(',');
				appendPermissions('o', 'o', buf);  buf.append(' ');
			}
			if (owner() != null) buf.append("u:").append(owner()).append(' ');
			if (group() != null) buf.append("g:").append(group()).append(' ');
			buf.append(creationDate());
			return buf.toString();
		}
		
		private void appendPermissions(char prefix, char who, StringBuilder buf) {
			buf.append(prefix).append('=');
			if (hasPermission(who, 'r')) buf.append('r');
			if (hasPermission(who, 'w')) buf.append('w');
			if (hasPermission(who, 'u')) buf.append('u');
		}
	}
	
	protected NamedResource(NamespaceMap namespaceBindings, Database db) {
		super(namespaceBindings, db);
	}
	
	/**
	 * Return the local name of this resource.  This name will never contain slashes.
	 *
	 * @return the local name of this resource
	 */
	public abstract String name();
	
	/**
	 * Return the absolute path of this resource.  This is the path of its parent folder
	 * plus its local name.
	 *
	 * @return the absolute path of this resource
	 */
	public abstract String path();
	
	/**
	 * Copy this resource to another location, potentially changing the copy's name in
	 * the process.
	 *
	 * @param destination the destination folder for the copy
	 * @param name the desired name for the copy
	 * @return the new copy of the resource
	 */
	public abstract NamedResource copy(Folder destination, Name name);
	
	/**
	 * Move this resource to another collection, potentially changing its name in the process.
	 * This object will refer to the resource in its new location after this method returns.
	 *
	 * @param destination the destination folder for the move
	 * @param name the desired name for the moved resource
	 */
	public abstract void move(Folder destination, Name name);
	
	/**
	 * Delete this resource from the database.
	 */
	public abstract void delete();
	
	/**
	 * Return the metadata facet for this resource, which lets you read and manipulate
	 * metadata such as ownership, access permissions, and creation/modification timestamps.
	 *
	 * @return the metadata facet for this resource
	 */
	public abstract MetadataFacet metadata();
}
