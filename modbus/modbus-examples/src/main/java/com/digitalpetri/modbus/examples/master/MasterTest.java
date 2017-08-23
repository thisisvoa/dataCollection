package com.digitalpetri.modbus.examples.master;

import com.digitalpetri.modbus.ModbusTimeoutException;
import com.digitalpetri.modbus.codec.Modbus;
import com.digitalpetri.modbus.master.ModbusTcpMaster;
import com.digitalpetri.modbus.master.ModbusTcpMasterConfig;
import com.digitalpetri.modbus.requests.ReadHoldingRegistersRequest;
import com.digitalpetri.modbus.responses.ModbusResponse;
import com.digitalpetri.modbus.responses.ReadHoldingRegistersResponse;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ConnectTimeoutException;
import io.netty.util.ReferenceCountUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Created by user on 5/8/2017.
 */
public class MasterTest {

    public static void main(String[] args) {
        ModbusTcpMasterConfig config = new ModbusTcpMasterConfig
//                .Builder("118.89.140.11")
                .Builder("localhost")
                .setPort(502)
                .setInstanceId("ModbosMaster1")
                .setTimeout(Duration.ofSeconds(1)).build();
        ModbusTcpMaster master = new ModbusTcpMaster(config);

        for (int i = 1; i < 2; i++){

            sendAndReceive(master, i);
        }

    }

    private static final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private static final Logger logger = LoggerFactory.getLogger(MasterTest.class);
    private static final AtomicInteger modbusTimeOutCount = new AtomicInteger(0);
    private static final AtomicInteger connectTimeOutCount = new AtomicInteger(0);
    private static final Integer MAX_COUNT = 5;

    private static  void sendAndReceive(ModbusTcpMaster master, int index) {

        CompletableFuture<ReadHoldingRegistersResponse> future =
                master.sendRequest(new ReadHoldingRegistersRequest(1, 3), index);


        future.whenCompleteAsync((response, ex) -> {
            if (response != null) {

                handleResponse(master, response);
                ReferenceCountUtil.release(response);
            } else {
                handleException(master, future, ex);
            }


            scheduler.schedule(() -> sendAndReceive(master, index), 1, TimeUnit.SECONDS);

//            logger.info("TimeOutCount=" + master.getTimeoutCounter().getCount());
//            logger.info("RequestCount=" + master.getRequestCounter().getCount());
//            logger.info("ResponseCount=" + master.getResponseCounter().getCount());

        }, Modbus.sharedExecutor());


    }

    private static boolean handleException(ModbusTcpMaster master, CompletableFuture<ReadHoldingRegistersResponse> future, Throwable ex) {
        if (ex instanceof ModbusTimeoutException){
            modbusTimeOutCount.incrementAndGet();
        }else if (ex instanceof ConnectTimeoutException){
            connectTimeOutCount.incrementAndGet();
        }

        logger.info("modbusTimeOutCount=" + modbusTimeOutCount.intValue());
        logger.info("connectTimeOutCount=" + connectTimeOutCount.intValue());


        if (modbusTimeOutCount.intValue() > MAX_COUNT || connectTimeOutCount.intValue() > MAX_COUNT){

            future.cancel(false);

            logger.info("future canceled");
            master.disconnect();
//            System.exit(0);
            return true;
        }

        logger.error("Completed exceptionally, message={}", ex.getMessage(), ex);
        return false;
    }

    private static void handleResponse(ModbusTcpMaster master, ModbusResponse response){
        logger.info("Client Address:" + master.getConfig().getAddress());
        logger.info("Unit Id:" + response.getUnitId());
        logger.info("getFunctionCode:" + response.getFunctionCode());
        logger.info("Response: " + ByteBufUtil.hexDump(((ReadHoldingRegistersResponse)response).getRegisters()));
    }
}
