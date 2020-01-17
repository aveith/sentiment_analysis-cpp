package fr.inria.sentiment_analysis;


import com.jsoniter.JsonIterator;
import fr.inria.sentiment_analysis.data.Tweet;
import fr.inria.sentiment_analysis.nlp.NegativeWords;
import fr.inria.sentiment_analysis.nlp.PositiveWords;
import fr.inria.sentiment_analysis.nlp.StopWords;
import fr.inria.sentiment_analysis.utils.Accumulator;
import org.apache.commons.lang3.StringUtils;
import org.apache.edgent.console.server.HttpServer;
import org.apache.edgent.function.BiFunction;
import org.apache.edgent.function.Function;
import org.apache.edgent.function.Supplier;
import org.apache.edgent.function.ToIntFunction;
import org.apache.edgent.metrics.Metrics;
import org.apache.edgent.providers.development.DevelopmentProvider;
import org.apache.edgent.providers.direct.DirectProvider;
import org.apache.edgent.topology.TStream;
import org.apache.edgent.topology.Topology;
import org.apache.edgent.topology.plumbing.PlumbingStreams;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

public class App {
    private static final Pattern TEXT_PATTERN = Pattern.compile("[^a-zA-Z\\s]");


    public long number_tweets = 0;
    private static Random r = new Random();
    static int fileCounter = 0;
    static int fileTotal = 0;


    //private static final Logger logger = LoggerFactory.getLogger(App.class);
    private DirectProvider provider;
    private Topology topology;
    private Properties props;
    private String fileResult;
    private File tweetDirectory = null;

    private BufferedWriter logBuffer;
    private String pipelineType = "v1";
    private long interArrivalTime = 1;
    private int parallelInstancesF1 = 0;
    private int parallelInstancesF2 = 0;
    //static ObjectMapper objectMapper = null;

    private String pChain = null;
    private static boolean readDirect = false;
    private  boolean saveLog = false;
    private  boolean isolate = false;
    //private static Accumulator acm;



    //----------------------------------------------------------------------------------//
    //Variable declaration                                                              //
    //----------------------------------------------------------------------------------//

    //Atomic Variables for Metrics Control
    //static AtomicBoolean first = new AtomicBoolean(true);
    static AtomicLong n_tweets = new AtomicLong(0);
    static AtomicLong t1 = new AtomicLong(0);


    public static void main(String[] args) throws Exception {
        /*
        if (args.length != 6) {
            logger.error("Arguments are Missing: (1)properties file, (2)result file, (3) application type, (4) inter arrival time, (5) pipeline chain, (6) parallel instances Farm 1, and (7) parallel instances Farm 2");
            throw new Exception("Enough arguments " + args.length);
        }
        */
        App app = null;

        //set up the parameter for execution: we have 3 flows (v1, v2, and v3), each one composed with its features. The application used is the SentimentAnalisis which has 6 operators.
        //  - 1: Parser
        //  - 2: Filter
        //  - 3: Standardization
        //  - 4: Stemmer
        //  - 5: Positive and Negative scorer
        //  - 6: Final Scorer
        // In this way, multiple combinations of parameters are applied as well as settings  for the graph.
        //The parameters are explained above.

        //Also the evaluation is carried out for 1 minute.

        if(args[2].equals("v1")) {
            //Sequential flow: 1->2->3->4->5->6: Edgent uses a PeriodcSource operator, in this way, it creates a thread for each operator
            app = new App(args[0], args[1], args[2], args[3], args[4]);

        }else if (args[2].equals("v2")){
            // Two farms (pool of threads) for processing: (Farm1)1-> 2-> (Farm2)3,4,5,6
            app = new App(args[0], args[1], args[2], args[3], args[4], args[5], args[6]);

        }else if (args[2].equals("v3")){
            // One farm (pool of threads) for processing: (Farm1)1,3,4,5,6 -> 2
            app = new App(args[0], args[1], args[2], args[3], args[4], args[5]);

        } else {
            throw new Exception("Invalid pipeline");
        }

        app.run();
    }

    private void basicData(String propertiesFile, String resultFile, String applicationType, String interArrivalTime, String pipelineChain) throws Exception{
        this.props = new Properties();
        this.props.load(Files.newBufferedReader(new File(propertiesFile).toPath()));

        //A different variable was created because there were losses between the tweet generator and the MQTT
        this.number_tweets = 40000000L;
        this.pChain = pipelineChain;
        this.readDirect = pipelineChain.contains("X");
        this.saveLog = pipelineChain.contains("L");
        this.isolate = pipelineChain.contains("I");

        this.tweetDirectory = new File(this.props.getProperty("tweetDirectory"));
        this.fileResult = resultFile;
        this.interArrivalTime = Long.parseLong(interArrivalTime);
        if (this.interArrivalTime == 0) this.interArrivalTime = 1;
        this.pipelineType = applicationType;
    }

    public App(String propertiesFile, String resultFile, String applicationType, String interArrivalTime, String pipelineChain) throws Exception {
        //----------------------------------------------------------------------------------//
        //Initializes the global variables according to the input values                    //
        //----------------------------------------------------------------------------------//
        basicData(propertiesFile, resultFile, applicationType, interArrivalTime, pipelineChain);
    }

    public App(String propertiesFile, String resultFile, String applicationType, String interArrivalTime, String pipelineChain, String parallelInstancesF1, String parallelInstancesF2) throws Exception {
        //----------------------------------------------------------------------------------//
        //Initializes the global variables according to the input values                    //
        //----------------------------------------------------------------------------------//
        basicData(propertiesFile, resultFile, applicationType, interArrivalTime, pipelineChain);
        this.parallelInstancesF1 = Integer.parseInt(parallelInstancesF1);
        this.parallelInstancesF2 = Integer.parseInt(parallelInstancesF2);
    }

    public App(String propertiesFile, String resultFile, String applicationType, String interArrivalTime, String pipelineChain, String parallelInstancesF1) throws Exception {
        //----------------------------------------------------------------------------------//
        //Initializes the global variables according to the input values                    //
        //----------------------------------------------------------------------------------//
        basicData(propertiesFile, resultFile, applicationType, interArrivalTime, pipelineChain);
        this.parallelInstancesF1 = Integer.parseInt(parallelInstancesF1);
    }

    public void run() throws Exception {
        //----------------------------------------------------------------------------------//
        //Initialization                                                                    //
        //----------------------------------------------------------------------------------//
        //Create the log file
        File logFile = new File(this.fileResult);
        if (saveLog) {
            logBuffer = new BufferedWriter(new FileWriter(logFile, false));
        }

        //Tweets to be published
        File[] subs = this.tweetDirectory.listFiles();
        fileTotal = subs.length;
        byte[] lines = null;


        if (readDirect){
            //This block of code was applied just to control the IO contention when submitting the tweets. Otherwise, the tweets are read from the dataset when
            //  the flow is running
            int value = 0;
            for (int i = 0; i < subs.length; i++) {

                byte[] linesMax = readFile(subs[i], Charset.defaultCharset());
                Tweet t = JsonIterator.parse(linesMax).read(Tweet.class);
                if (t.getText().length() > value) {
                    value = t.getText().length();
                    lines = linesMax;

                }
            }
        }

        //Accumulator
        //acm = new Accumulator();

        //Initialization of system variables (Apache Edgent)
        provider = new DirectProvider();
        //provider = new DevelopmentProvider();

        topology = provider.newTopology(" sentimentAnalysisTopology");

        //----------------------------------------------------------------------------------//
        //Pipeline Creation                                                                 //
        //----------------------------------------------------------------------------------//
        //Start the poll of messages
        //TStream<byte[]> msgs = null;
//        // if (this.pChain.contains("I")) {
//        TStream<byte[]> msgs = PlumbingStreams.isolate(topology.poll(queryPath(subs,
//                lines), this.interArrivalTime, TimeUnit.NANOSECONDS)
//                        .tag("Poll"),
//                true);

//        msgs = Metrics.rateMeter(msgs);

       /* }else{
            msgs = topology.poll(queryPath(subs, lines),
                    this.interArrivalTime,
                    TimeUnit.NANOSECONDS)
                    .tag("Poll");
        }*/

        TStream<byte[]> msgs = topology.poll(queryPath(subs, lines),
                this.interArrivalTime,
                TimeUnit.NANOSECONDS)
                .tag("Poll");

        TStream<Tweet> streamData =null;

        // The number of parallel processing channels to generate
        if (this.pipelineType.equals("v1")) {
            streamData = sequentialPipeline(msgs, streamData);

        } else if (this.pipelineType.equals("v2")) {
            // Define the splitter
            ToIntFunction<byte[]> splitterFarm1 = PlumbingStreams
                    .roundRobinSplitter(this.parallelInstancesF1);

            // Build the parallel analytic pipelines flow
            TStream<Tweet> farm1 = PlumbingStreams.parallel(msgs,
                    this.parallelInstancesF1,
                    splitterFarm1,
                    pipelineFarm1())
                    .tag("farm1");

            //Filter by language
            TStream<Tweet> filtered = PlumbingStreams.isolate
                    (farm1.filter(t -> t.getLang()
                                    .equalsIgnoreCase("en"))
                                    .tag("Filter"),
                            true);

            // Define the splitter
            ToIntFunction<Tweet> splitterFarm2 = PlumbingStreams
                    .roundRobinSplitter(this.parallelInstancesF2);

            // Build the parallel analytic pipelines flow
            streamData = PlumbingStreams.parallel(filtered,
                    this.parallelInstancesF2,
                    splitterFarm2,
                    pipelineFarm2())
                    .tag("farm2");


        } else if (this.pipelineType.equals("v3")) {
            // Define the splitter
            ToIntFunction<byte[]> splitterFarm3 = PlumbingStreams
                    .roundRobinSplitter(this.parallelInstancesF1);

            // Build the parallel analytic pipelines flow
            TStream<Tweet> farm1 = PlumbingStreams.parallel(msgs,
                    this.parallelInstancesF1,
                    splitterFarm3,
                    pipelineFarm3())
                    .tag("farm3");

            //Filter by language
            streamData = PlumbingStreams.isolate(farm1.filter(t -> t
                            .getLang()
                            .equalsIgnoreCase("en"))
                            .tag("Filter"),
                    true);

        }

        // Refresh the log file and the application is terminated when it reach the number of tweets.
        //   Also, the metrics are printed on the screen.
        streamData.sink(tuple -> {
            // Updating the variables
            n_tweets.getAndIncrement();

            //Saves the data in a log file
            if (saveLog) {
                try {
                    logBuffer.write("Processed tweet: " + n_tweets.get() +
                            " Score: " + tuple.getScore() +
                            "\n");
                    logBuffer.flush();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            Long t2 = ZonedDateTime.now().toInstant().toEpochMilli();

            // If the number of tweets is bigger than 0, the application will be terminated.
            if ((Double.valueOf((t2 - t1.get())) / 1000.0) >= 180) {

                // Updating the variables
                Double execTime = Double.valueOf((t2 - t1.get())) / 1000.0;
                Double tweetSec = Double.valueOf(n_tweets.get() / execTime);

                // Printing of information
                System.out.println(n_tweets.get() +
                        " " + execTime +
                        " " + tweetSec +
                        " " + t1.get() / 1000.0 +
                        " " + t2 / 1000.0);
                if (saveLog) {
                    //Application Completion
                    try {
                        logBuffer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                System.exit(1);
            }
        });


        //----------------------------------------------------------------------------------//
        // Submit the application                                                           //
        //----------------------------------------------------------------------------------//
        provider.submit(topology);

        //System.out.println(provider.getServices().getService(HttpServer.class).getConsoleUrl());
    }

    /**
     * Text filter operator
     */
    static class FilterText implements Function<Tweet, Tweet> {
        Function<String, String> funcText;

        FilterText(Function<String, String> func) {
            funcText = func;
        }

        public Tweet apply(Tweet tweet) {
            try {
                tweet.setText(funcText.apply(tweet.getText()));
                return tweet;
            } catch (Exception e) {
                return null;
            }
        }
    }

    static Supplier<byte[]> queryPath(File[] pathDir, byte[] lines) throws Exception {
        return () -> {
            if (!readDirect){
                //long startTime = System.currentTimeMillis();
                byte[] lines2 = null;

                try {
                    if (fileTotal == fileCounter) {
                        fileCounter = 0;
                        lines2 = readFile(pathDir[fileCounter],
                                Charset.defaultCharset());

                    } else {
                        lines2 = readFile(pathDir[fileCounter],
                                Charset.defaultCharset());

                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }

                fileCounter += 1;

                //long totalTime = System.currentTimeMillis() - startTime;
                //acm.add(totalTime);

                return lines2;

            }else{
                return lines;

            }
        };


    }

    private final TStream<Tweet> sequentialPipeline(TStream<byte[]> msgs, TStream<Tweet> tweet) {
        return addOperationsToChain(msgs,
                tweet,
                this.pChain);
    }

    /**
     * Function to create analytic pipeline and add it to a stream
     */
    private static BiFunction<TStream<byte[]>,  Integer, TStream<Tweet>> pipelineFarm1() {
        return (stream, channel) ->
        {
            TStream<Tweet> tweetChain = null;
            tweetChain= addOperationsToChain(stream,
                    tweetChain,
                    "P");
            return tweetChain;
        };
    }


    private static BiFunction<TStream<Tweet>, Integer, TStream<Tweet>> pipelineFarm2() {
        return (stream, channel) ->
        {
            TStream<Tweet> tweetChain= addOperationsToChain(null,
                    stream,
                    "STCE");
            return tweetChain;
        };
    }


    private static BiFunction<TStream<byte[]>, Integer, TStream<Tweet>> pipelineFarm3() {
        return (stream, channel) ->
        {
            TStream<Tweet> tweetChain = null;
            tweetChain= addOperationsToChain(stream,
                    tweetChain,
                    "PSTCE");
            return tweetChain;
        };
    }

    private static byte[] readFile(File path, Charset encoding)
            throws IOException {
        return Files.readAllBytes(Paths.get(String.valueOf(path)));

    }

    private static final TStream<Tweet> addOperationsToChain(TStream<byte[]> msgs, TStream<Tweet> tweet, String pipelinechain) {
        // Parse an make a filte over the tweets
        if (pipelinechain.contains("P")) {
            tweet = addParser(msgs, tweet, pipelinechain.contains("I") || pipelinechain.contains("K"));
        }

        //Filter only english words
        if (pipelinechain.contains("F")){
            tweet = addFilter(tweet, pipelinechain.contains("I"));
        }

        // Remove special characters and convert all characters in lowercase
        if (pipelinechain.contains("S")){
            tweet = addStandardization(tweet, pipelinechain.contains("I"));
        }

        // Remove non sentimental words
        if (pipelinechain.contains("T")){
            tweet = addStemmer(tweet, pipelinechain.contains("I"));
        }

        // Count the positive and negative words following a dictionary (NLP)
        //streams.add(streams.getLast().map(new CountPositiveNegativeWords()).tag("scoreCounters"));
        if (pipelinechain.contains("C")){
            tweet = addScorer(tweet, pipelinechain.contains("I"));
        }

        // Work out the score following the positive and negative counters
        if (pipelinechain.contains("E")){
            tweet= addResult(tweet, pipelinechain.contains("I"));
        }

        return tweet;
    }

    private static final TStream<Tweet> addParser(TStream<byte[]> msgs, TStream<Tweet> tweet, boolean isolate){
        if (isolate) {
            return PlumbingStreams.isolate(msgs.map(t -> {
                        if (t1.get() == 0) {
                            t1.set(ZonedDateTime.now()
                                    .toInstant()
                                    .toEpochMilli());
                        }

                        try {
                            return JsonIterator
                                    .parse(t)
                                    .read(Tweet.class); // input stream

                        } catch (Exception e) {
                            e.printStackTrace();
                            return null;
                        }

                    }).tag("parser")
                    ,
                    true);
        } else {
            return msgs.map(t -> {
                if (t1.get() == 0) {
                    t1.set(ZonedDateTime.now()
                            .toInstant()
                            .toEpochMilli());
                }

                try {
                    return JsonIterator
                            .parse(t)
                            .read(Tweet.class); // input stream

                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }

            }).tag("parser");
        }

        //tweet = Metrics.rateMeter(tweet);


    }

    private static final TStream<Tweet> addFilter(TStream<Tweet> tweet, boolean isolate){
        if (isolate) {
            return PlumbingStreams.isolate(tweet.filter(t -> t
                            .getLang()
                            .equalsIgnoreCase("en"))
                            .tag("Filter"),
                    true);

        }else{
            return tweet.filter(t -> t.getLang()
                    .equalsIgnoreCase("en"))
                    .tag("Filter");

        }

        //tweet = Metrics.rateMeter(tweet);

    }

    private static final TStream<Tweet> addStandardization(TStream<Tweet> tweet, boolean isolate){
        if (isolate) {
            return PlumbingStreams.isolate(tweet.map(new FilterText(s -> {
                        if (s!=null) {
                            s = s.toLowerCase();
                        }

                        StringBuilder text = new StringBuilder(s.length());
                        for (int i = 0; i < s.length(); i++) {
                            char c = s.charAt(i);
                            text.append(c >= 'a' && c <= 'z' ? c : ' ');
                        }

                        return text.toString();
                    }
                    )).tag("standardization"),
                    true);

        }else{
            return tweet.map(new FilterText(s -> {
                s = s.toLowerCase();

                StringBuilder text = new StringBuilder(s.length());
                for (int i = 0; i < s.length(); i++) {
                    char c = s.charAt(i);
                    text.append(c >= 'a' && c <= 'z' ? c : ' ');
                }
                return text.toString();
            }
            )).tag("standardization");

        }

        //tweet = Metrics.rateMeter(tweet);

    }

    private static final TStream<Tweet> addStemmer(TStream<Tweet> tweet, boolean isolate){
        if (isolate) {
            return PlumbingStreams.isolate(tweet.map(new FilterText(s -> {

                        String text = s;
                        List<String> wordList = new ArrayList<>();
                        List<String> replaceList = new ArrayList<>();
                        if(s!=null) {
                            for (String word : text.split(" ")) {
                                if (StopWords.getWords().contains(word)) {
                                    wordList.add(word);
                                    replaceList.add("");
                                }
                            }
                        }
                        String[] listChange = wordList.stream().toArray(String[]::new);
                        String[] newValues = replaceList.stream().toArray(String[]::new);

                        return StringUtils.replaceEach(s, listChange, newValues);
                    })).tag("stemmer"),
                    true);

        }else{
            return tweet.map(new FilterText(s -> {
                String text = s;
                List<String> wordList = new ArrayList<>();
                List<String> replaceList = new ArrayList<>();
                for (String word : text.split(" ")) {
                    if (StopWords.getWords().contains(word)) {
                        wordList.add(word);
                        replaceList.add("");
                    }
                }

                String[] listChange = wordList.stream().toArray(String[]::new);
                String[] newValues = replaceList.stream().toArray(String[]::new);

                return StringUtils.replaceEach(s, listChange, newValues);
            })).tag("stemmer");

        }

        //tweet = Metrics.rateMeter(tweet);
    }

    private static final TStream<Tweet> addScorer(TStream<Tweet> tweet, boolean isolate){
        if (isolate) {
            return PlumbingStreams.isolate(tweet.map(t -> {
                        int numPos = 0;
                        int numNeg = 0;
                        //try {
                        for (String word : t.getText().split(" ")) {
                            if (PositiveWords.getWords().contains(word)) {
                                numPos++;
                            } else if (NegativeWords.getWords().contains(word)) {
                                numNeg++;
                            }
                        }
                        t.setNegative(numNeg);
                        t.setPositive(numPos);

                        t.setText(null);
                        // Null'ify the text and user fields, so that they are not serialised when converted to a json
                        t.setUser(null);

                        return t;
                        // } catch (Exception e) {
                        //  e.printStackTrace();
                        //  return null;
                        // }
                    }).tag("scoreCounters"),
                    true);

        }else {
            return tweet.map(t -> {
                int numPos = 0;
                int numNeg = 0;
                try {
                    for (String word : t.getText().split(" ")) {
                        if (PositiveWords.getWords().contains(word)) {
                            numPos++;
                        } else if (NegativeWords.getWords().contains(word)) {
                            numNeg++;
                        }
                    }
                    t.setNegative(numNeg);
                    t.setPositive(numPos);

                    // Null'ify the text and user fields, so that they are not serialised when converted to a json
                    t.setText(null);
                    t.setUser(null);

                    return t;
                } catch (Exception e) {
                    e.printStackTrace();
                    return null;
                }
            }).tag("scoreCounters");

        }

        //tweet = Metrics.rateMeter(tweet);
    }

    private static final TStream<Tweet> addResult(TStream<Tweet> tweet, boolean isolate){
        if (isolate) {
            return PlumbingStreams.isolate(tweet.map(t -> {
                        t.setScore(t.getPositive() >= t.getNegative() ? "positive" : "negative");

                        return t;
                    }).tag("sentimentScorer"),
                    true);

        }else{
            return tweet.map(t -> {
                t.setScore(t.getPositive() >= t.getNegative() ? "positive" : "negative");

                return t;
            }).tag("sentimentScorer");
        }

        //tweet = Metrics.rateMeter(tweet);

    }
}