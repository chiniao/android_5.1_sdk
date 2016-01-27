/*******************************************************************************
 * Copyright (c) 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 * IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.team.examples.model;

import org.eclipse.core.resources.IFile;

public abstract class ModelFile extends ModelResource {

	protected ModelFile(IFile file) {
		super(file);
	}
	
	public String getName() {
		String name = super.getName();
		int index = name.lastIndexOf(".");
		return name.substring(0, index);
	}

}
