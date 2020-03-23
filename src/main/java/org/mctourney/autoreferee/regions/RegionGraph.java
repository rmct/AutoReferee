package org.mctourney.autoreferee.regions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collector;
import java.util.stream.Collectors;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.alg.connectivity.ConnectivityInspector;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;
import org.json.simple.JSONObject;
import org.mctourney.autoreferee.AutoRefTeam;
import org.mctourney.autoreferee.regions.AutoRefRegion.Flag;
import org.mctourney.autoreferee.util.BlockData;
import org.mctourney.autoreferee.util.Vec3;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import com.sk89q.worldedit.Vector;

public class RegionGraph {
	public static final List<Material> UNBREAKABLE_BLOCKS 
		= Arrays.asList(Material.BEDROCK, Material.ENDER_PORTAL_FRAME, Material.BARRIER);
	
	private Graph<Object, DefaultEdge> graph = new SimpleGraph<Object, DefaultEdge>(DefaultEdge.class);
	private HashBiMap<Vec3, Object> vertices = HashBiMap.create();
	private Set<Set<Vec3>> connectedRegions = Sets.newHashSet();
	
	private Set<AutoRefRegion> mapRegions;
	private Set<AutoRefRegion> dungeonOpenings = Sets.newHashSet();
	private World world;
	
	private boolean loaded = false;
	private Logger logger;
	private AutoRefTeam team;
	
	public RegionGraph(World w, Set<AutoRefRegion> regions, Logger logger, AutoRefTeam team) {
		this.mapRegions = regions;
		this.world = w;
		this.logger = logger;
		this.team = team;
	}
	
	// converts block data into a Graph object
	// this can then be used to make computations
	//    (e.g. finding dungeons, bedrock holes)
	//
	// computationally meh but its ok b/c it will
	// save relevant info to a file
	public RegionGraph computeGraph() {
		CuboidRegion bound = this.boundingBox();
		if(bound == null) return this;
		
		this.setLoaded(false);
		
		this.graph = new SimpleGraph<Object, DefaultEdge>(DefaultEdge.class);
		this.vertices.clear();
		this.connectedRegions.clear();
		
		// add all passable blocks as vertices
		for(AutoRefRegion r : regions()) {
			for(Vec3 vec : blocks(r)) {
				Block b = world().getBlockAt(vec.x(), vec.y(), vec.z());
				
				// disregard unbreakable blocks and dungeon openings
				// so we only have disconnected regions as vertices
				if(!this.isDungeonBound(b)) {
					Object vertex = new Object();
					this.vertices.put( vec , vertex );
					this.graph().addVertex(vertex);
				}
			}
		}
		
		// add edges between all blocks
		// not separated by unbreakables
		for(AutoRefRegion r : regions()) {
			for(Vec3 vec : blocks(r)) {
				Arrays.asList(world().getBlockAt(vec.x() + 1, vec.y(), vec.z()),
						      world().getBlockAt(vec.x() - 1, vec.y(), vec.z()),
						      world().getBlockAt(vec.x(), vec.y() + 1, vec.z()),
						      world().getBlockAt(vec.x(), vec.y() - 1, vec.z()),
						      world().getBlockAt(vec.x(), vec.y(), vec.z() + 1),
						      world().getBlockAt(vec.x(), vec.y(), vec.z() - 1))
				.forEach( b -> {
					// if it is not dungeon boundary and is within the region
					if(!this.isDungeonBound(b) && regions().stream().anyMatch(reg -> reg.containsBlock(b))) {
						Object v1 = this.vertex(vec(b)); if(v1 == null) return;
						Object v2 = this.vertex(vec); if(v2 == null) return;
						this.graph().addEdge( v1, v2 );
					}
				});;
			}
		}
		
		return this;
	}
	
	// finds connected areas (e.g. dungeons, the outside world)
	// useful for finding bedrock holes, and determining legality
	//									 of enderpearl teleports
	public RegionGraph findConnectedRegions() {
		log("Computing a RegionGraph...");
		
		ConnectivityInspector<Object, DefaultEdge> conn = new ConnectivityInspector<Object, DefaultEdge>(this.graph());
		List<Set<Object>> components = conn.connectedSets();
		
		this.connectedRegions = components.stream()
									.map(s -> s.stream()
											.map(o -> {
												Vec3 vec = this.vertexVec(o);
												return vec;
											})
											.filter(s2 -> s2 != null)
									.collect(Collectors.toSet())).filter(s2 -> !s2.isEmpty()).collect(Collectors.toSet());
		
		log("Initialized graph!");
		this.setLoaded(true);
		return this;
	}
	
	public HashMap<String, Object> toJSON(Set<Location> nonrestricted) {
		Set<Vec3> vecs = nonrestricted.stream().map(loc -> vec(loc)).collect(Collectors.toSet());
		Set<Set<Vec3>> restrictedRegions = regionsWithoutPoints(vecs);
		
		//JSONObject restricted = new JSONObject();
		//JSONObject regions = new JSONObject();
		
		/*int i = 0; int j = 0;
		for( Set<Vec3> vecl : restrictedRegions ) {
			JSONObject region = new JSONObject();
			
			for( Vec3 v : vecl ) {
				region.put(j, new int[] { v.x(), v.y(), v.z() } );
				j++;
			}
			
			regions.put(i, region);
			i++;
		}*/
		
		
		
		List<List<int[]>> regions = restrictedRegions.stream()
											.map(
													r -> r.stream().map(v -> (new int[]{ v.x(), v.y(), v.z() }) )
																.collect(Collectors.toList())
												)
											.collect(Collectors.toList());
		
		HashMap<String, Object> json = new HashMap<String, Object>();
		json.put("restricted", regions);
		
		return json;
		
		/*int i = 0; int j = 0;
		for( Set<Vec3> vecl : restrictedRegions ) {
			for( Vec3 v : vecl ) {
				regions[i][j][0] = v.x();
				regions[i][j][1] = v.y();
				regions[i][j][2] = v.z();
				j++;
			}
			i++;
		}*/
		
		//restricted.put("restricted", regions);
		//JSONObject r = new JSONObject();
		//r.put(this.team.getName(), restricted);
		
		//return r;
		//restricted.put("", arg1)
	}
	
	public Boolean isInRestrictedArea(Location l, Set<Location> nonrestricted) {
		if(this.connectedRegions().isEmpty()) return true;
		
		Set<Vec3> vecs = nonrestricted.stream().map(loc -> vec(loc)).collect(Collectors.toSet());
		Set<Set<Vec3>> restrictedRegions = regionsWithoutPoints(vecs);
		if(restrictedRegions == null) return null;
		
		return this.isRestricted(l, restrictedRegions, this.regions());
	}
	
	public boolean isRestricted(Location l, Set<Set<Vec3>> restrictedRegions, Set<AutoRefRegion> regions) {
		return restrictedRegions.stream().anyMatch(s -> s.contains( vec(l) )) ||
					!regions.stream().anyMatch(r -> r.contains(l));
	}
	
	public Set<Set<Vec3>> regionsWithPoints(Set<Vec3> pts) {
		return this.connectedRegions()
				.stream()
				.filter( s -> s.stream().anyMatch(v -> pts.contains(v)) )
				.collect(Collectors.toSet());
	}
	
	public Set<Set<Vec3>> regionsWithoutPoints(Set<Vec3> pts) {
		return this.connectedRegions()
				.stream()
				.filter( s -> s.stream().allMatch(v -> !pts.contains(v)) )
				.collect(Collectors.toSet());
	}
	
	public Set<Set<Vec3>> regionsWithoutPointsLoc(Set<Location> pts) {
		Set<Vec3> ptsVec = pts.stream().map(l -> vec(l)).collect(Collectors.toSet());
		return regionsWithoutPoints( ptsVec );
	}
	
	public Set<Block> shortestPath(Location l0, Location l1) {
		if(!this.loaded()) return Sets.newHashSet();
		
		DijkstraShortestPath<Object, DefaultEdge> path = new DijkstraShortestPath<Object, DefaultEdge>(this.graph());
		GraphPath<Object, DefaultEdge> gpath = path.getPath( vertex(vec(l0)), vertex(vec(l1)) );
		
		if(gpath == null) return null;
		
		return gpath.getVertexList().stream()
				.map(v -> this.world().getBlockAt(vertexVec(v).loc(this.world())))
				.collect(Collectors.toSet());
		
		//return null;
	}
	
	// creates Vec3 from coords
	private Vec3 vec(int x, int y, int z) {
		CuboidRegion bound = this.boundingBox();
		if(bound == null) return null;
		
		int maxX = bound.getMaximumPoint().getBlockX(); int minX = bound.getMinimumPoint().getBlockX();
		int maxY = bound.getMaximumPoint().getBlockY(); int minY = bound.getMinimumPoint().getBlockY();
		int maxZ = bound.getMaximumPoint().getBlockZ(); int minZ = bound.getMinimumPoint().getBlockZ();
		
		return new Vec3( x, y, z, maxX, maxY, maxZ, minX, minY, minZ );
	}
	
	// create Vec3 from Location
	public Vec3 vec(Location l) {
		return vec(l.getBlockX(), l.getBlockY(), l.getBlockZ());
	}

	// create Vec3 from block
	public Vec3 vec(Block b) {
		return vec(b.getLocation());
	}
	
	// helper function
	// gets all blocks in an ARRegion
	private Set<Vec3> blocks(AutoRefRegion reg) {
		CuboidRegion bound = reg.getBoundingCuboid();
		Set<Vec3> r = Sets.newHashSet();
		
		int maxX = bound.getMaximumPoint().getBlockX(); int minX = bound.getMinimumPoint().getBlockX();
		int maxY = bound.getMaximumPoint().getBlockY(); int minY = bound.getMinimumPoint().getBlockY();
		int maxZ = bound.getMaximumPoint().getBlockZ(); int minZ = bound.getMinimumPoint().getBlockZ();
		
		for( int z = minZ; z <= maxZ; z++ ) {
			for(int y = minY; y <= maxY; y++) {
				for(int x = minX; x <= maxX; x++) {
					if(reg.contains(new Location( world(), x, y, z ))) {
						r.add(vec( x, y, z ));
					}
				}
			}
		}
		
		return r;
	}
	
	private CuboidRegion boundingBox() {
		Set<CuboidRegion> reg = this.regions().stream()
				.map(r -> r.getBoundingCuboid())
				.collect(Collectors.toSet());
		
		return reg.stream()
				.reduce((a, b) -> CuboidRegion.combine(a, b))
				.orElse(null);
	}
	
	public boolean isDungeonBound(Block b) {
		return UNBREAKABLE_BLOCKS.contains(b.getType()) || 
					this.openings().stream().anyMatch(reg -> reg.containsBlock(b));
	}
	
	private Graph<Object, DefaultEdge> graph() { return this.graph; }
	private HashBiMap<Vec3, Object> vertices() { return this.vertices; }
	private Object vertex(Vec3 vec) { return this.vertices().get(vec); }
	private Vec3 vertexVec(Object obj) { return this.vertices().inverse().get(obj); }
	
	private World world() { return this.world; }
	private Set<AutoRefRegion> regions() { return this.mapRegions; }
	public Set<AutoRefRegion> openings() { 
		//return this.dungeonOpenings; 
		return this.regions().stream()
				.filter(r -> r.getFlags().contains(Flag.DUNGEON_BOUNDARY))
				.collect(Collectors.toSet());
	}
	public Set<Set<Vec3>> connectedRegions() { return this.connectedRegions; }
	
	public boolean loaded() { return this.loaded; }
	
	public RegionGraph regions(Set<AutoRefRegion> regions) { this.mapRegions = regions; return this; }
	private RegionGraph setLoaded(boolean loaded) { this.loaded = loaded; return this; }
	public RegionGraph setDungeonOpenings(Set<AutoRefRegion> openings) {
		this.dungeonOpenings = openings; return this;
	}
	
	private void log(String msg) {
		if(this.logger != null) this.logger.info( msg );
	}
	
	// Proper block in range
	public static Location unbreakableInRange(Location loc, int radius)
	{
		Block b = loc.getBlock();
		int h = loc.getWorld().getMaxHeight();
		int by = loc.getBlockY();

		for (int y = -radius; y <= radius; ++y) if (by + y >= 0 && by + y < h)
		for (int x = -radius; x <= radius; ++x)
		for (int z = -radius; z <= radius; ++z)
		{
			Block rel = b.getRelative(x, y, z);
			if(UNBREAKABLE_BLOCKS.contains(rel.getType())) return rel.getLocation();
			//if (blockdata.matchesBlock(rel)) return rel.getLocation();
		}

		return null;
	}
	
	public Set<Set<Vec3>> fromInts(List<List<List<Double>>> list) {	
		return list.stream()
					.map(l -> l.stream().map(v -> {
						if(v.size() < 3) return null;
						int v0 = (int) Math.round(v.get(0));
						int v1 = (int) Math.round(v.get(1));
						int v2 = (int) Math.round(v.get(2));
						return vec(v0, v1, v2);
					})
					.filter(v -> v != null)
					.collect(Collectors.toSet())).collect(Collectors.toSet());
	}
}
