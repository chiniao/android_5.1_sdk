/*******************************************************************************
 * Copyright (c) 1997-2007 by ProSyst Software GmbH
 * http://www.prosyst.com
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    ProSyst Software GmbH - initial API and implementation
 *******************************************************************************/
package javax.microedition.io;

public abstract interface Datagram extends java.io.DataInput, java.io.DataOutput {

	public abstract java.lang.String getAddress();

	public abstract byte[] getData();

	public abstract int getLength();

	public abstract int getOffset();

	public abstract void reset();

	public abstract void setAddress(javax.microedition.io.Datagram var0);

	public abstract void setAddress(java.lang.String var0) throws java.io.IOException;

	public abstract void setData(byte[] var0, int var1, int var2);

	public abstract void setLength(int var0);

}
