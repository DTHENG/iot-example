# IoT Example

Blinks the current bitcoin price from 5 LED's.

### Requirements

* Raspberry Pi 3
* Breadboard
* 5 Red LED's
* 1 Yellow LED
* 8 Male to Female Jumper Wires
* 1 Jumper Wire
* 6 330R Resistors

### Install

* Clone the repo
* Run `mvn clean package` from the `iot-example` package

### Run

* Take the `iot-example.jar` from the `target` package
* Copy it to your Raspberry Pi
* Run the jar file on the Raspberry Pi:

    `java -jar iot-example.jar`

### Authors

* Daniel Thengvall