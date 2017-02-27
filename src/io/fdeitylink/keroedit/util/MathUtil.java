package io.fdeitylink.keroedit.util;

public class MathUtil {
    /**
     * Returns either the given number or
     * the lower limit if it is less than the lower limit or
     * the higher limit if itis greater than the higher limit
     *
     * @param num The number to put within the given bounds
     * @param lower The inclusive lower limit of the number
     * @param upper The inclusive upper limit of the number
     *
     * @return A number within the specified bounds
     */
    public static int boundInt(final int num, final int lower, final int upper) {
        return Math.max(lower, Math.min(num, upper));
    }
}