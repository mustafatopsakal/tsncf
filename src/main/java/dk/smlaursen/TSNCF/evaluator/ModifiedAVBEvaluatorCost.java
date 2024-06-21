package dk.smlaursen.TSNCF.evaluator;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;

import dk.smlaursen.TSNCF.solver.Multicast;

public class ModifiedAVBEvaluatorCost implements Cost, Comparator<ModifiedAVBEvaluatorCost> {
	private double w1 = 10000, w2 = 3.0, w3 = 1.0, obj1, obj2, obj3;
	private boolean isUsed;
	private Map<Multicast, Double> aWCDMap = new HashMap<Multicast, Double>(); 
	
	public ModifiedAVBEvaluatorCost() {
		reset();
	}
	
	public void setWCD(Multicast m, Double wcd){
		aWCDMap.put(m, wcd);
	}
	
	public double getWCD(Multicast m){
		return aWCDMap.get(m);
	}
	
	public void add(Objective e, double value){
		isUsed = true;
		switch(e){
		case one:
			obj1 += value;
			break;
		case two:
			obj2 += value;
			break;
		case three:
			obj3 += value;
			break;
		}
	}
	
	@Override
	public double getTotalCost() {
		if(!isUsed){
			return Double.MAX_VALUE;
		}
		return  w1*obj1 + w2*obj2 + w3*obj3;
	}

	@Override
	public void reset() {
		isUsed = false;
		obj1 = 0.0;
		obj2 = 0.0;
		obj3 = 0.0;
	}

	@Override
	public int compare(ModifiedAVBEvaluatorCost o1, ModifiedAVBEvaluatorCost o2) {
		return (int) Math.round(o1.getTotalCost() - o2.getTotalCost());
	}

	public enum Objective{
		one, two, three;
	}
	
	public String toString(){
		return getTotalCost()+" (unschedulable = "+obj1+")";
	}
	
	public String toDetailedString(){
		//return "Total : "+toString()+" | o1 "+obj1+", o2 "+obj2+", o3 "+obj3+" -- "+ aWCDMap+" --";
		
		return "Total : "+toString()+" | o1 "+obj1+", o2 "+obj2+", o3 "+obj3+" --\n" + getaWCDMap();
	}
	
	public String getaWCDMap()
	{
		String staWCDMap = "";
		
		for ( Map.Entry<Multicast, Double> entry : aWCDMap.entrySet()) {
		    String key = entry.getKey().toString();
		    String tab = entry.getValue().toString();
		    // do something with key and/or tab
		    staWCDMap += key + " MaxLatency: " + tab + "\n";
		}
		
		return staWCDMap;
	}
}

