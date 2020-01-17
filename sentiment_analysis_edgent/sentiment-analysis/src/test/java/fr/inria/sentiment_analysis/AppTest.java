package fr.inria.sentiment_analysis;

import com.google.gson.Gson;
import fr.inria.sentiment_analysis.data.Tweet;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;

/**
 * Unit test for simple App.
 */
public class AppTest 
    extends TestCase
{
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public AppTest( String testName )
    {
        super( testName );
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite( AppTest.class );
    }

    public void testApp() {
        assertTrue( true );
    }

    public void testConfig() throws Exception {
        String configFile = "/sa.properties";
        InputStream stream = AppTest.class.getResourceAsStream(configFile);
        assertTrue(stream != null);
//        App app = new App(new BufferedReader(new InputStreamReader(stream)), "~/test.log", 5);
    }

    public void testJsonPojo() {
        String jsonFile = "/tweet_sample_01.json";
        InputStream stream = AppTest.class.getResourceAsStream(jsonFile);

        Gson gson = new Gson();
        Tweet t = gson.fromJson(new BufferedReader(new InputStreamReader(stream)), Tweet.class);
        assertEquals(t.getId(), 250075927172759552L);
        assertEquals(t.getUser().getLang(), "en");
        assertEquals(t.getText(),"Aggressive Ponytail #freebandnames");
    }
}
