/*******************************************************************************
 * Copyright (c) 1999, 2009 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.equinox.http;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Vector;
import org.eclipse.osgi.util.NLS;

/* @ThreadSafe */
public class HttpThreadPool extends ThreadGroup {
	/** Master HTTP object */
	final Http http;

	/** container to threads waiting for work */
	private final Vector idleThreads; /* @GuardedBy("this") */
	/** container to threads which are working */
	private final Vector activeThreads; /* @GuardedBy("this") */
	/** Upper bound on size of thread pool */
	private int upper; /* @GuardedBy("this") */
	/** Lower bound on size of thread pool */
	private int lower; /* @GuardedBy("this") */
	/** Priority of thread pool */
	private volatile int priority;
	/** number of threads to be terminated when they are returned to the pool */
	private int hitCount; /* @GuardedBy("this") */
	/** Thread allocation number */
	private int number; /* @GuardedBy("this") */
	/** prevent new threads from readjusting */
	private int adjusting = 0; /* @GuardedBy("this") */

	/**
	 * Constructs and populates a new thread pool with the specified thread group
	 * identifier and the specified number of threads.
	 *
	 */
	public HttpThreadPool(Http http, int lower, int upper, int priority) {
		super("Http Service Thread Pool"); //$NON-NLS-1$
		this.http = http;
		idleThreads = new Vector(upper);
		activeThreads = new Vector(upper);
		number = 0;
		setSize(lower, upper);

		setPriority(priority);
	}

	/**
	 * Returns the lower bound on size of thread pool.
	 */
	public synchronized int getLowerSizeLimit() {
		return lower;
	}

	/**
	 * Returns the upper bound on size of thread pool.
	 */
	public synchronized int getUpperSizeLimit() {
		return upper;
	}

	/**
	 * Sets the size of thread pool.
	 *
	 * @param lower the lower bounds on the size
	 * @param upper the upper bounds on the size
	 */
	public synchronized void setSize(int lower, int upper) {
		this.lower = lower;
		this.upper = upper;
		adjustThreadCount();
	}

	/**
	 * Must be called while synchronized on this object.
	 *
	 */
	/* @GuardedBy("this") */
	private void adjustThreadCount() {
		if (adjusting > 0) {
			adjusting--;
			return;
		}
		int active = activeThreads.size();
		int idle = idleThreads.size();
		int count = idle + active;

		if (Http.DEBUG) {
			http.logDebug("Current thread count: " + idle + " idle, " + active + " active"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
		}

		if (idle < 2) {
			count += 5;
		} else {
			if (idle > 10) {
				count -= 5;
			}
		}

		if (count > upper) {
			count = upper;
		}

		if (count < lower) {
			count = lower;
		}

		int delta = count - (idle + active);
		if (Http.DEBUG) {
			http.logDebug("New thread count: " + count + ", delta: " + delta); //$NON-NLS-1$ //$NON-NLS-2$
		}

		if (delta < 0) /* remove threads */
		{
			delta = -delta; /* invert sign */
			if (delta < idle) {
				for (int i = idle - 1; delta > 0; i--, delta--) {
					HttpThread thread = (HttpThread) idleThreads.elementAt(i);
					idleThreads.removeElementAt(i);
					thread.close();
				}
			} else {
				hitCount += delta - idle;
				for (int i = 0; i < idle; i++) {
					HttpThread thread = (HttpThread) idleThreads.elementAt(i);
					thread.close();
				}
				idleThreads.removeAllElements();
			}
		} else {
			if (delta > 0) /* add threads */
			{
				adjusting = delta; /* new threads will call this method */
				if (delta > hitCount) {
					delta -= hitCount;
					hitCount = 0;
					idleThreads.ensureCapacity(count);
					for (int i = 0; i < delta; i++) {
						number++;
						final String threadName = "HttpThread_" + number; //$NON-NLS-1$
						try {
							AccessController.doPrivileged(new PrivilegedAction() {
								public Object run() {
									HttpThread thread = new HttpThread(http, HttpThreadPool.this, threadName);
									thread.start(); /* thread will add itself to the pool */
									return null;
								}
							});
						} catch (RuntimeException e) {
							/* No resources to create another thread */
							http.logError(NLS.bind(HttpMsg.HTTP_THREAD_POOL_CREATE_NUMBER_ERROR, new Integer(number)), e);

							number--;

							/* Readjust the upper bound of the thread pool */
							upper -= delta - i;

							break;
						}
					}
				} else {
					hitCount -= delta;
				}
			}
		}
	}

	/**
	 * Set the priority of the threads in the thread pool.
	 *
	 * @param priority Thread priority.
	 */
	public void setPriority(int priority) {
		if ((Thread.MIN_PRIORITY <= priority) && (priority <= Thread.MAX_PRIORITY)) {
			this.priority = priority;
		} else {
			throw new IllegalArgumentException(NLS.bind(HttpMsg.HTTP_INVALID_VALUE_RANGE_EXCEPTION, new Object[] {new Integer(Thread.MIN_PRIORITY), new Integer(Thread.MAX_PRIORITY)}));
		}
	}

	/**
	 * Returns a thread to the thread pool and notifies the pool that
	 * a thread is available.
	 *
	 * @param thread the thread being added/returned to the pool
	 */
	public synchronized void putThread(HttpThread thread) {
		if (Http.DEBUG) {
			http.logDebug(thread.getName() + ": becoming idle"); //$NON-NLS-1$
		}

		activeThreads.removeElement(thread);

		if (hitCount > 0) {
			hitCount--;
			thread.close();
		} else {
			if (!idleThreads.contains(thread)) {
				idleThreads.addElement(thread);
				notify();
			}
		}

		adjustThreadCount();
	}

	/**
	 * Gets the next available thread from the thread pool.  If no thread is
	 * available, this method blocks until one becomes available or the pool is
	 * disposed of.
	 *
	 * @return the next available thread; if the pool has been (or is disposed
	 *         of while waiting), null is returned
	 */
	public synchronized HttpThread getThread() {
		adjustThreadCount();

		while (upper > 0) {
			int count = idleThreads.size();
			if (count > 0) {
				int i = count - 1;

				HttpThread thread = (HttpThread) idleThreads.elementAt(i);
				idleThreads.removeElementAt(i);
				if (thread.getPriority() != priority) {
					thread.setPriority(priority);
				}
				activeThreads.addElement(thread);
				//new Exception((size-i)+" Threads are at work!").printStackTrace();
				if (Http.DEBUG) {
					http.logDebug(thread.getName() + ": becoming active"); //$NON-NLS-1$
				}

				return thread;
			}
			try {
				wait();
			} catch (InterruptedException e) {
				// ignore and check exit condition
			}
		}

		return null;
	}

	/**
	 * Remove all thread from the pool.
	 */
	public synchronized void close() {
		recallThreads();

		setSize(0, 0);
		/* Notify everyone waiting for a thread */
		notifyAll();

		// destroy the threadgroup, will never go away otherwise
		try {
			// Need to set it to a daemon first otherwise it will not be destroyed
			setDaemon(true);
			destroy();
		} catch (Exception e) {
			// TODO: consider logging
		}
	}

	/**
	 * This method recalls threads that are waiting on a socket for work.
	 * This is needed when Keep-Alive is in use and we need to
	 * close the socket the thread is waiting on.
	 *
	 */
	public synchronized void recallThreads() {
		int count = activeThreads.size();
		for (int i = count - 1; i >= 0; i--) {
			HttpThread thread = (HttpThread) activeThreads.elementAt(i);
			thread.recall();
		}
	}
}
