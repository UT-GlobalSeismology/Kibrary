package io.github.kensuke1984.kibrary.util.spc;

import java.util.Arrays;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.voxel.ParameterType;
import io.github.kensuke1984.kibrary.waveform.WaveformDataWriter;

/**
 * Partial type, which can be specified as a combination of {@link ParameterType} and {@link VariableType}.
 *
 * CAUTION: values must be in -128~127 (range of byte)!! (The value is written into files as byte; see {@link WaveformDataWriter}.)
 *
 * @author Kensuke Konishi
 * @since version 0.0.3.1
 */
public enum PartialType {

    RHO1D(0), LAMBDA1D(1), MU1D(2), KAPPA1D(3), G1D(4),
    A1D(11), C1D(12), F1D(13), L1D(14), N1D(15),
    VP1D(21), VS1D(22), R1D(23), Q1D(24),

    RHO3D(30), LAMBDA3D(31), MU3D(32), KAPPA3D(33), G3D(34),
    A3D(41), C3D(42), F3D(43), L3D(44), N3D(45),
    VP3D(51), VS3D(52), R3D(53), Q3D(54),

    TIME_SOURCE(80), TIME_RECEIVER(90);
    // CAUTION: values must be in -128~127 (range of byte)!!

    private int value;

    PartialType(int n) {
        value = n;
    }

    public static PartialType getType(int n) {
        return Arrays.stream(PartialType.values()).filter(type -> type.value == n).findAny()
                .orElseThrow(() -> new IllegalArgumentException("Input n " + n + " is invalid."));
    }

    public boolean is1D() {
        return 0 <= value && value < 30;
    }

    public boolean is3D() {
        return 30 <= value && value < 60;
    }

    public boolean isTimePartial() {
        return 80 <= value;
    }

    public boolean isDensity() {
        return value == 0 || value == 30;
    }

    public int getValue() {
        return value;
    }

    /**
     * 変微分係数波形を計算するときのCijklの重み A C F L N Mu lambda
     *
     * @return weighting for {@link PartialType} to compute partials
     */
    public WeightingFactor getWeightingFactor() {
        switch (this) {
        case A3D:
            return WeightingFactor.A;
        case C3D:
            return WeightingFactor.C;
        case F3D:
            return WeightingFactor.F;
        case L3D:
            return WeightingFactor.L;
        case N3D:
            return WeightingFactor.N;
        case MU3D:
            return WeightingFactor.MU;
        case LAMBDA3D:
            return WeightingFactor.LAMBDA;
        case KAPPA3D:
            return WeightingFactor.KAPPA;
        case G3D:
            return WeightingFactor.G;
        default:
            throw new RuntimeException("Unexpected happens");
        }
    }

    // TODO erase
    public static PartialType of(ParameterType parameterType, VariableType variableType) {
        if (parameterType.equals(ParameterType.LAYER)) {
            switch (variableType) {
            case RHO: return PartialType.RHO1D;
            case LAMBDA: return PartialType.LAMBDA1D;
            case MU: return PartialType.MU1D;
            case KAPPA: return PartialType.KAPPA1D;
            case G: return PartialType.G1D;
            case A: return PartialType.A1D;
            case C: return PartialType.C1D;
            case F: return PartialType.F1D;
            case L: return PartialType.L1D;
            case N: return PartialType.N1D;
            default: throw new IllegalArgumentException("No corresponding PartialType");
            }
        } else if (parameterType.equals(ParameterType.VOXEL)) {
            switch (variableType) {
            case RHO: return PartialType.RHO3D;
            case LAMBDA: return PartialType.LAMBDA3D;
            case MU: return PartialType.MU3D;
            case KAPPA: return PartialType.KAPPA3D;
            case G: return PartialType.G3D;
            case A: return PartialType.A3D;
            case C: return PartialType.C3D;
            case F: return PartialType.F3D;
            case L: return PartialType.L3D;
            case N: return PartialType.N3D;
            default: throw new IllegalArgumentException("No corresponding PartialType");
            }
        } else if (parameterType.equals(ParameterType.SOURCE)) {
            switch (variableType) {
            case TIME: return PartialType.TIME_SOURCE;
            default: throw new IllegalArgumentException("No corresponding PartialType");
            }
        } else if (parameterType.equals(ParameterType.RECEIVER)) {
            switch (variableType) {
            case TIME: return PartialType.TIME_RECEIVER;
            default: throw new IllegalArgumentException("No corresponding PartialType");
            }
        } else {
            throw new IllegalArgumentException("No corresponding PartialType");
        }
    }

    public VariableType toVariableType() {
        switch (this) {
        case RHO1D: case RHO3D: return VariableType.RHO;
        case A1D: case A3D: return VariableType.A;
        case C1D: case C3D: return VariableType.C;
        case F1D: case F3D: return VariableType.F;
        case L1D: case L3D: return VariableType.L;
        case N1D: case N3D: return VariableType.N;
        case LAMBDA1D: case LAMBDA3D: return VariableType.LAMBDA;
        case MU1D: case MU3D: return VariableType.MU;
        case G3D: return VariableType.G;
        case KAPPA3D: return VariableType.KAPPA;
        default:
            throw new IllegalArgumentException("Illegal partial type");
        }
    }

    //TODO erase
    public ParameterType toParameterType() {
        if (is1D()) return ParameterType.LAYER;
        else if (is3D()) return ParameterType.VOXEL;
        else if (this == TIME_SOURCE) return ParameterType.SOURCE;
        else if (this == TIME_RECEIVER) return ParameterType.RECEIVER;
        else throw new RuntimeException("unexpected");
    }

    // TODO hmm...
    public SPCType toSpcFileType() {
        switch (this) {
        case RHO1D:
            return SPCType.RHO1D;
        case LAMBDA3D:
        case LAMBDA1D:
            return SPCType.LAMBDA1D;
        case MU3D:
        case MU1D:
            return SPCType.MU1D;
        case A3D:
        case A1D:
            return SPCType.A1D;
        case C3D:
        case C1D:
            return SPCType.C1D;
        case F3D:
        case F1D:
            return SPCType.F1D;
        case L3D:
        case L1D:
            return SPCType.L1D;
        case N3D:
        case N1D:
            return SPCType.N1D;
        case Q1D:
        case Q3D:
            return SPCType.Q1D;
        case VS1D:
            return SPCType.VS1D;
        case VP1D:
            throw new RuntimeException("Not SpcFileType");
        default:
            throw new RuntimeException("unexpected");
        }

    }

}
