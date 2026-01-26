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
import com.ptc.core.meta.common.TypeIdentifier;
import com.ptc.core.meta.server.TypeIdentifierUtility;

public class describeByLinkListener extends ServiceEventListenerAdapter {

    private static final Logger logger = LogR.getLogger(PostStorePartListener.class.getName());

    // =========================================================================
    // TODO: UPDATE THESE STRINGS WITH YOUR ACTUAL INTERNAL NAMES
    // =========================================================================
    
    // The Internal Names of your PARTS
    private static final String PART_TYPE_ELEC_INTERNAL = "com.ext.ElectricalPart";
    private static final String PART_TYPE_MECH_INTERNAL = "com.ext.MechanicalPart";

    // The Internal Names of the DOCUMENTS you want to create
    private static final String DOC_TYPE_ELEC_INTERNAL = "wt.doc.WTDocument|com.ext.ElectricalDocument";
    private static final String DOC_TYPE_MECH_INTERNAL = "wt.doc.WTDocument|com.ext.MechanicalDocument";

    public describeByLinkListener(String managerName) {
        super(managerName);
    }

    @Override
    public void notifyVetoableEvent(Object event) throws Exception {
        if (!(event instanceof PersistenceManagerEvent)) {
            return;
        }

        PersistenceManagerEvent pmEvent = (PersistenceManagerEvent) event;

        // Run only on POST_STORE (After creation)
        if (!PersistenceManagerEvent.POST_STORE.equals(pmEvent.getEventType())) {
            return;
        }

        Persistable targetObject = pmEvent.getTarget();

        if (targetObject instanceof WTPart) {
            WTPart objWTPart = (WTPart) targetObject;
            
            // Log detection
            logger.debug("New WTPart detected: " + objWTPart.getNumber());

            // Run the logic
            createAndLinkDocument(objWTPart);
        }
    }

    private void createAndLinkDocument(WTPart objWTPart) {
        boolean accessEnforced = SessionServerHelper.manager.isAccessEnforced();
        try {
            // 1. Determine the Type of the new Part
            String currentPartType = getInternalType(objWTPart);
            String targetDocType = null;

            // 2. Decide which Document to create based on Part Type
            if (currentPartType.contains(PART_TYPE_ELEC_INTERNAL)) {
                logger.debug("Part is Electrical. Setting target doc type to Electrical.");
                targetDocType = DOC_TYPE_ELEC_INTERNAL;
            } 
            else if (currentPartType.contains(PART_TYPE_MECH_INTERNAL)) {
                logger.debug("Part is Mechanical. Setting target doc type to Mechanical.");
                targetDocType = DOC_TYPE_MECH_INTERNAL;
            } 
            else {
                // If it is neither, do nothing and exit
                logger.debug("Part is generic or unknown type. No document will be created.");
                return; 
            }

            // 3. Bypass Access Control
            SessionServerHelper.manager.setAccessEnforced(false);

            // 4. Create the Document
            WTDocument objDocument = WTDocument.newWTDocument();
            objDocument.setNumber(objWTPart.getNumber());
            objDocument.setName(objWTPart.getName());

            // Set Container
            WTContainerRef objContainer = objWTPart.getContainerReference();
            objDocument.setContainerReference(objContainer);

            // Set the Type Definition dynamically based on logic above
            TypeDefinitionReference typeRef = TypedUtilityServiceHelper.service.getTypeDefinitionReference(targetDocType);
            
            if (typeRef != null) {
                objDocument.setTypeDefinitionReference(typeRef);
            } else {
                logger.error("Type Definition not found: " + targetDocType + ". Check your internal names.");
                // Optional: Return here to avoid creating a wrong object
                return; 
            }

            // Save Document
            PersistenceHelper.manager.save(objDocument);

            // 5. Create the Describe Link
            WTPartDescribeLink objDescribeLink = WTPartDescribeLink.newWTPartDescribeLink(objWTPart, objDocument);
            PersistenceHelper.manager.save(objDescribeLink);

            logger.info("Created " + targetDocType + " and linked to Part " + objWTPart.getNumber());

        } catch (WTException | WTPropertyVetoException | RemoteException e) {
            logger.error("Error in PostStorePartListener for Part: " + objWTPart.getNumber(), e);
        } finally {
            SessionServerHelper.manager.setAccessEnforced(accessEnforced);
        }
    }

    /**
     * Helper method to get the Internal Name of a Persistable object
     */
    private String getInternalType(Persistable obj) {
        try {
            // This returns the full internal string (e.g., "WTPart|com.ext.ElectricalPart")
            TypeIdentifier typeId = TypeIdentifierUtility.getTypeIdentifier(obj);
            return typeId.getTypename();
        } catch (Exception e) {
            logger.error("Could not determine type of object", e);
            return "";
        }
    }
}