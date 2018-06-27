/*******************************************************************************
 * Copyright (c) 2008 - 2018 Profactor GmbH, fortiss GmbH, 
 * 							 Johannes Kepler University
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Gerhard Ebenhofer, Alois Zoitl, Monika Wenger
 *     - initial API and implementation and/or initial documentation
 *******************************************************************************/
package org.eclipse.fordiac.ide.deployment;

import java.lang.reflect.InvocationTargetException;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.fordiac.ide.deployment.ResourceDeploymentData.ParameterData;
import org.eclipse.fordiac.ide.deployment.exceptions.DeploymentException;
import org.eclipse.fordiac.ide.deployment.interactors.DeviceManagementInteractorFactory;
import org.eclipse.fordiac.ide.deployment.interactors.IDeviceManagementInteractor;
import org.eclipse.fordiac.ide.deployment.util.DeploymentHelper;
import org.eclipse.fordiac.ide.deployment.util.IDeploymentListener;
import org.eclipse.fordiac.ide.model.libraryElement.Device;
import org.eclipse.fordiac.ide.model.libraryElement.FB;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetwork;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetworkElement;
import org.eclipse.fordiac.ide.model.libraryElement.InterfaceList;
import org.eclipse.fordiac.ide.model.libraryElement.Resource;
import org.eclipse.fordiac.ide.model.libraryElement.SubApp;
import org.eclipse.fordiac.ide.model.libraryElement.Value;
import org.eclipse.fordiac.ide.model.libraryElement.VarDeclaration;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;

public class DeploymentCoordinator implements IDeploymentListener {
	private static DeploymentCoordinator instance;
	
	private final Map<Device, List<VarDeclaration>> deployedDeviceProperties = new HashMap<>();

	public void addDeviceProperty(Device dev, VarDeclaration property) {
		if (deployedDeviceProperties.containsKey(dev)) {
			List<VarDeclaration> temp = deployedDeviceProperties.get(dev);
			if (!temp.contains(property)) {
				temp.add(property);
			}
		} else {
			ArrayList<VarDeclaration> temp = new ArrayList<VarDeclaration>();
			temp.add(property);
			deployedDeviceProperties.put(dev, temp);
		}
	}

	public void setDeviceProperties(Device dev,
			List<VarDeclaration> properties) {
		deployedDeviceProperties.put(dev, properties);
	}

	public void removeDeviceProperty(Device dev, VarDeclaration property) {
		if (deployedDeviceProperties.containsKey(dev)) {
			List<VarDeclaration> temp = deployedDeviceProperties.get(dev);
			if (temp.contains(property)) {
				temp.remove(property);
			}
		}
	}

	public List<VarDeclaration> getSelectedDeviceProperties(Device dev) {
		return deployedDeviceProperties.get(dev);
	}

	private DeploymentCoordinator() {
		// empty private constructor
	}

	public static DeploymentCoordinator getInstance() {
		if (instance == null) {
			instance = new DeploymentCoordinator();
		}
		return instance;
	}

	class DownloadRunnable implements IRunnableWithProgress {
		private final List<Device> devices;
		private final List<ResourceDeploymentData> resources;
		private AbstractDeviceManagementCommunicationHandler overrideDevMgmCommHandler;

		/**
		 * DownloadRunnable constructor.
		 * 
		 * @param work
		 *            the amount of download operations
		 * @param selection
		 *            the selection
		 * @param overrideDevMgmCommHandler
		 *            if not null this device management communication should be
		 *            used instead the one derived from the device profile.
		 */
		public DownloadRunnable(
				final List<Device> devices,
				final List<ResourceDeploymentData> resources,
				AbstractDeviceManagementCommunicationHandler overrideDevMgmCommHandler) {
			this.devices = devices;
			this.resources = resources;
			this.overrideDevMgmCommHandler = overrideDevMgmCommHandler;
		}

		/**
		 * Runs the check.
		 * 
		 * @param monitor
		 *            the progress monitor
		 * 
		 * @throws InvocationTargetException
		 *             the invocation target exception
		 * @throws InterruptedException
		 *             the interrupted exception
		 */
		public void run(final IProgressMonitor monitor)
				throws InvocationTargetException, InterruptedException {
			monitor.beginTask(Messages.DeploymentCoordinator_LABEL_PerformingDownload,
					calculateWorkAmount());

			for (ResourceDeploymentData resDepData : resources) {
				if (monitor.isCanceled()) {
					throw new InterruptedException(Messages.DeploymentCoordinator_LABEL_DownloadAborted);
				}

				IDeviceManagementInteractor executor = DeviceManagementInteractorFactory.INSTANCE.getDeviceManagementInteractor(resDepData.res.getDevice(), overrideDevMgmCommHandler);

				if (executor != null) {
					executor.addDeploymentListener(getInstance());
					executor.resetTypes();
					try {
						deployResource(monitor, resDepData, executor);
					} catch (final Exception e) {
						Display.getDefault().asyncExec(new Runnable() {
							@Override
							public void run() {
								final Shell shell = Display.getDefault().getActiveShell();
								
								MessageDialog.openError(shell, "Major Download Error", 
										"Resource: " + resDepData.res.getDevice().getName() + "." + resDepData.res.getName() + "\n" +
										"MGR_ID: " + DeploymentHelper.getMgrID(resDepData.res.getDevice()) + "\n" +		
										"Problem: "+ e.getMessage());
							}
						});
					}
					executor.removeDeploymentListener(getInstance());

				} else {
					printUnsupportedDeviceProfileMessageBox(resDepData.res.getDevice(), resDepData.res);
				}
			}
			for (Device device : devices){
				configureDevice(monitor, device);
				if (monitor.isCanceled()) {
					throw new InterruptedException(Messages.DeploymentCoordinator_LABEL_DownloadAborted);
				}
			}
			finished();
			monitor.done();
		}

		private int calculateWorkAmount() {
			int retVal = devices.size() + resources.size();
			for (ResourceDeploymentData resDepData : resources) {
				retVal += countResourceParams(resDepData.res);
				retVal += resDepData.fbs.size() + resDepData.connections.size() + resDepData.params.size();
				//TODO count variables of Fbs
			}
			return retVal;
		}
		
		private int countResourceParams(final Resource res) {
			int work = 0;
			for (VarDeclaration varDecl : res.getVarDeclarations()) {
				if (varDecl.getValue() != null
						&& varDecl.getValue().getValue() != null
						&& varDecl.getValue().getValue().equals("")) { //$NON-NLS-1$
					work++;
				}
			}
			return work;
		}

		protected void deployResource(final IProgressMonitor monitor, final ResourceDeploymentData resDepData, IDeviceManagementInteractor executor)
				throws DeploymentException {
			Resource res = resDepData.res;
			if (!res.isDeviceTypeResource()) {
				try {  //this try catch block with rethrowing is needed so that we can have the finally statement for disconnecting
					executor.connect();
					executor.createResource(res);
					monitor.worked(1);
					for (VarDeclaration varDecl : res.getVarDeclarations()) {
						String val = DeploymentHelper.getVariableValue(varDecl, res.getAutomationSystem());
						if(null != val){
							executor.writeResourceParameter(res, varDecl.getName(), val);
							monitor.worked(1);
						}
					}
	
					createFBInstance(resDepData, executor, monitor);	
					deployParamters(resDepData, executor, monitor);   //this needs to be done before the connections are created
					deployConnections(resDepData, executor, monitor);
	
					if (!devices.contains(res.getDevice())) {
						executor.startResource(res);
					} else {
						// resource is started when device is
						// started
					}
				} catch (Exception e) {
					throw e;
				}finally { 
					executor.disconnect();
				}
			}
		}

		private void configureDevice(final IProgressMonitor monitor, Device device) {
			IDeviceManagementInteractor executor = DeviceManagementInteractorFactory.INSTANCE.getDeviceManagementInteractor(device, overrideDevMgmCommHandler);
			List<VarDeclaration> parameters = getSelectedDeviceProperties(device);

			if (executor != null && parameters != null && parameters.size() > 0) {
				try {
					executor.addDeploymentListener(getInstance());
					executor.connect();
					for (VarDeclaration varDeclaration : parameters) {
						String value = DeploymentHelper.getVariableValue(varDeclaration, device.getAutomationSystem());
						if (null != value) {
							executor.writeDeviceParameter(device, varDeclaration.getName(), value);
						}
					}
					executor.startDevice(device);
					monitor.worked(1);
				} catch  (Exception e) {
					//TODO model refactoring - show error message to user
					Activator.getDefault().logError(e.getMessage(), e);
				}finally {
					try {
						executor.disconnect();
					} catch (DeploymentException e) {
						//TODO model refactoring - show error message to user
						Activator.getDefault().logError(e.getMessage(), e);
					}
					executor.removeDeploymentListener(getInstance());					
				}
			}
		}
		
		private void deployParamters(ResourceDeploymentData resDepData, IDeviceManagementInteractor executor,
				IProgressMonitor monitor) throws DeploymentException {
			for (ParameterData param : resDepData.params) {
				executor.writeFBParameter(resDepData.res, param.value, new FBDeploymentData(param.prefix, (FB) param.var.getFBNetworkElement()), 
						param.var);
				monitor.worked(1);				
			}
			
		}

		private void deployConnections(final ResourceDeploymentData resDepData, final IDeviceManagementInteractor executor,  IProgressMonitor monitor) throws DeploymentException {
			for (ConnectionDeploymentData con : resDepData.connections) {
				//TODO model refactoring - if one connection endpoint is part of resource find inner endpoint  
				executor.createConnection(resDepData.res, con);
				monitor.worked(1);
				if(monitor.isCanceled()){
					break;
				}
			}
		}
		
		private void createFBInstance(final ResourceDeploymentData resDepData, final IDeviceManagementInteractor executor,
				final IProgressMonitor monitor)
				throws DeploymentException {
			Resource res = resDepData.res;
			for (FBDeploymentData fb : resDepData.fbs) {
				if(!fb.fb.isResourceTypeFB()){
					executor.createFBInstance(fb, res);
					monitor.worked(1);
					InterfaceList interfaceList = fb.fb.getInterface();
					if (interfaceList != null) {
						for (VarDeclaration varDecl : interfaceList.getInputVars()) {
							String val = DeploymentHelper.getVariableValue(varDecl, res.getAutomationSystem());
							if(null != val){
								executor.writeFBParameter(res, val, fb, varDecl);
								monitor.worked(1);
							}
						}
					}
				}
			}
		}

	}
	
	/**
	 * Count work creating f bs.
	 * 
	 * @param res
	 *            the res
	 * @param fbs
	 *            the fbs
	 * @param subApps
	 *            the sub apps
	 * 
	 * @return the int
	 */
	private int countWorkCreatingFBs(final FBNetwork fbNetwork) {
		int work = 0;
		
		for (FBNetworkElement element : fbNetwork.getNetworkElements()) {
			if(element instanceof SubApp){
				work += countWorkCreatingFBs(((SubApp) element).getSubAppNetwork());				
			} else if(element instanceof FB){
				work++;
				FB fb = (FB)element;
				InterfaceList interfaceList = fb.getInterface();
				if (interfaceList != null) {
					for (VarDeclaration varDecl : interfaceList.getInputVars()) {
						if (varDecl.getInputConnections().size() == 0) {
							Value value = varDecl.getValue();
							if (value != null && value.getValue() != null) {
								work++;
							}
						}
					}
				}				
			}
		}
		return work;
	}

	public static void printUnsupportedDeviceProfileMessageBox(
			final Device device, final Resource res) {
		Display.getDefault().syncExec(() -> {				
			MessageBox messageBox = new MessageBox(PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell(), SWT.ICON_ERROR | SWT.OK);
			String resName = (null != res) ?  res.getName() : ""; //$NON-NLS-1$

			if(null != device.getProfile() && !device.getProfile().equals("")){ //$NON-NLS-1$
				messageBox.setMessage(MessageFormat.format(Messages.DeploymentCoordinator_MESSAGE_DefinedProfileNotSupported,
							new Object[] { device.getProfile(), device.getName(), resName }));
			}else{
				messageBox.setMessage(MessageFormat.format(Messages.DeploymentCoordinator_MESSAGE_ProfileNotSet,
						new Object[] {device.getName(), resName }));
			}				
			messageBox.open();
		});			
	}

	/**
	 * Perform deployment.
	 * 
	 * @param selection
	 *            the selection
	 * @param overrideDevMgmCommHandler
	 *            if not null this device management communication should be
	 *            used instead the one derived from the device profile.
	 */
	public void performDeployment(
			final Object[] selection,
			AbstractDeviceManagementCommunicationHandler overrideDevMgmCommHandler) {
		List<Device> devices = new ArrayList<>();
		List<ResourceDeploymentData> resDepData = new ArrayList<>();
		
		for (int i = 0; i < selection.length; i++) {
			Object object = selection[i];
			if (object instanceof Resource) {
				resDepData.add(new ResourceDeploymentData((Resource) object));				
			} else if(object instanceof Device){
				List<VarDeclaration> parameters = getSelectedDeviceProperties((Device)object);
				if (parameters != null && parameters.size() > 0) {
					devices.add((Device)object);
				}
			}
		}
		DownloadRunnable download = new DownloadRunnable(devices, resDepData, overrideDevMgmCommHandler);
		Shell shell = Display.getDefault().getActiveShell();
		try {
			new ProgressMonitorDialog(shell).run(true, true, download);
		} catch (InvocationTargetException ex) {
			MessageDialog.openError(shell, "Error", ex.getMessage());
		} catch (InterruptedException ex) {
			MessageDialog.openInformation(shell,
					Messages.DeploymentCoordinator_LABEL_DownloadAborted,
					ex.getMessage());
		}
	}

	public void performDeployment(final Object[] selection) {
		performDeployment(selection, null);
	}

	

	/** The listeners. */
	private final List<IDeploymentListener> listeners = new ArrayList<>();

	/**
	 * Response received x.
	 * 
	 * @param response
	 *            the response
	 * @param source
	 *            the source
	 */
	@Override
	public void responseReceived(final String response, final String source) {
		listeners.forEach(l -> l.responseReceived(response, source));
	}

	/**
	 * Post command sent x.
	 * 
	 * @param command
	 *            the command
	 * @param destination
	 *            the destination
	 */
	@Override
	public void postCommandSent(final String command, final String destination) {
		listeners.forEach(l -> l.postCommandSent(command, destination));
	}

	@Override
	public void postCommandSent(final String info, final String destination, final String command) {
		listeners.forEach(l -> l.postCommandSent(info, destination, command));
	}

	/**
	 * Post command sent x.
	 * 
	 * @param message
	 *            the message unformatted
	 */
	@Override
	public void postCommandSent(final String message) {
		listeners.forEach(l -> l.postCommandSent(message));
	}

	/**
	 * Finished x.
	 */
	@Override
	public void finished() {
		listeners.forEach(l -> l.finished());
	}

	/**
	 * Adds the deployment listener.
	 * 
	 * @param listener
	 *            the listener
	 */
	public void addDeploymentListener(final IDeploymentListener listener) {
		if (!listeners.contains(listener)) {
			listeners.add(listener);
		}
	}

	/**
	 * Removes the deployment listener.
	 * 
	 * @param listener
	 *            the listener
	 */
	public void removeDeploymentListener(final IDeploymentListener listener) {
		if (listeners.contains(listener)) {
			listeners.remove(listener);
		}
	}

	/**
	 * Enable output.
	 * 
	 * @param executor
	 *            the executor
	 */
	public void enableOutput(IDeviceManagementInteractor interactor) {
		interactor.addDeploymentListener(this);
	}

	/**
	 * Flush.
	 */
	public void flush() {
		finished();
	}

	/**
	 * Disable output.
	 * 
	 * @param executor
	 *            the executor
	 */
	public void disableOutput(IDeviceManagementInteractor interactor) {
		interactor.removeDeploymentListener(this);
	}

}
