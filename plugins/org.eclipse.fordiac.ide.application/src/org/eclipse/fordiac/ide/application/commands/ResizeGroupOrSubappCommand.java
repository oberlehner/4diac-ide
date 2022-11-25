/*******************************************************************************
 * Copyright (c) 2022 Primetals Technologies Austria GmbH
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *   Fabio Gandolfi - initial implementation and/or documentation
 *******************************************************************************/
package org.eclipse.fordiac.ide.application.commands;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.eclipse.draw2d.IFigure;
import org.eclipse.draw2d.geometry.Rectangle;
import org.eclipse.fordiac.ide.application.editparts.AbstractContainerContentEditPart;
import org.eclipse.fordiac.ide.application.editparts.GroupContentEditPart;
import org.eclipse.fordiac.ide.application.editparts.IContainerEditPart;
import org.eclipse.fordiac.ide.application.editparts.UnfoldedSubappContentEditPart;
import org.eclipse.fordiac.ide.application.policies.ContainerContentLayoutPolicy;
import org.eclipse.fordiac.ide.model.ConnectionLayoutTagger;
import org.eclipse.fordiac.ide.model.commands.change.AbstractChangeContainerBoundsCommand;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetworkElement;
import org.eclipse.gef.EditPartViewer;
import org.eclipse.gef.GraphicalEditPart;
import org.eclipse.gef.commands.Command;

public class ResizeGroupOrSubappCommand extends Command implements ConnectionLayoutTagger {

	final GraphicalEditPart graphicalEditPart;
	List<FBNetworkElement> fbnetworkElements;

	Command cmdToExecuteBefore;

	List<AbstractChangeContainerBoundsCommand> changeContainerBoundsCommandList = new ArrayList<>();

	public ResizeGroupOrSubappCommand(final GraphicalEditPart groupOrSubAppContentGraphicalEditPart) {
		this.graphicalEditPart = groupOrSubAppContentGraphicalEditPart;
	}

	public ResizeGroupOrSubappCommand(final GraphicalEditPart groupOrSubAppContentGraphicalEditPart,
			final Command commandToExecuteBefore) {
		this(groupOrSubAppContentGraphicalEditPart);
		this.cmdToExecuteBefore = commandToExecuteBefore;
	}

	public ResizeGroupOrSubappCommand(final GraphicalEditPart groupOrSubAppContentGraphicalEditPart,
			final List<FBNetworkElement> fbnetworkElements) {
		this(groupOrSubAppContentGraphicalEditPart);
		this.fbnetworkElements = fbnetworkElements;
	}

	public ResizeGroupOrSubappCommand(final GraphicalEditPart groupOrSubAppContentGraphicalEditPart,
			final List<FBNetworkElement> fbnetworkElements, final Command commandToExecuteBefore) {
		this(groupOrSubAppContentGraphicalEditPart, fbnetworkElements);
		this.cmdToExecuteBefore = commandToExecuteBefore;
	}

	@Override
	public void execute() {
		if (cmdToExecuteBefore != null && cmdToExecuteBefore.canExecute()) {
			cmdToExecuteBefore.execute();
			getViewer().flush();
		} else {
			cmdToExecuteBefore = null;
		}

		addChangeContainerBoundCommand(checkAndCreateResizeCommand(getTargetContainerEP(), fbnetworkElements));

		GraphicalEditPart parent = findNestedGraphicalEditPart(getTargetContainerEP());
		while (parent != null) {
			this.fbnetworkElements = null;
			addChangeContainerBoundCommand(checkAndCreateResizeCommand(parent, null));
			parent = findNestedGraphicalEditPart(parent);
		}
	}

	@Override
	public boolean canExecute() {
		return graphicalEditPart != null;
	}

	@Override
	public void undo() {
		for (final AbstractChangeContainerBoundsCommand changeBoundscmd : changeContainerBoundsCommandList) {
			if (changeBoundscmd != null && changeBoundscmd.canUndo()) {
				changeBoundscmd.undo();
			}
		}
		if (cmdToExecuteBefore != null) {
			cmdToExecuteBefore.undo();
		}
	}

	@Override
	public boolean canUndo() {
		return (cmdToExecuteBefore != null && cmdToExecuteBefore.canUndo())
				|| !changeContainerBoundsCommandList.isEmpty();
	}

	@Override
	public void redo() {
		if (cmdToExecuteBefore != null) {
			cmdToExecuteBefore.redo();
		}
		for (int i = changeContainerBoundsCommandList.size() - 1; i >= 0; i--) {
			if (changeContainerBoundsCommandList.get(i) != null && changeContainerBoundsCommandList.get(i).canRedo()) {
				changeContainerBoundsCommandList.get(i).redo();
			}
		}
	}

	@Override
	public boolean canRedo() {
		return (cmdToExecuteBefore != null && cmdToExecuteBefore.canRedo())
				|| !changeContainerBoundsCommandList.isEmpty();
	}

	private GraphicalEditPart getTargetContainerEP() {
		if (graphicalEditPart instanceof IContainerEditPart) {
			return ((IContainerEditPart) graphicalEditPart).getContentEP();
		}
		return graphicalEditPart;
	}

	private static GraphicalEditPart findNestedGraphicalEditPart(final GraphicalEditPart child) {
		if ((child.getParent() != null)
				&& (child.getParent().getParent() instanceof AbstractContainerContentEditPart)) {
			return (GraphicalEditPart) child.getParent().getParent();
		}
		return null;
	}

	private AbstractChangeContainerBoundsCommand checkAndCreateResizeCommand(final GraphicalEditPart containerEP,
			List<FBNetworkElement> children) {
		getViewer().flush();

		if (children == null) {
			children = ((AbstractContainerContentEditPart) containerEP).getModel().getNetworkElements();
		}

		final Rectangle fbBounds = getFBBounds(children);
		final Rectangle containerBounds = ContainerContentLayoutPolicy.getContainerAreaBounds(containerEP);
		if (fbBounds != null && !containerBounds.contains(fbBounds)) {
			fbBounds.union(containerBounds);
			if (containerEP instanceof UnfoldedSubappContentEditPart) {
				return ContainerContentLayoutPolicy.createChangeBoundsCommand(
						((UnfoldedSubappContentEditPart) containerEP).getModel().getSubapp(), containerBounds,
						fbBounds);
			}
			if (containerEP instanceof GroupContentEditPart) {
				return ContainerContentLayoutPolicy.createChangeBoundsCommand(
						((GroupContentEditPart) containerEP).getModel().getGroup(), containerBounds, fbBounds);
			}
		}
		return null;
	}

	private void addChangeContainerBoundCommand(final AbstractChangeContainerBoundsCommand cmd) {
		if (cmd != null && cmd.canExecute()) {
			changeContainerBoundsCommandList.add(cmd);
			cmd.execute();
		}
	}

	private EditPartViewer getViewer() {
		return graphicalEditPart.getViewer();
	}

	private Rectangle getFBBounds(final List<FBNetworkElement> children) {
		final Map<Object, Object> editPartRegistry = getViewer().getEditPartRegistry();
		Rectangle fbBounds = null;

		for (final FBNetworkElement fbe : children) {
			final Object object = editPartRegistry.get(fbe);
			if (object instanceof GraphicalEditPart) {
				final IFigure fbFigure = ((GraphicalEditPart) object).getFigure();
				if (fbFigure != null) {
					if (fbBounds == null) {
						fbBounds = fbFigure.getBounds().getCopy();
					} else {
						fbBounds.union(fbFigure.getBounds().getCopy());
					}
				}
			}
			addValueBounds(fbBounds, fbe, editPartRegistry);
		}
		return fbBounds;
	}

	private static void addValueBounds(final Rectangle fbBounds, final FBNetworkElement fbe,
			final Map<Object, Object> editPartRegistry) {
		fbe.getInterface().getInputVars().stream().filter(Objects::nonNull)
		.map(ie -> editPartRegistry.get(ie.getValue())).filter(GraphicalEditPart.class::isInstance)
		.forEach(ep -> {
			final Rectangle pin = ((GraphicalEditPart) ep).getFigure().getBounds().getCopy();
			fbBounds.union(pin);
		});
	}

}
