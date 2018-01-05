package com.kuyun.grm;

import com.kuyun.common.DeviceUtil;
import com.kuyun.eam.dao.model.EamEquipment;
import com.kuyun.eam.dao.model.EamGrmEquipmentVariable;
import com.kuyun.eam.vo.EamGrmEquipmentVariableVO;
import com.kuyun.grm.common.Session;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.message.BasicNameValuePair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static com.kuyun.grm.common.Constant.SERVER_URL;

/**
 * Created by user on 2017-07-16.
 */
public class GrmApi {
    private final Logger _logger = LoggerFactory.getLogger(GrmApi.class);

    @Autowired
    DeviceUtil deviceUtil = null;

    private static String PATH = "/exlog";
    private static String R = "R";
    private static String E = "E";
    private static String NTRPGC = "NTRPGC";

    public String getServerUrl(){
        return SERVER_URL + PATH;
    }

    public String getServerUrl(String sessionId, String op){
        return SERVER_URL + "/exdata?OP="+ op + "&SID=" + sessionId;
    }

    public String getWriteDataServerUrl(String sessionId){
        return SERVER_URL + "/exdata?OP=W" + "&SID=" + sessionId;
    }

    private Map<String, String> map = new ConcurrentHashMap<>(1000);


    public String getSessionId(String deviceId) throws IOException {
        String sessionId = map.get(deviceId);
        if (StringUtils.isEmpty(sessionId)){
            EamEquipment device = deviceUtil.getDevice(deviceId);
            if (device != null){
                String grm = device.getGrm();
                String password = device.getGrmPassword();

                Session session = getSessionId(grm, password);
                if (!StringUtils.isEmpty(session.getSessionId())){
                    sessionId = session.getSessionId();
                    map.put(deviceId, sessionId);
                }
            }
        }
        return sessionId;
    }

    public void cleanSessionId(String deviceId){
        map.remove(deviceId);
    }

    public Session getSessionId(String grm, String password) throws IOException {
        Session result = new Session();

        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(getServerUrl());
        httpPost.setHeader("Content-Type", "text/plain;charset=UTF-8");

        List<NameValuePair> nvps = new ArrayList<NameValuePair>();
        nvps.add(new BasicNameValuePair("GRM", grm));
        nvps.add(new BasicNameValuePair("PASS", password));
        httpPost.setEntity(new UrlEncodedFormEntity(nvps, "utf-8"));

        HttpResponse response = httpClient.execute(httpPost);
        int responseCode = response.getStatusLine().getStatusCode();
        if(responseCode != 200){
            _logger.error("send GRM message error, the responseCode is " + responseCode);
            return result;
        }

        String data = IOUtils.toString(response.getEntity().getContent());

        String [] rows = data.split("\r\n");
        if (rows != null && rows.length == 3){
            if ("OK".equalsIgnoreCase(rows[0])){
                result.setAddress(rows[1]);
                result.setSessionId(rows[2].substring(4));
            }
        }

        return result;
    }

    /**
     * requestData: 第一行是变量个数,以后每一行是一个变量名(示例有 3 个变量)。行与行之间的分隔符是回 车换行\r\n
     * example:
     * 3
     * 变 量名 1
     * 变 量名 2
     * 变 量名 3
     * @param sessionId
     * @param requestData
     * @return
     */

    public String getData(String sessionId, String requestData) throws IOException {
        String result = new String();

        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(getServerUrl(sessionId, R));
        httpPost.setHeader("Content-Type", "text/plain;charset=UTF-8");
        httpPost.setEntity(new StringEntity(requestData, "UTF-8"));

        HttpResponse response = httpClient.execute(httpPost);
        int responseCode = response.getStatusLine().getStatusCode();
        if(responseCode != 200){
            _logger.error("send GRM message error, the responseCode is " + responseCode);
            return result;
        }

        String data = IOUtils.toString(response.getEntity().getContent());

        String [] rows = data.split("\r\n");
        if (rows != null){
            if ("OK".equalsIgnoreCase(rows[0])){
                result = data;
            }
        }

        return result;
    }

    public String getRepeatData(String sessionId) throws IOException {
        String result = new String();

        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(getServerUrl(sessionId, R));
        httpPost.setHeader("Content-Type", "text/plain;charset=UTF-8");

        HttpResponse response = httpClient.execute(httpPost);
        int responseCode = response.getStatusLine().getStatusCode();
        if(responseCode != 200){
            _logger.error("send GRM message error, the responseCode is " + responseCode);
            return result;
        }

        String data = IOUtils.toString(response.getEntity().getContent());

        String [] rows = data.split("\r\n");
        if (rows != null){
            if ("OK".equalsIgnoreCase(rows[0])){
                result = data;
            }
        }

        return result;
    }

    /**
     *
     * @param sessionId
     * @param requestData : 第一行是变量个数，以后每一行依次是第一个变量名，第一个变量值，第二个变量名，第 二个变量值……
     *                    2
     *                    变 量名 1
     *                    变 量值 1
     *                    变 量名 2
     *                    变 量值 2
     * @return
     * @throws IOException
     */
    public String [] writeData(String sessionId, String requestData) throws IOException {
        String [] result = null;

        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(getWriteDataServerUrl(sessionId));
        httpPost.setHeader("Content-Type", "text/plain;charset=UTF-8");
        httpPost.setEntity(new StringEntity(requestData));

        HttpResponse response = httpClient.execute(httpPost);
        int responseCode = response.getStatusLine().getStatusCode();
        if(responseCode != 200){
            _logger.error("send GRM message error, the responseCode is " + responseCode);
            return result;
        }

        String data = IOUtils.toString(response.getEntity().getContent());

        result = data.split("\r\n");
//        if (rows != null){
//            if ("OK".equalsIgnoreCase(rows[0])){
//                result = data;
//            }
//        }

        return result;
    }

    /**
     *
     * @param sessionId
     * 只有一行，是一个字符串，指定返回的变量信息格式参数。
     * 可以是下面 6 个选项字母的任 意组合：
     *  N 是变量名，返回值为字符串
     *  T 是变量类型，返回值为 B/I/F，分别代表 开关量/整数/浮点数
     *  R 是变量读写属性，返回值为 R/W，分别代表 只读/可读写
     *  P 是网络权限，返回值为 0/1/2，分别代表 低/中/高
     *  G 是变量组名，返回值为字符串。如果有两级变量组，中间是.分隔。
     *  C 是 Web 变量描述，返回值为字符串 如果只枚举变量名，内容就是 N；
     * 如果枚举变量名，类型，读写属性，网络权限（常用的选项）， 内 容 就 是 NTRP 如果枚举变量的所有信息，内容就是 NTRPGC
     * @return
     */
    public List<EamGrmEquipmentVariableVO> getAllVariable(String sessionId) throws IOException {
        List<EamGrmEquipmentVariableVO> result = new ArrayList<EamGrmEquipmentVariableVO>();

        CloseableHttpClient httpClient = HttpClientBuilder.create().build();
        HttpPost httpPost = new HttpPost(getServerUrl(sessionId, E));
        httpPost.setHeader("Content-Type", "text/plain;charset=UTF-8");
        httpPost.setEntity(new StringEntity(NTRPGC, "UTF-8"));

        HttpResponse response = httpClient.execute(httpPost);
        int responseCode = response.getStatusLine().getStatusCode();
        if(responseCode != 200){
            _logger.error("send GRM message error, the responseCode is " + responseCode);
            return result;
        }

        String data = IOUtils.toString(response.getEntity().getContent(), "UTF-8");
        _logger.info("Session ID : {}, All Variables : {}", sessionId, data);
        String [] rows = data.split("\r\n");
        if (rows != null){
            if ("OK".equalsIgnoreCase(rows[0])){
                for (int i = 2; i < rows.length; i++){
                    String row = rows[i];
                    if (!StringUtils.startsWith(row, "$")){
                        String[] variable = row.split(",");
                        if (variable != null && variable.length >= 4){
                            EamGrmEquipmentVariableVO grmEquipmentVariable = new EamGrmEquipmentVariableVO();
                            grmEquipmentVariable.setName(variable[0]);
                            grmEquipmentVariable.setType(variable[1]);
                            grmEquipmentVariable.setAttribute(variable[2]);
                            grmEquipmentVariable.setNetworkPermisstion(variable[3]);
                            result.add(grmEquipmentVariable);
                        }
                    }
                }
            }
        }

        return result;
    }
}
