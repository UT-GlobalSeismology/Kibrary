package io.github.kensuke1984.kibrary.specfem;

import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.source.MomentTensor;
import io.github.kensuke1984.kibrary.util.DatasetAid;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTAccess;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.apache.commons.math3.util.Precision;

public class MakeRunFolder {

	public static void main(String[] args) {
		Path specfemRoot = Paths.get(System.getProperty("user.dir"));
		Path eventFolderPath = Paths.get(args[0]);
		int nSimultaneousRun = 20;
		
		try {
			if (!Files.isDirectory(specfemRoot))
				throw new FileNotFoundException(specfemRoot.toString());
			
			List<EventFolder> eventFolderSet = DatasetAid.eventFolderSet(eventFolderPath)
					.stream().collect(Collectors.toList());
			
			AtomicInteger iatom = new AtomicInteger(1);
			Path runGroupFolder = null;
			
			double tmp = (double) (eventFolderSet.size()) / nSimultaneousRun;
			int nOutFolder = tmp - (int) tmp == 0 ? (int) tmp : (int) tmp + 1;
			Path[] runDirs = new Path[nOutFolder];
			for (int i = 0; i < nOutFolder; i++) {
				runDirs[i] = specfemRoot.resolve(String.format("run%04d-%04d", nSimultaneousRun*i+1 ,nSimultaneousRun*(i+1)));
				Files.createDirectory(runDirs[i]);
			}
			
			for (int i = 0; i < eventFolderSet.size(); i++) {
//				if (iatom.get() == 1)
//					runGroupFolder 
//				if (iatom.get() <= nEvents)
				
				EventFolder eventFolder = eventFolderSet.get(i);
					
				String runFolder = String.format("run%04d", iatom.get());
				Path dirPath = runDirs[i/nSimultaneousRun].resolve(runFolder);
				Path dataFolderPath = dirPath.resolve("DATA");
				Path databasesmpiFolderPath = dirPath.resolve("DATABASES_MPI");
				Path outputfilesFolderPath = dirPath.resolve("OUTPUT_FILES");
				try {
					Files.createDirectories(dirPath);
					Files.createDirectories(dataFolderPath);
					Files.createDirectories(databasesmpiFolderPath);
					Files.createDirectories(outputfilesFolderPath);
					
					// write CMTSOLUTION file
					Path eventFile = dataFolderPath.resolve("CMTSOLUTION");
					GlobalCMTID id = eventFolder.getGlobalCMTID();
					String s = cmtSolutionString(id);
					Files.write(eventFile, s.getBytes());
					
					Set<Observer> stationSet = new HashSet<>();
					eventFolder.sacFileSet().parallelStream().forEach(sac -> {
						try {
							stationSet.add(sac.read().getObserver());
						} catch (IOException e) {
							System.err.format("IOException: %s%n", e);
						}
					});
					
					// write STATIONS file
					Path stationFile = dataFolderPath.resolve("STATIONS");
					try (BufferedWriter writer = Files.newBufferedWriter(stationFile)) {
						for (Observer sta : stationSet) {
							writer.write(String.format("%s %s %.3f %.3f 0.0 0.0%n"
								, sta.getStation()
								, sta.getNetwork()
								, sta.getPosition().getLatitude()
								, sta.getPosition().getLongitude())
							);
						}
					} catch (IOException e) {
						System.err.format("IOException: %s%n", e);
					}
				} catch (IOException e) {
					e.printStackTrace();
				}
				iatom.incrementAndGet();
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public static String cmtSolutionString(GlobalCMTID id) {
		GlobalCMTAccess idData = id.getEventData();
		LocalDateTime pdeTime = idData.getPDETime();
		MomentTensor mt = idData.getCmt();
		double pow = Math.pow(10, mt.getMtExponent());
		return String.format("%s%nevent name: %s%ntime shift: %.1f%nhalf duration: "
				+ "%.4f%nlatitude: %.4f%nlongitude: %.4f%ndepth: "
				+ "%.4f%nMrr: %.6e%nMtt: %.6e%nMpp: %.6e%nMrt: %.6e%nMrp: %.6e%nMtp: %.6e"
					, idData.getHypocenterReferenceCatalog()
						+ " " + pdeTime.getYear() + " " + pdeTime.getMonthValue()
						+ " " + pdeTime.getDayOfMonth() + " " + pdeTime.getHour()
						+ " " + pdeTime.getMinute() + " " + pdeTime.getSecond()
						+ "." + String.format("%.0f", pdeTime.getNano() * 1e-8)
						+ " " + String.format("%.2f", idData.getPDEPosition().getLatitude())
						+ " " + String.format("%.2f", idData.getPDEPosition().getLongitude())
						+ " " + String.format("%.2f", (6371. - idData.getPDEPosition().getR()))
						+ " " + idData.getMb() + " " + idData.getMs()
						+ " " + idData.getGeographicalLocationName()
					, id.toString()
					, idData.getTimeDifference()
					, idData.getHalfDuration()
					, idData.getCmtPosition().getLatitude()
					, idData.getCmtPosition().getLongitude()
					, Earth.EARTH_RADIUS - idData.getCmtPosition().getR()
					, mt.getMrrCoefficient() * pow
					, mt.getMttCoefficient() * pow
					, mt.getMppCoefficient() * pow
					, mt.getMrtCoefficient() * pow
					, mt.getMrpCoefficient() * pow
					, mt.getMtpCoefficient() * pow
			);
	}

}
