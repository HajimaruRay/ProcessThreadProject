import java.io.*;                                // ใช้สำหรับการรับส่งข้อมูลผ่าน Stream
import java.net.*;                               // ใช้สำหรับการเชื่อมต่อเครือข่าย (Socket)
import java.util.*;                              // ใช้งานโครงสร้างข้อมูลพื้นฐาน เช่น List
import java.util.concurrent.*;                   // ใช้ CopyOnWriteArrayList เพื่อ thread-safe list

public class PubSub {
    private static final int PORT = 9999;        // กำหนดพอร์ตที่ใช้สำหรับรอการเชื่อมต่อ
    private static final List<ObjectOutputStream> clients = new CopyOnWriteArrayList<>();
    // รายการของ client ทั้งหมดที่เชื่อมต่อเข้ามา ใช้ CopyOnWriteArrayList เพื่อให้สามารถใช้งานร่วมกันข้าม thread ได้อย่างปลอดภัย

    public static void main(String[] args) throws IOException {
        ServerSocket serverSocket = new ServerSocket(PORT); // สร้าง server socket ที่รอรับการเชื่อมต่อที่พอร์ต 9999
        System.out.println("PubSub Broker started on port " + PORT); // แสดงข้อความเมื่อเซิร์ฟเวอร์เริ่มทำงาน

        while (true) { // วนลูปรอรับการเชื่อมต่อจาก client ตลอดเวลา
            Socket socket = serverSocket.accept(); // ยอมรับการเชื่อมต่อจาก client
            System.out.println("New subscriber connected"); // แสดงข้อความเมื่อมี client ใหม่เชื่อมต่อ

            try {
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                // สร้าง output stream สำหรับส่ง object ไปยัง client
                out.flush(); // ล้างข้อมูลที่ค้างใน buffer เพื่อให้แน่ใจว่า header ถูกส่งแล้ว
                clients.add(out); // เพิ่ม output stream นี้ไว้ใน list เพื่อให้สามารถ broadcast ข้อความได้

                // สร้าง thread ใหม่เพื่อจัดการกับ client แต่ละคนแบบแยกอิสระ
                new Thread(() -> {
                    try (ObjectInputStream in = new ObjectInputStream(socket.getInputStream())) {
                        // สร้าง input stream เพื่อรับ object ที่ client ส่งมา
                        while (true) { // ลูปรับข้อความจาก client
                            Message msg = (Message) in.readObject(); // อ่าน object (Message) ที่ client ส่งเข้ามา

                            // ส่งข้อความไปยัง client ทุกคนที่อยู่ใน list
                            for (ObjectOutputStream client : clients) {
                                try {
                                    client.writeObject(msg); // ส่งข้อความไปยัง client
                                    client.flush(); // ล้าง buffer เพื่อให้แน่ใจว่าข้อมูลถูกส่งออก
                                } catch (IOException e) {
                                    clients.remove(client); // ถ้าเกิดปัญหาในการส่ง ให้ลบ client ออกจาก list
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.out.println("Client disconnected."); // แสดงข้อความเมื่อ client หลุด
                        clients.remove(out); // เอา output stream ของ client ที่หลุดออกจาก list
                    }
                }).start(); // เริ่ม thread ที่สร้างไว้ด้านบน

            } catch (IOException e) {
                System.out.println("Failed to create ObjectStreams."); // แสดงข้อความเมื่อสร้าง stream ไม่สำเร็จ
                e.printStackTrace(); // พิมพ์รายละเอียดของข้อผิดพลาด
            }
        }
    }
}
