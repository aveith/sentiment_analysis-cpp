package fr.inria.sentiment_analysis.utils;


public class Accumulator  {
    private int nItems = 0;              // the number of items accumulated
    private double mean = Double.NaN;    // the mean of accumulated items
    private double sqrMean = Double.NaN; // the square mean of accumulated items
    private double min = Double.NaN;     // the smallest of accumulated items
    private double max = Double.NaN;     // the largest of accumulated items
    private double last = Double.NaN;    // the last accumulated items

    /**
     * Resets the accumulator
     */
    public void reset() {
        this.nItems = 0;
        this.mean = Double.NaN;
        this.sqrMean = Double.NaN;
        this.min = Double.NaN;
        this.max = Double.NaN;
        this.last = Double.NaN;
    }

    /**
     * Adds a given item to this accumulator
     * @param item
     * @param times
     */
    public void add(double item, int times) {
        if (times < 1) {
            return;
        }

        last = item;

        if (nItems <= 0) {
            nItems += times;
            mean = item;
            min = item;
            max = item;
            sqrMean = item * item;
        } else {
            nItems += times;
            mean = ( (nItems - times) * mean + item * times ) / nItems;
            sqrMean = ( (nItems - times) * sqrMean + item * item * times ) / nItems;

            if (item < min) {
                min = item;
            }

            if (item > max) {
                max = item;
            }
        }
    }

    /**
     * Add a given item to the accumulator
     * @param item an item to be added
     */
    public void add(double item) {
        this.add(item, 1);
    }

    /**
     * Returns the mean of accumulated items
     * @return the mean of the items
     */
    public double getMean() {
        return mean;
    }

    /**
     * Returns the standard deviation of stored items
     * @return the standard deviation
     */
    public double getStandardDeviation() {
        return Math.sqrt(getVariance());
    }

    /**
     * Returns the variance of accumulated items
     * @return the variance of accumulated items
     */
    public double getVariance() {
        return sqrMean - (mean * mean);
    }

    /**
     * Returns the minimum value
     * @return the minimum value
     */
    public double getMin() {
        return min;
    }

    /**
     * Returns the largest number
     * @return the largest number
     */
    public double getMax() {
        return max;
    }

    /**
     * Returns the last item
     * @return the last item
     */
    public double getLast() {
        return last;
    }

    /**
     * Returns the number of items added so far
     * @return the number of items added so far
     */
    public int size() {
        return nItems;
    }
}
