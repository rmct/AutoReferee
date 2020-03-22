package org.mctourney.autoreferee.regions;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
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
import org.mctourney.autoreferee.util.Vec3;

import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
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
	
	public RegionGraph(World w, Set<AutoRefRegion> regions) {
		this.mapRegions = regions;
		this.world = w;
	}
	
	// converts block data into a Graph object
	// this can then be used to make computations
	//    (e.g. finding dungeons, bedrock holes)
	//
	// computationally meh but its ok b/c it will
	// only be run once per match
	public RegionGraph computeGraph() {
		CuboidRegion bound = this.boundingBox();
		if(bound == null) return this;
		
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
		
		this.setLoaded(true);
		return this;
	}
	
	// finds connected areas (e.g. dungeons, the outside world)
	// useful for finding bedrock holes, and determining legality
	//									 of enderpearl teleports
	public RegionGraph findConnectedRegions() {
		ConnectivityInspector<Object, DefaultEdge> conn = new ConnectivityInspector<Object, DefaultEdge>(this.graph());
		List<Set<Object>> components = conn.connectedSets();
		
		this.connectedRegions = components.stream()
									.map(s -> s.stream()
											.map(o -> {
												Vec3 vec = this.vertexVec(o);
												return vec;
											})
											.filter(s2 -> s2 != null)
									.collect(Collectors.toSet())).collect(Collectors.toSet());
		
		return this;
	}
	
	public Boolean isInRestrictedArea(Location l, Set<Location> nonrestricted) {
		if(this.connectedRegions().isEmpty()) return true;
		
		Set<Vec3> vecs = nonrestricted.stream().map(loc -> vec(loc)).collect(Collectors.toSet());
		Set<Set<Vec3>> restrictedRegions = regionsWithoutPoints(vecs);
		if(restrictedRegions == null) return null;
		
		return restrictedRegions.stream().anyMatch(s -> s.contains( vec(l) ));
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
	
	private boolean isDungeonBound(Block b) {
		return UNBREAKABLE_BLOCKS.contains(b.getType()) || 
					this.openings().stream().anyMatch(reg -> reg.containsBlock(b));
	}
	
	private Graph<Object, DefaultEdge> graph() { return this.graph; }
	private HashBiMap<Vec3, Object> vertices() { return this.vertices; }
	private Object vertex(Vec3 vec) { return this.vertices().get(vec); }
	private Vec3 vertexVec(Object obj) { return this.vertices().inverse().get(obj); }
	
	private World world() { return this.world; }
	private Set<AutoRefRegion> regions() { return this.mapRegions; }
	private Set<AutoRefRegion> openings() { return this.dungeonOpenings; }
	public Set<Set<Vec3>> connectedRegions() { return this.connectedRegions; }
	
	public boolean loaded() { return this.loaded; }
	
	public RegionGraph setLoaded(boolean loaded) { this.loaded = loaded; return this; }
	public RegionGraph setDungeonOpenings(Set<AutoRefRegion> openings) {
		this.dungeonOpenings = openings; return this;
	}
}
