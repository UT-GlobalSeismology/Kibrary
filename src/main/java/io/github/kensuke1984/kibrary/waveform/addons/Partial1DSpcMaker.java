package io.github.kensuke1984.kibrary.waveform.addons;

import java.io.IOException;
import java.io.PrintWriter;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.linear.ArrayRealVector;

import io.github.kensuke1984.anisotime.Phase;
import io.github.kensuke1984.kibrary.Operation;
import io.github.kensuke1984.kibrary.Property;
import io.github.kensuke1984.kibrary.correction.SourceTimeFunction;
import io.github.kensuke1984.kibrary.dsmsetup.PolynomialStructure;
import io.github.kensuke1984.kibrary.filter.BandPassFilter;
import io.github.kensuke1984.kibrary.filter.ButterworthFilter;
import io.github.kensuke1984.kibrary.math.FourierTransform;
import io.github.kensuke1984.kibrary.math.HilbertTransform;
import io.github.kensuke1984.kibrary.timewindow.TimewindowData;
import io.github.kensuke1984.kibrary.timewindow.TimewindowDataFile;
import io.github.kensuke1984.kibrary.util.EventFolder;
import io.github.kensuke1984.kibrary.util.Utilities;
import io.github.kensuke1984.kibrary.util.addons.Phases;
import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;
import io.github.kensuke1984.kibrary.util.sac.SACComponent;
import io.github.kensuke1984.kibrary.util.sac.SACFileAccess;
import io.github.kensuke1984.kibrary.util.sac.SACFileName;
import io.github.kensuke1984.kibrary.util.sac.WaveformType;
import io.github.kensuke1984.kibrary.util.spc.SPCFileAccess;
import io.github.kensuke1984.kibrary.util.spc.FormattedSPCFileName;
import io.github.kensuke1984.kibrary.util.spc.FujiConversion;
import io.github.kensuke1984.kibrary.util.spc.PartialType;
import io.github.kensuke1984.kibrary.util.spc.SACMaker;
import io.github.kensuke1984.kibrary.util.spc.SPCFileName;
import io.github.kensuke1984.kibrary.util.spc.SPCType;
import io.github.kensuke1984.kibrary.util.spc.VSConversion;
import io.github.kensuke1984.kibrary.waveform.BasicID;
import io.github.kensuke1984.kibrary.waveform.BasicIDFile;
import io.github.kensuke1984.kibrary.waveform.PartialID;
import io.github.kensuke1984.kibrary.waveform.PartialIDFile;
import io.github.kensuke1984.kibrary.waveform.WaveformDataWriter;

/**
 * Creates a pair of files containing 1-D partial derivatives
 * 
 * TODO shとpsvの曖昧さ 両方ある場合ない場合等 現状では combineして対処している
 * 
 * Time length (tlen) and the number of step in frequency domain (np) in DSM
 * software must be same. Those values are set in a parameter file.
 * 
 * Only partials for radius written in a parameter file are computed.
 * 
 * <b>Assume there are no station with the same name but different networks in
 * same events</b> TODO
 * 
 * @version 0.2.0.3
 * 
 * @author Kensuke Konishi
 * 
 */
public class Partial1DSpcMaker implements Operation {
	private boolean backward;

	private Path psvPath;
	
	private Path shPath;
	
	public static void writeDefaultPropertiesFile() throws IOException {
		Path outPath = Paths
				.get(Partial1DSpcMaker.class.getName() + Utilities.getTemporaryString() + ".properties");
		try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outPath, StandardOpenOption.CREATE_NEW))) {
			pw.println("manhattan Partial1DSpcMaker");
			pw.println("##Path of a working directory (.)");
			pw.println("#workPath");
			pw.println("##SacComponents to be used(Z R T)");
			pw.println("#components");
			pw.println("##If you do NOT want to use PSV or SH, you set the one 'null'.");
			pw.println("##Path of a PSV folder (.)");
			pw.println("#psvPath");
			pw.println("##Path of an SH folder (.)");
			pw.println("#shPath");
			pw.println("reFyIDPath");
			pw.println("reFyPath");
			pw.println("imFyIDPath");
			pw.println("imFyPath");
			pw.println("##String if it is PREM, spector files are found in [event folder]/PREM (PREM)");
			pw.println("modelName PREM");
			pw.println("##Type source time function 0:none, 1:boxcar, 2:triangle. (0)");
			pw.println("##or folder name containing *.stf if you want to your own GLOBALCMTID.stf ");
			pw.println("sourceTimeFunction 0");
			pw.println("##Path of a timewindow information file, must be set");
			pw.println("timewindowPath timewindow.dat");
			pw.println("##PartialType[] compute types (PARVS)");
			pw.println("partialTypes PARVS");
			pw.println("##Filter if backward filtering is applied (false)");
			pw.println("backward false");
			pw.println("##double time length:DSM parameter tlen (1638.4)");
			pw.println("tlen 1638.4");
			pw.println("##int step of frequency domain DSM parameter np (512)");
			pw.println("np 512");
			pw.println("##double[] freqRanges (input several ranges following the format minFreq1,maxFreq1 minFreq2,maxFreq2) (0.005,0.08)");
			pw.println("freqRanges 0.005,0.08");
			pw.println("filterNp 4");
			pw.println("#double");
			pw.println("#partialSamplingHz cant change now");
			pw.println("##double sampling Hz in output dataset (4)");
			pw.println("finalSamplingHz 4");
			pw.println("##radius for perturbation points, must be set");
			pw.println("bodyR 3505 3555 3605");
			pw.println("##path of the time partials directory, must be set if PartialType containes TIME_SOURCE or TIME_RECEIVER");
			pw.println("#timePartialPath");
			pw.println("##Polynomial structure file (leave blank if PREM)");
			pw.println("#ps");
			pw.println("lowFreq ");
			pw.println("highFreq ");
		}
		System.err.println(outPath + " is created.");
	}

	private Set<SACComponent> components;

	private Path workPath;
	
	private Path timePartialPath;

	private void checkAndPutDefaults() {
		if (!property.containsKey("workPath"))
			property.setProperty("workPath", ".");
		if (!property.containsKey("components"))
			property.setProperty("components", "Z R T");
		if (!property.containsKey("modelName"))
			property.setProperty("modelName", "PREM");
		if (!property.containsKey("sourceTimeFunction"))
			property.setProperty("sourceTimeFunction", "0");
		if (!property.containsKey("partialTypes"))
			property.setProperty("partialTypes", "PARVS");
		if (!property.containsKey("backward"))
			property.setProperty("backward", "false");
		if (!property.containsKey("freqRanges"))
			property.setProperty("freqRanges", "0.005,0.08");
		if (!property.containsKey("filterNp"))
			property.setProperty("filterNp", "4");
		if (!property.containsKey("finalSamplingHz"))
			property.setProperty("finalSamplingHz", "4");
		if (!property.containsKey("timewindowPath"))
			throw new IllegalArgumentException("There is no information about timewindowPath.");
		if (!property.containsKey("tlen"))
			property.setProperty("tlen", "1638.4");
		if (!property.containsKey("np"))
			property.setProperty("np", "512");
		if (!property.containsKey("ps"))
			property.setProperty("ps", "PREM");
	}
	
	private boolean par00;

	private int filterNp;
	
	private Path reFyIDPath;
	
	private Path reFyPath;
	
	private Path imFyIDPath;
	
	private Path imFyPath;
	
	double highFreq;
	
	double lowFreq;
	
	/**
	 * parameterのセット
	 */
	private void set() {
		checkAndPutDefaults();
		workPath = Paths.get(property.getProperty("workPath"));

		if (!Files.exists(workPath))
			throw new RuntimeException("The workPath: " + workPath + " does not exist");
		timewindowPath = getPath("timewindowPath");
		// pointFile = newFile(reader.getFirstValue("pointFile"));
		// System.out.println(reader.getFirstValue("pointFile"));
		components = Arrays.stream(property.getProperty("components").split("\\s+")).map(SACComponent::valueOf)
				.collect(Collectors.toSet());

		try {
			sourceTimeFunction = Integer.parseInt(property.getProperty("sourceTimeFunction"));
		} catch (Exception e) {
			sourceTimeFunction = -1;
			sourceTimeFunctionPath = getPath("sourceTimeFunction");
		}
		backward = Boolean.parseBoolean(property.getProperty("backward"));

		modelName = property.getProperty("modelName");

		partialTypes = Arrays.stream(property.getProperty("partialTypes").split("\\s+")).map(PartialType::valueOf)
				.collect(Collectors.toSet());
		if (partialTypes.contains(PartialType.PAR00)) {
			par00 = true;
			partialTypes.remove(PartialType.PAR00);
		}
		
		if (partialTypes.contains(PartialType.TIME_RECEIVER) || partialTypes.contains(PartialType.TIME_SOURCE)) {
				timePartialPath = Paths.get(property.getProperty("timePartialPath"));
				if (!Files.exists(timePartialPath))
					throw new RuntimeException("The timePartialPath: " + timePartialPath + " does not exist");
		}
		
		tlen = Double.parseDouble(property.getProperty("tlen"));
		np = Integer.parseInt(property.getProperty("np"));
		Set<double[]> periodsSet = Arrays.stream(property.getProperty("freqRanges").split("\\s+")).map(s 
				-> new double[] {1. / Double.parseDouble(s.split(",")[1]), 1. / Double.parseDouble(s.split(",")[0])}).collect(Collectors.toSet());
		periodRanges = periodsSet.toArray(new double[periodsSet.size()][]);
		filterNp = Integer.parseInt(property.getProperty("filterNp"));
		bodyR = Arrays.stream(property.getProperty("bodyR").split("\\s+")).mapToDouble(Double::parseDouble).toArray();
		// partialSamplingHz
		// =Double.parseDouble(reader.getFirstValue("partialSamplingHz")); TODO
		finalSamplingHz = Double.parseDouble(property.getProperty("finalSamplingHz"));
		
		filter = new ArrayList<>();
		
		structurePath = property.getProperty("ps");
		
		psvPath = property.containsKey("psvPath") ? Paths.get(property.getProperty("psvPath")) : null;
		shPath = property.containsKey("shPath") ? Paths.get(property.getProperty("shPath")) : null;
		
		if (shPath == null && psvPath == null)
			throw new RuntimeException("None of psv or sh paths are defined");
		
		reFyIDPath = getPath("reFyIDPath");
		reFyPath = getPath("reFyPath");
		
		imFyIDPath = getPath("imFyIDPath");
		imFyPath = getPath("imFyPath");
		
		highFreq = Double.parseDouble(property.getProperty("highFreq"));
		lowFreq = Double.parseDouble(property.getProperty("lowFreq"));
	}

	/**
	 * bp, fp フォルダの下のどこにspcファイルがあるか 直下なら何も入れない（""）
	 */
	private String modelName;
	
	private String structurePath;

	/**
	 * Path of a timewindow information file
	 */
	private Path timewindowPath;

	/**
	 * Partial types
	 */
	private Set<PartialType> partialTypes;

	/**
	 * bandpassの最小周波数（Hz）
	 */
//	private double minFreq;

	/**
	 * bandpassの最大周波数（Hz）
	 */
//	private double maxFreq;
	
	/**
	 * spcFileをコンボリューションして時系列にする時のサンプリングHz デフォルトは２０ TODOまだ触れない
	 */
	private double partialSamplingHz = 20;

	/**
	 * 最後に時系列で切り出す時のサンプリングヘルツ(Hz)
	 */
	private double finalSamplingHz;

	/**
	 * The folder contains source time functions.
	 */
	private Path sourceTimeFunctionPath;

	/**
	 * 0:none, 1:boxcar, 2:triangle.
	 */
	private int sourceTimeFunction;

	/**
	 * time length (DSM parameter)
	 */
	private double tlen;

	/**
	 * step of frequency domain (DSM parameter)
	 */
	private int np;

	/**
	 * radius of perturbation
	 */
	private double[] bodyR;
	/**
	 * 追加したID数
	 */
	private int numberOfAddedID;

	private synchronized void add() {
		numberOfAddedID++;
	}

	private int lsmooth;

	private void setLsmooth() {
		int pow2np = Integer.highestOneBit(np);
		if (pow2np < np)
			pow2np *= 2;

		int lsmooth = (int) (0.5 * tlen * partialSamplingHz / pow2np);
		int ismooth = Integer.highestOneBit(lsmooth);
		this.lsmooth = ismooth == lsmooth ? lsmooth : ismooth * 2;
	}

	private class Worker implements Runnable {

		private SourceTimeFunction sourceTimeFunction;
		private GlobalCMTID id;

		@Override
		public void run() {
			try {
				writeLog("Running on " + id);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			Path spcFolder = eventDir.toPath().resolve(modelName); // SPCの入っているフォルダ

			if (!Files.exists(spcFolder)) {
				System.err.println(spcFolder + " does not exist...");
				return;
			}

			Set<SPCFileName> spcFileNames;
			try {
				if (shPath != null && psvPath == null) {
					spcFileNames = collectSHSPCs(spcFolder);
					System.out.println("Collecting SH spc");
				}
				else if (psvPath != null && shPath == null) {
					spcFileNames = collectPSVSPCs(spcFolder);
					System.out.println("Collecting PSV spc");
				}
				else {
					spcFileNames = collectPSVSPCs(spcFolder);
					System.out.println("Collecting PSV and SH spc");
				}
			} catch (IOException e1) {
				e1.printStackTrace();
				return;
			}
			
			// compute source time function
			sourceTimeFunction = computeSourceTimeFunction();
			
			Set<TimewindowData> timewindowCurrentEvent = timewindowInformationSet
					.stream()
					.filter(tw -> tw.getGlobalCMTID().equals(id))
					.collect(Collectors.toSet());
			
			// すべてのspcファイルに対しての処理
			for (SPCFileName spcFileName : spcFileNames) {
				// 理論波形（非偏微分係数波形）ならスキップ
				if (spcFileName.isSynthetic())
					continue;

				if (!spcFileName.getSourceID().equals(id.toString())) {
					try {
						writeLog(spcFileName + " has an invalid global CMT ID.");
						continue;
					} catch (IOException e) {
						e.printStackTrace();
					}
				}

				SPCType spcFileType = spcFileName.getFileType();
				
				// 3次元用のスペクトルなら省く
				if (spcFileType == SPCType.PB || spcFileType == SPCType.PF)
					continue;

				// check if the partialtype is included in computing list.
				PartialType partialType = PartialType.valueOf(spcFileType.toString());

				if (!(partialTypes.contains(partialType)
						|| (partialTypes.contains(PartialType.PARQ) && spcFileType == SPCType.PAR2)))
					continue;
				
				SPCFileName shspcname = null;
				if (psvPath != null && shPath != null) {
					if (spcFileType.equals(SPCType.PARN)
					|| spcFileType.equals(SPCType.PARL)
					|| spcFileType.equals(SPCType.PAR0)
					|| spcFileType.equals(SPCType.PAR2))
						shspcname = new FormattedSPCFileName(spcFileName.getPath().replace("PSV.spc", "SH.spc"));
				}
				
				try {
					if (shspcname == null)
						addPartialSpectrum(spcFileName, timewindowCurrentEvent);
					else {
						if(shspcname.exists())
							addPartialSpectrum(spcFileName, shspcname, timewindowCurrentEvent);
						else {
							System.out.println("SH spc file not found " + shspcname);
							addPartialSpectrum(spcFileName, timewindowCurrentEvent);
						}
					}
				} catch (ClassCastException e) {
					// 出来上がったインスタンスがOneDPartialSpectrumじゃない可能性
					System.err.println(spcFileName + "is not 1D partial.");
					continue;
				} catch (Exception e) {
					System.err.println(spcFileName + " is invalid.");
					e.printStackTrace();
					try {
						writeLog(spcFileName + " is invalid.");
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					continue;
				}
			}
			System.out.print(".");
			
		}

		private SourceTimeFunction computeSourceTimeFunction() {
			GlobalCMTID id = eventDir.getGlobalCMTID();
			double halfDuration = id.getEvent().getHalfDuration();
			switch (Partial1DSpcMaker.this.sourceTimeFunction) {
			case -1:
				return userSourceTimeFunctions.get(id);
			case 0:
				return null;
			case 1:
				return SourceTimeFunction.boxcarSourceTimeFunction(np, tlen, partialSamplingHz, halfDuration);
			case 2:
				return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, partialSamplingHz, halfDuration);
			case 3:
				if (stfcat.contains("LSTF")) {
		        	double halfDuration1 = id.getEvent().getHalfDuration();
		        	double halfDuration2 = id.getEvent().getHalfDuration();
		        	boolean found = false;
			      	for (String str : stfcat) {
			      		String[] stflist = str.split("\\s+");
			      	    GlobalCMTID eventID = new GlobalCMTID(stflist[0]);
			      	    if(id.equals(eventID)) {
			      	    	if(Integer.valueOf(stflist[3]) >= 5.) {
			      	    		halfDuration1 = Double.valueOf(stflist[1]);
			      	    		halfDuration2 = Double.valueOf(stflist[2]);
			      	    		found = true;
			      	    	}
			      	    }
			      	}
			      	SourceTimeFunction stf = null;
			      	if (found) {
			      		stf = SourceTimeFunction.asymmetrictriangleSourceTimeFunction(np, tlen, partialSamplingHz, halfDuration1, halfDuration2);
		//	      		System.out.println(id + " Using LSTF with duration " + (halfDuration1 + halfDuration2));
			      	}
			      	else
			      		stf = SourceTimeFunction.triangleSourceTimeFunction(np, tlen, partialSamplingHz, id.getEvent().getHalfDuration());
			      	return stf;
				}
				else {
					boolean found = false;
					double ampCorr = 1.;
					for (String str : stfcat) {
			      		String[] ss = str.split("\\s+");
			      	    GlobalCMTID eventID = new GlobalCMTID(ss[0]);
			      	    if (id.equals(eventID)) {
			      	    	halfDuration = Double.parseDouble(ss[1]);
			      	    	ampCorr = Double.parseDouble(ss[2]);
			      	    	found = true;
			      	    	break;
			      	    }
			      	}
					if (found) {
//						System.out.println(id + " " + halfDuration + " " + ampCorr);
						return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, partialSamplingHz, halfDuration, ampCorr);
					}
					else
						return SourceTimeFunction.triangleSourceTimeFunction(np, tlen, partialSamplingHz, id.getEvent().getHalfDuration());
				}
			default:
				throw new RuntimeException("Integer for source time function is invalid.");
			}
		}
		
		private int finalFreqSamplingHz = 8;
		
		private void cutAndWrite(Observer station, double[] filteredUt, TimewindowData t, double bodyR,
				PartialType partialType, double[] periodRange) {
			
			double[] cutU = cutU(filteredUt, t);
			FourierTransform fourier = new FourierTransform(cutU, finalFreqSamplingHz);
			double[] partialReFy = fourier.getRealFy();
			double[] partialImFy = fourier.getImFy();
			
			double[] imFy = findImFyID(t).getData();
			double[] reFy = findReFyID(t).getData();
			double[] ampSquared = new double[imFy.length];
			for (int i = 0; i < imFy.length; i++)
				ampSquared[i] = imFy[i] * imFy[i] + reFy[i] * reFy[i];
			
			double df = fourier.getFreqIncrement(partialSamplingHz);
			
			if (highFreq > partialSamplingHz)
				throw new RuntimeException("f1 must be <= sacSamplingHz");
			int iStart = (int) (lowFreq / df) - 1;
			int fnpts = (int) ((highFreq - lowFreq) / df);
			
			double[] cutPartialReFy = IntStream.range(0, fnpts).parallel().mapToDouble(i -> partialReFy[i + iStart]).toArray();
			double[] cutPartialImFy = IntStream.range(0, fnpts).parallel().mapToDouble(i -> partialImFy[i + iStart]).toArray();
			
			double[] cutPartialSpcAmp = new double[cutPartialReFy.length];
			
			for (int i = 0; i < cutPartialReFy.length; i++) {
				cutPartialSpcAmp[i] = 1. / ampSquared[i] * (reFy[i] * cutPartialReFy[i] + imFy[i] * cutPartialImFy[i]);
			}
			
			// water level fix for "jumping" partials
			double max = new ArrayRealVector(ampSquared).getLInfNorm();
			double waterlevel = max / 100.;
			for (int i = 0; i < cutPartialReFy.length; i++) {
				if (ampSquared[i] < waterlevel)
					cutPartialSpcAmp[i] = 0.;
			}
			
			PartialID pid = new PartialID(station, id, t.getComponent(), finalSamplingHz, t.getStartTime(), cutPartialReFy.length,
					periodRange[0], periodRange[1], t.getPhases(), 0, sourceTimeFunction != null, new FullPosition(0, 0, bodyR), partialType,
					cutPartialSpcAmp);
		
			try {
				partialDataWriter.addPartialID(pid);
				add();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
		
		private BasicID findImFyID(TimewindowData t) {
			try {
				return Arrays.stream(imFyIDs).filter(id -> id.getObserver().equals(t.getObserver())
						&& id.getGlobalCMTID().equals(t.getGlobalCMTID()) && Math.abs(id.getStartTime() - t.getStartTime()) < 1.
						&& t.getComponent().equals(id.getSacComponent()))
					.findAny().get();
			} catch (NoSuchElementException e) {
				System.err.println(t);
				e.printStackTrace();
				throw new RuntimeException();
			}
		}
		
		private BasicID findReFyID(TimewindowData t) {
			return Arrays.stream(reFyIDs).filter(id -> id.getObserver().equals(t.getObserver())
					&& id.getGlobalCMTID().equals(t.getGlobalCMTID()) && Math.abs(id.getStartTime() - t.getStartTime()) < 1.
					&& t.getComponent().equals(id.getSacComponent()))
				.findAny().get();
		}

		private void process(SPCFileAccess spectrum) {
			for (SACComponent component : components)
				spectrum.getSpcBodyList().stream().map(body -> body.getSpcComponent(component))
						.forEach(spcComponent -> {
							if (sourceTimeFunction != null)
								spcComponent.applySourceTimeFunction(sourceTimeFunction);
							spcComponent.toTimeDomain(lsmooth);
							spcComponent.applyGrowingExponential(spectrum.omegai(), tlen);
							spcComponent.amplitudeCorrection(tlen);
						});
		}

		private void addPartialSpectrum(SPCFileName spcname, Set<TimewindowData> timewindowCurrentEvent) throws IOException {
			Set<TimewindowData> tmpTws = timewindowCurrentEvent.stream()
					.filter(info -> info.getObserver().getStation().equals(spcname.getStationCode())
							&& info.getObserver().getNetwork().equals(spcname.getNetworkCode()))
					.collect(Collectors.toSet());
			if (tmpTws.size() == 0) {
//				System.out.println("No timewindow found");	
				return;
			}
			
			System.out.println(spcname);
			SPCFileAccess spectrum = spcname.read();
			if (spectrum.tlen() != tlen || spectrum.np() != np) {
				System.err.println(spcname + " has different np or tlen.");
				writeLog(spcname + " has different np or tlen.");
				return;
			}
			
			String stationName = spcname.getStationCode();
			String network = spcname.getNetworkCode();
			Observer station = new Observer(stationName, spectrum.getObserverPosition(), network);
			PartialType partialType = PartialType.valueOf(spcname.getFileType().toString());
			SPCFileAccess qSpectrum = null;
			SPCFileAccess vsimSpectrum = null;
			if (spcname.getFileType() == SPCType.PAR2 && partialTypes.contains(PartialType.PARQ)) {
				qSpectrum = fujiConversion.convert(spectrum);
				process(qSpectrum);
			}
			process(spectrum);

			for (SACComponent component : components) {
				Set<TimewindowData> tw = tmpTws.stream()
						.filter(info -> info.getObserver().equals(station))
						.filter(info -> info.getGlobalCMTID().equals(id))
						.filter(info -> info.getComponent().equals(component)).collect(Collectors.toSet());

				if (tw.isEmpty()) {
					tmpTws.forEach(window -> {
						System.out.println(window);
						System.out.println(window.getObserver().getPosition());
					});
					System.err.println(station.getPosition());
					System.err.println("Ignoring empty timewindow " + spcname + " " + station);
					continue;
				}

				for (int k = 0; k < spectrum.nbody(); k++) {
					double bodyR = spectrum.getBodyR()[k];
					boolean exists = false;
					for (double r : Partial1DSpcMaker.this.bodyR)
						if (Utilities.equalWithinEpsilon(r, bodyR, eps))
							exists = true;
					if (!exists)
						continue;
					double[] ut = spectrum.getSpcBodyList().get(k).getSpcComponent(component).getTimeseries();
					// applying the filter
					
					for (int i = 0; i < periodRanges.length; i++) {
						ButterworthFilter tmpfilter = filter.get(i);
						double[] filteredUt = tmpfilter.applyFilter(ut);
						for (TimewindowData t : tw)
							cutAndWrite(station, filteredUt, t, bodyR, partialType, periodRanges[i]);
					}
				}
				if (qSpectrum != null)
					for (int k = 0; k < spectrum.nbody(); k++) {
						double bodyR = spectrum.getBodyR()[k];
						boolean exists = false;
						for (double r : Partial1DSpcMaker.this.bodyR)
							if (Utilities.equalWithinEpsilon(r, bodyR, eps))
								exists = true;
						if (!exists)
							continue;
						double[] ut = qSpectrum.getSpcBodyList().get(k).getSpcComponent(component).getTimeseries();
						// applying the filter
						
						for (int i = 0; i < periodRanges.length; i++) {
							ButterworthFilter tmpfilter = filter.get(i);
							double[] filteredUt = tmpfilter.applyFilter(ut);
							for (TimewindowData t : tw)
								cutAndWrite(station, filteredUt, t, bodyR, PartialType.PARQ, periodRanges[i]);
						}
					}
			}
		}
		
		private void addPartialSpectrum(SPCFileName spcname, SPCFileName shspcname, Set<TimewindowData> timewindowCurrentEvent) throws IOException {
			Set<TimewindowData> tmpTws = timewindowCurrentEvent.stream()
					.filter(info -> info.getObserver().getStation().equals(spcname.getStationCode())
							&& info.getObserver().getNetwork().equals(spcname.getNetworkCode()))
					.collect(Collectors.toSet());
			if (tmpTws.size() == 0) {
//				System.out.println("No timewindow found");	
				return;
			}
			
			System.out.println(spcname);
			SPCFileAccess spectrum = spcname.read();
			if (spectrum.tlen() != tlen || spectrum.np() != np) {
				System.err.println(spcname + " has different np or tlen.");
				writeLog(spcname + " has different np or tlen.");
				return;
			}
			
			System.out.println(shspcname);
			SPCFileAccess shspectrum = shspcname.read();
			if (shspectrum.tlen() != tlen || shspectrum.np() != np) {
				System.err.println(shspcname + " has different np or tlen.");
				writeLog(shspcname + " has different np or tlen.");
				return;
			}
			
			if (!SACMaker.check(spectrum, shspectrum)) {
				System.err.println("SH and PSV spc files are not a pair " + spectrum + " " + shspectrum);
				return;
			}

			String stationName = spcname.getStationCode();
			String network = spcname.getNetworkCode();
			Observer station = new Observer(stationName, spectrum.getObserverPosition(), network);
			PartialType partialType = PartialType.valueOf(spcname.getFileType().toString());
			SPCFileAccess qSpectrum = null;
			if (spcname.getFileType() == SPCType.PAR2 && partialTypes.contains(PartialType.PARQ)) {
				qSpectrum = fujiConversion.convert(spectrum);
				process(qSpectrum);
			}
			
			process(spectrum);
			process(shspectrum);

			for (SACComponent component : components) {
				Set<TimewindowData> tw = tmpTws.stream()
						.filter(info -> info.getObserver().equals(station))
						.filter(info -> info.getGlobalCMTID().equals(id))
						.filter(info -> info.getComponent().equals(component)).collect(Collectors.toSet());

				if (tw.isEmpty()) {
					tmpTws.forEach(window -> {
						System.out.println(window);
						System.out.println(window.getObserver().getPosition());
					});
					System.err.println(station.getPosition());
					System.err.println("Ignoring empty timewindow " + spcname + " " + station);
					continue;
				}

				for (int k = 0; k < spectrum.nbody(); k++) {
					double bodyR = spectrum.getBodyR()[k];
					if (shspectrum.getBodyR()[k] != bodyR)
						throw new RuntimeException("sh and psv bodyR differ " + shspectrum.getBodyR()[k] + " " + bodyR);
					boolean exists = false;
					for (double r : Partial1DSpcMaker.this.bodyR)
						if (Utilities.equalWithinEpsilon(r, bodyR, eps))
							exists = true;
					if (!exists)
						continue;
					double[] ut = spectrum.getSpcBodyList().get(k).getSpcComponent(component).getTimeseries();
					double[] shut = shspectrum.getSpcBodyList().get(k).getSpcComponent(component).getTimeseries();
					
					if (ut.length != shut.length)
						throw new RuntimeException("sh and psv timeseries do not have the same length " + shut.length + " " + ut.length);
					
					// applying the filter
					for (int i = 0; i < periodRanges.length; i++) {
						ButterworthFilter tmpfilter = filter.get(i);
						double[] filteredUt = tmpfilter.applyFilter(ut);
						double[] filteredSHUt = tmpfilter.applyFilter(shut);
						for (int it = 0; it < filteredUt.length; it++)
							filteredUt[it] += filteredSHUt[it];
						for (TimewindowData t : tw)
							cutAndWrite(station, filteredUt, t, bodyR, partialType, periodRanges[i]);
					}
				}
				
				if (qSpectrum != null) {
					System.err.println("Q sh and psv spc not implemented yet");
					for (int k = 0; k < spectrum.nbody(); k++) {
						double bodyR = spectrum.getBodyR()[k];
						boolean exists = false;
						for (double r : Partial1DSpcMaker.this.bodyR)
							if (Utilities.equalWithinEpsilon(r, bodyR, eps))
								exists = true;
						if (!exists)
							continue;
						double[] ut = qSpectrum.getSpcBodyList().get(k).getSpcComponent(component).getTimeseries();
						// applying the filter
						
						for (int i = 0; i < periodRanges.length; i++) {
							ButterworthFilter tmpfilter = filter.get(i);
							double[] filteredUt = tmpfilter.applyFilter(ut);
							for (TimewindowData t : tw)
								cutAndWrite(station, filteredUt, t, bodyR, PartialType.PARQ, periodRanges[i]);
						}
					}
				}
			}
		}

		/**
		 * @param u
		 *            partial waveform
		 * @param timewindowInformation
		 *            cut information
		 * @return u cut by considering sampling Hz
		 */
		private double[] sampleOutput(double[] u, TimewindowData timewindowInformation) {
			int cutstart = (int) (timewindowInformation.getStartTime() * partialSamplingHz);
			// 書きだすための波形
			int outnpts = (int) ((timewindowInformation.getEndTime() - timewindowInformation.getStartTime())
					* finalSamplingHz);
			double[] sampleU = new double[outnpts];
			// cutting a waveform for outputting
			Arrays.setAll(sampleU, j -> u[cutstart + j * step]);

			return sampleU;
		}
		
		private double[] cutU(double[] u, TimewindowData timewindowInformation) {
			int cutstart = (int) (timewindowInformation.getStartTime() * partialSamplingHz);
			// 書きだすための波形
			int outnpts = (int) ((timewindowInformation.getEndTime() - timewindowInformation.getStartTime())
					* partialSamplingHz);
			double[] sampleU = new double[outnpts];
			// cutting a waveform for outputting
			Arrays.setAll(sampleU, j -> u[cutstart + j]);

			return sampleU;
		}

		private EventFolder eventDir;

		private Worker(EventFolder eventDir) {
			this.eventDir = eventDir;
			id = eventDir.getGlobalCMTID();
		};

	}
	
	private class WorkerTimePartial implements Runnable {
		
		private EventFolder eventDir;
		private GlobalCMTID id;
		
		@Override
		public void run() {
			try {
				writeLog("Running on " + id);
			} catch (IOException e1) {
				e1.printStackTrace();
			}
			Path timePartialFolder = eventDir.toPath();

			if (!Files.exists(timePartialFolder)) {
				throw new RuntimeException(timePartialFolder + " does not exist...");
			}
			
			Set<SACFileName> sacnameSet;
			try {
				sacnameSet = eventDir.sacFileSet()
						.stream()
						.filter(sacname -> sacname.isTemporalPartial())
						.collect(Collectors.toSet());
			} catch (IOException e1) {
				e1.printStackTrace();
				return;
			}
			
//			System.out.println(sacnameSet.size());
//			sacnameSet.forEach(name -> System.out.println(name));
			
			Set<TimewindowData> timewindowCurrentEvent = timewindowInformationSet
					.stream()
					.filter(tw -> tw.getGlobalCMTID().equals(id))
					.collect(Collectors.toSet());
			
			// すべてのsacファイルに対しての処理
			for (SACFileName sacname : sacnameSet) {
				try {
					addTemporalPartial(sacname, timewindowCurrentEvent);
				} catch (ClassCastException e) {
					// 出来上がったインスタンスがOneDPartialSpectrumじゃない可能性
					System.err.println(sacname + "is not 1D partial.");
					continue;
				} catch (Exception e) {
					System.err.println(sacname + " is invalid.");
					e.printStackTrace();
					try {
						writeLog(sacname + " is invalid.");
					} catch (IOException e1) {
						e1.printStackTrace();
					}
					continue;
				}
			}
			System.out.print(".");
//			
		}
		
		private void addTemporalPartial(SACFileName sacname, Set<TimewindowData> timewindowCurrentEvent) throws IOException {
			Set<TimewindowData> tmpTws = timewindowCurrentEvent.stream()
					.filter(info -> info.getObserver().getStation().equals(sacname.getStationCode()))
					.collect(Collectors.toSet());
			if (tmpTws.size() == 0) {
				return;
			}
			
			System.out.println(sacname + " (time partials)");
			
			SACFileAccess sacdata = sacname.read();
			Observer station = sacdata.getObserver();
			
			for (SACComponent component : components) {
				Set<TimewindowData> tw = tmpTws.stream()
						.filter(info -> info.getObserver().equals(station))
						.filter(info -> info.getGlobalCMTID().equals(id))
						.filter(info -> info.getComponent().equals(component)).collect(Collectors.toSet());

				if (tw.isEmpty()) {
					tmpTws.forEach(window -> {
						System.out.println(window);
						System.out.println(window.getObserver().getPosition());
					});
					System.err.println(station.getPosition());
					System.err.println("Ignoring empty timewindow " + sacname + " " + station);
					continue;
				}
				
				for (int i = 0; i < periodRanges.length; i++) {
					ButterworthFilter tmpfilter = filter.get(i); // TO DO
					double[] filteredUt = sacdata.createTrace().getY();
					for (TimewindowData t : tw)
						cutAndWrite(station, filteredUt, t, periodRanges[i]);
				}
			}
		}
		
		/**
		 * @param u
		 *            partial waveform
		 * @param timewindowInformation
		 *            cut information
		 * @return u cut by considering sampling Hz
		 */
		private double[] sampleOutput(double[] u, TimewindowData timewindowInformation) {
			int cutstart = (int) (timewindowInformation.getStartTime() * partialSamplingHz);
			// 書きだすための波形
			int outnpts = (int) ((timewindowInformation.getEndTime() - timewindowInformation.getStartTime())
					* finalSamplingHz);
			double[] sampleU = new double[outnpts];
			// cutting a waveform for outputting
			Arrays.setAll(sampleU, j -> u[cutstart + j * step]);

			return sampleU;
		}
		
		private void cutAndWrite(Observer station, double[] filteredUt, TimewindowData t, double[] periodRange) {

			double[] cutU = sampleOutput(filteredUt, t);
			FullPosition stationLocation = new FullPosition(station.getPosition().getLatitude(), station.getPosition().getLongitude(), Earth.EARTH_RADIUS);
			
			if (sourceTimeFunction == -1)
				System.err.println("Warning: check that the source time function used for the time partial is the same as the one used here.");
			
			PartialID PIDReceiverSide = new PartialID(station, id, t.getComponent(), finalSamplingHz, t.getStartTime(), cutU.length,
					periodRange[0], periodRange[1], t.getPhases(), 0, true, stationLocation, PartialType.TIME_RECEIVER,
					cutU);
			PartialID PIDSourceSide = new PartialID(station, id, t.getComponent(), finalSamplingHz, t.getStartTime(), cutU.length,
					periodRange[0], periodRange[1], t.getPhases(), 0, true, id.getEvent().getCmtLocation(), PartialType.TIME_SOURCE,
					cutU);
			
			try {
				partialDataWriter.addPartialID(PIDReceiverSide);
				add();
				partialDataWriter.addPartialID(PIDSourceSide);
				add();
			} catch (Exception e) {
				e.printStackTrace();
			}
			
		}
		
		private WorkerTimePartial(EventFolder eventDir) {
			this.eventDir = eventDir;
			id = eventDir.getGlobalCMTID();
		};
	}

	public Partial1DSpcMaker(Properties property) throws IOException {
		this.property = (Properties) property.clone();
		set();
	}

	private Properties property;

	/**
	 * filter いじらなくていい
	 */
	private List<ButterworthFilter> filter;

	/**
	 * sacdataを何ポイントおきに取り出すか
	 */
	private int step;

	/**
	 * タイムウインドウの情報
	 */
	private Set<TimewindowData> timewindowInformationSet;

	//
	private WaveformDataWriter partialDataWriter;

	private Path logPath;

	private FujiConversion fujiConversion;
	
	private VSConversion vsConversion;
	
	private Map<GlobalCMTID, SourceTimeFunction> userSourceTimeFunctions;

	private void readSourceTimeFunctions() throws IOException {
		Set<GlobalCMTID> ids = timewindowInformationSet.stream().map(t -> t.getGlobalCMTID())
				.collect(Collectors.toSet());
		userSourceTimeFunctions = ids.stream().collect(Collectors.toMap(id -> id, id -> {
			try {
				Path sourceTimeFunctionPath = this.sourceTimeFunctionPath.resolve(id + ".stf");
				return SourceTimeFunction.readSourceTimeFunction(sourceTimeFunctionPath);
			} catch (Exception e) {
				throw new RuntimeException("Source time function file for " + id + " is broken.");
			}
		}));

	}

	/**
	 * @param args
	 *            [parameter file name]
	 */
	public static void main(String[] args) throws IOException {
		Partial1DSpcMaker pdm = new Partial1DSpcMaker(Property.parse(args));

		if (!Files.exists(pdm.timewindowPath))
			throw new NoSuchFileException(pdm.timewindowPath.toString());

		pdm.run();
	}
	
	private final List<String> stfcat = readSTFCatalogue("astf_cc_ampratio_ca.catalog");
	
	private List<String> readSTFCatalogue(String STFcatalogue) throws IOException {
		System.out.println("STF catalogue: " +  STFcatalogue);
		return IOUtils.readLines(Partial1DSpcMaker.class.getClassLoader().getResourceAsStream(STFcatalogue)
					, Charset.defaultCharset());
	}

	private Set<GlobalCMTID> idSet;
	private Set<Observer> stationSet;
	private Set<FullPosition> perturbationLocationSet;
	private Phase[] phases;

	private void setPerturbationLocation() {
		perturbationLocationSet = Arrays.stream(bodyR).mapToObj(r -> new FullPosition(0, 0, r)).collect(Collectors.toSet());
		if (timePartialPath != null) {
			if (stationSet.isEmpty() || idSet.isEmpty())
				throw new RuntimeException("stationSet and idSet must be set before perturbationLocation");
			stationSet.forEach(station -> perturbationLocationSet.add(new FullPosition(station.getPosition().getLatitude(),
					station.getPosition().getLongitude(), Earth.EARTH_RADIUS)));
			idSet.forEach(id -> perturbationLocationSet.add(id.getEvent().getCmtLocation()));
		}
	}
	
	private BasicID[] reFyIDs;
	
	private BasicID[] imFyIDs;
	
	@Override
	public void run() throws IOException {
		String dateString = Utilities.getTemporaryString();
		
		reFyIDs = Arrays.stream(BasicIDFile.read(reFyIDPath, reFyPath)).filter(id -> id.getWaveformType().equals(WaveformType.SYN))
				.collect(Collectors.toList()).toArray(new BasicID[0]);
		imFyIDs = Arrays.stream(BasicIDFile.read(imFyIDPath, imFyPath)).filter(id -> id.getWaveformType().equals(WaveformType.SYN))
				.collect(Collectors.toList()).toArray(new BasicID[0]);

		logPath = workPath.resolve("partial1D" + dateString + ".log");

		System.err.println(Partial1DSpcMaker.class.getName() + " is going.");
		long startTime = System.nanoTime();

		// pdm.createStreams();
		int N_THREADS = Runtime.getRuntime().availableProcessors();
		// N_THREADS = 2;
		writeLog("going with " + N_THREADS + " threads");
		
		PolynomialStructure structure = null;
		switch (structurePath) {
		case "PREM":
		case "prem":
			structure = PolynomialStructure.PREM;
			break;
		case "AK135":
		case "ak135":
			structure = PolynomialStructure.AK135;
			break;
		default:
			try {
				structure = new PolynomialStructure(Paths.get(structurePath));
			} catch (IOException e) {
				e.printStackTrace();
			}
			break;
		}
		
		if (partialTypes.contains(PartialType.PARQ))
			fujiConversion = new FujiConversion(structure);
		
		if (partialTypes.contains(PartialType.PARVS))
			vsConversion = new VSConversion(structure);
		
		System.err.println("going with the structure " + structurePath);
		writeLog("going with the structure " + structurePath);
		
		setLsmooth();
		writeLog("Set lsmooth " + lsmooth);

		// タイムウインドウの情報を読み取る。
		System.err.print("Reading timewindow information ");
		timewindowInformationSet = TimewindowDataFile.read(timewindowPath);
		System.err.println("done");
		
		//debug
//		timewindowInformationSet = timewindowInformationSet.parallelStream().filter(t -> t.getGlobalCMTID().equals(new GlobalCMTID("200609220232A"))
//				&& t.getStation().getStationName().contentEquals("ISCO")).collect(Collectors.toSet());

		if (sourceTimeFunction == -1)
			readSourceTimeFunctions();

		// filter設計
		System.err.println("Designing filter.");
		setBandPassFilter();
//		writeLog(filter.toString());
		stationSet = timewindowInformationSet.parallelStream().map(TimewindowData::getObserver)
				.collect(Collectors.toSet());
		idSet = Utilities.globalCMTIDSet(workPath);
		setPerturbationLocation();
		phases = timewindowInformationSet.parallelStream().map(TimewindowData::getPhases).flatMap(p -> Stream.of(p))
				.distinct().toArray(Phase[]::new);
		// information about output partial types
		writeLog(partialTypes.stream().map(Object::toString).collect(Collectors.joining(" ", "Computing for ", "")));

		// sacdataを何ポイントおきに取り出すか
		step = (int) (partialSamplingHz / finalSamplingHz);

		Set<EventFolder> eventDirs = Utilities.eventFolderSet(workPath);
		Set<EventFolder> timePartialEventDirs = new HashSet<>();
		if (timePartialPath != null)
			timePartialEventDirs = Utilities.eventFolderSet(timePartialPath);

		// create ThreadPool
		ExecutorService execs = Executors.newFixedThreadPool(N_THREADS);

		Path idPath = workPath.resolve("partial1DID" + dateString + ".dat");
		Path datasetPath = workPath.resolve("partial1D" + dateString + ".dat");
		try (WaveformDataWriter pdw = new WaveformDataWriter(idPath, datasetPath, stationSet, idSet, periodRanges,
				phases, perturbationLocationSet)) {

			partialDataWriter = pdw;
			for (EventFolder eventDir : eventDirs)
				execs.execute(new Worker(eventDir));
			// break;
			for (EventFolder eventDir2 : timePartialEventDirs)
				execs.execute(new WorkerTimePartial(eventDir2));
			execs.shutdown();

			while (!execs.isTerminated())
				Thread.sleep(100);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		// computing PAR00
		if (par00) {
			PartialID[] partials = PartialIDFile.read(idPath, datasetPath);
			List<PartialID> par0list = Stream.of(partials).filter(par -> par.getPartialType().equals(PartialType.PAR0)).collect(Collectors.toList());
			List<PartialID> par1list = Stream.of(partials).filter(par -> par.getPartialType().equals(PartialType.PAR1)).collect(Collectors.toList());
			List<PartialID> par2list = Stream.of(partials).filter(par -> par.getPartialType().equals(PartialType.PAR2)).collect(Collectors.toList());
			List<PartialID> parQlist = Stream.of(partials).filter(par -> par.getPartialType().equals(PartialType.PARQ)).collect(Collectors.toList());
			
			Path idPath00 = workPath.resolve("partial001DID" + dateString + ".dat");
			Path datasetPath00 = workPath.resolve("partial001D" + dateString + ".dat");
			try (WaveformDataWriter pdw = new WaveformDataWriter(idPath00, datasetPath00, stationSet, idSet, periodRanges,
					phases, perturbationLocationSet)) {
				for (PartialID par0 : par0list) {
					Phases phases = new Phases(par0.getPhases());
					
					PartialID par2  = null;
					PartialID parQ = null;
					
					try {
						par2 = par2list.parallelStream().filter(par -> par.getGlobalCMTID().equals(par0.getGlobalCMTID())
							&& par.getObserver().equals(par0.getObserver())
							&& par.getPerturbationLocation().equals(par0.getPerturbationLocation())
							&& par.getMinPeriod() == par0.getMinPeriod()
							&& par.getMaxPeriod() == par0.getMaxPeriod()
							&& new Phases(par.getPhases()).equals(phases)
							&& par.getSacComponent().equals(par0.getSacComponent()))
							.findFirst().get();
						parQ = parQlist.parallelStream().filter(par -> par.getGlobalCMTID().equals(par0.getGlobalCMTID())
								&& par.getObserver().equals(par0.getObserver())
								&& par.getPerturbationLocation().equals(par0.getPerturbationLocation())
								&& par.getMinPeriod() == par0.getMinPeriod()
								&& par.getMaxPeriod() == par0.getMaxPeriod()
								&& new Phases(par.getPhases()).equals(phases)
								&& par.getSacComponent().equals(par0.getSacComponent()))
								.findFirst().get();
					} catch (Exception e) {
						System.err.println(par0);
						continue;
//								e.printStackTrace();
					}
					
					double[] data = par0.getData();
					double[] data1 = new double[data.length];
					double[] data2 = par2.getData();
					double[] dataQ = parQ.getData();
					
					if (!par0.getSacComponent().equals(SACComponent.T)) {
						PartialID par1 = par1list.parallelStream().filter(par -> par.getGlobalCMTID().equals(par0.getGlobalCMTID())
								&& par.getObserver().equals(par0.getObserver())
								&& par.getPerturbationLocation().equals(par0.getPerturbationLocation())
								&& par.getMinPeriod() == par0.getMinPeriod()
								&& par.getMaxPeriod() == par0.getMaxPeriod()
								&& new Phases(par.getPhases()).equals(phases)
								&& par.getSacComponent().equals(par0.getSacComponent()))
								.findFirst().get();
						data1 = par1.getData();
					}
					
					double r = par0.getPerturbationLocation().getR();
					
					double mu = structure.computeMu(r);
					double M = structure.computeLambda(r) + 2 * mu;
					double rho = structure.getRhoAt(r);
					
					double[] dataG = new double[data.length];
					double[] dataM = data1;
					double[] dataRho = new double[data.length];
					double[] dataVp = new double[data.length];
					double[] dataVs = new double[data.length];
					
					for (int i = 0; i < data.length; i++) {
						dataM[i] = M * data1[i];
						dataG[i] = mu * (data2[i] - 2 * data1[i]);
//								dataVs[i] = 2 * (dataG[i] - rho * data[i]);
//								dataVp[i] = 2 * (dataM[i] - rho * data[i]);
						dataVs[i] = 2 * dataG[i];
						dataVp[i] = 2 * dataM[i];
						dataRho[i] = dataG[i] + dataM[i] + rho * data[i];
					}
					
					PartialID partialRho = new PartialID(par0.getObserver(), par0.getGlobalCMTID(), par0.getSacComponent()
							, par0.getSamplingHz(), par0.getStartTime(), par0.getNpts(), par0.getMinPeriod(), par0.getMaxPeriod()
							, par0.getPhases(), par0.getStartByte(), par0.isConvolute(), par0.getPerturbationLocation(), PartialType.PAR00, dataRho);
					
					PartialID partialVp = new PartialID(par0.getObserver(), par0.getGlobalCMTID(), par0.getSacComponent()
							, par0.getSamplingHz(), par0.getStartTime(), par0.getNpts(), par0.getMinPeriod(), par0.getMaxPeriod()
							, par0.getPhases(), par0.getStartByte(), par0.isConvolute(), par0.getPerturbationLocation(), PartialType.PARVP, dataVp);
					
					PartialID partialVs = new PartialID(par0.getObserver(), par0.getGlobalCMTID(), par0.getSacComponent()
							, par0.getSamplingHz(), par0.getStartTime(), par0.getNpts(), par0.getMinPeriod(), par0.getMaxPeriod()
							, par0.getPhases(), par0.getStartByte(), par0.isConvolute(), par0.getPerturbationLocation(), PartialType.PARVS, dataVs);
					
					PartialID partialM = new PartialID(par0.getObserver(), par0.getGlobalCMTID(), par0.getSacComponent()
							, par0.getSamplingHz(), par0.getStartTime(), par0.getNpts(), par0.getMinPeriod(), par0.getMaxPeriod()
							, par0.getPhases(), par0.getStartByte(), par0.isConvolute(), par0.getPerturbationLocation(), PartialType.PARM, dataM);
					
					PartialID partialG = new PartialID(par0.getObserver(), par0.getGlobalCMTID(), par0.getSacComponent()
							, par0.getSamplingHz(), par0.getStartTime(), par0.getNpts(), par0.getMinPeriod(), par0.getMaxPeriod()
							, par0.getPhases(), par0.getStartByte(), par0.isConvolute(), par0.getPerturbationLocation(), PartialType.PARG, dataG);
					
					PartialID partialQ = new PartialID(par0.getObserver(), par0.getGlobalCMTID(), par0.getSacComponent()
							, par0.getSamplingHz(), par0.getStartTime(), par0.getNpts(), par0.getMinPeriod(), par0.getMaxPeriod()
							, par0.getPhases(), par0.getStartByte(), par0.isConvolute(), par0.getPerturbationLocation(), PartialType.PARQ, dataQ);
					
//					pdw.addPartialID(partialM);
//					pdw.addPartialID(partialG);
//					pdw.addPartialID(partialRho);
					pdw.addPartialID(partialVs);
					pdw.addPartialID(partialVp);
					pdw.addPartialID(partialQ);
				}
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		
		System.err.println();
		String endLine = Partial1DSpcMaker.class.getName() + " finished in "
				+ Utilities.toTimeString(System.nanoTime() - startTime);
		System.err.println(endLine);
		writeLog(endLine);
		writeLog(idPath + " " + datasetPath + " were created");
		writeLog(numberOfAddedID + " IDs are added.");
	}

	private double[][] periodRanges;

	private void setBandPassFilter() throws IOException {
		for (double[] periods : periodRanges) {
			double omegaH = 1. / periods[0] * 2 * Math.PI / partialSamplingHz;
			double omegaL = 1. / periods[1] * 2 * Math.PI / partialSamplingHz;
			ButterworthFilter tmpfilter = new BandPassFilter(omegaH, omegaL, filterNp);
			tmpfilter.setBackward(backward);
			filter.add(tmpfilter);
			writeLog(tmpfilter.toString());
		}
	}

	private void writeLog(String line) throws IOException {
		Date now = new Date();
		synchronized (this) {
			try (PrintWriter pw = new PrintWriter(
					Files.newBufferedWriter(logPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND))) {
				pw.println(now + " : " + line);
			}
		}
	}

	@Override
	public Path getWorkPath() {
		return workPath;
	}

	@Override
	public Properties getProperties() {
		return (Properties) property.clone();
	}
	
	private Set<SPCFileName> collectSHSPCs(Path spcFolder) throws IOException {
		Set<SPCFileName> shSet = new HashSet<>();
		Utilities.collectSpcFileName(spcFolder).stream()
				.filter(f -> f.getName().contains("PAR") && f.getName().endsWith("SH.spc")).forEach(shSet::add);
		return shSet;
	}

	private Set<SPCFileName> collectPSVSPCs(Path spcFolder) throws IOException {
		Set<SPCFileName> psvSet = new HashSet<>();
		Utilities.collectSpcFileName(spcFolder).stream()
				.filter(f -> f.getName().contains("PAR") && f.getName().endsWith("PSV.spc")).forEach(psvSet::add);
		return psvSet;
	}
	
	private final double eps = 1e-6;
}
