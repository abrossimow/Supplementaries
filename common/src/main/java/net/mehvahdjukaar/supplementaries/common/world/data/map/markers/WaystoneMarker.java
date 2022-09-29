package net.mehvahdjukaar.supplementaries.common.world.data.map.markers;

import net.mehvahdjukaar.moonlight.api.map.CustomMapDecoration;
import net.mehvahdjukaar.moonlight.api.map.markers.NamedMapBlockMarker;
import net.mehvahdjukaar.supplementaries.common.world.data.map.ModMapMarkers;
import net.mehvahdjukaar.supplementaries.integration.CompatHandler;
import net.mehvahdjukaar.supplementaries.integration.WaystonesCompat;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.BlockGetter;

import javax.annotation.Nullable;

public class WaystoneMarker extends NamedMapBlockMarker<CustomMapDecoration> {

    public WaystoneMarker() {
        super(ModMapMarkers.WAYSTONE_DECORATION_TYPE);
    }

    public WaystoneMarker(BlockPos pos, @Nullable Component name) {
        super(ModMapMarkers.WAYSTONE_DECORATION_TYPE, pos);
        this.name = name;
    }

    @Nullable
    public static WaystoneMarker getFromWorld(BlockGetter world, BlockPos pos) {
        if (CompatHandler.waystones) {
            var te = world.getBlockEntity(pos);

            if (WaystonesCompat.isWaystone(te)) {
                Component name = WaystonesCompat.getName(te);
                return new WaystoneMarker(pos, name);
             }
        }
        return null;
    }

    @Nullable
    @Override
    public CustomMapDecoration doCreateDecoration(byte mapX, byte mapY, byte rot) {
        return new CustomMapDecoration(this.getType(), mapX, mapY, rot, name);
    }
}