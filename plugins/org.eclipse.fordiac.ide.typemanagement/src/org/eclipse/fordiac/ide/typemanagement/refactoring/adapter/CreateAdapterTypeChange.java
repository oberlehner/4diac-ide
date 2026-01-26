package org.eclipse.fordiac.ide.typemanagement.refactoring.adapter;

import java.util.Objects;
import java.util.Set;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterType;
import org.eclipse.fordiac.ide.model.libraryElement.InterfaceList;
import org.eclipse.fordiac.ide.model.typelibrary.AdapterTypeEntry;
import org.eclipse.fordiac.ide.model.typelibrary.TypeEntry;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibrary;
import org.eclipse.fordiac.ide.model.typelibrary.TypeLibraryManager;
import org.eclipse.ltk.core.refactoring.Change;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/**
 * Create an IEC 61499 AdapterType (.adp) by mirroring two FB interfaces.
 * Orientation is from provider/plug (left) to acceptor/socket (right).
 *
 * providerIF ➜ acceptorIF mapping: - provider EventOutput & acceptor EventInput
 * -> AdapterType EventOutput - acceptor EventOutput & provider EventInput ->
 * AdapterType EventInput - provider VarOutput & acceptor VarInput ->
 * AdapterType VarOutput - acceptor VarOutput & provider VarInput -> AdapterType
 * VarInput
 */
public class CreateAdapterTypeChange extends Change {

	private static final String PLUGIN_ID = "org.eclipse.fordiac.ide.typemanagement";

	private final IFolder libFolder;
	private final String typeName;
	private final InterfaceList providerIF; // left / plug side
	private final InterfaceList acceptorIF; // right / socket side
	private final Set<String> leftPinsToRemove;
	private final Set<String> rightPinsToRemove;
	private IFile createdFile;

	/**
	 * @param libFolder         destination folder in the project type library
	 * @param typeName          adapter type name (without extension)
	 * @param providerIF        interface of the providing (plug) side
	 * @param acceptorIF        interface of the accepting (socket) side
	 * @param rightPinsToRemove
	 * @param leftPinsToRemove
	 */
	public CreateAdapterTypeChange(final IFolder libFolder, final String typeName, final InterfaceList providerIF,
			final InterfaceList acceptorIF, final Set<String> leftPinsToRemove, final Set<String> rightPinsToRemove) {
		this.libFolder = Objects.requireNonNull(libFolder);
		this.typeName = Objects.requireNonNull(typeName);
		this.providerIF = Objects.requireNonNull(providerIF);
		this.acceptorIF = Objects.requireNonNull(acceptorIF);
		this.leftPinsToRemove = leftPinsToRemove;
		this.rightPinsToRemove = rightPinsToRemove;
	}

	@Override
	public String getName() {
		return "Create Adapter Type: " + typeName;
	}

	@Override
	public void initializeValidationData(final IProgressMonitor pm) {
		// nothing to pre-compute
	}

	@Override
	public RefactoringStatus isValid(final IProgressMonitor pm) {
		final RefactoringStatus status = new RefactoringStatus();
		if (!libFolder.exists()) {
			// not fatal; perform() will create it
		}
		if (typeName.isBlank()) {
			status.addFatalError("Adapter type name must not be blank");
		}
		return status;
	}

	@Override
	public Change perform(final IProgressMonitor pm) throws CoreException {
		// 1) Ensure target folder + file
		if (!libFolder.exists()) {
			libFolder.create(true, true, pm);
		}
		createdFile = libFolder.getFile(typeName + ".adp");

		// 2) Register TypeEntry in project TypeLibrary
		final TypeLibrary tl = TypeLibraryManager.INSTANCE.getTypeLibrary(libFolder.getProject());
		final TypeEntry entry = tl.createTypeEntry(createdFile);
		if (!(entry instanceof final AdapterTypeEntry atEntry)) {
			throw new CoreException(Status.error(PLUGIN_ID));
		}

		// 3) Build adapter type (IEC 61499-compliant) and save via the entry
		final AdapterType at = AdapterTypeBuilder.buildAdapterType(typeName, providerIF, acceptorIF, leftPinsToRemove,
				rightPinsToRemove);
		atEntry.save(at, pm); // exporter handles XML + IFile I/O

		// No undo change here (return null). If you need undo, wrap this in a
		// higher-level LTK refactoring.
		return null;
	}

	@Override
	public Object getModifiedElement() {
		return createdFile;
	}
}
