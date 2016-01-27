/*******************************************************************************
 * Copyright (c) 2003, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/

package org.eclipse.osgi.internal.serviceregistry;

import java.security.AccessController;
import java.security.PrivilegedAction;
import org.eclipse.osgi.framework.debug.Debug;
import org.eclipse.osgi.framework.internal.core.BundleContextImpl;
import org.eclipse.osgi.framework.internal.core.Msg;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.*;

/**
 * This class represents the use of a service by a bundle. One is created for each
 * service acquired by a bundle. This class manages the calls to ServiceFactory
 * and the bundle's use count.
 * 
 * @ThreadSafe
 */

public class ServiceUse {
	/** ServiceFactory object if the service instance represents a factory,
	 null otherwise */
	final ServiceFactory factory;
	/** BundleContext associated with this service use */
	final BundleContextImpl context;
	/** ServiceDescription of the registered service */
	final ServiceRegistrationImpl registration;

	/** Service object either registered or that returned by
	 ServiceFactory.getService() */
	/* @GuardedBy("this") */
	private Object cachedService;
	/** bundle's use count for this service */
	/* @GuardedBy("this") */
	private int useCount;

	/** Internal framework object. */

	/**
	 * Constructs a service use encapsulating the service object.
	 * Objects of this class should be constructed while holding the
	 * registrations lock.
	 *
	 * @param   context bundle getting the service
	 * @param   registration ServiceRegistration of the service
	 */
	ServiceUse(BundleContextImpl context, ServiceRegistrationImpl registration) {
		this.useCount = 0;
		Object service = registration.getServiceObject();
		if (service instanceof ServiceFactory) {
			this.factory = (ServiceFactory) service;
			this.cachedService = null;
		} else {
			this.factory = null;
			this.cachedService = service;
		}
		this.context = context;
		this.registration = registration;
	}

	/**
	 * Get a service's service object.
	 * Retrieves the service object for a service.
	 * A bundle's use of a service is tracked by a
	 * use count. Each time a service's service object is returned by
	 * {@link #getService}, the context bundle's use count for the service
	 * is incremented by one. Each time the service is release by
	 * {@link #ungetService}, the context bundle's use count
	 * for the service is decremented by one.
	 * When a bundle's use count for a service
	 * drops to zero, the bundle should no longer use the service.
	 *
	 * <p>The following steps are followed to get the service object:
	 * <ol>
	 * <li>The context bundle's use count for this service is incremented by one.
	 * <li>If the context bundle's use count for the service is now one and
	 * the service was registered with a {@link ServiceFactory},
	 * the {@link ServiceFactory#getService ServiceFactory.getService} method
	 * is called to create a service object for the context bundle.
	 * This service object is cached by the framework.
	 * While the context bundle's use count for the service is greater than zero,
	 * subsequent calls to get the services's service object for the context bundle
	 * will return the cached service object.
	 * <br>If the service object returned by the {@link ServiceFactory}
	 * is not an <code>instanceof</code>
	 * all the classes named when the service was registered or
	 * the {@link ServiceFactory} throws an exception,
	 * <code>null</code> is returned and a
	 * {@link FrameworkEvent} of type {@link FrameworkEvent#ERROR} is broadcast.
	 * <li>The service object for the service is returned.
	 * </ol>
	 *
	 * @return A service object for the service associated with this
	 * reference.
	 */
	/* @GuardedBy("this") */
	Object getService() {
		if ((useCount > 0) || (factory == null)) {
			useCount++;
			return cachedService;
		}

		if (Debug.DEBUG && Debug.DEBUG_SERVICES) {
			Debug.println("getService[factory=" + registration.getBundle() + "](" + context.getBundleImpl() + "," + registration + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}
		final Object service;
		try {
			service = AccessController.doPrivileged(new PrivilegedAction() {
				public Object run() {
					return factory.getService(context.getBundleImpl(), registration);
				}
			});
		} catch (Throwable t) {
			if (Debug.DEBUG && Debug.DEBUG_SERVICES) {
				Debug.println(factory + ".getService() exception: " + t.getMessage()); //$NON-NLS-1$
				Debug.printStackTrace(t);
			}
			// allow the adaptor to handle this unexpected error
			context.getFramework().getAdaptor().handleRuntimeError(t);
			ServiceException se = new ServiceException(NLS.bind(Msg.SERVICE_FACTORY_EXCEPTION, factory.getClass().getName(), "getService"), ServiceException.FACTORY_EXCEPTION, t); //$NON-NLS-1$ 
			context.getFramework().publishFrameworkEvent(FrameworkEvent.ERROR, registration.getBundle(), se);
			return null;
		}

		if (service == null) {
			if (Debug.DEBUG && Debug.DEBUG_SERVICES) {
				Debug.println(factory + ".getService() returned null."); //$NON-NLS-1$
			}

			ServiceException se = new ServiceException(NLS.bind(Msg.SERVICE_OBJECT_NULL_EXCEPTION, factory.getClass().getName()), ServiceException.FACTORY_ERROR);
			context.getFramework().publishFrameworkEvent(FrameworkEvent.WARNING, registration.getBundle(), se);
			return null;
		}

		String[] clazzes = registration.getClasses();
		String invalidService = ServiceRegistry.checkServiceClass(clazzes, service);
		if (invalidService != null) {
			if (Debug.DEBUG && Debug.DEBUG_SERVICES) {
				Debug.println("Service object is not an instanceof " + invalidService); //$NON-NLS-1$
			}
			ServiceException se = new ServiceException(NLS.bind(Msg.SERVICE_FACTORY_NOT_INSTANCEOF_CLASS_EXCEPTION, factory.getClass().getName(), invalidService), ServiceException.FACTORY_ERROR);
			context.getFramework().publishFrameworkEvent(FrameworkEvent.ERROR, registration.getBundle(), se);
			return null;
		}

		this.cachedService = service;
		useCount++;

		return service;
	}

	/**
	 * Unget a service's service object.
	 * Releases the service object for a service.
	 * If the context bundle's use count for the service is zero, this method
	 * returns <code>false</code>. Otherwise, the context bundle's use count for the
	 * service is decremented by one.
	 *
	 * <p>The service's service object
	 * should no longer be used and all references to it should be destroyed
	 * when a bundle's use count for the service
	 * drops to zero.
	 *
	 * <p>The following steps are followed to unget the service object:
	 * <ol>
	 * <li>If the context bundle's use count for the service is zero or
	 * the service has been unregistered,
	 * <code>false</code> is returned.
	 * <li>The context bundle's use count for this service is decremented by one.
	 * <li>If the context bundle's use count for the service is now zero and
	 * the service was registered with a {@link ServiceFactory},
	 * the {@link ServiceFactory#ungetService ServiceFactory.ungetService} method
	 * is called to release the service object for the context bundle.
	 * <li><code>true</code> is returned.
	 * </ol>
	 *
	 * @return <code>true</code> if the context bundle's use count for the service
	 *         is zero otherwise <code>false</code>.
	 */
	/* @GuardedBy("this") */
	boolean ungetService() {
		if (useCount == 0) {
			return true;
		}

		useCount--;
		if (useCount > 0) {
			return false;
		}

		if (factory == null) {
			return true;
		}

		final Object service = cachedService;
		cachedService = null;

		if (Debug.DEBUG && Debug.DEBUG_SERVICES) {
			Debug.println("ungetService[factory=" + registration.getBundle() + "](" + context.getBundleImpl() + "," + registration + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}
		try {
			AccessController.doPrivileged(new PrivilegedAction() {
				public Object run() {
					factory.ungetService(context.getBundleImpl(), registration, service);
					return null;
				}
			});
		} catch (Throwable t) {
			if (Debug.DEBUG && Debug.DEBUG_GENERAL) {
				Debug.println(factory + ".ungetService() exception"); //$NON-NLS-1$
				Debug.printStackTrace(t);
			}

			ServiceException se = new ServiceException(NLS.bind(Msg.SERVICE_FACTORY_EXCEPTION, factory.getClass().getName(), "ungetService"), ServiceException.FACTORY_EXCEPTION, t); //$NON-NLS-1$ 
			context.getFramework().publishFrameworkEvent(FrameworkEvent.ERROR, registration.getBundle(), se);
		}

		return true;
	}

	/**
	 * Release a service's service object.
	 * <ol>
	 * <li>The bundle's use count for this service is set to zero.
	 * <li>If the service was registered with a {@link ServiceFactory},
	 * the {@link ServiceFactory#ungetService ServiceFactory.ungetService} method
	 * is called to release the service object for the bundle.
	 * </ol>
	 */
	/* @GuardedBy("this") */
	void releaseService() {
		if ((useCount == 0) || (factory == null)) {
			return;
		}
		final Object service = cachedService;
		cachedService = null;
		useCount = 0;

		if (Debug.DEBUG && Debug.DEBUG_SERVICES) {
			Debug.println("releaseService[factory=" + registration.getBundle() + "](" + context.getBundleImpl() + "," + registration + ")"); //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
		}
		try {
			AccessController.doPrivileged(new PrivilegedAction() {
				public Object run() {
					factory.ungetService(context.getBundleImpl(), registration, service);
					return null;
				}
			});
		} catch (Throwable t) {
			if (Debug.DEBUG && Debug.DEBUG_SERVICES) {
				Debug.println(factory + ".ungetService() exception"); //$NON-NLS-1$
				Debug.printStackTrace(t);
			}

			ServiceException se = new ServiceException(NLS.bind(Msg.SERVICE_FACTORY_EXCEPTION, factory.getClass().getName(), "ungetService"), ServiceException.FACTORY_EXCEPTION, t); //$NON-NLS-1$ 
			context.getFramework().publishFrameworkEvent(FrameworkEvent.ERROR, registration.getBundle(), se);
		}
	}
}
