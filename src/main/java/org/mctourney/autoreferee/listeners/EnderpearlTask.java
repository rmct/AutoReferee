package org.mctourney.autoreferee.listeners;

import org.bukkit.Location;
import org.bukkit.entity.EnderPearl;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.mctourney.autoreferee.AutoRefMatch;
import org.mctourney.autoreferee.regions.AutoRefRegion.Flag;

public class EnderpearlTask extends BukkitRunnable {

    private final EnderPearl enderpearl;
    private final AutoRefMatch match;
    
    public EnderpearlTask(EnderPearl e, AutoRefMatch m) {
        this.enderpearl = e;
        this.match = m;
    }

    @Override
    public void run() {
    	//get enderpearl location
       Location loc = enderpearl.getLocation();
       //if the enderpearl is in a no enderpearl region
       if(match.hasFlag(loc, Flag.NO_ENDERPEARL)){
    	   //remove it from existance
    	   enderpearl.remove();
    	   //stop running tasks on a removed entity
    	   this.cancel();
       }
    }

}
