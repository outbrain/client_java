package io.prometheus.client.stream;

import java.util.Map;

/**
 * A stream of data-points that is responsible to summarize/compute quantiles for the collected data.
 *
 * @param <T> The data-type of the items consumed in the Stream
 */
public interface Stream<T extends Number & Comparable<T>> {
    void insert(T v);
    T query (double q);
    Map<Quantile, T> getSnapshot(Quantile...quantiles);
    void reset();
}
