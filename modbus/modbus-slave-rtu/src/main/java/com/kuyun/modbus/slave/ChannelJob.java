package com.kuyun.modbus.slave;

import com.digitalpetri.modbus.FunctionCode;
import com.digitalpetri.modbus.codec.ModbusRtuPayload;
import com.digitalpetri.modbus.requests.*;
import com.kuyun.eam.dao.model.EamEquipment;
import com.kuyun.eam.dao.model.EamSensor;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static com.digitalpetri.modbus.FunctionCode.*;

/**
 * Created by user on 2017-06-06.
 */
public class ChannelJob {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private Channel channel;
    private EamEquipment device;

    private final AtomicInteger transactionId = new AtomicInteger(0);
    private final ScheduledExecutorService scheduler = new ScheduledThreadPoolExecutor(1, new BasicThreadFactory.Builder().namingPattern("modbusRtu-schedule-pool-%d").daemon(true).build());



    public  ChannelJob(Channel channel, EamEquipment device){
        this.channel = channel;
        this.device = device;
    }



    public void run() {
        sendRequest();
    }

    class Tuple {
        Integer salveId;
        Integer functionCode;

        public Tuple(Integer salveId, Integer functionCode){
            this.salveId = salveId;
            this.functionCode = functionCode;
        }

        public Integer getSalveId() {
            return salveId;
        }

        public void setSalveId(Integer salveId) {
            this.salveId = salveId;
        }

        public Integer getFunctionCode() {
            return functionCode;
        }

        public void setFunctionCode(Integer functionCode) {
            this.functionCode = functionCode;
        }

        @Override
        public boolean equals(Object that) {
            if (this == that) {
                return true;
            }
            if (that == null) {
                return false;
            }
            if (getClass() != that.getClass()) {
                return false;
            }
            Tuple other = (Tuple) that;
            return (this.getSalveId() == null ? other.getSalveId() == null : this.getSalveId().equals(other.getSalveId()))
                    && (this.getFunctionCode() == null ? other.getFunctionCode() == null : this.getFunctionCode().equals(other.getFunctionCode()));
        }

        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((getSalveId() == null) ? 0 : getSalveId().hashCode());
            result = prime * result + ((getFunctionCode() == null) ? 0 : getFunctionCode().hashCode());
            return result;
        }
    }

    private void sendRequest(){
        Map<Tuple, List<EamSensor>> groupBySalveIdMap =
                getDevice().getSensors().stream().collect(Collectors.groupingBy(sensor -> new Tuple(sensor.getSalveId(), sensor.getFunctionCode())));

        for (Map.Entry<Tuple, List<EamSensor>> entry : groupBySalveIdMap.entrySet()){

            Tuple tuple = entry.getKey();
            List<EamSensor> sensors = entry.getValue();
            EamSensor sensor = sensors.get(0);
            int period = getPeriod();

            logger.info("SalveId="+tuple.getSalveId());
            logger.info("FunctionCode="+tuple.getFunctionCode());
            logger.info("sensor size="+ sensors.size());

            // combine to read request
            if (ReadCoils.equals(getFunctionCode(sensor)) ||
                    ReadDiscreteInputs.equals(getFunctionCode(sensor)) ||
                    ReadHoldingRegisters.equals(getFunctionCode(sensor)) ||
                    ReadInputRegisters.equals(getFunctionCode(sensor))) {

                sleep();

                scheduler.scheduleAtFixedRate(() -> sendRequest(sensors), 1,  period, TimeUnit.SECONDS);
            }else {
                for (EamSensor mySensor : sensors) {
                    sleep();
                    scheduler.scheduleAtFixedRate(() -> sendRequest(mySensor), 1, getPeriod(), TimeUnit.SECONDS);
                }
            }
        }
    }

    private int getPeriod(){
        int result = 20;
        if (getDevice().getModbusRtuPeriod() != null){
            result = getDevice().getModbusRtuPeriod();
        }else {
            logger.error("Device [ {} ] doesn't setting modbus rtu period, use 20 instead, please inform user to setting", getDevice().getEquipmentId());
        }
        return result;
    }

    private void sleep() {
        try{
            Thread.sleep(1000);
        }catch (InterruptedException ex){

        }
    }

    private void sendRequest(List<EamSensor> sensors){
        List<EamSensor> sortedSensors = sensors.stream().sorted((s1, s2) -> s1.getAddress().compareTo(s2.getAddress())).collect(Collectors.toList());

        ModbusRequest request = buildRequet(sortedSensors);
        EamSensor sensor = sortedSensors.get(0);

        if (request != null){
            String txId = String.valueOf((short) transactionId.incrementAndGet());
            logger.info("service Id: {}, transaction ID: {}, sensor function code: {}, address: {},  period: {}, ", getDevice().getEquipmentId(), txId, sensor.getFunctionCode(), sensor.getAddress(), getPeriod());
            getChannel().writeAndFlush(new ModbusRtuPayload(txId, sensor.getSalveId().shortValue(), request));
        }
    }


    private void sendRequest(EamSensor sensor){
        ModbusRequest request = buildRequet(sensor);
        if (request != null){
            String txId = String.valueOf((short) transactionId.incrementAndGet());
            logger.info("sevice Id: {}, transaction ID: {}, sensor function code: {}, address: {},  period: {}, ", getDevice().getEquipmentId(), txId, sensor.getFunctionCode(), sensor.getAddress(), getPeriod());
            getChannel().writeAndFlush(new ModbusRtuPayload(txId, sensor.getSalveId().shortValue(), request));
        }
    }

    public void writeData(EamSensor sensor){
        sendRequest(sensor);
    }


    private ModbusRequest buildRequet(EamSensor sensor){
        switch (getFunctionCode(sensor)){
            case ReadCoils:
                return buildReadCoils(sensor);

            case ReadDiscreteInputs:
                return buildReadDiscreteInputs(sensor);

            case ReadHoldingRegisters:
                return buildReadHoldingRegisters(sensor);

            case ReadInputRegisters:
                return buildReadInputRegisters(sensor);

            case WriteSingleCoil:
                return buildWriteSingleCoil(sensor);

            case WriteSingleRegister:
                return buildWriteSingleRegister(sensor);

            case WriteMultipleCoils:
                return buildWriteMultipleCoils(sensor);

            case WriteMultipleRegisters:
                return buildWriteMultipleRegisters(sensor);

            case MaskWriteRegister:
                return buildMaskWriteRegister(sensor);

            default:
                return null;
        }

    }

    private ModbusRequest buildRequet(List<EamSensor> sensors){
        switch (getFunctionCode(sensors.get(0))){
            case ReadCoils:
                return buildReadCoils(sensors);

            case ReadDiscreteInputs:
                return buildReadDiscreteInputs(sensors);

            case ReadHoldingRegisters:
                return buildReadHoldingRegisters(sensors);

            case ReadInputRegisters:
                return buildReadInputRegisters(sensors);
            default:
                return null;
        }
    }

    private FunctionCode getFunctionCode(EamSensor sensor){
        return FunctionCode.fromCode(sensor.getFunctionCode().intValue()).get();
    }

    private ModbusRequest buildReadCoils(EamSensor sensor){
        int address = sensor.getAddress();
        int quantity = sensor.getQuantity() != null ? sensor.getQuantity().intValue() : 1;

        return new ReadCoilsRequest(address, quantity);
    }

    private ModbusRequest buildReadCoils(List<EamSensor> sensors){
        int address = sensors.get(0).getAddress();
        int quantity = sensors.stream().collect(Collectors.summingInt(s -> s.getQuantity()));
        return new ReadCoilsRequest(address, quantity);
    }

    private ReadDiscreteInputsRequest buildReadDiscreteInputs(List<EamSensor> sensors) {
        int address = sensors.get(0).getAddress();
        int quantity = sensors.stream().collect(Collectors.summingInt(s -> s.getQuantity()));

        return new ReadDiscreteInputsRequest(address, quantity);
    }

    private ReadDiscreteInputsRequest buildReadDiscreteInputs(EamSensor sensor) {
        int address = sensor.getAddress();
        int quantity = sensor.getQuantity() != null ? sensor.getQuantity().intValue() : 1;

        return new ReadDiscreteInputsRequest(address, quantity);
    }

    private ReadHoldingRegistersRequest buildReadHoldingRegisters(List<EamSensor> sensors) {
        int address = sensors.get(0).getAddress();
        int quantity = sensors.stream().collect(Collectors.summingInt(s -> s.getQuantity()));

        return new ReadHoldingRegistersRequest(address, quantity);
    }

    private ReadHoldingRegistersRequest buildReadHoldingRegisters(EamSensor sensor) {
        int address = sensor.getAddress();
        int quantity = sensor.getQuantity() != null ? sensor.getQuantity().intValue() : 1;

        return new ReadHoldingRegistersRequest(address, quantity);
    }

    private ReadInputRegistersRequest buildReadInputRegisters(List<EamSensor> sensors) {
        int address = sensors.get(0).getAddress();
        int quantity = sensors.stream().collect(Collectors.summingInt(s -> s.getQuantity()));

        return new ReadInputRegistersRequest(address, quantity);
    }


    private ReadInputRegistersRequest buildReadInputRegisters(EamSensor sensor) {
        int address = sensor.getAddress();
        int quantity = sensor.getQuantity() != null ? sensor.getQuantity().intValue() : 1;

        return new ReadInputRegistersRequest(address, quantity);
    }

    private WriteSingleCoilRequest buildWriteSingleCoil(EamSensor sensor) {
        int address = sensor.getAddress();
        boolean value = sensor.getWriteNumber() == 0xFF00;

        return new WriteSingleCoilRequest(address, value);
    }

    private WriteSingleRegisterRequest buildWriteSingleRegister(EamSensor sensor) {
        int address = sensor.getAddress();
        int value = sensor.getWriteNumber();

        return new WriteSingleRegisterRequest(address, value);
    }

    private WriteMultipleCoilsRequest buildWriteMultipleCoils(EamSensor sensor) {
        int address = sensor.getAddress();
        int quantity = sensor.getQuantity() != null ? sensor.getQuantity().intValue() : 1;
        int byteCount = sensor.getWriteNumber();
        ByteBuf values = Unpooled.copyInt(byteCount).retain();

        return new WriteMultipleCoilsRequest(address, quantity, values);
    }

    private WriteMultipleRegistersRequest buildWriteMultipleRegisters(EamSensor sensor) {
        int address = sensor.getAddress();
        int quantity = sensor.getQuantity() != null ? sensor.getQuantity().intValue() : 1;
        int byteCount = sensor.getWriteNumber();
        ByteBuf values = Unpooled.copyInt(byteCount).retain();

        return new WriteMultipleRegistersRequest(address, quantity, values);
    }

    private MaskWriteRegisterRequest buildMaskWriteRegister(EamSensor sensor) {
        int address = sensor.getAddress();
        int andMask = 1;
        int orMask = 1;

        return new MaskWriteRegisterRequest(address, andMask, orMask);
    }

    public Channel getChannel() {
        return channel;
    }

    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public EamEquipment getDevice() {
        return this.device;
    }

    public void setDevice(EamEquipment device) {
        this.device = device;
    }

    public ScheduledExecutorService getSchedulerService(){
        return this.scheduler;
    }


    public static void main(String[] args) {

        ChannelJob job = new ChannelJob(null, buildEamEquipment());

        job.run();

    }

    private static EamEquipment buildEamEquipment(){
        EamEquipment device = new EamEquipment();
        device.setEquipmentId("1001");
        device.setSensors(buildSensors(device));

        return device;
    }

    private static List<EamSensor> buildSensors(EamEquipment device) {
        List<EamSensor> result = new ArrayList<>();
        EamSensor sensor = new EamSensor();
        sensor.setEquipmentModelPropertyId(1);
        sensor.setSalveId(1);
        sensor.setFunctionCode(3);
        sensor.setAddress(1);
        sensor.setQuantity(1);
        sensor.setPeriod(5);
        result.add(sensor);

        sensor = new EamSensor();
        sensor.setEquipmentModelPropertyId(2);
        sensor.setSalveId(1);
        sensor.setFunctionCode(3);
        sensor.setAddress(2);
        sensor.setQuantity(1);
        sensor.setPeriod(5);
        result.add(sensor);


        sensor = new EamSensor();
        sensor.setEquipmentModelPropertyId(3);
        sensor.setSalveId(1);
        sensor.setFunctionCode(3);
        sensor.setAddress(3);
        sensor.setQuantity(2);
        sensor.setPeriod(5);
        result.add(sensor);

        sensor = new EamSensor();
        sensor.setEquipmentModelPropertyId(4);
        sensor.setSalveId(1);
        sensor.setFunctionCode(4);
        sensor.setAddress(4);
        sensor.setQuantity(1);
        sensor.setPeriod(5);
        result.add(sensor);

        return result;
    }
}
