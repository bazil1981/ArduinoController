#include <SPI.h>
#include <Ethernet.h>

// MAC адрес (уникальный для вашего shield)
byte mac[] = { 0xDE, 0xAD, 0xBE, 0xEF, 0xFE, 0xED };

// IP адрес (подберите под свою сеть)
IPAddress ip(192, 168, 1, 199);
IPAddress gateway(192, 168, 1, 1);
IPAddress subnet(255, 255, 255, 0);

// Порт для UDP
const unsigned int localPort = 8888;

EthernetUDP udp;

// Пин для встроенного LED (13)
const int ledPin = 13;

// Буфер для входящих данных
char packetBuffer[UDP_TX_PACKET_MAX_SIZE];

void setup() {
  Serial.begin(9600);
  pinMode(ledPin, OUTPUT);
  
  // Инициализация Ethernet
  Ethernet.begin(mac, ip, gateway, subnet);
  udp.begin(localPort);
  
  Serial.print("Arduino IP: ");
  Serial.println(Ethernet.localIP());
  Serial.println("Ready to receive commands");
}

void loop() {
  int packetSize = udp.parsePacket();
  
  if (packetSize) {
    // Читаем команду
    int len = udp.read(packetBuffer, UDP_TX_PACKET_MAX_SIZE);
    if (len > 0) {
      packetBuffer[len] = '\0';
    }
    
    String command = String(packetBuffer);
    command.trim();
    
    Serial.print("Received: ");
    Serial.println(command);
    
    // Обработка команд
    if (command == "LED_ON") {
      digitalWrite(ledPin, HIGH);
      sendResponse("OK: LED turned ON");
    } 
    else if (command == "LED_OFF") {
      digitalWrite(ledPin, LOW);
      sendResponse("OK: LED turned OFF");
    }
    else if (command == "STATUS") {
      String status = digitalRead(ledPin) ? "ON" : "OFF";
      sendResponse("STATUS: LED is " + status);
    }
    else if (command.startsWith("PWM:")) {
      int value = command.substring(4).toInt();
      if (value >= 0 && value <= 255) {
        analogWrite(9, value); // Используем пин 9 для PWM
        sendResponse("OK: PWM set to " + String(value));
      }
    }
    else {
      sendResponse("ERROR: Unknown command");
    }
  }
}

void sendResponse(String message) {
  udp.beginPacket(udp.remoteIP(), udp.remotePort());
  udp.print(message);
  udp.endPacket();
}