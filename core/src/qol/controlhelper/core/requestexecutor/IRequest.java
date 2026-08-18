package qol.controlhelper.core.requestexecutor;

import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Teamc;
import mindustry.gen.Unit;

import static mindustry.Vars.player;

public interface IRequest{
    void Execute();

    class TransferItemsTo implements IRequest{
        public Unit unit;
        public int amount;
        public Building building;
        public Runnable callback;

        public TransferItemsTo(Unit unit, int amount, Building building, Runnable callback){
            this.unit = unit;
            this.amount = amount;
            this.building = building;
            this.callback = callback;
        }

        public TransferItemsTo(Unit unit, int amount, Building building){
            this.unit = unit;
            this.amount = amount;
            this.building = building;
        }

        @Override
        public void Execute(){
            if(!unit.dead && !building.dead){
                int accepted = building.acceptStack(unit.stack.item, amount, (Teamc)unit);
                Call.transferItemTo(unit, unit.stack.item, accepted, unit.x, unit.y, building);
                if(callback != null) callback.run();
            }
        }
    }

    class TileConfig implements IRequest{
        public Building building;
        public Object value;
        public Runnable callback;

        public TileConfig(Building building, Object value, Runnable callback){
            this.building = building;
            this.value = value;
            this.callback = callback;
        }

        public TileConfig(Building building, Object value){
            this.building = building;
            this.value = value;
        }

        @Override
        public void Execute(){
            if(building != null && !building.dead){
                Call.tileConfig(player, building, value);
                if(callback != null) callback.run();
            }
        }
    }
}
