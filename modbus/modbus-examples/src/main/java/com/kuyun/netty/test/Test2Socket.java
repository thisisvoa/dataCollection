package com.kuyun.netty.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;

public class Test2Socket {
    public static void main(String[] args) {
        try {
            ServerSocket skt = new ServerSocket(8234);

            Socket clientSocket = skt.accept();

            clientSocket.setKeepAlive(true);

            System.out.println("Connected..");

            BufferedReader input = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            String inputLine;

            while((inputLine = input.readLine()) != null)
            {
                System.out.println(inputLine);
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}