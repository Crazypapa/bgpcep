/*
 * Copyright (c) 2013 Cisco Systems, Inc. and others.  All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 */
package org.opendaylight.protocol.bgp.parser.impl.message.update;

import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import org.opendaylight.protocol.bgp.parser.spi.AttributeParser;
import org.opendaylight.protocol.bgp.parser.spi.AttributeSerializer;
import org.opendaylight.protocol.bgp.parser.spi.AttributeUtil;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev171207.path.attributes.Attributes;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev171207.path.attributes.AttributesBuilder;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev171207.path.attributes.attributes.LocalPref;
import org.opendaylight.yang.gen.v1.urn.opendaylight.params.xml.ns.yang.bgp.message.rev171207.path.attributes.attributes.LocalPrefBuilder;
import org.opendaylight.yangtools.yang.binding.DataObject;

public final class LocalPreferenceAttributeParser implements AttributeParser,AttributeSerializer {

    public static final int TYPE = 5;

    @Override
    public void parseAttribute(final ByteBuf buffer, final AttributesBuilder builder) {
        builder.setLocalPref(new LocalPrefBuilder().setPref(buffer.readUnsignedInt()).build());
    }

    @Override
    public void serializeAttribute(final DataObject attribute, final ByteBuf byteAggregator) {
        Preconditions.checkArgument(attribute instanceof Attributes, "Attribute parameter is not a PathAttribute object.");
        final LocalPref lp = ((Attributes) attribute).getLocalPref();
        if (lp == null) {
            return;
        }
        AttributeUtil.formatAttribute(AttributeUtil.TRANSITIVE, TYPE, Unpooled.copyInt(lp.getPref().intValue()), byteAggregator);
    }
}
