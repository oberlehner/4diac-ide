package org.eclipse.fordiac.ide.typemanagement.refactoring.adapter;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.fordiac.ide.model.typelibrary.AdapterTypeEntry;
import org.eclipse.fordiac.ide.model.typelibrary.TypeEntry;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibrary;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibraryManager;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Explorer handler: "Insert Adapter Proxy" - works on AdapterTypeEntry
 * selection OR on a .adp IFile selection - creates <AdapterName>_Proxy
 * (Composite FB) next to the .adp
 */
public class IntroduceAdapterProxyHandler extends AbstractHandler {

	public IntroduceAdapterProxyHandler() {
		setBaseEnabled(true);
	}

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final ISelection sel = HandlerUtil.getCurrentSelection(event);

		final AdapterTypeEntry atEntry = resolveAdapterTypeEntry(sel);
		if (atEntry == null) {
			return null; // wrong context -> do nothing
		}

		final String adapterName = atEntry.getTypeName(); // e.g. "movePos"
		final String proxyName = adapterName + "_Proxy"; // e.g. "movePos_Proxy"

		final IFile adpFile = atEntry.getFile();
		final IProject project = adpFile.getProject();

		// prefer same folder as the .adp
		final IFolder targetFolder = (adpFile.getParent() instanceof IFolder) ? (IFolder) adpFile.getParent()
				: project.getFolder("typelibrary"); // fallback

		// run change that builds & persists the proxy type
		final CreateAdapterProxyTypeChange change = new CreateAdapterProxyTypeChange(targetFolder, proxyName,
				adapterName);

		try {
			new PerformChangeOperation(change).run(null);

			// register in project TypeLibrary so it is immediately usable
			final TypeLibrary tl = TypeLibraryManager.INSTANCE.getTypeLibrary(project);
			tl.createTypeEntry(targetFolder.getFile(proxyName + ".fbt"));

		} catch (final CoreException e) {
			throw new ExecutionException("Insert Adapter Proxy failed", e);
		}

		return null;
	}

	// --- helpers ---------------------------------------------------------------

	private static AdapterTypeEntry resolveAdapterTypeEntry(final ISelection selection) throws ExecutionException {
		if (!(selection instanceof final IStructuredSelection iss) || iss.isEmpty()) {
			return null;
		}
		final Object first = iss.getFirstElement();

		if (first instanceof AdapterTypeEntry) {
			return (AdapterTypeEntry) first;
		}

		if (first instanceof final IFile f && "adp".equalsIgnoreCase(f.getFileExtension())) {
			final TypeEntry te = TypeLibraryManager.INSTANCE.getTypeEntryForFile(f);
			if (te instanceof AdapterTypeEntry) {
				return (AdapterTypeEntry) te;
			}
		}
		return null;
	}

	// optional small util for error reporting (not strictly required)
	@SuppressWarnings("unused")
	private static ExecutionException fail(final String msg, final Throwable t) {
		return new ExecutionException(msg,
				new CoreException(new Status(IStatus.ERROR, "org.eclipse.fordiac.ide.typemanagement", msg, t)));
	}
}
