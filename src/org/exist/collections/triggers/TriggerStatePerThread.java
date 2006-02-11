package org.exist.collections.triggers;

import org.exist.storage.txn.Txn;

/** Finite State Machine, managing the state of a Running trigger, 
 * allows to avoid infinite recursions by forbidding another trigger to run
 * where there is allready one; feature trigger_update
 * TODO: apply "state" design pattern */
public class TriggerStatePerThread {

	public static final int NO_TRIGGER_RUNNING = (0);
	public static final int TRIGGER_RUNNING_PREPARE = (1);
	public static final int TRIGGER_RUNNING_FINISH = (2);
	
	private static ThreadLocal triggerRunningState = new ThreadLocal() {
	    protected synchronized Object initialValue() {
	        return new TriggerState(NO_TRIGGER_RUNNING);
	    };
	};

	public static boolean verifyUniqueTriggerPerThreadBeforePrepare(DocumentTrigger trigger) {
		if( getTriggerRunningState() == NO_TRIGGER_RUNNING ) {
			setTriggerRunningState( TRIGGER_RUNNING_PREPARE, trigger );
			return true;
		} else {
			return false;
		}
	}
	
	public static boolean verifyUniqueTriggerPerThreadBeforeFinish(DocumentTrigger trigger) {

		// another trigger is allready running
		if( trigger != getRunningTrigger() )
			return false;
		
		if ( getTriggerRunningState() == TRIGGER_RUNNING_PREPARE) {
			setTriggerRunningState( TRIGGER_RUNNING_FINISH, trigger );
			return true;
		} else {
			return false;
		}
	}
	
	public static class TriggerState {
		private int state;
		private DocumentTrigger currentTrigger;
		private Txn transaction;
		public TriggerState(int state) {
			super();
			this.setState(state, null);
		}
		
		private void setState( int state, DocumentTrigger trigger) {
			this.state = state;
			if( state == NO_TRIGGER_RUNNING )
				this.currentTrigger = null;
			else
				this.currentTrigger = trigger;
		}

		private int getState() {
			return state;
		}

		void setTransaction(Txn transaction) {
			this.transaction = transaction;
		}
		Txn getTransaction() {
			return transaction;
		}

		public DocumentTrigger getTrigger() {
			return currentTrigger;
		}	
	}
	
	public static int getTriggerRunningState() {
		return ((TriggerState)triggerRunningState.get()).getState();
	}

	public static DocumentTrigger getRunningTrigger() {
		return ((TriggerState)triggerRunningState.get()).getTrigger();
	}
	
	public static void setTriggerRunningState(int state, DocumentTrigger trigger) {
		((TriggerState)triggerRunningState.get()).setState(state, trigger);
	}

	public static Txn getTransaction() {
		return ((TriggerState)triggerRunningState.get()).getTransaction();
	}
	public static void setTransaction(Txn transaction) {
		((TriggerState)triggerRunningState.get()).setTransaction(transaction);
	}

}
