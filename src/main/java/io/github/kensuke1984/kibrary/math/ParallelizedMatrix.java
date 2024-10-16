package io.github.kensuke1984.kibrary.math;

import org.apache.commons.math3.exception.DimensionMismatchException;
import org.apache.commons.math3.linear.Array2DRowRealMatrix;
import org.apache.commons.math3.linear.DefaultRealMatrixPreservingVisitor;
import org.apache.commons.math3.linear.MatrixUtils;
import org.apache.commons.math3.linear.RealMatrix;
import org.apache.commons.math3.linear.RealVector;

/**
 * 行列計算 parallelized Matrix
 *
 * @author Kensuke Konishi
 * @since a long time ago
 */
public class ParallelizedMatrix extends Array2DRowRealMatrix {

    private static final long serialVersionUID = 1L;

    public ParallelizedMatrix() {
        super();
    }

    public ParallelizedMatrix(double[] arg0) {
        super(arg0);
    }

    public ParallelizedMatrix(double[][] arg0, boolean arg1) throws IllegalArgumentException, NullPointerException {
        super(arg0, arg1);
    }

    public ParallelizedMatrix(double[][] d) throws IllegalArgumentException, NullPointerException {
        super(d);
    }

    public ParallelizedMatrix(int rowDimension, int columnDimension) throws IllegalArgumentException {
        super(rowDimension, columnDimension);
    }


    @Override
    public ParallelizedMatrix multiply(RealMatrix arg0) throws IllegalArgumentException {
        MatrixUtils.checkMultiplicationCompatible(this, arg0);
        return MatrixComputation.computeAB(this, arg0);
    }

    @Override
    public ParallelizedMatrix preMultiply(RealMatrix m) throws DimensionMismatchException {
        return MatrixComputation.computeAB(m, this);
    }

    public ParallelizedMatrix computeAtA() {
        return MatrixComputation.computeAtA(this);
    }

    @Override
    public RealVector preMultiply(RealVector v) throws DimensionMismatchException {
        if (v.getDimension() != getRowDimension())
            throw new DimensionMismatchException(v.getDimension(), getRowDimension());
        return MatrixComputation.premultiply(v, this);
    }

    @Override
    public RealVector operate(RealVector arg0) throws IllegalArgumentException {
        if (arg0.getDimension() != getColumnDimension())
            throw new DimensionMismatchException(arg0.getDimension(), getColumnDimension());
        return MatrixComputation.operate(this, arg0);
    }

    @Override
    public RealMatrix transpose() {
        RealMatrix out = new ParallelizedMatrix(getColumnDimension(), getRowDimension());
        walkInOptimizedOrder(new DefaultRealMatrixPreservingVisitor() {
            /** {@inheritDoc} */
            @Override
            public void visit(int row, int column, double value) {
                out.setEntry(column, row, value);
            }

        });
        return out;
    }
}
