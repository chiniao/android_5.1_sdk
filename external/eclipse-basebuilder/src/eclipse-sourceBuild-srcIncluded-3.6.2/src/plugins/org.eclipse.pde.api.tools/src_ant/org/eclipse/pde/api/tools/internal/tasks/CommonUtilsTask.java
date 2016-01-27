/*******************************************************************************
 * Copyright (c) 2008, 2010 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.api.tools.internal.tasks;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.osgi.util.NLS;
import org.eclipse.pde.api.tools.internal.model.ApiModelFactory;
import org.eclipse.pde.api.tools.internal.provisional.ApiPlugin;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiBaseline;
import org.eclipse.pde.api.tools.internal.provisional.model.IApiComponent;
import org.eclipse.pde.api.tools.internal.util.ExcludedElements;
import org.eclipse.pde.api.tools.internal.util.TarException;
import org.eclipse.pde.api.tools.internal.util.Util;

/**
 * Common code for api tooling ant task.
 * 
 * @since 1.0.0
 * @noextend This class is not intended to be subclassed by clients.
 */
public abstract class CommonUtilsTask extends Task {
	private static final String CVS_FOLDER_NAME = "CVS"; //$NON-NLS-1$
	private static final String ECLIPSE_FOLDER_NAME = "eclipse"; //$NON-NLS-1$
	private static final String PLUGINS_FOLDER_NAME = "plugins"; //$NON-NLS-1$

	protected static final String CURRENT = "currentBaseline"; //$NON-NLS-1$
	protected static final String CURRENT_BASELINE_NAME = "current_baseline"; //$NON-NLS-1$
	protected static final String REFERENCE = "referenceBaseline"; //$NON-NLS-1$
	protected static final String REFERENCE_BASELINE_NAME = "reference_baseline"; //$NON-NLS-1$

	protected boolean debug;
	protected String eeFileLocation;
	protected String currentBaselineLocation;
	protected String referenceBaselineLocation;
	protected String excludeListLocation;
	
	protected String reportLocation;
	
	/**
	 * Creates a baseline with the given name and ee file location in the given directory
	 * @param baselineName
	 * @param dir
	 * @param eeFileLocation
	 * @return a new {@link IApiBaseline}
	 */
	protected IApiBaseline createBaseline(String baselineName, File dir, String eeFileLocation) {
		try {
			IApiBaseline baseline = null;
			if (ApiPlugin.isRunningInFramework()) {
				baseline = ApiModelFactory.newApiBaseline(baselineName);
			} else if (eeFileLocation != null) {
				baseline = ApiModelFactory.newApiBaseline(baselineName, new File(eeFileLocation));
			} else {
				baseline = ApiModelFactory.newApiBaseline(baselineName, Util.getEEDescriptionFile());
			}
			// create a component for each jar/directory in the folder
			File[] files = dir.listFiles();
			if(files == null) {
				throw new BuildException(
						NLS.bind(Messages.directoryIsEmpty,
						dir.getAbsolutePath()));
			}
			List components = new ArrayList();
			for (int i = 0; i < files.length; i++) {
				File bundle = files[i];
				if (!bundle.getName().equals(CVS_FOLDER_NAME)) {
					// ignore CVS folder
					IApiComponent component = ApiModelFactory.newApiComponent(baseline, bundle.getAbsolutePath());
					if(component != null) {
						components.add(component);
					}
				}
			}
			
			baseline.addApiComponents((IApiComponent[]) components.toArray(new IApiComponent[components.size()]));
			return baseline;
		} catch (CoreException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	/**
	 * Deletes an {@link IApiBaseline} from the given folder
	 * @param referenceLocation
	 * @param folder
	 */
	protected void deleteBaseline(String referenceLocation, File folder) {
		if (isArchive(referenceLocation)) {
			Util.delete(folder.getParentFile());
		}
	}
	
	/**
	 * Extract extracts the SDK from the given location to the given directory name
	 * @param installDirName
	 * @param location
	 * @return the {@link File} handle to the extracted SDK
	 */
	protected File extractSDK(String installDirName, String location) {
		File file = new File(location);
		File locationFile = file;
		if (!locationFile.exists()) {
			throw new BuildException(NLS.bind(Messages.fileDoesnotExist, location));
		}
		if (isArchive(location)) {
			File tempDir = new File(System.getProperty("java.io.tmpdir")); //$NON-NLS-1$
			File installDir = new File(tempDir, installDirName);
			if (installDir.exists()) {
				// delete existing folder
				if (!Util.delete(installDir)) {
					throw new BuildException(
						NLS.bind(
							Messages.couldNotDelete,
							installDir.getAbsolutePath()));
				}
			}
			if (!installDir.mkdirs()) {
				throw new BuildException(
						NLS.bind(
								Messages.couldNotCreate,
								installDir.getAbsolutePath()));
			}
			try {
				if (isZipJarFile(location)) {
					Util.unzip(location, installDir.getAbsolutePath());
				} else if (isTGZFile(location)) {
					Util.guntar(location, installDir.getAbsolutePath());
				}
			} catch (IOException e) {
				throw new BuildException(
					NLS.bind(
						Messages.couldNotUnzip,
						new String[] {
								location,
								installDir.getAbsolutePath()
						}));
			} catch (TarException e) {
				throw new BuildException(
						NLS.bind(
								Messages.couldNotUntar,
								new String[] {
										location,
										installDir.getAbsolutePath()
								}));
			}
			return new File(installDir, ECLIPSE_FOLDER_NAME);
		} else {
			return locationFile;
		}
	}
	
	/**
	 * Returns a file handle for the plug-ins directory within the given file
	 * @param dir
	 * @return the plug-ins directory file handle within the given file
	 */
	protected File getInstallDir(File dir) {
		return new File(dir, PLUGINS_FOLDER_NAME);
	}
	
	/**
	 * Returns if the given file name is the name of an archive file, 
	 * where an archive file is described as *.zip, *.jar, *.tar.gz or *.tgz
	 * 
	 * @param fileName
	 * @return true if the file name if that of an archive, false otherwise
	 */
	private boolean isArchive(String fileName) {
		return isZipJarFile(fileName) || isTGZFile(fileName);
	}
	
	/**
	 * Returns if the given file name represents a G-zip file name, where the name 
	 * has an extension of *.tar.gz or *.tgz
	 * 
	 * @param fileName
	 * @return true if the given file name is that of a G-zip archive, false otherwise
	 */
	private boolean isTGZFile(String fileName) {
		String normalizedFileName = fileName.toLowerCase();
		return normalizedFileName.endsWith(".tar.gz") //$NON-NLS-1$
			|| normalizedFileName.endsWith(".tgz"); //$NON-NLS-1$
	}
	
	/**
	 * Returns if the given file name represents a 'standard' archive, where the name
	 * has an extension of *.zip or *.jar
	 * 
	 * @param fileName
	 * @return true if the given file name is that of a 'standard' archive, false otherwise
	 */
	private boolean isZipJarFile(String fileName) {
		String normalizedFileName = fileName.toLowerCase();
		return normalizedFileName.endsWith(".zip") //$NON-NLS-1$
			|| normalizedFileName.endsWith(".jar"); //$NON-NLS-1$
	}
	
	/**
	 * Initializes the exclude list from the given file location, and returns
	 * a {@link Set} of project names that should be excluded.
	 * 
	 * @param excludeListLocation
	 * @return the set of project names to be excluded
	 */
	protected static ExcludedElements initializeExcludedElement(String excludeListLocation, IApiBaseline baseline, boolean debug) {
		return Util.initializeRegexExcludeList(excludeListLocation, baseline, debug);
	}

	/**
	 * Saves the report with the given name in the report location in a child directory with 
	 * the componentID name
	 * @param componentID
	 * @param contents
	 * @param reportname
	 */
	protected void saveReport(String componentID, String contents, String reportname) {
		File dir = new File(this.reportLocation);
		if (!dir.exists()) {
			if (!dir.mkdirs()) {
				throw new BuildException(NLS.bind(Messages.errorCreatingReportDirectory, this.reportLocation));
			}
		}
		File reportComponentIDDir = new File(dir, componentID);
		if (!reportComponentIDDir.exists()) {
			if (!reportComponentIDDir.mkdirs()) {
				throw new BuildException(NLS.bind(Messages.errorCreatingReportDirectory, reportComponentIDDir));
			}
		}
		File reportFile = new File(reportComponentIDDir, reportname);
		BufferedWriter writer = null;
		try {
			writer = new BufferedWriter(new FileWriter(reportFile));
			writer.write(contents);
			writer.flush();
		} catch (IOException e) {
			ApiPlugin.log(e);
		} finally {
			if (writer != null) {
				try {
					writer.close();
				} catch (IOException e) {
					// ignore
				}
			}
		}
	}
	
	/**
	 * Parses and returns patterns as an array of Strings or <code>null</code> if none.
	 * 
	 * @param patterns comma separated list or <code>null</code>
	 * @return individual patterns or <code>null</code>
	 */
	protected String[] parsePatterns(String patterns) {
		if (patterns == null || patterns.trim().length() == 0) {
			return null;
		}
		String[] strings = patterns.split(","); //$NON-NLS-1$
		List list = new ArrayList();
		for (int i = 0; i < strings.length; i++) {
			String pattern = strings[i].trim();
			if (pattern.length() > 0) {
				list.add(pattern);
			}
		}
		return (String[]) list.toArray(new String[list.size()]);
	}

	public static String convertToHtml(String s) {
		char[] contents = s.toCharArray();
		StringBuffer buffer = new StringBuffer();
		for (int i = 0, max = contents.length; i < max; i++) {
			char c = contents[i];
			switch (c) {
				case '<':
					buffer.append("&lt;"); //$NON-NLS-1$
					break;
				case '>':
					buffer.append("&gt;"); //$NON-NLS-1$
					break;
				case '\"':
					buffer.append("&quot;"); //$NON-NLS-1$
					break;
				case '&':
					buffer.append("&amp;"); //$NON-NLS-1$
					break;
				case '^':
					buffer.append("&and;"); //$NON-NLS-1$
					break;
				default:
					buffer.append(c);
			}
		}
		return String.valueOf(buffer);
	}
}
