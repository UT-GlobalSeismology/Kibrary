package io.github.kensuke1984.kibrary.util.globalcmt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import io.github.kensuke1984.kibrary.math.CircularRange;
import io.github.kensuke1984.kibrary.math.LinearRange;
/**
 * Query for search of Global CMT
 *
 * @author Kensuke Konishi
 * @version 0.1.12
 * TODO thread safe (immutable)
 */
public class GlobalCMTSearch {

    /**
     * Added predicate set.
     */
    private Set<Predicate<GlobalCMTAccess>> predicateSet = new HashSet<>();
    /**
     * Start date and time for CMT.
     */
    private LocalDateTime startDate;
    /**
     * End date and time for CMT.
     */
    private LocalDateTime endDate;

    /**
     * Depth range [km].
     */
    private LinearRange depthRange = new LinearRange("Depth", 0, 1000);
    /**
     * Latitude range [deg].
     */
    private LinearRange latitudeRange = new LinearRange("Latitude", -90.0, 90.0);
    /**
     * Longitude range [deg].
     */
    private CircularRange longitudeRange = new CircularRange("Longitude", -180.0, 180.0);
    /**
     * Body wave magnitude range.
     */
    private LinearRange mbRange = new LinearRange("Mb", 0, 10);
    /**
     * Surface wave magnitude range.
     */
    private LinearRange msRange = new LinearRange("Ms", 0, 10);
    /**
     * Moment magnitude range.
     */
    private LinearRange mwRange = new LinearRange("Mw", 0, 10);
    /**
     * Centroid time shift range [s].
     */
    private LinearRange centroidTimeShiftRange = new LinearRange("Centroid time shift", -9999, 9999);
    /**
     * Half duration range [s].
     */
    private LinearRange halfDurationRange = new LinearRange("Half duration", 0, 20);
    /**
     * Tension axis plunge range [deg].
     */
    private LinearRange tensionAxisPlungeRange = new LinearRange("Tension axis plunge", 0, 90);
    /**
     * Null axis plunge range [deg].
     */
    private LinearRange nullAxisPlungeRange = new LinearRange("Null axis plunge", 0, 90);

    /**
     * Search on 1 day.
     *
     * @param startDate on which this searches
     */
    public GlobalCMTSearch(LocalDate startDate) {
        this(startDate, startDate);
    }

    /**
     * Search within a date range.
     *
     * @param startDate (LocalDate) Starting date of the search, inclusive.
     * @param endDate (LocalDate) Ending date of the search, INCLUSIVE.
     */
    public GlobalCMTSearch(LocalDate startDate, LocalDate endDate) {
        this.startDate = startDate.atTime(0, 0);
        this.endDate = endDate.plusDays(1).atTime(0, 0);
    }

    /**
     * Search within a date-and-time range.
     *
     * @param startDate (LocalDateTime) Starting date and time of the search, inclusive.
     * @param endDate (LocalDateTime) Ending date and time of the search, INCLUSIVE.
     */
    public GlobalCMTSearch(LocalDateTime startDate, LocalDateTime endDate) {
        this.startDate = startDate;
        this.endDate = endDate;
    }

    /**
     * @return Set of {@link GlobalCMTID} which fulfill queries
     */
    public Set<GlobalCMTID> search() {
        return GlobalCMTCatalog.allNDKs().parallelStream().filter(ndk -> ndk.fulfill(this)).map(NDK::getGlobalCMTID)
                .collect(Collectors.toSet());
    }

    /**
     * @param predicate {@link Predicate} for Event data of global CMT IDs
     * @return all global CMT IDs satisfying the input predicate
     */
    public static Set<GlobalCMTID> search(Predicate<GlobalCMTAccess> predicate) {
        return GlobalCMTCatalog.allNDKs().stream().filter(predicate).map(NDK::getGlobalCMTID)
                .collect(Collectors.toSet());
    }

    /**
     * Adds the predicate for another condition.
     *
     * @param predicate {@link Predicate} for {@link GlobalCMTAccess}
     */
    public GlobalCMTSearch addPredicate(Predicate<GlobalCMTAccess> predicate) {
        predicateSet.add(predicate);
        return this;
    }

    /**
     * @return copy of predicate set
     */
    public Set<Predicate<GlobalCMTAccess>> getPredicateSet() {
        return new HashSet<>(predicateSet);
    }

    public LocalDateTime getStartDate() {
        return startDate;
    }
    public LocalDateTime getEndDate() {
        return endDate;
    }

    public LinearRange getDepthRange() {
        return depthRange;
    }

    public LinearRange getLatitudeRange() {
        return latitudeRange;
    }

    public CircularRange getLongitudeRange() {
        return longitudeRange;
    }

    public LinearRange getMbRange() {
        return mbRange;
    }

    public LinearRange getMsRange() {
        return msRange;
    }

    public LinearRange getMwRange() {
        return mwRange;
    }

    public LinearRange getCentroidTimeShiftRange() {
        return centroidTimeShiftRange;
    }

    public LinearRange getHalfDurationRange() {
        return halfDurationRange;
    }

    public LinearRange getTensionAxisPlungeRange() {
        return tensionAxisPlungeRange;
    }

    public LinearRange getNullAxisPlungeRange() {
        return nullAxisPlungeRange;
    }


    @Deprecated
    public GlobalCMTSearch setDepthRange(double lowerDepth, double upperDepth) {
        this.depthRange = new LinearRange("Depth", lowerDepth, upperDepth);
        return this;
    }
    /**
     * Set DEPTH range [km] (<b>NOT</b> radius). Default: [0:1000].
     *
     * @param depthRange ({@link LinearRange}) Depth range [km].
     * @return this
     */
    public GlobalCMTSearch setDepthRange(LinearRange depthRange) {
        this.depthRange = depthRange;
        return this;
    }

    @Deprecated
    public GlobalCMTSearch setLatitudeRange(double lowerLatitude, double upperLatitude) {
        this.latitudeRange = new LinearRange("Latitude", lowerLatitude, upperLatitude, -90.0, 90.0);
        return this;
    }
    /**
     * Set latitude range. Default: [-90:90].
     *
     * @param latitudeRange ({@link LinearRange}) Latitude range [deg].
     * @return this
     */
    public GlobalCMTSearch setLatitudeRange(LinearRange latitudeRange) {
        this.latitudeRange = latitudeRange;
        return this;
    }

    @Deprecated
    public GlobalCMTSearch setLongitudeRange(double lowerLongitude, double upperLongitude) {
        this.longitudeRange = new CircularRange("Longitude", lowerLongitude, upperLongitude, -180.0, 360.0);
        return this;
    }
    /**
     * Set longitude range. Default: [-180:180].
     *
     * @param longitudeRange ({@link CircularRange}) Longitude range [deg].
     * @return this
     */
    public GlobalCMTSearch setLongitudeRange(CircularRange longitudeRange) {
        this.longitudeRange = longitudeRange;
        return this;
    }

    /**
     * Set Mb range. Default: [0:10].
     * @param mbRange ({@link LinearRange}) Mb range.
     * @return this
     */
    public GlobalCMTSearch setMbRange(LinearRange mbRange) {
        this.mbRange = mbRange;
        return this;
    }

    /**
     * Set Ms range. Default: [0:10].
     * @param msRange ({@link LinearRange}) Ms range.
     * @return this
     */
    public GlobalCMTSearch setMsRange(LinearRange msRange) {
        this.msRange = msRange;
        return this;
    }

    /**
     * Set Mw Range.  Default: [0:10].
     * @param mwRange ({@link LinearRange}) Mw range.
     * @return this
     */
    public GlobalCMTSearch setMwRange(LinearRange mwRange) {
        this.mwRange = mwRange;
        return this;
    }

    /**
     * Set centroid timeshift range. Default: [-9999:9999].
     *
     * @param centroidTimeShiftRange ({@link LinearRange}) Centroid timeshift range [s].
     * @return this
     */
    public GlobalCMTSearch setCentroidTimeShiftRange(LinearRange centroidTimeShiftRange) {
        this.centroidTimeShiftRange = centroidTimeShiftRange;
        return this;
    }

    /**
     * Set half duration range. Default: [0:20].
     *
     * @param halfDurationRange ({@link LinearRange}) Half duration range [s].
     * @return this
     */
    public GlobalCMTSearch setHalfDurationRange(LinearRange halfDurationRange) {
        this.halfDurationRange = halfDurationRange;
        return this;
    }

    /**
     * Set tension axis plunge range. Default: [0:90].
     * @param tensionAxisPlungeRange ({@link LinearRange}) Tension axis plunge range [deg].
     * @return this
     */
    public GlobalCMTSearch setTensionAxisPlungeRange(LinearRange tensionAxisPlungeRange) {
        this.tensionAxisPlungeRange = tensionAxisPlungeRange;
        return this;
    }

    /**
     * Set null axis plunge range. Default: [0:90].
     * @param nullAxisPlungeRange ({@link LinearRange}) Null axis plunge range [deg].
     * @return this
     */
    public GlobalCMTSearch setNullAxisPlungeRange(LinearRange nullAxisPlungeRange) {
        this.nullAxisPlungeRange = nullAxisPlungeRange;
        return this;
    }

}
