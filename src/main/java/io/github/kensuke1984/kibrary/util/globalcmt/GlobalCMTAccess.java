package io.github.kensuke1984.kibrary.util.globalcmt;

import java.time.LocalDateTime;
import io.github.kensuke1984.kibrary.source.MomentTensor;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionType;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
 * Data for global CMT data used frequently.
 *
 * @since before 2016/1/25
 * @author Kensuke Konishi
 *
 * @version 2021/11/2 Renamed from GlobalCMTData to GlobalCMTAccess.
 * @author otsuru
 */
public interface GlobalCMTAccess {

    GlobalCMTID getGlobalCMTID();

    double getMb();

    double getMs();

    MomentTensor getCmt();
    GlobalCMTAccess withCMT(MomentTensor mt);

    FullPosition getCmtPosition();

    LocalDateTime getCMTTime();

    FullPosition getPDEPosition();

    LocalDateTime getPDETime();

    double getTimeDifference();

    SourceTimeFunctionType getSTFType();

    double getHalfDuration();

    String getHypocenterReferenceCatalog();

    String getGeographicalLocationName();

}
