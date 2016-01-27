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

import java.io.PrintStream;

/**
 * Temporary Logging class
 */
public class Logging {

	public static final int TRACE = 0;
	public static final int DEBUG = 1;
	public static final int WARN = 2;
	public static final int ERROR = 3;

	private static int _logging_level = WARN;
	private static PrintStream out = System.out;

	/*
	 * 
	 */
	public static void log(int type, String message) {
		log(type, null, null, message);
	}

	/*
	 * Main method to print log message
	 */
	public static void log(int type, Object obj, String method, String message) {

		if (type >= _logging_level) {

			switch (type) {
				case TRACE :
					out.println("[Trace log]"); //$NON-NLS-1$
					break;
				case DEBUG :
					out.println("[Debug log]"); //$NON-NLS-1$
					break;
				case WARN :
					out.println("[Warning log]"); //$NON-NLS-1$
					break;
				default :
					out.println("[Error log]"); //$NON-NLS-1$
			}

			if (obj != null) {
				out.println("\tObject:  " + obj.getClass().getName()); //$NON-NLS-1$
			}
			if (method != null) {
				out.println("\tMethod:  " + method); //$NON-NLS-1$
			}
			out.println("\tMessage: " + message); //$NON-NLS-1$
		}
	}

	/*
	 * 
	 */
	public static void debug(String message) {

		log(DEBUG, null, null, message);
	}
}
