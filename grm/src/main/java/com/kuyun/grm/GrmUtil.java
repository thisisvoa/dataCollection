package com.kuyun.grm;

import com.kuyun.common.DeviceUtil;
import com.kuyun.eam.dao.model.EamGrmVariable;
import com.kuyun.eam.dao.model.EamProductLine;
import com.kuyun.eam.vo.EamGrmVariableVO;
import com.kuyun.grm.common.Session;
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


    public void readData(final String productLineId) throws IOException {
        _logger.info("ProductLineId : " + productLineId);
        Session session = grmApi.getSession(productLineId);
        if (StringUtils.isNotEmpty(session.getSessionId())){
            _logger.info("sessionId : " + session.getSessionId());

            List<EamGrmVariable> grmVariables = getGrmVariables(productLineId);

            if (grmVariables != null){
                String requestData = buildRequestData(grmVariables);

                _logger.info("Request Data : " + requestData);

                if(!StringUtils.isEmpty(requestData)){
                    String data = grmApi.getData(session, requestData);
                    _logger.info("Response Data : " + data);

                    if (!StringUtils.isEmpty(productLineId)){
                        if (StringUtils.isEmpty(data)){
                            grmApi.cleanSession(productLineId);
                        }else {
                            List<Pair<EamGrmVariable, String>> pairs = buildPairData(grmVariables, data);

                            persistData(pairs, productLineId);
                        }
                    }
                }
            }
        }
    }

    private List<Pair<EamGrmVariable, String>> buildPairData(List<EamGrmVariable> grmVariables, String data) {
        List<Pair<EamGrmVariable, String>> result = new ArrayList<Pair<EamGrmVariable, String>>();
        String[] datas = data.split(SUFFIX);

        if (OK.equalsIgnoreCase(datas[0])){
            for (int i = 0; i < grmVariables.size(); i++) {
                EamGrmVariable variable = grmVariables.get(i);
                String value = datas[i + 2];
                Pair pair = new Pair(variable, value);
                result.add(pair);
            }
        }
        return result;
    }




    private void persistData(List<Pair<EamGrmVariable, String>> pairs, String productLineId){
        deviceUtil.getEamApiService().processData(pairs);
    }

    private List<EamGrmVariable> getGrmVariables(String productLineId){
        return deviceUtil.getEamApiService().getGrmVariables(productLineId);
    }

    private String buildRequestData(List<EamGrmVariable> grmVariables) throws UnsupportedEncodingException {
        StringBuilder data = new StringBuilder();
        for(EamGrmVariable variable : grmVariables){
            data.append(variable.getName()).append(SUFFIX);
        }

        data.insert(0, grmVariables.size() + SUFFIX);

        return data.substring(0, data.length() - 2);
    }

    public int getGrmPeriod(String productLineId){
        _logger.info("productLineId="+productLineId);
        int result = 10;
        EamProductLine productLine = deviceUtil.getProductLine(productLineId);
        if (productLine != null){
            result = productLine.getGrmPeriod();
            _logger.info("ProductLineId: {}, Grm Period: {}", productLineId, result);
        }
        return result;
    }

    public void setOffline(String productLineId){
        EamProductLine productLine = deviceUtil.getProductLine(productLineId);
        if (productLine != null){
            deviceUtil.setOffline(productLine);
        }
    }

    public void setOnline(String productLineId){
        EamProductLine productLine = deviceUtil.getProductLine(productLineId);
        if (productLine != null){
            deviceUtil.setOnline(productLine);
        }
    }

    public boolean isOffline(String productLineId){
        boolean result = false;
        EamProductLine productLine = deviceUtil.getProductLine(productLineId);
        if (productLine != null){
            result = !productLine.getIsOnline();
        }
        return result;
    }

    public String [] writeData(final String productLineId, final String requestData) throws IOException {
        String [] result = null;

        _logger.info("ProductLineId : " + productLineId);
        _logger.info("Write Data : " + requestData);
        grmApi.cleanSession(productLineId);
        Session session = grmApi.getSession(productLineId);
        _logger.info("sessionId : " + session.getSessionId());

        if (!StringUtils.isEmpty(session.getSessionId())){
            result = grmApi.writeData(session, requestData);
        }

        return result;
    }

    public List<EamGrmVariableVO> getAllVariable(String productLineId) throws IOException{
        List<EamGrmVariableVO> result = new ArrayList<>();
        _logger.info("ProductLineId : " + productLineId);
        grmApi.cleanSession(productLineId);
        Session session = grmApi.getSession(productLineId);
        _logger.info("sessionId : " + session.getSessionId());

        if (!StringUtils.isEmpty(session.getSessionId())){
            result = grmApi.getAllVariable(session);
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
