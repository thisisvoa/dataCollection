/*
 * Copyright 2016 Kevin Herron
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kuyun.modbus.slave;

import com.digitalpetri.modbus.ModbusPdu;
import com.digitalpetri.modbus.UnsupportedPdu;
import com.digitalpetri.modbus.codec.ModbusPduDecoder;
import com.digitalpetri.modbus.codec.ModbusPduEncoder;
import com.digitalpetri.modbus.codec.ModbusRtuPayload;
import com.digitalpetri.modbus.codec.ModbusUtil;
import com.digitalpetri.modbus.responses.ExceptionResponse;
import com.kuyun.common.util.CommonUtil;
import com.kuyun.common.util.SpringContextUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static com.kuyun.common.util.CommonUtil.DEVICE_ID_LENGTH;

public class ModbusRtuCodec extends ByteToMessageCodec<ModbusRtuPayload> {

    private static final int LengthFieldIndex = 7;

    private final Logger logger = LoggerFactory.getLogger(getClass());

    private final ModbusPduEncoder encoder;
    private final ModbusPduDecoder decoder;

    private AtomicInteger index = new AtomicInteger(0);

    private ChannelManager channelManager = SpringContextUtil.getBean(ChannelManager.class);


    public ModbusRtuCodec(ModbusPduEncoder encoder, ModbusPduDecoder decoder) {
        this.encoder = encoder;
        this.decoder = decoder;
        logger.info("ModbusRtuCodec index : [{}]", index.incrementAndGet());

    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ModbusRtuPayload payload, ByteBuf buffer) throws Exception {
        buffer.writeByte(payload.getUnitId());

        buffer = encoder.encode(payload.getModbusPdu(), buffer);

        addCRC(buffer);

        logger.info("encode = [ {} ]", ByteBufUtil.hexDump(buffer));
    }

    private void addCRC(ByteBuf buffer){
        int startReaderIndex = buffer.readerIndex();
        int crc = ModbusUtil.calculateCRC(buffer);

        buffer.readerIndex(startReaderIndex);

        buffer.writeByte((byte) (0xff & (crc >> 8)));
        buffer.writeByte((byte) (0xff & crc));
    }


    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {

        channelManager.add(ctx, buffer);

        while (buffer.readableBytes() >= LengthFieldIndex) {
            try {
                int size = buffer.readableBytes();
                logger.info("before decode size = [ {} ] ", size);

                String deviceId = channelManager.getDeviceId(ctx);

                short unitId = getUnitId(buffer);

                logger.info("unitId = [ {} ]", unitId);

                logger.info("decode = [ {} ]", ByteBufUtil.hexDump(buffer));

                ModbusPdu modbusPdu = decoder.decode(buffer);

                size = buffer.readableBytes();
                logger.info("after decode size = [ {} ] ", size);
                buffer.clear();

                if (modbusPdu instanceof UnsupportedPdu) {
                }else if (modbusPdu instanceof ExceptionResponse){
                    out.add(new ModbusRtuPayload(deviceId, unitId, modbusPdu));
                }else {
                    out.add(new ModbusRtuPayload(deviceId, unitId, modbusPdu));
                }

            } catch (Throwable t) {
                buffer.clear();
                logger.error("error decoding header/pdu", t);
            }
        }
    }

    private short getUnitId(ByteBuf buffer){
        return buffer.readUnsignedByte();
    }

}
