import psutil
from time import sleep, time
from argparse import ArgumentParser
from util import Config


def parse_options():
    """Parse the command line options for workload creation"""
    parser = ArgumentParser(description='Create tweet events and publish them to MQTT.')
    parser.add_argument('--config', dest='config', type=str, required=True,
                        help='the configuration file to use')

    parser.add_argument('--logfile', dest='logfile', type=str, required=True,
                        help='the log file to which to write')

    args = parser.parse_args()
    return args

''
def monitor(interval, logfile, timespan):
    try:
        log = open(logfile, 'w')
        log.write("%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s\n" % ("TIMESTAMP",
                                                                                                       "CPU_USAGE",
                                                                                                       "MEM_TOTAL",
                                                                                                       "MEM_AVAILABLE",
                                                                                                       "MEM_USED",
                                                                                                       "MEM_FREE",
                                                                                                       "MEM_ACTIVE",
                                                                                                       "MEM_INACTIVE",
                                                                                                       "MEM_BUFFERS",
                                                                                                       "MEM_CACHE",
                                                                                                       "MEM_SHARED",
                                                                                                       "MEM_PERCENT",
                                                                                                       "SWAP_TOTAL",
                                                                                                       "SWAP_USED",
                                                                                                       "SWAP_FREE",
                                                                                                       "SWAP_PERCENT",
                                                                                                       "SWAP_SIN",
                                                                                                       "SWAP_US_OUT",
                                                                                                       "NET_BYTES_SENT_ETH1",
                                                                                                       "NET_BYTES_REC_ETH1",
                                                                                                       "NET_PACK_SENT_ETH1",
                                                                                                       "NET_PACK_REC_ETH1",
                                                                                                       "NET_BYTES_SENT_ETH2",
                                                                                                       "NET_BYTES_REC_ETH2",
                                                                                                       "NET_PACK_SENT_ETH2",
                                                                                                       "NET_PACK_REC_ETH2"
                                                                                                       ))
        log.flush()
        start_time = time()
        while time() - start_time < timespan:
            mem_stats = psutil.virtual_memory()
            mem_swap = psutil.swap_memory()
            net_io = psutil.net_io_counters(pernic=True)

            now = str(int(time() * 1000))

            cpu_percent = str(psutil.cpu_percent())

            mem_total = str(mem_stats.total)
            mem_available = str(mem_stats.available)
            mem_used = str(mem_stats.used)
            mem_free = str(mem_stats.free)
            mem_active = str(mem_stats.active)
            mem_inactive = str(mem_stats.inactive)
            mem_buffers = str(mem_stats.buffers)
            mem_cached = str(mem_stats.cached)
            mem_shared = str(mem_stats.shared)
            mem_percent = str(mem_stats.percent)

            swap_total = str(mem_swap.total)
            swap_used = str(mem_swap.used)
            swap_free = str(mem_swap.free)
            swap_percent = str(mem_swap.percent)
            swap_sin = str(mem_swap.sin)
            swap_scout = str(mem_swap.sout)

            net_bytes_sent_eth1 = str(net_io["eth1"].bytes_sent)
            net_bytes_recd_eth1 = str(net_io["eth1"].bytes_recv)
            net_packets_sent_eth1 = str(net_io["eth1"].packets_sent)
            net_packets_rect_eth1 = str(net_io["eth1"].packets_recv)

            net_bytes_sent_eth2 = 0
            net_bytes_recd_eth2 = 0
            net_packets_sent_eth2 = 0
            net_packets_rect_eth2 = 0

            if "eth2" in net_io:
                net_bytes_sent_eth2 = str (net_io["eth2"].bytes_sent)
                net_bytes_recd_eth2 = str (net_io["eth2"].bytes_recv)
                net_packets_sent_eth2 = str (net_io["eth2"].packets_sent)
                net_packets_rect_eth2 = str (net_io["eth2"].packets_recv)

            log.write("%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s;%s" % (now,
                                                                                                         cpu_percent,
                                                                                                         mem_total,
                                                                                                         mem_available,
                                                                                                         mem_used,
                                                                                                         mem_free,
                                                                                                         mem_active,
                                                                                                         mem_inactive,
                                                                                                         mem_buffers,
                                                                                                         mem_cached,
                                                                                                         mem_shared,
                                                                                                         mem_percent,
                                                                                                         swap_total,
                                                                                                         swap_used,
                                                                                                         swap_free,
                                                                                                         swap_percent,
                                                                                                         swap_sin,
                                                                                                         swap_scout,
                                                                                                         net_bytes_sent_eth1,
                                                                                                         net_bytes_recd_eth1,
                                                                                                         net_packets_sent_eth1,
                                                                                                         net_packets_rect_eth1,
                                                                                                         net_bytes_sent_eth2,
                                                                                                         net_bytes_recd_eth2,
                                                                                                         net_packets_sent_eth2,
                                                                                                         net_packets_rect_eth2
                                                                                                         ))
            log.write("\n")
            log.flush()

            sleep(interval)
    except KeyboardInterrupt:
        print("Stopping the monitoring process...")
    except:
        raise
    finally:
        log.close()


def main():
    opts = parse_options()
    conf = Config(opts.config)
    i = conf.getint('monitoring', 'monitorInterval')
    t = conf.getint('monitoring', 'timeSpan')
    monitor(interval=i, logfile=opts.logfile, timespan=t)


if __name__ == "__main__":
    main()
