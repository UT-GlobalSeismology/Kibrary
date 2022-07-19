package io.github.afeborgeaud.tomotool.raytheory;

import java.util.List;

import io.github.kensuke1984.kibrary.util.data.Observer;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

public class TraveltimeData {

    private String phaseName;

    private double[] traveltimes;

    private Observer observer;

    private GlobalCMTID event;

    private List<ScatterPoint> scatterPoints;

    public TraveltimeData(Observer observer, GlobalCMTID event
            , String phaseName, List<ScatterPoint> scatterPoints, double traveltimes[]) {
        this.phaseName = phaseName;
        this.traveltimes = traveltimes;
        this.observer = observer;
        this.event = event;
        this.scatterPoints = scatterPoints;
    }

    public double getEpicentralDistance() {
        return Math.toDegrees(event.getEventData().getCmtLocation().calculateGeographicalDistance(observer.getPosition()));
    }

    public double getAzimuth() {
        return Math.toDegrees(event.getEventData().getCmtLocation().calculateGeographicalAzimuth(observer.getPosition()));
    }

    public List<ScatterPoint> getScatterPointList() {
        return scatterPoints;
    }

    public double getTraveltimePerturbation() {
        return traveltimes[0];
    }

    public double getTraveltimePerturbationToPREM() {
        return getAbsoluteTraveltime3D() - getAbsoluteTraveltimePREM();
    }

    public double getAbsoluteTraveltimeRef() {
        return traveltimes[1];
    }

    public double getAbsoluteTraveltime3D() {
        return getAbsoluteTraveltimeRef() + getTraveltimePerturbation();
    }

    public double getAbsoluteTraveltimePREM() {
        return traveltimes[2];
    }

    public boolean isSameRecord(TraveltimeData o) {
        return o.event.equals(event) && o.observer.equals(observer);
    }

    public String getPhaseName() {
        return phaseName;
    }

    @Override
    public int hashCode() {
        return observer.hashCode() + event.hashCode() + phaseName.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        TraveltimeData m = (TraveltimeData) o;
        return phaseName.equals(m.phaseName)
            && observer.equals(m.observer)
            && event.equals(m.event);
    }

    @Override
    public String toString(){
        return observer +  " " + event + " " + phaseName + " " + String.format("%.5f", traveltimes[0]);
    }

    public String getHashableID() {
        return observer.toString() + event.toString() + phaseName;
    }

    public Observer getObserver() {
        return observer;
    }

    public GlobalCMTID getGlobalCMTID() {
        return event;
    }
}
