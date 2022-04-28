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

public class LetMeInvert_temp {

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
        Path outputPath = Paths.get(nameRoot + "Model.lst");
        double amplify = Double.parseDouble(args[2]);

        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownsPath);
        double[] values = Files.readAllLines(valuesPath).stream().mapToDouble(Double::parseDouble).toArray();

        if (parameterList.size() != values.length) throw new IllegalStateException("number mismatch");

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outputPath))) {
            for (int i = 0; i < parameterList.size(); i++) {
                pw.println(parameterList.get(i).getPosition() + " " + values[i] * amplify);
            }
        }

        double[] radii = parameterList.stream().mapToDouble(unknown -> unknown.getPosition().getR()).distinct().sorted().toArray();
        MapperShellscript script = new MapperShellscript(radii, nameRoot + "Model");
        script.write(Paths.get(""));

    }

/*
    public static void main(String[] args) throws IOException {
        Path partialIDPath = Paths.get(args[0]);
        Path partialPath = Paths.get(args[1]);
        Path unknownsPath = Paths.get(args[2]);

        LetMeInvert_temp tmp = new LetMeInvert_temp(partialIDPath, partialPath, unknownsPath);
        tmp.setEquation();
    }

    public LetMeInvert_temp(Path partialIDPath, Path partialPath, Path unknownsPath) throws IOException {
        this.partialIDPath = partialIDPath;
        this.partialPath = partialPath;
        this.unknownsPath = unknownsPath;
    }

    private void setEquation() throws IOException {

        List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownsPath);
        PartialID[] partialIDs = PartialIDFile.read(partialIDPath, partialPath);


    }

    private void readA(PartialID[] ids, List<UnknownParameter> parameterList) {

        Matrix a = new Matrix(DVECTOR.getNpts(), parameterList.size());
        a.scalarMultiply(0);



    }
*/
}
