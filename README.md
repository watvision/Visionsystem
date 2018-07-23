# WatVision Source Code

This repository contains all of the source code for the WatVision product, which aims to empower every blind or visually impaired person to use any touch screen. The project was part of a Fourth Year Design Project for my Mechatronics Engineering Degree at the University of Waterloo.

## How does it work?

The WatVision system includes an app, and a ring that you wear on your finger. You run the app, and point the camera at the touch screen you wish to interact with. The app will tell you when it's seen and understood the seen. After that, you can move the finger wearing the ring around the screen and it will read out to you whatever is underneath your finger. If you want to learn more (or see it in action) check out the website [here.](https://watvision.github.io/)

## What does the code exactly do?

This repository contains the code to support the system described above. Each of the classes written in /app/src/main/java/com/watvision/mainapp/ will be described here. This includes:
- An Android based interface to interact with the WatVision System including:
    - Interfacing with the bluetooth on the WatVision glove (WatBlueToothService.java)
    - Visualizing what the camera sees on the Android UI (MainActivity.java)
- Tracking the position of the menu and the finger from an input image (MenuAndFingerTracking.java)
- Analyzing an inputted screen to obtain its text and other information (ScreenAnalyzer.java). This includes:
    - Defining a screen (Screen.java)
    - Defining elements on the screen (ScreenElement.java)
- Controlling what level of vibrations to send to the WatVision glove (VibrateControls.java)

## Why make this open source / What can I do with this repo?

Our main goal is to get what we worked on: making touch screens more accessible, out in the world! That's why we put the MIT license on the repo so anyone could use our code and hopefully make the world a more accessible place. 

If you have any questions about our code or want to get in contact with us email: watvisionteam@gmail.com

# Dependencies

- Google Cloud: You will need a google cloud account and have to put your API key into the ScreenAnalyzer.java class to perform the text recognition
- ArUco: A library that helps with Aruco detection. Source code is included in this repository based on this [github repo](https://github.com/sidberg/aruco-android) which seems based upon [this library](https://www.uco.es/investiga/grupos/ava/node/26)
- OpenCV: Relevant code is included in this repository

# Install

- Clone the repository onto your machine
- Open it up in Android Studio
- Replace "YOUR_API_KEY_HERE" in ScreenAnalyzer.java to your Google Cloud API Key
- Finished!