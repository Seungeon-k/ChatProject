package com.example.Chat;
import java.io.*;
import java.net.Socket;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static java.util.Map.*;

class ChatServerThread extends Thread {
    private Socket socket;
    private String name;
    private Map<String, PrintWriter> clients;   // 클라이언트 메시지
    private Map<String, Integer> userRooms; // 클라이언트의 방

    private BufferedReader br;
    private PrintWriter pw;

    private static AtomicInteger nextRoomNumber = new AtomicInteger(1);
    private static Set<String> nicknames = new HashSet<>(); // 중복된 닉네임을 확인하기 위한 Set

    // 다음 사용 가능한 방 번호를 반환
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

            while(true) {
                // 클라이언트가 접속 시 닉네임 설정
                name = br.readLine();

                // 닉네임이 중복되는지 확인
                if (nicknames.contains(name)) {
                    pw.println("이미 사용 중인 닉네임입니다. 다른 닉네임을 선택해주세요.");
                } else if (name.isEmpty() || name.equals(" ")) {
                    pw.println("닉네임을 공백으로 설정할 수 없습니다.");
                } else {
                    nicknames.add(name); // 새로운 닉네임을 Set에 추가
                    break;
                }
            }

            // 접속 시 클라이언트의 정보 출력
            System.out.println("접속 멤버: " + name + ", Client Port: " + socket.getInetAddress());

            pw.println("방 목록 보기 : /list\n방 생성 : /create\n방 입장 : /join [방번호]\n방 나가기 : /exit\n접속종료 : /bye");

        }
        catch (Exception e) {
            System.out.println(e);
        }
    }

    @Override
    public void run() {
        String msg;
        try {
            while ((msg = br.readLine()) != null) {
                // 접속 종료
                if("/bye".equalsIgnoreCase(msg)) {
                    pw.println("접속을 종료합니다.");
                    break;
                }

                // 방 목록 보기
                if("/list".equalsIgnoreCase(msg)) {
                    seeRooms();
                }
                // 방 생성
                else if("/create".equalsIgnoreCase(msg)) {
                    synchronized (userRooms) {
                        int room = getNextRoomNumber();
                        userRooms.put(this.name, room);
                        enterRoom();
                        System.out.println(room + "번 방이 생성되었습니다.");
                    }
                }
                // 방 참가
                else if(msg.startsWith("/join")) {
                    synchronized (userRooms) {
                        // 공백으로 분리
                        int firstSpaceIndex = msg.indexOf(" "); // 첫번째 공백의 인덱스
                        int roomNum = Integer.parseInt(msg.substring(firstSpaceIndex+1));

                        // 방이 존재할 떄만 들어가기
                        if(userRooms.values().stream().noneMatch(r -> r.equals(roomNum)) || roomNum == 0)
                            pw.println("방 번호를 올바르게 입력해주세요.");
                        else {
                            userRooms.put(this.name, roomNum);
                            enterRoom(roomNum);
                        }
                    }
                }
                // 사용자의 방 번호가 0이면 채팅 불가
                else if(userRooms.get(this.name).equals(0)) {
                    pw.println("방에 먼저 입장해주세요. /create: 방 생성, /join [방번호]: 방 입장");
                }
                // 방 나가기
                else if(!userRooms.get(this.name).equals(0) && "/exit".equalsIgnoreCase(msg)) {
                    synchronized (userRooms) {
                        exitRoom();
                    }
                }
                // 방에 참가된 경우 메시지 주고 받기 가능
                else /*if(!userRooms.get(this.id).equals(0))*/ {
                    sendMessage(msg);
                }
            }
        } catch (IOException e) {
            System.out.println(e);
        } finally {

            System.out.println("종료한 멤버: " + name);

            // 사용 후 BufferedReader와 Socket 닫기
            if(br != null) {
                try {
                    br.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }

            if(socket != null) {
                try {
                    socket.close();
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    // 방의 리스트 보기
    public void seeRooms() {
        synchronized (userRooms) {
            // 방 목록
            Map<String, Integer> existRooms = userRooms.entrySet().stream()
                    .filter(entry -> entry.getValue() != 0)  // 방 번호가 0이 아닌 것만 필터링
                    .collect(Collectors.toMap(Entry::getKey, Entry::getValue));

            // 방 목록이 비어있는지 확인하여 메시지 전송
            if (existRooms.isEmpty()) {
                pw.println("방이 없습니다. 방을 생성하려면 /create를 입력하세요.");
            }
            else {
                pw.print("방 목록: ");
                existRooms.forEach((roomNumber, roomName) -> pw.print(roomName + " "));
                pw.println();
            }
        }
    }

    // 방 참가
    // create 해서 들어온 경우
    public void enterRoom() {
        int currentRoom = userRooms.get(this.name);   // 들어온 방 번호

        // 방 멤버들에게 입장 알림
        sendMessageToRoom(currentRoom, name + "님이 입장하였습니다.");
    }

    // join 해서 들어온 경우
    public void enterRoom(int room) {
        sendMessageToRoom(room, name + "님이 입장하였습니다.");
    }

    // 방 나가기
    public void exitRoom() {
        int currentRoom = userRooms.get(this.name);
        userRooms.put(this.name, 0); // 사용자의 방 번호를 0으로 설정
        pw.println("방을 퇴장하였습니다.");

        synchronized (clients) {
            // 해당 방에 남아있는 사용자가 있는지 확인
            boolean isRoomEmpty = userRooms.values().stream()
                    .noneMatch(room -> room == currentRoom);

            // 비었으면 방 삭제
            if (isRoomEmpty) {
                System.out.println(currentRoom + "번 방이 삭제되었습니다.");
            }
            else {
                // 방에 남아있는 사용자에게 메시지 전송
                sendMessageToRoom(currentRoom, name + "님이 퇴장했습니다.");
            }
        }
    }

    // 해당 방을 사용하는 사람에게 입장/퇴장 메시지 전송
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

    // 메시지 보내기
    public void sendMessage(String msg) {
        int currentRoom = userRooms.get(this.name); // 현재 클라이언트의 방 번호

        synchronized (clients) {
            clients.forEach((clientId, clientPw) -> {
                // 현재 클라이언트와 같은 방에 있는 클라이언트에게만 메시지 전송
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
