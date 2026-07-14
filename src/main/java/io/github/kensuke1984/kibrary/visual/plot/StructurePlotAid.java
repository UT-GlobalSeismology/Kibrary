package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;
import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;

/**
 * Utils for plotting 1-D structure.
 *
 * @since 2024/3/30
 * @author otsuru
 */
public class StructurePlotAid {

    private static final String[][] COLORS = {
            {"gray20", "dark-gray", "gray", "light-gray"},
            {"red", "orange-red", "salmon", "light-pink"},
            {"blue", "web-blue", "skyblue", "light-cyan"},
            {"yellow4", "goldenrod", "gold", "khaki"},
            {"dark-violet", "dark-magenta", "purple", "plum"},
            {"dark-green", "web-green", "greenyellow", "seagreen"}};

    static enum Distinguisher {
        COLOR, SHADE, DASH, NONE;
    }

    static enum Color {
        GRAY(0), RED(1), BLUE(2), ORANGE(3), PURPLE(4), GREEN(5), NONE(-1);

        private final int number;
        private Color(int number) {
            this.number = number;
        }
        public int getNumber() {
            return number;
        }
    }

    private Distinguisher structureDistinguisher;
    private Distinguisher modelDistinguisher;
    private Distinguisher variableDistinguisher;
    private List<VariableType> variableTypes;
    private List<Color> colors;

    /**
     * Prints definition of a set of polynomial functions for a variable of a 1-D structure.
     * The name of the function will be 'variable.toString().toLowerCase() + index'.
     * @param variable ({@link VariableType}) Variable to define function for.
     * @param structure ({@link PolynomialStructure}) 1-D structure.
     * @param index (int) Index to add to function name, in case of plotting multiple structures.
     * @param pw (PrintWriter) Where to output.
     * @throws IOException
     */
    static void defineFunction(VariableType variable, PolynomialStructure structure, int index, PrintWriter pw) throws IOException {
        PolynomialFunction[] functions;
        switch (variable) {
        case RHO:
            functions = structure.getRho();
            break;
        case Vpv:
            functions = structure.getVpv();
            break;
        case Vph:
        case Vp:
            functions = structure.getVph();
            break;
        case Vsv:
            functions = structure.getVsv();
            break;
        case Vsh:
        case Vs:
            functions = structure.getVsh();
            break;
        case ETA:
            functions = structure.getEta();
            break;
        default:
            throw new IllegalArgumentException(variable + " not supported yet.");
        }
        writeFunction(structure.getRmin(), structure.getRmax(), structure.planetRadius(),
                functions, variable.toString().toLowerCase() + index, pw);
    }

    private static void writeFunction(double[] rmin, double[] rmax, double planetRadius,
            PolynomialFunction[] functions, String funcName, PrintWriter pw) throws IOException {
        // each layer
        for (int i = 0; i < functions.length; i++) {
            double[] coeffs = Arrays.copyOf(functions[i].getCoefficients(), 4);
            pw.print(funcName + "_" + i + "(x) = (x<" + rmin[i] + ") ? 0 : (x<" + rmax[i] + ") ? " + coeffs[0]);
            for (int j = 1; j < 4; j++) {
                pw.print("+(" + coeffs[j] + ")*(x/" + planetRadius + ")");
                if (j > 1) pw.print("**" + j);
            }
            pw.println(" : 0");
        }

        // sum
        pw.print(funcName + "(x)=");
        for (int i = 0; i < functions.length; i++) {
            if (i > 0) pw.print("+");
            pw.print(funcName + "_" + i + "(x)");
        }
        pw.println();
    }

    /**
     * Get the label for each variable. Greek letters will be transformed to Greek letter format.
     * @param variable ({@link VariableType}) Variable of label.
     * @return (String) Formatted label.
     */
    static String labelStringFor(VariableType variable) {
        switch (variable) {
        case RHO: return "{/Symbol r}";
        case ETA: return "{/Symbol h}";
        default: return variable.toString();
        }
    }

    /**
     * Create instance to manage color, shade, and dash type of lines to plot.
     * @param structureDistinguisher ({@link Distinguisher}) How to distinguish the structure.
     * @param modelDistinguisher ({@link Distinguisher}) How to distinguish models.
     * @param variableDistinguisher ({@link Distinguisher}) How to distinguish variables.
     * @param variableTypes (List of {@link VariableType}) Variables that will be used.
     */
    StructurePlotAid(Distinguisher structureDistinguisher, Distinguisher modelDistinguisher, Distinguisher variableDistinguisher,
            List<VariableType> variableTypes) {
        this.structureDistinguisher = structureDistinguisher;
        this.modelDistinguisher = modelDistinguisher;
        this.variableDistinguisher = variableDistinguisher;
        this.variableTypes = variableTypes;
    }

    /**
     * Set color for each structure.
     * @param colors (List of {@link Color}) Colors for each structure.
     */
    void setColors(List<Color> colors) {
        this.colors = colors;
    }

    /**
     * Get String specifying line type. When distinguishing structure by SHADE or DASH, the order is flipped (to make newer ones stronger).
     * @param iStructure (int) Index of structure.
     * @param iModel (int) Index of model.
     * @param variable ({@link VariableType}) Variable plotted for by this line.
     * @param nStructure (int) Total number of structures.
     * @return (String) String specifying line type.
     */
    String lineTypeFor(int iStructure, int iModel, VariableType variable, int nStructure) {
        switch (structureDistinguisher) {
        case SHADE:
        case DASH:
            return lineTypeFor(nStructure - 1 - iStructure, iModel, variable);
        default:
            return lineTypeFor(iStructure, iModel, variable);
        }
    }

    /**
     * Get String specifying line type.
     * @param iStructure (int) Index of structure.
     * @param iModel (int) Index of model.
     * @param variable ({@link VariableType}) Variable plotted for by this line.
     * @return (String) String specifying line type.
     */
    String lineTypeFor(int iStructure, int iModel, VariableType variable) {
        int iVariable = (variable != null) ? variableTypes.indexOf(variable) : 0;
        // set defaults
        int iColor = 0;
        int iShade = 1;
        int iDash = 1;
        // select
        switch (structureDistinguisher) {
        case COLOR: iColor = iStructure % 6; break;
        case SHADE: iShade = iStructure % 4; break;
        case DASH: iDash = iStructure + 1; break;
        case NONE: break;
        }
        switch (modelDistinguisher) {
        case COLOR: iColor = iModel % 6; break;
        case SHADE: iShade = iModel % 4; break;
        case DASH: iDash = iModel + 1; break;
        case NONE: break;
        }
        switch (variableDistinguisher) {
        case COLOR: iColor = iVariable % 6; break;
        case SHADE: iShade = iVariable % 4; break;
        case DASH: iDash = iVariable + 1; break;
        case NONE: break;
        }
        // when structure color is set, overwrite color
        if (colors != null && colors.get(iStructure) != null && colors.get(iStructure) != Color.NONE) {
            iColor = colors.get(iStructure).getNumber();
        }
        // create and return String
        String lineTypeString = "dt " + iDash + " lc rgb '" + COLORS[iColor][iShade] + "'";
        return lineTypeString;
    }

}
