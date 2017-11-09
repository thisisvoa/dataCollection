package com.kuyun.common;

import com.kuyun.eam.common.constant.DataFormat;
import javafx.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.kuyun.eam.common.constant.BitOrder.*;
import static com.kuyun.eam.common.constant.DataFormat.*;

/**
 * Created by user on 2017-06-25.
 */
public class CommonUtil {
    private static final Logger logger = LoggerFactory.getLogger(CommonUtil.class);

    public static String covertHexTo16Signed(String hexData){
        return String.valueOf(Integer.parseUnsignedInt(hexData, 16));
    }

    public static String covertHexTo16UnSigned(String hexData){
        return String.valueOf(Integer.parseUnsignedInt(hexData, 16));
    }

    public static String covertHexTo32Signed(String hexData){
        return String.valueOf(Long.parseUnsignedLong(hexData, 16));
    }

    public static String covertHexTo32UnSigned(String hexData){
        return String.valueOf(Long.parseUnsignedLong(hexData, 16));
    }

    public static String covertHexTo32Float(String hexData){
        Long i = Long.parseLong(hexData, 16);
        Float f = Float.intBitsToFloat(i.intValue());
        return f.toString();
    }

    public static void main(String[] args) {
//        String hex = "002a";
//        // 16 signed
//        logger.info("hex [{}] = [{}]", hex, covertHexTo16Signed(hex));
//
//        // 16 unsigned
//        hex = "002a";
//        logger.info("hex [{}] = [{}]", hex, covertHexTo16UnSigned(hex));
//
//        // 32 signed
//        hex = "002a";
//        logger.info("hex [{}] = [{}]", hex, covertHexTo32Signed(hex));
//
//        //32 unsigned
//        hex = "002a";
//        logger.info("hex [{}] = [{}]", hex, covertHexTo32UnSigned(hex));
//
//        //32 float
//        hex = "422dc28f";
//        logger.info("hex [{}] = [{}]", hex, covertHexTo32Float(hex));
//
//        String allData = "002ac28f422d002d0000";
//
//        char[] chars = allData.toCharArray();
//
//
//        sliptData(allData);

        String hex = "00020003000400050006000700080009";
        int startAddress = 1;
        int currentAddress = 8;
        int index = (currentAddress - startAddress) * 4;
        int quantity = 1;

        logger.info(hex.substring(index, index + quantity *4));


    }

    private static void sliptData(String allData) {
        Pair data = covenrtHexToNumber(allData, SIGNED_16.getCode(), null);
        logger.info("allData [{}],  data format [{}] = [{}]", allData,  SIGNED_16.getCode(), data.getKey());

        data = covenrtHexToNumber((String) data.getValue(), FLOAT_32.getCode(), CDAB.getName());
        logger.info("allData [{}],  data format [{}] = [{}]", allData,  FLOAT_32.getCode(), data.getKey());

        data = covenrtHexToNumber((String) data.getValue(), SIGNED_16.getCode(), null);
        logger.info("allData [{}],  data format [{}] = [{}]", allData,  SIGNED_16.getCode(), data.getKey());

        data = covenrtHexToNumber((String) data.getValue(), SIGNED_16.getCode(), null);
        logger.info("allData [{}],  data format [{}] = [{}]", allData,  SIGNED_16.getCode(), data.getKey());


    }

    public static Pair<String, String> covenrtHexToNumber(String allData, String dataFormat, String bitOrder){

        int quantity = DataFormat.getQuantity(dataFormat);

        logger.info("response data =" + allData);
        logger.info("dataFormat =" + dataFormat);
        logger.info("quantity =" + quantity);

        String data = allData;
        if (allData.length() >= quantity*4){
            data = allData.substring(0, quantity*4);
        }

        if (SIGNED_16.getCode().matches(dataFormat) || UNSIGNED_16.getCode().matches(dataFormat)){
            //do nothing
        }else if (SIGNED_32.getCode().matches(dataFormat) || UNSIGNED_32.getCode().matches(dataFormat) ||
                FLOAT_32.getCode().matches(dataFormat)){

            char[] chars = data.toCharArray();
            String a = String.valueOf(chars[0]) + String.valueOf(chars[1]);
            String b = String.valueOf(chars[2]) + String.valueOf(chars[3]);
            String c = String.valueOf(chars[4]) + String.valueOf(chars[5]);
            String d = String.valueOf(chars[6]) + String.valueOf(chars[7]);


            if (ABCD.getName().matches(bitOrder)){
                //do nothing
            }else if (BADC.getName().matches(bitOrder)){
                data = b + a + d + c;
            }else if (CDAB.getName().matches(bitOrder)){
                data = c + d + a + b;
            }else if (DCBA.getName().matches(bitOrder)){
                data = d + c + b + a;
            }

        }

        allData = allData.substring(quantity*4 , allData.length());

        String result = null;
        if (SIGNED_16.getCode().matches(dataFormat)){
            result = covertHexTo16Signed(data);

        }else if (UNSIGNED_16.getCode().matches(dataFormat)){
            result = covertHexTo16UnSigned(data);

        }else if (SIGNED_32.getCode().matches(dataFormat)){
            result = covertHexTo32Signed(data);

        }else if (UNSIGNED_32.getCode().matches(dataFormat)){
            result = covertHexTo32UnSigned(data);

        }else if (FLOAT_32.getCode().matches(dataFormat)){
            result = covertHexTo32Float(data);

        }


        Pair pair = new Pair(result, allData);
        return pair;
    }

    public static String covenrtHexToNumber(int startAddress, int currentAddress, String allData, String dataFormat, String bitOrder){

        int quantity = DataFormat.getQuantity(dataFormat);

        logger.info("response data =" + allData);
        logger.info("dataFormat =" + dataFormat);
        logger.info("quantity =" + quantity);

        int index = (currentAddress - startAddress) * 4;
        logger.info("index =" + index);
        String data = allData.substring(index, index + quantity*4);
        logger.info("data =" + data);

        if (SIGNED_16.getCode().matches(dataFormat) || UNSIGNED_16.getCode().matches(dataFormat)){
            //do nothing
        }else if (SIGNED_32.getCode().matches(dataFormat) || UNSIGNED_32.getCode().matches(dataFormat) ||
                FLOAT_32.getCode().matches(dataFormat)){

            char[] chars = data.toCharArray();
            String a = String.valueOf(chars[0]) + String.valueOf(chars[1]);
            String b = String.valueOf(chars[2]) + String.valueOf(chars[3]);
            String c = String.valueOf(chars[4]) + String.valueOf(chars[5]);
            String d = String.valueOf(chars[6]) + String.valueOf(chars[7]);


            if (ABCD.getName().matches(bitOrder)){
                //do nothing
            }else if (BADC.getName().matches(bitOrder)){
                data = b + a + d + c;
            }else if (CDAB.getName().matches(bitOrder)){
                data = c + d + a + b;
            }else if (DCBA.getName().matches(bitOrder)){
                data = d + c + b + a;
            }

        }

//        allData = allData.substring(quantity*4 , allData.length());

        String result = null;
        if (SIGNED_16.getCode().matches(dataFormat)){
            result = covertHexTo16Signed(data);

        }else if (UNSIGNED_16.getCode().matches(dataFormat)){
            result = covertHexTo16UnSigned(data);

        }else if (SIGNED_32.getCode().matches(dataFormat)){
            result = covertHexTo32Signed(data);

        }else if (UNSIGNED_32.getCode().matches(dataFormat)){
            result = covertHexTo32UnSigned(data);

        }else if (FLOAT_32.getCode().matches(dataFormat)){
            result = covertHexTo32Float(data);

        }


        return result;
    }

}
