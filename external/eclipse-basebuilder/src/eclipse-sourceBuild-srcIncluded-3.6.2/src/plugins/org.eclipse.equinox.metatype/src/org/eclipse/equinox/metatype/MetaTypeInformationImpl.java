/*******************************************************************************
 * Copyright (c) 2005, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.metatype;

import java.util.Enumeration;
import java.util.Vector;
import javax.xml.parsers.SAXParserFactory;
import org.osgi.framework.Bundle;
import org.osgi.service.metatype.MetaTypeInformation;

/**
 * Implementation of MetaTypeProvider
 * <p>
 * Extension of MetaTypeProvider
 * <p>
 * Provides methods to:
 * <p> - getPids() get the Pids for a given Locale
 * <p> - getFactoryPids() get the Factory Pids for a given Locale
 * <p>
 */
public class MetaTypeInformationImpl extends MetaTypeProviderImpl implements MetaTypeInformation {

	/**
	 * Constructor of class MetaTypeInformationImpl.
	 */
	MetaTypeInformationImpl(Bundle bundle, SAXParserFactory parserFactory) throws java.io.IOException {
		super(bundle, parserFactory);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.service.metatype.MetaTypeInformation#getPids()
	 */
	public String[] getPids() {

		if (_allPidOCDs.size() == 0) {
			return new String[0];
		}

		Vector pids = new Vector(7);
		Enumeration e = _allPidOCDs.keys();
		while (e.hasMoreElements()) {
			pids.addElement(e.nextElement());
		}

		String[] retvalue = new String[pids.size()];
		pids.toArray(retvalue);
		return retvalue;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.service.metatype.MetaTypeInformation#getFactoryPids()
	 */
	public String[] getFactoryPids() {
		if (_allFPidOCDs.size() == 0) {
			return new String[0];
		}
		Vector fpids = new Vector(7);
		Enumeration e = _allFPidOCDs.keys();
		while (e.hasMoreElements()) {
			fpids.addElement(e.nextElement());
		}
		String[] retvalue = new String[fpids.size()];
		fpids.toArray(retvalue);
		return retvalue;
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.osgi.service.metatype.MetaTypeInformation#getBundle()
	 */
	public Bundle getBundle() {
		return this._bundle;
	}
}
