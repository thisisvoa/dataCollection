package com.kuyun.datagather;

import io.netty.channel.Channel;

public interface ProtocolChannelInitializer {

	public void initChannel(Channel ch);

}
