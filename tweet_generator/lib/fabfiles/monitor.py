from fabric.api import *

_tarfile="/tmp/scripts.tgz"
_piplibs="psutil"
_aptlibs="python-pip dtach"
_monconf="conf/workload.ini"
_monscript="lib/monitor.py"
_twiscript="lib/twitter.py"

def package(tarfile):
    local("tar cfz %s ." % tarfile)

@task
def install_dependencies():
    sudo("apt install %s" % _aptlibs)
    sudo("pip install %s" % _piplibs)

@task
def deploy():
    package(_tarfile)
    put(_tarfile, _tarfile)
    run("mkdir -p scripts")
    with cd("scripts"):
        run("tar zxvf %s " % _tarfile)
        run("rm %s " % _tarfile)


def background_run(command):
    run("dtach -n `mktemp -u /tmp/detach.XXXX` %s" % command)


@task
def monitor():
    deploy()
    with cd("scripts"):
        execute(background_run, "python %s --config %s --logfile log/`hostname`.log" % (_monscript, _monconf))

@task
def twitterKafka():
    deploy()
    with cd("scripts"):
        execute(background_run, "python %s --config %s --client-type kafka" % (_twiscript, _monconf))

@task
def twitterMQTT():
    deploy()
    with cd("scripts"):
        execute(background_run, "python %s --config %s --client-type mqtt" % (_twiscript, _monconf))

@task
def copy_logs():
    get("scripts/log/*.log", "log/")
