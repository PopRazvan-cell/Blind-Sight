#include "BluetoothSerial.h"

#if !defined(CONFIG_BT_ENABLED) || !defined(CONFIG_BLUEDROID_ENABLED)
#error Bluetooth is not enabled! Please run make menuconfig to and enable it
#endif

BluetoothSerial SerialBT;


// Sensor 1 pins
const int trigPin1 = 25;
const int echoPin1 = 27;

// Sensor 2 pinsa
const int trigPin2 = 26;
const int echoPin2 = 14;

// Sensor 3 pins
const int trigPin3 = 33;
const int echoPin3 = 32;

#define SOUND_SPEED 0.034


long duration;
int distanceCm;


void setup() {
  Serial.begin(115200);

  SerialBT.begin("ESP32"); //Bluetooth device name
  Serial.println("The device started, now you can pair it with bluetooth!");
  // Setup for sensor 1
  pinMode(trigPin1, OUTPUT);
  pinMode(echoPin1, INPUT);

  // Setup for sensor 2
  pinMode(trigPin2, OUTPUT);
  pinMode(echoPin2, INPUT);

  // Setup for sensor 3
  pinMode(trigPin3, OUTPUT);
  pinMode(echoPin3, INPUT);
}

void loop() {
  if (Serial.available()) {
    SerialBT.write(Serial.read());
  }
  if (SerialBT.available()) {
    Serial.write(SerialBT.read());
  }
  

  readSensor(trigPin1, echoPin1, 1);
  readSensor(trigPin2, echoPin2, 2);
  readSensor(trigPin3, echoPin3, 3);

  delay(5000); // Wait 5 seconds before next reading
}

void readSensor(int trigPin, int echoPin, int sensorNumber) {
  // Clear the trigPin
  digitalWrite(trigPin, LOW);
  delayMicroseconds(2);
  
  // Trigger the sensor
  digitalWrite(trigPin, HIGH);
  delayMicroseconds(10);
  digitalWrite(trigPin, LOW);

  // Read the echoPin
  duration = pulseIn(echoPin, HIGH);

  // Calculate distance
  distanceCm = duration * SOUND_SPEED / 2;
  

  // Print the result
  SerialBT.print("Sensor ");
  SerialBT.print(sensorNumber);
  SerialBT.print(" - Distance (cm): " );
  SerialBT.print(distanceCm);
  SerialBT.print('\n');
  
}
