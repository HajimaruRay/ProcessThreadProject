import java.io.*; // ใช้สำหรับการรับส่งข้อมูลผ่าน Stream
import java.net.*; // ใช้สำหรับการเชื่อมต่อเครือข่าย (Socket)
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*; // ใช้งานโครงสร้างข้อมูลพื้นฐาน เช่น List
import java.util.concurrent.*; // ใช้ CopyOnWriteArrayList เพื่อ thread-safe list
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class PubSub {
    private static final int PORT = 6000; // กำหนดพอร์ตที่ใช้สำหรับรอการเชื่อมต่อ
    private static final List<ObjectOutputStream> clients = new CopyOnWriteArrayList<>(); // ObjectOutputStream
                                                                                          // ใช้บันทึก object ลง stream
                                                                                          // (serialize object)
                                                                                          // เพื่อเก็บลงไฟล์หรือส่งต่อทาง
                                                                                          // network
    // รายการของ client ทั้งหมดที่เชื่อมต่อเข้ามา ใช้ CopyOnWriteArrayList
    // เพื่อให้สามารถใช้งานร่วมกันข้าม thread ได้อย่างปลอดภัย
    private static final long bossNotificationPID[] = new long[3];
    private static long maxPid = -1;
    private static int maxBossId = -1;

    // Boss ปัจจุบัน
    private static int currentBossId = -1;
    private static long currentBossPid = -1;

    public static void log(String message) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        System.out.println("[" + now.format(formatter) + "] " + message);
    }

    public static void main(String[] args) throws IOException {
        for (int i = 0; i < bossNotificationPID.length; i++) {
            bossNotificationPID[i] = -1;
        }
        ServerSocket serverSocket = new ServerSocket(PORT); // สร้าง Server ที่รับการเชื่อมต่อPORT
        log("[PubSub] PubSub Broker Started on Port " + PORT); // เเจ้งเตือนว่ามีการสร้าง Server เเล้วด้วยPORTใดๆ

        while (true) {
            Socket socket = serverSocket.accept(); // ตอบรับการเชื่อมต่อใหม่เสมอจาก client
            log("[PubSub] New socket connected!"); // เเจ้งเตือนว่ามีการเชื่อมต่อใหม่

            try {
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream()); // สร้างตัวส่ง Object ไปให้
                                                                                           // client
                out.flush(); // ล้างข้อมูลที่ค้างใน buffer เพื่อให้แน่ใจว่า header ถูกส่งแล้ว
                clients.add(out); // เพิ่มตัวส่ง Object ไปเก็บไว้ใน List

                // ถ้ามี Boss อยู่แล้ว ส่งสถานะ Boss ให้ client ที่เพิ่งเข้ามา
                if (currentBossId != -1) {
                    try {
                        String content = currentBossId + ":" + currentBossPid;
                        Message bossMsg = new Message(currentBossId, "newBoss", content);
                        out.writeObject(bossMsg);
                        out.flush();
                        log("[PubSub] Sent current Boss info to new client: Node " + currentBossId + " (PID "
                                + currentBossPid + ")");
                    } catch (IOException e) {
                        log("[PubSub] Failed to send Boss info to new client.");
                    }
                }

                new Thread(() -> {
                    try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) { // สร้าง input stream
                                                                                                  // เพื่อรับ object ที่
                                                                                                  // client ส่งมา
                        while (true) {
                            Message msg = (Message) in.readObject();
                            String[] parts = msg.content.split(":");
                            int senderID = Integer.parseInt(parts[0]);
                            long senderPID = Long.parseLong(parts[1]);
                            log("[PubSub] Received Message from [Node " + msg.senderId + " ] (PID: " + senderPID + " ) type " + msg.type);

                            // ถ้ามีการประกาศ Boss ใหม่ อัปเดต state ที่ PubSub
                            if (msg.type.equals("newBoss")) {
                                try {
                                    bossNotificationPID[senderID - 1] = senderPID;
                                    log("[PubSub] Notified about new Boss candidate: Node " + senderID + " (PID "
                                            + senderPID + ")");
                                    boolean allNotified = true;
                                    for (int i = 0; i < bossNotificationPID.length; i++) {
                                        if (bossNotificationPID[i] == -1) {
                                            allNotified = false;
                                            maxPid = -1;
                                            maxBossId = -1;
                                        }
                                    }
                                    log("[PubSub] finished checking allNotified: " + allNotified);

                                    if (allNotified) {
                                        for (int i = 0; i < bossNotificationPID.length; i++) {
                                            if (bossNotificationPID[i] > maxPid) { // เลือก boss จาก PID ที่ใหญ่ที่สุด
                                                maxBossId = i + 1;
                                                maxPid = bossNotificationPID[i];
                                            }
                                        }
                                        currentBossId = maxBossId;
                                        currentBossPid = maxPid;
                                        // ส่งข้อความไปยัง client ทุกคนที่อยู่ใน list
                                        String content = currentBossId + ":" + currentBossPid;
                                        Message bossMsg = new Message(currentBossId, "newBoss", content);
                                        for (ObjectOutputStream client : clients) { // for-each
                                            try {
                                                client.writeObject(bossMsg); // ส่งข้อความไปยัง client
                                                client.flush(); // ล้างข้อมูลที่ค้างใน buffer เพื่อให้แน่ใจว่า header
                                                                // ถูกส่งแล้ว
                                            } catch (IOException e) {
                                                clients.remove(client); // ถ้าเกิดปัญหาในการส่ง ให้ลบ client ออกจาก list
                                            }
                                        }
                                        log("[PubSub] Boss updated: Node " + currentBossId + " (PID " + currentBossPid
                                                + ")");
                                        for (int i = 0; i < bossNotificationPID.length; i++) {
                                        // เคลียร์สถานะการแจ้งเตือน Boss ใหม่
                                        if (bossNotificationPID[i] != -1) {
                                            bossNotificationPID[i] = -1;
                                        }
                                    }
                                    }

                                } catch (Exception ex) {
                                    log("[PubSub] Failed to parse Boss info from message. Error: " + ex.getMessage());
                                }
                            } else {
                                for (ObjectOutputStream client : clients) { // for-each
                                    try {
                                        client.writeObject(msg); // ส่งข้อความไปยัง client
                                        client.flush(); // ล้างข้อมูลที่ค้างใน buffer เพื่อให้แน่ใจว่า header
                                                        // ถูกส่งแล้ว
                                    } catch (IOException e) {
                                        clients.remove(client); // ถ้าเกิดปัญหาในการส่ง ให้ลบ client ออกจาก list
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        log("[PubSub] Client disconnected."); // แสดงข้อความเมื่อ client หลุด
                        clients.remove(out); // เอา output stream ของ client ที่หลุดออกจาก list
                    }
                }).start();
            } catch (IOException e) {
                log("[PubSub] Failed to create ObjectStreams."); // แสดงข้อความเมื่อสร้าง stream ไม่สำเร็จ
                e.printStackTrace(); // พิมพ์รายละเอียดของข้อผิดพลาด
            }
        }
    }
}