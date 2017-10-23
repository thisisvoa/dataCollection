package com.kuyun.modbus.slave;

import com.kuyun.common.DeviceUtil;
import com.kuyun.eam.common.constant.CollectStatus;
import com.kuyun.eam.dao.model.EamEquipment;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import javafx.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.kuyun.common.util.CommonUtil.DEVICE_ID_LENGTH;
import static com.kuyun.eam.common.constant.CollectStatus.WORKING;
import static io.netty.util.CharsetUtil.UTF_8;
import static org.apache.commons.lang.StringUtils.EMPTY;

/**
 * Created by user on 2017-06-06.
 */
public class ChannelManager {
    private static final Logger logger = LoggerFactory.getLogger(ChannelManager.class);

    final ChannelGroup channels = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE);
    final Map<String, Pair<ChannelId, ChannelJob>> channelMap = new ConcurrentHashMap<String, Pair<ChannelId, ChannelJob>>(2000);

    private AtomicInteger index = new AtomicInteger(0);
    public ChannelManager(){
        logger.info("ChannelManager index : [{}]", index.incrementAndGet());
    }


    @Autowired
    private DeviceUtil deviceUtil = null;

    public void add(Channel channel){
        channels.add(channel);
    }


    public void add(ChannelHandlerContext ctx, ByteBuf buffer){
        logger.info("response before decode = [ {} ]", ByteBufUtil.hexDump(buffer));

        handleDeviceId(ctx, buffer);

        handleHeartData(ctx, buffer);

    }

    private void handleDeviceId(ChannelHandlerContext ctx, ByteBuf buffer) {
        String deviceId = getDeviceId(buffer);

        logger.info("device Id = [ {} ], device length = [ {} ]", deviceId, StringUtils.length(deviceId));

        if (deviceUtil.isDevice(deviceId)){
            buffer.readerIndex(buffer.readableBytes());

            logger.info("buff write index  = [{}]", buffer.writerIndex());
            logger.info("buff read index  = [{}]", buffer.readerIndex());
            logger.info("buff readable size = [{}]", buffer.readableBytes());

            if (channelMap.get(deviceId) == null  || !isSameChannel(ctx, deviceId)){

                EamEquipment device = deviceUtil.getDevice(deviceId);

                logger.info("device Id from DB = [ {} ]", device.getEquipmentId());

                if (device != null){
                    deviceUtil.setOnline(device);


                    ChannelJob job = new ChannelJob(ctx.channel(), device);

                    Pair<ChannelId, ChannelJob> myPair = new Pair<>(ctx.channel().id(), job);
                    channelMap.put(deviceId, myPair);

                    logger.info("device Id from DB = [ {} ]", job.getDevice().getEquipmentId());

                    if (WORKING.getCode().equalsIgnoreCase(device.getCollectStatus())){
                        job.run();
                    }

                }
            }
        }else {
            logger.info("isDevice = [ {} ]", false);
            //buffer.resetReaderIndex();
        }
    }

    public void remove(Channel channel){
        logger.info("Remove Channel = [ {} ]", channel.id());
        channels.remove(channel);


        ChannelId channelId = channel.id();

        for (Map.Entry entry : channelMap.entrySet()){
            Pair<ChannelId, ChannelJob> myPair = (Pair<ChannelId, ChannelJob>)entry.getValue();
            logger.info("Remove Channel Id : [ {} ] = [{}]", myPair.getKey(), channel.id());
            if (myPair.getKey() == channelId){
                logger.info("Removed device Id : [ {} ]", entry.getKey());
                channelMap.remove(entry.getKey());
                ChannelJob job = myPair.getValue();
                job.getSchedulerService().shutdown();

                EamEquipment device = deviceUtil.getDevice(String.valueOf(entry.getKey()));

                if (device != null) {
                    deviceUtil.setOffline(device);
                }

                deviceUtil.remove((String) entry.getKey());
                break;
            }
        }
        channel.close();
        logger.info("channel closed: [ {} ]", channel.id());
    }

    private void handleHeartData(ChannelHandlerContext ctx, ByteBuf buffer){
        ChannelId channelId = ctx.channel().id();
        for (Map.Entry entry : channelMap.entrySet()){
            Pair<ChannelId, ChannelJob> myPair = (Pair<ChannelId, ChannelJob>)entry.getValue();
            if (myPair.getKey().equals(channelId)){
                logger.info(" handle heart data start !!");
                logger.info(" device Id = [{}]", String.valueOf(entry.getKey()));

                EamEquipment device = deviceUtil.getDevice(String.valueOf(entry.getKey()));
                if (device != null && isHearData(device, buffer)){
                    //心跳数据不处理
                    buffer.readerIndex(buffer.readableBytes());
                    return;
                }
            }
        }
    }

    private boolean isSameChannel(ChannelHandlerContext ctx, String deviceId){
        boolean result = false;
        Pair<ChannelId, ChannelJob> entry = channelMap.get(deviceId);
        if (ctx.channel().id().equals(entry.getKey())){
            result = true;
        }

        return result;
    }

    public String getDeviceId(ChannelHandlerContext ctx){
        String deviceId = EMPTY;
        ChannelId channelId = ctx.channel().id();
        for (Map.Entry entry : channelMap.entrySet()){
            Pair<ChannelId, ChannelJob> myPair = (Pair<ChannelId, ChannelJob>)entry.getValue();
            if (myPair.getKey() == channelId){
                deviceId = String.valueOf(entry.getKey());
                break;
            }
        }
        return deviceId;
    }
    public String getDeviceId(ByteBuf buffer){
        String deviceId = EMPTY;
        if (buffer.isReadable(DEVICE_ID_LENGTH)){
            deviceId = buffer.toString(UTF_8);
        }
        return deviceId;
    }

    private boolean isHearData(EamEquipment device, ByteBuf buffer){
        boolean result = false;
        String heartData = device.getHeartData();
        String data = buffer.toString(UTF_8);

        logger.info("Device Heart Data : [{}] equals [{}]", heartData, data);
        if (heartData != null && heartData.equalsIgnoreCase(data)){
            result = true;
        }

        return result;
    }


    public Map<String, Pair<ChannelId, ChannelJob>> getChannelMap(){
        return this.channelMap;
    }

    public ChannelGroup getChannels(){
        return this.channels;
    }
}
