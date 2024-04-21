package io.github.kensuke1984.kibrary.waveform;

import java.util.Arrays;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.voxel.ParameterType;

/**
 * Partial type, which can be specified as a combination of {@link ParameterType} and {@link VariableType}.
 *
 * CAUTION: values must be in -128~127 (range of byte)!! (The value is written into files as byte; see {@link WaveformDataWriter}.)
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
enum PartialType {

    RHO1D(0), LAMBDA1D(1), MU1D(2), KAPPA1D(3), LAMBDA2MU1D(4),
    A1D(11), C1D(12), F1D(13), L1D(14), N1D(15),
    VP1D(21), VS1D(22), R1D(23), Q1D(24),

    RHO3D(30), LAMBDA3D(31), MU3D(32), KAPPA3D(33), LAMBDA2MU3D(34),
    A3D(41), C3D(42), F3D(43), L3D(44), N3D(45),
    VP3D(51), VS3D(52), R3D(53), Q3D(54),

    TIME_SOURCE(80), TIME_RECEIVER(90);
    // CAUTION: values must be in -128~127 (range of byte)!!

    private int number;

    private PartialType(int number) {
        this.number = number;
    }

    int getNumber() {
        return number;
    }

    static PartialType ofNumber(int n) {
        return Arrays.stream(PartialType.values()).filter(type -> type.number == n).findAny()
                .orElseThrow(() -> new IllegalArgumentException("No PartialType for " + n + "."));
    }

    boolean is1D() {
        return 0 <= number && number < 30;
    }

    boolean is3D() {
        return 30 <= number && number < 60;
    }

    boolean isTimePartial() {
        return 80 <= number;
    }

    static PartialType of(ParameterType parameterType, VariableType variableType) {
        if (parameterType.equals(ParameterType.LAYER)) {
            switch (variableType) {
            case RHO: return PartialType.RHO1D;
            case LAMBDA: return PartialType.LAMBDA1D;
            case MU: return PartialType.MU1D;
            case A: return PartialType.A1D;
            case C: return PartialType.C1D;
            case F: return PartialType.F1D;
            case L: return PartialType.L1D;
            case N: return PartialType.N1D;
            default: throw new IllegalArgumentException("No corresponding PartialType.");
            }
        } else if (parameterType.equals(ParameterType.VOXEL)) {
            switch (variableType) {
            case RHO: return PartialType.RHO3D;
            case LAMBDA: return PartialType.LAMBDA3D;
            case MU: return PartialType.MU3D;
            case A: return PartialType.A3D;
            case C: return PartialType.C3D;
            case F: return PartialType.F3D;
            case L: return PartialType.L3D;
            case N: return PartialType.N3D;
            default: throw new IllegalArgumentException("No corresponding PartialType.");
            }
        } else if (parameterType.equals(ParameterType.SOURCE)) {
            switch (variableType) {
            case TIME: return PartialType.TIME_SOURCE;
            default: throw new IllegalArgumentException("No corresponding PartialType.");
            }
        } else if (parameterType.equals(ParameterType.RECEIVER)) {
            switch (variableType) {
            case TIME: return PartialType.TIME_RECEIVER;
            default: throw new IllegalArgumentException("No corresponding PartialType.");
            }
        } else {
            throw new IllegalArgumentException("No corresponding PartialType.");
        }
    }

    VariableType toVariableType() {
        switch (this) {
        case RHO1D: case RHO3D: return VariableType.RHO;
        case A1D: case A3D: return VariableType.A;
        case C1D: case C3D: return VariableType.C;
        case F1D: case F3D: return VariableType.F;
        case L1D: case L3D: return VariableType.L;
        case N1D: case N3D: return VariableType.N;
        case LAMBDA1D: case LAMBDA3D: return VariableType.LAMBDA;
        case MU1D: case MU3D: return VariableType.MU;
        case LAMBDA2MU3D: return VariableType.LAMBDA2MU;
        case KAPPA3D: return VariableType.KAPPA;
        default:
            throw new IllegalArgumentException("Unexpected partial type.");
        }
    }

    ParameterType toParameterType() {
        if (is1D()) return ParameterType.LAYER;
        else if (is3D()) return ParameterType.VOXEL;
        else if (this == TIME_SOURCE) return ParameterType.SOURCE;
        else if (this == TIME_RECEIVER) return ParameterType.RECEIVER;
        else throw new RuntimeException("Unexpected partial type.");
    }

}
