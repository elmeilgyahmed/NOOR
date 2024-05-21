# NOOR Customized Egyptian Voice Interactive Chatbot

# Usage
Try using the NOOR web at (https://34.171.33.125:8443/#)
# Contribution 
Installation:
Install Java and Maven:

    sudo apt-get update
    sudo apt-get install -y maven openjdk-8-jdk
    git clone https://github.com/elmeilgyahmed/NOOR.git
    cd NOOR/speaking-with-NOOR/04-speech
    mvn clean jetty:run
    
# Configuring on GCP:
The maven jetty plugin listens for http and https connections on ports `8080`
and `8443` by default. Open them up on the Compute Engine firewall:

    gcloud compute firewall-rules create dev-ports \
        --allow=tcp:8080,tcp:8443 \
        --source-ranges=0.0.0.0/0

## Step 1

Create whatever webapp you want on Google Compute Engine that can serve static javascript, a
static `index.html`, and a dynamic controller.

When using `getUserInput` to access microphone input, browsers require the
connection to be `https`. Configure the app for https - for development
purposes, a self-signed certificate suffices.

Generate a self-signed SSL cert for now:
سس
    keytool -genkey -alias jetty -keyalg RSA \
        -keystore src/main/resources/jetty.keystore \
        -storepass secret -keypass secret -dname "CN=localhost"

Then run:

    cd 04-speech/
    mvn clean jetty:run

## Step 2

Stream the raw audio data from the client to the server using WebSockets.

When on an `https` webpage, websocket connections are required to be secure
`wss`.
port:8443

## Step 3

Use the Speech API to stream transcriptions of speech to the client.

## Disclaimer

This is not an official Google product
