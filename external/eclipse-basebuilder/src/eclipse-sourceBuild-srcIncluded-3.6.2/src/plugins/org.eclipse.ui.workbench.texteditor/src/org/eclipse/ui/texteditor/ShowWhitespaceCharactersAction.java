/*******************************************************************************
 * Copyright (c) 2006, 2008 Wind River Systems, Inc., IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Anton Leherbauer (Wind River Systems) - initial API and implementation
 *******************************************************************************/
package org.eclipse.ui.texteditor;

import java.util.ResourceBundle;

import org.eclipse.core.runtime.Assert;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.preference.IPreferenceStore;

import org.eclipse.jface.text.IPainter;
import org.eclipse.jface.text.ITextViewer;
import org.eclipse.jface.text.ITextViewerExtension2;
import org.eclipse.jface.text.WhitespaceCharacterPainter;


/**
 * This action toggles the display of whitespace characters by
 * attaching/detaching an {@link WhitespaceCharacterPainter} to the
 * associated text editor.
 * <p>
 * <strong>Note:</strong> Currently this action only works if the given
 * editor inherits from {@link AbstractTextEditor}.
 * </p>
 *
 * @since 3.3
 */
public class ShowWhitespaceCharactersAction extends TextEditorAction {

	/** The preference store. */
	private IPreferenceStore fStore;
	/** The painter. */
	private IPainter fWhitespaceCharPainter;

	/**
	 * Construct the action and initialize its state.
	 *
	 * @param resourceBundle  the resource bundle to construct label and tooltip from
	 * @param prefix  the prefix to use for constructing resource bundle keys
	 * @param editor  the editor this action is associated with
	 * @param store  the preference store (may be <code>null</code>)
	 */
	public ShowWhitespaceCharactersAction(ResourceBundle resourceBundle, String prefix, ITextEditor editor, IPreferenceStore store) {
		super(resourceBundle, prefix, editor, IAction.AS_CHECK_BOX);
		fStore= store;
		synchronizeWithPreference();
	}

	/**
	 * Sets the preference store of this action.
	 *
	 * @param store the preference store
	 */
	public void setPreferenceStore(IPreferenceStore store) {
		fStore= store;
		synchronizeWithPreference();
	}

	/*
	 * @see org.eclipse.jface.action.Action#run()
	 */
	public void run() {
		togglePainterState(isChecked());
		if (fStore != null)
			fStore.setValue(AbstractTextEditor.PREFERENCE_SHOW_WHITESPACE_CHARACTERS, isChecked());
	}

	/*
	 * @see org.eclipse.ui.texteditor.TextEditorAction#update()
	 */
	public void update() {
		setEnabled(getTextViewer() instanceof ITextViewerExtension2);
		synchronizeWithPreference();
	}

	/**
	 * Installs the painter on the editor.
	 */
	private void installPainter() {
		Assert.isTrue(fWhitespaceCharPainter == null);

		ITextViewer viewer= getTextViewer();
		if (viewer instanceof ITextViewerExtension2) {
			fWhitespaceCharPainter= new WhitespaceCharacterPainter(viewer);
			((ITextViewerExtension2)viewer).addPainter(fWhitespaceCharPainter);
		}
	}

	/**
	 * Remove the painter from the current editor.
	 */
	private void uninstallPainter() {
		if (fWhitespaceCharPainter == null)
			return;

		ITextViewer viewer= getTextViewer();
		if (viewer instanceof ITextViewerExtension2)
			((ITextViewerExtension2)viewer).removePainter(fWhitespaceCharPainter);

		fWhitespaceCharPainter.deactivate(true);
		fWhitespaceCharPainter= null;
	}

	/**
	 * Get the <code>ITextViewer</code> from an <code>ITextEditor</code>.
	 *
	 * @return  the text viewer or <code>null</code>
	 */
	private ITextViewer getTextViewer() {
		ITextEditor editor= getTextEditor();
		if (editor instanceof AbstractTextEditor)
			return ((AbstractTextEditor)editor).getSourceViewer();

		return null;
	}

	/**
	 * Synchronize state with the preference.
	 */
	private void synchronizeWithPreference() {
		boolean checked= false;
		if (fStore != null)
			checked= fStore.getBoolean(AbstractTextEditor.PREFERENCE_SHOW_WHITESPACE_CHARACTERS);

		if (checked != isChecked()) {
			setChecked(checked);
			togglePainterState(checked);
		}
	}

	/**
	 * Toggles the painter state.
	 *
	 * @param newState <code>true</code> if the painter should be installed
	 */
	private void togglePainterState(boolean newState) {
		if (newState)
			installPainter();
		else
			uninstallPainter();
	}
}
