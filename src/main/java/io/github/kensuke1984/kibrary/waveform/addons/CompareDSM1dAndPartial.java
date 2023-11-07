package io.github.kensuke1984.kibrary.waveform.addons;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

/**
 * Calcurate the variance between residual waveforms and partial derivative waveforms.
 * To run this class, The paths to perturbed waveform, original waveform, partial derivative waveform,
 * and unknown file are needed.
 *
 * @author rei
 *
 */
public class CompareDSM1dAndPartial {

    public void main(String[] args) {
        if (args.length != 4) {
            System.err.println("To run this class, 4 arguments are needed");
            System.err.println("[1] Path to perturbed waveform");
            System.err.println("[2] Path to original waveform");
            System.err.println("[3] Path to partial");
            System.err.println("[4] Path to unknown parameter file");
            return;
        }

        Path perturbedBasicPath = Paths.get(args[0]);
        Path originalBasicPath = Paths.get(args[1]);
        Path partialPath = Paths.get(args[2]);
        Path unknownParameterPath = Paths.get(args[3]);

        try {
            // Read files
            List<BasicID> perturbedIDs = BasicIDFile.read(perturbedBasicPath, true);
            List<BasicID> originalIDs = BasicIDFile.read(originalBasicPath, true);
            List<PartialID> partialIDs = PartialIDFile.read(partialPath, true);
            List<UnknownParameter> parameterList = UnknownParameterFile.read(unknownParameterPath);

            for (BasicID originalID: originalIDs) {
                BasicID perturbedID;
                PartialID partialID;
                boolean findPair = false;
                // Find corresponding pertirbed basicID
                for (BasicID pID: perturbedIDs) {
                    if (BasicID.isPair(pID, originalID)) {
                        perturbedID = pID;
                        findPair = true;
                    }
                }
                if (!findPair)
                    throw new IllegalArgumentException("Basic ID pair is not found");
                // Find corresponding partialID
                findPair = false;
                for (PartialID pID: partialIDs) {
                    if (BasicID.isPair(pID, originalID)) {
                        partialID = pID;
                        findPair = true;
                    }
                }
                if (!findPair)
                    throw new IllegalArgumentException("BasicID and PartialID pair is not found");

            }
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
    }

}
