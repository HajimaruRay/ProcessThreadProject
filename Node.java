import java.io.*;                      // สำหรับใช้งาน Input/Output streams
import java.net.*;                    // สำหรับการสื่อสารผ่าน network sockets
import java.util.*;                   // สำหรับใช้งาน utility classes เช่น Random

public class Node {
    private static long pid;          // เก็บ Process ID ของ Node นี้
    private static int id;            // ID ของ Node (ใส่ผ่าน arguments ตอนเริ่มโปรแกรม)
    private static int bossId = -1;   // ID ของ Boss node ปัจจุบัน (-1 หมายถึงยังไม่มี)
    private static long bossPid = -1; // PID ของ Boss node ปัจจุบัน
    private static String role = "Unknown"; // สถานะของ node นี้ (Boss หรือ Slave)
    private static final String HOST = "localhost"; // Host ของ PubSub broker
    private static final int PORT = 9999;           // Port ของ PubSub broker
    private static long lastBossHeartbeat = System.currentTimeMillis(); // เวลา heartbeat ล่าสุดจาก Boss

    public static void main(String[] args) throws Exception {
        // ตรวจสอบว่าใส่ argument มาครบไหม (ต้องมี 1 คือ id)
        if (args.length != 1) {
            System.out.println("Usage: java Node <id>");
            return;
        }

        id = Integer.parseInt(args[0]);                  // อ่านค่า id จาก arguments
        pid = ProcessHandle.current().pid();             // ดึง PID ของ process นี้
        role = "Unknown";                                // ตั้งค่าตำแหน่งเริ่มต้น

        // เชื่อมต่อกับ PubSub broker
        Socket socket = new Socket(HOST, PORT);
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream()); // stream สำหรับส่งข้อมูล
        out.flush();                                                               // flush ครั้งแรกเพื่อป้องกัน deadlock
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());     // stream สำหรับรับข้อมูล

        // แสดงว่า Node เริ่มทำงานแล้ว
        System.out.println("[Node " + id + "] (PID " + pid + ") started");

        // --- Thread 1: ส่ง heartbeat ทุก 20 วินาที ---
        new Thread(() -> {
            try {
                while (true) {
                    String content = id + ":" + pid;                  // ประกอบข้อมูล heartbeat
                    out.writeObject(new Message(id, "heartbeat", content)); // ส่งข้อความชนิด heartbeat
                    out.flush();                                      // flush เพื่อให้ข้อความถูกส่งทันที
                    Thread.sleep(20000);                              // รอ 20 วินาที
                }
            } catch (Exception e) {
                System.out.println("[" + role + " " + id + "] Heartbeat thread stopped");
            }
        }).start();

        // --- Thread 2: รับข้อความจาก PubSub และประมวลผล ---
        new Thread(() -> {
            try {
                while (true) {
                    Message msg = (Message) in.readObject();   // รับข้อความจาก PubSub

                    // ถ้าเป็น heartbeat จาก Node อื่น
                    if (msg.type.equals("heartbeat")) {
                        String[] parts = msg.content.split(":");        // แยกข้อมูลออกเป็น id กับ pid
                        int senderId = Integer.parseInt(parts[0]);      // อ่าน id ของ Node ที่ส่งมา
                        long senderPid = Long.parseLong(parts[1]);      // อ่าน pid

                        // ถ้าเป็น heartbeat จาก Boss
                        if (senderPid == bossPid) {
                            lastBossHeartbeat = System.currentTimeMillis(); // อัปเดตเวลา heartbeat ล่าสุด
                        }

                        // แสดงผลว่าได้รับ heartbeat
                        System.out.println("[" + role + " " + id + "] Received heartbeat from Node " + senderId + " (PID " + senderPid + ")");
                    }

                    // ถ้าเป็นข้อความ newBoss (มี Node เสนอ Boss ใหม่)
                    else if (msg.type.equals("newBoss")) {
                        String[] parts = msg.content.split(":");           // แยก id กับ pid
                        int newBossId = Integer.parseInt(parts[0]);        // id ของ Boss ใหม่
                        long newBossPid = Long.parseLong(parts[1]);        // pid ของ Boss ใหม่

                        // ถ้า pid ใหม่มากกว่า pid ของ Boss ปัจจุบัน → เปลี่ยน Boss
                        if (newBossPid > bossPid) {
                            bossId = newBossId;                // กำหนด bossId ใหม่
                            bossPid = newBossPid;              // กำหนด bossPid ใหม่
                            role = (id == bossId) ? "Boss" : "Slave";  // ถ้า Node นี้เป็น Boss → ตั้งเป็น Boss

                            // แสดงผลว่าได้เปลี่ยนตำแหน่ง
                            System.out.println("[Node " + id + "] I am now a " + role);
                            System.out.println("[" + role + " " + id + "] New boss elected: Node " + bossId + " (PID " + bossPid + ")");
                        }
                    }
                }
            } catch (EOFException e) {
                // เมื่อเชื่อมต่อกับ PubSub ถูกปิด
                System.out.println("[" + role + " " + id + "] Connection closed by broker.");
            } catch (Exception e) {
                // ข้อผิดพลาดอื่นๆ ขณะรับข้อความ
                System.out.println("[" + role + " " + id + "] Error in receive thread:");
                e.printStackTrace();
            }
        }).start();

        // --- Thread 3: ตรวจสอบ Boss ว่ายังอยู่หรือไม่ ---
        new Thread(() -> {
            try {
                while (true) {
                    Thread.sleep(5000);                                 // ตรวจสอบทุก 5 วินาที
                    long now = System.currentTimeMillis();              // เวลาปัจจุบัน

                    // ถ้า Boss ไม่เคยถูกเลือก หรือหายไปนานกว่า 25 วินาที
                    if (bossPid == -1 || (now - lastBossHeartbeat) > 25000) {
                        System.out.println("[" + role + " " + id + "] Boss timeout detected. Re-electing...");
                        Thread.sleep(new Random().nextInt(1000));       // random delay ป้องกันชนกัน

                        String content = id + ":" + pid;                // ส่งข้อมูลเสนอชื่อ Boss ใหม่
                        out.writeObject(new Message(id, "newBoss", content));
                        out.flush();
                    }
                }
            } catch (Exception ignored) {} // ไม่ทำอะไรหากเกิดข้อผิดพลาด
        }).start();
    }
}
