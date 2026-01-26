package org.eclipse.fordiac.ide.typemanagement.refactoring.adapter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fordiac.ide.model.data.EventType;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterType;
import org.eclipse.fordiac.ide.model.libraryElement.Event;
import org.eclipse.fordiac.ide.model.libraryElement.InterfaceList;
import org.eclipse.fordiac.ide.model.libraryElement.LibraryElement;
import org.eclipse.fordiac.ide.model.libraryElement.LibraryElementFactory;
import org.eclipse.fordiac.ide.model.libraryElement.VarDeclaration;
import org.eclipse.fordiac.ide.model.libraryElement.With;
import org.eclipse.fordiac.ide.model.typelibrary.EventTypeLibrary;
import org.eclipse.fordiac.ide.typemanagement.preferences.TypeManagementPreferencesHelper;

/**
 * Build an IEC 61499 AdapterType from two FB interfaces. The adapter interface
 * is declared from the provider/plug perspective.
 *
 * providerIF: the FB interface on the "left" (providing side) acceptorIF: the
 * FB interface on the "right" (accepting side)
 *
 * For every matching name: - provider EventOutput & acceptor EventInput ->
 * AdapterType EventOutput - acceptor EventOutput & provider EventInput ->
 * AdapterType EventInput - provider VarOutput & acceptor VarInput ->
 * AdapterType VarOutput - acceptor VarOutput & provider VarInput -> AdapterType
 * VarInput
 */
public final class AdapterTypeBuilder {
	private AdapterTypeBuilder() {
	}

	public static AdapterType buildAdapterType(final String name, final InterfaceList providerIF,
			final InterfaceList acceptorIF, final Set<String> leftPinsToRemove, final Set<String> rightPinsToRemove) {
		Objects.requireNonNull(name, "name");
		Objects.requireNonNull(providerIF, "providerIF");
		Objects.requireNonNull(acceptorIF, "acceptorIF");

		final var f = LibraryElementFactory.eINSTANCE;
		final var at = f.createAdapterType();
		at.setName(name);

		// Minimal header required by importer/exporter
		at.setIdentification(f.createIdentification());
		at.getIdentification().setStandard("61499-1");

		final EObject rootContainer = EcoreUtil.getRootContainer(acceptorIF);
		IProject project = null;
		if (rootContainer instanceof final LibraryElement le) {
			project = le.getTypeEntry().getFile().getProject();
		}

		TypeManagementPreferencesHelper.setupVersionInfo(at, project);

		final var interfaceList = f.createInterfaceList();
		at.setInterfaceList(interfaceList);

		// Build ONLY from selection (paired by insertion order)
		final List<String> left = new ArrayList<>(leftPinsToRemove != null ? leftPinsToRemove : Set.of());
		final List<String> right = new ArrayList<>(rightPinsToRemove != null ? rightPinsToRemove : Set.of());
		final int n = Math.min(left.size(), right.size());

		final Set<String> addedEvents = new HashSet<>();
		final Set<String> addedVars = new HashSet<>();

		for (int i = 0; i < n; i++) {
			final String srcName = left.get(i); // e.g. PosReady, X, Y, Z
			final String dstName = right.get(i); // e.g. Check, X, Y, Z

			// EVENT: provider EventOutput -> acceptor EventInput
			final Event srcEO = findEvent(providerIF.getEventOutputs(), srcName);
			final Event dstEI = findEvent(acceptorIF.getEventInputs(), dstName);
			if (srcEO != null && dstEI != null) {
				if (addedEvents.add(srcName)) {
					final var e = f.createEvent();
					e.setName(srcName);
					e.setComment(""); // avoid nulls

					final EventType type = EventTypeLibrary.getInstance().getType("Event");
					e.setType(type);
					interfaceList.getEventOutputs().add(e);
				}
				continue;
			}

			// DATA: provider OutputVar -> acceptor InputVar
			final VarDeclaration srcVO = findVar(providerIF.getOutputVars(), srcName);
			final VarDeclaration dstVI = findVar(acceptorIF.getInputVars(), dstName);
			if (srcVO != null && dstVI != null) {
				if (addedVars.add(srcName)) {
					final var v = f.createVarDeclaration();
					v.setName(srcName);
					v.setType(srcVO.getType()); // source type wins
					v.setComment("");
					interfaceList.getOutputVars().add(v);
				}
				continue;
			}

			// If you later support reverse direction, add the other two cases here.
			// For now: ignore anything that doesn't match these two patterns.
		}

		// Add WITHs:
		// - each output event WITH all output vars
		// - each input event WITH all input vars
		for (final Event e : interfaceList.getEventOutputs()) {
			e.getWith().clear();
			for (final VarDeclaration v : interfaceList.getOutputVars()) {
				final With w = f.createWith();
				w.setVariables(v);
				e.getWith().add(w);
			}
		}
		for (final Event e : interfaceList.getEventInputs()) {
			e.getWith().clear();
			for (final VarDeclaration v : interfaceList.getInputVars()) {
				final With w = f.createWith();
				w.setVariables(v);
				e.getWith().add(w);
			}
		}

		// Optional minimal service (keeps some editors happy)
		final var svc = f.createService();
		svc.setLeftInterface(f.createServiceInterface());
		svc.setRightInterface(f.createServiceInterface());
		at.setService(svc);

		return at;
	}

	private static Event findEvent(final List<Event> events, final String name) {
		if (name == null) {
			return null;
		}
		for (final Event e : events) {
			if (name.equals(e.getName())) {
				return e;
			}
		}
		return null;
	}

	private static VarDeclaration findVar(final List<VarDeclaration> vars, final String name) {
		if (name == null) {
			return null;
		}
		for (final VarDeclaration v : vars) {
			if (name.equals(v.getName())) {
				return v;
			}
		}
		return null;
	}
}
