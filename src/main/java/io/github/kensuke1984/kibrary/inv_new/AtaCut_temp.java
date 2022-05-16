package io.github.kensuke1984.kibrary.inv_new;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import io.github.kensuke1984.kibrary.inversion.Dvector;
import io.github.kensuke1984.kibrary.visual.MapperShellscript;
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
        MapperShellscript script = new MapperShellscript(radii, nameRoot + column + "Model");
        script.write(Paths.get(""));

    }
}
