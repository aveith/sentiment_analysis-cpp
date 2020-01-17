Sentiment_analysis

This application implements in Apache Edgent the Sentiment Application, which analyzes the sentiment of the tweets arriving from the input stream. The actual version is based on APACHE MAVEN.

A) DOWNLOAD EDGENT
wget http://apache.crihan.fr/dist/incubator/edgent/1.1.0-incubating/apache-edgent-1.1.0-incubating-src.tgz

B) INSTALL GRADLE IS A REQUIREMENT
sudo apt install gradle

C)COMPILE F

C) BASIC APPLICATION RUNNING 
java -jar target/sentiment-analysis-1.0-SNAPSHOT-jar-with-dependencies.jar ../../tweet_generator/conf/workload.ini /home/veith/test.log

D) COMPILING
mvn clean install
