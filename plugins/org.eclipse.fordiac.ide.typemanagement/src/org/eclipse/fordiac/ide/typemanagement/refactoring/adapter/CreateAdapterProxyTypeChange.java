package org.eclipse.fordiac.ide.typemanagement.refactoring.adapter;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.fordiac.ide.model.commands.create.AbstractConnectionCreateCommand;
import org.eclipse.fordiac.ide.model.commands.create.CreateInterfaceElementCommand;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterDeclaration;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterFB;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterType;
import org.eclipse.fordiac.ide.model.libraryElement.CompositeFBType;
import org.eclipse.fordiac.ide.model.libraryElement.Event;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetwork;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;
import org.eclipse.fordiac.ide.model.libraryElement.InterfaceList;
import org.eclipse.fordiac.ide.model.libraryElement.LibraryElementFactory;
import org.eclipse.fordiac.ide.model.libraryElement.VarDeclaration;
import org.eclipse.fordiac.ide.model.typelibrary.AdapterTypeEntry;
import org.eclipse.fordiac.ide.model.typelibrary.EventTypeLibrary;
import org.eclipse.fordiac.ide.model.typelibrary.FBTypeEntry;
import org.eclipse.fordiac.ide.model.typelibrary.TypeEntry;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibrary;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibraryManager;
import org.eclipse.gef.commands.CompoundCommand;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

public class CreateAdapterProxyTypeChange extends Change {

	private final IFolder libFolder;
	private final String typeName; // e.g. "movePos_Proxy"
	private final String adapterTypeName;

	private IFile createdFile;

	public CreateAdapterProxyTypeChange(final IFolder libFolder, final String typeName, final String adapterTypeName) {
		this.libFolder = libFolder;
		this.typeName = typeName;
		this.adapterTypeName = adapterTypeName;
	}

	@Override
	public String getName() {
		return "Create Adapter Proxy Type: " + typeName;
	}

	@Override
	public void initializeValidationData(final IProgressMonitor pm) {
	}

	@Override
	public RefactoringStatus isValid(final IProgressMonitor pm) {
		return new RefactoringStatus();
	}

	@Override
	public Change perform(final IProgressMonitor pm) throws CoreException {
		// 1) resolve target file in the same (source) folder as the .adp
		if (!libFolder.exists()) {
			libFolder.create(true, true, pm);
		}
		createdFile = libFolder.getFile(typeName + ".fbt");

		// 2) get TypeLibrary + create a TypeEntry for that file (this registers the
		// entry)
		final TypeLibrary tl = TypeLibraryManager.INSTANCE.getTypeLibrary(libFolder.getProject());
		final TypeEntry entry = tl.createTypeEntry(createdFile);
		if (!(entry instanceof final FBTypeEntry fbEntry)) {
			throw new CoreException(new Status(IStatus.ERROR, "org.eclipse.fordiac.ide.typemanagement",
					"Failed to create FBTypeEntry for " + createdFile.getFullPath()));
		}

		// 3) build the Composite FB type in-memory (proxy) and save via the entry

		final AdapterTypeEntry adapterTypeEntry = tl.getAdapterTypeEntry(adapterTypeName);

		final CompositeFBType proxy = buildProxy(adapterTypeEntry.getType(), typeName);
		fbEntry.save(proxy, pm); // <-- correct way: TypeEntry.save handles exporter & file I/O

		return null; // no undo change
	}

	@Override
	public Object getModifiedElement() {
		return createdFile;
	}

	private static CompositeFBType buildProxy(final AdapterType at, final String typeName) {
		final var f = LibraryElementFactory.eINSTANCE;

		final CompositeFBType cfb = f.createCompositeFBType();
		cfb.setName(typeName);

		// Minimal, valid header (Importer requires VersionInfo)
		cfb.setIdentification(f.createIdentification());
		cfb.getIdentification().setStandard("61499-2");
		{
			final var vi = f.createVersionInfo();
			vi.setOrganization("Generated");
			vi.setVersion("1.0");
			final String author = System.getProperty("user.name");
			vi.setAuthor((author != null && !author.isBlank()) ? author : "Auto");
			vi.setDate(java.time.LocalDate.now().toString());
			vi.setRemarks("Created by Insert Adapter Proxy");
			cfb.getVersionInfo().add(vi);
		}
		{ // keep service page happy
			final var svc = f.createService();
			svc.setLeftInterface(f.createServiceInterface());
			svc.setRightInterface(f.createServiceInterface());
			final var seq = f.createServiceSequence();
			seq.setName("SEQ");
			svc.getServiceSequence().add(seq);
			cfb.setService(svc);
		}

		// Interface + Network
		cfb.setInterfaceList(f.createInterfaceList());
		final InterfaceList il = cfb.getInterfaceList();
		final FBNetwork net = f.createFBNetwork();
		cfb.setFBNetwork(net);

		// Create SOCKET1 / PLUG1 (also creates their AdapterFBs)
		final var cmdSock = new CreateInterfaceElementCommand(at, "SOCKET1", il, true, -1);
		cmdSock.execute();
		final AdapterDeclaration socketDecl = (AdapterDeclaration) cmdSock.getCreatedElement();

		final var cmdPlug = new CreateInterfaceElementCommand(at, "PLUG1", il, false, -1);
		cmdPlug.execute();
		final AdapterDeclaration plugDecl = (AdapterDeclaration) cmdPlug.getCreatedElement();

		// Make sure the internal AdapterFBs are REAL network elements
		final AdapterFB sockFB = socketDecl.getAdapterFB();
		final AdapterFB plugFB = plugDecl.getAdapterFB();
		if (sockFB != null && sockFB.eContainer() == null) {
			net.getNetworkElements().add(sockFB);
		}
		if (plugFB != null && plugFB.eContainer() == null) {
			net.getNetworkElements().add(plugFB);
		}

		// Positions (nice defaults)
		if (sockFB != null) {
			final var p = f.createPosition();
			p.setX(40);
			p.setY(40);
			sockFB.setPosition(p);
		}
		if (plugFB != null) {
			final var p = f.createPosition();
			p.setX(180);
			p.setY(40);
			plugFB.setPosition(p);
		}

		// Mirror SOCKET interface on the CFB interface (names + directions + WITHs)
		final InterfaceList ai = at.getInterfaceList();
		if (ai != null) {
			// data
			for (final VarDeclaration v : ai.getInputVars()) {
				new CreateInterfaceElementCommand(v.getType(), v.getName(), il, true, -1).execute();
			}
			for (final VarDeclaration v : ai.getOutputVars()) {
				new CreateInterfaceElementCommand(v.getType(), v.getName(), il, false, -1).execute();
			}

			// events (+ WITHs mapped to mirrored vars)
			final var findInVar = (java.util.function.Function<String, VarDeclaration>) name -> il.getInputVars()
					.stream().filter(x -> x.getName().equals(name)).findFirst().orElse(null);
			final var findOutVar = (java.util.function.Function<String, VarDeclaration>) name -> il.getOutputVars()
					.stream().filter(x -> x.getName().equals(name)).findFirst().orElse(null);

			for (final Event e : ai.getEventInputs()) {
				final var et = EventTypeLibrary.getInstance()
						.getType((e.getTypeName() != null && !e.getTypeName().isBlank()) ? e.getTypeName() : "Event");
				final var ce = new CreateInterfaceElementCommand(et, e.getName(), il, true, -1);
				ce.execute();
				final Event cfbEI = (Event) ce.getCreatedElement();
				for (final var w : e.getWith()) {
					if (w.getVariables() == null) {
						continue;
					}
					final var t = findInVar.apply(w.getVariables().getName());
					if (t != null) {
						final var nw = f.createWith();
						nw.setVariables(t);
						cfbEI.getWith().add(nw);
					}
				}
			}
			for (final Event e : ai.getEventOutputs()) {
				final var et = EventTypeLibrary.getInstance()
						.getType((e.getTypeName() != null && !e.getTypeName().isBlank()) ? e.getTypeName() : "Event");
				final var ce = new CreateInterfaceElementCommand(et, e.getName(), il, false, -1);
				ce.execute();
				final Event cfbEO = (Event) ce.getCreatedElement();
				for (final var w : e.getWith()) {
					if (w.getVariables() == null) {
						continue;
					}
					final var t = findOutVar.apply(w.getVariables().getName());
					if (t != null) {
						final var nw = f.createWith();
						nw.setVariables(t);
						cfbEO.getWith().add(nw);
					}
				}
			}
		}

//		// exporter safety
//		if (socketDecl.getComment() == null) {
//			socketDecl.setComment("");
//		}
//		if (plugDecl.getComment() == null) {
//			plugDecl.setComment("");
//		}
//		il.getEventInputs().forEach(e -> {
//			if (e.getComment() == null) {
//				e.setComment("");
//			}
//		});
//		il.getEventOutputs().forEach(e -> {
//			if (e.getComment() == null) {
//				e.setComment("");
//			}
//		});
//		il.getInputVars().forEach(v -> {
//			if (v.getComment() == null) {
//				v.setComment("");
//			}
//		});
//		il.getOutputVars().forEach(v -> {
//			if (v.getComment() == null) {
//				v.setComment("");
//			}
//		});

		// === Helper to create + force-init connection commands
		final java.util.function.BiConsumer<IInterfaceElement, IInterfaceElement> addConn = (src, dst) -> {
			var cmd = AbstractConnectionCreateCommand.createCommand(net, src, dst);
			if (cmd instanceof final AbstractConnectionCreateCommand acc) {
				acc.setSource(src);
				acc.setDestination(dst);
			}
			if (cmd != null && cmd.canExecute()) {
				// collect for single undo step
				// (CompoundCommand#add is safe even if it's already a CompoundCommand)
				// no-op if cannot execute
			} else {
				// try reverse once (some versions expect reverse order for OUT->OUT exposure)
				cmd = AbstractConnectionCreateCommand.createCommand(net, dst, src);
				if (cmd instanceof final AbstractConnectionCreateCommand acc2) {
					acc2.setSource(dst);
					acc2.setDestination(src);
				}
			}
		};

		final var cc = new CompoundCommand("Adapter Proxy Wiring");

		// Wrap addConn so we can actually add to cc after canExecute
		final java.util.function.BiConsumer<IInterfaceElement, IInterfaceElement> addToCC = (src, dst) -> {
			var cmd = AbstractConnectionCreateCommand.createCommand(net, src, dst);
			if (cmd instanceof final AbstractConnectionCreateCommand acc) {
				acc.setSource(src);
				acc.setDestination(dst);
			}
			if (cmd != null && cmd.canExecute()) {
				cc.add(cmd);
			} else {
				// fallback: reverse order once
				cmd = AbstractConnectionCreateCommand.createCommand(net, dst, src);
				if (cmd instanceof final AbstractConnectionCreateCommand acc2) {
					acc2.setSource(dst);
					acc2.setDestination(src);
				}
				if (cmd != null && cmd.canExecute()) {
					cc.add(cmd);
				}
			}
		};

		// 0) Adapter pass-through (declaration-to-declaration)
//		addToCC.accept(socketDecl, plugDecl);

		if (sockFB != null && plugFB != null) {
			final InterfaceList sockIF = sockFB.getInterface();
			final InterfaceList plugIF = plugFB.getInterface();

			// A) EVENTS: SOCKET OUT -> PLUG IN and SOCKET OUT -> CFB EO (same name)
			for (final Event se : sockIF.getEventOutputs()) {
				final String n = se.getName();

				final Event plugIn = plugIF.getEventInputs().stream().filter(e -> n.equals(e.getName())).findFirst()
						.orElse(null);
				if (plugIn != null) {
					addToCC.accept(se, plugIn);
				}

				final Event cfbEO = il.getEventOutputs().stream().filter(e -> n.equals(e.getName())).findFirst()
						.orElse(null);
				if (cfbEO != null) {
					addToCC.accept(se, cfbEO);
				}
			}

			// B) DATA: SOCKET OUT -> PLUG IN and SOCKET OUT -> CFB OV (same name)
			for (final VarDeclaration sv : sockIF.getOutputVars()) {
				final String n = sv.getName();

				final VarDeclaration plugIn = plugIF.getInputVars().stream().filter(v -> n.equals(v.getName()))
						.findFirst().orElse(null);
				if (plugIn != null) {
					addToCC.accept(sv, plugIn);
				}

				final VarDeclaration cfbOV = il.getOutputVars().stream().filter(v -> n.equals(v.getName())).findFirst()
						.orElse(null);
				if (cfbOV != null) {
					addToCC.accept(sv, cfbOV);
				}
			}
		}

		if (cc.canExecute()) {
			cc.execute();
		}
		return cfb;
	}

}
