package io.prometheus.client.stream;

/**
 *  The invariant calculation differs based on the type of quantiles we are interested in, e.g Biased vs Targeted.
 *
 *  TODO
 *  An interface would probably suffice as there is no common behavior that needs to be implemented.
 */
public abstract class Invariant {
    public abstract double f(double r, int n);
}
