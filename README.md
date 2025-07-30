# Pub/Sub Node Boss Election Project

## คำอธิบายโปรเจกต์

โปรเจกต์นี้เป็นระบบจำลองการสื่อสารระหว่าง 3 Process (Node) โดยมีการเลือก Boss จาก Node ที่มี PID มากที่สุด และมีการตรวจสอบสถานะ Boss ด้วย Heartbeat ทุก 20 วินาที

ระบบใช้โครงสร้าง Pub/Sub (Publisher/Subscriber) เพื่อให้ Node แต่ละตัวสามารถส่งและรับข้อความกันได้แบบกระจาย โดยมี PubSub Broker เป็นศูนย์กลางจัดการรับส่งข้อความระหว่าง Node

---

## ส่วนประกอบของโปรเจกต์

- PubSub.java  
  เป็น Broker ที่รอรับการเชื่อมต่อจาก Node และส่งข้อความ (broadcast) ไปยัง Node ทุกตัวที่เชื่อมต่อ

- Node.java  
  ตัวแทน Process แต่ละตัวในระบบ  
  - ส่ง Heartbeat ทุก 20 วินาที  
  - รับ Heartbeat จาก Node ตัวอื่นเพื่อตรวจสอบสถานะ Boss  
  - หาก Boss หายไป จะเลือก Boss ใหม่โดยใช้ PID ของแต่ละ Node

- Message.java  
  คลาสเก็บข้อมูลข้อความที่จะส่งผ่าน Pub/Sub Broker

---

## วิธีใช้งาน

1. คอมไพล์โปรเจกต์ 
javac PubSub.java Node.java Message.java

2. รัน PubSub Broker  
java PubSub

3. เปิดเทอร์มินอลใหม่และรัน Node แต่ละตัว (3 Process)  
java Node 1
java Node 2
java Node 3

*หมายเหตุ:* `1`, `2`, `3` คือหมายเลข ID ของแต่ละ Node

---

## วิธีทำงาน

- ทุก Node เชื่อมต่อกับ PubSub Broker ผ่าน TCP port 9999  
- Node ส่ง Heartbeat พร้อม PID ทุก 20 วินาที  
- Broker รับข้อความและกระจายไปยัง Node ทุกตัว  
- Node ตรวจสอบ heartbeat ของ Boss อยู่เสมอ  
- หาก Boss หายไป (ไม่ได้ส่ง heartbeat นานกว่า 25 วินาที) ทุก Node จะเสนอ PID ของตนเองเพื่อเลือก Boss ใหม่  
- Node ที่มี PID สูงสุดจะถูกเลือกเป็น Boss และประกาศสถานะใหม่

---

## ข้อดีและข้อจำกัด

- แสดงแนวคิด Pub/Sub และ Leader Election แบบง่าย  
- ไม่รองรับการเชื่อมต่อข้ามเครื่อง (ใช้ localhost เท่านั้น)  
- ไม่มีระบบจัดการความผิดพลาดขั้นสูง  
- เหมาะสำหรับการศึกษาและทดลองระบบกระจายเบื้องต้น

---

## ขยายเพิ่มเติม

- เพิ่ม UI แสดงสถานะ Node และ Boss  
- เพิ่มระบบล็อกและตรวจสอบประวัติการเลือก Boss  
- รองรับการรันบนหลายเครื่อง  
- ปรับให้รองรับ Node จำนวนมากกว่า 3 ตัว

---

## ติดต่อ

หากมีคำถามหรือข้อเสนอแนะ ติดต่อได้ที่

- Email: your.email@example.com  
- GitHub: https://github.com/yourusername/pubsub-node-boss-election

---

ขอบคุณที่สนใจโปรเจกต์นี้ครับ!
