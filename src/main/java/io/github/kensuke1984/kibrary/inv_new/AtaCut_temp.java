package io.github.kensuke1984.kibrary.inv_new;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import io.github.kensuke1984.kibrary.inv_old.Dvector;
import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

public class AtaCut_temp {

    /**
     * path of partial ID file
     */
    protected Path partialIDPath;
    /**
     * path of partial data
     */
    protected Path partialPath;
    private Path unknownsPath;
    private Dvector DVECTOR;

    public static void main(String[] args) throws IOException {
        Path unknownsPath = Paths.get(args[0]);
        Path ataPath = Paths.get("ata.lst");
        Path outputPath = Paths.get("sectionAta.lst");

        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownsPath);
        List<String> lines = Files.readAllLines(ataPath);
        double[][] values = new double[lines.size()][lines.size()];
        for (int i = 0; i < parameterList.size(); i++) {
            String[] entries = lines.get(i).split("\\s+");
            for (int j = 0; j < parameterList.size(); j++) {
                values[i][j] = Double.parseDouble(entries[j]);
            }
        }

        for (int i = 0; i < parameterList.size(); i++) {
            for (int j = 0; j < parameterList.size(); j++) {
                if (i == j) continue;
                double coeff = values[i][j] * values[i][j] / values[i][i] / values[j][j];
                if (values[i][i] > Double.parseDouble(args[1]) && coeff > Double.parseDouble(args[2])) {
                    System.out.println(i + " " + j + " " + values[i][i] + " " + values[i][j] + " " + coeff);
                    System.out.println("  " + parameterList.get(i).getPosition() + " " + parameterList.get(j).getPosition());
                }
            }
        }
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {

            int iAccu = 0;
            for (int i = 0; i < parameterList.size(); i++) {
                UnknownParameter unknown = parameterList.get(i);

                if (MathAid.equalWithinEpsilon(unknown.getPosition().getR(), 3505, 1)) {
                    iAccu++;
                    String[] entries = lines.get(i).split("\\s+");

                    int jAccu = 0;
                    for (int j = 0; j < parameterList.size(); j++) {
                        UnknownParameter unknownJ = parameterList.get(j);

                        if (MathAid.equalWithinEpsilon(unknownJ.getPosition().getR(), 3505, 1)) {
                            jAccu++;
                            int base = 5;
                            if (iAccu > base && jAccu > base) {
                                pw.println((iAccu - base) + " " + (jAccu - base) + " " + entries[j]);
                            }
                        }
                    }
                }
            }
        }

    }

    public static void extractColumn(String[] args) throws IOException {
        Path unknownsPath = Paths.get(args[0]);
        String nameRoot = args[1];
        Path valuesPath = Paths.get(nameRoot + ".lst");
        int column = Integer.parseInt(args[2]);
        Path outputPath = Paths.get(nameRoot + column + "Model.lst");
        double amplify = Double.parseDouble(args[3]);

        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownsPath);
        double[] values = Files.readAllLines(valuesPath).stream().map(line -> line.split("\\s+")[column])
                .mapToDouble(Double::parseDouble).toArray();

        if (parameterList.size() != values.length) throw new IllegalStateException("number mismatch");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            for (int i = 0; i < parameterList.size(); i++) {
                pw.println(parameterList.get(i).getPosition() + " " + values[i] * amplify);
            }
        }

        double[] radii = parameterList.stream().mapToDouble(unknown -> unknown.getPosition().getR()).distinct().sorted().toArray();
//        MapperShellscript script = new MapperShellscript(radii, nameRoot + column + "Model");
//        script.write(Paths.get(""));

    }
}
