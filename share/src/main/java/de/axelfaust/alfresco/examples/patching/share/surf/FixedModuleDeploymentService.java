package de.axelfaust.alfresco.examples.patching.share.surf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.springframework.extensions.surf.ModuleDeploymentService;
import org.springframework.extensions.surf.types.ModuleDeployment;

/**
 * This is a trivial sub-class of the default module deployment service implementation intended to fix some (stupid) issues with deployed
 * modules sorting.
 *
 * @author Axel Faust
 */
public class FixedModuleDeploymentService extends ModuleDeploymentService
{

    /**
     * {@link Comparator} for sorting deployed modules - copy of private base member class
     */
    protected class DeployedModuleComparator implements Comparator<ModuleDeployment>
    {

        /**
         *
         * {@inheritDoc}
         */
        @Override
        public int compare(final ModuleDeployment o1, final ModuleDeployment o2)
        {
            final String indexO1 = o1.getProperty(ModuleDeployment.PROP_INDEX);
            final String indexO2 = o2.getProperty(ModuleDeployment.PROP_INDEX);

            int result = 0;

            int indexValO1 = o1.getIndex();
            int indexValO2 = o2.getIndex();

            // 0 is default value for modules without persisted index - should treat as "last" / "new" module by default
            if (indexValO1 == 0)
            {
                if (indexO1 == null || !indexO1.matches("\\d+"))
                {
                    indexValO1 = Integer.MAX_VALUE;
                }
            }

            if (indexValO2 == 0)
            {
                if (indexO2 == null || !indexO2.matches("\\d+"))
                {
                    indexValO2 = Integer.MAX_VALUE;
                }
            }

            result = Integer.compare(indexValO1, indexValO2);

            return result;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public synchronized List<ModuleDeployment> getDeployedModules()
    {
        final List<ModuleDeployment> deployedModules = new ArrayList<ModuleDeployment>(super.getDeployedModules());

        // ensure these are really sorted (they aren't until fix for MNT-13824 which is only available on 6.0 HEAD at the time of this fix)
        Collections.sort(deployedModules, new DeployedModuleComparator());

        return deployedModules;
    }
}
