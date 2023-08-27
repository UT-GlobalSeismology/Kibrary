package io.github.kensuke1984.kibrary.util.addons;

import io.github.kensuke1984.kibrary.inv_old.Dvector_old;
import io.github.kensuke1984.kibrary.inv_old.ObservationEquation_old;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameterFile;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.apache.commons.math3.linear.RealMatrix;

public class QMUPartialCorrelation {

	public static void main(String[] args) throws IOException {
		if (args.length != 5) {
			throw new RuntimeException("partialID partial basicID basic unknowns");
		}
		Path dataPath = Paths.get(args[1]);
		Path idPath = Paths.get(args[0]);
		Path unknownsPath = Paths.get(args[4]);
		Path basicDataPath = Paths.get(args[3]);
		Path basicIDPath = Paths.get(args[2]);
		
		PartialID[] partialIDs = PartialIDFile.read(idPath, dataPath);
		List<UnknownParameter> unknowns = UnknownParameterFile.read(unknownsPath);
		BasicID[] basicIDs = BasicIDFile.read(basicIDPath, basicDataPath);
		Dvector_old dVector = new Dvector_old(basicIDs);
		
		ObservationEquation_old equation = new ObservationEquation_old(partialIDs, unknowns, dVector, false, false, null, null, null, null);
		RealMatrix ata = equation.getAtA();
		
		Path outpath = Paths.get("parMU_Q_correlation.inf");
		try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outpath, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))) {
			for (int i = 0; i < unknowns.size(); i++) {
				UnknownParameter iPar = unknowns.get(i);
				double iloc = iPar.getPosition().getR();
				if (!iPar.getPartialType().equals(PartialType.MU1D))
					continue;
				for (int j = 0; j < unknowns.size(); j++) {
					UnknownParameter jPar = unknowns.get(j);
					double jloc = jPar.getPosition().getR();
					if (jPar.getPartialType().equals(PartialType.Q1D)
							&& iloc == jloc) {
						double dot = ata.getEntry(i, j) / Math.sqrt(ata.getEntry(i, i) * ata.getEntry(j, j));
						double depth = iPar.getPosition().getR();
						
						pw.println(depth + " " + dot);
						break;
					}
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
