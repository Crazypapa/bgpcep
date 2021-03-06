/*
 * Copyright (c) 2018 AT&T Intellectual Property. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.protocol.bgp.mode.impl;

import org.opendaylight.protocol.bgp.rib.spi.Peer;
import org.opendaylight.protocol.bgp.rib.spi.policy.BGPRouteEntryExportParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.rib.rev171207.PeerId;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.rib.rev171207.PeerRole;

public final class BGPRouteEntryExportParametersImpl implements BGPRouteEntryExportParameters {
    private final Peer fromPeer;
    private final Peer toPeer;

    public BGPRouteEntryExportParametersImpl(final Peer fromPeer, final Peer toPeer) {
        this.fromPeer = fromPeer;
        this.toPeer = toPeer;
    }

    @Override
    public PeerRole getFromPeerRole() {
        return this.fromPeer.getRole();
    }

    @Override
    public PeerId getFromPeerId() {
        return this.fromPeer.getPeerId();
    }

    @Override
    public PeerRole getToPeerRole() {
        return this.toPeer.getRole();
    }

    @Override
    public PeerId getToPeerId() {
        return this.toPeer.getPeerId();
    }
}
