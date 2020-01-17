from argparse import ArgumentParser
from urllib import URLopener
from subprocess import call
import sys

TMP_DIR = "/tmp/"
EDGENT_VERSION = "1.1.0"
EDGENT_MIRROR = "http://apache.mediamirrors.org/incubator/edgent/"
EDGENT_TAR = "apache-edgent-%s-incubating-bin.tgz" % EDGENT_VERSION
EDGENT_TAR_URL = "%s/%s-incubating/binaries/%s" % (EDGENT_MIRROR, EDGENT_VERSION, EDGENT_TAR)
EDGENT_DIR = "%s/edgent-%s" % (TMP_DIR, EDGENT_VERSION)

EDGENT_GROUP_IP = "org.apache.edgent"
EDGENT_REPO_ID = "local-maven-repo"
EDGENT_ARTIFACTS = {"api.execution" : "lib/edgent.api.execution.jar",
                    "api.function": "lib/edgent.api.function.jar",
                    "api.graph" : "lib/edgent.api.graph.jar",
                    "api.oplet" : "lib/edgent.api.oplet.jar",
                    "api.topology" : "lib/edgent.api.topology.jar",
                    "api.window" : "lib/edgent.api.window.jar",
                    "providers.development" : "lib/edgent.providers.development.jar",
                    "providers.direct" : "lib/edgent.providers.direct.jar",
                    "providers.iot" : "lib/edgent.providers.iot.jar",
                    "runtime.appservice" : "lib/edgent.runtime.appservice.jar",
                    "runtime.etiao" : "lib/edgent.runtime.etiao.jar",
                    "runtime.jmxcontrol" : "lib/edgent.runtime.jmxcontrol.jar",
                    "runtime.jobregistry" : "lib/edgent.runtime.jobregistry.jar",
                    "runtime.jsoncontrol" : "lib/edgent.runtime.jsoncontrol.jar",
                    "console.server" : "console/server/lib/edgent.console.server.jar",
                    "spi.graph" : "lib/edgent.spi.graph.jar",
                    "spi.topology" : "lib/edgent.spi.topology.jar",
                    "analytics.sensors" : "analytics/sensors/lib/edgent.analytics.sensors.jar",
                    "analytics.math3" : "analytics/math3/lib/edgent.analytics.math3.jar",
                    "connectors.command" : "connectors/command/lib/edgent.connectors.command.jar",
                    "connectors.common" : "connectors/common/lib/edgent.connectors.common.jar",
                    "connectors.file" : "connectors/file/lib/edgent.connectors.file.jar",
                    "connectors.http" : "connectors/http/lib/edgent.connectors.http.jar",
                    "connectors.iot": "connectors/iot/lib/edgent.connectors.iot.jar",
                    "connectors.iotp": "connectors/iotp/lib/edgent.connectors.iotp.jar",
                    "connectors.jdbc": "connectors/jdbc/lib/edgent.connectors.jdbc.jar",
                    "connectors.kafka": "connectors/kafka/lib/edgent.connectors.kafka.jar",
                    "connectors.mqtt": "connectors/mqtt/lib/edgent.connectors.mqtt.jar",
                    "connectors.pubsub": "connectors/pubsub/lib/edgent.connectors.pubsub.jar",
                    "connectors.serial": "connectors/serial/lib/edgent.connectors.serial.jar",
                    "connectors.wsclient": "connectors/wsclient/lib/edgent.connectors.wsclient.jar",
                    "utils.metrics": "utils/metrics/lib/edgent.utils.metrics.jar",
                    "utils.streamscope": "utils/streamscope/lib/edgent.utils.streamscope.jar",
                    "test.fvtiot": "test/fvtiot/lib/edgent.test.fvtiot.jar"}


def exec_cmd(cmd):
    try:
        retcode = call(cmd, shell=True)
        if retcode < 0:
            print >> sys.stderr, "Child was terminated by signal", -retcode
        else:
            print >> sys.stderr, "Child returned", retcode
    except:
        print >> sys.stderr, "Execution failed:"


def download_edgent():
    file = URLopener()
    file.retrieve(EDGENT_TAR_URL, TMP_DIR + "/" + EDGENT_TAR)
    exec_cmd("cd %s ; tar zxvf %s/%s" % (TMP_DIR, TMP_DIR, EDGENT_TAR))


def create_maven_repo(repo_dir):
    exec_cmd("mkdir -p %s" % repo_dir)
    for artifactId, jar_file in EDGENT_ARTIFACTS.items():
        cmd = "mvn deploy:deploy-file -DgroupId=%s -DartifactId=%s " \
              "-Dversion=%s -Durl=file:%s -DrepositoryId=local-maven-repo " \
              "-DupdateReleaseInfo=true -Dfile=%s/java8/%s" % \
              (EDGENT_GROUP_IP, artifactId, EDGENT_VERSION,
               repo_dir, EDGENT_DIR, jar_file)
        exec_cmd(cmd)


def parse_options():
    """Parse the command line options for the edgent maven repository creation"""
    parser = ArgumentParser(description='Create a local Maven repository for Apache Edgent.')
    parser.add_argument('--directory', dest='directory', type=str, required=True,
                        help='the directory where the local repository will be placed')

    args = parser.parse_args()
    return args


def main():
    opts = parse_options()
    download_edgent()
    create_maven_repo(opts.directory + EDGENT_REPO_ID)