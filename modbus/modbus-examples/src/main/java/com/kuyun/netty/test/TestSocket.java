package com.kuyun.netty.test;

import io.netty.buffer.ByteBufUtil;
import sun.misc.IOUtils;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import javax.xml.bind.DatatypeConverter;
import java.util.ArrayList;

/**
 * Created by user on 5/27/2017.
 */
public class TestSocket {

    private static byte[] MODBUS_COMMOND = {0x01, 0x03, 0x00, 0x00, 0x00, 0x03, 0x05, (byte) 0xCB};

    public static void main(String args[]) throws IOException, Exception {
        final int portNumber = 8234;
        System.out.println("Creating server socket on port " + portNumber);
        ServerSocket serverSocket = new ServerSocket(portNumber);
        while (true) {
            Socket socket = serverSocket.accept();
            OutputStream os = socket.getOutputStream();

            DataOutputStream dos = new DataOutputStream(os);
            dos.write(MODBUS_COMMOND);

            try {
                Thread.sleep(5000);                 //1000 milliseconds is one second.
            } catch(InterruptedException ex) {
                Thread.currentThread().interrupt();
            }

            byte[] input = readSocket(socket);

            System.out.println("received: "+DatatypeConverter.printHexBinary(input));
            //System.out.println("received: "+bytesToHexString(input));


            socket.close();

        }
    }

    /* Convert byte[] to hex string.这里我们可以将byte转换成int，然后利用Integer.toHexString(int)来转换成16进制字符串。
            * @param src byte[] data
 * @return hex string
 */
    public static String bytesToHexString(byte[] src){
        StringBuilder stringBuilder = new StringBuilder("");
        if (src == null || src.length <= 0) {
            return null;
        }
        for (int i = 0; i < src.length; i++) {
            int v = src[i] & 0xFF;
            String hv = Integer.toHexString(v);
            if (hv.length() < 2) {
                stringBuilder.append(0);
            }
            stringBuilder.append(hv);
        }
        return stringBuilder.toString();
    }

    public static byte[] readSocket(Socket socket) throws Exception{
        InputStream stream = socket.getInputStream();
        byte[] data = new byte[100];
        int count = stream.read(data);
        System.out.println("length= [" + count + "]");
        for (byte a : data){
            System.out.print(a);
        }
        System.out.println("data = [" + data + "]");


        return data;
    }



}
