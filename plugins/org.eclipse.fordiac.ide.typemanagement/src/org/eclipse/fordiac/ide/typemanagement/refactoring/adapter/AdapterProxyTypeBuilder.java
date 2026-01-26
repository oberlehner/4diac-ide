package org.eclipse.fordiac.ide.typemanagement.refactoring.adapter;

import org.eclipse.fordiac.ide.model.libraryElement.AdapterConnection;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterDeclaration;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterFB;
import org.eclipse.fordiac.ide.model.libraryElement.AdapterType;
import org.eclipse.fordiac.ide.model.libraryElement.CompositeFBType;
import org.eclipse.fordiac.ide.model.libraryElement.FBNetwork;
import org.eclipse.fordiac.ide.model.libraryElement.LibraryElementFactory;

public final class AdapterProxyTypeBuilder {
	private AdapterProxyTypeBuilder() {
	}

	public static CompositeFBType build(final AdapterType adapterType, final String typeName) {
		final LibraryElementFactory f = LibraryElementFactory.eINSTANCE;

		final CompositeFBType cfb = f.createCompositeFBType();
		cfb.setName(typeName);
		cfb.setInterfaceList(f.createInterfaceList());

		// external interface: mirror adapter (1 socket + 1 plug)
		final AdapterDeclaration socket = f.createAdapterDeclaration();
		socket.setName("SOCKET1");
		socket.setType(adapterType);
		socket.setIsInput(true); // socket == input side
		cfb.getInterfaceList().getSockets().add(socket);

		final AdapterDeclaration plug = f.createAdapterDeclaration();
		plug.setName("PLUG1");
		plug.setType(adapterType);
		plug.setIsInput(false); // plug == output side
		cfb.getInterfaceList().getPlugs().add(plug);

		// internal network with boundary AdapterFBs and a single adapter connection
		final FBNetwork net = f.createFBNetwork();
		cfb.setFBNetwork(net);

		final AdapterFB ifSocket = f.createAdapterFB();
		ifSocket.setName("SOCKET1"); // bound to boundary socket
		ifSocket.setAdapterDecl(socket);
		net.getNetworkElements().add(ifSocket);

		final AdapterFB ifPlug = f.createAdapterFB();
		ifPlug.setName("PLUG1"); // bound to boundary plug
		ifPlug.setAdapterDecl(plug);
		net.getNetworkElements().add(ifPlug);

		final AdapterConnection ac = f.createAdapterConnection();
		ac.setSource(ifSocket.getAdapterDecl());
		ac.setDestination(ifPlug.getAdapterDecl());
		net.getAdapterConnections().add(ac);

		return cfb;
	}
}
