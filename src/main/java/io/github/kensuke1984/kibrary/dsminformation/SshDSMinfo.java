package io.github.kensuke1984.kibrary.dsminformation;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.OpenOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.util.Location;
import io.github.kensuke1984.kibrary.util.Station;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTData;

/**
 * Information file for SSHPSV and SSHSH
 * 
 * <p>
 * This class is <b>IMMUTABLE</b>
 * 
 * 
 * @version 0.0.6
 * 
 * @author Kensuke Konishi
 * 
 */
public class SshDSMinfo extends SyntheticDSMInfo {

	private final double[] RADII;

	public SshDSMinfo(PolynomialStructure structure, GlobalCMTData event, Set<Station> stations, String outputDir,
			double[] perturbationR, double tlen, int np) {
		super(structure, event, stations, outputDir, tlen, np);
		RADII = perturbationR.clone();
	}

	/**
	 * sshpsv(TI)計算用のファイル出力
	 * 
	 * @param psvPath
	 *            output path
	 * @param options
	 *            options for writing
	 * @throws IOException
	 *             if any
	 */
	public void writeTIPSV(Path psvPath, OpenOption... options) throws IOException {
		try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(psvPath, options))) {
			// header
			String[] header = outputDSMHeader();
			Arrays.stream(header).forEach(pw::println);

			// structure
			Arrays.stream(STRUCTURE.toPSVlines()).forEach(pw::println);

			Location eventLocation = EVENT.getCmtLocation();

			// source
			pw.println(eventLocation.getR() + " " + eventLocation.getLatitude() + " " + eventLocation.getLongitude()
					+ " r0(km), lat, lon (deg)");
			pw.println(
					Arrays.stream(EVENT.getCmt().getDSMmt()).mapToObj(Double::toString).collect(Collectors.joining(" "))
							+ " Moment Tensor (1.e25 dyne cm)");
			pw.println("c directory of outputs");
			pw.println(OUTPUT + "/");
			pw.println("PSV.spc");
			pw.println(STATIONS.size() + " nsta");
			STATIONS.stream().sorted().map(Station::getStationName).forEach(n -> {
				pw.println(n + "." + EVENT + ".PARA");
				pw.println(n + "." + EVENT + ".PARC");
				pw.println(n + "." + EVENT + ".PARF");
				pw.println(n + "." + EVENT + ".PARL");
				pw.println(n + "." + EVENT + ".PARN");
			});

			STATIONS.stream().sorted().map(Station::getPosition)
					.forEach(p -> pw.println(p.getLatitude() + " " + p.getLongitude()));
			pw.println(RADII.length + " nr");
			Arrays.stream(RADII).forEach(pw::println);
			pw.println("end");
		}
	}

	/**
	 * sshpsvi(isotropic)計算用のファイル出力
	 * 
	 * @param psvPath
	 *            output path
	 * @param options
	 *            options for writing
	 * @throws IOException
	 *             if any
	 */
	public void writeISOPSV(Path psvPath, OpenOption... options) throws IOException {
		try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(psvPath, options))) {
			// header
			String[] header = outputDSMHeader();
			Arrays.stream(header).forEach(pw::println);

			// structure
			Arrays.stream(STRUCTURE.toPSVlines()).forEach(pw::println);
			Location eventLocation = EVENT.getCmtLocation();
			// source
			pw.println(eventLocation.getR() + " " + eventLocation.getLatitude() + " " + eventLocation.getLongitude()
					+ " r0(km), lat, lon (deg)");
			pw.println(
					Arrays.stream(EVENT.getCmt().getDSMmt()).mapToObj(Double::toString).collect(Collectors.joining(" "))
							+ " Moment Tensor (1.e25 dyne cm)");
			pw.println("c directory of outputs");
			pw.println(OUTPUT + "/");
			pw.println("PSV.spc");

			pw.println(STATIONS.size() + " nsta");
			STATIONS.stream().sorted().map(Station::getStationName).forEach(n -> pw.println(n + "." + EVENT + ".PAR2"));
			STATIONS.stream().sorted().map(Station::getPosition)
					.forEach(p -> pw.println(p.getLatitude() + " " + p.getLongitude()));

			pw.println(RADII.length + " nr");
			Arrays.stream(RADII).forEach(pw::println);
			pw.println("end");
		}
	}

	/**
	 * sshsh(TI)計算用のファイル出力
	 * 
	 * @param shPath
	 *            output path
	 * @param options
	 *            options for writing
	 * @throws IOException
	 *             if any
	 */
	public void writeTISH(Path shPath, OpenOption... options) throws IOException {
		try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(shPath, options))) {
			// header
			String[] header = outputDSMHeader();
			Arrays.stream(header).forEach(pw::println);

			// structure
			Arrays.stream(STRUCTURE.toSHlines()).forEach(pw::println);
			Location eventLocation = EVENT.getCmtLocation();
			// source
			pw.println(eventLocation.getR() + " " + eventLocation.getLatitude() + " " + eventLocation.getLongitude()
					+ " r0(km), lat, lon (deg)");
			pw.println(
					Arrays.stream(EVENT.getCmt().getDSMmt()).mapToObj(Double::toString).collect(Collectors.joining(" "))
							+ " Moment Tensor (1.e25 dyne cm)");
			pw.println("c directory of outputs");
			pw.println(OUTPUT + "/");
			pw.println("SH.spc");

			pw.println(STATIONS.size() + " nsta");
			STATIONS.stream().sorted().map(Station::getStationName).forEach(n -> {
				pw.println(n + "." + EVENT + ".PARL");
				pw.println(n + "." + EVENT + ".PARN");
			});

			STATIONS.stream().sorted().map(Station::getPosition)
					.forEach(p -> pw.println(p.getLatitude() + " " + p.getLongitude()));
			pw.println(RADII.length + " nr");
			Arrays.stream(RADII).forEach(pw::println);
			pw.println("end");
		}
	}

	/**
	 * sshshi(isotropic)計算用のファイル出力
	 * 
	 * @param shPath
	 *            output path
	 * @param options
	 *            for writing
	 * @throws IOException
	 *             if any
	 */
	public void writeISOSH(Path shPath, OpenOption... options) throws IOException {
		try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(shPath, options))) {
			// header
			String[] header = outputDSMHeader();
			Arrays.stream(header).forEach(pw::println);

			// structure
			Arrays.stream(STRUCTURE.toSHlines()).forEach(pw::println);
			Location eventLocation = EVENT.getCmtLocation();
			// source
			pw.println(eventLocation.getR() + " " + eventLocation.getLatitude() + " " + eventLocation.getLongitude()
					+ " r0(km), lat, lon (deg)");
			pw.println(
					Arrays.stream(EVENT.getCmt().getDSMmt()).mapToObj(Double::toString).collect(Collectors.joining(" "))
							+ " Moment Tensor (1.e25 dyne cm)");
			pw.println("c directory of outputs");
			pw.println(OUTPUT + "/");
			pw.println("SH.spc");
			pw.println(STATIONS.size() + " nsta");
			STATIONS.stream().sorted().map(Station::getStationName).forEach(n -> pw.println(n + "." + EVENT + ".PAR2"));
			STATIONS.stream().sorted().map(Station::getPosition)
					.forEach(p -> pw.println(p.getLatitude() + " " + p.getLongitude()));

			pw.println(RADII.length + " nr");
			Arrays.stream(RADII).forEach(pw::println);
			pw.println("end");
		}
	}

	/**
	 * @return radii for the perturbation points
	 */
	public double[] getPerturbationPointDepth() {
		return RADII.clone();
	}
}
