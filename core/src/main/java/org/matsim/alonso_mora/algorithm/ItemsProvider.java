package org.matsim.alonso_mora.algorithm;

import org.matsim.contrib.dvrp.load.DvrpLoad;

public interface ItemsProvider {
    int getItems(DvrpLoad load);
}
