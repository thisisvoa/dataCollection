package com.kuyun.modbus.rpc.service.impl;

import com.kuyun.common.util.SpringContextUtil;
import com.kuyun.modbus.ModbusRtuServiceApplication;
import com.kuyun.modbus.slave.ChannelJob;
import com.kuyun.modbus.slave.ChannelManager;
import com.kuyun.modbus.rpc.api.ModbusSlaveRtuApiService;
import io.netty.channel.Channel;
import io.netty.channel.ChannelId;
import javafx.util.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created by user on 2017-08-18.
 */
public class ModbusRtuServiceImpl implements ModbusSlaveRtuApiService {
    private static Logger _log = LoggerFactory.getLogger(ModbusRtuServiceImpl.class);

    private ChannelManager channelManager = null;

    @Override
    public void startJob(String deviceId) {
        init();
        remove(deviceId);
    }

    @Override
    public void pauseJob(String deviceId) {
        init();
        remove(deviceId);

    }

    private void remove(String deviceId) {
        _log.info("Remove deviceId:[]", deviceId);

        Pair<ChannelId, ChannelJob> pair = channelManager.getChannelMap().get(deviceId);

        _log.info("Remove Channel ID:[]", pair != null ? pair.getKey() : null);
        _log.info("Remove ChannelJob :[]", pair != null ? pair.getValue() : null);

        if (pair != null){
            Channel channel = channelManager.getChannels().find(pair.getKey());
            if (channel != null){
                channelManager.remove(channel);
            }
        }
    }

    private void init() {
        if (channelManager == null){
            channelManager = SpringContextUtil.getBean(ChannelManager.class);
        }
    }

}
