package io.github.kensuke1984.kibrary.source;

import java.util.Arrays;

/**
 * Types of source time functions.
 * @author otsuru
 * @since 2022/11/3
 */
public enum SourceTimeFunctionType {
    NONE(0), BOXCAR(1), TRIANGLE(2), ASYMMETRIC_TRIANGLE(3), AUTO(4);

    private final int number;

    private SourceTimeFunctionType(int number) {
        this.number = number;
    }

    public int getNumber() {
        return number;
    }

    public static SourceTimeFunctionType ofNumber(int number) {
        return Arrays.stream(values()).filter(type -> type.number == number).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Input number " + number + " is invalid."));
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
