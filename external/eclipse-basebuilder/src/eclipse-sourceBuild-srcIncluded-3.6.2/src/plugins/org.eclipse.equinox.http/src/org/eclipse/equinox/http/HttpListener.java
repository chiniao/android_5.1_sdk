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

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.*;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.equinox.http.servlet.*;
import org.eclipse.equinox.socket.ServerSocketInterface;
import org.eclipse.equinox.socket.SocketInterface;
import org.eclipse.osgi.util.NLS;
import org.osgi.framework.*;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.NamespaceException;

public class HttpListener extends Thread implements ServiceFactory {
	protected Http http;
	/** ServerSocket upon which this listener operates */
	protected volatile ServerSocketInterface serverSocket;
	/** Controlling HttpConfiguration object */
	protected HttpConfiguration configuration;
	/** if true this thread must terminate */
	protected volatile boolean running;
	protected ServiceRegistration service;
	private String httpsvcClass = "org.osgi.service.http.HttpService"; //$NON-NLS-1$
	protected Hashtable registrations;
	/** Mapping of HttpContext => ServletContextImpl */
	protected Hashtable servletContexts;
	protected int socketTimeout;
	protected Object lock = new Object();

	/**
	 * Constructor.
	 *
	 */
	protected HttpListener(Http http, HttpConfiguration configuration, Dictionary properties) throws IOException {
		this.http = http;
		this.configuration = configuration;
		registrations = new Hashtable(51);
		//initialize servletContext Hashtable
		servletContexts = new Hashtable(15);
		setProperties(properties);
		start();
	}

	/**
	 * Returns true if this thread has been closed.
	 * @return boolean
	 */
	public boolean isClosed() {
		return (!running);
	}

	/**
	 * Close this thread.
	 */
	public void close() {
		running = false;
		try {
			setProperties(null);
		} catch (IOException e) {
			/* this will not occur when calling with null */
		}

		servletContexts = null;
	}

	/*
	 * ----------------------------------------------------------------------
	 *      ServiceFactory Interface implementation
	 * ----------------------------------------------------------------------
	 */
	public Object getService(Bundle bundle, ServiceRegistration reg) {
		return (new HttpService(this, bundle));
	}

	public void ungetService(Bundle bundle, ServiceRegistration reg, Object httpService) {
		((HttpService) httpService).destroy();
	}

	public synchronized void setProperties(Dictionary properties) throws IOException {
		ServerSocketInterface oldServerSocket = serverSocket;

		if (properties != null) {
			String address = (String) properties.get(HttpConfiguration.keyHttpAddress);
			int port = ((Integer) (properties.get(HttpConfiguration.keyHttpPort))).intValue();
			String scheme = (String) properties.get(HttpConfiguration.keyHttpScheme);
			socketTimeout = ((Integer) (properties.get(HttpConfiguration.keyHttpTimeout))).intValue() * 1000;

			if ("ALL".equalsIgnoreCase(address)) { //$NON-NLS-1$
				address = null;
			}

			if ((serverSocket == null) || (port != serverSocket.getLocalPort()) || !scheme.equals(serverSocket.getScheme())) {
				serverSocket = configuration.createServerSocket(address, port, scheme);
				closeServerSocket(oldServerSocket);
			} else if ((serverSocket.getAddress() != null && !serverSocket.getAddress().equals(address)) || (serverSocket.getAddress() == null && address != null)) {
				serverSocket = null;
				closeServerSocket(oldServerSocket);
				serverSocket = configuration.createServerSocket(address, port, scheme);
				synchronized (lock) {
					lock.notify();
				}
			}

			properties.put(HttpConfiguration.keyHttpPort, new Integer(serverSocket.getLocalPort()));
			if (service == null) {
				service = http.context.registerService(httpsvcClass, this, properties);
			} else {
				service.setProperties(properties);
			}
		} else {
			serverSocket = null;
			closeServerSocket(oldServerSocket);
			service.unregister();
			service = null;
		}
	}

	protected Object getProperty(String key) {
		return service.getReference().getProperty(key);
	}

	private void closeServerSocket(ServerSocketInterface oldServerSocket) {
		if (oldServerSocket != null) {
			try {
				try {
					Socket phonyClient = new Socket(InetAddress.getByName(oldServerSocket.getAddress()), oldServerSocket.getLocalPort());
					phonyClient.close();
				} catch (IOException e) {
					http.logWarning(HttpMsg.HTTP_UNEXPECTED_IOEXCEPTION, e);
				}
			} finally {
				try {
					oldServerSocket.close();
				} catch (IOException e) {
					http.logWarning(HttpMsg.HTTP_UNEXPECTED_IOEXCEPTION, e);
				}
			}

			configuration.pool.recallThreads();
		}
	}

	public void run() {
		running = true;

		while (running) {
			SocketInterface socket = null;

			try {
				if (serverSocket == null) {
					if (running) {
						try {
							synchronized (lock) {
								lock.wait(5000);
							}
						} catch (InterruptedException e) {
							// ignore and check exit condition
						}
						if (serverSocket == null) {
							running = false;
							continue;
						}
					} else {
						continue;
					}
				}
				ServerSocketInterface tempServerSocket = this.serverSocket;
				socket = tempServerSocket.acceptSock();

				if (tempServerSocket != this.serverSocket) /* socket changed while we were waiting */
				{
					try {
						socket.close();
					} catch (IOException e) {
						// TODO: consider logging
					}

					socket = null;
				}
			} catch (IOException e) {
				if (serverSocket != null) {
					http.logError(NLS.bind(HttpMsg.HTTP_ACCEPT_SOCKET_EXCEPTION, new Integer(serverSocket.getLocalPort())), e);
				}
			}

			if (socket != null) {
				HttpThread thread = configuration.pool.getThread();

				if (thread != null) {
					thread.handleConnection(new HttpConnection(http, this, socket, socketTimeout));
				} else {
					try {
						socket.close();
					} catch (Exception e) {
						// TODO: consider logging
					}
				}
			}
		}
	}

	protected void handleConnection(SocketInterface socket) throws IOException, ServletException {
		/* Create the servlet request and response objects */
		HttpServletResponseImpl response = new HttpServletResponseImpl(socket, http);

		HttpServletRequestImpl request = new HttpServletRequestImpl(socket, http, response);

		/* After the request and response objects are successfully created,
		 * we enter a try/finally block to ensure that the response is
		 * always closed.
		 */
		try {
			//Get the URI from socket
			String uri = request.getRequestURI();
			//Get Registration object associated with the request
			Registration registration = getRegistration(uri);

			while (registration != null) {
				try {
					if (registration.getHttpContext().handleSecurity(request, response)) {
						//Service Request
						registration.service(request, response);
					}

					return;
				} catch (ResourceUnavailableException e) {
					uri = reduceURI(uri);

					if (uri == null) {
						registration = null;
					} else {
						registration = getRegistration(uri);
					}
				}
			}

			if (registration == null) {
				if (Http.DEBUG) {
					http.logDebug("File " + request.getRequestURI() + //$NON-NLS-1$
							" not found -- No Registration object"); //$NON-NLS-1$
				}

				response.sendError(HttpServletResponse.SC_NOT_FOUND);
			}
		} finally {
			response.close(); /* close (and flush) the response output */
		}
	}

	protected ResourceRegistration registerResources(Bundle bundle, String alias, String name, HttpContext httpContext) throws NamespaceException {
		synchronized (registrations) {
			if (Http.DEBUG) {
				http.logDebug("HttpService -- Registering Resource --  Alias = " + //$NON-NLS-1$
						alias + " Name = " + name); //$NON-NLS-1$
			}

			checkAlias(alias);

			//check to see if name is valid
			if (name == null)
				throw new IllegalArgumentException(NLS.bind(HttpMsg.HTTP_RESOURCE_NAME_INVALID_EXCEPTION, name));
			int length = name.length();
			if (length > 1) { //we need to allow "/" and ""
				if (name.endsWith("/")) //$NON-NLS-1$
				{
					throw new IllegalArgumentException(NLS.bind(HttpMsg.HTTP_RESOURCE_NAME_INVALID_EXCEPTION, name));
				}
			}

			checkNamespace(alias);

			ResourceRegistration registration = new ResourceRegistration(bundle, alias, name, httpContext, http);

			registrations.put(alias, registration);

			return (registration);
		}
	}

	private void checkAlias(String alias) {
		if (alias == null)
			throw new IllegalArgumentException(NLS.bind(HttpMsg.HTTP_ALIAS_INVALID_EXCEPTION, alias));
		//check to see if the alias is valid
		if (!alias.equals("/")) //$NON-NLS-1$
		{ //so one can register at "/"
			if (!alias.startsWith("/") || alias.endsWith("/")) //$NON-NLS-1$ //$NON-NLS-2$
			{
				throw new IllegalArgumentException(NLS.bind(HttpMsg.HTTP_ALIAS_INVALID_EXCEPTION, alias));
			}
		}
	}

	protected ServletRegistration registerServlet(Bundle bundle, String alias, Servlet servlet, Dictionary initparams, HttpContext httpContext) throws ServletException, NamespaceException {
		synchronized (registrations) {
			if (Http.DEBUG) {
				http.logDebug("HttpService -- Registering Servlet -- Alias = " + alias); //$NON-NLS-1$
			}

			checkAlias(alias);

			checkNamespace(alias);

			if (servlet == null) {
				throw new IllegalArgumentException(HttpMsg.HTTP_SERVLET_NULL_EXCEPTION);
			}

			if (http.servlets.contains(servlet)) {
				throw new ServletException(HttpMsg.HTTP_SERVLET_ALREADY_REGISTERED_EXCEPTION);
			}
			/* Determine the ServletContext */
			ServletContextImpl servletContext = getServletContext(httpContext);
			//Create registration object
			ServletRegistration registration = new ServletRegistration(bundle, alias, servlet, httpContext, servletContext);

			//call servlet's init() method
			try {
				servlet.init(new ServletConfigImpl(servletContext, initparams));
			} catch (ServletException e) {
				ungetServletContext(httpContext);

				throw e;
			} catch (Throwable t) {
				ungetServletContext(httpContext);

				throw new ServletException(HttpMsg.HTTP_SERVET_INIT_EXCEPTION, t);
			}

			http.servlets.addElement(servlet);
			registrations.put(alias, registration);
			return (registration);
		}
	}

	protected void unregister(Bundle bundle, String alias) throws IllegalArgumentException {
		synchronized (registrations) {
			Registration registration = (Registration) registrations.get(alias);
			if (registration != null) {
				//this is to prevent other bundles from unregistering a bundle's resource/servlet
				if (registration.getBundle() != bundle) {
					registration = null;
				}
			}

			removeRegistration(registration);
		}
	}

	protected HttpContext createDefaultHttpContext(Bundle bundle) {
		return (new DefaultHttpContext(bundle, http.securityTracker));
	}

	protected ServletContextImpl getServletContext(HttpContext httpContext) {
		/* Determine the ServletContext */
		ServletContextImpl servletContext = (ServletContextImpl) servletContexts.get(httpContext);

		if (servletContext == null) {
			servletContext = new ServletContextImpl(http, this, httpContext);
			servletContexts.put(httpContext, servletContext);
		}

		servletContext.incrUseCount();

		return (servletContext);
	}

	protected void ungetServletContext(HttpContext httpContext) {
		ServletContextImpl servletContext = (ServletContextImpl) servletContexts.get(httpContext);

		if (servletContext != null) {
			int useCount = servletContext.decrUseCount();

			if (useCount <= 0) {
				servletContexts.remove(httpContext);
			}
		}
	}

	protected void destroyBundle(Bundle bundle) {
		if (registrations != null) {
			if (Http.DEBUG) {
				http.logDebug("Removing bundle " + bundle + //$NON-NLS-1$
						" from HttpListener"); //$NON-NLS-1$
			}

			synchronized (registrations) {
				Enumeration e = registrations.elements();

				while (e.hasMoreElements()) {
					Registration reg = (Registration) e.nextElement();

					if (bundle == reg.getBundle()) {
						removeRegistration(reg);
					}
				}
			}
		}
	}

	private void checkNamespace(String uri) throws NamespaceException {
		if (uri == null)
			throw new IllegalArgumentException(NLS.bind(HttpMsg.HTTP_ALIAS_INVALID_EXCEPTION, uri));
		// If alias already exists in master resource table,
		// throw NamespaceException
		if (registrations.get(uri) != null) {
			throw new NamespaceException(NLS.bind(HttpMsg.HTTP_ALIAS_ALREADY_REGISTERED_EXCEPTION, uri));
		}
	}

	public Registration getRegistration(String uri) {
		synchronized (registrations) {
			while (uri != null) {
				Registration reg = (Registration) registrations.get(uri);

				if (reg != null) {
					return (reg);
				}
				uri = reduceURI(uri);
			}
			return (null);
		}
	}

	/**
	 * Reduce the input URI per the HttpService spec.
	 *
	 * @param uri input URI to be reduced.
	 * @return Reduced URI or null if no further reduction possible.
	 */
	private String reduceURI(String uri) {
		if (uri.equals("/")) //$NON-NLS-1$
		{
			return (null);
		}

		int index = uri.lastIndexOf('/');

		if (index < 0) {
			return (null);
		}

		if (index == 0) {
			return ("/"); //$NON-NLS-1$
		}

		return (uri.substring(0, index));
	}

	/**
	 * Must be called while holding the registrations lock
	 *
	 */
	private void removeRegistration(Registration registration) {
		if (registration == null) {
			throw new IllegalArgumentException(HttpMsg.HTTP_ALIAS_UNREGISTER_EXCEPTION);
		}
		registrations.remove(registration.getAlias());
		if (registration instanceof ServletRegistration) {
			Servlet servlet = ((ServletRegistration) registration).getServlet();
			http.servlets.removeElement(servlet);
		}

		// BUGBUG Must not call servlet.destroy while it is processing requests!
		// Servlet 2.2 Section 3.3.4
		registration.destroy();

		if (registration instanceof ServletRegistration) {
			ungetServletContext(registration.getHttpContext());
		}
	}
}
