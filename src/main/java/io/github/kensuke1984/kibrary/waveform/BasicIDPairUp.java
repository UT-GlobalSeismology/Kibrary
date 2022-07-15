package io.github.kensuke1984.kibrary.waveform;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.util.sac.WaveformType;

/**
 * Pairs up observed and synthetic BasicIDs included inside an array with random order.
 *
 * @author otsuru
 * @since 2022/7/5 extracted BasicIDFile.pairUp() into a class
 */
public class BasicIDPairUp {

    /**
     * unmodifiable List of paired up observed IDs
     */
    private final List<BasicID> obsList;
    /**
     * unmodifiable List of paired up synthetic IDs
     */
    private final List<BasicID> synList;

    public BasicIDPairUp(BasicID[] basicIDs) {

        // extract observed IDs
        List<BasicID> tempObsList = Arrays.stream(basicIDs).filter(id -> id.getWaveformType() == WaveformType.OBS)
                .collect(Collectors.toList());
        // check for duplication
        for (int i = 0; i < tempObsList.size(); i++)
            for (int j = i + 1; j < tempObsList.size(); j++)
                if (tempObsList.get(i).equals(tempObsList.get(j)))
                    throw new RuntimeException("Duplicate observed IDs detected");

        // extract synthetic IDs
        List<BasicID> tempSynList = Arrays.stream(basicIDs).filter(id -> id.getWaveformType() == WaveformType.SYN)
                .collect(Collectors.toList());
        // check for duplication
        for (int i = 0; i < tempSynList.size() - 1; i++)
            for (int j = i + 1; j < tempSynList.size(); j++)
                if (tempSynList.get(i).equals(tempSynList.get(j)))
                    throw new RuntimeException("Duplicate synthetic IDs detected");

        System.err.println("Number of obs IDs before pairing with syn IDs: " + tempObsList.size());
        if (tempObsList.size() != tempSynList.size())
            System.err.println("The numbers of observed IDs " + tempObsList.size() + " and " + " synthetic IDs "
                    + tempSynList.size() + " are different ");

        // pair up each syn with obs
        List<BasicID> resultObsList = new ArrayList<>();
        List<BasicID> resultSynList = new ArrayList<>();
        for (int i = 0; i < tempSynList.size(); i++) {
            boolean foundPair = false;
            for (int j = 0; j < tempObsList.size(); j++) {
                if (BasicID.isPair(tempSynList.get(i), tempObsList.get(j))) {
                    resultObsList.add(tempObsList.get(j));
                    resultSynList.add(tempSynList.get(i));
                    foundPair = true;
                    break;
                }
            }
            if (!foundPair) {
                System.err.println("Didn't find OBS for " + tempSynList.get(i));
            }
        }

        if (resultObsList.size() != resultSynList.size())
            throw new RuntimeException("unanticipated");
        System.err.println("Number of pairs created: " + resultObsList.size());

        obsList = Collections.unmodifiableList(resultObsList);
        synList = Collections.unmodifiableList(resultSynList);
    }

    /**
     * @return unmodifiable List of paired up observed IDs
     */
    public List<BasicID> getObsList() {
        return obsList;
    }

    /**
     * @return unmodifiable List of paired up synthetic IDs
     */
    public List<BasicID> getSynList() {
        return synList;
    }


}
