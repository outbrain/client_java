package io.prometheus.client.stream;

/**
 * Created by warebot on 12/27/15.
 */

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class CKMSStream<T extends Number & Comparable<T>> implements Stream<T> {

    /**
     *  Defines the default buffer capacity.
     */
    private static final int DEF_BUFFER_CAP = 4096;

    /**
     *  Holds the specified value of the buffer capacity.
     */
    private int bufferCap;


    /**
     *  Defines the default targeted-quantiles to track/compute.
     */
    private static final Quantile[] DEF_TARGETED_TARGETED_QUANTILEs = new Quantile[]{
            new Quantile(0.5, 0.05), new Quantile(0.99, 0.001)};


    /**
     *  Invariant function to use based on the type of Quantiles that are being summarized.
     */
    private Invariant invariant = null;

    /**
     *  Tracks the total number of data points that have been observed in the stream.
     */
    AtomicInteger count = new AtomicInteger(0);

    /**
     *  Holds the list of data-points represented by `Sample` at time S(n).
     */
    private final LinkedList<Sample> samples = new LinkedList<Sample>();

    /**
     *  Buffers data-points observed from the data-stream that are yet to be flushed.
     *
     *  Reference:
     *  Incoming items are buffered in sorted order and are inserted/merged
     *  into the underlying S(n) data-structure
     */
    private final ArrayList<T> buffer;


    /**
     *
     *  @param quantiles The targeted-quantiles that the stream will eventually compute.
     */
    public CKMSStream(final Quantile... quantiles) {
        this.invariant = new TargetedQuantileInvariant(quantiles);
        this.bufferCap = DEF_BUFFER_CAP;
        buffer = new ArrayList<T>(bufferCap);
    }


    public CKMSStream() {
        this(DEF_TARGETED_TARGETED_QUANTILEs);
    }

    /**
     *
     * @param bufferCap The capacity of the buffer. Once reached, a flush is triggered.
     */
    public CKMSStream(final int bufferCap) {
        this(bufferCap, DEF_TARGETED_TARGETED_QUANTILEs);
    }

    /**
     *
     * @param bufferCap The capacity of the buffer. Once reached, a flush is triggered.
     * @param quantiles The targeted-quantiles that the stream will eventually compute.
     */
    public CKMSStream(final int bufferCap, final Quantile... quantiles) {
        this.invariant = new TargetedQuantileInvariant(quantiles);
        this.bufferCap = bufferCap;
        buffer = new ArrayList<T>(bufferCap);
    }


    /**
     *
     * @param r The rank of the item in the sample-set
     * @param n The running count of data-points observed.
     * @return Delta based on the acceptable error.
     *
     */
    private double f(double r, int n) {
        double error = invariant.f(r, n);
        return error;
    }


    /**
     *
     * @param v Data point to be observed i.e inserted into our sample-set.
     */
    public void insert(final T v) {
        buffer.add(buffer.size(), v);
        if (buffer.size() >= bufferCap) {
            flush();
        }
    }


    /**
     *  `merge` merges all data-points from the buffer into the sample-set.
     *
     *   It Finds the index that satisfies vi < v ≤ vi+1 and inserts the data-point
     *   represented by the data-structure e (v, g =1, ∆ = floor(f(ri, n)) − 1)
     */
    public void merge() {
        int start = 0;
        int bufferSize = buffer.size();
        if (bufferSize == 0) {
            return;
        }

        Collections.sort(buffer);

        if (samples.size() == 0) {
            samples.add(new Sample(buffer.get(0), 1, 0));
            start = 1;
            count.getAndIncrement();
        }

        ListIterator<Sample> iterator = samples.listIterator(0);

        for (int bufIdx = start; bufIdx < bufferSize; bufIdx++) {
            T v = buffer.get(bufIdx);
            int ri = 0;

            Sample vi = iterator.next();

            while (iterator.hasNext() && v.compareTo(vi.value) > 0) {
                vi = iterator.next();
                ri += vi.g;

            }

            if (vi.value.compareTo(v) > 0) {
                iterator.previous();
                if (ri > 0) {
                    ri -= vi.g;
                }
            }

            int delta;

            if (!iterator.hasPrevious() || !iterator.hasNext()) {
                delta = 0;
            } else {
                delta = (int) Math.floor(f(ri, count.get())) - 1;
            }
            iterator.add(new Sample(v, 1, delta));

            // TODO
            // Rewinding the iterator to the beginning of list for every item in the merge process might not be the
            // best thing to do, but so far it's the easiest way to calculate `ri` accurately without getting to clever
            // with spaghetti code.
            // I am sure there is a better way.

            iterator = samples.listIterator(0);
            count.getAndIncrement();
        }

        buffer.clear();
    }

    /**
     * Periodically, the algorithm scans the data structure and merges adjacent nodes when this does not
     * violate the invariant. That is, find nodes (vi, gi, ∆i) and (vi+1, gi+1, ∆i+1), and replace them
     * with(vi+1,(gi+gi+1), ∆i+1) provided that (gi + gi+1 + ∆i+1) ≤ f(ri, n)
     */
    private void compress() {
        // If we only have one item in the sample-set, there is no reason to compress (nothing to compress)
        if (samples.size() < 2) {
            return;
        }

        final ListIterator<Sample> it = samples.listIterator(0);

        Sample prev;
        Sample next = it.next();
        int ri = 0;
        int width = next.g;
        while (it.hasNext()) {
            // `prev` represents vi
            prev = next;
            // `next` represents vi+1
            next = it.next();

            // `ri` is the computed rank of vi
            ri += width;

            if (prev.g + next.g + next.delta <= f(ri, count.get())) {
                next.g += prev.g;
                // We want to remove vi. So we rewind twice
                it.previous();
                it.previous();
                it.remove();
                // Proceed with the iteration
                it.next();
                width = next.g - prev.g;
            } else {
                width = next.g;
            }
        }
    }


    /**
     * Resets the state of the Stream.
     */
    public void reset() {
        samples.clear();
        count.set(0);
        buffer.clear();
    }


    /**
     * @param quantile The quantile to query
     * @return The Sample represented by rank quantile(n)
     * @throws IllegalStateException
     */
    public T query(final double quantile) throws IllegalStateException {

        /*
         * TODO
         * `flush` will most likely need to be decoupled from the query functionality and left to
         * the client implementation to ensure that data is flushed before it is queried
         *
         */
        // Make sure we flush any buffered items into our sample-set S(n) before we query
        flush();

        if (samples.size() == 0) {
            return null;
        }


        int ri = 0;
        final int desired = (int) (quantile * count.get());

        final ListIterator<Sample> it = samples.listIterator(0);
        Sample prev, cur;
        cur = it.next();

        while (it.hasNext()) {
            prev = cur;
            cur = it.next();

            ri += prev.g;
            if (ri + cur.g + cur.delta > desired + (f(desired, count.get())) / 2) {
                return prev.value;
            }

        }

        T v = samples.getLast().value;
        return v;
    }


    /**
     * @param quantiles A list of the desired quantiles to compute
     * @return A snaphot of the quantile(s) computation represent by a Map
     */
    @Override
    public Map<Quantile, T> getSnapshot(Quantile... quantiles) {
        Map<Quantile, T> snapshot = new HashMap<Quantile, T>();
        for (Quantile q : quantiles) {
            T approx = this.query(q.getQuantile());
            snapshot.put(q, approx);
        }
        return snapshot;
    }

    /**
     * Flushes buffered samples by merging them into the the underlying sample-set followed
     * by a call to compress to evict data-points that qualify for eviction with respect to the invariant function.
     */
    void flush() {
        // If the buffer is at capacity, merge and compress to relinquish storage.
        merge();
        compress();
    }


    /**
     * This class represents the data-structure (tuple) required to store an observed data-point
     * into the underlying sample-set
     * <p>
     * (ti = (vi, gi, delta_i))
     */
    private class Sample {

        /**
         * Holds the value of the observed data-point.
         */
        final T value;

        /**
         * Holds the computed width of the data point.
         */
        int g;

        /**
         * Holds the difference of the maximum rank of vi and the lowest rank of vi.
         * (which represents the uncertainty of the rank.)
         */
        final int delta;

        public Sample(final T value, final int g, final int delta) {
            this.value = value;
            this.g = g;
            this.delta = delta;
        }
    }
}