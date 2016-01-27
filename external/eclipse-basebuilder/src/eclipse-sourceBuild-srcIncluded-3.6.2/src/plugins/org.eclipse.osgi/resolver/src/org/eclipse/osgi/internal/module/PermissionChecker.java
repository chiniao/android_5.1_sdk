/*******************************************************************************
 * Copyright (c) 2004, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.internal.module;

import java.security.Permission;
import org.eclipse.osgi.service.resolver.*;
import org.osgi.framework.*;

public class PermissionChecker {
	private BundleContext context;
	private boolean checkPermissions = false;
	private ResolverImpl resolver;

	public PermissionChecker(BundleContext context, boolean checkPermissions, ResolverImpl resolver) {
		this.context = context;
		this.checkPermissions = checkPermissions && context != null; // only can check permissions if context is not null
		this.resolver = resolver;
	}

	/*
	 * checks the permission for a bundle to import/reqiure a constraint
	 * and for a bundle to export/provide a package/BSN
	 */
	public boolean checkPermission(VersionConstraint vc, BaseDescription bd) {
		if (!checkPermissions)
			return true;
		// TODO could optimize out the producer permission check on export package
		boolean success = false;
		Permission producerPermission = null, consumerPermission = null;
		Bundle producer = null, consumer = null;
		int errorType = 0;
		if (vc instanceof ImportPackageSpecification) {
			errorType = ResolverError.IMPORT_PACKAGE_PERMISSION;
			producer = context.getBundle(((ExportPackageDescription) bd).getExporter().getBundleId());
			producerPermission = new PackagePermission(bd.getName(), PackagePermission.EXPORTONLY);
			consumerPermission = producer != null ? new PackagePermission(vc.getName(), producer, PackagePermission.IMPORT) : new PackagePermission(vc.getName(), PackagePermission.IMPORT);
		} else {
			boolean requireBundle = vc instanceof BundleSpecification;
			errorType = requireBundle ? ResolverError.REQUIRE_BUNDLE_PERMISSION : ResolverError.FRAGMENT_BUNDLE_PERMISSION;
			producerPermission = new BundlePermission(bd.getName(), requireBundle ? BundlePermission.PROVIDE : BundlePermission.HOST);
			consumerPermission = new BundlePermission(vc.getName(), requireBundle ? BundlePermission.REQUIRE : BundlePermission.FRAGMENT);
			producer = context.getBundle(((BundleDescription) bd).getBundleId());
		}
		consumer = context.getBundle(vc.getBundle().getBundleId());
		if (producer != null && (producer.getState() & Bundle.UNINSTALLED) == 0) {
			success = producer.hasPermission(producerPermission);
			if (!success) {
				switch (errorType) {
					case ResolverError.IMPORT_PACKAGE_PERMISSION :
						errorType = ResolverError.EXPORT_PACKAGE_PERMISSION;
						break;
					case ResolverError.REQUIRE_BUNDLE_PERMISSION :
					case ResolverError.FRAGMENT_BUNDLE_PERMISSION :
						errorType = errorType == ResolverError.REQUIRE_BUNDLE_PERMISSION ? ResolverError.PROVIDE_BUNDLE_PERMISSION : ResolverError.HOST_BUNDLE_PERMISSION;
						break;
				}
				resolver.getState().addResolverError(vc.getBundle(), errorType, producerPermission.toString(), vc);
			}
		}
		if (success && consumer != null && (consumer.getState() & Bundle.UNINSTALLED) == 0) {
			success = consumer.hasPermission(consumerPermission);
			if (!success)
				resolver.getState().addResolverError(vc.getBundle(), errorType, consumerPermission.toString(), vc);
		}

		return success;
	}

	boolean checkPackagePermission(ExportPackageDescription export) {
		if (!checkPermissions)
			return true;
		Bundle bundle = context.getBundle(export.getExporter().getBundleId());
		return bundle == null ? false : bundle.hasPermission(new PackagePermission(export.getName(), PackagePermission.EXPORTONLY));
	}
}
