package io.github.kensuke1984.kibrary.visual.plot;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;

import org.apache.commons.math3.analysis.polynomials.PolynomialFunction;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;

/**
 * Utils for plotting 1-D structure.
 * @author otsuru
 * @since 2024/3/30
 */
public class StructurePlotAid {

    private static final int NUM_VARIABLES = 6;
    private static final String[] COLORS = {
            "dark-magenta", "dark-orange", "web-green", "red", "web-blue", "dark-gray",
            "purple", "goldenrod", "greenyellow", "salmon", "skyblue", "gray",
            "plum", "khaki", "seagreen", "light-pink", "light-cyan", "light-gray"};

    private boolean colorByStructure;
    private boolean colorByVariable;
    private boolean dashByStructure;
    private boolean dashByVariable;

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
            functions = structure.getRho(); break;
        case Vpv:
            functions = structure.getVpv(); break;
        case Vph:
        case Vp:
            functions = structure.getVph(); break;
        case Vsv:
            functions = structure.getVsv(); break;
        case Vsh:
        case Vs:
            functions = structure.getVsh(); break;
        case ETA:
            functions = structure.getEta(); break;
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

    StructurePlotAid(boolean colorByStructure, boolean colorByVariable, boolean dashByStructure, boolean dashByVariable) {
        this.colorByStructure = colorByStructure;
        this.colorByVariable = colorByVariable;
        this.dashByStructure = dashByStructure;
        this.dashByVariable = dashByVariable;
    }

    /**
     * Get string specifying line type.
     * @param iStructure (int) Index of structure.
     * @param variable ({@link VariableType}) Variable plotted for by this line.
     * @return (String) String specifying line type.
     */
    String lineTypeFor(int iStructure, VariableType variable) {
        int iVariable;
        switch (variable) {
        case RHO:
            iVariable = 0; break;
        case Vpv:
            iVariable = 1; break;
        case Vph:
        case Vp:
            iVariable = 2; break;
        case Vsv:
            iVariable = 3; break;
        case Vsh:
        case Vs:
            iVariable = 4; break;
        case ETA:
            iVariable = 5; break;
        default:
            throw new IllegalArgumentException(variable + " not supported yet.");
        }

        int iColor;
        if (colorByStructure && colorByVariable) iColor = iStructure * NUM_VARIABLES + iVariable;
        else if (colorByStructure) iColor = iStructure;
        else if (colorByVariable) iColor = iVariable;
        else iColor = 0;

        int iDash;
        if (dashByStructure && dashByVariable) iDash = iStructure * NUM_VARIABLES + iVariable + 1;
        else if (dashByStructure) iDash = iStructure + 1;
        else if (dashByVariable) iDash = iVariable + 1;
        else iDash = 1;

        String lineTypeString = "dt " + iDash + " lc rgb '" + COLORS[iColor] + "'";
        return lineTypeString;
    }

}
