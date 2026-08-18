package eui.units;

import arc.struct.ObjectMap;
import arc.struct.Seq;
import eui.util.RelativeValue;
import mindustry.game.Team;
import mindustry.gen.Groups;
import mindustry.gen.Unit;
import mindustry.type.UnitType;

/**
 * Groups every unit currently in play by team and type, ranks each team's composition by total
 * {@link RelativeValue}, and ranks teams against each other the same way - powers the units table HUD
 * widget (top N teams, top N unit types per team) and the "is this enemy group actually a threat"
 * check the under-attack alert uses. Ported from units/units-counter.js.
 */
public class UnitsCounter{
    public static class UnitTypeInfo{
        public final UnitType type;
        public int amount;
        public Unit entity; //one representative unit of this type, for its icon/portrait
        public float value;

        UnitTypeInfo(UnitType type, Unit entity){
            this.type = type;
            this.entity = entity;
        }
    }

    public static class TeamInfo{
        public final Team team;
        public Seq<UnitTypeInfo> units = new Seq<>();
        public float value;

        TeamInfo(Team team){
            this.team = team;
        }
    }

    public static Seq<TeamInfo> getUnitsValueTop(int amountToDisplay, int granularity, boolean hideCoreUnits, boolean hideSupportUnits){
        ObjectMap<Team, ObjectMap<UnitType, UnitTypeInfo>> byTeam = new ObjectMap<>();

        for(Unit unit : Groups.unit){
            String typeName = unit.type.name;
            if(hideCoreUnits && CoreUnits.includes(typeName)) continue;
            if(hideSupportUnits && SupportUnits.includes(typeName)) continue;
            if(Blacklist.includes(typeName)) continue;

            ObjectMap<UnitType, UnitTypeInfo> perType = byTeam.get(unit.team, ObjectMap::new);
            UnitTypeInfo info = perType.get(unit.type);
            if(info == null){
                info = new UnitTypeInfo(unit.type, unit);
                perType.put(unit.type, info);
            }
            info.amount++;
        }

        Seq<TeamInfo> result = new Seq<>();
        for(ObjectMap.Entry<Team, ObjectMap<UnitType, UnitTypeInfo>> teamEntry : byTeam){
            TeamInfo teamInfo = new TeamInfo(teamEntry.key);

            Seq<UnitTypeInfo> allUnits = new Seq<>();
            for(UnitTypeInfo info : teamEntry.value.values()){
                info.value = info.amount * RelativeValue.getUnitValue(info.type.name);
                teamInfo.value += info.value;
                allUnits.add(info);
            }
            allUnits.sort((a, b) -> Float.compare(b.value, a.value));
            allUnits.truncate(granularity);
            teamInfo.units = allUnits;

            result.add(teamInfo);
        }

        result.sort((a, b) -> Float.compare(b.value, a.value));
        result.truncate(amountToDisplay);
        return result;
    }

    public static boolean isDangerous(Unit unit){
        String type = unit.type.name;
        if(CoreUnits.includes(type)) return false;
        return !SupportUnits.includes(type);
    }
}
