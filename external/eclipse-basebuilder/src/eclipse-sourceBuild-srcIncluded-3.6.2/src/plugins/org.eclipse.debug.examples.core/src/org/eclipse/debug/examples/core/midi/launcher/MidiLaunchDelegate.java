/*******************************************************************************
 * Copyright (c) 2008 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.debug.examples.core.midi.launcher;

import java.io.BufferedInputStream;
import java.io.IOException;

import javax.sound.midi.InvalidMidiDataException;
import javax.sound.midi.MidiFileFormat;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.MidiUnavailableException;
import javax.sound.midi.Sequencer;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Status;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.model.LaunchConfigurationDelegate;
import org.eclipse.debug.examples.core.pda.DebugCorePlugin;

/**
 * Creates and starts a MIDI sequencer.
 * 
 * @since 1.0
 */
public class MidiLaunchDelegate extends LaunchConfigurationDelegate {

	/**
	 * Identifier for the MIDI launch configuration type
	 * (value <code>midi.launchType</code>)
	 */
	public static final String ID_MIDI_LAUNCH_CONFIGURATION_TYPE = "midi.launchType";
	
	/**
	 * Launch configuration attribute for the MIDI file to play
	 * (value <code>midi.file</code>)
	 */
	public static final String ATTR_MIDI_FILE = "midi.file";
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ILaunchConfigurationDelegate#launch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String, org.eclipse.debug.core.ILaunch, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public void launch(ILaunchConfiguration configuration, String mode, ILaunch launch, IProgressMonitor monitor) throws CoreException {
		String fileName = configuration.getAttribute(ATTR_MIDI_FILE, (String)null);
		if (fileName == null) {
			abort("MIDI file not specified.", null);
		}
		IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
		IFile file = root.getFile(new Path(fileName));
		if (!file.exists()) {
			abort("MIDI file does not exist.", null);
		}
		Sequencer sequencer = null;
		MidiFileFormat fileFormat = null;
		try {
			sequencer = MidiSystem.getSequencer();
			sequencer.open();
			IPath location = file.getLocation();
			if (location != null) {
				fileFormat = MidiSystem.getMidiFileFormat(location.toFile());
			}
		} catch (MidiUnavailableException e) {
			abort("Cannot initialize sequencer.", e);
		} catch (InvalidMidiDataException e) {
			abort("Invalid MIDI file.", e);
		} catch (IOException e) {
			abort("Error reading MIDI file.", e);
		}
		BufferedInputStream stream = new BufferedInputStream(file.getContents());
		try {
			sequencer.setSequence(stream);
		} catch (IOException e) {
			abort("Error reading MIDI file", e);
		} catch (InvalidMidiDataException e) {
			abort("Inavlid MIDI file.", e);
		}
		MidiLaunch midiLaunch = (MidiLaunch)launch;
		midiLaunch.setSequencer(sequencer);
		midiLaunch.setFormat(fileFormat);
		sequencer.start();
	}

	/**
	 * Throws an exception with a new status containing the given
	 * message and optional exception.
	 * 
	 * @param message error message
	 * @param e underlying exception
	 * @throws CoreException
	 */
	private void abort(String message, Throwable e) throws CoreException {
		throw new CoreException(new Status(IStatus.ERROR, DebugCorePlugin.PLUGIN_ID, 0, message, e));
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.ILaunchConfigurationDelegate2#getLaunch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String)
	 */
	public ILaunch getLaunch(ILaunchConfiguration configuration, String mode) throws CoreException {
		return new MidiLaunch(configuration, mode);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.debug.core.model.LaunchConfigurationDelegate#buildForLaunch(org.eclipse.debug.core.ILaunchConfiguration, java.lang.String, org.eclipse.core.runtime.IProgressMonitor)
	 */
	public boolean buildForLaunch(ILaunchConfiguration configuration, String mode, IProgressMonitor monitor) throws CoreException {
		return false;
	}
	
	

}
