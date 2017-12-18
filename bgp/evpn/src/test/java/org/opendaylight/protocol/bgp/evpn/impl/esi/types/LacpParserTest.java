/*
 * Copyright (c) 2016 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.protocol.bgp.evpn.impl.esi.types;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.opendaylight.protocol.bgp.evpn.impl.EvpnTestUtil.MAC;
import static org.opendaylight.protocol.bgp.evpn.impl.EvpnTestUtil.MAC_MODEL;
import static org.opendaylight.protocol.bgp.evpn.impl.EvpnTestUtil.PORT;
import static org.opendaylight.protocol.bgp.evpn.impl.EvpnTestUtil.VALUE_SIZE;
import static org.opendaylight.protocol.bgp.evpn.impl.EvpnTestUtil.createContBuilder;
import static org.opendaylight.protocol.bgp.evpn.impl.EvpnTestUtil.createValueBuilder;
import static org.opendaylight.protocol.bgp.evpn.impl.esi.types.EsiModelUtil.LACP_MAC_NID;
import static org.opendaylight.protocol.bgp.evpn.impl.esi.types.EsiModelUtil.PK_NID;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.junit.Before;
import org.junit.Test;
import org.opendaylight.protocol.util.ByteArray;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.evpn.rev171213.esi.Esi;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.evpn.rev171213.esi.esi.ArbitraryCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.evpn.rev171213.esi.esi.LacpAutoGeneratedCase;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.evpn.rev171213.esi.esi.LacpAutoGeneratedCaseBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.evpn.rev171213.esi.esi.lacp.auto.generated._case.LacpAutoGenerated;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.evpn.rev171213.esi.esi.lacp.auto.generated._case.LacpAutoGeneratedBuilder;
import org.opendaylight.yangtools.yang.data.api.YangInstanceIdentifier.NodeIdentifier;
import org.opendaylight.yangtools.yang.data.api.schema.ContainerNode;

public final class LacpParserTest {
    private static final byte[] VALUE = {(byte) 0xf2, (byte) 0x0c, (byte) 0xdd, (byte) 0x80, (byte) 0x9f, (byte) 0xf7,
        (byte) 0x02, (byte) 0x02, (byte) 0x00};
    private static final byte[] RESULT = {(byte) 0x01, (byte) 0xf2, (byte) 0x0c, (byte) 0xdd, (byte) 0x80, (byte) 0x9f,
        (byte) 0xf7, (byte) 0x02, (byte) 0x02, (byte) 0x00};
    private LacpParser parser;

    @Before
    public void setUp() {
        this.parser = new LacpParser();
    }

    @Test
    public void parserTest() {
        final ByteBuf buff = Unpooled.buffer(VALUE_SIZE);

        final LacpAutoGeneratedCase lanAuto = new LacpAutoGeneratedCaseBuilder()
                .setLacpAutoGenerated(new LacpAutoGeneratedBuilder()
                        .setCeLacpMacAddress(MAC).setCeLacpPortKey(PORT).build()).build();
        this.parser.serializeEsi(lanAuto, buff);
        assertArrayEquals(RESULT, ByteArray.getAllBytes(buff));

        final Esi acResult = this.parser.parseEsi(Unpooled.wrappedBuffer(VALUE));
        assertEquals(lanAuto, acResult);

        final ContainerNode cont = createContBuilder(new NodeIdentifier(LacpAutoGenerated.QNAME))
                .addChild(createValueBuilder(MAC_MODEL, LACP_MAC_NID).build())
                .addChild(createValueBuilder(PORT, PK_NID).build())
                .build();
        final Esi acmResult = this.parser.serializeEsi(cont);
        assertEquals(lanAuto, acmResult);
    }

    @Test(expected = IllegalArgumentException.class)
    public void wrongCaseTest() {
        this.parser.serializeEsi(new ArbitraryCaseBuilder().build(), null);
    }
}