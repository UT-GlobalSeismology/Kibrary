package io.github.kensuke1984.kibrary.model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import io.github.kensuke1984.kibrary.dsmsetup.PolynomialStructure;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

/**
 * @author otsuru
 * @since 2022/4/9
 */
public class VelocityModelMapper {

    public static void main(String[] args) throws IOException {
        int maxk = Integer.parseInt(args[0]);

        for (int k=1; k<maxk; k++){
            Path homedir = Paths.get(args[1]);
            Path unknownsPath = homedir.resolve("unknownParameterOrder.inf");
            Path ansHome = homedir.resolve("CG"+k);
            Path answerPath = ansHome.resolve("CG"+k+".txt"); // 4のファイル

            Path outputPercentPath = ansHome.resolve("vsPercent"+k+".dat");


            List<UnknownParameter> unknowns = UnknownParameterFile.read(unknownsPath);
            double[] answers = readAnswer(answerPath);
            PerturbationModel model = new PerturbationModel(unknowns, answers, PolynomialStructure.PREM);

            PerturbationModelFile.writePercentForType(PartialType.Vs, model, outputPercentPath);
        }
    }

    private static double[] readAnswer(Path answerPath) throws IOException {
        return Files.readAllLines(answerPath).stream().mapToDouble(Double::parseDouble).toArray();
    }

}
