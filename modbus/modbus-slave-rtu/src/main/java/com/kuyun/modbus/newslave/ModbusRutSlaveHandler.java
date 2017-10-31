package com.kuyun.modbus.newslave;

import com.digitalpetri.modbus.codec.ModbusRtuPayload;
import com.digitalpetri.modbus.responses.*;
import com.kuyun.common.DeviceUtil;
import com.kuyun.common.util.SpringContextUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.ReferenceCountUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.net.UnknownHostException;
import java.util.concurrent.atomic.AtomicInteger;

import static io.netty.buffer.Unpooled.buffer;

/**
 * Created by user on 2017-06-01.
 */
@ChannelHandler.Sharable
public class ModbusRutSlaveHandler extends SimpleChannelInboundHandler<ModbusRtuPayload> {
    private static final Logger logger = LoggerFactory.getLogger(ModbusRutSlaveHandler.class);

//    private AtomicInteger index = new AtomicInteger(0);

//    public ModbusRutSlaveHandler(){
//        logger.info("ModbusRutSlaveHandler index : [{}]", index.incrementAndGet());
//    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
    	DataCollectionSession session = ctx.channel().attr(DataCollectionSession.SERVER_SESSION_KEY).get();
    	
    	if (session != null) {
    		session.destory();
    	}

    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, ModbusRtuPayload payload) throws Exception {
        onChannelRead(ctx, payload);
    }

    private void onChannelRead(ChannelHandlerContext ctx, ModbusRtuPayload payload) {
       
    	DataCollectionSession session = ctx.channel().attr(DataCollectionSession.SERVER_SESSION_KEY).get();
    	
    	ByteBuf buffer =  buffer(128);

        if (payload.getModbusPdu() instanceof ExceptionResponse){
            int exceptionCode = ((ExceptionResponse) payload.getModbusPdu()).getExceptionCode().getCode();
            logger.info("exceptionCode="+ exceptionCode);

        }else {
            switch (payload.getModbusPdu().getFunctionCode()) {
                case ReadCoils:
                    buffer = ((ReadCoilsResponse)payload.getModbusPdu()).getCoilStatus();
                    break;

                case ReadDiscreteInputs:
                    buffer = ((ReadDiscreteInputsResponse)payload.getModbusPdu()).getInputStatus();
                    break;

                case ReadHoldingRegisters:
                    buffer = ((ReadHoldingRegistersResponse)payload.getModbusPdu()).getRegisters();
                    break;

                case ReadInputRegisters:
                    buffer = ((ReadInputRegistersResponse)payload.getModbusPdu()).getRegisters();
                    break;

                case WriteSingleCoil:
                    break;

                case WriteSingleRegister:
                    break;

                case WriteMultipleCoils:
                    break;

                case WriteMultipleRegisters:
                    break;

                case MaskWriteRegister:
                    break;
                default:

                    break;
            }
        }

        handleResponse(session, payload, buffer);
    }

    private void handleResponse(DataCollectionSession session , ModbusRtuPayload payload, ByteBuf buffer){
        String data = ByteBufUtil.hexDump(buffer);
        ReferenceCountUtil.release(buffer);
        session.saveData(payload, data);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
