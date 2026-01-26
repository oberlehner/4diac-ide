package org.eclipse.fordiac.ide.typemanagement.refactoring.adapter;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.OperationCanceledException;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.util.EcoreUtil;
import org.eclipse.fordiac.ide.model.commands.create.AbstractConnectionCreateCommand;
import org.eclipse.fordiac.ide.model.commands.delete.DeleteConnectionCommand;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterConnection;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterDeclaration;
import org.eclipse.fordiac.ide.model.libraryElement.AutomationSystem;
import org.eclipse.fordiac.ide.model.libraryElement.BlockFBNetworkElement;
import org.eclipse.fordiac.ide.model.libraryElement.Connection;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetwork;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetworkElement;
import org.eclipse.fordiac.ide.model.typelibrary.SystemEntry;
import org.eclipse.fordiac.ide.typemanagement.refactoring.ModelEdit;
import org.eclipse.gef.commands.Command;
import org.eclipse.gef.commands.CompoundCommand;
import org.eclipse.ltk.core.refactoring.RefactoringStatus;

/**
 * Replaces selected (data/event) connections between two FB instances with an
 * AdapterConnection between newly added adapter pins (PLUG/SOCKET).
 *
 * Performance notes: - Updates each affected instance at most once
 * (UpdateFBTypeCommand is expensive). - Builds connection/adapter indices AFTER
 * updates, using stable string keys. - Delete-skip optimization: only deletes
 * old connections if they still exist after the FB-type update (usually the
 * update already removes them).
 */
public final class ReplaceConnectionWithAdapterEdits {
	private ReplaceConnectionWithAdapterEdits() {
	}

	/**
	 * Backwards compatible overload (replaces ONLY the single selected connection).
	 *
	 * If you want to remove ALL selected pins (events+data), call the overload with
	 * leftPinsToRemove/rightPinsToRemove.
	 */
	public static List<ModelEdit<?>> build(final Connection selectedConn, final String adapterTypeName,
			final String leftAdapterInstName, final String rightAdapterInstName) {

		final Set<String> left = new LinkedHashSet<>();
		final Set<String> right = new LinkedHashSet<>();
		if (selectedConn != null && selectedConn.getSource() != null && selectedConn.getDestination() != null) {
			left.add(selectedConn.getSource().getName());
			right.add(selectedConn.getDestination().getName());
		}
		return build(selectedConn, adapterTypeName, leftAdapterInstName, rightAdapterInstName, left, right);
	}

	/**
	 * Preferred overload: provide the ordered pairs of pins that shall be replaced.
	 *
	 * IMPORTANT: leftPinsToRemove/rightPinsToRemove must be in the same order
	 * (pairing by index), exactly like you collected them from the selection.
	 */
	public static List<ModelEdit<?>> build(final Connection selectedConn, final String adapterTypeName,
			final String leftAdapterInstName, final String rightAdapterInstName, final Set<String> leftPinsToRemove,
			final Set<String> rightPinsToRemove) {

		Objects.requireNonNull(selectedConn, "selectedConn");
		Objects.requireNonNull(adapterTypeName, "adapterTypeName");
		Objects.requireNonNull(leftAdapterInstName, "leftAdapterInstName");
		Objects.requireNonNull(rightAdapterInstName, "rightAdapterInstName");

		final URI networkURI = EcoreUtil.getURI(selectedConn.getFBNetwork());
		final URI conURI = EcoreUtil.getURI(selectedConn);
		// keep pairing order (selection order)
		final List<String> leftPins = new ArrayList<>(leftPinsToRemove != null ? leftPinsToRemove : Set.of());
		final List<String> rightPins = new ArrayList<>(rightPinsToRemove != null ? rightPinsToRemove : Set.of());

		final List<ModelEdit<?>> edits = new ArrayList<>();
		edits.add(new ReplaceConnectionWithAdapterEdit(networkURI, conURI, adapterTypeName, leftAdapterInstName,
				rightAdapterInstName, leftPins, rightPins));
		return edits;
	}

	private static final class ReplaceConnectionWithAdapterEdit extends ModelEdit<FBNetwork> {
		private final URI netwconURI;
		@SuppressWarnings("unused")
		private final String adapterTypeName; // kept for debugging / future use
		private final String leftAdapterInstName;
		private final String rightAdapterInstName;
		private final List<String> leftPins;
		private final List<String> rightPins;

		ReplaceConnectionWithAdapterEdit(final URI networkURI, final URI connURI, final String adapterTypeName,
				final String leftAdapterInstName, final String rightAdapterInstName, final List<String> leftPins,
				final List<String> rightPins) {

			super("Replace connection(s) with adapter", networkURI, FBNetwork.class);
			this.netwconURI = connURI;
			this.adapterTypeName = adapterTypeName;
			this.leftAdapterInstName = leftAdapterInstName;
			this.rightAdapterInstName = rightAdapterInstName;
			this.leftPins = leftPins != null ? leftPins : List.of();
			this.rightPins = rightPins != null ? rightPins : List.of();
		}

		@Override
		protected Command createCommand(final FBNetwork model) {
			if (model == null) {
				return new Command() {
					@Override
					public boolean canExecute() {
						return false;
					}
				};
			}
			// IMPORTANT: defer all lookups to execute() (adapter decls might not exist at
			// creation time)
			return new DeferredReplaceWithAdapterCommand(model, netwconURI, leftAdapterInstName, rightAdapterInstName,
					leftPins, rightPins);
		}

		@Override
		public void initializeValidationData(final FBNetwork element, final IProgressMonitor pm) {
			// no-op
		}

		@Override
		public RefactoringStatus isValid(final FBNetwork element, final IProgressMonitor pm)
				throws CoreException, OperationCanceledException {
			final RefactoringStatus status = new RefactoringStatus();
			if (element == null) {
				status.addFatalError("FBNetwork is null");
				return status;
			}
			if (netwconURI == null || netwconURI.fragment() == null) {
				status.addFatalError("Selected connection URI is invalid");
				return status;
			}
			return status;
		}
	}

	/**
	 * Does not resolve AdapterDeclarations or the list of affected instance-pairs
	 * until execute() is called.
	 */
	static final class DeferredReplaceWithAdapterCommand extends Command {
		private FBNetwork network;
		private final URI connectionUri;
		private final String leftAdapterInstName;
		private final String rightAdapterInstName;
		private final List<String> leftPins;
		private final List<String> rightPins;

		private CompoundCommand executed;

		DeferredReplaceWithAdapterCommand(final FBNetwork network, final URI connectionUri,
				final String leftAdapterInstName, final String rightAdapterInstName, final List<String> leftPins,
				final List<String> rightPins) {
			this.network = network;
			this.connectionUri = connectionUri;
			this.leftAdapterInstName = leftAdapterInstName;
			this.rightAdapterInstName = rightAdapterInstName;
			this.leftPins = leftPins != null ? leftPins : List.of();
			this.rightPins = rightPins != null ? rightPins : List.of();
		}

		@Override
		public boolean canExecute() {
			return network != null && connectionUri != null && connectionUri.fragment() != null;
		}

		@Override
		public void execute() {
			executed = new CompoundCommand("Replace connections with adapter (deferred)");

			final int n = Math.min(leftPins.size(), rightPins.size());
			if (n <= 0) {
				return;
			}

			final AutomationSystem sys = (AutomationSystem) EcoreUtil.getRootContainer(network);

			final SystemEntry typeEntry = (SystemEntry) sys.getTypeEntry();
			network = typeEntry.getSystem().getApplication().get(0).getFBNetwork();

			final Connection original = resolveConnection(network, connectionUri);
			if (original == null || original.getSource() == null || original.getDestination() == null) {
				return; // nothing we can do safely
			}

			final BlockFBNetworkElement baseLeft = original.getSource().getBlockFBNetworkElement();
			final BlockFBNetworkElement baseRight = original.getDestination().getBlockFBNetworkElement();
			if (baseLeft == null || baseRight == null) {
				return;
			}

			// -------------------------
			// Phase 1: scan BEFORE updates using ONLY stable string keys
			// -------------------------

			final List<Connection> preConnections = allNonAdapterConnections(network);
			final Map<ConnKey, Connection> preConnIndex = buildConnIndex(preConnections);

			final Set<PairKey> pairs = new LinkedHashSet<>();

			// 1) Find all instance-pairs that have *all* selected connections (fast, 1
			// scan)
			pairs.addAll(findPairsFromExistingConnectionsStrict(preConnections, leftPins, rightPins));

			// 2) Add name-pattern pairs (sensor_2 <-> vision_2) but keep only those that
			// actually
			// have the full set (either normal or swapped wiring)
			for (final PairKey pk : findPairsByNamePattern(network, baseLeft, baseRight)) {
				if (hasAllSelectedConnections(preConnIndex, pk.leftName, pk.rightName, false, leftPins, rightPins)
						|| hasAllSelectedConnections(preConnIndex, pk.leftName, pk.rightName, true, leftPins,
								rightPins)) {
					pairs.add(pk);
				}
			}

			// 3) Always include the originally selected pair (if it matches)
			if (baseLeft.getName() != null && baseRight.getName() != null) {
				pairs.add(new PairKey(baseLeft.getName(), baseRight.getName()));
			}

			if (pairs.isEmpty()) {
				return;
			}

			// -------------------------
			// Phase 2: update all affected instances ONCE
			// -------------------------

//			final Map<String, BlockFBNetworkElement> preBlocks = indexBlocksByName(network);
//
//			final Set<String> toUpdateNames = new LinkedHashSet<>();
//			for (final PairKey pk : pairs) {
//				toUpdateNames.add(pk.leftName);
//				toUpdateNames.add(pk.rightName);
//			}
//
//			// Execute updates immediately (as before), but only once per block instance.
//			// NOTE: UpdateFBTypeCommand may replace the instance EObject. Do not cache any
//			// EObject references across updates.
//			for (final String fbName : toUpdateNames) {
//				final BlockFBNetworkElement fb = preBlocks.get(fbName);
//				if (fb == null) {
//					continue;
//				}
//				final UpdateFBTypeCommand update = new UpdateFBTypeCommand(fb);
//				update.execute();
//			}

			// -------------------------
			// Phase 3: build indices AFTER updates and create adapter connections
			// -------------------------

			final Map<String, BlockFBNetworkElement> postBlocks = indexBlocksByName(network);
			final Map<ConnKey, Connection> postConnIndex = buildConnIndex(allNonAdapterConnections(network));
			final Set<AdapterConnKey> postAdapterIndex = buildAdapterConnIndex(network);

			for (final PairKey pk : pairs) {
				final BlockFBNetworkElement leftFB = postBlocks.get(pk.leftName);
				final BlockFBNetworkElement rightFB = postBlocks.get(pk.rightName);
				if (leftFB == null || rightFB == null) {
					continue;
				}

				// Determine orientation: expected is left has PLUG, right has SOCKET
				AdapterDeclaration plug = findPlug(leftFB, leftAdapterInstName);
				AdapterDeclaration socket = findSocket(rightFB, rightAdapterInstName);
				boolean swapped = false;

				if (plug == null || socket == null) {
					// Defensive swapped orientation
					final AdapterDeclaration plug2 = findPlug(rightFB, leftAdapterInstName);
					final AdapterDeclaration socket2 = findSocket(leftFB, rightAdapterInstName);
					if (plug2 == null || socket2 == null) {
						continue;
					}
					plug = plug2;
					socket = socket2;
					swapped = true;
				}

				final AdapterConnKey akey = AdapterConnKey.of(pk.leftName, pk.rightName, leftAdapterInstName,
						rightAdapterInstName, swapped);

				// If adapter already exists, we still delete any remaining old connections (if
				// any)
				if (postAdapterIndex.contains(akey) || hasAdapterConnection(network, plug, socket)) {
					addDeleteRemainingConnections(executed, postConnIndex, pk.leftName, pk.rightName, swapped, leftPins,
							rightPins);
					continue;
				}

				final AbstractConnectionCreateCommand createAdapter = createAdapterConnection(network, plug, socket);
				if (createAdapter == null || !createAdapter.canExecute()) {
					continue;
				}

				executed.add(createAdapter);
				postAdapterIndex.add(akey);

				// Delete-skip optimization:
				// Only delete old connections that still exist after UpdateFBTypeCommand.
				addDeleteRemainingConnections(executed, postConnIndex, pk.leftName, pk.rightName, swapped, leftPins,
						rightPins);
			}

			executed.execute();
		}

		@Override
		public void undo() {
			if (executed != null) {
				executed.undo();
			}
		}

		@Override
		public void redo() {
			if (executed != null) {
				executed.redo();
			}
		}

		private static Connection resolveConnection(final FBNetwork network, final URI connectionUri) {
			if (network == null || network.eResource() == null || connectionUri == null
					|| connectionUri.fragment() == null) {
				return null;
			}
			final EObject obj = network.eResource().getEObject(connectionUri.fragment());
			return (obj instanceof Connection) ? (Connection) obj : null;
		}

		private static List<Connection> allNonAdapterConnections(final FBNetwork net) {
			final List<Connection> all = new ArrayList<>();
			if (net == null) {
				return all;
			}
			// Data + Event (NOT adapter connections)
			all.addAll(net.getDataConnections());
			all.addAll(net.getEventConnections());
			return all;
		}

		private static Map<String, BlockFBNetworkElement> indexBlocksByName(final FBNetwork net) {
			final Map<String, BlockFBNetworkElement> idx = new HashMap<>();
			if (net == null) {
				return idx;
			}
			for (final FBNetworkElement el : net.getNetworkElements()) {
				if (el instanceof final BlockFBNetworkElement fb) {
					final String name = fb.getName();
					if (name != null) {
						idx.put(name, fb);
					}
				}
			}
			return idx;
		}

		// ---------- fast indices (string keys) ----------

		private static final class ConnKey {
			final String sFB;
			final String sPin;
			final String dFB;
			final String dPin;

			ConnKey(final String sFB, final String sPin, final String dFB, final String dPin) {
				this.sFB = sFB;
				this.sPin = sPin;
				this.dFB = dFB;
				this.dPin = dPin;
			}

			@Override
			public int hashCode() {
				return Objects.hash(sFB, sPin, dFB, dPin);
			}

			@Override
			public boolean equals(final Object obj) {
				if (this == obj) {
					return true;
				}
				if (!(obj instanceof final ConnKey other)) {
					return false;
				}
				return Objects.equals(sFB, other.sFB) && Objects.equals(sPin, other.sPin)
						&& Objects.equals(dFB, other.dFB) && Objects.equals(dPin, other.dPin);
			}
		}

		private static Map<ConnKey, Connection> buildConnIndex(final List<Connection> conns) {
			final Map<ConnKey, Connection> idx = new HashMap<>();
			if (conns == null) {
				return idx;
			}
			for (final Connection c : conns) {
				if (c == null || c.getSource() == null || c.getDestination() == null) {
					continue;
				}
				final BlockFBNetworkElement srcFB = c.getSource().getBlockFBNetworkElement();
				final BlockFBNetworkElement dstFB = c.getDestination().getBlockFBNetworkElement();
				if (srcFB == null || dstFB == null) {
					continue;
				}
				final String sFB = srcFB.getName();
				final String dFB = dstFB.getName();
				final String sPin = c.getSource().getName();
				final String dPin = c.getDestination().getName();
				if (sFB == null || dFB == null || sPin == null || dPin == null) {
					continue;
				}
				idx.put(new ConnKey(sFB, sPin, dFB, dPin), c);
			}
			return idx;
		}

		private static boolean hasAllSelectedConnections(final Map<ConnKey, Connection> idx, final String leftFBName,
				final String rightFBName, final boolean swapped, final List<String> leftPins,
				final List<String> rightPins) {

			if (idx == null || leftFBName == null || rightFBName == null) {
				return false;
			}
			final int n = Math.min(leftPins.size(), rightPins.size());
			for (int i = 0; i < n; i++) {
				final String lPin = leftPins.get(i);
				final String rPin = rightPins.get(i);
				if (lPin == null || rPin == null) {
					return false;
				}
				final ConnKey k = swapped ? new ConnKey(rightFBName, rPin, leftFBName, lPin)
						: new ConnKey(leftFBName, lPin, rightFBName, rPin);
				if (!idx.containsKey(k)) {
					return false;
				}
			}
			return true;
		}

		private static void addDeleteRemainingConnections(final CompoundCommand cc, final Map<ConnKey, Connection> idx,
				final String leftFBName, final String rightFBName, final boolean swapped, final List<String> leftPins,
				final List<String> rightPins) {

			if (cc == null || idx == null || leftFBName == null || rightFBName == null) {
				return;
			}
			final int n = Math.min(leftPins.size(), rightPins.size());
			for (int i = 0; i < n; i++) {
				final String lPin = leftPins.get(i);
				final String rPin = rightPins.get(i);
				if (lPin == null || rPin == null) {
					continue;
				}
				final ConnKey k = swapped ? new ConnKey(rightFBName, rPin, leftFBName, lPin)
						: new ConnKey(leftFBName, lPin, rightFBName, rPin);
				final Connection c = idx.remove(k);
				if (c != null) {
					cc.add(new DeleteConnectionCommand(c));
				}
			}
		}

		private static final class AdapterConnKey {
			final String aEnd;
			final String bEnd;

			private AdapterConnKey(final String aEnd, final String bEnd) {
				this.aEnd = aEnd;
				this.bEnd = bEnd;
			}

			static AdapterConnKey of(final String leftFBName, final String rightFBName, final String plugName,
					final String socketName, final boolean swapped) {

				// We want a direction-independent key, so we normalize endpoints
				// lexicographically.
				final String left = (swapped ? rightFBName : leftFBName) + "#" + plugName; //$NON-NLS-1$
				final String right = (swapped ? leftFBName : rightFBName) + "#" + socketName; //$NON-NLS-1$

				return normalized(left, right);
			}

			static AdapterConnKey normalized(final String end1, final String end2) {
				if (end1 == null || end2 == null) {
					return new AdapterConnKey(String.valueOf(end1), String.valueOf(end2));
				}
				return (end1.compareTo(end2) <= 0) ? new AdapterConnKey(end1, end2) : new AdapterConnKey(end2, end1);
			}

			@Override
			public int hashCode() {
				return Objects.hash(aEnd, bEnd);
			}

			@Override
			public boolean equals(final Object obj) {
				if (this == obj) {
					return true;
				}
				if (!(obj instanceof final AdapterConnKey other)) {
					return false;
				}
				return Objects.equals(aEnd, other.aEnd) && Objects.equals(bEnd, other.bEnd);
			}
		}

		private static Set<AdapterConnKey> buildAdapterConnIndex(final FBNetwork net) {
			final Set<AdapterConnKey> idx = new LinkedHashSet<>();
			if (net == null) {
				return idx;
			}
			for (final AdapterConnection c : net.getAdapterConnections()) {
				if (c == null || c.getSource() == null || c.getDestination() == null) {
					continue;
				}
				final AdapterDeclaration s = (AdapterDeclaration) c.getSource();
				final AdapterDeclaration d = (AdapterDeclaration) c.getDestination();

				final BlockFBNetworkElement sFB = s.getBlockFBNetworkElement();
				final BlockFBNetworkElement dFB = d.getBlockFBNetworkElement();
				if (sFB == null || dFB == null || sFB.getName() == null || dFB.getName() == null || s.getName() == null
						|| d.getName() == null) {
					continue;
				}

				final String e1 = sFB.getName() + "#" + s.getName(); //$NON-NLS-1$
				final String e2 = dFB.getName() + "#" + d.getName(); //$NON-NLS-1$
				idx.add(AdapterConnKey.normalized(e1, e2));
			}
			return idx;
		}

		// ---------- pair detection (fast/strict) ----------

		private static Set<PairKey> findPairsFromExistingConnectionsStrict(final List<Connection> all,
				final List<String> leftPins, final List<String> rightPins) {

			final Set<PairKey> result = new LinkedHashSet<>();
			if (all == null) {
				return result;
			}
			final int n = Math.min(leftPins.size(), rightPins.size());
			if (n <= 0) {
				return result;
			}

			final Map<PairKey, BitSet> matchBits = new HashMap<>();

			for (final Connection c : all) {
				if (c == null || c.getSource() == null || c.getDestination() == null) {
					continue;
				}
				final BlockFBNetworkElement srcFB = c.getSource().getBlockFBNetworkElement();
				final BlockFBNetworkElement dstFB = c.getDestination().getBlockFBNetworkElement();
				if (srcFB == null || dstFB == null || srcFB.getName() == null || dstFB.getName() == null) {
					continue;
				}

				final String srcName = c.getSource().getName();
				final String dstName = c.getDestination().getName();
				if (srcName == null || dstName == null) {
					continue;
				}

				for (int i = 0; i < n; i++) {
					if (Objects.equals(srcName, leftPins.get(i)) && Objects.equals(dstName, rightPins.get(i))) {
						final PairKey pk = new PairKey(srcFB.getName(), dstFB.getName());
						final BitSet bs = matchBits.computeIfAbsent(pk, k -> new BitSet(n));
						bs.set(i);
						break;
					}
					// also allow swapped direction when discovering pairs
					if (Objects.equals(srcName, rightPins.get(i)) && Objects.equals(dstName, leftPins.get(i))) {
						final PairKey pk = new PairKey(dstFB.getName(), srcFB.getName());
						final BitSet bs = matchBits.computeIfAbsent(pk, k -> new BitSet(n));
						bs.set(i);
						break;
					}
				}
			}

			for (final Map.Entry<PairKey, BitSet> e : matchBits.entrySet()) {
				if (e.getValue().cardinality() == n) {
					result.add(e.getKey());
				}
			}
			return result;
		}

		private static Set<PairKey> findPairsByNamePattern(final FBNetwork net, final BlockFBNetworkElement baseLeft,
				final BlockFBNetworkElement baseRight) {

			final Set<PairKey> pairs = new LinkedHashSet<>();
			if (net == null || baseLeft == null || baseRight == null) {
				return pairs;
			}

			final String leftBaseName = baseLeft.getName();
			final String rightBaseName = baseRight.getName();
			final String leftBaseType = typeName(baseLeft);
			final String rightBaseType = typeName(baseRight);

			if (leftBaseName == null || rightBaseName == null || leftBaseType == null || rightBaseType == null) {
				return pairs;
			}

			final Map<String, BlockFBNetworkElement> leftBySuffix = new HashMap<>();
			final Map<String, BlockFBNetworkElement> rightBySuffix = new HashMap<>();

			for (final FBNetworkElement el : net.getNetworkElements()) {
				if (!(el instanceof final BlockFBNetworkElement fb)) {
					continue;
				}
				final String t = typeName(fb);
				if (t == null || fb.getName() == null) {
					continue;
				}

				final String suffixL = extractSuffix(fb.getName(), leftBaseName);
				if (suffixL != null && Objects.equals(t, leftBaseType)) {
					leftBySuffix.put(suffixL, fb);
				}

				final String suffixR = extractSuffix(fb.getName(), rightBaseName);
				if (suffixR != null && Objects.equals(t, rightBaseType)) {
					rightBySuffix.put(suffixR, fb);
				}
			}

			// pair by same suffix
			for (final Map.Entry<String, BlockFBNetworkElement> e : leftBySuffix.entrySet()) {
				final String suffix = e.getKey();
				final BlockFBNetworkElement l = e.getValue();
				final BlockFBNetworkElement r = rightBySuffix.get(suffix);
				if (l != null && r != null && l.getName() != null && r.getName() != null) {
					pairs.add(new PairKey(l.getName(), r.getName()));
				}
			}
			return pairs;
		}

		private static String extractSuffix(final String name, final String base) {
			if (name == null || base == null) {
				return null;
			}
			if (name.equals(base)) {
				return ""; // base instance
			}
			final String prefix = base + "_"; //$NON-NLS-1$
			if (name.startsWith(prefix) && name.length() > prefix.length()) {
				return name.substring(prefix.length());
			}
			return null;
		}

		private static String typeName(final BlockFBNetworkElement fb) {
			if (fb == null || fb.getType() == null) {
				return null;
			}
			return fb.getType().getName();
		}

		private static AdapterDeclaration findPlug(final BlockFBNetworkElement fb, final String name) {
			if (fb == null || fb.getInterface() == null || name == null) {
				return null;
			}
			for (final AdapterDeclaration a : fb.getInterface().getPlugs()) {
				if (name.equals(a.getName())) {
					return a;
				}
			}
			return null;
		}

		private static AdapterDeclaration findSocket(final BlockFBNetworkElement fb, final String name) {
			if (fb == null || fb.getInterface() == null || name == null) {
				return null;
			}
			for (final AdapterDeclaration a : fb.getInterface().getSockets()) {
				if (name.equals(a.getName())) {
					return a;
				}
			}
			return null;
		}

		private static boolean hasAdapterConnection(final FBNetwork net, final AdapterDeclaration a,
				final AdapterDeclaration b) {
			if (net == null || a == null || b == null) {
				return false;
			}
			for (final AdapterConnection c : net.getAdapterConnections()) {
				if (c == null || c.getSource() == null || c.getDestination() == null) {
					continue;
				}
				if ((c.getSource() == a && c.getDestination() == b)
						|| (c.getSource() == b && c.getDestination() == a)) {
					return true;
				}
			}
			return false;
		}

		private static AbstractConnectionCreateCommand createAdapterConnection(final FBNetwork net,
				final AdapterDeclaration a, final AdapterDeclaration b) {
			// try a -> b
			AbstractConnectionCreateCommand cmd = AbstractConnectionCreateCommand.createCommand(net, a, b);
			if (cmd != null) {
				cmd.setSource(a);
				cmd.setDestination(b);
				if (cmd.canExecute()) {
					return cmd;
				}
			}
			// try b -> a
			cmd = AbstractConnectionCreateCommand.createCommand(net, b, a);
			if (cmd != null) {
				cmd.setSource(b);
				cmd.setDestination(a);
				if (cmd.canExecute()) {
					return cmd;
				}
			}
			return null;
		}
	}

	private static final class PairKey {
		final String leftName;
		final String rightName;

		PairKey(final String leftName, final String rightName) {
			this.leftName = leftName;
			this.rightName = rightName;
		}

		@Override
		public int hashCode() {
			return Objects.hash(leftName, rightName);
		}

		@Override
		public boolean equals(final Object obj) {
			if (this == obj) {
				return true;
			}
			if (!(obj instanceof final PairKey other)) {
				return false;
			}
			return Objects.equals(leftName, other.leftName) && Objects.equals(rightName, other.rightName);
		}
	}
}
