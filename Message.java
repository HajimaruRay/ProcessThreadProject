import java.io.Serializable; // นำเข้า interface Serializable เพื่อให้ Message สามารถส่งผ่าน stream ได้

public class Message implements Serializable{
    public int senderId;    // รหัสผู้ส่งข้อความ (Node ที่ส่ง)
    public String type;     // ประเภทของข้อความ เช่น "heartbeat", "newBoss"
    public String content;  // เนื้อหาข้อความ เช่น "1:12345" (id:pid)

    public Message(int senderId, String type,String content){
        this.senderId = senderId;
        this.type = type;
        this.content = content;
    }
}
