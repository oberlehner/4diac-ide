package org.eclipse.fordiac.ide.typemanagement.refactoring.adapter;

import java.util.List;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterConnection;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterType;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetwork;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;
import org.eclipse.fordiac.ide.typemanagement.refactoring.ModelEditChange;
import org.eclipse.fordiac.ide.typemanagement.refactoring.adapter.edits.InsertAdapterProxyOnConnectionEdit;
import org.eclipse.gef.ConnectionEditPart;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.CreateChangeOperation;
import org.eclipse.ltk.core.refactoring.PerformChangeOperation;
import org.eclipse.ltk.core.refactoring.Refactoring;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;
import org.eclipse.ui.handlers.HandlerUtil;

public class IntroduceAdapterProxyOnConnectionHandler extends AbstractHandler {

	@Override
	public Object execute(final ExecutionEvent event) throws ExecutionException {
		final ISelection sel = HandlerUtil.getActiveMenuSelection(event);
		if (!(sel instanceof final IStructuredSelection s) || s.isEmpty()) {
			return null;
		}

		final Object first = s.getFirstElement();
		final AdapterConnection ac = unwrapAdapterConnection(first);
		if (ac == null) {
			return null; // not our selection
		}

		// Determine adapter type name from connection (source or destination)
		final String adapterTypeName = getAdapterTypeName(ac);
		if (adapterTypeName == null || adapterTypeName.isBlank()) {
			throw new ExecutionException("Cannot determine adapter type from selected connection.");
		}
		final String proxyTypeName = adapterTypeName + "_Proxy";

		// Build the model edit
		final FBNetwork net = (FBNetwork) ac.eContainer();
		final URI netURI = EcoreUtil.getURI(net);
		final URI connURI = EcoreUtil.getURI(ac);

		// Your existing edit that does the actual insertion + reconnect
		final InsertAdapterProxyOnConnectionEdit edit = new InsertAdapterProxyOnConnectionEdit(connURI);

		// Wrap it with the factory (this is what you asked for)
		final Change change = ModelEditChange.fromModelEdits("Replace Connection with Adapter Proxy", List.of(edit));

		// === LTK path: Refactoring -> CreateChangeOperation -> PerformChangeOperation
		final Refactoring refactoring = new Refactoring() {
			@Override
			public String getName() {
				return "Insert Adapter Proxy";
			}

			@Override
			public RefactoringStatus checkInitialConditions(final org.eclipse.core.runtime.IProgressMonitor pm) {
				// keep lightweight; ModelEditChange will validate during execution
				return new RefactoringStatus();
			}

			@Override
			public RefactoringStatus checkFinalConditions(final org.eclipse.core.runtime.IProgressMonitor pm) {
				return new RefactoringStatus();
			}

			@Override
			public Change createChange(final org.eclipse.core.runtime.IProgressMonitor pm) {
				// Build the ModelEdit here (no “out of nothing”)
				final InsertAdapterProxyOnConnectionEdit edit = new InsertAdapterProxyOnConnectionEdit(connURI);

				// Wrap the single edit into a Change using the factory you require
				return ModelEditChange.fromModelEdits("Replace Connection with Adapter Proxy", List.of(edit));
			}
		};

		final CreateChangeOperation create = new CreateChangeOperation(refactoring);
		final PerformChangeOperation perform = new PerformChangeOperation(create);
		// perform.setUndoManager("Insert Adapter Proxy", 1);

		try {
			ResourcesPlugin.getWorkspace()
					.run(monitor -> perform.run(monitor != null ? monitor : new NullProgressMonitor()), null);
		} catch (final Exception e) {
			throw new ExecutionException("Failed to perform refactoring", e);
		}
		return null;
	}

	private static AdapterConnection unwrapAdapterConnection(final Object element) {
		if (element instanceof final ConnectionEditPart cep && cep.getModel() instanceof final AdapterConnection ac) {
			return ac;
		}
		if (element instanceof AdapterConnection) {
			return (AdapterConnection) element;
		}
		return null;
	}

	private static String getAdapterTypeName(final AdapterConnection ac) {
		final IInterfaceElement src = ac.getSource();
		final IInterfaceElement dst = ac.getDestination();
		final AdapterType at = (src != null && src.getType() instanceof AdapterType) ? (AdapterType) src.getType()
				: (dst != null && dst.getType() instanceof AdapterType ? (AdapterType) dst.getType() : null);
		return (at != null) ? at.getName() : null;
	}
}
