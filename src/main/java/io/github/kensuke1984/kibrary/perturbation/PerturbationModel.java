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

    public PerturbationModel(List<UnknownParameter> unknowns, double[] values, PolynomialStructure initialStructure) {
        if (unknowns.size() != values.length) throw new IllegalArgumentException("Number of unknowns and values does not match");

        for (int i = 0; i < unknowns.size(); i++) {
            boolean flag = false;

            // if a voxel of same position is already added, set value to that voxel
            for (PerturbationVoxel voxel : voxelList) {
                if (voxel.getPosition().equals(unknowns.get(i).getPosition())) {
                    voxel.setDelta(VariableType.of(unknowns.get(i).getPartialType()), values[i]);
                    flag = true;
                }
            }

            // otherwise, create new voxel
            if (flag == false) {
                PerturbationVoxel voxel = new PerturbationVoxel(unknowns.get(i).getPosition(), unknowns.get(i).getSize(), initialStructure);
                voxel.setDelta(VariableType.of(unknowns.get(i).getPartialType()), values[i]);
                voxelList.add(voxel);
            }
        }

        // if RHO is not included in unknowns, set RHO to value in initial structure
        for (PerturbationVoxel voxel : voxelList) {
            voxel.setDefaultIfUndefined(VariableType.RHO);
        }
    }

    public PerturbationModel(List<KnownParameter> knowns, PolynomialStructure initialStructure) {
        for (int i = 0; i < knowns.size(); i++) {
            boolean flag = false;

            UnknownParameter parameter = knowns.get(i).getParameter();
            double value = knowns.get(i).getValue();

            // if a voxel of same position is already added, set value to that voxel
            for (PerturbationVoxel voxel : voxelList) {
                if (voxel.getPosition().equals(parameter.getPosition())) {
                    voxel.setDelta(VariableType.of(parameter.getPartialType()), value);
                    flag = true;
                }
            }

            // otherwise, create new voxel
            if (flag == false) {
                PerturbationVoxel voxel = new PerturbationVoxel(parameter.getPosition(), parameter.getSize(), initialStructure);
                voxel.setDelta(VariableType.of(parameter.getPartialType()), value);
                voxelList.add(voxel);
            }
        }

        // if RHO is not included in unknowns, set RHO to value in initial structure
        for (PerturbationVoxel voxel : voxelList) {
            voxel.setDefaultIfUndefined(VariableType.RHO);
        }
    }

    /**
     * Create a new perturbation model with the same absolute parameter values but with a different initial structure.
     * @param oneDStructure
     * @return
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

    public List<PerturbationVoxel> getVoxels() {
        return new ArrayList<>(voxelList);
    }

    /**
     * Get perturbation values (in [%]) for a certain variable at all voxels.
     * @param type ({@link VariableType})
     * @return (LinkedHashMap of {@link FullPosition}, Double) Correspondence of position and perturbation value (in [%])
     *
     * @author otsuru
     * @since 2023/3/4
     */
    public Map<FullPosition, Double> getPercentForType(VariableType type) {
        // This is created as LinkedHashMap to preserve the order of voxels
        Map<FullPosition, Double> map = new LinkedHashMap<>();
        for (PerturbationVoxel voxel : voxelList) {
            map.put(voxel.getPosition(), voxel.getPercent(type));
        }
        return map;
    }

}
