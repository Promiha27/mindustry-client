package qol.controlhelper.core.buildingsdepowerer;

import arc.struct.Seq;
import mindustry.content.Blocks;
import mindustry.world.Block;
import qol.controlhelper.core.requestexecutor.RequestExecutor;

public class ProducersDepowerer extends BuildingsDepowerer{
    public ProducersDepowerer(RequestExecutor requestExecutor){
        super(new Seq<>(new Block[]{Blocks.surgeSmelter, Blocks.plastaniumCompressor}), requestExecutor);
    }
}
