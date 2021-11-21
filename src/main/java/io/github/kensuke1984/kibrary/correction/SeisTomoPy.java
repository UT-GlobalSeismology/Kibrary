package io.github.kensuke1984.kibrary.correction;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

public class SeisTomoPy {

	public static void main(String[] args) {
//		Path waveformIDPath = Paths.get(args[0]);
//		Path outpath = Paths.get("event_stations.inf");
		
		Path correctionFile = Paths.get(args[0]);
		Path outpath = Paths.get("seisTomoPyStaticCorrection.dat");
		try {
//			writeRaypathFile(outpath, waveformIDPath);
			
			Set<StaticCorrectionData> correctionSet = readCorrections(correctionFile);
			StaticCorrectionDataFile.write(correctionSet, outpath);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
	private static void writeRaypathFile(Path outpath, Path waveformIDPath) throws IOException {
		BasicID[] ids = BasicIDFile.read(waveformIDPath);
		BufferedWriter br = new BufferedWriter(new FileWriter(outpath.toFile()));
		for (BasicID id : ids) {
			if (!id.getWaveformType().equals(WaveformType.SYN))
				continue;
			Observer station = id.getObserver();
			FullPosition evtloc = id.getGlobalCMTID().getEvent().getCmtLocation();
			String phaseString = "S";
			if (id.getSacComponent().equals(SACComponent.Z))
				phaseString = "P";
			br.write(id.getGlobalCMTID() + " " + evtloc.getLatitude() + " " + evtloc.getLongitude() + " " + (Earth.EARTH_RADIUS - evtloc.getR())
					+ " " + station.getStation() + " " + station.getNetwork() + " " 
					+ station.getPosition().getLatitude() + " " + station.getPosition().getLongitude()
					+ " " + id.getSacComponent() + " " + id.getStartTime() + " " + phaseString + "\n");
		}
		br.close();
	}
	
	private static Set<StaticCorrectionData> readCorrections(Path correctionFile) throws IOException {
		Set<StaticCorrectionData> corrections = new HashSet<>();
		BufferedReader br = Files.newBufferedReader(correctionFile);
		String line = "";
		while ((line = br.readLine()) != null) {
			String[] ss = line.split("\\s+");
			Observer station = new Observer(ss[4], new HorizontalPosition(Double.parseDouble(ss[6]), Double.parseDouble(ss[7])), ss[5]);
			SACComponent component = ss[9].trim().equals("S") ? SACComponent.T : SACComponent.Z;
			
			double shift = -Double.parseDouble(ss[10]);
			if (Double.parseDouble(ss[12]) > -100.)
				shift = -Double.parseDouble(ss[10]) + Double.parseDouble(ss[12]);
//				-Double.parseDouble(ss[10]) - Double.parseDouble(ss[12]); //bad
//				Double.parseDouble(ss[10]) +  Double.parseDouble(ss[12]); //bad
//				Double.parseDouble(ss[10]) - Double.parseDouble(ss[12]); //bad
			
			StaticCorrectionData correction = new StaticCorrectionData(station, new GlobalCMTID(ss[0].trim())
			, component, Double.parseDouble(ss[8]), shift, 1., new Phase[] { Phase.create(ss[9].trim())});
			
			corrections.add(correction);
		}
		
		return corrections;
	}

}
