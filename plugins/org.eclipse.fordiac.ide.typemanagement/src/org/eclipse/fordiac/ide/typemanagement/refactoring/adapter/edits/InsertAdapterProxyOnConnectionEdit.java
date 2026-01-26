package org.eclipse.fordiac.ide.typemanagement.refactoring.adapter.edits;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fordiac.ide.model.NameRepository;
import org.eclipse.fordiac.ide.model.commands.create.AbstractConnectionCreateCommand;
import org.eclipse.fordiac.ide.model.commands.create.FBCreateCommand;
import org.eclipse.fordiac.ide.model.commands.delete.DeleteConnectionCommand;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterConnection;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterDeclaration;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterType;
import org.eclipse.fordiac.ide.model.libraryElement.BlockFBNetworkElement;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetwork;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetworkElement;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;
import org.eclipse.fordiac.ide.model.libraryElement.LibraryElement;
import org.eclipse.fordiac.ide.model.libraryElement.LibraryElementFactory;
import org.eclipse.fordiac.ide.model.typelibrary.FBTypeEntry;
import org.eclipse.fordiac.ide.model.typelibrary.TypeEntry;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibrary;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibraryManager;
import org.eclipse.fordiac.ide.typemanagement.refactoring.ModelEdit;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class InsertAdapterProxyOnConnectionEdit extends ModelEdit<AdapterConnection> {

	// We resolve these again on execution against the resource loaded by the
	// framework
	private final URI connectionURI;

	public InsertAdapterProxyOnConnectionEdit(final URI connectionURI) {
		super("Insert Adapter Proxy", connectionURI, AdapterConnection.class);
		this.connectionURI = connectionURI;
	}

	// ---------- ModelEdit abstract methods ----------

	@Override
	public void initializeValidationData(final AdapterConnection element, final IProgressMonitor pm) {
		// nothing special to precompute
	}

	@Override
	public RefactoringStatus isValid(final AdapterConnection element, final IProgressMonitor pm) throws CoreException {
		final RefactoringStatus rs = new RefactoringStatus();

		final AdapterConnection ac = element;
		if (ac == null) {
			rs.addFatalError("Selected adapter connection no longer exists.");
			return rs;
		}

		final AdapterType at = inferAdapterType(ac);
		if (at == null || at.getName() == null || at.getName().isBlank()) {
			rs.addFatalError("Cannot determine adapter type from the selected connection.");
			return rs;
		}

		final FBTypeEntry proxy = findProxyEntry(ac, at.getName() + "_Proxy");
		if (proxy == null) {
			rs.addWarning("Proxy type '" + at.getName() + "_Proxy.fbt' not found in project. "
					+ "The operation will fail at execution.");
		}
		System.out.println(rs);
		return rs;
	}

	@Override
	protected Command createCommand(final AdapterConnection net) {
		final AdapterConnection original = net;
		if (original == null) {
			return null;
		}

		final AdapterDeclaration src = safeCastAdapterDecl(original.getSource());
		final AdapterDeclaration dst = safeCastAdapterDecl(original.getDestination());
		if (src == null || dst == null) {
			return null;
		}

		final AdapterType at = inferAdapterType(original);
		if (at == null || at.getName() == null || at.getName().isBlank()) {
			return null;
		}

		final FBTypeEntry proxyEntry = findProxyEntry(original, at.getName() + "_Proxy");
		if (proxyEntry == null) {
			return null;
		}

		// Position in the middle between endpoint FBs
		final FBNetworkElement srcFB = src.getBlockFBNetworkElement();
		final FBNetworkElement dstFB = dst.getBlockFBNetworkElement();
		final int midX = avg(xOf(srcFB), xOf(dstFB));
		final int midY = avg(yOf(srcFB), yOf(dstFB));

		// Create instance
		final String baseName = proxyEntry.getTypeName();
		final String instName = uniqueInstanceName(srcFB, baseName);

		final org.eclipse.fordiac.ide.model.libraryElement.Position position = LibraryElementFactory.eINSTANCE
				.createPosition();
		position.setX(midX);
		position.setY(midY);

		final FBCreateCommand createProxy = new FBCreateCommand(proxyEntry, srcFB.getFbNetwork(), position);

		// Wire & replace original connection after creation
		final CompoundCommand compound = new CompoundCommand("Insert Adapter Proxy");
		compound.add(createProxy);
		compound.add(new WireAroundProxyAndRemoveOriginal(original, createProxy));
		return compound;
	}

	// ---------- helper command (executes after FBCreateCommand) ----------

	private static final class WireAroundProxyAndRemoveOriginal extends Command {
		private final AdapterConnection original;
		private final FBCreateCommand createdCmd;
		private List<Command> executed = new ArrayList<>();

		WireAroundProxyAndRemoveOriginal(final AdapterConnection original, final FBCreateCommand createdCmd) {

			this.original = original;
			this.createdCmd = createdCmd;
		}

		@Override
		public void execute() {
			executed = new ArrayList<>();

			final FBNetwork net = original.getFBNetwork();
			final IInterfaceElement src = original.getSource(); // e.g., sensor.PLUG1
			final IInterfaceElement dst = original.getDestination(); // e.g., vision.SOCKET1
			final boolean srcIsPlug = (src instanceof final AdapterDeclaration ad) && !ad.isIsInput();

			// 0) delete original first -> frees the plug/socket for the two new connections
			final DeleteConnectionCommand del = new DeleteConnectionCommand(original);
			if (del.canExecute()) {
				del.execute();
				executed.add(del);
			}

			// 1) newly created proxy instance and its adapter pins
			final BlockFBNetworkElement proxy = createdCmd.getFB();
			final AdapterDeclaration proxySock = proxy.getInterface().getSockets().stream()
					.filter(a -> a.isIsInput() && "SOCKET1".equals(a.getName())).findFirst()
					.orElseThrow(() -> new IllegalStateException("Proxy SOCKET1 not found"));
			final AdapterDeclaration proxyPlug = proxy.getInterface().getPlugs().stream()
					.filter(a -> !a.isIsInput() && "PLUG1".equals(a.getName())).findFirst()
					.orElseThrow(() -> new IllegalStateException("Proxy PLUG1 not found"));

			// Determine which end was the plug in the original connection
			final IInterfaceElement origPlug = srcIsPlug ? src : dst;
			final IInterfaceElement origSocket = srcIsPlug ? dst : src;

			// 2) sensor.PLUG1 -> movePos_Proxy.SOCKET1
			addAndExecuteConn(net, origPlug, proxySock);

			// 3) movePos_Proxy.PLUG1 -> vision.SOCKET1
			addAndExecuteConn(net, proxyPlug, origSocket);
		}

//		@Override
//		public void execute() {
//			final BlockFBNetworkElement proxy = createdCmd.getFB();
//			if (proxy == null || proxy.getInterface() == null) {
//				return;
//			}
//
//			final AdapterDeclaration proxySock = proxy.getInterface().getSockets().stream()
//					.filter(a -> "SOCKET1".equals(a.getName())).findFirst().orElse(null);
//			final AdapterDeclaration proxyPlug = proxy.getInterface().getPlugs().stream()
//					.filter(a -> "PLUG1".equals(a.getName())).findFirst().orElse(null);
//			if (proxySock == null || proxyPlug == null) {
//				return;
//			}
//
//			final AdapterDeclaration src = safeCastAdapterDecl(original.getSource());
//			final AdapterDeclaration dst = safeCastAdapterDecl(original.getDestination());
//			if (src == null || dst == null) {
//				return;
//			}
//
//			final boolean srcIsPlug = !src.isIsInput(); // plug has isInput=false
//			final FBNetwork net = proxy.getFbNetwork();
//			// Build new connections around the proxy
//			if (srcIsPlug) {
//				addAndExecuteConn(net, src, proxySock);
//				addAndExecuteConn(net, proxyPlug, dst);
//			} else {
//				addAndExecuteConn(net, src, proxyPlug);
//				addAndExecuteConn(net, proxySock, dst);
//			}
//
//			// Remove original connection at the end
//			final var del = new DeleteConnectionCommand(original);
//			if (del.canExecute()) {
//				del.execute();
//				executed.add(del);
//			}
//		}

		@Override
		public void undo() {
			// undo in reverse order
			for (int i = executed.size() - 1; i >= 0; --i) {
				executed.get(i).undo();
			}
			// FBCreateCommand is undone by the outer CompoundCommand
		}

		private void addAndExecuteConn(final FBNetwork net, final IInterfaceElement src, final IInterfaceElement dst) {
			if (src == null || dst == null) {
				return;
			}

			Command c = AbstractConnectionCreateCommand.createCommand(net, src, dst);
			if (c instanceof final AbstractConnectionCreateCommand acc) {
				acc.setSource(src);
				acc.setDestination(dst);
			}
			if (c != null && c.canExecute()) {
				c.execute();
				executed.add(c);
				return;
			}
			// fallback: try reverse (older builds differ in expectations)
			c = AbstractConnectionCreateCommand.createCommand(net, dst, src);
			if (c instanceof final AbstractConnectionCreateCommand acc2) {
				acc2.setSource(dst);
				acc2.setDestination(src);
			}
			if (c != null && c.canExecute()) {
				c.execute();
				executed.add(c);
			}
		}
	}

	// ---------- resolution / lookup helpers ----------

	private AdapterConnection resolveConnection(final FBNetwork element) {
		if (element == null || element.eResource() == null) {
			return null;
		}
		final Resource res = element.eResource();
		// resolve by fragment against the same resource
		final String frag = connectionURI.fragment();
		if (frag == null || frag.isBlank()) {
			return null;
		}
		final Object eo = res.getEObject(frag);
		return (eo instanceof final AdapterConnection a) ? a : null;
	}

	private static AdapterDeclaration safeCastAdapterDecl(final IInterfaceElement ie) {
		return (ie instanceof final AdapterDeclaration a) ? a : null;
	}

	private static AdapterType inferAdapterType(final AdapterConnection ac) {
		if (ac.getSource() instanceof final AdapterDeclaration a && a.getType() != null) {
			return a.getType();
		}
		if (ac.getDestination() instanceof final AdapterDeclaration a && a.getType() != null) {
			return a.getType();
		}
		return null;
	}

	private static FBTypeEntry findProxyEntry(final AdapterConnection ac, final String proxyTypeName) {
		final EObject rootContainer = EcoreUtil.getRootContainer(ac);

		final IProject project = ((LibraryElement) rootContainer).getTypeEntry().getFile().getProject();

		final TypeLibrary tl = TypeLibraryManager.INSTANCE.getTypeLibrary(project);

		// 1) preferred: by name (recent 4diac)
		try {
			final TypeEntry te = tl.getFBTypeEntry(proxyTypeName);
			if (te instanceof final FBTypeEntry fb) {
				return fb;
			}
		} catch (final Throwable ignore) {
			/* older API */ }

		// 2) fallback: search any <name>.fbt in project and register
		final FBTypeEntry[] out = new FBTypeEntry[1];
		try {
			project.accept(r -> {
				if (r instanceof final IFile file && "fbt".equalsIgnoreCase(file.getFileExtension())
						&& file.getName().equals(proxyTypeName + ".fbt")) {
					final TypeEntry te2 = tl.createTypeEntry(file);
					if (te2 instanceof final FBTypeEntry fb) {
						out[0] = fb;
						return false;
					}
				}
				return true;
			});
		} catch (final Exception e) {
			return null;
		}
		return out[0];
	}

	// ---------- small utils ----------

	private static int xOf(final FBNetworkElement e) {
		return (int) ((e != null && e.getPosition() != null) ? e.getPosition().getX() : 0);
	}

	private static int yOf(final FBNetworkElement e) {
		return (int) ((e != null && e.getPosition() != null) ? e.getPosition().getY() : 0);
	}

	private static int avg(final int a, final int b) {
		return a + ((b - a) / 2);
	}

	private static String uniqueInstanceName(final FBNetworkElement srcFB, final String base) {
		return NameRepository.createUniqueName(srcFB, base);

	}
}
