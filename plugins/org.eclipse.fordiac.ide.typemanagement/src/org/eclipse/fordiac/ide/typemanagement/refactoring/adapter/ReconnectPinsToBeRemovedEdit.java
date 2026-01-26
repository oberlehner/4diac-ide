package org.eclipse.fordiac.ide.typemanagement.refactoring.adapter;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.emf.common.util.EList;
import org.eclipse.emf.common.util.URI;
import org.eclipse.fordiac.ide.model.commands.change.ReconnectDataConnectionCommand;
import org.eclipse.fordiac.ide.model.commands.change.ReconnectEventConnectionCommand;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterFB;
import org.eclipse.fordiac.ide.model.libraryElement.CompositeFBType;
import org.eclipse.fordiac.ide.model.libraryElement.Connection;
import org.eclipse.fordiac.ide.model.libraryElement.DataConnection;
import org.eclipse.fordiac.ide.model.libraryElement.EventConnection;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetwork;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetworkElement;
import org.eclipse.fordiac.ide.model.libraryElement.FBType;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;
import org.eclipse.fordiac.ide.model.libraryElement.InterfaceList;
import org.eclipse.fordiac.ide.typemanagement.refactoring.ModelEdit;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class ReconnectPinsToBeRemovedEdit extends ModelEdit<FBType> {

	private final Set<String> pinsToRemove;
	private final String adapterTypeName;
	private final Map<String, String> oldToAdapterPinName;

	public ReconnectPinsToBeRemovedEdit(final URI typeURI, final String fbTypeName, final Set<String> pinsToRemove,
			final String adapterTypeName) {
		this(typeURI, fbTypeName, pinsToRemove, adapterTypeName, null);
	}

	public ReconnectPinsToBeRemovedEdit(final URI typeURI, final String fbTypeName, final Set<String> pinsToRemove,
			final String adapterTypeName, final Map<String, String> oldToAdapterPinName) {

		super("reconnect", typeURI, FBType.class);
		this.pinsToRemove = (pinsToRemove != null) ? Collections.unmodifiableSet(pinsToRemove) : Set.of();
		this.adapterTypeName = Objects.requireNonNull(adapterTypeName, "adapterTypeName");

		if (oldToAdapterPinName == null || oldToAdapterPinName.isEmpty()) {
			this.oldToAdapterPinName = Map.of();
		} else {
			this.oldToAdapterPinName = Collections.unmodifiableMap(new LinkedHashMap<>(oldToAdapterPinName));
		}
	}

	@Override
	protected Command createCommand(final FBType fbType) {
		if (!(fbType instanceof final CompositeFBType composite)) {
			// nothing to do for basic / simple / service FBs
			return null;
		}
		// IMPORTANT: do NOT resolve adapter declaration / adapter FB here.
		// At this point, CreateAdapterDeclarationEdit has not executed yet.
		return new ReconnectPinsCommand(composite);
	}

	private final class ReconnectPinsCommand extends Command {

		private final CompositeFBType type;
		private final CompoundCommand cmds = new CompoundCommand();

		ReconnectPinsCommand(final CompositeFBType type) {
			super("reconnect");
			this.type = type;
		}

		@Override
		public boolean canExecute() {
			return true; // validate in execute()
		}

		@Override
		public void execute() {
			final InterfaceList iface = type.getInterfaceList();
			if (iface == null) {
				return;
			}

			final FBNetwork fbNetwork = type.getFBNetwork();
			if (fbNetwork == null) {
				return;
			}

			final AdapterFB adapterFB = findAdapterFB(fbNetwork, adapterTypeName);
			if (adapterFB == null) {
				return;
			}

			for (final String oldPinName : pinsToRemove) {
				if (oldPinName == null || oldPinName.isBlank()) {
					continue;
				}
				final ArrayList<String> l = new ArrayList<>();
				l.add(oldPinName);

				final IInterfaceElement oldIE = iface.getInterfaceElement(l);
				if (oldIE == null) {
					continue;
				}

				// Mapping für Fälle wie: CHECK -> PosReady
				final String adapterPinName = oldToAdapterPinName.getOrDefault(oldPinName, oldPinName);

				final ArrayList<String> f = new ArrayList<>();
				f.add(adapterPinName);
				final IInterfaceElement adapterPin = adapterFB.getInterface().getInterfaceElement(f);
				if (adapterPin == null) {
					continue;
				}

				// 4diac Pattern (ReconnectPinChange): Input -> getInputConnections(), Output ->
				// getOutputConnections()
				final EList<Connection> connections = getConnections(oldIE);
				if (connections.isEmpty()) {
					continue;
				}

				for (final Connection c : connections) {
					// 4diac Pattern: boolean invertiert übergeben (!isIsInput())
					if (c instanceof final DataConnection dc) {
						cmds.add(new ReconnectDataConnectionCommand(dc, oldIE.isIsInput(), adapterPin, fbNetwork));
					} else if (c instanceof final EventConnection ec) {
						cmds.add(new ReconnectEventConnectionCommand(ec, oldIE.isIsInput(), adapterPin, fbNetwork));
					}
				}
			}

			if (!cmds.isEmpty()) {
				cmds.execute();
			}
		}

		@Override
		public void undo() {
			cmds.undo();
		}

		@Override
		public void redo() {
			cmds.redo();
		}
	}

	private static EList<Connection> getConnections(final IInterfaceElement iE) {
		// same as in typemanagement/refactoring/ReconnectPinChange
		return !iE.isIsInput() ? iE.getInputConnections() : iE.getOutputConnections();
	}

	private static AdapterFB findAdapterFB(final FBNetwork fbNetwork, final String adapterTypeName) {
		for (final FBNetworkElement element : fbNetwork.getNetworkElements()) {
			if ((element instanceof final AdapterFB adapterFB) && (adapterFB.getAdapterDecl() != null
					&& adapterTypeName.equals(adapterFB.getAdapterDecl().getTypeName()))) {
				return adapterFB;
			}
		}
		return null;
	}

	@Override
	public void initializeValidationData(final FBType element, final IProgressMonitor pm) {
		// no-op
	}

	@Override
	public RefactoringStatus isValid(final FBType element, final IProgressMonitor pm)
			throws CoreException, OperationCanceledException {
		return new RefactoringStatus();
	}
}
