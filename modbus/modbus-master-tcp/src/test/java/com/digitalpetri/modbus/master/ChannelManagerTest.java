package com.digitalpetri.modbus.master;

import java.util.concurrent.TimeUnit;

import org.testng.annotations.Test;

public class ChannelManagerTest {

    @Test
    public void testDisconnectWhenIdle() throws Exception {
        com.digitalpetri.modbus.master.ChannelManager channelManager = new com.digitalpetri.modbus.master.ChannelManager(null);

        channelManager.disconnect().get(1, TimeUnit.SECONDS);
    }

}