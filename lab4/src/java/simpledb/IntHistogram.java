package simpledb;

import java.io.Serializable;
import java.util.*;
/** A class to represent a fixed-width histogram over a single integer-based field.
 */
public class IntHistogram {

    /**
     * Create a new IntHistogram.
     * 
     * This IntHistogram should maintain a histogram of integer values that it receives.
     * It should split the histogram into "buckets" buckets.
     * 
     * The values that are being histogrammed will be provided one-at-a-time through the "addValue()" function.
     * 
     * Your implementation should use space and have execution time that are both
     * constant with respect to the number of values being histogrammed.  For example, you shouldn't 
     * simply store every value that you see in a sorted list.
     * 
     * @param buckets The number of buckets to split the input value into.
     * @param min The minimum integer value that will ever be passed to this class for histogramming
     * @param max The maximum integer value that will ever be passed to this class for histogramming
     */
    ArrayList<Integer> buckets = new ArrayList<Integer>();
    int min;
    int max;
    int bucketSize;
    int nTups;
    
    public IntHistogram(int buckets, int min, int max) {
    	// some code goes here
        this.bucketSize = Math.abs((max-min)/buckets);
        for(int i = 0; i <= buckets+1; i++){
            this.buckets.add(0);
        }
        this.min = min;
        this.max = max;
        this.nTups = 0;
        if (this.bucketSize == 0){
            this.bucketSize = 1;
        }
    }

    /**
     * Add a value to the set of values that you are keeping a histogram of.
     * @param v Value to add to the histogram
     */
    public void addValue(int v) {
    	// some code goes here
        int bucketIndex = Math.abs((v-min)/this.bucketSize);
        int oldValue = this.buckets.get(bucketIndex);
        this.buckets.set(bucketIndex,oldValue + 1);
        this.nTups += 1;
        
    }

    /**
     * Estimate the selectivity of a particular predicate and operand on this table.
     * 
     * For example, if "op" is "GREATER_THAN" and "v" is 5, 
     * return your estimate of the fraction of elements that are greater than 5.
     * 
     * @param op Operator
     * @param v Value
     * @return Predicted selectivity of this particular operator and value
     */
    public double estimateSelectivity(Predicate.Op op, int v) {

    	// some code goes here
        double count = 0;
        int aboveValue = 0;
        int belowValue = 0;
        int bucketIndex = Math.abs(v-min)/this.bucketSize;

        
        int height = this.buckets.get(bucketIndex);
        switch(op){
            case GREATER_THAN:
                if (v < min) return 1.0;
                if (v > max) return 0.0;
                for(int i = bucketIndex+1; i < this.buckets.size(); i++){
                    count += this.buckets.get(i);
                }
                aboveValue = (bucketIndex+1)*this.bucketSize - v;
                count += aboveValue * ((double)height/(double)this.bucketSize);
                break;
            case LESS_THAN:
                if (v < min) return 0.0;
                if (v > max) return 1.0;
                for(int i = 0; i < bucketIndex; i++){
                    count += this.buckets.get(i);
                }
                belowValue = v - bucketIndex*this.bucketSize;
                count += belowValue * ((double)height/(double)this.bucketSize);
                break;
            case EQUALS:
                if (v < min) return 0.0;
                if (v > max) return 0.0;
                count = (double)height/(double)this.bucketSize;
                break;
            case LESS_THAN_OR_EQ:
                if (v < min) return 0.0;
                if (v >= max) return 1.0;
                for(int i = 0; i < bucketIndex; i++){
                    count += this.buckets.get(i);
                }
                belowValue = v - bucketIndex*this.bucketSize;
                count += (belowValue + 1) * ((double)height/(double)this.bucketSize);
                break;
            case GREATER_THAN_OR_EQ:
                if (v <= min) return 1.0;
                if (v > max) return 0.0;
                for(int i = bucketIndex+1; i < this.buckets.size(); i++){
                    count += this.buckets.get(i);
                }
                aboveValue = (bucketIndex+1)*this.bucketSize - v;
                count += (aboveValue+1) * ((double)height/(double)this.bucketSize);
                break;
            case NOT_EQUALS:
                if (v < min || v > max) return 1.0;
                count = this.nTups - ((double)height/(double)this.bucketSize);
                break;
        }
        
        return (double)count/(double)this.nTups;
    }
    
    /**
     * @return
     *     the average selectivity of this histogram.
     *     
     *     This is not an indispensable method to implement the basic
     *     join optimization. It may be needed if you want to
     *     implement a more efficient optimization
     * */
    public double avgSelectivity()
    {
        // some code goes here
        return 1.0;
    }
    
    /**
     * @return A string describing this histogram, for debugging purposes
     */
    public String toString() {
        // some code goes here
        return null;
    }
}
