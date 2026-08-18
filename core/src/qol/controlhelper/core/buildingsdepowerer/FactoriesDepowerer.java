package qol.controlhelper.core.buildingsdepowerer;

import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.world.Block;
import qol.controlhelper.core.requestexecutor.RequestExecutor;

public class FactoriesDepowerer extends BuildingsDepowerer{
    public FactoriesDepowerer(RequestExecutor requestExecutor){
        super(new Seq<>(new Block[]{
            Blocks.groundFactory, Blocks.airFactory, Blocks.navalFactory,
            Blocks.additiveReconstructor, Blocks.multiplicativeReconstructor,
            Blocks.exponentialReconstructor, Blocks.tetrativeReconstructor
        }), requestExecutor);
    }
}
