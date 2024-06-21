package dk.smlaursen.TSNCF.evaluator;

import java.util.Collection;
import java.util.HashMap;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.jgrapht.Graph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dk.smlaursen.TSNCF.application.Application;
import dk.smlaursen.TSNCF.application.AVBApplication;
import dk.smlaursen.TSNCF.application.TTApplication;
import dk.smlaursen.TSNCF.architecture.GCLEdge;
import dk.smlaursen.TSNCF.architecture.Node;
import dk.smlaursen.TSNCF.evaluator.ModifiedAVBEvaluatorCost.Objective;
import dk.smlaursen.TSNCF.solver.Multicast;
import dk.smlaursen.TSNCF.solver.Unicast;

/** This {@link Evaluator} is used for calculating the cost of the given {@link Unicast} assignment taking SR- and TT-timings into account.*/
public class ModifiedAVBEvaluator implements Evaluator{
	//----------- PENALTIES ----------------------
	/** The threshold of WCRT / DEADLINE for when starting to increase with {@value #THRESHOLD_EXCEEDED_PENALITY} per. percent*/
	private final static double PENALITY_THRESHOLD = 0.0;
	//----------- CONFIGURATION ------------------
	/** The maximum BE frame-size*/
	private final static int MAX_BE_FRAME_BYTES = 1522;

	private static Logger logger = LoggerFactory.getLogger(ModifiedAVBEvaluator.class.getSimpleName());

	@Override
	public Cost evaluate(Collection<Unicast> route, Graph<Node, GCLEdge> graph) {
		Map<GCLEdge, Double> allocMap = new HashMap<GCLEdge, Double>(); 
		Map<GCLEdge, Double> ttAllocMap = new HashMap<GCLEdge, Double>();
		List<Multicast> multicasts = Multicast.generateMulticasts(route);
		ModifiedAVBEvaluatorCost cost = new ModifiedAVBEvaluatorCost();

		// Then we identify all the different modes 
		Map<String, Set<Multicast>> modeMap = new HashMap<String, Set<Multicast>>();
		for(Multicast m : multicasts){
			//System.out.println("TitleM1: " + m.getApplication().getTitle() + " Interval: " + m.getApplication().getInterval());
			if(m.getApplication() instanceof AVBApplication){
				AVBApplication app = (AVBApplication) m.getApplication();
				for(String mode : app.getModes()){
					if(!modeMap.containsKey(mode)){
						modeMap.put(mode, new HashSet<Multicast>());
					}
					modeMap.get(mode).add(m);
				}
			} else if(m.getApplication() instanceof TTApplication){
				//First we add the TT-contribution
				//TODO this is only for 125us intervals - if we also consider 250us class B intervals, 
				//TODO this only allows one TT streams per edge
				for(Unicast u : m.getUnicasts()){
					for(GCLEdge edge : u.getRoute().getEdgeList()){
						double reservedTTTraffic = edge.calculateReservedForTTTraffic(125);
						//System.out.println("Title: " + u.getApplication().getTitle() + "U Interval: " + u.getApplication().getInterval());
						//System.out.println("Title: " + m.getApplication().getTitle() + "M Interval: " + m.getApplication().getInterval());

						//double reservedTTTraffic = edge.calculateReservedForTTTraffic(u.getApplication().getInterval());
						ttAllocMap.put(edge, reservedTTTraffic);
					}
				}
			
			} else {
				throw new IllegalArgumentException("Unsupported application class '"+m.getApplication().getClass().getSimpleName()+"' of "+m.getApplication().getTitle());
			}
		}
		HashMap<Application, HashSet<GCLEdge>> edgeMap = new HashMap<Application, HashSet<GCLEdge>>(); 
		//First we put all unqiue edges in the map
		for(Unicast r : route){
			if(!edgeMap.containsKey(r.getApplication())){
				edgeMap.put(r.getApplication(), new HashSet<GCLEdge>());
			}
			edgeMap.get(r.getApplication()).addAll(r.getRoute().getEdgeList());
		}
		
		//Cost also includes the the sum of all disjoint edges
		for(Application app : edgeMap.keySet()){
			if(app instanceof AVBApplication){
				cost.add(Objective.three, edgeMap.get(app).size());
			}
		}
		
		//Then we run through each mode
		for(String mode : modeMap.keySet()){
			Set<Multicast> rs_in_curr_mode = new HashSet<Multicast>(modeMap.get(mode));

			//Clear allocationMap before evaluating next mode
			allocMap.clear();
			//Add all pre-calculated TTAllocs
			allocMap.putAll(ttAllocMap);

			//First we calculate the accumulated bwthRequirement and the cost due to disjointEdges
			for(Multicast m : rs_in_curr_mode){
				AVBApplication app = (AVBApplication) m.getApplication();
				
				//Run over all the unique edges in that application
				for(GCLEdge edge : edgeMap.get(app)){
					//If not already there, put it there
					if(!allocMap.containsKey(edge)){
						allocMap.put(edge, 0.0);
					}

					double allocMbps = (app.getMaxFrameSize() * 8) * app.getNoOfFramesPerInterval() / app.getInterval();
					double totalAllocMbps = allocMap.get(edge) + allocMbps;

					//Abort if edge-capacity exceeded (remember not 100% of the edge is allowed to be reserved)
					/*if(totalAllocMbps > edge.getRateMbps() * edge.getAllocationCapacity()){
						if(logger.isDebugEnabled()){
							logger.debug("Route invalid : edge "+edge+"'s capacity exceeded. "+totalAllocMbps+"/"+(edge.getRateMbps() * edge.getAllocationCapacity())+" Route : "+route);
						}
						cost.add(Objective.one, 1);
					}*/
					allocMap.put(edge, totalAllocMbps);
				}
			}
			
			//Now calculate timings 
			for(Multicast m : rs_in_curr_mode){
				Application app = m.getApplication();
				//Below calculation is only relevant for AVB-traffic
				if(app instanceof TTApplication){
					continue;
				}
				double maxLatency = 0;
				for(Unicast u : m.getUnicasts()){
					double latency = 0;
					for(GCLEdge edge : u.getRoute().getEdgeList()){
						double capacity = edge.getAllocationCapacity() - (edge.calculateWorstCaseInterference(app.getInterval()) /app.getInterval()-1);
						latency += calculateMaxLatency(edge, allocMap.get(edge),(AVBApplication) app, capacity);
					}
					//For multicast routing, were only interested in the worst route
					if(maxLatency < latency){
						maxLatency = latency;
					}
				}
				cost.setWCD(m, maxLatency);
				if(maxLatency > app.getDeadline()){
					if(logger.isDebugEnabled()){
						logger.debug(route+" set non-schedulable : "+app.getTitle()+"'s maxLatency exceeds deadline");
					}
					cost.add(Objective.one, 1);
				}
				if (maxLatency / app.getDeadline() > PENALITY_THRESHOLD){
					cost.add(Objective.two, (maxLatency/app.getDeadline() - PENALITY_THRESHOLD));
				}
			}
		}
		return cost;
	}
	//	//TODO Add UnitTests
	//	/** This method has been based on the formulas in 802.1BA Draft 2.5 
	//	 * http://www.ieee802.org/1/files/private/ba-drafts/d2/802-1ba-d2-5.pdf*/
	private double calculateMaxLatency(GCLEdge edge, double totalAlloc_mbps, AVBApplication app, double capacity){
		//Time to transmit max interfering BE packet.  
		double tMaxPacket = (MAX_BE_FRAME_BYTES + 8)*8 / edge.getRateMbps();
		//Time to transmit frame_size
		double tStreamPacket = (app.getMaxFrameSize() + 8)*8 / edge.getRateMbps();
		//Inter-Frame-Gap
		double tIFG = 12*8 / edge.getRateMbps();
		//Sum of transmission times of all Class A stream frames in a 125us interval
		double tAllStreams = totalAlloc_mbps * app.getInterval() / edge.getRateMbps();
		double maxLatency = edge.getLatency() + edge.calculateWorstCaseInterference((tMaxPacket+tIFG +
				tAllStreams - (tStreamPacket+tIFG)) * (edge.getRateMbps()/capacity)/100 + 
				tStreamPacket); 
		return maxLatency; 
	}
}
