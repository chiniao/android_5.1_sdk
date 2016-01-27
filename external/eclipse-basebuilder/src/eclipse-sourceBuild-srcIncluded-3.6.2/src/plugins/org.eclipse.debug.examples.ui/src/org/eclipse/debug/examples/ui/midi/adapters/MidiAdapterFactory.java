/*******************************************************************************
 * Copyright (c) 2008, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Patrick Chuong (Texas Instruments) - Checkbox support for Flexible Hierachy view (Bug 286310)
 *******************************************************************************/
package org.eclipse.debug.examples.ui.midi.adapters;

import javax.sound.midi.MidiEvent;
import javax.sound.midi.Track;

import org.eclipse.core.runtime.IAdapterFactory;
import org.eclipse.debug.core.commands.IStepOverHandler;
import org.eclipse.debug.examples.core.midi.launcher.MidiLaunch;
import org.eclipse.debug.examples.core.midi.launcher.SequencerControl;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IColumnPresentationFactory;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IElementContentProvider;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IElementEditor;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IElementLabelProvider;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IElementMementoProvider;
import org.eclipse.debug.internal.ui.viewers.model.provisional.IModelProxyFactory;

/**
 * Adapter factory for MIDI elements.
 * 
 * @since 1.0
 */
public class MidiAdapterFactory implements IAdapterFactory {

	private static IElementContentProvider fgSequencerContentProvider = new SequencerContentProvider();
	private static IElementContentProvider fgTackContentProvider = new TrackContentProvider();
	
	private static IElementLabelProvider fgTrackLabelProvider = new TrackLabelProvider();
	private static IElementLabelProvider fgControlLabelProvider = new ControlLabelProvider();
	private static IElementLabelProvider fgMidiEventLabelProvdier = new MidiEventLabelProvider();
	
	private static IColumnPresentationFactory fgSequencerColumnFactory = new SequencerColumnFactory();
	private static IColumnPresentationFactory fgTrackColumnFactory = new TrackColumnFactory();
	
	private static IModelProxyFactory fgSequencerModelProxyFactory = new SequencerModelProxyFactory();
	
	private static IElementMementoProvider fgMementoProvider = new ControlsMementoProvider();
	
	private static IStepOverHandler fgStepOverHandler = new MidiStepOverHandler();
	
	private static IModelProxyFactory fgCheckboxModelProxyFactory = new CheckboxModelProxyFactory();

	public Object getAdapter(Object adaptableObject, Class adapterType) {
		if (IElementContentProvider.class.equals(adapterType)) {
			if (adaptableObject instanceof MidiLaunch) {
				return fgSequencerContentProvider;
			}
			if (adaptableObject instanceof Track) {
				return fgTackContentProvider;
			}
		}
		if (IElementLabelProvider.class.equals(adapterType)) {
			if (adaptableObject instanceof Track) {
				return fgTrackLabelProvider;
			}
			if (adaptableObject instanceof SequencerControl) {
				return fgControlLabelProvider;
			}
			if (adaptableObject instanceof MidiEvent) {
				return fgMidiEventLabelProvdier;
			}
		}
		
		if (IColumnPresentationFactory.class.equals(adapterType)) {
			if (adaptableObject instanceof MidiLaunch) {
				return fgSequencerColumnFactory;
			}
			if (adaptableObject instanceof Track) {
				return fgTrackColumnFactory;
			}
		}
		if (IElementEditor.class.equals(adapterType)) {
			if (adaptableObject instanceof SequencerControl) {
				return new ControlEditor();
			}
		}
		
		if (IModelProxyFactory.class.equals(adapterType)) {
			if (adaptableObject instanceof MidiLaunch) {
				return fgSequencerModelProxyFactory;
			} else if (adaptableObject instanceof Track) {
				return fgCheckboxModelProxyFactory;
			} else if (adaptableObject instanceof MidiEvent) {
				return fgCheckboxModelProxyFactory;
			}
		}
		if (IElementMementoProvider.class.equals(adapterType)) {
			return fgMementoProvider;
		}
		if (IStepOverHandler.class.equals(adapterType)) {
			return fgStepOverHandler;
		}
		
		return null;
	}

	public Class[] getAdapterList() {
		return new Class[]{IElementContentProvider.class, IElementLabelProvider.class, IStepOverHandler.class,};
	}
}
