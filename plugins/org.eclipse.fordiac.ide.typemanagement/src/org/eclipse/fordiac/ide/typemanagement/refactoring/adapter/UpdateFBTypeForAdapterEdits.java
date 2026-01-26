package org.eclipse.fordiac.ide.typemanagement.refactoring.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fordiac.ide.model.commands.create.CreateInterfaceElementCommand;
import org.eclipse.fordiac.ide.model.commands.delete.DeleteInterfaceCommand;
import org.eclipse.fordiac.ide.model.libraryElement.FBType;
import org.eclipse.fordiac.ide.model.libraryElement.IInterfaceElement;
import org.eclipse.fordiac.ide.model.libraryElement.InterfaceList;
import org.eclipse.fordiac.ide.model.libraryElement.LibraryElementFactory;
import org.eclipse.fordiac.ide.model.typelibrary.AdapterTypeEntry;
import org.eclipse.fordiac.ide.typemanagement.refactoring.ModelEdit;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/**
 * Splits "update FB type for adapter" into two ModelEdits: 1) remove selected
 * interface elements by name 2) create the adapter declaration (PLUG or SOCKET)
 *
 * Use with {@code ModelEditChange.fromModelEdits(...)}.
 */
public final class UpdateFBTypeForAdapterEdits {

	private UpdateFBTypeForAdapterEdits() {
	}

	/**
	 * @param fbType          the FB type to modify
	 * @param pinsToRemove    names of interface elements to remove
	 *                        (events/vars/adapters)
	 * @param createPlug      true -> create PLUG, false -> create SOCKET
	 * @param adapterTypeName adapter TYPE name (without extension), e.g.
	 *                        "sensor_visionAdapter"
	 * @param adapterDeclName desired declaration name on the FB, e.g.
	 *                        "PLUG1"/"SOCKET1" (nullable -> defaults)
	 */
	public static List<ModelEdit<?>> build(final FBType fbType, final Set<String> pinsToRemove,
			final boolean createPlug, final String adapterTypeName, final String adapterDeclName,
			final Map<String, String> oldToAdapterPinName) {

		Objects.requireNonNull(fbType, "fbType");
		Objects.requireNonNull(adapterTypeName, "adapterTypeName");

		final URI typeURI = EcoreUtil.getURI(fbType);

		final List<ModelEdit<?>> edits = new ArrayList<>(2);

		edits.add(new CreateAdapterDeclarationEdit(typeURI, fbType.getName(), createPlug, adapterTypeName,
				adapterDeclName));

//		edits.add(new ReconnectPinsToBeRemovedEdit(typeURI, fbType.getName(), pinsToRemove, adapterTypeName,
//				oldToAdapterPinName));

		edits.add(new RemoveInterfaceElementsEdit(typeURI, fbType.getName(), pinsToRemove));
		return edits;
	}

	/**
	 * Convenience overload with default decl name (PLUG1/SOCKET1).
	 */
	public static List<ModelEdit<?>> build(final FBType fbType, final Set<String> pinsToRemove,
			final boolean createPlug, final String adapterTypeName) {

		return build(fbType, pinsToRemove, createPlug, adapterTypeName, null, null);
	}

	// --------------------------------------------------------------------------
	// ModelEdits
	// --------------------------------------------------------------------------

	private static final class RemoveInterfaceElementsEdit extends ModelEdit<FBType> {
		private final Set<String> pinsToRemove;

		RemoveInterfaceElementsEdit(final URI fbTypeURI, final String fbTypeName, final Set<String> pinsToRemove) {
			super("Remove interface elements from " + (fbTypeName != null ? fbTypeName : fbTypeURI.lastSegment()),
					fbTypeURI, FBType.class);
			this.pinsToRemove = pinsToRemove;
		}

		@Override
		public void initializeValidationData(final FBType element, final IProgressMonitor pm) {
			// no-op
		}

		@Override
		public RefactoringStatus isValid(final FBType element, final IProgressMonitor pm) throws CoreException {
			return new RefactoringStatus(); // removal is best-effort (missing pins are ignored)
		}

		@Override
		protected Command createCommand(final FBType type) {
			final CompoundCommand cmd = new CompoundCommand(getName());
			final InterfaceList il = ensureInterfaceList(type);

			boolean addedAnything = false;

			if (pinsToRemove != null && !pinsToRemove.isEmpty()) {
				// Snapshot all elements once; we only create commands here (no mutation yet).
				final List<IInterfaceElement> all = new ArrayList<>(il.getAllInterfaceElements().toList());

				for (final String pin : pinsToRemove) {
					if (pin == null || pin.isBlank()) {
						continue;
					}
					for (final IInterfaceElement ie : all) {
						if (ie != null && pin.equals(ie.getName())) {
							cmd.add(new DeleteInterfaceCommand(ie));
							addedAnything = true;
						}
					}
				}
			}

			// Important: never return null; also avoid an empty CompoundCommand if it can't
			// execute in your GEF version.
			if (!addedAnything) {
				return new Command("No-op (nothing to remove)") {
					@Override
					public void execute() {
						/* no-op */}
				};
			}
			return cmd;
		}
	}

	private static final class CreateAdapterDeclarationEdit extends ModelEdit<FBType> {
		private final boolean createPlug;
		private final String adapterTypeName;
		private final String adapterDeclName;

		CreateAdapterDeclarationEdit(final URI fbTypeURI, final String fbTypeName, final boolean createPlug,
				final String adapterTypeName, final String adapterDeclName) {

			super("Create " + (createPlug ? "PLUG" : "SOCKET") + " adapter on "
					+ (fbTypeName != null ? fbTypeName : fbTypeURI.lastSegment()), fbTypeURI, FBType.class);

			this.createPlug = createPlug;
			this.adapterTypeName = Objects.requireNonNull(adapterTypeName, "adapterTypeName");
			this.adapterDeclName = (adapterDeclName != null && !adapterDeclName.isBlank()) ? adapterDeclName
					: (createPlug ? "PLUG1" : "SOCKET1");
		}

		@Override
		public void initializeValidationData(final FBType element, final IProgressMonitor pm) {
			// no-op
		}

		@Override
		public RefactoringStatus isValid(final FBType type, final IProgressMonitor pm) throws CoreException {
			final RefactoringStatus st = new RefactoringStatus();

			if (type.getTypeLibrary() == null) {
				st.addFatalError("TypeLibrary is null for FBType '" + type.getName() + "'");
				return st;
			}

			return st;
		}

		@Override
		protected Command createCommand(final FBType type) {
			final InterfaceList il = ensureInterfaceList(type);

			final AdapterTypeEntry adapterTypeEntry = (type.getTypeLibrary() != null)
					? type.getTypeLibrary().getAdapterTypeEntry(adapterTypeName)
					: null;

			if (adapterTypeEntry == null || adapterTypeEntry.getType() == null) {
				// isValid() should prevent this, but keep it safe
				return new Command("No-op (adapter type missing)") {
					@Override
					public boolean canExecute() {
						return false;
					}
				};
			}

			return new CreateInterfaceElementCommand(adapterTypeEntry.getType(), adapterDeclName, il, !createPlug, -1);
		}
	}

	// --------------------------------------------------------------------------
	// Helpers
	// --------------------------------------------------------------------------

	private static InterfaceList ensureInterfaceList(final FBType type) {
		if (type.getInterfaceList() == null) {
			type.setInterfaceList(LibraryElementFactory.eINSTANCE.createInterfaceList());
		}
		return type.getInterfaceList();
	}
}
