const int redPin = 7;
const int greenPin = 10;

void setup() {
  pinMode(redPin, OUTPUT);
  pinMode(greenPin, OUTPUT);

  digitalWrite(greenPin, HIGH); // Începem cu verde aprins
  delay(5000); // Așteptăm 5 secunde
  digitalWrite(greenPin, LOW);  // Oprim verdele

  digitalWrite(redPin, HIGH);   // Aprindem roșul
  delay(10000); // Așteptăm 5 secunde
  digitalWrite(redPin, LOW);    // Oprim roșul
}

void loop() {
  // Aici putem adăuga un ciclu continuu, dacă vrem să repete aceleași secvențe
  digitalWrite(greenPin, HIGH);
  delay(15000);  // Verde aprins 5 secunde
  digitalWrite(greenPin, LOW);

  digitalWrite(redPin, HIGH);
  delay(20000);  // Roșu aprins 5 secunde
  digitalWrite(redPin, LOW);
}
