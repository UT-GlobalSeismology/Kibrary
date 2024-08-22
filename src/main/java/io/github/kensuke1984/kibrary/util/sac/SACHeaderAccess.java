package io.github.kensuke1984.kibrary.util.sac;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

/**
 * Interface of SAC header data<br>
 * <p>
 * The SAC header is described
 * <A href=https://ds.iris.edu/files/sac-manual/manual/file_format.html>here</a>
 *
 * @author Kensuke Konishi
 * @since a long time ago
 * @see <a href=http://ds.iris.edu/ds/nodes/dmc/forms/sac/>SAC</a>
 */
public interface SACHeaderAccess {

    /**
     * @return ({@link FullPosition}) Position of the source made from EVLA, EVLO and EVDP.
     */
    default FullPosition getEventLocation() {
        return FullPosition.constructByDepth(getValue(SACHeaderEnum.EVLA), getValue(SACHeaderEnum.EVLO), getValue(SACHeaderEnum.EVDP));
    }

    /**
     * Creates a new instance with a new EVLA, EVLO, and EVDP value.
     * @param eventPosition ({@link FullPosition}) Position to be set to EVLA, EVLO and EVDP.
     * @return ({@link SACHeaderAccess}) New instance with the specified event position.
     */
    default SACHeaderAccess withEventLocation(FullPosition eventPosition) {
        return withValue(SACHeaderEnum.EVLA, eventPosition.getLatitude())
                .withValue(SACHeaderEnum.EVLO, eventPosition.getLongitude())
                .withValue(SACHeaderEnum.EVDP, eventPosition.getDepth());
    }

    /**
     * @return (LocalDateTime) Date and time of CMT.
     */
    default LocalDateTime getEventTime() {
        return LocalDateTime.of(LocalDate.ofYearDay(getInt(SACHeaderEnum.NZYEAR), getInt(SACHeaderEnum.NZJDAY)),
                LocalTime.of(getInt(SACHeaderEnum.NZHOUR), getInt(SACHeaderEnum.NZMIN), getInt(SACHeaderEnum.NZSEC),
                        getInt(SACHeaderEnum.NZMSEC) * 1000 * 1000));
    }

    /**
     * Creates a new instance with a new event date and time.
     * @param eventDateTime (LocalDateTime) Date and time to set in the SAC header.
     * @return ({@link SACHeaderAccess}) New instance with the specified event date and time.
     */
    default SACHeaderAccess withEventTime(LocalDateTime eventDateTime) {
        return withInt(SACHeaderEnum.NZYEAR, eventDateTime.getYear())
                .withInt(SACHeaderEnum.NZJDAY, eventDateTime.getDayOfYear())
                .withInt(SACHeaderEnum.NZHOUR, eventDateTime.getHour())
                .withInt(SACHeaderEnum.NZMIN, eventDateTime.getMinute())
                .withInt(SACHeaderEnum.NZSEC, eventDateTime.getSecond())
                .withInt(SACHeaderEnum.NZMSEC, eventDateTime.getNano() / 1000 / 1000);
    }

    /**
     * @return ({@link Observer}) Observer of this header.
     */
    default Observer getObserver() {
        return Observer.of(this);
    }

    /**
     * Creates a new instance with a new KSTNM, KNETWK, STLA, and STLO value.
     * @param observer ({@link Observer}) Observer to be set.
     * @return ({@link SACHeaderAccess}) New instance with the specified observer.
     */
    default SACHeaderAccess withObserver(Observer observer) {
        SACHeaderAccess sd = withSACString(SACHeaderEnum.KSTNM, observer.getStation());
        sd = sd.withSACString(SACHeaderEnum.KNETWK, observer.getNetwork());
        return sd.withValue(SACHeaderEnum.STLA, observer.getPosition().getLatitude())
                .withValue(SACHeaderEnum.STLO, observer.getPosition().getLongitude());
    }

    /**
     * Get the component in KCMPNM (vertical:Z, radial:R, trnsvers:T).
     * @return ({@link SACComponent}) Component in KCMPNM.
     */
    default SACComponent getComponent() {
        switch (getSACString(SACHeaderEnum.KCMPNM)) {
            case "Z":
            case "BHZ": //TODO erase: this is set here because DataKitchen hadn't placed "vertical" in Sac headers before.
            case "vertical": //TODO erase: old format
                return SACComponent.Z;
            case "R":
            case "radial": //TODO erase: old format
                return SACComponent.R;
            case "T":
            case "trnsvers": //TODO erase: old format
                return SACComponent.T;
            default:
                throw new RuntimeException("KCMPNM is invalid; must be Z, R, or T.");
        }
    }

    /**
     * Get the globalCMTID in KEVNM.
     * If the value KEVNM is not valid for GlobalCMTID, then RuntimeException will be thrown.
     * @return ({@link GlobalCMTID}) Event ID in KEVNM.
     */
    default GlobalCMTID getGlobalCMTID() {
        return new GlobalCMTID(getSACString(SACHeaderEnum.KEVNM));
    }

    /**
     * @return (boolean) Whether the SACfile is filtered. true: filtered, false: not filtered.
     */
    default boolean isFiltered() {
        return getValue(SACHeaderEnum.USER0) != -12345 || getValue(SACHeaderEnum.USER1) != -12345;
    }

    /**
     * マーカーに時間を設定する ぴっちりdelta * n の時刻に少し修正する round(time/delta)*delta Set a time
     * marker. Input time will be changed as the new one is on the SAC file
     * <p>
     * If a SAC file has values of time = 0.05, 0.10, 0.15 and the input time is
     * 0.09, then a marker will be set on 0.10(closest).
     *
     * @param marker must be Tn n=[0-9], A
     * @param time   to set in this
     * @return {@link SACHeaderAccess} with a time marker.
     * @throws IllegalArgumentException if marker is not Tn
     */
    default SACHeaderAccess withTimeMarker(SACHeaderEnum marker, double time) {
        if (marker != SACHeaderEnum.T0 && marker != SACHeaderEnum.T1 && marker != SACHeaderEnum.T2 &&
                marker != SACHeaderEnum.T3 && marker != SACHeaderEnum.T4 && marker != SACHeaderEnum.T5 &&
                marker != SACHeaderEnum.T6 && marker != SACHeaderEnum.T7 && marker != SACHeaderEnum.T8 &&
                marker != SACHeaderEnum.T9 && marker != SACHeaderEnum.A)
            throw new IllegalArgumentException("Only Tn n=[0-9] can be set");
        double b = getValue(SACHeaderEnum.B);
        // if(getValue(SacHeaderEnum.B)!=0)
        // throw new IllegalStateException("Value B is not 0.");
        double delta = getValue(SACHeaderEnum.DELTA);
        double inputTime = Math.round((time - b) / delta) * delta + b;
        // System.out.println(b + " " + inputTime);
        return withValue(marker, inputTime);
    }

    /**
     * Get a boolean value of a header field.
     * @param sacHeaderEnum ({@link SACHeaderEnum}) A key to a boolean value.
     * @return (boolean) The value in the header field.
     * @throws IllegalArgumentException if the input {@link SACHeaderEnum} is not of a boolean value.
     */
    boolean getBoolean(SACHeaderEnum sacHeaderEnum);

    /**
     * Get an integer value of a header field.
     * @param sacHeaderEnum ({@link SACHeaderEnum}) A key to an integer value.
     * @return (int) The value in the header field.
     * @throws IllegalArgumentException if the input {@link SACHeaderEnum} is not of an integer value.
     */
    int getInt(SACHeaderEnum sacHeaderEnum);

    /**
     * @param sacHeaderEnum a key to a Enumerated value
     * @return a enumerated value to the input {@link SACHeaderEnum}
     * @throws IllegalArgumentException if the input {@link SACHeaderEnum} is not of an Emumerated
     *                                  value.
     */
    int getSACEnumerated(SACHeaderEnum sacHeaderEnum);

    /**
     * Get a double value of a header field. The value is set as a float value in the header.
     * @param sacHeaderEnum ({@link SACHeaderEnum}) A key to a float value.
     * @return (double) The value in the header field.
     * @throws IllegalArgumentException if the input {@link SACHeaderEnum} is not of a float value.
     */
    double getValue(SACHeaderEnum sacHeaderEnum);

    /**
     * Get an String value of a header field.
     * @param sacHeaderEnum ({@link SACHeaderEnum}) A key to a String value.
     * @return (String) The value in the header field.
     */
    String getSACString(SACHeaderEnum sacHeaderEnum);

    /**
     * Set a boolean value
     *
     * @param sacHeaderEnum a key to a boolean value
     * @param bool          to be set
     * @return {@link SACHeaderAccess} with the bool
     * @throws IllegalArgumentException if the input {@link SACHeaderEnum} is not of a boolean value
     *                                  of is a special boolean.
     */
    SACHeaderAccess withBoolean(SACHeaderEnum sacHeaderEnum, boolean bool);

    /**
     * 整数値を代入する not enumerized TODO debug
     *
     * @param sacHeaderEnum a key to an integer value
     * @param value         an integer value to be set
     * @return {@link SACHeaderAccess} with the value
     */
    SACHeaderAccess withInt(SACHeaderEnum sacHeaderEnum, int value);

    /**
     * Enumeratedフィールドの代入 今は整数値で受け取る
     *
     * @param sacHeaderEnum a key to an Enumerated field
     * @param value         a integer value to input
     * @return {@link SACHeaderAccess} with the value
     * @throws IllegalArgumentException if the input {@link SACHeaderEnum} is not of an enumarated
     *                                  value
     */
    SACHeaderAccess withSACEnumerated(SACHeaderEnum sacHeaderEnum, int value);

    /**
     * Set a double value. Note that a SAC file just holds values as Float not
     * Double
     *
     * @param sacHeaderEnum a key to a float value
     * @param value         a double value to be set
     * @return {@link SACHeaderAccess} with the value
     */
    SACHeaderAccess withValue(SACHeaderEnum sacHeaderEnum, double value);

    /**
     * Set a String value
     *
     * @param sacHeaderEnum a key to a String value
     * @param string        to be set
     * @return {@link SACHeaderAccess} with the string
     * @throws IllegalArgumentException if the input {@link SACHeaderEnum} is not of a String value,
     *                                  if the input string has a invalid length.
     */
    SACHeaderAccess withSACString(SACHeaderEnum sacHeaderEnum, String string);

}
