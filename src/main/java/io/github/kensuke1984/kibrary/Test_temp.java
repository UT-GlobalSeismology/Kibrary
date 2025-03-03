package io.github.kensuke1984.kibrary;

import java.io.IOException;

import edu.sc.seis.TauP.TauModelException;
import io.github.kensuke1984.kibrary.util.earth.Earth;
import io.github.kensuke1984.kibrary.util.earth.HorizontalPosition;

public class Test_temp {

    public static void main(String[] args) throws IOException, TauModelException {
        HorizontalPosition pos0 = new HorizontalPosition(0,0);
        HorizontalPosition pos1 = new HorizontalPosition(0,50);
        HorizontalPosition pos2 = new HorizontalPosition(30,-50);

        System.err.println(Earth.computeMidpoint(pos0, pos1));
        System.err.println(Earth.computeMidpoint(pos1, pos0));
        System.err.println(Earth.computeMidpoint(pos1, pos2));

    }
}
