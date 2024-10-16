package io.github.kensuke1984.kibrary.waveform.convert;

import java.io.IOException;

public class MuToVs {

    public static void main(String[] args) throws IOException {
/*
        Path partialIDPath = Paths.get(args[0]);
        Path partialPath = Paths.get(args[1]);

        PartialID[] partials = PartialIDFile.read(partialIDPath, partialPath);

        List<PartialID> partialsMU = Stream.of(partials).filter(p -> p.getPartialType().equals(PartialType.MU3D)).collect(Collectors.toList());

        System.out.println(partialsMU.size());

        PolynomialStructure_old structure = PolynomialStructure_old.PREM;

        List<PartialID> partialsVs = partialsMU.stream().map(p -> {
            double r = p.getVoxelPosition().getR();
            double[] vsData = new ArrayRealVector(p.getData()).mapMultiply(2 * structure.getRhoAt(r) * structure.getVshAt(r)).toArray();

            PartialID parVs = new PartialID(p.getObserver(), p.getGlobalCMTID(), p.getSacComponent(), p.getSamplingHz(),
                    p.getStartTime(), p.getNpts(), p.getMinPeriod(), p.getMaxPeriod(),
                    p.getPhases(), p.isConvolved(), ParameterType.VOXEL, VariableType.Vs, p.getVoxelPosition(), vsData);
            return parVs;
        }).collect(Collectors.toList());


        String tmpString = GadgetAid.getTemporaryString();
        Path outID = Paths.get("partialID" + tmpString +".dat");
        Path out = Paths.get("partial" + tmpString + ".dat");

        Set<Observer> stationSet = partialsMU.stream().map(p -> p.getObserver()).collect(Collectors.toSet());
        Set<GlobalCMTID> globalCMTIDSet = partialsMU.stream().map(p -> p.getGlobalCMTID()).collect(Collectors.toSet());

        Set<Phase> phaseSet = new HashSet<>();
        partialsMU.stream().map(p -> new Phases(p.getPhases())).collect(Collectors.toSet()).forEach(p -> p.toSet().forEach(pp -> phaseSet.add(pp)));
        Phase[] phases = phaseSet.stream().collect(Collectors.toList()).toArray(new Phase[0]);

        List<double[]> periodRangeList = partialsMU.stream().map(p -> new FrequencyRange(1./p.getMaxPeriod(), 1./p.getMinPeriod())).distinct().map(f -> f.toPeriodRange()).collect(Collectors.toList());
        double[][] periodRanges = new double[periodRangeList.size()][];
        for (int i = 0; i < periodRangeList.size(); i++) {
            periodRanges[i] = periodRangeList.get(i);
        }

        Set<FullPosition> locationSet = partialsMU.stream().map(p -> p.getVoxelPosition()).collect(Collectors.toSet());

        WaveformDataWriter writer = new WaveformDataWriter(outID, out, stationSet, globalCMTIDSet, periodRanges, phases, locationSet);

        for (PartialID partial : partialsVs)
            writer.addPartialID(partial);

        writer.close();
*/
    }

}
