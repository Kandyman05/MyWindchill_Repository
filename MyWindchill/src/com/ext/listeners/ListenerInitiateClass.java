package com.ext.listeners;

import wt.fc.PersistenceManagerEvent;
import wt.services.ManagerException;
import wt.services.StandardManager;
import wt.util.WTException;

public class ListenerInitiateClass extends StandardManager implements ListenerOneInterface {
	private static final long serialVersionUID = 1L;
	
	public static final String ClassName = ListenerInitiateClass.class.getName();
	
	public static ListenerInitiateClass newListenerInitiateClass() throws WTException
	{
		ListenerInitiateClass obj = new ListenerInitiateClass();
		obj.initialize();
		
		return obj;
	}

	@Override
	protected synchronized void performStartupProcess() throws ManagerException {
		//Describe By Link (Mechanical and Electrical Part)
		getManagerService().addEventListener(new describeByLinkListener(ClassName),PersistenceManagerEvent.generateEventKey(PersistenceManagerEvent.POST_STORE));
	}
	
}
 