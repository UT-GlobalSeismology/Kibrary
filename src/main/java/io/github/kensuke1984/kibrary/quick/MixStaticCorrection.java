package io.github.kensuke1984.kibrary.quick;

import io.github.kensuke1984.kibrary.correction.StaticCorrectionData;
import io.github.kensuke1984.kibrary.correction.StaticCorrectionDataFile;
import io.github.kensuke1984.kibrary.util.GadgetAid;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class MixStaticCorrection {

	public static void main(String[] args) throws IOException {
		Path fujiStaticPath = Paths.get(args[0]);
		Path SEMFullStaticPath = Paths.get(args[1]);
		Path SEMTruncStaticPath = Paths.get(args[2]);
		
		Set<StaticCorrectionData> fujiCorrections = StaticCorrectionDataFile.read(fujiStaticPath);
		Set<StaticCorrectionData> semCorrections = StaticCorrectionDataFile.read(SEMFullStaticPath);
		Set<StaticCorrectionData> semTruncCorrections = StaticCorrectionDataFile.read(SEMTruncStaticPath);
		
		Set<StaticCorrectionData> mixed = new HashSet<>();
		
		for (StaticCorrectionData corr : fujiCorrections) {
			StaticCorrectionData semCorr = semCorrections.stream().filter(c -> corr.getGlobalCMTID().equals(c.getGlobalCMTID())
					&& corr.getObserver().equals(c.getObserver())
					&& corr.getComponent().equals(c.getComponent())
					&& corr.getSynStartTime() == c.getSynStartTime()).findFirst().get();
			StaticCorrectionData semTruncCorr = semTruncCorrections.stream().filter(c -> corr.getGlobalCMTID().equals(c.getGlobalCMTID())
					&& corr.getObserver().equals(c.getObserver())
					&& corr.getComponent().equals(c.getComponent())
					&& corr.getSynStartTime() == c.getSynStartTime()).findFirst().get();
			double difference = corr.getTimeshift() - semCorr.getTimeshift();
			StaticCorrectionData zeroCorr = new StaticCorrectionData(corr.getObserver(), corr.getGlobalCMTID(), corr.getComponent()
					, corr.getSynStartTime(), 0., corr.getAmplitudeRatio(), corr.getPhases());
			StaticCorrectionData mix = Math.abs(difference) <= Math.abs(corr.getTimeshift()) ? semTruncCorr : zeroCorr;
			mixed.add(mix);
		}
		
		Path outmix = Paths.get("staticCorrection" + GadgetAid.getTemporaryString() + ".dat");
		System.out.println("Write mixed correction");
		StaticCorrectionDataFile.write(mixed, outmix);
	}
	
}
