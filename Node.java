import java.io.*;                      // สำหรับใช้งาน Input/Output streams
import java.net.*;                    // สำหรับการสื่อสารผ่าน network sockets
import java.util.*;                   // สำหรับใช้งาน utility classes เช่น Random
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Node {
    private static long pid;          // เก็บ Process ID ของ Node นี้
    private static int id;            // ID ของ Node (ใส่ผ่าน arguments ตอนเริ่มโปรแกรม)
    private static int bossId = -1;   // ID ของ Boss node ปัจจุบัน (-1 หมายถึงยังไม่มี)
    private static long bossPid = -1; // PID ของ Boss node ปัจจุบัน
    private static String role = "Unknown"; // สถานะของ node นี้ (Boss หรือ Slave)
    private static final String HOST = "localhost"; // Host ของ PubSub broker
    private static final int PORT = 6000;           // Port ของ PubSub broker
    private static long lastBossHeartbeat = System.currentTimeMillis(); // เวลา heartbeat ล่าสุดจาก Boss

    public static void log(String message) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        System.out.println("[" + now.format(formatter) + "] " + message);
    }

    public void Heartbeat(ObjectInputStream in, ObjectOutputStream out){
        try{
            while(true){
                String content = id + ":" + pid;  // สร้าง content ประกอบข้อความ Heartbeat
                Message msg = new Message(id, "heartbeat", content);    // สร้าง msg
                out.writeObject(msg);   // ส่งข้อความชนิด heartbeat
                out.flush();    // flush ข้อความ
                log("[Node "+ id +" ] [RULE: " + role + " ] Send HeartBeat");
                Thread.sleep(5000);    // รอ 5 วินาที
            }
        } catch(Exception e){
            log("[Node "+ id +" ] [RULE: " + role + " ] Stopped Heartbeat thread");
        }
    }

    public void ReceiveMSGFromPubSub(ObjectInputStream in, ObjectOutputStream out){
        try{
            while(true){
                Message msg = (Message) in.readObject();

                // รับ Message จาก HeartBeatที่ถูกส่งมาจากPubSub
                if (msg.type.equals("heartbeat")){
                    String[] parts = msg.content.split(":");
                    // log(msg.content);
                    int senderId = Integer.parseInt(parts[0]);
                    long senderPid = Long.parseLong(parts[1]);

                    // ถ้าเป็น heartbeat จาก Boss
                    if (senderPid == bossPid) {
                        lastBossHeartbeat = System.currentTimeMillis(); // อัปเดตเวลา heartbeat ล่าสุด
                    }

                    log("[Node "+ id +" ] [RULE: " + role + " ] Received heartbeat from [Node "+ senderId +" ] (PID " + senderPid + " )");
                }
                
                if (msg.type.equals("newBoss")){
                    String[] parts = msg.content.split(":");
                    int newBossId = Integer.parseInt(parts[0]);
                    long newBossPid = Long.parseLong(parts[1]);


                    if (newBossPid > bossPid){
                        bossId = newBossId;
                        bossPid = newBossPid;
                        log(""+ bossPid);
                        if (id == bossId){
                            role = "Boss";
                        } else{
                            role = "Slave";
                        }

                        log("[Node "+ id +" ] [RULE: " + role + " ] I am now a " + role);
                        log("[Node "+ id +" ] [RULE: " + role + " ] New boss selected: [Node "+ bossId +" ] (PID " + bossPid + " )");
                    }
                }
            }
        } catch (EOFException e) {
            // เมื่อเชื่อมต่อกับ PubSub ถูกปิด
            log("[Node "+ id +" ] [RULE: " + role + " ] Connection closed by broker.");
        } catch (Exception e) {
            // ข้อผิดพลาดอื่นๆ ขณะรับข้อความ
            log("[Node "+ id +" ] [RULE: " + role + " ] Error in receive thread:");
            e.printStackTrace();
        }
    }

    public void IsBossStillAlive(ObjectInputStream in, ObjectOutputStream out){
        try {
                while (true) {
                    Thread.sleep(20000);                          // ตรวจสอบทุก 20 วินาที
                    long now = System.currentTimeMillis();              // เวลาปัจจุบัน

                    // ถ้า Boss ไม่เคยถูกเลือก หรือหายไปนานกว่า 25 วินาที
                    if (bossPid == -1 || (now - lastBossHeartbeat) > 25000) {
                        if ((now - lastBossHeartbeat) > 25000){
                            log("[Node "+ id +" ] [RULE: " + role + " ] Boss timeout detected.");
                        } else if (bossPid == -1){
                            log("[Node "+ id +" ] [RULE: " + role + " ] Don't have Boss");
                        }
                        Thread.sleep(new Random().nextInt(1000));       // random delay ป้องกันชนกัน

                        String content = id + ":" + pid;                // ส่งข้อมูลเสนอชื่อตัวเองเป็น Boss ใหม่
                        Message msg = new Message(id, "newBoss", content);
                        out.writeObject(msg);
                        out.flush();
                    }
                }
            } catch (Exception ignored) {} // ไม่ทำอะไรหากเกิดข้อผิดพลาด
    }

    public static void main(String[] args) throws Exception {
        // ตรวจสอบว่าใส่ argument มาครบไหม (ต้องมี 1 คือ id)
        // java Node 1 === args[0] == 1 ไม่เข้าเงื่อนไข ทำงานต่อ
        // java Node 1 1 1 === args[0] == 3 เข้าเงื่อนไข
        if (args.length != 1) {
            log("Usage: java Node <id>");
            return;
        }

        id = Integer.parseInt(args[0]);         // อ่านค่า id จาก arguments
        pid = ProcessHandle.current().pid();    // ดึงค่า PID ของ process ปัจจุบัน
        Node node = new Node();

        Socket socket = new Socket(HOST,PORT);  // เชื่อมต่อ PubSub
        ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());  // stream สำหรับส่งข้อมูล
        out.flush();    // กัน deadlock
        ObjectInputStream in = new ObjectInputStream(socket.getInputStream());      // stream สำหรับรับข้อมูล

        log("[Node " + id + " ] (PID " + pid + " ) connected to PubSub Broker");

        new Thread(() ->{
            node.Heartbeat(in, out);
        }).start();

        new Thread(() ->{
            node.ReceiveMSGFromPubSub(in, out);
        }).start();

        new Thread(() ->{
            node.IsBossStillAlive(in, out);
        }).start();

    }
}