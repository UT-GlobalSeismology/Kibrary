package io.github.kensuke1984.kibrary.math.geometry;

/**
 * Coordinate (x, y), where x and y are integers.
 *
 * @since 2026/2/5
 * @author otsuru
 */
public class IntegerXY {
    private final int x;
    private final int y;

    public IntegerXY(int x, int y) {
        this.x = x;
        this.y = y;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + x;
        result = prime * result + y;
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        IntegerXY other = (IntegerXY) obj;
        if (x != other.x)
            return false;
        if (y != other.y)
            return false;
        return true;
    }

    public int x() {
        return x;
    }
    public int y() {
        return y;
    }

}
