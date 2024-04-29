package com.example.Chat;

import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Map.*;

class ChatServerThread extends Thread {
    private Socket socket;
    private String name;
    private Map<String, PrintWriter> clients;
    private Map<String, Integer> userRooms;

    private BufferedReader br;
    private PrintWriter pw;

    private static AtomicInteger nextRoomNumber = new AtomicInteger(1);

    public static int getNextRoomNumber() {
        return nextRoomNumber.getAndIncrement();
    }

    public ChatServerThread(Socket socket, Map<String, PrintWriter> clients, Map<String, Integer> userRooms) {
        this.socket = socket;
        this.clients = clients;
        this.userRooms = userRooms;

        try {
            pw = new PrintWriter(socket.getOutputStream(), true);
            br = new BufferedReader(new InputStreamReader(socket.getInputStream()));

            while (true) {
                name = br.readLine();

                if (clients.containsKey(name))
                    pw.println("이미 존재하는 닉네임입니다. 다시 입력해주세요.");
                else if (name.isEmpty() || name.equals(" "))
                    pw.println("닉네임을 공백으로 설정할 수 없습니다.");
                else break;
            }

            System.out.println("Connected member: " + name + ", Client Port: " + socket.getInetAddress());

            pw.println("방 목록 보기 : /list\n방 생성 : /create\n방 입장 : /join [방번호]\n방 나가기 : /exit\n접속종료 : /bye");

            synchronized (clients) {
                clients.put(this.name, pw);
                userRooms.put(this.name, 0);
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }

    @Override
    public void run() {
        String msg;
        try {
            while ((msg = br.readLine()) != null) {
                if ("/bye".equalsIgnoreCase(msg)) {
                    pw.println("접속을 종료합니다.");
                    break;
                }

                if (msg.startsWith("/")) {
                    processCommand(msg);
                    continue;
                }

                sendMessage(msg);
            }
        } catch (IOException e) {
            System.out.println(e);
        } finally {
            synchronized (clients) {
                clients.remove(name);
            }

            if (br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if (socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void processCommand(String command) {
        if ("/list".equalsIgnoreCase(command)) {
            seeRooms();
        } else if ("/create".equalsIgnoreCase(command)) {
            synchronized (userRooms) {
                int room = getNextRoomNumber();
                userRooms.put(this.name, room);
                enterRoom();
                System.out.println(room + "번 방이 생성되었습니다.");
            }
        } else if (command.startsWith("/join")) {
            synchronized (userRooms) {
                int firstSpaceIndex = command.indexOf(" ");
                int roomNum = Integer.parseInt(command.substring(firstSpaceIndex + 1));

                if (userRooms.values().stream().noneMatch(r -> r.equals(roomNum)) || roomNum == 0)
                    pw.println("방 번호를 올바르게 입력해주세요.");
                else {
                    userRooms.put(this.name, roomNum);
                    enterRoom(roomNum);
                }
            }
        } else if ("/exit".equalsIgnoreCase(command)) {
            synchronized (userRooms) {
                exitRoom();
            }
        }
    }

    public void seeRooms() {
        synchronized (userRooms) {
            Map<String, Integer> existRooms = userRooms.entrySet().stream()
                    .filter(entry -> entry.getValue() != 0)
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

            if (existRooms.isEmpty()) {
                pw.println("방이 없습니다. 방을 생성하려면 /create를 입력하세요.");
            } else {
                pw.print("방 목록: ");
                existRooms.forEach((roomNumber, roomName) -> pw.print(roomName + " "));
                pw.println();
            }
        }
    }

    public void enterRoom() {
        int currentRoom = userRooms.get(this.name);
        sendMessageToRoom(currentRoom, name + "님이 입장하였습니다.");
    }

    public void enterRoom(int room) {
        sendMessageToRoom(room, name + "님이 입장하였습니다.");
    }

    public void exitRoom() {
        int currentRoom = userRooms.get(this.name);
        userRooms.put(this.name, 0);
        pw.println("방을 퇴장하였습니다.");

        synchronized (clients) {
            boolean isRoomEmpty = userRooms.values().stream()
                    .noneMatch(room -> room == currentRoom);

            if (isRoomEmpty) {
                System.out.println(currentRoom + "번 방이 삭제되었습니다.");
            } else {
                sendMessageToRoom(currentRoom, name + "님이 퇴장했습니다.");
            }
        }
    }

    public void sendMessageToRoom(int room, String message) {
        synchronized (clients) {
            clients.forEach((clientId, clientPw) -> {
                if (userRooms.get(clientId).equals(room)) {
                    try {
                        clientPw.println(message);
                    } catch (Exception e) {
                        clients.remove(clientId);
                        e.printStackTrace();
                    }
                }
            });
        }
    }

    public void sendMessage(String msg) {
        int currentRoom = userRooms.get(this.name);

        synchronized (clients) {
            clients.forEach((clientId, clientPw) -> {
                if (userRooms.get(clientId).equals(currentRoom)) {
                    try {
                        clientPw.println(name + ": " + msg);
                    } catch (Exception e) {
                        clients.remove(clientId);
                        e.printStackTrace();
                    }
                }
            });
        }
    }
}