package io.github.kensuke1984.kibrary.source;

import java.util.Arrays;

/**
 * Types of source time functions.
 * @author otsuru
 * @since 2022/11/3
 */
public enum SourceTimeFunctionType {
    NONE(0), BOXCAR(1), TRIANGLE(2), ASYMMETRIC_TRIANGLE(3), AUTO(4);

    private int value;

    SourceTimeFunctionType(int n) {
        value = n;
    }

    public int getValue() {
        return value;
    }

    public static SourceTimeFunctionType valueOf(int n) {
        return Arrays.stream(values()).filter(type -> type.value == n).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Input n " + n + " is invalid."));
    }

    public static SourceTimeFunctionType ofCode(String code) {
        switch (code) {
        case "BOXHD": return BOXCAR;
        case "TRIHD": return TRIANGLE;
        default: throw new IllegalArgumentException("Unrecognizable source time function type.");
        }
    }

    public String toCode() {
        switch(this) {
        case BOXCAR: return "BOXHD";
        case TRIANGLE: return "TRIHD";
        default: throw new IllegalArgumentException("No code for this source time function type.");
        }
    }
}
