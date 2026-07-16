package io.github.kensuke1984.kibrary.entrance;

import java.util.ArrayList;
import java.util.List;

/**
 * List of URLs of each data center.
 *
 * @since 2026/6/12
 * @author otsuru
 */
enum DataCenterEnum {

    EarthScope("https://service.earthscope.org/fdsnws/dataselect/1/query?", "https://service.earthscope.org/fdsnws/station/1/query?"),
    ORFEUS_FED("https://federator.orfeus-eu.org/fdsnws/dataselect/1/query?", "https://federator.orfeus-eu.org/fdsnws/station/1/query?"),
    ODC("https://www.orfeus-eu.org/fdsnws/dataselect/1/query?", "https://www.orfeus-eu.org/fdsnws/station/1/query?"),
    GFZ("https://geofon.gfz.de/fdsnws/dataselect/1/query?", "https://geofon.gfz.de/fdsnws/station/1/query?"),
    EPOSFR("https://seisdata.epos-france.fr/fdsnws/dataselect/1/query?", "https://seisdata.epos-france.fr/fdsnws/station/1/query?"),
    INGV("https://webservices.ingv.it/fdsnws/dataselect/1/query?", "https://webservices.ingv.it/fdsnws/station/1/query?"),
    ETHZ("https://eida.ethz.ch/fdsnws/dataselect/1/query?", "https://eida.ethz.ch/fdsnws/station/1/query?"),
    BGR("https://eida.bgr.de/fdsnws/dataselect/1/query?", "https://eida.bgr.de/fdsnws/station/1/query?"),
    NIEP("https://eida-sc3.infp.ro/fdsnws/dataselect/1/query?", "https://eida-sc3.infp.ro/fdsnws/station/1/query?"),
    KOERI("https://eida.koeri.boun.edu.tr/fdsnws/dataselect/1/query?", "https://eida.koeri.boun.edu.tr/fdsnws/station/1/query?"),
    LMU("https://erde.geophysik.uni-muenchen.de/fdsnws/dataselect/1/query?", "https://erde.geophysik.uni-muenchen.de/fdsnws/station/1/query?"),
    NOA("https://eida.gein.noa.gr/fdsnws/dataselect/1/query?", "https://eida.gein.noa.gr/fdsnws/station/1/query?"),
    UIB("https://eida.geo.uib.no/fdsnws/dataselect/1/query?", "https://eida.geo.uib.no/fdsnws/station/1/query?"),
    ICGC("https://ws.icgc.cat/fdsnws/dataselect/1/query?", "https://ws.icgc.cat/fdsnws/station/1/query?"),
    BGS("https://eida.bgs.ac.uk/fdsnws/dataselect/1/query?", "https://eida.bgs.ac.uk/fdsnws/station/1/query?"),
    ;

    private final String dataSelectUrl;
    private final String stationUrl;

    private DataCenterEnum(String dataSelectUrl, String stationUrl) {
        this.dataSelectUrl = dataSelectUrl;
        this.stationUrl = stationUrl;
    }

    static List<DataCenterEnum> listForMseed(String dataCenter) {
        List<DataCenterEnum> dataCenterList = new ArrayList<>();

        switch (dataCenter) {
        case "EarthScope":
        case "IRIS":
            dataCenterList.add(EarthScope);
            break;
        case "ORFEUS":
        case "ORFEUS_EACH":
            dataCenterList.add(ODC);
            dataCenterList.add(GFZ);
            dataCenterList.add(EPOSFR);
            dataCenterList.add(INGV);
            dataCenterList.add(ETHZ);
            dataCenterList.add(BGR);
            dataCenterList.add(NIEP);
            dataCenterList.add(KOERI);
            dataCenterList.add(LMU);
            dataCenterList.add(NOA);
            dataCenterList.add(UIB);
//            dataCenterList.add(ICGC);  TODO: SSLHandshakeException caused by SunCertPathBuilderException "unable to find valid certification path" occurs
            dataCenterList.add(BGS);
            break;
        case "ORFEUS_FED":
            dataCenterList.add(ORFEUS_FED);
            break;
        default:
            throw new IllegalArgumentException("Invalid data center name.");
        }
        return dataCenterList;
    }

    static DataCenterEnum forStationXML(String dataCenter) {
        switch (dataCenter) {
        case "EarthScope":
        case "IRIS":
            return EarthScope;
        case "ORFEUS":
        case "ORFEUS_FED":
            return ORFEUS_FED;
        case "ORFEUS_EACH":
        default:
            throw new IllegalArgumentException("Invalid data center name.");
        }
    }

    String getDataSelectUrl() {
        return dataSelectUrl;
    }

    String getStationUrl() {
        return stationUrl;
    }

}
