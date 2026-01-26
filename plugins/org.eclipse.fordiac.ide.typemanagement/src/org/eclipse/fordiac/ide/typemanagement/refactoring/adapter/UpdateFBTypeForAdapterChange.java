package org.eclipse.fordiac.ide.typemanagement.refactoring.adapter;

import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.fordiac.ide.model.commands.create.CreateInterfaceElementCommand;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterDeclaration;
import org.eclipse.fordiac.ide.model.libraryElement.Event;
import org.eclipse.fordiac.ide.model.libraryElement.FBType;
import org.eclipse.fordiac.ide.model.libraryElement.InterfaceList;
import org.eclipse.fordiac.ide.model.libraryElement.LibraryElementFactory;
import org.eclipse.fordiac.ide.model.libraryElement.VarDeclaration;
import org.eclipse.fordiac.ide.model.typelibrary.AdapterTypeEntry;
import org.eclipse.fordiac.ide.model.typelibrary.InterfaceTypeEntry;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/**
 * Updates a FB type: - removes the (old) pin by name - adds an Adapter (PLUG or
 * SOCKET) with a given name and type - saves the FB type entry
 *
 * Executed before the application reconnect step.
 */
public class UpdateFBTypeForAdapterChange extends Change {

	private final InterfaceTypeEntry fbTypeEntry;
	private final boolean createPlug; // true -> create PLUG, false -> create SOCKET
	private final String newAdapterName;
	private final Set<String> pinsToRemove;

	public UpdateFBTypeForAdapterChange(final InterfaceTypeEntry fbTypeEntry, final boolean createPlug,
			final String newAdapterName, final Set<String> pinsToRemove) {

		this.fbTypeEntry = Objects.requireNonNull(fbTypeEntry);
		this.createPlug = createPlug;
		this.newAdapterName = Objects.requireNonNull(newAdapterName);
		this.pinsToRemove = pinsToRemove;
	}

	@Override
	public String getName() {
		return "Update FB type '" + fbTypeEntry.getFile().getName() + "' (" + (createPlug ? "add PLUG" : "add SOCKET")
				+ ", remove '" + pinsToRemove.toString() + "')";
	}

	@Override
	public void initializeValidationData(final IProgressMonitor pm) {
		// nothing
	}

	@Override
	public RefactoringStatus isValid(final IProgressMonitor pm) throws CoreException {
		return new RefactoringStatus();
	}

	@Override
	public Change perform(final IProgressMonitor pm) throws CoreException {
		final FBType type = (FBType) fbTypeEntry.getType();
		final InterfaceList il = ensureInterfaceList(type);

		// 1) remove the pin (search events + vars on both sides; we don't consider
		// fan-out here)

		pinsToRemove.forEach(pin -> removeIfPresent(il, pin));
		// removeIfPresent(il, pinNameToRemove);

		// 2) add the adapter declaration (PLUG or SOCKET)

		final AdapterTypeEntry adapterTypeEntry = fbTypeEntry.getTypeLibrary().getAdapterTypeEntry(newAdapterName);

		// final String prefferedName = createPlug ?
		// NameRepository.createUniqueName(type, "PLUG")
		// : NameRepository.createUniqueName(type, "PLUG");

		// --- Use the generic CreateInterfaceElementCommand for adapters ---
		final CreateInterfaceElementCommand addAdapterCmd = new CreateInterfaceElementCommand(
				adapterTypeEntry.getType(), il, !createPlug, -1);

		addAdapterCmd.execute();

		// If you’re composing a bigger operation, add it to your CompoundCommand:
//		compound.add(addAdapterCmd);

		// After execution, the created element is retrievable like this:
//		final AdapterDeclaration created = (AdapterDeclaration) addAdapterCmd.getCreatedElement();

		// if (createPlug) {
		// il.getPlugs().add(created);
//		} else {
		// il.getSockets().add(created);
//		}

		// 3) save
		fbTypeEntry.save(type, pm);

		return null; // no undo
	}

	@Override
	public Object getModifiedElement() {
		return fbTypeEntry.getFile();
	}

	// -------- helpers --------

	private static InterfaceList ensureInterfaceList(final FBType type) {
		if (type.getInterfaceList() == null) {
			type.setInterfaceList(LibraryElementFactory.eINSTANCE.createInterfaceList());
		}
		return type.getInterfaceList();
	}

	private static void removeIfPresent(final InterfaceList il, final String name) {
		// events
		il.getEventInputs().removeIf(e -> nameEquals(e, name));
		il.getEventOutputs().removeIf(e -> nameEquals(e, name));
		// vars
		il.getInputVars().removeIf(v -> nameEquals(v, name));
		il.getOutputVars().removeIf(v -> nameEquals(v, name));
		// adapters (just in case)
		il.getSockets().removeIf(a -> nameEquals(a, name));
		il.getPlugs().removeIf(a -> nameEquals(a, name));
	}

	private static boolean nameEquals(final Event e, final String n) {
		return e != null && e.getName() != null && e.getName().equals(n);
	}

	private static boolean nameEquals(final VarDeclaration v, final String n) {
		return v != null && v.getName() != null && v.getName().equals(n);
	}

	private static boolean nameEquals(final AdapterDeclaration a, final String n) {
		return a != null && a.getName() != null && a.getName().equals(n);
	}
}
