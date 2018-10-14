package org.exist.fluent;

import java.lang.ref.WeakReference;
import java.util.*;

import static org.exist.util.ThreadUtils.newGlobalThread;

class WeakMultiValueHashMap<K,V> {

	/**
	 * Number of puts between dead reference sweep requests.
	 */
	private static final int SWEEP_COUNT = 100;
	
	private final Map<K, Collection<WeakReference<V>>> map = new HashMap<>();
	private int putCounter;
	
	public synchronized void put(K key, V value) {
		final Collection<WeakReference<V>> list = map.computeIfAbsent(key, k -> new LinkedList<>());
		list.add(new WeakReference<V>(value));
		putCounter = (putCounter + 1) % SWEEP_COUNT;
		if (putCounter == 0) SWEEPER.clean(this);
	}
	
	public synchronized void remove(K key) {
		map.remove(key);
	}
	
	public synchronized boolean containsKey(K key) {
		final Collection<WeakReference<V>> list = map.get(key);
		if (list == null) return false;
		for (Iterator<WeakReference<V>> it = list.iterator(); it.hasNext(); ) {
			if (it.next().get() != null) return true;
			it.remove();
		}
		map.remove(key);
		return false;
	}
	
	@SuppressWarnings("unchecked")
	public synchronized Iterable<V> get(final K key) {
		final Collection<WeakReference<V>> list = map.get(key);
		if (list == null) {
			return Database.EMPTY_ITERABLE;
		}
		return () -> new WeakMultiValueHashMapIterator(key, list);
	}

	private class WeakMultiValueHashMapIterator implements Iterator<V> {
		private final K key;
		private final Collection<WeakReference<V>> list;
		private final Iterator<WeakReference<V>> it;
		private V nextItem;

		public WeakMultiValueHashMapIterator(final K key, final Collection<WeakReference<V>> list) {
			this.key = key;
			this.list = list;
			this.it = list.iterator();
			advance();
		}

		private void advance() {
			synchronized(WeakMultiValueHashMap.this) {
				while(nextItem == null && it.hasNext()) {
					nextItem = it.next().get();
					if (nextItem == null) it.remove();
				}
				if (!it.hasNext() && list.isEmpty()) map.remove(key);
			}
		}
		public boolean hasNext() {
			advance();
			return nextItem != null;
		}
		public V next() {
			advance();
			if (nextItem == null) throw new NoSuchElementException();
			V item = nextItem;
			nextItem = null;
			return item;
		}
		public void remove() {
			throw new UnsupportedOperationException();
		}
	}
	
	private static final Sweeper SWEEPER = new Sweeper();
	static {
		Thread thread = newGlobalThread("fluent.weakMultiValueHashMap.sweeper", SWEEPER);
		thread.setPriority(Thread.NORM_PRIORITY-3);
		thread.setDaemon(true);
		thread.start();
	}
	
	private static class Sweeper implements Runnable {
		private final LinkedList<WeakMultiValueHashMap<?,?>> inbox = new LinkedList<WeakMultiValueHashMap<?,?>>();
		public synchronized void clean(WeakMultiValueHashMap<?,?> map) {
			inbox.add(map);
			notifyAll();
		}
		public void run() {
			try {
				while(true) {
					WeakMultiValueHashMap<?,?> map;
					synchronized(this) {
						while(inbox.isEmpty()) wait();
						map = inbox.removeFirst();
						for (Iterator<WeakMultiValueHashMap<?,?>> it = inbox.iterator(); it.hasNext(); ) {
							if (it.next() == map) it.remove();
						}
					}
					if (map != null) {
						synchronized(map) {
							for (Iterator<? extends Collection<? extends WeakReference<?>>> it = map.map.values().iterator(); it.hasNext(); ) {
								Collection<? extends WeakReference<?>> list = it.next();
								for (Iterator<? extends WeakReference<?>> it2 = list.iterator(); it2.hasNext(); ) {
									if (it2.next().get() == null) it2.remove();
								}
								if (list.isEmpty()) it.remove();
							}
						}
					}
				}
			} catch (InterruptedException e) {
			}
		}
	}
}
