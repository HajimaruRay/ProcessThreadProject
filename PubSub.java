import java.io.*; 
import java.net.*; 
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*; 
import java.util.concurrent.*; 

public class PubSub {
    private static final int PORT = 6000; 
    private static final List<ObjectOutputStream> clients = new CopyOnWriteArrayList<>(); 
    private static final List<Long> bossNotificationPID = new CopyOnWriteArrayList<>();
    private static final List<Integer> clientNodeIDs = new CopyOnWriteArrayList<>();
    private static final List<String> bossHistory = new CopyOnWriteArrayList<>();

    private static long maxPid = -1;
    private static int currentBossId = -1;
    private static long currentBossPid = -1;

    public static void log(String message) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        System.out.println("[" + now.format(formatter) + "] " + message);
    }

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT); 
        log("[PubSub] PubSub Broker Started on Port " + PORT); 

        while (true) {
            Socket socket = serverSocket.accept(); 
            log("[PubSub] New socket connected!"); 

            try {
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream()); 
                out.flush(); 
                clients.add(out); 

                // >>> จอง slot ทั้ง PID และ NodeID
                bossNotificationPID.add(-1L);
                clientNodeIDs.add(-1);
                clients.indexOf(out);

                // ส่ง Boss ปัจจุบันให้ client ที่เพิ่งเข้ามา
                if (currentBossId != -1) {
                    try {
                        String content = currentBossId + ":" + currentBossPid;
                        Message bossMsg = new Message(currentBossId, "newBoss", content);
                        out.writeObject(bossMsg);
                        out.flush();
                        log("[PubSub] Sent current Boss info to new client: Node " 
                                + currentBossId + " (PID " + currentBossPid + ")");
                    } catch (IOException e) {
                        log("[PubSub] Failed to send Boss info to new client.");
                    }
                }

                new Thread(() -> {
                    try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) { 
                        while (true) {
                            Message msg = (Message) in.readObject();
                            String[] parts = msg.content.split(":");
                            int senderID = Integer.parseInt(parts[0]);
                            long senderPID = Long.parseLong(parts[1]);
                            log("[PubSub] Received Message from [Node " 
                                    + msg.senderId + " ] (PID: " + senderPID + " ) type " + msg.type);

                            if (msg.type.equals("newBoss")) {
                                try {
                                    int index = clients.indexOf(out);
                                    if (index >= 0) {
                                        bossNotificationPID.set(index, senderPID);
                                        clientNodeIDs.set(index, senderID);  // ผูก index กับ SenderID
                                    }

                                    log("[PubSub] Notified about new Boss candidate: Node " 
                                            + senderID + " (PID " + senderPID + ")");

                                    boolean allNotified = true;
                                    for (Long pid : bossNotificationPID) {
                                        if (pid == -1) {
                                            allNotified = false;
                                            maxPid = -1;
                                        }
                                    }
                                    log("[PubSub] finished checking allNotified: " + allNotified);

                                    if (allNotified) {
                                        maxPid = -1;
                                        currentBossId = -1;

                                        for (int i = 0; i < bossNotificationPID.size(); i++) {
                                            if (bossNotificationPID.get(i) > maxPid) { 
                                                maxPid = bossNotificationPID.get(i);
                                                currentBossId = clientNodeIDs.get(i); // ใช้ SenderID จริง
                                            }
                                        }
                                        currentBossPid = maxPid;

                                        // เก็บ History
                                        String record = "BossID=" + currentBossId + ", PID=" + currentBossPid;
                                        bossHistory.add(record);
                                        log("[PubSub] Boss History Updated: " + record);

                                        log("[PubSub] ==== Boss History ====");
                                        for (String h : bossHistory) {
                                            log("[History] " + h);
                                        }
                                        log("[PubSub] ======================");

                                        // ส่งแจ้งไปยัง client ทุกคน
                                        String content = currentBossId + ":" + currentBossPid;
                                        Message bossMsg = new Message(currentBossId, "newBoss", content);
                                        for (ObjectOutputStream client : clients) { 
                                            try {
                                                client.writeObject(bossMsg); 
                                                client.flush(); 
                                            } catch (IOException e) {
                                                int idx = clients.indexOf(client);
                                                if (idx >= 0) {
                                                    clients.remove(idx);
                                                    bossNotificationPID.remove(idx);
                                                    clientNodeIDs.remove(idx);
                                                    log("[PubSub] Removed slot " + idx + " after write failure.");
                                                }
                                            }
                                        }
                                        log("[PubSub] Boss updated: Node " + currentBossId 
                                                + " (PID " + currentBossPid + ")");

                                        // reset ค่า
                                        for (int i = 0; i < bossNotificationPID.size(); i++) {
                                            if (bossNotificationPID.get(i) != -1) {
                                                bossNotificationPID.set(i, -1L);
                                            }
                                        }
                                    }

                                } catch (Exception ex) {
                                    log("[PubSub] Failed to parse Boss info from message. Error: " 
                                            + ex.getMessage());
                                }
                            } else {
                                for (ObjectOutputStream client : clients) { 
                                    try {
                                        client.writeObject(msg); 
                                        client.flush(); 
                                    } catch (IOException e) {
                                        int idx = clients.indexOf(client);
                                        if (idx >= 0) {
                                            clients.remove(idx);
                                            bossNotificationPID.remove(idx);
                                            clientNodeIDs.remove(idx);
                                            log("[PubSub] Removed slot " + idx + " after write failure.");
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log("[PubSub] Client disconnected."); 
                        int idx = clients.indexOf(out);
                        if (idx >= 0) {
                            clients.remove(idx);
                            bossNotificationPID.remove(idx);
                            clientNodeIDs.remove(idx);
                            log("[PubSub] Freed slot " + idx + " after disconnect.");
                        }
                    }
                }).start();
            } catch (IOException e) {
                log("[PubSub] Failed to create ObjectStreams."); 
                e.printStackTrace(); 
            }
        }
    }
}
