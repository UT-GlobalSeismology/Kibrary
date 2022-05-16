package io.github.kensuke1984.kibrary.inv_new;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import io.github.kensuke1984.kibrary.util.MathAid;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.voxel.Physical3DParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;

public class Multigrid_temp {

    private static PolynomialStructure initialStructure = PolynomialStructure.PREM;


    public static void main(String[] args) throws IOException {
        String mode = args[0];

        if (mode.equals("-u")) {
            System.err.println("working");

            Path unknownsPath = Paths.get(args[1]);
            List<UnknownParameter> unknownsList = UnknownParameterFile.read(unknownsPath);
            List<UnknownParameter> newList = new ArrayList<>();

            for (UnknownParameter unknown : unknownsList) {
                FullPosition position = unknown.getPosition();

                if (position.getR() < 3610 && position.getLatitude() < -34) {
                    if (MathAid.equalWithinEpsilon(position.getLongitude(), -25, 0.1)) {
                        UnknownParameter doubled = new Physical3DParameter(unknown.getPartialType(), unknown.getPosition(), unknown.getWeighting() * 2);
                        newList.add(doubled);
                    } else if (MathAid.equalWithinEpsilon(position.getLongitude(), -20, 0.1)) {
                        continue;
                    } else {
                        newList.add(unknown);
                    }
                } else {
                    newList.add(unknown);
                }
            }

            UnknownParameterFile.write(newList, Paths.get("unknowns_multi.lst"));
        }



    }
}
