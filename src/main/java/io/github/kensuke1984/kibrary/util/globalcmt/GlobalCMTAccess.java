package io.github.kensuke1984.kibrary.util.globalcmt;

import io.github.kensuke1984.kibrary.correction.MomentTensor;
import io.github.kensuke1984.kibrary.util.FullPosition;

import java.time.LocalDateTime;

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

    FullPosition getCmtLocation();

    LocalDateTime getCMTTime();

    double getHalfDuration();

    FullPosition getPDELocation();

    LocalDateTime getPDETime();

    GlobalCMTID getGlobalCMTID();
    
    void setCMT(MomentTensor mt);
    
    double getTimeDifference();

	String getHypocenterReferenceCatalog();
	
	String getGeographicalLocationName();
    
}
