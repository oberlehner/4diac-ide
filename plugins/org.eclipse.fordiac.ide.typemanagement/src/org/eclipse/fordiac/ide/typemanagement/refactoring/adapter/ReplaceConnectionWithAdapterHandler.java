package org.eclipse.fordiac.ide.typemanagement.refactoring.adapter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.fordiac.ide.model.libraryElement.BlockFBNetworkElement;
import org.eclipse.fordiac.ide.model.libraryElement.Connection;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetworkElement;
import org.eclipse.fordiac.ide.model.libraryElement.FBType;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;
import org.eclipse.fordiac.ide.model.typelibrary.InterfaceTypeEntry;
import org.eclipse.fordiac.ide.typemanagement.refactoring.ModelEditChange;
import org.eclipse.gef.ConnectionEditPart;
import org.eclipse.gef.EditPart;
import org.eclipse.gef.GraphicalViewer;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CompositeChange;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ui.handlers.HandlerUtil;

/**
 * Entry point: takes a single selected Connection and replaces it with an
 * Adapter type, updates both FB types (left: PLUG; right: SOCKET), creates the
 * adapter connection, and writes the adapter *.adp file into the project's type
 * folder.
 */
public class ReplaceConnectionWithAdapterHandler extends AbstractHandler {

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final IStructuredSelection sel = (IStructuredSelection) HandlerUtil.getCurrentSelectionChecked(event);
		final Object first = sel.getFirstElement();
		if (!(first instanceof final ConnectionEditPart cep)) {
			return null;
		}

		Connection selectedConn = null;
		try {
			selectedConn = getSelectedConnections(event).get(0);
		} catch (final Exception e) {

			e.printStackTrace();
		}
		final IInterfaceElement leftEnd = selectedConn.getSource();
		final IInterfaceElement rightEnd = selectedConn.getDestination();
		final BlockFBNetworkElement leftFB = leftEnd.getBlockFBNetworkElement();
		final BlockFBNetworkElement rightFB = rightEnd.getBlockFBNetworkElement();

		final Connection firstA = selectedConn;
		final var srcFB = firstA.getSource().getBlockFBNetworkElement();
		final var dstFB = firstA.getDestination().getBlockFBNetworkElement();

		boolean samePair = false;
		try {
			samePair = getSelectedConnections(event).stream()
					.allMatch(c -> c.getSource().getBlockFBNetworkElement() == srcFB
							&& c.getDestination().getBlockFBNetworkElement() == dstFB);
		} catch (final Exception e) {

			e.printStackTrace();
		}

		if (!samePair) {
			return null;
		}

		final Set<String> leftPinsToRemove = new LinkedHashSet<>();
		final Set<String> rightPinsToRemove = new LinkedHashSet<>();

		try {
			for (final Connection c : getSelectedConnections(event)) {
				leftPinsToRemove.add(c.getSource().getName()); // PosReady, X, Y, Z
				rightPinsToRemove.add(c.getDestination().getName()); // CHECK, X, Y, Z
			}
		} catch (final Exception e) {

			e.printStackTrace();
		}

		final String leftAdapterInst = "PLUG1";
		final String rightAdapterInst = "SOCKET1";

		final String newAdapterTypeName = leftFB.getName() + "_" + rightFB.getName() + "Adapter";
		final IFile adpFile = resolveAdapterTypeFile(leftFB, newAdapterTypeName);

		final var project = leftFB.eResource() != null ? org.eclipse.core.resources.ResourcesPlugin.getWorkspace()
				.getRoot().findMember(leftFB.eResource().getURI().toPlatformString(true)).getProject() : null;

		// Typically you already pass the libFolder around (same place as your proxy
		// .fbt):
		IFolder libFolder = null;/* resolve your destination folder */

		final org.eclipse.emf.ecore.resource.Resource leftTypeRes = (leftFB != null && rightFB.getType() != null)
				? leftFB.getType().eResource()
				: null;

		if (leftTypeRes != null && leftTypeRes.getURI() != null && leftTypeRes.getURI().isPlatformResource()) {
			final IPath path = new org.eclipse.core.runtime.Path(leftTypeRes.getURI().toPlatformString(true));
			final IFile leftTypeFile = org.eclipse.core.resources.ResourcesPlugin.getWorkspace().getRoot()
					.getFile(path);
			libFolder = (leftTypeFile != null && leftTypeFile.getParent() instanceof org.eclipse.core.resources.IFolder)
					? (org.eclipse.core.resources.IFolder) leftTypeFile.getParent()
					: project.getFolder("types");
		} else {
			libFolder = project.getFolder("types");
		}

		// Build and run the change (inside your LTK flow or directly):
		final var createAdapterTypeChange = new CreateAdapterTypeChange(libFolder, newAdapterTypeName,
				leftFB.getInterface(), // provider/plug
				// perspective
				rightFB.getInterface(), leftPinsToRemove, rightPinsToRemove); // acceptor/socket perspective

		// If you’re already inside a refactoring operation, enqueue this change.
		// Otherwise you can run it via PerformChangeOperation like your proxy change.

		final InterfaceTypeEntry leftTypeEntry = leftFB.getTypeEntry();
		final InterfaceTypeEntry rightTypeEntry = rightFB.getTypeEntry();

		// 1) update the LEFT FB *type*: remove old pin, add PLUG, save
		/*
		 * final Change updateLeftType = new UpdateFBTypeForAdapterChange(leftTypeEntry,
		 * true, newAdapterTypeName, leftPinsToRemove);
		 */

		final Change updateLeftType = ModelEditChange.fromModelEdits("change left", UpdateFBTypeForAdapterEdits
				.build((FBType) leftTypeEntry.getType(), leftPinsToRemove, true, newAdapterTypeName));

		/*
		 * 2) update the RIGHT FB *type*: remove old pin, add SOCKET, save final Change
		 * updateRightType = new UpdateFBTypeForAdapterChange(rightTypeEntry, false,
		 * newAdapterTypeName, rightPinsToRemove);
		 */

		final Map<String, String> rightMap = buildOldToAdapterPinNameMapping(leftPinsToRemove, rightPinsToRemove,
				((FBType) rightTypeEntry.getType()).getInterfaceList());

		final Change updateRightType = ModelEditChange.fromModelEdits("change left", UpdateFBTypeForAdapterEdits.build(
				(FBType) rightTypeEntry.getType(), rightPinsToRemove, false, newAdapterTypeName, null, rightMap));

		final List<Connection> selectedConnections = getSelectedConnections(event);

		final var edits = ModelEditChange.fromModelEdits("Replace Connection with Adapter",
				ReplaceConnectionWithAdapterEdits.build(selectedConn, newAdapterTypeName, leftAdapterInst,
						rightAdapterInst, leftPinsToRemove, rightPinsToRemove));

		// run change that builds & persists the proxy type
		final CreateAdapterProxyTypeChange adapterProxyChange = new CreateAdapterProxyTypeChange(libFolder,
				(newAdapterTypeName + "_Proxy"), newAdapterTypeName);

		final CompositeChange all = new CompositeChange("Replace Connection with Adapter");
		all.add(createAdapterTypeChange);
		all.add(adapterProxyChange);
		all.add(updateLeftType);
		all.add(updateRightType);

		// UpdateTypeEntryChange updateSysChange = new Upd

		// all.add(edits);

		try {

			final Refactoring ref = new ReplaceConnectionWithAdapterRefactoring(all);

			final CreateChangeOperation create = new CreateChangeOperation(ref);
			final PerformChangeOperation perform = new PerformChangeOperation(create);

			// enable undo/redo in the global refactoring undo manager
			// perform.setUndoManager(RefactoringCore.getUndoManager(), ref.getName());

			// run inside workspace runnable

			final var editor = HandlerUtil.getActiveEditor(event);
			org.eclipse.swt.widgets.Control redrawCtrl = null;
			final GraphicalViewer v = editor.getAdapter(GraphicalViewer.class);

			redrawCtrl = v.getControl();
			try {
				if (redrawCtrl != null && !redrawCtrl.isDisposed()) {
					redrawCtrl.setRedraw(false);
				}

				ResourcesPlugin.getWorkspace().run(perform, new NullProgressMonitor());

			} finally {
				if (redrawCtrl != null && !redrawCtrl.isDisposed()) {
					redrawCtrl.setRedraw(true);
					redrawCtrl.redraw();
				}
			}

			// ResourcesPlugin.getWorkspace().run(perform, new NullProgressMonitor());

		} catch (final org.eclipse.core.runtime.CoreException e) {
			throw new ExecutionException("Replace Connection with Adapter failed", e);
		}

		return null;
	}

// --- inline refactoring wrapper ---
	static public class ReplaceConnectionWithAdapterRefactoring extends Refactoring {
		private final Change change;

		ReplaceConnectionWithAdapterRefactoring(final Change change) {
			this.change = change;
		}

		@Override
		public String getName() {
			return change.getName();
		}

		@Override
		public RefactoringStatus checkInitialConditions(final IProgressMonitor pm) {
			return new RefactoringStatus(); // you can add sanity checks here if you like
		}

		@Override
		public RefactoringStatus checkFinalConditions(final IProgressMonitor pm) {
			return new RefactoringStatus(); // nothing extra; your ModelEditChange validates itself
		}

		@Override
		public Change createChange(final IProgressMonitor pm) {
			return change; // return the change we already built
		}

	}

	private static String safeName(final String s) {
		if (s == null || s.isBlank()) {
			return UUID.randomUUID().toString().replace("-", "");
		}
		return s.replaceAll("[^A-Za-z0-9_]", "_");
	}

	private static IFile resolveAdapterTypeFile(final FBNetworkElement anyFB, final String typeName) {

		final IProject project = anyFB.getTypeEntry().getFile().getProject();

		final IFolder typeLib = project.getFolder("typelib");
		if (!typeLib.exists()) {
			try {
				typeLib.create(true, true, null);
			} catch (final CoreException ignore) {
			}
		}
		return typeLib.getFile(typeName + ".adp");
	}

	private static List<Connection> getSelectedConnections(final ExecutionEvent event) {
		IStructuredSelection sel = null;
		try {
			sel = (IStructuredSelection) HandlerUtil.getCurrentSelectionChecked(event);
		} catch (final ExecutionException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		final Set<Connection> unique = new LinkedHashSet<>(); // de-dupe, keep order
		for (final Object o : sel.toArray()) {
			if (o instanceof final ConnectionEditPart cep) {
				final Object m = cep.getModel();
				if (m instanceof final Connection c) {
					unique.add(c);
				}
				continue;
			}

			// sometimes selection is the model already
			if (o instanceof final Connection c) {
				unique.add(c);
				continue;
			}

			// generic fallback: selected EditPart whose model is a Connection
			if (o instanceof final EditPart ep && ep.getModel() instanceof final Connection c) {
				unique.add(c);
			}
		}

		return new ArrayList<>(unique);
	}

	/**
	 * Builds a mapping from "old pin name" -> "adapter pin name".
	 *
	 * For the RIGHT side this must map: oldRightPin -> leftPin e.g. CHECK ->
	 * PosReady, X -> X, ...
	 *
	 * The mapping is robust against case/whitespace mismatches by resolving both
	 * sides against the actual names present in the given InterfaceList (if
	 * provided).
	 */
	private static Map<String, String> buildOldToAdapterPinNameMapping(final Set<String> leftPinsOrderedSet,
			final Set<String> rightPinsOrderedSet,
			final org.eclipse.fordiac.ide.model.libraryElement.InterfaceList rightInterfaceOrNull) {

		// preserve selection order
		final List<String> left = new ArrayList<>(leftPinsOrderedSet != null ? leftPinsOrderedSet : Set.of());
		final List<String> right = new ArrayList<>(rightPinsOrderedSet != null ? rightPinsOrderedSet : Set.of());

		final int n = Math.min(left.size(), right.size());
		final Map<String, String> mapping = new LinkedHashMap<>();

		// Build an index of actual right-side pin names for case-insensitive resolution
		final Map<String, String> rightIndex = new LinkedHashMap<>();
		if (rightInterfaceOrNull != null) {
			rightInterfaceOrNull.getAllInterfaceElements().forEach(ie -> {
				if (ie != null && ie.getName() != null) {
					rightIndex.put(normalize(ie.getName()), ie.getName());
				}
			});
		}

		for (int i = 0; i < n; i++) {
			final String leftPinRaw = left.get(i);
			final String rightPinRaw = right.get(i);
			if (leftPinRaw == null || rightPinRaw == null) {
				continue;
			}

			final String adapterPin = leftPinRaw.trim(); // adapter uses provider/left names
			final String oldRightPin = resolveRightPinName(rightPinRaw, rightIndex);

			if (!oldRightPin.isBlank() && !adapterPin.isBlank()) {
				mapping.put(oldRightPin, adapterPin);
			}
		}

		return mapping;
	}

	private static String resolveRightPinName(final String candidate, final Map<String, String> rightIndex) {
		final String trimmed = candidate != null ? candidate.trim() : "";
		if (trimmed.isBlank()) {
			return "";
		}
		if (rightIndex == null || rightIndex.isEmpty()) {
			return trimmed;
		}
		// Case-insensitive resolution to the actual pin name in the FB type
		final String resolved = rightIndex.get(normalize(trimmed));
		return resolved != null ? resolved : trimmed;
	}

	private static String normalize(final String s) {
		return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
	}

}
