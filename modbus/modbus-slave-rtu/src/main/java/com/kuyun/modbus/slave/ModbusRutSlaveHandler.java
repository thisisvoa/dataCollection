package com.kuyun.modbus.slave;

import com.digitalpetri.modbus.codec.ModbusRtuPayload;
import com.digitalpetri.modbus.responses.*;
import com.kuyun.common.DeviceUtil;
import com.kuyun.common.util.SpringContextUtil;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
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

    private AtomicInteger index = new AtomicInteger(0);

    public ModbusRutSlaveHandler(){
        logger.info("ModbusRutSlaveHandler index : [{}]", index.incrementAndGet());
    }

    private ChannelManager channelManager = SpringContextUtil.getBean(ChannelManager.class);

    private DeviceUtil deviceUtil = SpringContextUtil.getBean(DeviceUtil.class);

    @Override
    public void channelActive(final ChannelHandlerContext ctx) throws UnknownHostException {
        channelManager.add(ctx.channel());
        // handle client on line
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        channelManager.remove(ctx.channel());
        // handle client off line
    }

    @Override
    public void channelRead0(ChannelHandlerContext ctx, ModbusRtuPayload payload) throws Exception {
        onChannelRead(ctx, payload);
    }

    private void onChannelRead(ChannelHandlerContext ctx, ModbusRtuPayload payload) {
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

        handleResponse(ctx, payload, buffer);
    }

    private void handleResponse(ChannelHandlerContext ctx, ModbusRtuPayload payload, ByteBuf buffer){
        String deviceId = channelManager.getDeviceId(ctx);
        int unitId = payload.getUnitId();
        String data = ByteBufUtil.hexDump(buffer);

        logger.info("device Id = [ {} ]", deviceId);
        logger.info("unit Id = [ {} ]", unitId);
        logger.info("client response = [ {} ]", data);

        if (!StringUtils.isEmpty(data)){
            deviceUtil.persistDB(deviceId, unitId, data);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}
