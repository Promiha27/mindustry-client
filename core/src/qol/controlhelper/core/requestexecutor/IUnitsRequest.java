package qol.controlhelper.core.requestexecutor;

import arc.math.geom.Position;
import arc.math.geom.Vec2;
import arc.struct.Seq;
import mindustry.ai.UnitCommand;
import mindustry.gen.Building;
import mindustry.gen.Call;
import mindustry.gen.Unit;
import qol.controlhelper.util.ArrayUtils;

import static mindustry.Vars.player;

public interface IUnitsRequest extends IRequest{
    boolean AreSimiliar(IUnitsRequest request);

    void MergeRequest(IUnitsRequest request);

    class UnitCommandRequest implements IUnitsRequest{
        public int[] unitIds;
        public UnitCommand command;
        public Seq<Runnable> callbacks = new Seq<>();

        public UnitCommandRequest(int[] unitIds, UnitCommand command){
            this.unitIds = unitIds;
            this.command = command;
        }

        public UnitCommandRequest(int[] unitIds, UnitCommand command, Runnable callback){
            this.unitIds = unitIds;
            this.command = command;
            this.callbacks.add(callback);
        }

        @Override
        public void Execute(){
            Call.setUnitCommand(player, unitIds, command);
            for(Runnable callback : callbacks){
                if(callback != null) callback.run();
            }
        }

        @Override
        public boolean AreSimiliar(IUnitsRequest request){
            if(!(request instanceof UnitCommandRequest commandRequest)) return false;
            return commandRequest.command == command;
        }

        @Override
        public void MergeRequest(IUnitsRequest request){
            if(request instanceof UnitCommandRequest commandRequest){
                unitIds = ArrayUtils.Concatenate(unitIds, commandRequest.unitIds);
                callbacks.add(commandRequest.callbacks);
            }
        }
    }

    class MoveRequest implements IUnitsRequest{
        public int[] unitIds;
        public Building building;
        public Unit unit;
        public Vec2 target;
        public Seq<Runnable> callbacks = new Seq<>();

        public MoveRequest(int[] unitIds, Building building, Unit unit, Vec2 target){
            this.unitIds = unitIds;
            this.building = building;
            this.unit = unit;
            this.target = target;
        }

        public MoveRequest(int[] unitIds, Building building, Unit unit, Vec2 target, Runnable callback){
            this.unitIds = unitIds;
            this.building = building;
            this.unit = unit;
            this.target = target;
            this.callbacks.add(callback);
        }

        @Override
        public void Execute(){
            Call.commandUnits(player, unitIds, building, unit, target, false, true);
            for(Runnable callback : callbacks){
                if(callback != null) callback.run();
            }
        }

        @Override
        public boolean AreSimiliar(IUnitsRequest request){
            if(!(request instanceof MoveRequest moveRequest)) return false;
            if(moveRequest.building != building || moveRequest.unit != unit) return false;
            return target != null && moveRequest.target != null && moveRequest.target.within((Position)target, 0.2f);
        }

        @Override
        public void MergeRequest(IUnitsRequest request){
            if(request instanceof MoveRequest moveRequest){
                unitIds = ArrayUtils.Concatenate(unitIds, moveRequest.unitIds);
                callbacks.add(moveRequest.callbacks);
            }
        }
    }
}
