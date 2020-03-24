package org.mctourney.autoreferee.entity;

import org.bukkit.craftbukkit.v1_8_R3.entity.CraftPlayer;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.craftbukkit.v1_8_R3.CraftWorld;
import org.bukkit.entity.Player;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.AutoReferee;
import org.mctourney.autoreferee.regions.AutoRefRegion;

import com.sk89q.worldedit.Location;

import net.minecraft.server.v1_8_R3.EntityEnderPearl;
import net.minecraft.server.v1_8_R3.EntityLiving;
import net.minecraft.server.v1_8_R3.EntityPlayer;
import net.minecraft.server.v1_8_R3.EntityTypes;
import net.minecraft.server.v1_8_R3.World;

public class EntityAREnderPearl extends EntityEnderPearl {
	private AutoReferee ar;
	private AutoRefTeam team;
	private Player player;
	private boolean canTravelThroughVoid = false;
	
	public EntityAREnderPearl(Player player, AutoRefTeam team, boolean travelThroughVoid) {
		super(((CraftWorld) ((CraftPlayer) player).getWorld()).getHandle(), ((CraftPlayer) player).getHandle());
		this.team = team;
		this.player = player;
		this.canTravelThroughVoid = travelThroughVoid;
	}

	// Called when pearl updates
	@Override
	public void t_() {
		if(!this.canTravelThroughVoid()) {
			if(!this.regions().stream()
					.anyMatch(r -> r.distanceToRegion( this.locX, this.locY, this.locZ ) <= 0.0 ) ) {
				if(this.getAR() != null) 
					this.getAR().sendMessageSync(this.player(), ChatColor.RED + "Illegal pearl!");
				
				this.die(); return;
			}
		}
		
		super.t_();
	}
	
	public void spawn() {
		this.getWorld().addEntity(this);
	}
	
	public static boolean PATCHED = false;
	
	public static boolean patch() {
		try {
			registerEntity(EntityAREnderPearl.class, "ThrownAREnderpearl", 14);
			PATCHED = true;
			
			return true;
		   } catch (Exception ignored) {
			   return false;
		   }
	}
	
	private static void registerEntity(Class entityClass, String entityName, int entityId) 
			throws NoSuchFieldException, SecurityException, IllegalArgumentException, IllegalAccessException{
		//ReflectionUtil.get
		
		for(String fieldname : Arrays.asList("c", "d", "f", "g")) {
			Field field = EntityTypes.class.getDeclaredField(fieldname);
			field.setAccessible(true);
			
			Field modifiersField = Field.class.getDeclaredField("modifiers");
			modifiersField.setAccessible(true);
			modifiersField.setInt(field, field.getModifiers() & ~Modifier.FINAL);
			
			Map m = (Map) field.get(null);
			
			switch(fieldname) {
			case "c":
				m.put(entityName, entityClass);
				break;
			case "d":
				m.put(entityClass, entityName);
				break;
			case "f":
				m.put(entityClass, entityId);
				break;
			case "g":
				m.put(entityName, entityId);
				break;
			}
		}
    }
	
	public EntityAREnderPearl setAR(AutoReferee ar) { this.ar = ar; return this; }
	
	private AutoRefTeam team() { return this.team; }
	private Set<AutoRefRegion> regions() { return this.team().getRegions(); }
	private Player player() { return this.player; }
	private boolean canTravelThroughVoid() { return this.canTravelThroughVoid; }
	private AutoReferee getAR() { return this.ar; }
}
