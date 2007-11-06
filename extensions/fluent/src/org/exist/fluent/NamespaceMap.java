package org.exist.fluent;

import java.util.*;

/**
 * A map of short keys to namespace uris that can be cascaded.  Use the empty string as the
 * key for the default namespace.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public class NamespaceMap implements Cloneable {
	
	private static class ReservedMap extends NamespaceMap {
		public ReservedMap() {
			super();
			// bootstrap root node directly to avoid checks
			map = new TreeMap<String, String>();
			map.put("xml", "http://www.w3.org/XML/1998/namespace");
			map.put("xmlns", "http://www.w3.org/2000/xmlns/");
			assert parent == null;
		}
		@Override public Map<String,String> getCombinedMap() {
			return new TreeMap<String,String>();
		}
	}
	
	/**
	 * Reserved keys and URIs, always declared and not overwriteable or overrideable.
	 * Note that the reserved map's parent will be <code>null</code>, even though
	 * it goes through the common constructor, since at construction time the
	 * <code>RESERVED</code> field has not yet been initialized.  It is the only map
	 * that will have a <code>null</code> parent.
	 */
	private static final NamespaceMap RESERVED = new ReservedMap();
	
	protected Map<String, String> map;
	
	/**
	 * The parent map from which bindings are inherited.  It cannot be modified
	 * or accessed through its children.
	 */
	protected NamespaceMap parent;

	/**
	 * Return whether the given prefix is reserved by the XML spec and should not be
	 * manually bound to namespaces.
	 *
	 * @param prefix the prefix to check
	 * @return <code>true</code> if the prefix is reserved, <code>false</code> if it's available for binding
	 */
	public static boolean isReservedPrefix(String prefix) {
		return RESERVED.get(prefix) != null;
	}
	
	/**
	 * Create a new namespace map with no inherited bindings.  Immediate bindings can
	 * be specified as a list of key-URI pairs.
	 * @param args a list interleaving keys and their associated URIs; its length must be even
	 */
	public NamespaceMap(String... args) {
		if (args.length % 2 != 0) throw new IllegalArgumentException("incomplete pair, " + args.length + " arguments received");
		for (int i = 0; i < args.length; i+=2) {
			put(args[i], args[i+1]);
		}
		parent = RESERVED;
	}
	
	/**
	 * Create a namespace map inheriting from this one.
	 * If this new map lacks a binding, it will be looked up in the parent.
	 * New bindings will always be entered in this map, and may override
	 * the parent's bindings.
	 * 
	 * @return an extension of this map
	 */
	public NamespaceMap extend() {
		NamespaceMap extension = new NamespaceMap();
		extension.parent = this;
		return extension;
	}
	
	/**
	 * Return a clone of this map.  The immediate bindings are cloned, but the ineritance
	 * chain remains unaltered.
	 * 
	 * @return a combined clone of this map cascade
	 */
	@Override
	public NamespaceMap clone() {
		try {
			NamespaceMap clone = (NamespaceMap) super.clone();
			if (this.map != null) clone.map = new HashMap<String,String>(this.map);
			return clone;
		} catch (CloneNotSupportedException e) {
			throw new RuntimeException("unexpected exception", e);
		}
	}
	
	/**
	 * Sever this namespace map from its parents.  The contents are collapsed prior to
	 * the map being severed, so the mappings are not affected.  However, any future
	 * changes to the previous parents will have no effect on this map.  Calling {@link #clear()}
	 * on a severed map is guaranteed to clear all bindings.
	 */
	public void sever() {
		map = getCombinedMap();
		parent = RESERVED;
	}

	/**
	 * Get the URI bound to the given key, either in this map or the closest inherited one.
	 * If the key is not bound, return <code>null</code>.
	 *
	 * @param key the key to look up
	 * @return the bound URI or <code>null</code> if none
	 */
	public String get(String key) {
		if (key == null) throw new NullPointerException("null key");
		String result = null;
		if (result == null && map != null) result = map.get(key);
		if (result == null && parent != null) result = parent.get(key);
		return result;
	}
	
	private static void checkKey(String key) {
		if (key == null) throw new NullPointerException("null key");
		if (RESERVED.get(key) != null) throw new IllegalArgumentException("reserved key '" + key + "'");
	}
	
	/**
	 * Bind the given key to the given URI in this map.  If the key was already bound in this
	 * map, the binding is overwritten.  If the key was bound in an inherited map, it is
	 * overriden.
	 *
	 * @param key the key to use
	 * @param uri the namespace URI to bind
	 */
	public void put(String key, String uri) {
		checkKey(key);
		if (RESERVED.map.containsValue(uri)) throw new IllegalArgumentException("reserved URI '" + uri + "'");
		if (map == null) map = new TreeMap<String, String>();
		map.put(key, uri);
	}

	/**
	 * Remove any binding for the given key from the map.  Has no effect if the key is
	 * not bound in this map.  If the key binding is inherited, the binding is not affected.
	 *
	 * @param key the key to remove
	 */
	public void remove(String key) {
		checkKey(key);
		if (map != null) map.remove(key);
	}
	
	/**
	 * Clear all bindings from this map.  Does not affect any inherited bindings.
	 */
	public void clear() {
		if (map != null) map.clear();
	}
	
	/**
	 * Put all bindings from the given map into this one.  Bindings inherited by
	 * the given map are included.  Existing bindings may be overwritten or
	 * overriden, as appropriate.
	 *
	 * @param that the map to copy bindings from
	 */
	public void putAll(NamespaceMap that) {
		if (map == null) map = new TreeMap<String, String>();
		map.putAll(that.getCombinedMap());
	}
	
	/**
	 * Sever inheritance connections and replace all bindings with ones from the given
	 * map.  Equivalent to calling {@link #sever()}, {@link #clear()} and {@link #putAll(NamespaceMap)}
	 * in sequence.
	 *
	 * @param that the map to copy bindings from
	 */
	public void replaceWith(NamespaceMap that) {
		parent = RESERVED;
		map = new HashMap<String,String>(that.getCombinedMap());
	}
	
	/**
	 * Return a realized map of keys to URIs that combines all information inherited
	 * from parents.  This is effectively the map that is used for lookups, but it is
	 * normally kept in virtual form for efficiency.  The map returned is a copy and
	 * is safe for mutation.  The map does not include reserved bindings.
	 *
	 * @return a combined map of keys to namespace uris
	 */
	public Map<String,String> getCombinedMap() {
		Map<String, String> all = parent.getCombinedMap();
		if (map != null) all.putAll(map);
		return all;
	}
	
	@Override public boolean equals(Object o) {
		if (!(o instanceof NamespaceMap)) return false;
		return getCombinedMap().equals(((NamespaceMap) o).getCombinedMap());
	}
	
	@Override public int hashCode() {
		return getCombinedMap().hashCode();
	}
}
