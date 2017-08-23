package com.kuyun.grm;

import com.kuyun.common.DeviceUtil;
import com.kuyun.eam.dao.model.EamEquipment;
import com.kuyun.eam.dao.model.EamSensor;
import com.kuyun.eam.dao.model.EamSensorData;
import javafx.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.kuyun.grm.common.Action.READ;
import static com.kuyun.grm.common.Constant.OK;
import static java.util.Comparator.comparing;

/**
 * Created by user on 2017-07-19.
 */
@Component
public class GrmUtil {
    private static Logger _logger = LoggerFactory.getLogger(GrmUtil.class);

    @Autowired
    DeviceUtil deviceUtil = null;

    @Autowired
    GrmApi grmApi = null;

    private String SUFFIX = "\r\n";

    public void readData(String deviceId) throws IOException {
        String sessionId = grmApi.getSessionId(deviceId);
        _logger.info("sessionId : " + sessionId);

        List<EamSensor> sensors = getSensors(deviceId);


        String requestData = buildRequestData(sensors);

        _logger.info("Request Data : " + requestData);

        if(!StringUtils.isEmpty(sessionId) && !StringUtils.isEmpty(requestData)){
            String data = grmApi.getData(sessionId, requestData);
            _logger.info("Response Data : " + data);

            if (StringUtils.isEmpty(data)){
                grmApi.cleanSessionId(deviceId);
            }else {
                List<Pair<EamSensor, String>> pairs = buildPairData(sensors, data);

                persistSensorData(pairs);
            }

        }
    }

    private List<Pair<EamSensor, String>> buildPairData(List<EamSensor> sensors, String data) {
        List<Pair<EamSensor, String>> result = new ArrayList<Pair<EamSensor, String>>();
        String[] datas = data.split(SUFFIX);

        if (OK.equalsIgnoreCase(datas[0])){
            for (int i = 0; i < sensors.size(); i++) {
                EamSensor sensor = sensors.get(i);
                String sensorData = datas[i + 2];
                Pair pair = new Pair(sensor, sensorData);
                result.add(pair);
            }
        }
        return result;
    }

    private void persistSensorData(List<Pair<EamSensor, String>> pairs){
        for(Pair<EamSensor, String> pair : pairs){
            EamSensorData result = deviceUtil.createSensorData(pair.getKey().getSensorId(), pair.getValue());
            deviceUtil.getEamSensorDataService().insertSelective(result);
        }

    }

    private List<EamSensor> getSensors(String deviceId){
        List<EamSensor> result = new ArrayList<EamSensor>();
        List<EamSensor> sensors = deviceUtil.getSensors(deviceId);
        return sensors.stream().filter(sensor -> READ.getCode().equalsIgnoreCase(sensor.getGrmAction()))     
                               .sorted(comparing(EamSensor::getGrmVariableOrder)).collect(Collectors.toList());

    }

    private String buildRequestData(List<EamSensor> sensors) throws UnsupportedEncodingException {
        StringBuilder data = new StringBuilder();
        for(EamSensor sensor : sensors){
            data.append(sensor.getGrmVariable()).append(SUFFIX);
        }

        data.insert(0, sensors.size() + SUFFIX);

        return data.substring(0, data.length() - 2);
    }

    public int getGrmPeriod(String deviceId){
        int result = 10;
        EamEquipment device = deviceUtil.getDevice(deviceId);
        if (device != null){
            result = device.getGrmPeriod();
        }
        return result;
    }

//    public static void main(String[] args) throws IOException {
//
//        ApplicationContext ctx = new ClassPathXmlApplicationContext("classpath:/spring/*.xml");
//        GrmUtil aplication = ctx.getBean(GrmUtil.class);
//
//        aplication.readData("ucw09FVYXDIF9VED");
//    }

}
