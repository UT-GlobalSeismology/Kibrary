package io.github.kensuke1984.kibrary.waveform.addons;

import io.github.kensuke1984.kibrary.correction.StaticCorrectionType;
import io.github.kensuke1984.kibrary.inversion.Physical3DParameter;
import io.github.kensuke1984.kibrary.inversion.UnknownParameter;
import io.github.kensuke1984.kibrary.inversion.addons.WeightingType;
import io.github.kensuke1984.kibrary.util.addons.FrequencyRange;
import io.github.kensuke1984.kibrary.util.addons.Phases;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.spc.PartialType;

import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;

import org.apache.http.params.CoreConnectionPNames;

public class AtdHeader {
	WeightingType[] weightingTypes;
	FrequencyRange[] frequencyRanges;
	Phases[] phases;
	StaticCorrectionType[] correctionTypes;
	PartialType[] partialTypes;
	FullPosition[] locations;
	
	public static final int oneWeightingTypeByte = 4;
	public static final int onePhasesByte = 20;
	public static final int onePartialTypeByte = 10;
	public static final int oneCorrectionTypeByte = 10;
	
	public AtdHeader(WeightingType[] weightingTypes, FrequencyRange[] frequencyRanges
			, Phases[] phases, StaticCorrectionType[] correctionTypes, PartialType[] partialTypes, FullPosition[] locations) {
		this.weightingTypes = weightingTypes;
		this.frequencyRanges = frequencyRanges;
		this.phases = phases;
		this.correctionTypes = correctionTypes;
		this.partialTypes = partialTypes;
		this.locations = locations;
	}
	
	public static AtdHeader readHeader(Path ataPath) throws IOException {
		WeightingType[] weightingTypes;
		FrequencyRange[] frequencyRanges;
		Phases[] phases;
		StaticCorrectionType[] correctionTypes;
		PartialType[] partialTypes;
		FullPosition[] locations;
		
		try (DataInputStream dis = new DataInputStream(new BufferedInputStream(Files.newInputStream(ataPath)));) {
			// Read header
			weightingTypes = new WeightingType[dis.readShort()];
			frequencyRanges = new FrequencyRange[dis.readShort()];
			phases = new Phases[dis.readShort()];
			correctionTypes = new StaticCorrectionType[dis.readShort()];
			partialTypes = new PartialType[dis.readShort()];
			locations = new FullPosition[dis.readShort()];
			
			for (int i = 0; i < weightingTypes.length; i++) {
				int n = dis.readInt();
				weightingTypes[i] = WeightingType.getType(n);
			}
			byte[] frequencyRangeByte = new byte[16];
			for (int i = 0; i < frequencyRanges.length; i++) {
				dis.read(frequencyRangeByte);
				ByteBuffer bb = ByteBuffer.wrap(frequencyRangeByte);
				frequencyRanges[i] = new FrequencyRange(bb.getDouble(), bb.getDouble());
			}
			byte[] phasesByte = new byte[onePhasesByte];
			for (int i = 0; i < phases.length; i++) {
				dis.read(phasesByte);
				phases[i] = new Phases(new String(phasesByte).trim());
			}
			byte[] correctionByte = new byte[oneCorrectionTypeByte];
			for (int i = 0; i < correctionTypes.length; i++) {
				dis.read(correctionByte);
				correctionTypes[i] = StaticCorrectionType.valueOf(new String(correctionByte).trim());
			}
			byte[] partialTypeByte = new byte[onePartialTypeByte];
			for (int i = 0; i < partialTypes.length; i++) {
				dis.read(partialTypeByte);
				partialTypes[i] = PartialType.valueOf(new String(partialTypeByte).trim());
			}
			byte[] locationByte = new byte[12];
			for (int i = 0; i < locations.length; i++) {
				dis.read(locationByte);
				ByteBuffer bb = ByteBuffer.wrap(locationByte);
				locations[i] = new FullPosition(bb.getFloat(), bb.getFloat(), bb.getFloat());
			}
		}
		
		return new AtdHeader(weightingTypes, frequencyRanges, phases, correctionTypes, partialTypes, locations);
	}
	
	public WeightingType[] getWeightingTypes() {
		return weightingTypes;
	}
	
	public FrequencyRange[] getFrequencyRanges() {
		return frequencyRanges;
	}
	
	public Phases[] getPhases() {
		return phases;
	}
	
	public StaticCorrectionType[] getCorrectionTypes() {
		return correctionTypes;
	}
	
	public PartialType[] getPartialTypes() {
		return partialTypes;
	}
	
	public FullPosition[] getLocations() {
		return locations;
	}
}
