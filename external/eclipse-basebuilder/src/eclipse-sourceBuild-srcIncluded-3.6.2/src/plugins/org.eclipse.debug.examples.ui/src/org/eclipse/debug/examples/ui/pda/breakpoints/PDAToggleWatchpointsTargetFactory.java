/*******************************************************************************
 * Copyright (c) 2008 Wind River Systems and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     Wind River Systems - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.examples.ui.pda.breakpoints;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.eclipse.debug.ui.actions.IToggleBreakpointsTarget;
import org.eclipse.debug.ui.actions.IToggleBreakpointsTargetFactory;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.ui.IWorkbenchPart;

/**
 * Toggle breakpoints target factory for creating PDA watchpoints.  It allows the 
 * user the select the type of watchpoint that will be created when the watchpoint 
 * is toggles inside the editor or variables view.
 */
public class PDAToggleWatchpointsTargetFactory implements IToggleBreakpointsTargetFactory {

    private static final String TOGGLE_WATCHPOINT_TARGET_ACCESS = "org.eclipse.debug.examples.ui.pda.watchpoint_access";
    private static final String TOGGLE_WATCHPOINT_TARGET_MODIFICATION = "org.eclipse.debug.examples.ui.pda.watchpoint_modification";
    private static final String TOGGLE_WATCHPOINT_TARGET_BOTH = "org.eclipse.debug.examples.ui.pda.watchpoint_both";
    
    private static Set TOGGLE_WATCHPOINTS_TARGETS = new LinkedHashSet();
    
    private Map fToggleWatchpointTargets = new HashMap(3);
    
    static {
        TOGGLE_WATCHPOINTS_TARGETS.add(TOGGLE_WATCHPOINT_TARGET_BOTH);
        TOGGLE_WATCHPOINTS_TARGETS.add(TOGGLE_WATCHPOINT_TARGET_ACCESS);
        TOGGLE_WATCHPOINTS_TARGETS.add(TOGGLE_WATCHPOINT_TARGET_MODIFICATION);
    }   
    
    public IToggleBreakpointsTarget createToggleTarget(String targetID) {
        IToggleBreakpointsTarget target = (IToggleBreakpointsTarget)fToggleWatchpointTargets.get(targetID);
        if (target == null) {
            if (TOGGLE_WATCHPOINT_TARGET_BOTH.equals(targetID)) {
                target = new PDAToggleWatchpointsTarget(true, true);
            } else if (TOGGLE_WATCHPOINT_TARGET_ACCESS.equals(targetID)) {
                target = new PDAToggleWatchpointsTarget(true, false);
            } else if (TOGGLE_WATCHPOINT_TARGET_MODIFICATION.equals(targetID)) {
                target = new PDAToggleWatchpointsTarget(false, true);
            } else { 
                return null;
            }
            fToggleWatchpointTargets.put(targetID, target);
        }
        return target;
    }
    
    public String getDefaultToggleTarget(IWorkbenchPart part, ISelection selection) {
        return TOGGLE_WATCHPOINT_TARGET_BOTH;
    }

    public Set getToggleTargets(IWorkbenchPart part, ISelection selection) {
        return TOGGLE_WATCHPOINTS_TARGETS;
    }

    public String getToggleTargetName(String targetID) {
        if (TOGGLE_WATCHPOINT_TARGET_BOTH.equals(targetID)) {
            return "Watchpoints (Read/Write)";
        } else if (TOGGLE_WATCHPOINT_TARGET_ACCESS.equals(targetID)) {
            return "Watchpoints (Read)";
        } else if (TOGGLE_WATCHPOINT_TARGET_MODIFICATION.equals(targetID)) {
            return "Watchpoints (Write)";
        } else { 
            return null;
        }
    }

    public String getToggleTargetDescription(String targetID) {
        return getToggleTargetName(targetID);
    }
}
