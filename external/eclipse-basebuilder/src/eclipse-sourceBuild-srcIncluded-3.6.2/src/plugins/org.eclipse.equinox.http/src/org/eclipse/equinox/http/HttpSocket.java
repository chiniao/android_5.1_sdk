/*******************************************************************************
 * Copyright (c) 1999, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.http;

import java.io.*;
import org.eclipse.equinox.socket.SocketInterface;

/* @ThreadSafe */
public class HttpSocket extends java.net.Socket implements SocketInterface {
	private InputStream in = null;
	private boolean closed = false;
	private boolean active = false;
	private final String scheme;

	public HttpSocket(String sheme) {
		super();
		this.scheme = sheme;
	}

	public synchronized void close() throws IOException {
		super.close();
		//must set closed to try after calling super.close() otherwise
		//jdk1.4 will not close the socket
		closed = true;
	}

	/**
	 * Wrap the real socket input stream in a buffered input stream
	 *
	 * @return a buffered InputStream which wraps the real input stream.
	 * @throws IOException
	 */
	public synchronized InputStream getInputStream() throws IOException {
		if (in == null) {
			in = new BufferedInputStream(super.getInputStream());
		}

		return in;
	}

	/**
	 * Return the scheme this socket is using.
	 *
	 * @return Either "http" or "https".
	 */
	public String getScheme() {
		return scheme;
	}

	/**
	 * Test to see if the socket has been closed.
	 *
	 * @return true if close has been called on this socket.
	 */
	public synchronized boolean isClosed() {
		return closed;
	}

	/**
	 * Test to see if the socket is active.
	 *
	 * @return true if markActive has been called.
	 */
	public synchronized boolean isActive() {
		return active;
	}

	/**
	 * Mark the socket active.
	 *
	 */
	public synchronized void markActive() {
		active = true;
	}

	/**
	 * Mark the socket inactive.
	 *
	 */
	public synchronized void markInactive() {
		active = false;
	}
}
