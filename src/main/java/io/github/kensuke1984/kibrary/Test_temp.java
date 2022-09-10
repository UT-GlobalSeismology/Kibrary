package io.github.kensuke1984.kibrary;

import java.io.IOException;
import java.util.List;

import edu.sc.seis.TauP.Arrival;
import edu.sc.seis.TauP.TauModelException;
import edu.sc.seis.TauP.TauP_Pierce;
import edu.sc.seis.TauP.TimeDist;
import io.github.kensuke1984.kibrary.util.globalcmt.GlobalCMTID;

public class Test_temp {

    public static void main(String[] args) throws IOException, TauModelException {

        TauP_Pierce timeTool = new TauP_Pierce("prem");
        timeTool.setAddDepths("2861,2871");
        String[] phaseNames = "S,ScS".split(",");
        timeTool.setPhaseNames(phaseNames);
        timeTool.setSourceDepth(new GlobalCMTID("201506231218A").getEventData().getCmtPosition().getDepth()); //TODO use this for later calculation

        timeTool.calculate(80);
        List<Arrival> arrivals = timeTool.getArrivals();

        for (Arrival arrival : arrivals) {
            System.err.println(arrival);

            TimeDist[] pierces = arrival.getPierce();
            for (TimeDist pierce : pierces) {
                System.err.println(pierce);
            }
        }

    }
}
