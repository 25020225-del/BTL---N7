# Hướng dẫn cài đặt đối với linux

---

### Bước 1: Cài đặt java bản 25
- Đối với Ubuntu/ Debian:
```
sudo apt install openjdk-25-jdk
```
- Đối với Fedora
```
sudo dnf update
sudo dnf install java-25-openjdk
```
- Đối với Arch
```
sudo pacman -S java-openjdk
```

- Trong trường hợp không tìm thấy gói của bạn, hãy thử update trình quản lí gói phụ thuộc của bạn.
### Bước 2: Cài đặt maven.
- Đối với Ubuntu/ Debian:
```
sudo apt install maven
```
- Đối với Fedora:
```
sudo dnf install maven
```
- Đối với Arch:
```
sudo pacman -S maven
```
- Đối với các môi trường khác, tham khảo: [Hướng dẫn cài đặt maven](https://maven.apache.org/install.html)
### Bước 3: Tạo file jar của bạn.
- Chạy lệnh này trên máy bạn
```
git clone https://github.com/25020225-del/BTL---N7.git
cd ./BTL---N7
mvn package
```

Lệnh này sẽ tạo 2 file jar riêng của bạn, một file chạy của server, một file chạy của client.
### Bước 4: Chạy chương trình.
- Với mỗi file jar, chạy lệnh.
```
java -jar file-name.jar
```
- Nên chạy của server trước 2 giây, rồi chạy của client.
