package com.kuyun.modbus.slave;

import java.math.BigDecimal;

/**
 * Created by user on 2017-06-24.
 */
public class HexToDoubleTest {

    public static void main(String[] args) {
//        String hexString = "ea02";
//        long longBits = Long.valueOf(hexString,32).longValue();
//        double doubleValue = Double.longBitsToDouble(longBits);
//        System.out.println( "double float hexString is = " + doubleValue );


        String myString = "FFFFFB2E";
        System.out.println((int)Long.parseLong(myString, 16));
        long i = Long.parseUnsignedLong(myString, 16);

        System.out.println("value="+i);

    }
}
