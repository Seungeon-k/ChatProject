package com.example.Chat;


import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {
    public static void main(String[] args) {
        String hostName = "localhost";
        int portNumber = 12345;

        Socket socket = null;
        PrintWriter out = null;
        BufferedReader in = null;
        try {
            socket = new Socket(hostName, portNumber);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            Scanner stdIn = new Scanner(System.in);

            System.out.print("Enter your nickname: ");
            String nickname = stdIn.nextLine();
            out.println(nickname);

            // 서버로부터 메시지를 읽어 화면에 출력하는 스레드 시작
            Thread readThread = new Thread(new ServerMessageReader(in));
            readThread.start();

            String userInput;
            while (true) {
                userInput = stdIn.nextLine();

                // 명령어 처리
                if (userInput.startsWith("/")) {
                    out.println(userInput);
                    continue;
                }

                out.println(userInput);
            }

        } catch (IOException e) {
            System.out.println("Exception caught when trying to connect to " + hostName + " on port " + portNumber);
            e.printStackTrace();
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
                if (socket != null) socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}