# Pub/Sub Node Boss Election Project
# 📌 คำอธิบายโปรเจกต์

โปรเจกต์นี้เป็นระบบจำลอง Pub/Sub + Leader Election ระหว่างหลาย Process (Node) โดย

ใช้ PubSub Broker เป็นศูนย์กลางกระจายข้อความ เชื่อมต่อกับ Process (Node) ผ่าน Socket

แต่ละ Node สามารถส่ง Heartbeat และตรวจสอบสถานะของ Boss ได้

Boss ถูกเลือกจาก Node ที่มี PID สูงที่สุด

หาก Boss หายไป ระบบจะทำการเลือก Boss ใหม่อัตโนมัติ

---

## ⚙️ ส่วนประกอบของโปรเจกต์

- PubSub.java

  - ทำหน้าที่เป็น Broker

  - รับการเชื่อมต่อจาก Node ผ่าน TCP

  - กระจายข้อความที่ได้รับไปยัง Node ทุกตัว

  - เก็บสถานะ Boss ปัจจุบัน และส่งต่อให้ Node ที่เพิ่งเข้ามาใหม่

  - คัดเลือก Boss โดยใช้ PID สูงที่สุด

  - เก็บ Boss History ที่เคยเป็น

  - เเสดง Boss History

- Node.java

  - แทน Process แต่ละตัว

  - ส่ง Heartbeat พร้อม PID ทุก 5 วินาที

  - ตรวจสอบ Heartbeat จาก Node อื่นเพื่อยืนยันว่า Boss ยังอยู่

  - หาก Boss หายไป -> ส่งชื่อตนเองเป็น Boss ใหม่

  - รับ Message จากPub/Sub Broker เเละวิเคราะห์ว่าเป็น Message ประเภทใด

- Message.java

  - คลาสโมเดลเก็บข้อมูลข้อความที่ใช้สื่อสารผ่าน PubSub

---

## วิธีใช้งาน

1. คอมไพล์โปรเจกต์
```bash
javac PubSub.java Node.java Message.java
```

3. รัน PubSub Broker
```bash
java PubSub
```

5. เปิดเทอร์มินอลใหม่และรัน Node แต่ละตัว 
```bash
java Node 1
java Node 2
java Node 3
.
.
.
java Node ...
```

*หมายเหตุ:* `1`, `2`, `3` คือหมายเลข ID ของแต่ละ Node

---หรือ---

ใช้งานไฟล์ชื่อ
```
RunBatch.bat
```

*หมายเหตุ* batch file นี้จะสร้าง Process เเค่ 3 ตัวเท่านั้น

## 🔄 วิธีทำงานของระบบ

- ทุก Node เชื่อมต่อกับ PubSub Broker ผ่าน TCP port 6000

- Node จะส่ง Heartbeat ทุก 5 วินาที

- Broker จะกระจาย Heartbeat ไปยัง Node ทุกตัว

- Node ตรวจสอบ Heartbeat ของ Boss เสมอ

- หาก Boss ขาดการส่ง Heartbeat > 25 วินาที -> ทุก Node จะเสนอ PID ของตนเอง

- Pub/Sub Broker จะตรวจสอบ PID เเละเลือก PID ที่สูงที่สุดเป็น Boss ใหม่

- เมื่อ Node ใหม่เชื่อมต่อ Broker -> จะได้รับข้อมูล Boss ปัจจุบันทันที

---

## ✅ ข้อดีและข้อจำกัด
# ข้อดี

- แสดงแนวคิด Pub/Sub และ Leader Election แบบเข้าใจง่าย

- Node ใหม่สามารถ sync สถานะ Boss ปัจจุบัน ได้ทันที

- ใช้โครงสร้าง ObjectInputStream/ObjectOutputStream ทำให้สื่อสารสะดวก

- มีระบบ History ของ Boss

# ข้อจำกัด

- รองรับเฉพาะการรันบน localhost

- ไม่มีระบบจัดการความผิดพลาดขั้นสูง (fault tolerance ยังจำกัด)

---

## 🔮 แนวทางการพัฒนาเพิ่มเติม

- เพิ่ม UI Dashboard แสดงสถานะ Node และ Boss แบบ real-time

- รองรับการเชื่อมต่อจากหลายเครื่อง (Distributed Environment)

---

## ติดต่อ

หากมีคำถามหรือข้อเสนอแนะ ติดต่อได้ที่

- Email: chonlatreeketkorwoingwork@gmail.com
- GitHub: https://github.com/HajimaruRay/ProcessThreadProject

---

ขอบคุณที่สนใจโปรเจกต์นี้ครับ!
