package io.core9.plugin.features;

import io.core9.core.boot.BootStrategy;
import io.core9.core.plugin.Core9Plugin;
import io.core9.plugin.server.VirtualHostProcessor;

public interface FeaturesBootstrap extends Core9Plugin, VirtualHostProcessor, BootStrategy {

}
