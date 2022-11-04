package io.github.kensuke1984.kibrary.util.globalcmt;

import java.time.LocalDateTime;

import io.github.kensuke1984.kibrary.source.MomentTensor;
import io.github.kensuke1984.kibrary.source.SourceTimeFunctionType;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;

/**
 * Data for global CMT data used frequently.
 *
 * @author Kensuke Konishi
 * @version 0.0.1.0.1
 */
public interface GlobalCMTAccess {

    double getMb();

    double getMs();

    MomentTensor getCmt();

    FullPosition getCmtPosition();

    LocalDateTime getCMTTime();

    SourceTimeFunctionType getSTFType();

    double getHalfDuration();

    FullPosition getPDEPosition();

    LocalDateTime getPDETime();

    GlobalCMTID getGlobalCMTID();

    void setCMT(MomentTensor mt);

    double getTimeDifference();

    String getHypocenterReferenceCatalog();

    String getGeographicalLocationName();

}
