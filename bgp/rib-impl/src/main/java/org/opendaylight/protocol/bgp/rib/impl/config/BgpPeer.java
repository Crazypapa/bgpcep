/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.protocol.bgp.rib.impl.config;

import static java.util.Objects.requireNonNull;
import static org.opendaylight.protocol.bgp.rib.impl.config.OpenConfigMappingUtil.getHoldTimer;
import static org.opendaylight.protocol.bgp.rib.impl.config.OpenConfigMappingUtil.getPeerAs;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import io.netty.util.concurrent.Future;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import javax.annotation.concurrent.GuardedBy;
import org.opendaylight.controller.sal.binding.api.RpcProviderRegistry;
import org.opendaylight.protocol.bgp.openconfig.spi.BGPTableTypeRegistryConsumer;
import org.opendaylight.protocol.bgp.parser.BgpExtendedMessageUtil;
import org.opendaylight.protocol.bgp.parser.spi.MultiprotocolCapabilitiesUtil;
import org.opendaylight.protocol.bgp.rib.impl.BGPPeer;
import org.opendaylight.protocol.bgp.rib.impl.spi.BGPDispatcher;
import org.opendaylight.protocol.bgp.rib.impl.spi.BGPPeerRegistry;
import org.opendaylight.protocol.bgp.rib.impl.spi.BGPSessionPreferences;
import org.opendaylight.protocol.bgp.rib.impl.spi.RIB;
import org.opendaylight.protocol.bgp.rib.spi.state.BGPPeerState;
import org.opendaylight.protocol.bgp.rib.spi.state.BGPPeerStateConsumer;
import org.opendaylight.protocol.concepts.KeyMapping;
import org.opendaylight.protocol.util.Ipv4Util;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.bgp.multiprotocol.rev151009.bgp.common.afi.safi.list.AfiSafi;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.bgp.rev151009.bgp.neighbor.group.AfiSafis;
import org.opendaylight.yang.gen.v1.http.openconfig.net.yang.bgp.rev151009.bgp.neighbors.Neighbor;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.inet.types.rev130715.IpAddress;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev171207.open.message.BgpParameters;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev171207.open.message.BgpParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev171207.open.message.bgp.parameters.OptionalCapabilities;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev171207.open.message.bgp.parameters.OptionalCapabilitiesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev171207.open.message.bgp.parameters.optional.capabilities.CParametersBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev171207.open.message.bgp.parameters.optional.capabilities.c.parameters.As4BytesCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.multiprotocol.rev171207.BgpTableType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.multiprotocol.rev171207.CParameters1;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.multiprotocol.rev171207.CParameters1Builder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.multiprotocol.rev171207.mp.capabilities.AddPathCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.multiprotocol.rev171207.mp.capabilities.MultiprotocolCapabilityBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.multiprotocol.rev171207.mp.capabilities.add.path.capability.AddressFamilies;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.rib.rev171207.rib.TablesKey;
import org.osgi.framework.ServiceRegistration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BgpPeer implements PeerBean, BGPPeerStateConsumer {

    private static final Logger LOG = LoggerFactory.getLogger(BgpPeer.class);

    private final RpcProviderRegistry rpcRegistry;
    @GuardedBy("this")
    private ServiceRegistration<?> serviceRegistration;
    @GuardedBy("this")
    private Neighbor currentConfiguration;
    @GuardedBy("this")
    private BgpPeerSingletonService bgpPeerSingletonService;

    public BgpPeer(final RpcProviderRegistry rpcRegistry) {
        this.rpcRegistry = rpcRegistry;
    }

    private static List<BgpParameters> getBgpParameters(final Neighbor neighbor, final RIB rib,
            final BGPTableTypeRegistryConsumer tableTypeRegistry) {
        final List<BgpParameters> tlvs = new ArrayList<>();
        final List<OptionalCapabilities> caps = new ArrayList<>();
        caps.add(new OptionalCapabilitiesBuilder().setCParameters(new CParametersBuilder().setAs4BytesCapability(
                new As4BytesCapabilityBuilder().setAsNumber(rib.getLocalAs()).build()).build()).build());

        caps.add(new OptionalCapabilitiesBuilder()
                .setCParameters(BgpExtendedMessageUtil.EXTENDED_MESSAGE_CAPABILITY).build());
        caps.add(new OptionalCapabilitiesBuilder()
                .setCParameters(MultiprotocolCapabilitiesUtil.RR_CAPABILITY).build());

        final List<AfiSafi> afiSafi = OpenConfigMappingUtil
                .getAfiSafiWithDefault(neighbor.getAfiSafis(), false);
        final List<AddressFamilies> addPathCapability = OpenConfigMappingUtil
                .toAddPathCapability(afiSafi, tableTypeRegistry);
        if (!addPathCapability.isEmpty()) {
            caps.add(new OptionalCapabilitiesBuilder()
                    .setCParameters(new CParametersBuilder().addAugmentation(CParameters1.class,
                            new CParameters1Builder().setAddPathCapability(
                                    new AddPathCapabilityBuilder()
                                            .setAddressFamilies(addPathCapability).build()).build()).build()).build());
        }

        final List<BgpTableType> tableTypes = OpenConfigMappingUtil.toTableTypes(afiSafi, tableTypeRegistry);
        for (final BgpTableType tableType : tableTypes) {
            if (!rib.getLocalTables().contains(tableType)) {
                LOG.info("RIB instance does not list {} " +
                        "in its local tables. Incoming data will be dropped.", tableType);
            }

            caps.add(new OptionalCapabilitiesBuilder().setCParameters(
                    new CParametersBuilder().addAugmentation(CParameters1.class,
                            new CParameters1Builder().setMultiprotocolCapability(
                                    new MultiprotocolCapabilityBuilder(tableType).build()).build()).build()).build());
        }
        tlvs.add(new BgpParametersBuilder().setOptionalCapabilities(caps).build());
        return tlvs;
    }

    private static Optional<byte[]> getPassword(final KeyMapping key) {
        if (key != null) {
            return Optional.of(Iterables.getOnlyElement(key.values()));
        }
        return Optional.absent();
    }

    @Override
    public synchronized void start(final RIB rib, final Neighbor neighbor,
            final BGPTableTypeRegistryConsumer tableTypeRegistry) {
        Preconditions.checkState(this.bgpPeerSingletonService == null,
                "Previous peer instance was not closed.");
        this.bgpPeerSingletonService = new BgpPeerSingletonService(rib, neighbor, tableTypeRegistry);
        this.currentConfiguration = neighbor;
    }

    @Override
    public synchronized void restart(final RIB rib, final BGPTableTypeRegistryConsumer tableTypeRegistry) {
        Preconditions.checkState(this.currentConfiguration != null);
        start(rib, this.currentConfiguration, tableTypeRegistry);
    }

    @Override
    public synchronized void close() {
        if (this.serviceRegistration != null) {
            this.serviceRegistration.unregister();
            this.serviceRegistration = null;
        }
    }

    @Override
    public synchronized void instantiateServiceInstance() {
        if (this.bgpPeerSingletonService != null) {
            this.bgpPeerSingletonService.instantiateServiceInstance();
        }
    }

    @Override
    public synchronized ListenableFuture<Void> closeServiceInstance() {
        if (this.bgpPeerSingletonService != null) {
            final ListenableFuture<Void> fut = this.bgpPeerSingletonService.closeServiceInstance();
            this.bgpPeerSingletonService = null;
            return fut;
        }
        return Futures.immediateFuture(null);
    }

    @Override
    public synchronized Boolean containsEqualConfiguration(final Neighbor neighbor) {
        if (this.currentConfiguration == null) {
            return false;
        }
        final AfiSafis actAfiSafi = this.currentConfiguration.getAfiSafis();
        final AfiSafis extAfiSafi = neighbor.getAfiSafis();
        final List<AfiSafi> actualSafi = actAfiSafi != null ? actAfiSafi.getAfiSafi() : Collections.emptyList();
        final List<AfiSafi> extSafi = extAfiSafi != null ? extAfiSafi.getAfiSafi() : Collections.emptyList();
        return actualSafi.containsAll(extSafi) && extSafi.containsAll(actualSafi)
                && Objects.equals(this.currentConfiguration.getConfig(), neighbor.getConfig())
                && Objects.equals(this.currentConfiguration.getNeighborAddress(), neighbor.getNeighborAddress())
                && Objects.equals(this.currentConfiguration.getAddPaths(), neighbor.getAddPaths())
                && Objects.equals(this.currentConfiguration.getApplyPolicy(), neighbor.getApplyPolicy())
                && Objects.equals(this.currentConfiguration.getAsPathOptions(), neighbor.getAsPathOptions())
                && Objects.equals(this.currentConfiguration.getEbgpMultihop(), neighbor.getEbgpMultihop())
                && Objects.equals(this.currentConfiguration.getGracefulRestart(), neighbor.getGracefulRestart())
                && Objects.equals(this.currentConfiguration.getErrorHandling(), neighbor.getErrorHandling())
                && Objects.equals(this.currentConfiguration.getLoggingOptions(), neighbor.getLoggingOptions())
                && Objects.equals(this.currentConfiguration.getRouteReflector(), neighbor.getRouteReflector())
                && Objects.equals(this.currentConfiguration.getState(), neighbor.getState())
                && Objects.equals(this.currentConfiguration.getTimers(), neighbor.getTimers())
                && Objects.equals(this.currentConfiguration.getTransport(), neighbor.getTransport());
    }

    @Override
    public synchronized BGPPeerState getPeerState() {
        if (this.bgpPeerSingletonService == null) {
            return null;
        }
        return this.bgpPeerSingletonService.getPeerState();
    }

    synchronized void setServiceRegistration(final ServiceRegistration<?> serviceRegistration) {
        this.serviceRegistration = serviceRegistration;
    }

    synchronized void removePeer(final BGPPeerRegistry bgpPeerRegistry) {
        if (BgpPeer.this.currentConfiguration != null) {
            bgpPeerRegistry.removePeer(BgpPeer.this.currentConfiguration.getNeighborAddress());
        }
    }

    private final class BgpPeerSingletonService implements BGPPeerStateConsumer {
        private final boolean activeConnection;
        private final BGPDispatcher dispatcher;
        private final InetSocketAddress inetAddress;
        private final int retryTimer;
        private final KeyMapping keys;
        private final BGPPeer bgpPeer;
        private final IpAddress neighborAddress;
        private final BGPSessionPreferences prefs;
        private Future<Void> connection;
        private boolean isServiceInstantiated;

        private BgpPeerSingletonService(final RIB rib, final Neighbor neighbor,
                final BGPTableTypeRegistryConsumer tableTypeRegistry) {
            this.neighborAddress = neighbor.getNeighborAddress();
            final AfiSafis afisSAfis = requireNonNull(neighbor.getAfiSafis());
            final Set<TablesKey> afiSafisAdvertized = OpenConfigMappingUtil
                    .toTableKey(afisSAfis.getAfiSafi(), tableTypeRegistry);
            this.bgpPeer = new BGPPeer(this.neighborAddress, rib,
                    OpenConfigMappingUtil.toPeerRole(neighbor), BgpPeer.this.rpcRegistry,
                    afiSafisAdvertized, Collections.emptySet());
            final List<BgpParameters> bgpParameters = getBgpParameters(neighbor, rib, tableTypeRegistry);
            final KeyMapping keyMapping = OpenConfigMappingUtil.getNeighborKey(neighbor);
            this.prefs = new BGPSessionPreferences(rib.getLocalAs(), getHoldTimer(neighbor), rib.getBgpIdentifier(),
                    getPeerAs(neighbor, rib), bgpParameters, getPassword(keyMapping));
            this.activeConnection = OpenConfigMappingUtil.isActive(neighbor);
            this.dispatcher = rib.getDispatcher();
            this.inetAddress = Ipv4Util.toInetSocketAddress(this.neighborAddress,
                    OpenConfigMappingUtil.getPort(neighbor));
            this.retryTimer = OpenConfigMappingUtil.getRetryTimer(neighbor);
            this.keys = keyMapping;
        }

        private synchronized void instantiateServiceInstance() {
            this.isServiceInstantiated = true;
            LOG.info("Peer instantiated {}", this.neighborAddress);
            this.bgpPeer.instantiateServiceInstance();
            this.dispatcher.getBGPPeerRegistry().addPeer(this.neighborAddress, this.bgpPeer, this.prefs);
            if (this.activeConnection) {
                this.connection = this.dispatcher.createReconnectingClient(this.inetAddress, this.retryTimer,
                        this.keys);
            }
        }

        private synchronized ListenableFuture<Void> closeServiceInstance() {
            if (!this.isServiceInstantiated) {
                LOG.info("Peer {} already closed", this.neighborAddress);
                return Futures.immediateFuture(null);
            }
            LOG.info("Close Peer {}", this.neighborAddress);
            this.isServiceInstantiated = false;
            if (this.connection != null) {
                this.connection.cancel(true);
                this.connection = null;
            }
            final ListenableFuture<Void> future = this.bgpPeer.close();
            removePeer(this.dispatcher.getBGPPeerRegistry());
            return future;
        }

        @Override
        public BGPPeerState getPeerState() {
            return this.bgpPeer.getPeerState();
        }
    }
}
