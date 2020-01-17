library(dplyr)
library(data.table)
library(sqldf)
library(gsubfn)
library(proto)
library(RSQLite)
library(splitstackshape)

OUTPUT <- data.table()

#TIME TO BE ANALYSED
TIMESTAP_INI   = 60000
TIMESTAMP_FINI = 180000

#GO THROUGH ALL FOLDER OF THE TESTBED
for (edgeOperators in 0:5){
  
  # FOLDER OF THE RESULTS
  folder<- ""
  folder <- paste(paste("/home/veith/1489503733_10/",edgeOperators, sep = ""),"/", sep = "")
  setwd(dir = paste(folder,"/", sep = ""))
  
  # LOAD THE FILES WHICH REPRESENT THE DATA SOURCE
  file_list <- list.files(path=folder, pattern="*tweets.log")
  
  # SUMMARIZE THE DATA SOURCE FILES
  DATA <- 
    do.call("rbind", 
            lapply(file_list, 
                   function(x) 
                     read.csv(paste(folder, x, sep=''),
                              sep = ";", quote = "\"", 
                              stringsAsFactors = FALSE)))

  
  # GET THE EXECUTION PERIOD (TIMESTAMP)
  RESULT <- sqldf("select 
                  max(FINISH_PUBLISH) as MAX 
                  , min(START_PUBLISH) as MIN
                  , count() as COUNT
               from DATA")
  

  # COUNT OF TWEETS THAT THE DATA SOURCE SENT IN THE PERIOD
  COUNT <- sqldf(paste(paste(paste('select 
                                      count() as COUNT
                                   from DATA
                                     where START_PUBLISH >= ', RESULT$MIN+TIMESTAP_INI, sep = " "), 
                                                         " and START_PUBLISH <=", sep=""),
                                                   RESULT$MIN+TIMESTAMP_FINI, sep = " "))  
  
  
  # LOAD THE FILES WHICH REPRESENTS THE RESOURCE USAGE
  PERFORMANCE1 <- read.csv(file="albacore-1.log", header=TRUE, sep=";")
  PERFORMANCE2 <- read.csv(file="albacore-2.log", header=TRUE, sep=";")
  PERFORMANCE3 <- read.csv(file="albacore-3.log", header=TRUE, sep=";")
  PERFORMANCE4 <- read.csv(file="albacore-4.log", header=TRUE, sep=";")
  
  
  # SUMMARIZE THE RESOURCES USAGES FROM THE MACHINES
  PERFORMANCE1 <-  sqldf(paste(paste(paste('select avg(CPU_USAGE) as CPU_USAGE, 
                                                   avg(MEM_PERCENT) as MEM_PERCENT 
                                            from PERFORMANCE1 
                                            where TIMESTAMP >= ', RESULT$MIN+TIMESTAP_INI, sep = " "), 
                                     " and TIMESTAMP <=", sep=""),
                               RESULT$MIN+TIMESTAMP_FINI, sep = " "))
  
  PERFORMANCE2 <-  sqldf(paste(paste(paste('select avg(CPU_USAGE) as CPU_USAGE, 
                                                   avg(MEM_PERCENT) as MEM_PERCENT 
                                            from PERFORMANCE2 
                                            where TIMESTAMP >= ', RESULT$MIN+TIMESTAP_INI, sep = " "), 
                                     " and TIMESTAMP <=", sep=""),
                               RESULT$MIN+TIMESTAMP_FINI, sep = " "))
  
  PERFORMANCE3 <-  sqldf(paste(paste(paste('select avg(CPU_USAGE) as CPU_USAGE, 
                                                   avg(MEM_PERCENT) as MEM_PERCENT 
                                            from PERFORMANCE3 
                                            where TIMESTAMP >= ', RESULT$MIN+TIMESTAP_INI, sep = " "), 
                                     " and TIMESTAMP <=", sep=""),
                               RESULT$MIN+TIMESTAMP_FINI, sep = " "))
  
  PERFORMANCE4 <-  sqldf(paste(paste(paste('select avg(CPU_USAGE) as CPU_USAGE, 
                                                   avg(MEM_PERCENT) as MEM_PERCENT 
                                            from PERFORMANCE4
                                            where TIMESTAMP >= ', RESULT$MIN+TIMESTAP_INI, sep = " "), 
                                     " and TIMESTAMP <=", sep=""),
                               RESULT$MIN+TIMESTAMP_FINI, sep = " "))
  
  
  # CREATE A STRING TO GET THE KAFKA PARTIONS DATA
  LINE <- 5
  TEXT <- ""
  
  for (i in 1:16) {
    if (LINE < 10){
        TEXT <- paste(TEXT,"V1_","0", LINE, "+", sep = "") 
      } else{
        TEXT <- paste(TEXT,"V1_",LINE, "+", sep = "") 
      }
    
    LINE <- LINE + 1
    
    if (LINE < 10){
      TEXT <- paste(TEXT,"V1_","0", LINE, "+", sep = "") 
    } else{
      TEXT <- paste(TEXT,"V1_",LINE, "+", sep = "") 
    }
    
    LINE <- LINE + 1
    LINE <- LINE + 1
  }
  
  QUEUE_MQTT <- TEXT 
  
  LINE = 55
  TEXT = ""
  
  for (i in 1:4) {
    
    if (LINE < 10){
      TEXT = paste(TEXT,"V1_","0", LINE, "+", sep = "") 
    } else{
      TEXT = paste(TEXT,"V1_",LINE, "+", sep = "") 
    }
    
    LINE = LINE + 1
    
    if (LINE < 10){
      TEXT = paste(TEXT,"V1_","0", LINE, "+", sep = "") 
    } else{
      TEXT = paste(TEXT,"V1_",LINE, "+", sep = "") 
    }
    
    LINE = LINE + 1
    LINE = LINE + 1
  }
  
  QUEUE_SINK <- TEXT 
  
  
  # LOAD THE KAFKA PARTITIONS DATA
  KAFKA_QUEUE <- read.csv(file="kafka-mqtt.log", header=FALSE, fill = TRUE)
  
  # PREPARE THE DATA TO SPLIT IN COLUMNS
  KAFKA_QUEUE[] <- lapply(KAFKA_QUEUE, gsub, pattern = " ", replacement = ";", fixed = TRUE)
  KAFKA_QUEUE[] <- lapply(KAFKA_QUEUE, gsub, pattern = ":", replacement = ";", fixed = TRUE)
  KAFKA_QUEUE <-cSplit(KAFKA_QUEUE, splitCols = "V1", sep = ";", direction = "wide", drop = FALSE)
  
  # GET THE MINIMAL AND MAXIMUM TIMESTAMP - COMPARING WITH THE TESTBED PERIOD
  TIMESTAMP <-  sqldf(paste(paste(paste('select min(V1_01) as TIMESTAMP_START,
                                                max(V1_01) as TIMESTAMP_FINISH  
                                            from KAFKA_QUEUE
                                            where V1_01 >= ', ((RESULT$MIN+TIMESTAP_INI)/1000), sep = " "), 
                                  " and V1_01 <=", sep=""),
                            ((RESULT$MIN+TIMESTAMP_FINI)/1000), sep = " "))
  
  # GET THE INITIAL POSITION FROM THE QUEUES (SINK AND MQTT)
  QUEUE_START <- sqldf(paste('select ', substr(QUEUE_MQTT, 1,  nchar(QUEUE_MQTT)-1), ' as MQTT, ',
                       substr(QUEUE_SINK, 1,  nchar(QUEUE_SINK)-1), ' as SINK ',
                              ' from KAFKA_QUEUE where V1_01 = ', TIMESTAMP$TIMESTAMP_START, 
                             sep = " "))
  
  # GET THE FINISH POSITION FROM THE QUEUES (SINK AND MQTT)
  QUEUE_FINISH <- sqldf(paste('select ', substr(QUEUE_MQTT, 1,  nchar(QUEUE_MQTT)-1), ' as MQTT, ',
                             substr(QUEUE_SINK, 1,  nchar(QUEUE_SINK)-1), ' as SINK ',
                             ' from KAFKA_QUEUE where V1_01 = ', TIMESTAMP$TIMESTAMP_FINISH, 
                             sep = " "))

  # SUMMARIZE THE TEST RESULTS
  OUTPUT <- rbind(OUTPUT, data.table(EDGE_OPERATORS = edgeOperators,
                                     TIMESTAMP_START = RESULT$MIN+TIMESTAP_INI, 
                                     TIMESTAMP_END = RESULT$MIN+TIMESTAMP_FINI, 
                                     CLOUD_CPU = PERFORMANCE1$CPU_USAGE, 
                                     CLOUD_MEM = PERFORMANCE1$MEM_PERCENT, 
                                     GATEWAY_CPU = PERFORMANCE2$CPU_USAGE, 
                                     GATEWAY_MEM = PERFORMANCE2$MEM_PERCENT,
                                     SOURCE_CPU = PERFORMANCE3$CPU_USAGE, 
                                     SOURCE_MEM = PERFORMANCE3$MEM_PERCENT,
                                     SLAVE_CPU = PERFORMANCE4$CPU_USAGE, 
                                     SLAVE_MEM = PERFORMANCE4$MEM_PERCENT,
                                     TWEETS_SOURCE = COUNT$COUNT,
                                     MQTT = QUEUE_FINISH$MQTT-QUEUE_START$MQTT,
                                     SINK = QUEUE_FINISH$SINK-QUEUE_START$SINK))
  
  # CLEAN VARIABLES
  rm(PERFORMANCE1, PERFORMANCE2, PERFORMANCE3, 
     PERFORMANCE4, DATA, COUNT, RESULT, TIMESTAMP, 
     QUEUE_START, QUEUE_FINISH, QUEUE_SINK,
     KAFKA_QUEUE, QUEUE_MQTT, LINE, TEXT,
     file_list)
}

# SAVE FILE
write.csv(OUTPUT, file = "~/output.csv")