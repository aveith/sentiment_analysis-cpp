from argparse import ArgumentParser
from util import Config, ThreadPool
from os import listdir 
from os.path import isfile, join
from time import sleep, time
#from confluent_kafka import Producer, KafkaError
import paho.mqtt.client as mqtt
import json
import socket
import random
import sys

# The callback for when the client receives a CONNACK response from the server.
def _on_mqtt_connect(client, userdata, flags, rc):
    print("Connected to MQTT with result code "+str(rc))

def _on_mqtt_disconnect(client, userdata, rc):
    print("Disconnected from MQTT with result code "+str(rc))

def _on_mqtt_publish(client, userdata, mid):
    print("Message published with MID code " + str(mid))


class Workload(object):
    #instanceNumber = 0

    def __init__(self, conf, end):
        self._config = conf
        self._jsons = [x for x in listdir(self.tweet_directory) if isfile(join(self.tweet_directory, x))]
        # self._log = "%s%s_%s_tweets.log" % (self.config.get('workload', 'logdir'),
        #                                     socket.gethostname(),
        #                                     str(Workload.instanceNumber).zfill(5))
        #Workload.instanceNumber += 1
        self._end= end

    @property
    def jsons(self):
        return self._jsons

    @property
    def config(self):
        return self._config

    @property
    def tweet_directory(self):
        return self.config.get('workload', 'tweetDirectory')

    @property
    def tweet_interarrival(self):
        return self.config.getfloat('workload', 'tweetInterarrival')

    @property
    def time_span(self):
        return self.config.getint('workload', 'timeSpan')

    @property
    def number_tweets(self):
        return self.config.getint('workload', 'numTweets')

    def publish(self):
        pass

    def connect(self):
        pass

    def disconnect(self):
        pass

    def run(self):
        self.connect()
        if not self._end:
          #print("Publishing tweets...")
          #log = open(self._log, 'w+')
          #log.write("%s;%s\n" % ("START_PUBLISH", "FINISH_PUBLISH"))

          if self.number_tweets < 1:
              print("Publishing tweets by time span "+str(self.time_span)+"...")
              startTime = time()
              while time() - startTime < self.time_span:
                #startPublish = int(time() * 1000)
                self.publish()
                #endPublish = int(time() * 1000)
                #log.write("%d;%d\n" % (startPublish, endPublish))
                sleep(self.tweet_interarrival)
              #log.close()
          else:
              print("Publishing tweets by number tweets " + str(self.number_tweets) + "...")
              counter = 0
              while counter != self.number_tweets:
                  self.publish()
                  counter+=1
                 # sleep(self.tweet_interarrival)

        else:
          self.publish(True)
        self.disconnect()


class WorkloadMQTT(Workload):

    def __init__(self, config, end= False):
        Workload.__init__(self, config, end)
        url = config.get('mqtt', 'serverURL')
        self.host = url[url.rfind('/') + 1 : url.rfind(':')]
        self.port = int(url[url.rfind(':') + 1:])
        self.topic = config.get('mqtt', 'topic')
        self.user = config.get('mqtt', 'userName')
        self.password = config.get('mqtt', 'password')
        self.keepalive = config.getint('mqtt', 'keepAlive')

    def connect(self):
        self.client = mqtt.Client()
        self.client.on_connect = _on_mqtt_connect
        self.client.on_disconnect = _on_mqtt_disconnect
        # client.on_publish = _on_mqtt_publish
        self.client.username_pw_set(self.user, password=self.password)
        self.client.connect(self.host, self.port, self.keepalive)
        self.client.loop_start()

    def publish(self, end= False):
        if not end:
          json_file = join(self.tweet_directory, random.choice(self.jsons))
          f = open(json_file)
          tweet = json.load(f)
          tweet["timestamp_ms"]= int(round(time() * 1000000))
          f.close()
          self.client.publish(self.topic, payload=json.dumps(tweet), qos=1, retain=False)
        else:
           # if self.number_tweets < 1:
          self.client.publish("exit", payload="", qos=1, retain=False)

    def disconnect(self):
        self.client.loop_stop()
        self.client.disconnect()

"""
class WorkloadKafka(Workload):

    def __init__(self, config):
        Workload.__init__(self, config)
        self.conf = {}
        for key in config.options('kafka'):
            self.conf[key] = config.get('kafka', key)

        conf_topic = {}
        for key in config.options('kafka.topic'):
            conf_topic[key] = config.get('kafka.topic', key)

        self.conf['default.topic.config'] = conf_topic

        del self.conf['topic']
        self.topic = config.get('kafka', 'topic')

    def connect(self):
        self.producer = Producer(self.conf)

    def publish(self):
        try:
            json_file = join(self.tweet_directory, random.choice(self.jsons))
            f = open(json_file)
            tweet = json.load(f)
            f.close()
            # TODO: Check other options, partition, timeout...
            self.producer.produce(topic=self.topic, value=json.dumps(tweet))
            # self.producer.flush()
        except KafkaError as e:
            print("Error publishing tweet to kafka topic: " + str(e))
        except:
            raise

    def disconnect(self):
        # TODO: Check whether there is anything to be cleaned up
        print("Cleaning up...")
"""

def parse_options():
    """Parse the command line options for workload creation"""
    parser = ArgumentParser(description='Create tweet events and publish them to MQTT.')
    parser.add_argument('--config', dest='config', type=str, required=True,
                        help='the configuration file to use')

    parser.add_argument('--client-type', dest='client_type', type=str, required=True,
                        help='the type of client to use', choices=['mqtt', 'kafka'])

    args = parser.parse_args()
    return args


def startWorkload(className, conf):
    wkl = className(conf)
    wkl.run()


def main():
    opts = parse_options()
    conf = Config(opts.config)
    num_threads = conf.getint('workload', 'numThreads')
    random.seed (a=900)

    if 'mqtt' in opts.client_type:
        WklClass = WorkloadMQTT
    #else:
    #    WklClass = WorkloadKafka

    workloads = []
    for i in range(0, num_threads):
        workloads.append(WklClass(conf))

    pool = ThreadPool(num_threads)
    for w in workloads:
        pool.add_task(w.run)

    pool.start_workers()
    pool.wait_completion()

    WklClass(conf, True).run()

if __name__ == "__main__":
    main()
