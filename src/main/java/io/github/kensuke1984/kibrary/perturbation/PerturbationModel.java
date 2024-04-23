package io.github.kensuke1984.kibrary.perturbation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.kensuke1984.kibrary.elastic.VariableType;
import io.github.kensuke1984.kibrary.util.earth.FullPosition;
import io.github.kensuke1984.kibrary.util.earth.PolynomialStructure;
import io.github.kensuke1984.kibrary.voxel.KnownParameter;
import io.github.kensuke1984.kibrary.voxel.UnknownParameter;

/**
 * A model composed of a set of voxels, each of which its parameters are perturbed.
 * This class holds a set of {@link PerturbationVoxel}s.
 *
 * <p>
 * CAUTION, this class is <b>NOT IMMUTABLE</b>.
 *
 * @author otsuru
 * @since 2022/4/9
 */
public class PerturbationModel {

    private List<PerturbationVoxel> voxelList = new ArrayList<>();

    public PerturbationModel() {}

    private PerturbationModel(List<PerturbationVoxel> voxelList) {
        this.voxelList = voxelList;
    }

    public PerturbationModel(List<KnownParameter> knowns, PolynomialStructure initialStructure) {
        for (int i = 0; i < knowns.size(); i++) {
            boolean flag = false;

            UnknownParameter parameter = knowns.get(i).getParameter();
            double value = knowns.get(i).getValue();

            // if a voxel of same position is already added, set value to that voxel
            for (PerturbationVoxel voxel : voxelList) {
                if (voxel.getPosition().equals(parameter.getPosition())) {
                    voxel.setValue(parameter.getVariableType(), ScalarType.DELTA, value);
                    flag = true;
                }
            }

            // otherwise, create new voxel
            if (flag == false) {
                PerturbationVoxel voxel = new PerturbationVoxel(parameter.getPosition(), parameter.getSize(), initialStructure);
                voxel.setValue(parameter.getVariableType(), ScalarType.DELTA, value);
                voxelList.add(voxel);
            }
        }

        // if RHO is not included in unknowns, set RHO to value in initial structure
        for (PerturbationVoxel voxel : voxelList) {
            voxel.setDefaultIfUndefined(VariableType.RHO);
        }
    }

    /**
     * Create a new perturbation model with the same absolute parameter values but with a different reference structure.
     * @param oneDStructure ({@link PolynomialStructure}) New reference structure.
     * @return ({@link PerturbationModel}) New perturbation model with the given reference structure.
     */
    public PerturbationModel withReferenceStructureAs(PolynomialStructure oneDStructure) {
        List<PerturbationVoxel> newVoxelList = new ArrayList<>();
        for (PerturbationVoxel voxel : this.voxelList) {
            newVoxelList.add(voxel.withReferenceStructureAs(oneDStructure));
        }
        return new PerturbationModel(newVoxelList);
    }

    public void add(PerturbationVoxel voxel) {
        voxelList.add(voxel);
    }

    /**
     * Get perturbation values for a certain variable in the specified scalar type at all voxels.
     * @param variable ({@link VariableType}) Variable to get values for.
     * @param scalarType ({@link ScalarType}) Scalar type to get values in.
     * @return (LinkedHashMap of {@link FullPosition}, Double) Correspondence of position and values for each voxel.
     *
     * @author otsuru
     * @since 2024/4/22
     */
    public Map<FullPosition, Double> getValueMap(VariableType variable, ScalarType scalarType) {
        // This is created as LinkedHashMap to preserve the order of voxels
        Map<FullPosition, Double> map = new LinkedHashMap<>();
        for (PerturbationVoxel voxel : voxelList) {
            map.put(voxel.getPosition(), voxel.getValue(variable, scalarType));
        }
        return map;
    }

    public List<PerturbationVoxel> getVoxels() {
        return new ArrayList<>(voxelList);
    }

}
