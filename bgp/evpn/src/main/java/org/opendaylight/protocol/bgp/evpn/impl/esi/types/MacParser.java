/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */

package org.opendaylight.protocol.bgp.evpn.impl.esi.types;

import static org.opendaylight.protocol.bgp.evpn.impl.esi.types.EsiModelUtil.extractSystmeMac;
import static org.opendaylight.protocol.bgp.evpn.impl.esi.types.EsiModelUtil.extractUint24LD;

import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import org.opendaylight.protocol.util.ByteArray;
import org.opendaylight.protocol.util.ByteBufWriteUtil;
import org.opendaylight.yang.gen.v1.urn.ietf.params.xml.ns.yang.ietf.yang.types.rev130715.IetfYangUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.evpn.rev171213.EsiType;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.evpn.rev171213.Uint24;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.evpn.rev171213.esi.Esi;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.evpn.rev171213.esi.esi.MacAutoGeneratedCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.evpn.rev171213.esi.esi.MacAutoGeneratedCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.evpn.rev171213.esi.esi.mac.auto.generated._case.MacAutoGenerated;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.evpn.rev171213.esi.esi.mac.auto.generated._case.MacAutoGeneratedBuilder;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

final class MacParser extends AbstractEsiType {

    @Override
    public void serializeBody(final Esi esi, final ByteBuf body) {
        Preconditions.checkArgument(esi instanceof MacAutoGeneratedCase,
                "Unknown esi instance. Passed %s. Needed MacAutoGeneratedCase.", esi.getClass());
        final MacAutoGenerated macAuto = ((MacAutoGeneratedCase) esi).getMacAutoGenerated();
        body.writeBytes(IetfYangUtil.INSTANCE.bytesFor(macAuto.getSystemMacAddress()));
        ByteBufWriteUtil.writeMedium(macAuto.getLocalDiscriminator().getValue().intValue(), body);
    }

    @Override
    protected EsiType getType() {
        return EsiType.MacAutoGenerated;
    }

    @Override
    public Esi serializeEsi(final ContainerNode esi) {
        final MacAutoGeneratedBuilder builder = new MacAutoGeneratedBuilder();
        builder.setSystemMacAddress(extractSystmeMac(esi));
        builder.setLocalDiscriminator(extractUint24LD(esi));
        return new MacAutoGeneratedCaseBuilder().setMacAutoGenerated(builder.build()).build();
    }


    @Override
    public Esi parseEsi(final ByteBuf buffer) {
        final MacAutoGenerated t3 = new MacAutoGeneratedBuilder()
            .setSystemMacAddress(IetfYangUtil.INSTANCE.macAddressFor(ByteArray.readBytes(buffer, MAC_ADDRESS_LENGTH)))
            .setLocalDiscriminator(new Uint24(Long.valueOf(buffer.readUnsignedMedium()))).build();
        return new MacAutoGeneratedCaseBuilder().setMacAutoGenerated(t3).build();
    }
}
