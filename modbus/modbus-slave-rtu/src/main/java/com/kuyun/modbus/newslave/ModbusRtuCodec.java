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

package com.kuyun.modbus.newslave;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.ModbusPdu;
import com.digitalpetri.modbus.UnsupportedPdu;
import com.digitalpetri.modbus.codec.ModbusPduDecoder;
import com.digitalpetri.modbus.codec.ModbusPduEncoder;
import com.digitalpetri.modbus.codec.ModbusRtuPayload;
import com.digitalpetri.modbus.codec.ModbusTcpPayload;
import com.digitalpetri.modbus.codec.ModbusUtil;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.ByteToMessageCodec;
import io.netty.handler.codec.DecoderException;

/**
 * Deal with RTU message package.
 * 
 * request message format :
 * 
 * (1 byte) --- (multiBytes according to operation) --- (2 bytes) UnitId --- PDU
 * --- CRC
 * 
 * response message format :
 * 
 * (1 byte) (1 byte) (multiBytes) (2 bytes) UnitId --- Operation --- Operation
 * Result --- CRC --- --- Length + Data/ Fix Length Data ---
 * 
 * @author youjun
 *
 */
public class ModbusRtuCodec extends ByteToMessageCodec<ModbusRtuPayload> {

	private static final int OperationFieldIndex = 1;
	private static final int LengthFieldIndex = 2; // for some operation
	private static final int MinMessageSize = 5; // at least 5 bytes to generate a valid Rtu Message, 5 Bytes are for
													// exception message

	private final Logger logger = LoggerFactory.getLogger(getClass());

	private final ModbusPduEncoder encoder;
	private final ModbusPduDecoder decoder;

	private short transactionId = 0;

	public ModbusRtuCodec(ModbusPduEncoder encoder, ModbusPduDecoder decoder) {
		this.encoder = encoder;
		this.decoder = decoder;
	}

	@Override
	protected void encode(ChannelHandlerContext ctx, ModbusRtuPayload payload, ByteBuf buffer) throws Exception {
		buffer.writeByte(payload.getUnitId());

		buffer = encoder.encode(payload.getModbusPdu(), buffer);

		writeCRC(buffer);

		logger.info("encode = [ {} ]", ByteBufUtil.hexDump(buffer));
	}

	private void writeCRC(ByteBuf buffer) {
		int startReaderIndex = buffer.readerIndex();
		int crc = ModbusUtil.calculateCRC(buffer);

		buffer.readerIndex(startReaderIndex);

		buffer.writeByte((byte) (0xff & (crc >> 8)));
		buffer.writeByte((byte) (0xff & crc));
	}

	/**
	 * 
	 * we have ten cases when decode the RTU message package. please refer to the
	 * encode* function in the class
	 * {@link com.digitalpetri.modbus.codec.ModbusResponseEncoder}
	 * 
	 * 
	 */

	@Override
	protected void decode(ChannelHandlerContext ctx, ByteBuf buffer, List<Object> out) throws Exception {
		int startIndex = buffer.readerIndex();
		DataCollectionSession session = ctx.channel().attr(DataCollectionSession.SERVER_SESSION_KEY).get();
		if (session == null) {
			// should never happen.
			logger.error("no session attached when deal with message");
			ctx.close();
		}

		try {
			while (buffer.readableBytes() >= MinMessageSize
					&& buffer.readableBytes() >= getMessagelength(buffer, startIndex)
					|| session.getHeartBeat() != null && buffer.readableBytes() >= session.getHeartBeat().length) {

				// skip the heart beat message
				if (filterHeartBeat(session.getHeartBeat(), session.getHeartBeatStr(), buffer)) {
					continue;
				}

				short unitId = buffer.readUnsignedByte();

				ModbusPdu modbusPdu = decoder.decode(buffer);

				int CRC = buffer.readUnsignedShort(); // for time being, just discard the CRC validation.

				if (modbusPdu instanceof UnsupportedPdu) {
					// Advance past any bytes we should have read but didn't...
					int endIndex = startIndex + getLength(buffer, startIndex) + 6;
					buffer.readerIndex(endIndex);
				}

				out.add(new ModbusTcpPayload(++transactionId, unitId, modbusPdu));

				startIndex = buffer.readerIndex();

			}
		} catch (Throwable t) {
			throw new Exception("error decoding Rtu", t);
		}

	}

	private int getLength(ByteBuf in, int startIndex) {
		return in.getUnsignedByte(startIndex + LengthFieldIndex);
	}

	private short getOperation(ByteBuf in, int startIndex) {
		return in.getUnsignedByte(startIndex + OperationFieldIndex);
	}

	private int getMessagelength(ByteBuf in, int startIndex) {

		int code = getOperation(in, startIndex);

		if (FunctionCode.isExceptionCode(code)) {
			// unitid , operationcode, errorcode, CRC
			return 1 + 1 + 1 + 2;
		}

		FunctionCode functionCode = FunctionCode.fromCode(code)
				.orElseThrow(() -> new DecoderException("invalid function code: " + code));

		switch (functionCode) {
		case ReadCoils:
		case ReadDiscreteInputs:
		case ReadHoldingRegisters:
		case ReadInputRegisters:
			int length = getLength(in, startIndex);
			// unitid, operationcode, length, length value, CRC
			return 1 + 1 + 1 + length + 2;
		case WriteSingleCoil:
		case WriteSingleRegister:
		case WriteMultipleCoils:
		case WriteMultipleRegisters:
			// unit id, operation code, fix length, CRC
			return 1 + 1 + 4 + 2;
		case MaskWriteRegister:
			// unit id, operation code, fix length, CRC
			return 1 + 1 + 6 + 2;

		default:
			return 0;
		}
	}

	private boolean filterHeartBeat(byte[] heartBeat, String heartBeatStr, ByteBuf buffer) {

		boolean filtered = false;

		if (heartBeat != null) {
			ByteBuf temp = buffer.slice(buffer.readerIndex(), heartBeat.length);

			if (heartBeatStr.equals(temp.toString(StandardCharsets.UTF_8))) {
				logger.info("Device Heart Data : [{}] recieved", heartBeatStr);
				buffer.skipBytes(heartBeat.length);
				filtered = true;
			}
		}

		return filtered;
	}

	@Override
	public void close(ChannelHandlerContext ctx, ChannelPromise promise) throws Exception {
		DataCollectionSession session = ctx.channel().attr(DataCollectionSession.SERVER_SESSION_KEY).get();

		if (session != null) {
			session.destory();
		}
		super.close(ctx, promise);
	}

}
