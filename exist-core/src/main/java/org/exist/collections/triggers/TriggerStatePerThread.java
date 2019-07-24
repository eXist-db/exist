/*
 *  eXist Open Source Native XML Database
 *  Copyright (C) 2006-2012 The eXist Project
 *  http://exist-db.org
 *
 *  This program is free software; you can redistribute it and/or
 *  modify it under the terms of the GNU Lesser General Public License
 *  as published by the Free Software Foundation; either version 2
 *  of the License, or (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU Lesser General Public License for more details.
 *
 *  You should have received a copy of the GNU Lesser General Public
 *  License along with this library; if not, write to the Free Software
 *  Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 *  $Id$
 */
package org.exist.collections.triggers;

import org.exist.xmldb.XmldbURI;
import org.exist.storage.txn.Txn;

/** Finite State Machine, managing the state of a Running trigger;
 * allows to avoid infinite recursions by forbidding another trigger to run
 * where there is already one; feature trigger_update .
 * I implemented that when a trigger is running , another trigger in the same
 * Thread cannot be fired .
 * There is a second condition that  when a trigger is running triggered by 
 * some document d, even the same trigger cannot run on a different document .

 * maybe TODO: apply "state" design pattern */
public class TriggerStatePerThread {

	public static final int NO_TRIGGER_RUNNING = (0);
	public static final int TRIGGER_RUNNING_PREPARE = (1);
	public static final int TRIGGER_RUNNING_FINISH = (2);
	
	private static ThreadLocal<TriggerState> triggerRunningState = new ThreadLocal<TriggerState>() {
	    protected synchronized TriggerState initialValue() {
	        return new TriggerState(NO_TRIGGER_RUNNING);
	    }
	};

	public static boolean verifyUniqueTriggerPerThreadBeforePrepare(
			DocumentTrigger trigger, XmldbURI modifiedDocument) {
		if (getTriggerRunningState() == NO_TRIGGER_RUNNING) {
			setTriggerRunningState(TRIGGER_RUNNING_PREPARE, trigger,
					modifiedDocument);
			return true;
		} else {
			return false;
		}
	}
	
	/**
	 * @param trigger the trigger that got modified
	 * @param modifiedDocument the document whose modification triggered the trigger
	 * @return true if successfully validated otherwise false
	 */
	public static boolean verifyUniqueTriggerPerThreadBeforeFinish(
			DocumentTrigger trigger, XmldbURI modifiedDocument) {

		// another trigger is already running
		final DocumentTrigger runningTrigger = getRunningTrigger();
		if ( runningTrigger != null && 
			trigger != runningTrigger ) {
			return false;
		}

		// current trigger is busy with another document
		if(getModifiedDocument() != null && !modifiedDocument.equals(getModifiedDocument()))
		{
			return false;
		}
		
		if (getTriggerRunningState() == TRIGGER_RUNNING_PREPARE) {
			setTriggerRunningState(TRIGGER_RUNNING_FINISH, trigger,
					modifiedDocument);
			return true;
		} else {
			return false;
		}
	}
	
	public static class TriggerState {
		private int state;
		private DocumentTrigger currentTrigger;
		private Txn txn;
		private XmldbURI modifiedDocument;
		public TriggerState(int state) {
			super();
			this.setState(state, null, null);
		}
		
		private void setState(int state, DocumentTrigger trigger, XmldbURI modifiedDocument) {
			this.state = state;
			if (state == NO_TRIGGER_RUNNING) {
				this.currentTrigger = null;
				this.setModifiedDocument(null);
			} else {
				this.currentTrigger = trigger;
				this.setModifiedDocument(modifiedDocument);
			}
		}

		private int getState() {
			return state;
		}

		void setTransaction(Txn txn) {
			this.txn = txn;
		}
		Txn getTransaction() {
			return txn;
		}

		public DocumentTrigger getTrigger() {
			return currentTrigger;
		}

		private void setModifiedDocument(XmldbURI modifiedDocument) {
			this.modifiedDocument = modifiedDocument;
		}

		private XmldbURI getModifiedDocument() {
			return modifiedDocument;
		}	
	}
	
	public static int getTriggerRunningState() {
		return triggerRunningState.get().getState();
	}

	public static DocumentTrigger getRunningTrigger() {
		return triggerRunningState.get().getTrigger();
	}
	
	public static void setTriggerRunningState( int state, DocumentTrigger trigger, XmldbURI modifiedDocument ) {
		triggerRunningState.get().setState(state, trigger, modifiedDocument);
	}

	public static Txn getTransaction() {
		return triggerRunningState.get().getTransaction();
	}

	public static void setTransaction(Txn txn) {
        triggerRunningState.get().setTransaction(txn);
	}
	
	public static XmldbURI getModifiedDocument() {
		return triggerRunningState.get().getModifiedDocument();		
	}
}
