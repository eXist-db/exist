package org.exist.collections.triggers;

import org.exist.storage.txn.Txn;

/** Finite State Machine, managing the state of a Running trigger, 
 * allows to avoid infinite recursions by forbidding another trigger to run
 * where there is allready one; feature trigger_update
 * TODO: encapsulate the ThreadLocal ; apply "state" design pattern */
public class TriggerStatePerThread {

	public static final int NO_TRIGGER_RUNNING = (0);
	public static final int TRIGGER_RUNNING_PREPARE = (1);
	public static final int TRIGGER_RUNNING_FINISH = (2);
	
	private static ThreadLocal triggerRunningState = new ThreadLocal() {
	    protected synchronized Object initialValue() {
	        return new TriggerState(NO_TRIGGER_RUNNING);
	    };
	};

	public static boolean verifyUniqueTriggerPerThreadBeforePrepare() {
		if( getTriggerRunningState() == NO_TRIGGER_RUNNING ) {
			setTriggerRunningState( TRIGGER_RUNNING_PREPARE );
			return true;
		} else {
			return false;
		}
	}
	
	public static boolean verifyUniqueTriggerPerThreadBeforeFinish() {
		if ( getTriggerRunningState() == TRIGGER_RUNNING_PREPARE) {
			setTriggerRunningState( TRIGGER_RUNNING_FINISH );
			return true;
		} else {
			return false;
		}
	}
	
	public static class TriggerState {
		private int state;
		private Txn transaction;
		public TriggerState(int state) {
			super();
			this.setState(state);
		}
		
		private void setState(int state) {
			this.state = state;
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
	}
	
	public static int getTriggerRunningState() {
		return ((TriggerState)triggerRunningState.get()).getState();
	}
	
	public static void setTriggerRunningState(int state) {
		((TriggerState)triggerRunningState.get()).setState(state);
	}

	public static Txn getTransaction() {
		return ((TriggerState)triggerRunningState.get()).getTransaction();
	}
	
	public static void setTransaction(Txn transaction) {
		((TriggerState)triggerRunningState.get()).setTransaction(transaction);
	}

}
