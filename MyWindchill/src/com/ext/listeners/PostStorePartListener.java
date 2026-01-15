package com.ext.listeners;

import java.rmi.RemoteException;

import org.apache.logging.log4j.Logger;

import wt.doc.WTDocument;
import wt.fc.Persistable;
import wt.fc.PersistenceHelper;
import wt.fc.PersistenceManagerEvent;
import wt.inf.container.WTContainerRef;
import wt.log4j.LogR;
import wt.part.WTPart;
import wt.part.WTPartDescribeLink;
import wt.services.ServiceEventListenerAdapter;
import wt.session.SessionServerHelper;
import wt.type.TypeDefinitionReference;
import wt.type.TypedUtilityServiceHelper;
import wt.util.WTException;
import wt.util.WTPropertyVetoException;

public class PostStorePartListener extends ServiceEventListenerAdapter {

    // Initialize Logger
    private static final Logger logger = LogR.getLogger(PostStorePartListener.class.getName());
    
    // Internal name of the Type you want to create
    private static final String TARGET_INTERNAL_TYPE = "wt.doc.WTDocument|com.ext.ElectricalDocument";

    public PostStorePartListener(String managerName) {
        super(managerName);
    }

    @Override
    public void notifyVetoableEvent(Object event) throws Exception {
        // 1. Basic Validation
        if (!(event instanceof PersistenceManagerEvent)) {
            return;
        }

        PersistenceManagerEvent pmEvent = (PersistenceManagerEvent) event;

        // 2. CRITICAL: Only run on POST_STORE (After creation)
        // Without this, it runs on updates, deletes, etc.
        if (!PersistenceManagerEvent.POST_STORE.equals(pmEvent.getEventType())) {
            return;
        }

        Persistable targetObject = pmEvent.getTarget();

        // 3. Check if target is WTPart
        if (targetObject instanceof WTPart) {
            WTPart objWTPart = (WTPart) targetObject;
            
            logger.debug("New WTPart detected: " + objWTPart.getNumber() + ". Starting Document creation...");

            createAndLinkDocument(objWTPart);
        }
    }

    private void createAndLinkDocument(WTPart objWTPart) {
        boolean accessEnforced = SessionServerHelper.manager.isAccessEnforced();
        try {
            // 4. Bypassing Access Control safely
            SessionServerHelper.manager.setAccessEnforced(false);

            // 5. Create the Document
            WTDocument objDocument = WTDocument.newWTDocument();
            objDocument.setNumber(objWTPart.getNumber()); // Assumes Number is unique and doesn't exist yet
            objDocument.setName(objWTPart.getName());
            
            // Set Container (Location)
            WTContainerRef objContainer = objWTPart.getContainerReference();
            objDocument.setContainerReference(objContainer);

            // Set Type Definition
            TypeDefinitionReference typeRef = TypedUtilityServiceHelper.service.getTypeDefinitionReference(TARGET_INTERNAL_TYPE);
            if (typeRef != null) {
                objDocument.setTypeDefinitionReference(typeRef);
            } else {
                logger.error("Type Definition not found: " + TARGET_INTERNAL_TYPE + ". Defaulting to standard WTDocument.");
            }

            // Save Document
            PersistenceHelper.manager.save(objDocument);
            
            // 6. Create the Link
            WTPartDescribeLink objDescribeLink = WTPartDescribeLink.newWTPartDescribeLink(objWTPart, objDocument);
            PersistenceHelper.manager.save(objDescribeLink);

            logger.info("Successfully created and linked Document " + objDocument.getNumber() + " to Part " + objWTPart.getNumber());

        } catch (WTException | WTPropertyVetoException | RemoteException e) {
            logger.error("Error in PostStorePartListener for Part: " + objWTPart.getNumber(), e);
        } finally {
            // 7. ALWAYS restore access control
            SessionServerHelper.manager.setAccessEnforced(accessEnforced);
        }
    }
}