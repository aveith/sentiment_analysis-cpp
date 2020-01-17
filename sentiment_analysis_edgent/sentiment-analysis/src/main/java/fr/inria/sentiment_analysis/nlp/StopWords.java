package fr.inria.sentiment_analysis.nlp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class StopWords implements Serializable {
    //private static final Logger logger = LoggerFactory.getLogger(StopWords.class);
    public static final long serialVersionUID = 42L;
    private HashSet<String> stopWords;
    private static StopWords _singleton;

    private StopWords() {
        stopWords = new HashSet<String>();
        BufferedReader rd = null;

        try {
            rd = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/stop-words.txt")));

            String line = null;
            while ((line = rd.readLine()) != null) {
                this.stopWords.add(line);
            }
        } catch (IOException ex) {
            //logger.error("IO error while initializing", ex);
        } finally {
            try {
                if (rd != null) rd.close();
            } catch (IOException ex) {}
        }
    }

    private static StopWords get() {
        if (_singleton == null)
            _singleton = new StopWords();
        return _singleton;
    }

    public static Set<String> getWords() {
        return get().stopWords;
    }
}
