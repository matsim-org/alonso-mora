package org.matsim.alonso_mora.algorithm;

import org.matsim.contrib.dvrp.load.DvrpLoad;
import org.matsim.contrib.dvrp.load.DvrpLoadType;

public class DefaultItemsProvider implements ItemsProvider {
    private final int size;

    public DefaultItemsProvider(DvrpLoadType loadType) {
        this.size = loadType.size();
    }

    @Override
    public int getItems(DvrpLoad load) {
        int items = 0;

        for (int k = 0; k < size; k++) {
            items += (int) load.getElement(k);
        }

        return items;
    }
}
