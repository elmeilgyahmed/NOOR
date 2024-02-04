sudo apt-get update
sudo apt-get install -y maven openjdk-8-jdk
git clone https://github.com/elmeilgyahmed/NOOR.git
cd NOOR/speaking-with-NOOR/04-speech
mvn clean jetty:run

