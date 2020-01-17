package fr.inria.sentiment_analysis.nlp;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

public class NegativeWords implements Serializable {
    //private static final Logger logger = LoggerFactory.getLogger(NegativeWords.class);
    public static final long serialVersionUID = 42L;
    private HashSet<String> negWords;
    private static NegativeWords _singleton;

    private NegativeWords() {
        negWords = new HashSet<String>();
        BufferedReader rd = null;

        try {
            rd = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/neg-words.txt")));

            String line;
            while ((line = rd.readLine()) != null) {
                negWords.add(line);
            }
        } catch (IOException ex) {
            //logger.error("IO error while initializing", ex);
        } finally {
            try {
                if (rd != null) rd.close();
            } catch (IOException ex) {}
        }
    }

    private static NegativeWords get() {
        if (_singleton == null)
            _singleton = new NegativeWords();
        return _singleton;
    }

    public static Set<String> getWords() {
        return get().negWords;
    }
}
