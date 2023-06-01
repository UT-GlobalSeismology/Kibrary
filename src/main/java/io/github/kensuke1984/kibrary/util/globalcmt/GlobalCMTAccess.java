package io.github.kensuke1984.kibrary.util.globalcmt;

import java.time.LocalDateTime;

import io.github.kensuke1984.kibrary.source.MomentTensor;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionType;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
 * Data for global CMT data used frequently.
 *
 * @author Kensuke Konishi
 * @since version 0.0.1.0.1
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
