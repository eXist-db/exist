package org.exist.fluent;

import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.exist.collections.Collection;
import org.exist.security.PermissionDeniedException;
import org.exist.storage.DBBroker;
import org.exist.util.LockException;
import org.exist.xmldb.XmldbURI;

/**
 * An actual or virtual name for a document, augmented with instructions for processing
 * in case of duplication.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public abstract class Name {
	
	protected static final Random rand = new Random();
	
	protected String givenName, oldName;
	protected Collection context;
        private final Database db;
	
	private Name(Database db) {
            this.db = db;
        }
	
	Folder stripPathPrefix(Folder base) {
		return base;
	}
	
	private static abstract class SpecifiedName extends Name {
		protected String specifiedName;
		SpecifiedName(Database db, String specifiedName) {
                        super(db);
			this.specifiedName = specifiedName;
		}
		Folder stripPathPrefix(Folder base) {
			int k = specifiedName.lastIndexOf('/');
			if (k == -1) {
				return base;
			} else {
				Folder target = base.children().create(specifiedName.substring(0, k));
				specifiedName = specifiedName.substring(k+1);
				return target;
			}
		}		
	}
	
	void setOldName(String oldName) {assert this.oldName == null; this.oldName = oldName;}
	void setContext(Collection context) {assert this.context == null; this.context = context;}
	
	/**
	 * Get the computed value of this name.  Once computed, the value is cached.
	 *
	 * @return the value of this name
	 */
	public String get() {
		if (givenName == null) eval();
		return givenName;
	}
	
	protected abstract void eval();
	protected abstract String def();
	
	@Override public String toString() {
		StringBuilder buf = new StringBuilder();
		if (givenName != null) buf.append(givenName).append(" ");
		buf.append("{");
		buf.append(def());
		buf.append("}");
		return buf.toString();
	}
	
	protected boolean existsInContext(String proposedName) {
		if (proposedName == null || proposedName.length() == 0) throw new IllegalArgumentException("name null or empty");
		XmldbURI proposedUri = XmldbURI.create(proposedName);
                
                DBBroker _broker = null;
                try {
                    _broker = db.acquireBroker();
                    return context.hasDocument(_broker, proposedUri) || context.hasChildCollection(_broker, proposedUri);
                } catch(final PermissionDeniedException | LockException e) {
                    throw new DatabaseException(e.getMessage(), e);
                } finally {
                    if(_broker != null) {
                        db.releaseBroker(_broker);
                    }   
                }
	}
	
	protected void evalInsert(String proposedName) {
		if (existsInContext(proposedName)) throw new DatabaseException("entry with name " + proposedName + " already exists in destination");
		givenName = proposedName;
	}
	
	private static final Pattern NAME_PATTERN = Pattern.compile("(.*)($[0-9a-z]+)?(\\..+)?");
	protected void evalDeconflict(String proposedName) {
		if (!existsInContext(proposedName)) {
			givenName = proposedName;
			return;
		}
		
		Matcher matcher = NAME_PATTERN.matcher(proposedName);
		boolean matchResult = matcher.matches();
		assert matchResult;
		String baseName = matcher.group(1);
		if (baseName.length() > 0) baseName += "$";
		String suffix = matcher.group(3);
		if (suffix == null) suffix = "";
		
		evalGenerate(baseName, suffix);
	}
	
	protected void evalGenerate(String baseName, String suffix) {
		synchronized(rand) {
			do {
				givenName = baseName + Integer.toHexString(rand.nextInt()) + suffix;
			} while (existsInContext(givenName));
		}
	}
	
	/**
	 * Generate a random name that will not conflict with anything else in the destination folder.
	 *
	 * @return a random name unique within the target folder
	 */
	public static Name generate(Database db) {
		return generate(db, "");
	}
	
	/**
	 * Generate a random name with the given suffix that will not conflict with anything else in 
	 * the destination folder.
	 *
	 * @param suffix the string to append to the random name, e.g. ".xml"
	 * @return a random name unique within the target folder and ending with the given suffix
	 */
	public static Name generate(Database db, final String suffix) {
		return new Name(db) {
			@Override protected void eval() {evalGenerate("", suffix);}
			@Override public String def() {return "generate" + (suffix.length() == 0 ? "" : " " + suffix);}
		};
	}
	
	/**
	 * Keep the existing name of the source item if it is unique in the destination folder, otherwise
	 * adjust it as per the rules of {@link #adjust(Database, String)}.
	 *
	 * @return the existing name if unique, otherwise a unique variation of the existing name
	 */
	public static Name keepAdjust(Database db) {
		return new Name(db) {
			@Override protected void eval() {evalDeconflict(oldName);}
			@Override protected String def() {return "adjust " + (oldName == null ? "old name" : oldName);}
		};
	}
	
	/**
	 * Try to use the given name but, if it conflicts with anything in the destination folder, add
	 * a random component that will make it unique.  If a random component needs to be added,
	 * it is inserted between the main part of the name and its dotted suffix, separated by
	 * a '$' sign.  (If the name has no suffix, the random component is simply appended to the
	 * name.)  If the given name already has a random component in the format described (perhaps
	 * resulting from a previous 'adjustment'), it is first removed before a new one is selected.
	 *
	 * @param name the desired name
	 * @return if the given name is unique, the name; otherwise, a unique variation on the given name
	 */
	public static Name adjust(Database db, String name) {
		return new SpecifiedName(db, name) {
			@Override protected void eval() {evalDeconflict(specifiedName);}
			@Override protected String def() {return "adjust " + specifiedName;}
		};
	}

	/**
	 * Keep the existing name of the source item, overwriting any document with the same name
	 * in the destination folder as per the rules for {@link #overwrite(Database, String)}.
	 *
	 * @return the existing name that will be used whether it's unique or not
	 */
	public static Name keepOverwrite(Database db) {
		return new Name(db) {
			@Override protected void eval() {givenName = oldName;}
			@Override protected String def() {return "overwrite " + (oldName == null ? "old name" : oldName);}
		};
	}
	
	/**
	 * Use the given name whether it is unique or not.  If the name is already used for another
	 * document in the destination folder, that document will be overwritten.  If the name is already
	 * used for a child folder of the destination folder, the folder will not be affected and the
	 * operation will throw an exception.
	 *
	 * @param name the desired name
	 * @return the desired name that will be used whether it's unique or not
	 */
	public static Name overwrite(Database db, String name) {
		return new SpecifiedName(db, name) {
			@Override protected void eval() {givenName = specifiedName;}
			@Override protected String def() {return "overwrite " + specifiedName;}
		};
	}
	
	/**
	 * Keep the existing name of the source item, believed to be unique in the destination folder.
	 * The name follows the rules given in {@link #create(Database, String)}.
	 *
	 * @return the existing name, with a stipulation that any operation using it will fail if it's a duplicate
	 */
	public static Name keepCreate(Database db) {
		return new Name(db) {
			@Override protected void eval() {evalInsert(oldName);}
			@Override protected String def() {return "create " + (oldName == null ? "old name" : oldName);}
		};
	}
	
	/**
	 * Use the given name that is believed to be unique.  If the given name is already used in the
	 * destination folder, the existing owner of the name will not be affected and the operation
	 * will throw an exception.
	 *
	 * @param name the desired name believed to be unique
	 * @return the desired name, with a stipulation that any operation using it will fail if it's a duplicate
	 */
	public static Name create(Database db, String name) {
		return new SpecifiedName(db, name) {
			@Override protected void eval() {evalInsert(specifiedName);}
			@Override protected String def() {return "create " + specifiedName;}
		};
	}
	
}
