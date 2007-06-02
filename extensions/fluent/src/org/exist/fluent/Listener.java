package org.exist.fluent;

/**
 * The supertype for all listeners on database documents and folders.
 *
 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
 */
public interface Listener {
	
	/**
	 * The superclass for all database events.
	 *
	 * @author <a href="mailto:piotr@ideanest.com">Piotr Kaminski</a>
	 */
	public abstract class Event {
		/**
		 * The trigger that caused this event.
		 */
		public final Trigger trigger;
		
		/**
		 * The path of the event's subject, either a document or a folder.
		 */
		public final String path;
		
		/**
		 * Construct a new event for the given trigger and path. 
		 *
		 * @param trigger the reason for the event
		 * @param path the path of the event's subject
		 */
		Event(Trigger trigger, String path) {
			this.trigger = trigger;
			this.path = path;
		}
		
		/**
		 * Construct a new event, taking the trigger and path from the given event key.
		 *
		 * @param key the key to copy data from
		 */
		Event(ListenerManager.EventKey key) {
			this.trigger = key.trigger;
			this.path = key.path;
		}
		
		@Override
		public boolean equals(Object o) {
			if (o == null || this.getClass() != o.getClass()) return false;
			Event that = (Event) o;
			return
				this.trigger == that.trigger &&
				this.path.equals(that.path);
		}
		
		@Override
		public int hashCode() {
			return path.hashCode() * 37 + trigger.hashCode();
		}
		
		@Override
		public String toString() {
			return "db.Event(" + trigger + ", " + path + ")";
		}
	}
	
}
